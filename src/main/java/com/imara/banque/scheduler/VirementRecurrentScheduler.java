package com.imara.banque.scheduler;

import com.imara.banque.model.Compte;
import com.imara.banque.model.Transaction;
import com.imara.banque.model.VirementRecurrent;
import com.imara.banque.repository.CompteRepository;
import com.imara.banque.repository.TransactionRepository;
import com.imara.banque.repository.VirementRecurrentRepository;
import com.imara.banque.service.CompteService;
import com.imara.banque.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Scheduler Spring — remplace Celery Beat + tasks.py Django.
 *
 * Équivalent de {@code executer_virements_recurrents} (Celery task).
 *
 * Fréquence : toutes les heures (configurable via cron expression).
 * Pour changer la fréquence, modifier le {@code cron} dans @Scheduled.
 *
 * Exemples :
 *   "0 * * * * *"      → toutes les minutes (tests)
 *   "0 0 * * * *"      → toutes les heures
 *   "0 0 8 * * *"      → tous les jours à 8h00
 */
@Component
public class VirementRecurrentScheduler {

    private static final Logger log = LoggerFactory.getLogger(VirementRecurrentScheduler.class);

    private final VirementRecurrentRepository virementRepo;
    private final TransactionRepository       transactionRepo;
    private final CompteRepository            compteRepo;
    private final CompteService               compteService;
    private final EmailService                emailService;

    public VirementRecurrentScheduler(VirementRecurrentRepository virementRepo,
                                       TransactionRepository transactionRepo,
                                       CompteRepository compteRepo,
                                       CompteService compteService,
                                       EmailService emailService) {
        this.virementRepo    = virementRepo;
        this.transactionRepo = transactionRepo;
        this.compteRepo      = compteRepo;
        this.compteService   = compteService;
        this.emailService    = emailService;
    }

    /**
     * Exécution toutes les heures.
     * Équivalent exact de la tâche Celery Beat Django.
     *
     * Le cron Spring utilise 6 champs : seconde minute heure jour mois jour-semaine
     * "0 0 * * * *" = à la 0e seconde, 0e minute de chaque heure.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void executerVirementsRecurrents() {
        LocalDate aujourd_hui = LocalDate.now();

        List<VirementRecurrent> dus = virementRepo.findDus(aujourd_hui);

        int succes  = 0;
        int echecs  = 0;

        for (VirementRecurrent v : dus) {
            try {
                String resultat = executer(v);
                if (resultat == null) {
                    succes++;
                    log.info("OK  — {}", v);
                    emailService.envoyerSuccesVirementRecurrent(v);
                } else {
                    echecs++;
                    log.warn("ECHEC — {} : {}", v, resultat);
                    emailService.envoyerEchecVirementRecurrent(v, resultat);
                }
            } catch (Exception e) {
                echecs++;
                log.error("Erreur inattendue pour virement {} : {}", v.getId(), e.getMessage());
            }
        }

        log.info("Scheduler virements récurrents terminé : {} succès, {} échecs", succes, echecs);
    }

    /**
     * Exécute un virement récurrent.
     * Équivalent de {@code VirementRecurrent.executer()} Django (modèle).
     *
     * @return null si succès, message d'erreur si échec
     */
    private String executer(VirementRecurrent v) {
        if (v.getStatut() != VirementRecurrent.Statut.actif) {
            return "Virement inactif.";
        }

        Compte source = v.getCompteSource();
        Compte dest   = v.getCompteDestination();

        // Vérification solde
        if (source.getSolde().compareTo(v.getMontant()) < 0) {
            return "Solde insuffisant sur " + source.getNumeroCompte() + ".";
        }

        // Vérification plafond
        Optional<String> errPlafond = compteService.verifierPlafond(source, v.getMontant());
        if (errPlafond.isPresent()) {
            return errPlafond.get();
        }

        // Exécution financière
        source.setSolde(source.getSolde().subtract(v.getMontant()));
        dest.setSolde(dest.getSolde().add(v.getMontant()));
        compteRepo.save(source);
        compteRepo.save(dest);

        // Création de la transaction
        Transaction t = new Transaction();
        t.setCompteSource(source);
        t.setCompteDestination(dest);
        t.setTypeTransaction(Transaction.TypeTransaction.virement);
        t.setMontant(v.getMontant());
        t.setMotif(v.getMotif() != null ? v.getMotif()
                   : "Virement récurrent (" + v.getFrequenceDisplay() + ")");
        t.setStatut(true);
        t.setVirementRecurrent(v);
        transactionRepo.save(t);

        // Mise à jour de la prochaine date
        LocalDate prochaine = v.calculerProchaineDate();
        if (v.getDateFin() != null && prochaine.isAfter(v.getDateFin())) {
            v.setStatut(VirementRecurrent.Statut.termine);
        } else {
            v.setProchaineExecution(prochaine);
        }
        virementRepo.save(v);

        return null; // succès
    }
}
