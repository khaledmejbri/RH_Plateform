package com.hr.referentiel.repository;

import com.hr.referentiel.entity.SocieteParametres;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SocieteParametresRepository extends JpaRepository<SocieteParametres, UUID> {

	Optional<SocieteParametres> findFirstByOrderByIdAsc();
}
