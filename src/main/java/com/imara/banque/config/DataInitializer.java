// src/main/java/com/imara/banque/config/DataInitializer.java
package com.imara.banque.config;

import com.imara.banque.model.Compte;
import com.imara.banque.model.User;
import com.imara.banque.repository.CompteRepository;
import com.imara.banque.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository    userRepo;
    private final CompteRepository  compteRepo;
    private final PasswordEncoder   passwordEncoder;

    public DataInitializer(UserRepository userRepo,
                           CompteRepository compteRepo,
                           PasswordEncoder passwordEncoder) {
        this.userRepo        = userRepo;
        this.compteRepo      = compteRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        // Ne crée l'utilisateur que si la base est vide
        if (userRepo.existsByUsername("alpha.diop")) return;

        //  Créer l'utilisateur
        User user = new User();
        user.setUsername("alpha.diop");
        user.setPassword(passwordEncoder.encode("admin123")); // BCrypt auto
        user.setFirstName("Alpha");
        user.setLastName("Diop");
        user.setEmail("alpha.diop@email.com");
        user.setIsActive(true);
        user.setIsStaff(false);
        user.setIsSuperuser(false);
        userRepo.save(user);

        //  Créer un compte courant
        Compte compteCourant = new Compte();
        compteCourant.setUtilisateur(user);
        compteCourant.setNumeroCompte("CPT0000001");
        compteCourant.setTypeCompte(Compte.TypeCompte.courant);
        compteCourant.setSolde(new BigDecimal("500000.00"));
        compteCourant.setActif(true);
        compteRepo.save(compteCourant);

        // Créer un compte épargne
        Compte compteEpargne = new Compte();
        compteEpargne.setUtilisateur(user);
        compteEpargne.setNumeroCompte("CPT0000002");
        compteEpargne.setTypeCompte(Compte.TypeCompte.epargne);
        compteEpargne.setSolde(new BigDecimal("1200000.00"));
        compteEpargne.setActif(true);
        compteRepo.save(compteEpargne);

        System.out.println("Utilisateur de test créé : alpha.diop / admin123");
        System.out.println("   CPT0000001 (courant)  — 500 000 FCFA");
        System.out.println("   CPT0000002 (épargne) — 1 200 000 FCFA");
    }
}