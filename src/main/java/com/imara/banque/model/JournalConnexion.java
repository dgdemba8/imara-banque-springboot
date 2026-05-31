package com.imara.banque.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Journal des connexions (réussies et échouées).
 *
 * Équivalent Django : {@code apps.accounts.models.JournalConnexion}
 * Table : {@code accounts_journalconnexion}
 */
@Entity
@Table(name = "accounts_journalconnexion",
       indexes = @Index(name = "idx_journal_user_date",
                        columnList = "utilisateur_id, date_connexion"))
@Getter @Setter @NoArgsConstructor
public class JournalConnexion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private User utilisateur;

    /** Rempli automatiquement à la création. */
    @Column(name = "date_connexion", nullable = false, updatable = false)
    private LocalDateTime dateConnexion;

    @Column(name = "adresse_ip", length = 45)
    private String adresseIp;

    /** True = connexion réussie, False = échec. */
    @Column(nullable = false)
    private Boolean succes = true;

    @PrePersist
    protected void onCreate() {
        this.dateConnexion = LocalDateTime.now();
    }

    public JournalConnexion(User utilisateur, String adresseIp, boolean succes) {
        this.utilisateur = utilisateur;
        this.adresseIp   = adresseIp;
        this.succes      = succes;
    }

    @Override
    public String toString() {
        return utilisateur.getUsername() + " - " + dateConnexion;
    }
}
