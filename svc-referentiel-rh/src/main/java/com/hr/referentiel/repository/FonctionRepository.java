package com.hr.referentiel.repository;

import com.hr.referentiel.entity.Fonction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FonctionRepository extends JpaRepository<Fonction, UUID> {

	List<Fonction> findAllByOrderByLibelleAsc();

	List<Fonction> findAllByActifTrueOrderByLibelleAsc();

	Optional<Fonction> findByCode(String code);

	boolean existsByCode(String code);

	boolean existsByLibelleNormalise(String libelleNormalise);

	boolean existsByLibelleNormaliseAndIdNot(String libelleNormalise, UUID id);
}
