package com.imara.banque.service;

import com.imara.banque.config.AppConfig;
import com.imara.banque.model.Compte;
import com.imara.banque.model.Transaction;
import com.imara.banque.model.User;
import com.imara.banque.model.VirementRecurrent;
import com.imara.banque.repository.TransactionRepository;
import com.imara.banque.repository.VirementRecurrentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Service métier pour les transactions et virements.
 *
 * Équivalent de {@code apps/transactions/views.py} Django :
 *   - virement()             → effectuerVirement()
 *   - annuler_virement()     → annulerVirement()
 *   - virements_recurrents() → getVirements..()
 *   - creer_virement_recurrent() → creerVirementRecurrent()
 *   - toggle_virement_recurrent() → toggleVirementRecurrent()
 *   - supprimer_virement_recurrent() → supprimerVirementRecurrent()
 *
 * La logique d'exécution périodique (tasks.py) est dans
 * {@link com.imara.banque.scheduler.VirementRecurrentScheduler}.
 */
@Service
public class TransactionService {

    private final TransactionRepository        transactionRepo;
    private final VirementRecurrentRepository  virementRepo;
    private final CompteService                compteService;
    private final EmailService                 emailService;
    private final AppConfig                    config;

    public TransactionService(TransactionRepository transactionRepo,
                               VirementRecurrentRepository virementRepo,
                               CompteService compteService,
                               EmailService emailService,
                               AppConfig config) {
        this.transactionRepo = transactionRepo;
        this.virementRepo    = virementRepo;
        this.compteService   = compteService;
        this.emailService    = emailService;
        this.config          = config;
    }

    // ── Résultat typé pour virement ───────────────────────────────────────

    public record ResultatVirement(boolean succes, String message, Transaction transaction) {}

    // ── HISTORIQUE ────────────────────────────────────────────────────────

    public List<Transaction> getHistorique(List<Compte> comptes,
                                           Transaction.TypeTransaction type,
                                           LocalDate debut,
                                           LocalDate fin) {
        LocalDateTime debutDt = debut != null ? debut.atStartOfDay()          : null;
        LocalDateTime finDt   = fin   != null ? fin.atTime(LocalTime.MAX)     : null;
        return transactionRepo.findWithFilters(comptes, type, debutDt, finDt);
    }

    // ── VIREMENT ──────────────────────────────────────────────────────────

    /**
     * Effectue un virement entre deux comptes.
     * Équivalent du bloc POST de {@code virement()} Django.
     *
     * @return ResultatVirement avec succes=true et la transaction, ou succes=false + message erreur
     */
    @Transactional
    public ResultatVirement effectuerVirement(Compte source,
                                              Compte destination,
                                              BigDecimal montant,
                                              String motif,
                                              User user) {
        // Validations
        if (source.getId().equals(destination.getId())) {
            return new ResultatVirement(false, "Vous ne pouvez pas virer vers le même compte.", null);
        }
        if (montant.compareTo(BigDecimal.ZERO) <= 0) {
            return new ResultatVirement(false, "Le montant doit être supérieur à 0.", null);
        }
        if (source.getSolde().compareTo(montant) < 0) {
            return new ResultatVirement(false, "Solde insuffisant.", null);
        }

        // Vérification plafond journalier
        Optional<String> erreurPlafond = compteService.verifierPlafond(source, montant);
        if (erreurPlafond.isPresent()) {
            return new ResultatVirement(false, erreurPlafond.get(), null);
        }

        // Exécution
        source.setSolde(source.getSolde().subtract(montant));
        destination.setSolde(destination.getSolde().add(montant));
        compteService.sauvegarder(source);
        compteService.sauvegarder(destination);

        Transaction transaction = new Transaction();
        transaction.setCompteSource(source);
        transaction.setCompteDestination(destination);
        transaction.setTypeTransaction(Transaction.TypeTransaction.virement);
        transaction.setMontant(montant);
        transaction.setMotif(motif);
        transaction.setStatut(true);
        transactionRepo.save(transaction);

        // Email si montant ≥ seuil
        if (montant.compareTo(BigDecimal.valueOf(config.seuilEmailVirement)) >= 0) {
            emailService.envoyerConfirmationVirement(user, transaction);
        }

        String msg = String.format(
            "Virement de %,.0f FCFA effectué. Vous avez %d minutes pour l'annuler depuis l'historique.",
            montant, config.delaiAnnulationMinutes
        );
        return new ResultatVirement(true, msg, transaction);
    }

