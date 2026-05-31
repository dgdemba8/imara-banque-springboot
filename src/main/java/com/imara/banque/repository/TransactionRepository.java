package com.imara.banque.repository;

import com.imara.banque.model.Compte;
import com.imara.banque.model.Transaction;
import com.imara.banque.model.VirementRecurrent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Équivalent de {@code Transaction.objects.filter(...)} Django.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Historique de toutes les transactions liées à un compte (source ou dest).
     * Équivalent Django :
     *   (Transaction.objects.filter(compte_source__in=comptes) |
     *    Transaction.objects.filter(compte_destination__in=comptes))
     *   .order_by('-date_transaction')
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE (t.compteSource IN :comptes OR t.compteDestination IN :comptes)
        ORDER BY t.dateTransaction DESC
        """)
    List<Transaction> findByComptes(@Param("comptes") List<Compte> comptes);

    /**
     * Transactions d'un compte sur une période, non annulées.
     * Utilisé pour le relevé PDF.
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.annule = false
          AND t.dateTransaction >= :debut
          AND t.dateTransaction <= :fin
          AND (t.compteSource = :compte OR t.compteDestination = :compte)
        ORDER BY t.dateTransaction DESC
        """)
    List<Transaction> findByComptePeriode(
        @Param("compte") Compte compte,
        @Param("debut")  LocalDateTime debut,
        @Param("fin")    LocalDateTime fin
    );

    /**
     * Calcule le total des virements émis aujourd'hui depuis un compte.
     * Équivalent de {@code montant_vire_aujourd_hui()} Django.
     */
    @Query("""
        SELECT COALESCE(SUM(t.montant), 0) FROM Transaction t
        WHERE t.compteSource = :compte
          AND t.typeTransaction = 'virement'
          AND t.statut = true
          AND CAST(t.dateTransaction AS LocalDate) = :aujourd_hui
        """)
    BigDecimal sumVirementsAujourdHui(
        @Param("compte")       Compte compte,
        @Param("aujourd_hui")  LocalDate aujourd_hui
    );

    /**
     * Recherche par ID avec vérification du propriétaire (sécurité).
     * Équivalent de get_object_or_404(Transaction, id=id,
     *              compte_source__utilisateur=request.user, ...).
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.id = :id
          AND t.compteSource.utilisateur.id = :userId
          AND t.typeTransaction = 'virement'
        """)
    Optional<Transaction> findByIdAndUserId(
        @Param("id")     Long id,
        @Param("userId") Long userId
    );

    // ── Filtres avancés pour l'historique ────────────────────────────────

    @Query("""
        SELECT t FROM Transaction t
        WHERE (t.compteSource IN :comptes OR t.compteDestination IN :comptes)
          AND (:type IS NULL OR t.typeTransaction = :type)
          AND (:debut IS NULL OR t.dateTransaction >= :debut)
          AND (:fin   IS NULL OR t.dateTransaction <= :fin)
        ORDER BY t.dateTransaction DESC
        """)
    List<Transaction> findWithFilters(
        @Param("comptes") List<Compte> comptes,
        @Param("type")    Transaction.TypeTransaction type,
        @Param("debut")   LocalDateTime debut,
        @Param("fin")     LocalDateTime fin
    );
}
