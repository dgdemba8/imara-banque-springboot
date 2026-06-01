package com.imara.banque.controller;

import com.imara.banque.model.TentativeConnexion;
import com.imara.banque.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;                              // ← AJOUT
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;


@Controller
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authManager;

    public AuthController(AuthService authService, AuthenticationManager authManager) {
        this.authService = authService;
        this.authManager = authManager;
    }

    private String getIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** GET /auth/ */
    @GetMapping("/")
    public String etape1Get() {
        return "accounts/step1_username";
    }

    /** POST /auth/ */
    @PostMapping("/")
    public String etape1Post(@RequestParam String username,
                              HttpServletRequest request,
                              HttpSession session,
                              Model model) {

        String ip = getIp(request);
        AuthService.VerifUsernameResult result =
                authService.verifierUsername(username.trim(), ip);

        if (!result.existe()) {
            model.addAttribute("erreur", "Ce nom d'utilisateur n'existe pas.");
            return "accounts/step1_username";
        }
        if (result.bloque()) {
            session.setAttribute("bloque_username", username.trim());
            session.setAttribute("bloque_secondes", result.secondesRestantes());
            return "redirect:/auth/bloque/";
        }

        session.setAttribute("username_temp", username.trim());
        return "redirect:/auth/password/";
    }

    /** GET /auth/password/ */
    @GetMapping("/password/")
    public String etape2Get(HttpSession session, Model model) {
        String username = (String) session.getAttribute("username_temp");
        if (username == null) {
            return "redirect:/auth/";
        }
        model.addAttribute("username", username);
        model.addAttribute("tentativesRestantes", 3);
        return "accounts/step2_password";
    }

    /** POST /auth/password/ */
    @PostMapping("/password/")
    public String etape2Post(@RequestParam String password,
                              HttpServletRequest request,
                              HttpSession session,
                              Model model) {

        String username = (String) session.getAttribute("username_temp");
        if (username == null) {
            return "redirect:/auth/";
        }

        String ip = getIp(request);
        AuthService.ConnexionResult result =
                authService.tenterConnexion(username, password, ip);

        if (result.succes()) {
            try {
                Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
                );

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                SecurityContextHolder.setContext(context);

                HttpSession newSession = request.getSession(true);
                newSession.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context
                );
                newSession.removeAttribute("username_temp");

                return "redirect:/dashboard/";

            } catch (Exception e) {
                model.addAttribute("erreur", "Erreur lors de la connexion, réessayez.");
                model.addAttribute("username", username);
                return "accounts/step2_password";
            }
        }

        if (result.bloque()) {
            session.setAttribute("bloque_username", username);
            session.setAttribute("bloque_secondes", result.secondesRestantes());
            return "redirect:/auth/bloque/";
        }

        model.addAttribute("username", username);
        model.addAttribute("erreur", result.message());
        model.addAttribute("tentativesRestantes", result.tentativesRestantes());
        return "accounts/step2_password";
    }

    /** GET /auth/bloque/ */
    @GetMapping("/bloque/")
    public String compteBloque(HttpServletRequest request,
                                HttpSession session,
                                Model model) {
        String username = (String) session.getAttribute("bloque_username");
        long secondes   = 900L;

        if (username != null) {
            String ip = getIp(request);
            Optional<TentativeConnexion> t = authService.getTentative(username, ip);
            if (t.isPresent()) {
                if (!t.get().isEstBloque()) {
                    return "redirect:/auth/";
                }
                secondes = t.get().getTempsRestant();
            }
        }

        model.addAttribute("username", username != null ? username : "");
        model.addAttribute("secondes", secondes);
        return "accounts/compte_bloque";
    }

    /** POST /auth/refresh-session/ */
    @PostMapping("/refresh-session/")
    @ResponseBody
    public ResponseEntity<Map<String, String>> refreshSession(HttpSession session) {
        session.setAttribute("_refresh", System.currentTimeMillis());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}