package com.hr.presence.repository;

import com.hr.presence.entity.PresenceParametreValeur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PresenceParametreValeurRepository extends JpaRepository<PresenceParametreValeur, UUID> {

	List<PresenceParametreValeur> findByReferentielOrderByOrdreAscCodeAsc(String referentiel);

	Optional<PresenceParametreValeur> findByReferentielAndCode(String referentiel, String code);
}
