package com.imara.banque.repository;

import com.imara.banque.model.JournalConnexion;
import com.imara.banque.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Équivalent de {@code JournalConnexion.objects.filter(...)} Django.
 */
@Repository
public interface JournalConnexionRepository extends JpaRepository<JournalConnexion, Long> {

    /** Toutes les connexions d'un utilisateur, triées par date desc. */
    List<JournalConnexion> findByUtilisateurOrderByDateConnexionDesc(User utilisateur);

    /**
     * Compte les connexions réussies dans une fenêtre de temps.
     * Équivalent Django :
     * JournalConnexion.objects.filter(utilisateur=user, succes=True,
     *                                  date_connexion__gte=fenetre).count()
     */
    long countByUtilisateurAndSuccesAndDateConnexionAfter(
        User utilisateur,
        Boolean succes,
        LocalDateTime depuis
    );
}
