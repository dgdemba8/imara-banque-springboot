package com.imara.banque.service;

import com.imara.banque.model.Compte;
import com.imara.banque.model.Transaction;
import com.imara.banque.model.User;
import com.imara.banque.repository.CompteRepository;
import com.imara.banque.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service métier pour les comptes bancaires.
 *
 * Équivalent des méthodes du modèle Django {@code Compte} :
 *   - montant_vire_aujourd_hui()
 *   - peut_virer()
 * Et de la vue {@code apps/comptes/views.py}.
 */
@Service
public class CompteService {

    private final CompteRepository      compteRepo;
    private final TransactionRepository transactionRepo;

    public CompteService(CompteRepository compteRepo,
                         TransactionRepository transactionRepo) {
        this.compteRepo      = compteRepo;
        this.transactionRepo = transactionRepo;
    }

    public List<Compte> getComptesActifs(User user) {
        return compteRepo.findByUtilisateurAndActifTrue(user);
    }

    public Optional<Compte> getCompteActif(Long id, User user) {
        return compteRepo.findByIdAndUtilisateurAndActifTrue(id, user);
    }

    public Optional<Compte> getCompteParNumero(String numero) {
        return compteRepo.findByNumeroCompte(numero);
    }

    /**
     * Équivalent de {@code montant_vire_aujourd_hui()} Django.
     */
    @Transactional(readOnly = true)
    public BigDecimal montantVireAujourdHui(Compte compte) {
        BigDecimal total = transactionRepo.sumVirementsAujourdHui(compte, LocalDate.now());
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Équivalent de {@code peut_virer(montant)} Django.
     * Retourne un Optional<String> : vide = OK, présent = message d'erreur.
     */
    @Transactional(readOnly = true)
    public Optional<String> verifierPlafond(Compte compte, BigDecimal montant) {
        if (compte.getPlafondVirement() == null) {
            return Optional.empty(); // pas de limite
        }
        BigDecimal dejaVire = montantVireAujourdHui(compte);
        BigDecimal total    = dejaVire.add(montant);
        if (total.compareTo(compte.getPlafondVirement()) > 0) {
            BigDecimal restant = compte.getPlafondVirement().subtract(dejaVire).max(BigDecimal.ZERO);
            String msg = String.format(
                "Plafond journalier atteint. Il vous reste %,.0f FCFA disponibles aujourd'hui (plafond : %,.0f FCFA).",
                restant, compte.getPlafondVirement()
            );
            return Optional.of(msg);
        }
        return Optional.empty();
    }

    /**
     * Mise à jour du plafond journalier.
     * Équivalent du POST de {@code modifier_plafond} Django.
     */
    @Transactional
    public void modifierPlafond(Compte compte, BigDecimal plafond) {
        compte.setPlafondVirement(plafond); // null = illimité
        compteRepo.save(compte);
    }

    @Transactional
    public Compte sauvegarder(Compte compte) {
        return compteRepo.save(compte);
    }
}
