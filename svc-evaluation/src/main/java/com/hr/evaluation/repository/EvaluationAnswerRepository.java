package com.hr.evaluation.repository;

import com.hr.evaluation.entity.EvaluationAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationAnswerRepository extends JpaRepository<EvaluationAnswer, UUID> {

    List<EvaluationAnswer> findByEvaluationIdOrderByQuestionOrdreAsc(UUID evaluationId);

    Optional<EvaluationAnswer> findByEvaluationIdAndQuestionId(UUID evaluationId, UUID questionId);

    List<EvaluationAnswer> findByEvaluationIdAndReponseCollaborateurIsNotNull(UUID evaluationId);

    List<EvaluationAnswer> findByEvaluationIdAndReponseManagerIsNotNull(UUID evaluationId);
}
