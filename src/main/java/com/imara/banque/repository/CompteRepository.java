package com.imara.banque.repository;

import com.imara.banque.model.Compte;
import com.imara.banque.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Équivalent de {@code Compte.objects.filter(...)} Django.
 */
@Repository
public interface CompteRepository extends JpaRepository<Compte, Long> {

    /** Équivalent : Compte.objects.filter(utilisateur=user, actif=True) */
    List<Compte> findByUtilisateurAndActifTrue(User utilisateur);

    /** Équivalent : Compte.objects.get(numero_compte=...) */
    Optional<Compte> findByNumeroCompte(String numeroCompte);

    /** Équivalent : get_object_or_404(Compte, id=id, utilisateur=user, actif=True) */
    Optional<Compte> findByIdAndUtilisateurAndActifTrue(Long id, User utilisateur);

    boolean existsByNumeroCompte(String numeroCompte);
}
