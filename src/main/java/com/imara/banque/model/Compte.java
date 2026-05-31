package com.imara.banque.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité JPA — équivalent du modèle Django {@code Compte}.
 *
 * apps/comptes/models.py → com.imara.banque.model.Compte
 */
@Entity
@Table(name = "comptes_compte")
@Getter @Setter @NoArgsConstructor
public class Compte {

    public enum TypeCompte {
        courant("Compte Courant"),
        epargne("Compte Épargne");

        private final String libelle;
        TypeCompte(String libelle) { this.libelle = libelle; }
        public String getLibelle() { return libelle; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Propriétaire du compte — FK vers User Spring Security. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private User utilisateur;

    @Column(name = "numero_compte", length = 20, unique = true, nullable = false)
    private String numeroCompte;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte", length = 10, nullable = false)
    private TypeCompte typeCompte = TypeCompte.courant;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal solde = BigDecimal.ZERO;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    @Column(nullable = false)
    private Boolean actif = true;

    /**
     * Plafond journalier de virement (null = pas de limite).
     * Équivalent de {@code plafond_virement} dans le modèle Django.
     */
    @Column(name = "plafond_virement", precision = 12, scale = 2)
    private BigDecimal plafondVirement;

    @PrePersist
    protected void onCreate() {
        this.dateCreation = LocalDateTime.now();
    }

    // ── Helpers métier ────────────────────────────────────────────────────

    /**
     * Libellé lisible du type de compte.
     * Équivalent de {@code get_type_compte_display()} Django.
     */
    public String getTypeCompteDisplay() {
        return typeCompte != null ? typeCompte.getLibelle() : "";
    }

    @Override
    public String toString() {
        return numeroCompte + " - " + (utilisateur != null ? utilisateur.getUsername() : "?");
    }
}
