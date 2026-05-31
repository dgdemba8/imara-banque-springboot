package com.imara.banque.service;

import com.imara.banque.config.AppConfig;
import com.imara.banque.model.JournalConnexion;
import com.imara.banque.model.TentativeConnexion;
import com.imara.banque.model.User;
import com.imara.banque.repository.JournalConnexionRepository;
import com.imara.banque.repository.TentativeConnexionRepository;
import com.imara.banque.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service d'authentification en 2 étapes.
 *
 * tenterConnexion() valide le mot de passe et gère le journal/blocage
 * SANS établir la session Spring Security.
 *
 * C'est AuthController.etape2Post() qui appelle request.login()
 * pour établir la session — c'est la seule méthode fiable pour
 * que Spring Security persiste l'authentification dans la session HTTP.
 */
@Service
public class AuthService {

    private final UserRepository               userRepo;
    private final TentativeConnexionRepository tentativeRepo;
    private final JournalConnexionRepository   journalRepo;
    private final AuthenticationManager        authManager;
    private final EmailService                 emailService;
    private final AppConfig                    config;

    public AuthService(UserRepository userRepo,
                       TentativeConnexionRepository tentativeRepo,
                       JournalConnexionRepository journalRepo,
                       AuthenticationManager authManager,
                       EmailService emailService,
                       AppConfig config) {
        this.userRepo      = userRepo;
        this.tentativeRepo = tentativeRepo;
        this.journalRepo   = journalRepo;
        this.authManager   = authManager;
        this.emailService  = emailService;
        this.config        = config;
    }

    // ── Records résultats ─────────────────────────────────────────────────

    public record VerifUsernameResult(
        boolean existe,
        boolean bloque,
        long secondesRestantes
    ) {}

    public record ConnexionResult(
        boolean succes,
        boolean bloque,
        long secondesRestantes,
        int tentativesRestantes,
        String message
    ) {}

    // ── ÉTAPE 1 : vérifier que le username existe ─────────────────────────

    @Transactional
    public VerifUsernameResult verifierUsername(String username, String ip) {
        if (!userRepo.existsByUsername(username)) {
            return new VerifUsernameResult(false, false, 0);
        }
        TentativeConnexion t = getOuCreerTentative(username, ip);
        if (t.isEstBloque()) {
            return new VerifUsernameResult(true, true, t.getTempsRestant());
        }
        return new VerifUsernameResult(true, false, 0);
    }

    // ── ÉTAPE 2 : valider le mot de passe ────────────────────────────────
    //
    // IMPORTANT : cette méthode NE fait PAS request.login() ni
    // SecurityContextHolder.getContext().setAuthentication().
    // C'est AuthController qui appelle request.login(username, password)
    // après un succes=true, ce qui établit correctement la session.

    @Transactional
    public ConnexionResult tenterConnexion(String username, String password, String ip) {
        TentativeConnexion tentative = getOuCreerTentative(username, ip);

        // Vérification blocage préalable
        if (tentative.isEstBloque()) {
            return new ConnexionResult(false, true, tentative.getTempsRestant(),
                                       0, "Compte temporairement bloqué.");
        }

        try {
            // Validation via Spring Security (BCrypt)
            Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
            );

            // ── Succès ────────────────────────────────────────────────────
            User user = (User) auth.getPrincipal();

            // Journal de connexion
            journalRepo.save(new JournalConnexion(user, ip, true));

            // Alerte connexions multiples
            alerterConnexionsMultiples(user, ip);

            // Réinitialiser le compteur de tentatives
            reinitialiserTentative(tentative);

            return new ConnexionResult(true, false, 0, config.maxTentatives, "Connexion réussie.");

        } catch (BadCredentialsException e) {
            // ── Mauvais mot de passe ──────────────────────────────────────
            userRepo.findByUsername(username).ifPresent(user ->
                journalRepo.save(new JournalConnexion(user, ip, false))
            );

            enregistrerEchec(tentative);

            if (tentative.isEstBloque()) {
                return new ConnexionResult(false, true, tentative.getTempsRestant(),
                                           0, "Compte bloqué après trop d'échecs.");
            }

            int restantes = config.maxTentatives - tentative.getTentatives();
            String msg = restantes == 1
                ? "Mot de passe incorrect. Attention : il ne vous reste plus qu'une seule tentative."
                : "Mot de passe incorrect. Il vous reste " + restantes + " tentative(s).";

            return new ConnexionResult(false, false, 0, restantes, msg);

        } catch (Exception e) {
            // Autre erreur inattendue
            return new ConnexionResult(false, false, 0, 0,
                "Erreur d'authentification. Réessayez.");
        }
    }

    // ── Info blocage ──────────────────────────────────────────────────────

    public Optional<TentativeConnexion> getTentative(String username, String ip) {
        return tentativeRepo.findByUsernameAndAdresseIp(username, ip);
    }

    // ── Helpers privés ────────────────────────────────────────────────────

    private TentativeConnexion getOuCreerTentative(String username, String ip) {
        return tentativeRepo.findByUsernameAndAdresseIp(username, ip)
            .orElseGet(() -> tentativeRepo.save(new TentativeConnexion(username, ip)));
    }

    private void enregistrerEchec(TentativeConnexion t) {
        // Si un blocage précédent est expiré → repartir de zéro
        if (t.getBloqueJusqu() != null && LocalDateTime.now().isAfter(t.getBloqueJusqu())) {
            t.setTentatives(0);
            t.setBloqueJusqu(null);
        }
        t.setTentatives(t.getTentatives() + 1);
        if (t.getTentatives() >= config.maxTentatives) {
            t.setBloqueJusqu(LocalDateTime.now().plusMinutes(config.dureeBlocageMinutes));
        }
        tentativeRepo.save(t);
    }

    private void reinitialiserTentative(TentativeConnexion t) {
        t.setTentatives(0);
        t.setBloqueJusqu(null);
        tentativeRepo.save(t);
    }

    private void alerterConnexionsMultiples(User user, String ip) {
        LocalDateTime depuis = LocalDateTime.now().minusMinutes(config.fenetreAlerteMinutes);
        long nb = journalRepo.countByUtilisateurAndSuccesAndDateConnexionAfter(
            user, true, depuis
        );
        if (nb >= config.nbConnexionsAlerte) {
            emailService.envoyerAlerteConnexion(user, ip, nb + 1);
        }
    }
}