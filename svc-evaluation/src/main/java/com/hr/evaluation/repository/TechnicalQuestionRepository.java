package com.hr.evaluation.repository;

import com.hr.evaluation.entity.TechnicalQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TechnicalQuestionRepository extends JpaRepository<TechnicalQuestion, UUID> {

    List<TechnicalQuestion> findByTemplateIdAndActifTrueOrderByOrdreAsc(UUID templateId);

    void deleteByTemplateId(UUID templateId);
}
