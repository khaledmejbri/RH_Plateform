package com.hr.evaluation.repository;

import com.hr.evaluation.entity.TechnicalTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TechnicalTemplateRepository extends JpaRepository<TechnicalTemplate, UUID> {

    List<TechnicalTemplate> findByActifTrueOrderByNameAsc();

    Optional<TechnicalTemplate> findFirstByNiveauSenioriteAndRoleMetierAndActifTrue(
            String niveauSeniorite, 
            String roleMetier);

    List<TechnicalTemplate> findByNiveauSenioriteAndActifTrue(String niveauSeniorite);

    List<TechnicalTemplate> findByRoleMetierAndActifTrue(String roleMetier);
}
