package com.imara.banque.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Stocke les tentatives de connexion échouées par (username, IP).
 * Permet le blocage temporaire après N échecs consécutifs.
 *
 * Équivalent Django : {@code apps.accounts.models.TentativeConnexion}
 */
@Entity
@Table(name = "accounts_tentativeconnexion",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_tentative_username_ip",
           columnNames = {"username", "adresse_ip"}
       ))
@Getter @Setter @NoArgsConstructor
public class TentativeConnexion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String username;

    @Column(name = "adresse_ip", length = 45)
    private String adresseIp;

    @Column(nullable = false)
    private Integer tentatives = 0;

    /**
     * Date/heure jusqu'à laquelle le compte est bloqué.
     * Null si pas de blocage en cours.
     */
    @Column(name = "bloque_jusqu")
    private LocalDateTime bloqueJusqu;

    @Column(name = "derniere_tentative")
    private LocalDateTime derniereTentative;

    @PreUpdate @PrePersist
    protected void onUpdate() {
        this.derniereTentative = LocalDateTime.now();
    }

    // ── Propriétés calculées (équivalent @property Django) ─────────────────

    /**
     * True si le compte est actuellement bloqué.
     * Équivalent de {@code est_bloque} (property Django).
     */
    public boolean isEstBloque() {
        return bloqueJusqu != null && LocalDateTime.now().isBefore(bloqueJusqu);
    }

    /**
     * Secondes restantes avant déblocage (0 si non bloqué).
     * Équivalent de {@code temps_restant} (property Django).
     */
    public long getTempsRestant() {
        if (!isEstBloque()) return 0L;
        long secondes = java.time.Duration.between(LocalDateTime.now(), bloqueJusqu).getSeconds();
        return Math.max(secondes, 0L);
    }

    // ── Constructeur de commodité ──────────────────────────────────────────

    public TentativeConnexion(String username, String adresseIp) {
        this.username   = username;
        this.adresseIp  = adresseIp;
        this.tentatives = 0;
    }

    @Override
    public String toString() {
        return username + " (" + adresseIp + ") — " + tentatives + " tentative(s)";
    }
}
