package com.hr.evaluation.repository;

import com.hr.evaluation.entity.ParametreValeur;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParametreValeurRepository extends JpaRepository<ParametreValeur, UUID> {

    List<ParametreValeur> findByReferentielOrderByOrdreAscCodeAsc(String referentiel);

    Optional<ParametreValeur> findByReferentielAndCode(String referentiel, String code);

    /** Verrou d'écriture : suppression et marque « utilisée » se sérialisent sur la même ligne (ADR § 6). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ParametreValeur v where v.referentiel = :referentiel and v.code = :code")
    Optional<ParametreValeur> findForUpdate(String referentiel, String code);

    boolean existsByReferentielAndCode(String referentiel, String code);

    boolean existsByReferentielAndLibelleNormalise(String referentiel, String libelleNormalise);

    long countByReferentielAndActifTrue(String referentiel);

    /** Marque collante : posée une seule fois, jamais retirée ; n'incrémente pas la version de la valeur. */
    @Modifying(flushAutomatically = false, clearAutomatically = false)
    @Query("update ParametreValeur v set v.premiereUtilisationLe = :quand "
            + "where v.referentiel = :referentiel and v.code = :code and v.premiereUtilisationLe is null")
    int marquerUtilisee(String referentiel, String code, Instant quand);
}
