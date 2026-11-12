package com.hr.evaluation.repository;

import com.hr.evaluation.domain.QuestionType;
import com.hr.evaluation.entity.EvaluationQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvaluationQuestionRepository extends JpaRepository<EvaluationQuestion, UUID> {

    List<EvaluationQuestion> findByTemplateIdAndActifTrueOrderByOrdreAsc(UUID templateId);

    void deleteByTemplateId(UUID templateId);

    /** Utilisations d'un type de question (écran Paramètres, ADR lot 3 § 6). */
    long countByTypeQuestion(QuestionType typeQuestion);
}
