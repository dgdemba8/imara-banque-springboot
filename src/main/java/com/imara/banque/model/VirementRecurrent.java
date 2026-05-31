package com.imara.banque.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Virement récurrent programmé.
 *
 * Équivalent Django : {@code apps.transactions.models.VirementRecurrent}
 * La tâche planifiée est dans {@link com.imara.banque.scheduler.VirementRecurrentScheduler}
 * (remplace Celery Beat + tasks.py).
 */
@Entity
@Table(name = "transactions_virementrecurrent")
@Getter @Setter @NoArgsConstructor
public class VirementRecurrent {

    public enum Frequence {
        quotidien("Quotidien"),
        hebdo("Hebdomadaire"),
        mensuel("Mensuel");

        private final String libelle;
        Frequence(String libelle) { this.libelle = libelle; }
        public String getLibelle() { return libelle; }
    }

    public enum Statut {
        actif("Actif"),
        pause("En pause"),
        termine("Terminé");

        private final String libelle;
        Statut(String libelle) { this.libelle = libelle; }
        public String getLibelle() { return libelle; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private User utilisateur;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compte_source_id", nullable = false)
    private Compte compteSource;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compte_destination_id", nullable = false)
    private Compte compteDestination;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montant;

    @Column(length = 255)
    private String motif;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private Frequence frequence = Frequence.mensuel;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    /** Null = sans fin. */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "prochaine_execution", nullable = false)
    private LocalDate prochaineExecution;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private Statut statut = Statut.actif;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @PrePersist
    protected void onCreate() {
        this.dateCreation = LocalDateTime.now();
    }


    public String getFrequenceDisplay() {
        return frequence != null ? frequence.getLibelle() : "";
    }

    public String getStatutDisplay() {
        return statut != null ? statut.getLibelle() : "";
    }

    // ── Logique métier (extraite de executer() / calculer_prochaine_date()) ─
    // La logique d'exécution est dans VirementRecurrentService pour respecter
    // la séparation des responsabilités (Service Layer pattern Spring).

    /**
     * Calcule la prochaine date d'exécution après la date actuelle.
     * Équivalent de {@code calculer_prochaine_date()} Django.
     */
    public LocalDate calculerProchaineDate() {
        LocalDate base = this.prochaineExecution;
        return switch (this.frequence) {
            case quotidien -> base.plusDays(1);
            case hebdo     -> base.plusWeeks(1);
            case mensuel   -> base.plusMonths(1);
        };
    }

    @Override
    public String toString() {
        return getFrequenceDisplay() + " — " + montant + " FCFA (" +
               compteSource.getNumeroCompte() + " → " + compteDestination.getNumeroCompte() + ")";
    }
}
