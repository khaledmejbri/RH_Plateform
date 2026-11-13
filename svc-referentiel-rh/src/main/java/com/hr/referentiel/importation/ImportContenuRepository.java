package com.hr.referentiel.importation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImportContenuRepository extends JpaRepository<ImportContenu, UUID> {
}
