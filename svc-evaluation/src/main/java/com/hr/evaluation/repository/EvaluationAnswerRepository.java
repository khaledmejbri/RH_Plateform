package com.hr.evaluation.repository;

import com.hr.evaluation.entity.EvaluationAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationAnswerRepository extends JpaRepository<EvaluationAnswer, UUID> {

    List<EvaluationAnswer> findByEvaluationIdOrderByQuestionOrdreAsc(UUID evaluationId);

    Optional<EvaluationAnswer> findByEvaluationIdAndQuestionId(UUID evaluationId, UUID questionId);

    List<EvaluationAnswer> findByEvaluationIdAndReponseCollaborateurIsNotNull(UUID evaluationId);

    List<EvaluationAnswer> findByEvaluationIdAndReponseManagerIsNotNull(UUID evaluationId);

    /** Réponses avec leur question chargée (open-in-view désactivé) : détection d'écart et analyse web. */
    @Query("select a from EvaluationAnswer a join fetch a.question q where a.evaluation.id = :evaluationId order by q.ordre asc")
    List<EvaluationAnswer> findWithQuestionByEvaluationId(UUID evaluationId);
}
