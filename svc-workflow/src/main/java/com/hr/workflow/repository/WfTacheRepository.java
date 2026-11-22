package com.hr.workflow.repository;

import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.entity.WfTache;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WfTacheRepository extends JpaRepository<WfTache, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT t FROM WfTache t WHERE t.id = :id")
	Optional<WfTache> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
			SELECT t FROM WfTache t
			JOIN FETCH t.instance i
			JOIN FETCH i.modeleVersion v
			JOIN FETCH t.etapeExecution e
			WHERE t.id = :id
			""")
	Optional<WfTache> findByIdAvecDetails(@Param("id") UUID id);

	List<WfTache> findByInstanceId(UUID instanceId);

	List<WfTache> findByInstanceIdAndStatutIn(UUID instanceId, Collection<StatutTache> statuts);

	List<WfTache> findByInstanceIdInAndStatutIn(Collection<UUID> instanceIds, Collection<StatutTache> statuts);

	List<WfTache> findByEtapeExecutionId(UUID etapeExecutionId);

	Optional<WfTache> findByCleIdempotence(String cleIdempotence);

	@Query(value = """
			SELECT t FROM WfTache t
			JOIN FETCH t.instance i
			JOIN FETCH t.etapeExecution e
			WHERE t.statut = com.hr.workflow.domain.StatutTache.A_TRAITER
			  AND i.statut IN (com.hr.workflow.domain.StatutInstance.EN_COURS, com.hr.workflow.domain.StatutInstance.BLOQUEE)
			  AND EXISTS (
			    SELECT 1 FROM WfTacheCandidat c
			    WHERE c.id.tacheId = t.id
			      AND c.compteId = :compteId
			      AND c.retireLe IS NULL
			  )
			ORDER BY t.echeanceLe ASC NULLS LAST, t.id ASC
			""",
			countQuery = """
			SELECT COUNT(t) FROM WfTache t
			WHERE t.statut = com.hr.workflow.domain.StatutTache.A_TRAITER
			  AND t.instance.statut IN (com.hr.workflow.domain.StatutInstance.EN_COURS, com.hr.workflow.domain.StatutInstance.BLOQUEE)
			  AND EXISTS (
			    SELECT 1 FROM WfTacheCandidat c
			    WHERE c.id.tacheId = t.id
			      AND c.compteId = :compteId
			      AND c.retireLe IS NULL
			  )
			""")
	Page<WfTache> findATraiterPourCompte(@Param("compteId") UUID compteId, Pageable pageable);

	@Query(value = """
			SELECT t FROM WfTache t
			JOIN FETCH t.instance i
			JOIN FETCH t.etapeExecution e
			WHERE t.statut = com.hr.workflow.domain.StatutTache.TRAITEE
			  AND t.decideParCompteId = :compteId
			  AND t.decideLe >= :depuis
			ORDER BY t.decideLe DESC, t.id DESC
			""",
			countQuery = """
			SELECT COUNT(t) FROM WfTache t
			WHERE t.statut = com.hr.workflow.domain.StatutTache.TRAITEE
			  AND t.decideParCompteId = :compteId
			  AND t.decideLe >= :depuis
			""")
	Page<WfTache> findTraiteesPourCompte(
			@Param("compteId") UUID compteId,
			@Param("depuis") Instant depuis,
			Pageable pageable);

	@Query("""
			SELECT COUNT(t) FROM WfTache t
			WHERE t.statut = com.hr.workflow.domain.StatutTache.A_TRAITER
			  AND t.instance.statut IN (com.hr.workflow.domain.StatutInstance.EN_COURS, com.hr.workflow.domain.StatutInstance.BLOQUEE)
			  AND EXISTS (
			    SELECT 1 FROM WfTacheCandidat c
			    WHERE c.id.tacheId = t.id
			      AND c.compteId = :compteId
			      AND c.retireLe IS NULL
			  )
			""")
	long countATraiterPourCompte(@Param("compteId") UUID compteId);

	@Query("""
			SELECT COUNT(t) FROM WfTache t
			WHERE t.statut = com.hr.workflow.domain.StatutTache.A_TRAITER
			  AND t.instance.statut IN (com.hr.workflow.domain.StatutInstance.EN_COURS, com.hr.workflow.domain.StatutInstance.BLOQUEE)
			  AND t.echeanceLe IS NOT NULL
			  AND t.echeanceLe < :maintenant
			  AND EXISTS (
			    SELECT 1 FROM WfTacheCandidat c
			    WHERE c.id.tacheId = t.id
			      AND c.compteId = :compteId
			      AND c.retireLe IS NULL
			  )
			""")
	long countEnRetardPourCompte(@Param("compteId") UUID compteId, @Param("maintenant") Instant maintenant);

	@Query("""
			SELECT DISTINCT t.decideParCollaborateurId FROM WfTache t
			WHERE t.instance.id = :instanceId
			  AND t.decision = com.hr.workflow.domain.DecisionTache.APPROUVEE
			  AND t.decideParCollaborateurId IS NOT NULL
			""")
	List<UUID> findApprobateursCollaborateurIds(@Param("instanceId") UUID instanceId);

	@Query(value = """
			SELECT * FROM wf_tache
			WHERE statut = 'A_TRAITER'
			  AND (
			    (prochaine_relance_le IS NOT NULL AND prochaine_relance_le <= :maintenant)
			    OR (echeance_le IS NOT NULL AND echeance_le <= :maintenant AND echeance_traitee_le IS NULL)
			    OR (escalade_a_faire_le IS NOT NULL AND escalade_a_faire_le <= :maintenant AND escalade_le IS NULL)
			  )
			ORDER BY COALESCE(echeance_le, prochaine_relance_le, escalade_a_faire_le) ASC
			LIMIT :limite
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<WfTache> verrouillerLotEcheances(@Param("maintenant") Instant maintenant, @Param("limite") int limite);

	@Query("""
			SELECT t FROM WfTache t
			JOIN FETCH t.instance i
			JOIN FETCH i.modeleVersion
			JOIN FETCH t.etapeExecution ee
			WHERE t.statut = com.hr.workflow.domain.StatutTache.A_TRAITER
			  AND t.type <> com.hr.workflow.domain.TypeTache.COMPLEMENT
			  AND i.statut IN (com.hr.workflow.domain.StatutInstance.EN_COURS, com.hr.workflow.domain.StatutInstance.BLOQUEE)
			  AND EXISTS (
			    SELECT 1 FROM WfTacheCandidat c
			    WHERE c.id.tacheId = t.id
			      AND c.id.collaborateurId = :delegantId
			      AND c.origine = com.hr.workflow.domain.OrigineCandidat.TITULAIRE
			      AND c.retireLe IS NULL
			  )
			""")
	List<WfTache> findTachesOuvertesPourTitulaire(@Param("delegantId") UUID delegantId);

	@Query("""
			SELECT t FROM WfTache t
			JOIN FETCH t.instance i
			JOIN FETCH i.modeleVersion
			JOIN FETCH t.etapeExecution ee
			WHERE t.statut = com.hr.workflow.domain.StatutTache.A_TRAITER
			  AND t.type <> com.hr.workflow.domain.TypeTache.COMPLEMENT
			  AND i.statut IN (com.hr.workflow.domain.StatutInstance.EN_COURS, com.hr.workflow.domain.StatutInstance.BLOQUEE)
			""")
	List<WfTache> findTachesOuvertesAvecInstance();

	@Query("""
			SELECT COUNT(DISTINCT t.instance.id) FROM WfTache t
			WHERE t.statut = com.hr.workflow.domain.StatutTache.A_TRAITER
			  AND t.echeanceLe IS NOT NULL
			  AND t.echeanceLe < :maintenant
			  AND t.instance.statut IN (com.hr.workflow.domain.StatutInstance.EN_COURS, com.hr.workflow.domain.StatutInstance.BLOQUEE)
			""")
	long countInstancesEnRetard(@Param("maintenant") Instant maintenant);

	@Query(value = """
			SELECT i.modele_code,
			       ee.etape_code,
			       ROUND(CAST(AVG(EXTRACT(EPOCH FROM (t.decide_le - ee.active_le)) / 3600.0) AS numeric), 2) AS delai_moyen_heures,
			       COUNT(*) AS nb
			FROM wf_tache t
			JOIN wf_etape_execution ee ON ee.id = t.etape_execution_id
			JOIN wf_instance i ON i.id = t.instance_id
			WHERE t.statut = 'TRAITEE'
			  AND t.decide_le IS NOT NULL
			  AND t.decide_le >= :debut
			  AND t.decide_le < :finExclue
			GROUP BY i.modele_code, ee.etape_code
			ORDER BY i.modele_code ASC, ee.etape_code ASC
			""", nativeQuery = true)
	List<Object[]> calculerIndicateursParEtape(@Param("debut") Instant debut, @Param("finExclue") Instant finExclue);
}
