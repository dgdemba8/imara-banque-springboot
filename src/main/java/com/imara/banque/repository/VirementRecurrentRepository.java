package com.imara.banque.repository;

import com.imara.banque.model.Compte;
import com.imara.banque.model.User;
import com.imara.banque.model.VirementRecurrent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Équivalent de {@code VirementRecurrent.objects.filter(...)} Django.
 */
@Repository
public interface VirementRecurrentRepository extends JpaRepository<VirementRecurrent, Long> {

    /** Tous les virements récurrents d'un utilisateur. */
    List<VirementRecurrent> findByUtilisateurOrderByProchaineExecution(User utilisateur);

    /** Pour le relevé PDF : virements depuis un compte donné. */
    List<VirementRecurrent> findByUtilisateurAndCompteSourceOrderByProchaineExecution(
        User utilisateur, Compte compteSource
    );

    /**
     * Virements actifs dont l'exécution est due aujourd'hui ou dans le passé.
     * Équivalent Django :
     *   VirementRecurrent.objects.filter(statut='actif',
     *                                     prochaine_execution__lte=aujourd_hui)
     */
    @Query("""
        SELECT v FROM VirementRecurrent v
        WHERE v.statut = 'actif'
          AND v.prochaineExecution <= :date
        """)
    List<VirementRecurrent> findDus(@Param("date") LocalDate date);

    /** Recherche par ID avec vérification du propriétaire. */
    java.util.Optional<VirementRecurrent> findByIdAndUtilisateur(Long id, User utilisateur);
}
