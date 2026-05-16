package com.hr.evaluation.repository;

import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.domain.TypeEvaluationRh;
import com.hr.evaluation.entity.EvaluationRh;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvaluationRhRepository extends JpaRepository<EvaluationRh, UUID> {

	List<EvaluationRh> findByCollaborateurIdentifiantOrderByCreeLeDesc(UUID collaborateurIdentifiant);

	List<EvaluationRh> findBySuperieurIdentifiantOrderByCreeLeDesc(UUID superieurIdentifiant);

	List<EvaluationRh> findByTypeAndStatutOrderByCreeLeDesc(TypeEvaluationRh type, StatutEvaluationRh statut);

	List<EvaluationRh> findByTypeOrderByCreeLeDesc(TypeEvaluationRh type);

	List<EvaluationRh> findByStatutOrderByCreeLeDesc(StatutEvaluationRh statut);

	List<EvaluationRh> findAllByOrderByCreeLeDesc();
}
