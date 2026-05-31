package com.imara.banque.controller;

import com.imara.banque.model.TentativeConnexion;
import com.imara.banque.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Contrôleur d'authentification en 2 étapes.
 *
 * Routes :
 *   GET  /auth/               → page saisie username (étape 1)
 *   POST /auth/               → vérification username
 *   GET  /auth/password/      → page saisie mot de passe (étape 2)
 *   POST /auth/password/      → tentative de connexion
 *   GET  /auth/bloque/        → page blocage temporaire
 *   POST /auth/deconnexion/   → déconnexion (géré par Spring Security)
 *   POST /auth/refresh-session/ → refresh AJAX session
 */
@Controller
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // ── Utilitaire IP ─────────────────────────────────────────────────────

    private String getIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ── ÉTAPE 1 : Saisie du username ──────────────────────────────────────

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

    // ── ÉTAPE 2 : Saisie du mot de passe ──────────────────────────────────

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
            // ── Connexion réussie ─────────────────────────────────────────
            // On utilise request.login() pour établir correctement la session
            // Spring Security (remplace SecurityContextHolder seul qui
            // ne persiste pas entre les requêtes).
            try {
                session.removeAttribute("username_temp");
                request.login(username, password);
            } catch (Exception e) {
                // Ne devrait pas arriver (AuthService a déjà validé)
                model.addAttribute("erreur", "Erreur lors de la connexion, réessayez.");
                model.addAttribute("username", username);
                return "accounts/step2_password";
            }
            return "redirect:/dashboard/";
        }

        // ── Blocage ───────────────────────────────────────────────────────
        if (result.bloque()) {
            session.setAttribute("bloque_username", username);
            session.setAttribute("bloque_secondes", result.secondesRestantes());
            return "redirect:/auth/bloque/";
        }

        // ── Échec simple ──────────────────────────────────────────────────
        model.addAttribute("username", username);
        model.addAttribute("erreur", result.message());
        model.addAttribute("tentativesRestantes", result.tentativesRestantes());
        return "accounts/step2_password";
    }

    // ── PAGE BLOCAGE ──────────────────────────────────────────────────────

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

    // ── REFRESH SESSION (AJAX) ────────────────────────────────────────────

    /** POST /auth/refresh-session/ */
    @PostMapping("/refresh-session/")
    @ResponseBody
    public ResponseEntity<Map<String, String>> refreshSession(HttpSession session) {
        session.setAttribute("_refresh", System.currentTimeMillis());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}