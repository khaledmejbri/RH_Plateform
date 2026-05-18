package com.hr.evaluation.repository;

import com.hr.evaluation.entity.EvaluationQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvaluationQuestionRepository extends JpaRepository<EvaluationQuestion, UUID> {

    List<EvaluationQuestion> findByTemplateIdAndActifTrueOrderByOrdreAsc(UUID templateId);

    void deleteByTemplateId(UUID templateId);
}
