package com.imara.banque.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Entité utilisateur.
 *
 * Remplace {@code django.contrib.auth.models.User}.
 * Implémente {@link UserDetails} pour l'intégration Spring Security.
 */
@Entity
@Table(name = "auth_user")
@Getter @Setter @NoArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 150)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 150)
    private String firstName = "";

    @Column(nullable = false, length = 150)
    private String lastName = "";

    @Column(nullable = false, length = 254)
    private String email = "";

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private Boolean isStaff = false;

    @Column(nullable = false)
    private Boolean isSuperuser = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateJoined;

    @PrePersist
    protected void onCreate() {
        this.dateJoined = LocalDateTime.now();
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (Boolean.TRUE.equals(isSuperuser)) {
            return List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_USER")
            );
        }
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override public boolean isAccountNonExpired()    { return true; }
    @Override public boolean isAccountNonLocked()     { return true; }
    @Override public boolean isCredentialsNonExpired(){ return true; }
    @Override public boolean isEnabled()              { return Boolean.TRUE.equals(isActive); }

    /**
     * Équivalent de {@code user.get_full_name()} Django.
     * Retourne "Prénom Nom" ou username si vide.
     */
    public String getFullName() {
        String full = (firstName + " " + lastName).trim();
        return full.isEmpty() ? username : full;
    }
}
