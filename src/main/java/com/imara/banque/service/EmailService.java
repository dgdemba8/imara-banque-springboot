package com.imara.banque.service;

import com.imara.banque.model.Transaction;
import com.imara.banque.model.User;
import com.imara.banque.model.VirementRecurrent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service d'envoi d'emails.
 *
 * Équivalent de {@code send_mail()} Django dans accounts/views.py
 * et transactions/views.py.
 *
 * Les méthodes sont {@code @Async} : l'envoi ne bloque pas la requête HTTP.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm:ss");
    private static final String FROM = "noreply@imarabanque.com";

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ── Alerte connexions multiples ───────────────────────────────────────

    /**
     * Équivalent de {@code _alerter_connexions_multiples()} Django.
     */
    @Async
    public void envoyerAlerteConnexion(User user, String ip, long nbConnexions) {
        String corps = String.format("""
            Bonjour %s,
            
            Nous avons détecté %d connexions à votre compte en moins de 10 minutes.
            
            Détails de la dernière connexion :
              • Adresse IP : %s
              • Date       : %s
            
            Si vous êtes à l'origine de toutes ces connexions, vous pouvez ignorer ce message.
            
            Dans le cas contraire, nous vous recommandons de :
              1. Changer immédiatement votre mot de passe
              2. Contacter votre conseiller bancaire
            
            Cordialement,
            L'équipe Imara Banque
            ──────────────────────────────
            Ce message est envoyé automatiquement, merci de ne pas y répondre.
            """,
            user.getFullName(),
            nbConnexions,
            ip,
            LocalDateTime.now().format(FMT)
        );
        envoyerEmail(user.getEmail(),
                     "Connexion suspecte détectée - Imara Banque",
                     corps);
    }

    // ── Confirmation virement ─────────────────────────────────────────────

    /**
     * Email de confirmation pour les virements ≥ 200 000 FCFA.
     * Équivalent de {@code _envoyer_email_virement()} Django.
     */
    @Async
    public void envoyerConfirmationVirement(User user, Transaction transaction) {
        String destNumero = transaction.getCompteDestination() != null
            ? transaction.getCompteDestination().getNumeroCompte() : "—";
        String montantFmt = String.format("%,.0f", transaction.getMontant()).replace(',', ' ');

        String corps = String.format("""
            Bonjour %s,
            
            Votre virement a été exécuté avec succès.
            
              - Montant        : %s FCFA
              - Compte source  : %s
              - Destinataire   : %s
              - Motif          : %s
              - Date           : %s
            
            Si vous n'êtes pas à l'origine de cette opération,
            contactez immédiatement votre conseiller bancaire.
            
            Cordialement,
            L'équipe Imara Banque
            ---
            Ce message est envoyé automatiquement, merci de ne pas y répondre.
            """,
            user.getFullName(),
            montantFmt,
            transaction.getCompteSource().getNumeroCompte(),
            destNumero,
            transaction.getMotif() != null ? transaction.getMotif() : "-",
            LocalDateTime.now().format(FMT)
        );
        envoyerEmail(user.getEmail(),
                     "Virement de " + montantFmt + " FCFA confirmé - Imara Banque",
                     corps);
    }

    // ── Virement récurrent : succès ───────────────────────────────────────

    /**
     * Notification de succès d'un virement récurrent.
     * Équivalent de la notification dans {@code tasks.py} Celery Django.
     */
    @Async
    public void envoyerSuccesVirementRecurrent(VirementRecurrent v) {
        String corps = String.format("""
            Bonjour %s,
            
            Votre virement récurrent de %,.0f FCFA (%s) a été exécuté avec succès.
            
            Compte source      : %s
            Compte destination : %s
            Motif              : %s
            
            Cordialement,
            L'équipe Imara Banque
            """,
            v.getUtilisateur().getUsername(),
            v.getMontant(),
            v.getFrequenceDisplay(),
            v.getCompteSource().getNumeroCompte(),
            v.getCompteDestination().getNumeroCompte(),
            v.getMotif() != null ? v.getMotif() : "—"
        );
        envoyerEmail(v.getUtilisateur().getEmail(),
                     "Virement récurrent exécuté — Imara Banque",
                     corps);
    }

    // ── Virement récurrent : échec ────────────────────────────────────────

    @Async
    public void envoyerEchecVirementRecurrent(VirementRecurrent v, String raison) {
        String corps = String.format("""
            Bonjour %s,
            
            Votre virement récurrent de %,.0f FCFA n'a pas pu être exécuté.
            
            Raison : %s
            
            Veuillez vous connecter pour régulariser la situation.
            
            Cordialement,
            L'équipe Imara Banque
            """,
            v.getUtilisateur().getUsername(),
            v.getMontant(),
            raison
        );
        envoyerEmail(v.getUtilisateur().getEmail(),
                     "Échec virement récurrent — Imara Banque",
                     corps);
    }

    // ── Méthode générique ─────────────────────────────────────────────────

    private void envoyerEmail(String destinataire, String sujet, String corps) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(FROM);
            message.setTo(destinataire);
            message.setSubject(sujet);
            message.setText(corps);
            mailSender.send(message);
        } catch (Exception e) {
            // fail_silently=True Django → on logue mais on n'interrompt pas
            log.warn("Échec envoi email à {} : {}", destinataire, e.getMessage());
        }
    }
}
