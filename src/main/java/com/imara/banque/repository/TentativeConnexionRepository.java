package com.imara.banque.repository;

import com.imara.banque.model.TentativeConnexion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Équivalent de {@code TentativeConnexion.objects.get_or_create(...)} Django.
 */
@Repository
public interface TentativeConnexionRepository extends JpaRepository<TentativeConnexion, Long> {

    /** Équivalent : get_or_create(username=..., adresse_ip=...) */
    Optional<TentativeConnexion> findByUsernameAndAdresseIp(String username, String adresseIp);
}
