package com.imara.banque.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité Transaction.
 *
 * Équivalent Django : {@code apps.transactions.models.Transaction}
 */
@Entity
@Table(name = "transactions_transaction",
       indexes = {
           @Index(name = "idx_txn_source",      columnList = "compte_source_id"),
           @Index(name = "idx_txn_dest",        columnList = "compte_destination_id"),
           @Index(name = "idx_txn_date",        columnList = "date_transaction"),
       })
@Getter @Setter @NoArgsConstructor
public class Transaction {

    public enum TypeTransaction {
        virement("Virement"),
        depot("Dépôt"),
        retrait("Retrait");

        private final String libelle;
        TypeTransaction(String libelle) { this.libelle = libelle; }
        public String getLibelle() { return libelle; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compte_source_id", nullable = false)
    private Compte compteSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compte_destination_id")
    private Compte compteDestination;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_transaction", length = 10, nullable = false)
    private TypeTransaction typeTransaction;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montant;

    @Column(name = "date_transaction", nullable = false, updatable = false)
    private LocalDateTime dateTransaction;

    @Column(length = 255)
    private String motif;

    @Column(nullable = false)
    private Boolean statut = true;

    /** Lien optionnel vers le virement récurrent générateur. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "virement_recurrent_id")
    private VirementRecurrent virementRecurrent;

    @Column(nullable = false)
    private Boolean annule = false;

    @Column(name = "date_annulation")
    private LocalDateTime dateAnnulation;

    @PrePersist
    protected void onCreate() {
        this.dateTransaction = LocalDateTime.now();
    }

    /**
     * True si la transaction peut encore être annulée.
     * Équivalent de {@code annulable} (property Django).
     */
    public boolean isAnnulable(int delaiMinutes) {
        if (Boolean.TRUE.equals(annule) || typeTransaction != TypeTransaction.virement) {
            return false;
        }
        LocalDateTime limite = dateTransaction.plusMinutes(delaiMinutes);
        return LocalDateTime.now().isBefore(limite);
    }

    /**
     * Secondes restantes avant fin de la fenêtre d'annulation.
     * Équivalent de {@code secondes_avant_expiration} (property Django).
     */
    public long getSecondesAvantExpiration(int delaiMinutes) {
        if (!isAnnulable(delaiMinutes)) return 0L;
        LocalDateTime limite = dateTransaction.plusMinutes(delaiMinutes);
        long secondes = java.time.Duration.between(LocalDateTime.now(), limite).getSeconds();
        return Math.max(secondes, 0L);
    }

    /**
     * Libellé du type de transaction.
     * Équivalent de {@code get_type_transaction_display()} Django.
     */
    public String getTypeTransactionDisplay() {
        return typeTransaction != null ? typeTransaction.getLibelle() : "";
    }

    @Override
    public String toString() {
        return typeTransaction + " - " + montant + " FCFA - " + dateTransaction;
    }
}
