package com.hr.referentiel.repository;

import com.hr.referentiel.domain.RhEvenementTraite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RhEvenementTraiteRepository extends JpaRepository<RhEvenementTraite, UUID> {
}
