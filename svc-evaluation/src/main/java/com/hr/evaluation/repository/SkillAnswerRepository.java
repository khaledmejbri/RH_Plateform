package com.hr.evaluation.repository;

import com.hr.evaluation.entity.SkillAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillAnswerRepository extends JpaRepository<SkillAnswer, UUID> {

    List<SkillAnswer> findByEvaluationIdOrderByQuestionTechniqueOrdreAsc(UUID evaluationId);

    Optional<SkillAnswer> findByEvaluationIdAndQuestionTechniqueId(
            UUID evaluationId, 
            UUID questionTechniqueId);

    List<SkillAnswer> findByEvaluationIdAndNiveauAutoEvaluationIsNotNull(UUID evaluationId);

    List<SkillAnswer> findByEvaluationIdAndNiveauManagerIsNotNull(UUID evaluationId);
}
