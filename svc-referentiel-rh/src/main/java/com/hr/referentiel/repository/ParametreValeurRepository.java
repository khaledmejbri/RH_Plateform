package com.hr.referentiel.repository;

import com.hr.referentiel.entity.ParametreValeur;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParametreValeurRepository extends JpaRepository<ParametreValeur, UUID> {

	List<ParametreValeur> findByReferentielOrderByOrdreAscLibelleAsc(String referentiel);

	Optional<ParametreValeur> findByReferentielAndCode(String referentiel, String code);

	Optional<ParametreValeur> findByReferentielAndLibelleNormalise(String referentiel, String libelleNormalise);

	/** Verrou d'écriture : la suppression et la marque « utilisée » s'attendent l'une l'autre (ADR § 6). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from ParametreValeur p where p.referentiel = :referentiel and p.code = :code")
	Optional<ParametreValeur> findForUpdate(@Param("referentiel") String referentiel, @Param("code") String code);

	/** Marque collante (§ 6) : posée une seule fois, par un UPDATE sur la ligne. Renvoie 1 si la marque vient d'être posée. */
	@Modifying(flushAutomatically = true, clearAutomatically = false)
	@Query("update ParametreValeur p set p.premiereUtilisationLe = :quand "
			+ "where p.referentiel = :referentiel and p.code = :code and p.premiereUtilisationLe is null")
	int marquerUtilisee(@Param("referentiel") String referentiel, @Param("code") String code,
			@Param("quand") Instant quand);
}