    // ── ANNULATION ────────────────────────────────────────────────────────

    /**
     * Équivalent du bloc POST de {@code annuler_virement()} Django.
     */
    @Transactional
    public ResultatVirement annulerVirement(Long transactionId, User user) {
        Optional<Transaction> opt = transactionRepo.findByIdAndUserId(transactionId, user.getId());
        if (opt.isEmpty()) {
            return new ResultatVirement(false, "Transaction introuvable.", null);
        }
        Transaction t = opt.get();

        if (!t.isAnnulable(config.delaiAnnulationMinutes)) {
            return new ResultatVirement(false,
                "Ce virement ne peut plus être annulé (délai de " + config.delaiAnnulationMinutes + " minutes dépassé).",
                null);
        }

        // Remboursement
        t.getCompteSource().setSolde(t.getCompteSource().getSolde().add(t.getMontant()));
        t.getCompteDestination().setSolde(t.getCompteDestination().getSolde().subtract(t.getMontant()));
        compteService.sauvegarder(t.getCompteSource());
        compteService.sauvegarder(t.getCompteDestination());

        t.setAnnule(true);
        t.setStatut(false);
        t.setDateAnnulation(LocalDateTime.now());
        transactionRepo.save(t);

        String msg = String.format("Virement de %,.0f FCFA annulé avec succès.", t.getMontant());
        return new ResultatVirement(true, msg, t);
    }

    public Optional<Transaction> getTransactionPourAnnulation(Long id, User user) {
        return transactionRepo.findByIdAndUserId(id, user.getId());
    }

    // ── VIREMENTS RÉCURRENTS ──────────────────────────────────────────────

    public List<VirementRecurrent> getVirements(User user) {
        return virementRepo.findByUtilisateurOrderByProchaineExecution(user);
    }

    public List<VirementRecurrent> getVirementsParCompte(User user, Compte compte) {
        return virementRepo.findByUtilisateurAndCompteSourceOrderByProchaineExecution(user, compte);
    }

    @Transactional
    public VirementRecurrent creerVirementRecurrent(User user,
                                                     Compte source,
                                                     Compte destination,
                                                     BigDecimal montant,
                                                     String motif,
                                                     VirementRecurrent.Frequence frequence,
                                                     LocalDate dateDebut,
                                                     LocalDate dateFin) {
        VirementRecurrent v = new VirementRecurrent();
        v.setUtilisateur(user);
        v.setCompteSource(source);
        v.setCompteDestination(destination);
        v.setMontant(montant);
        v.setMotif(motif);
        v.setFrequence(frequence);
        v.setDateDebut(dateDebut);
        v.setDateFin(dateFin);
        v.setProchaineExecution(dateDebut);
        v.setStatut(VirementRecurrent.Statut.actif);
        return virementRepo.save(v);
    }

    @Transactional
    public Optional<String> toggleVirementRecurrent(Long id, User user) {
        return virementRepo.findByIdAndUtilisateur(id, user).map(v -> {
            if (v.getStatut() == VirementRecurrent.Statut.actif) {
                v.setStatut(VirementRecurrent.Statut.pause);
                virementRepo.save(v);
                return "Virement récurrent mis en pause.";
            } else if (v.getStatut() == VirementRecurrent.Statut.pause) {
                v.setStatut(VirementRecurrent.Statut.actif);
                virementRepo.save(v);
                return "Virement récurrent réactivé.";
            }
            return "Impossible de modifier ce virement.";
        });
    }

    @Transactional
    public boolean supprimerVirementRecurrent(Long id, User user) {
        return virementRepo.findByIdAndUtilisateur(id, user).map(v -> {
            virementRepo.delete(v);
            return true;
        }).orElse(false);
    }

    public Optional<VirementRecurrent> getVirementRecurrent(Long id, User user) {
        return virementRepo.findByIdAndUtilisateur(id, user);
    }

    // ── Relevé PDF ────────────────────────────────────────────────────────

    public List<Transaction> getTransactionsPeriode(Compte compte,
                                                     LocalDate debut,
                                                     LocalDate fin) {
        return transactionRepo.findByComptePeriode(
            compte,
            debut.atStartOfDay(),
            fin.atTime(LocalTime.MAX)
        );
    }
}
