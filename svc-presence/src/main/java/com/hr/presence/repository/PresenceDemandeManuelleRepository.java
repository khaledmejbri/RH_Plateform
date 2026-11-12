package com.hr.presence.repository;

import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.entity.PresenceDemandeManuelle;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PresenceDemandeManuelleRepository extends JpaRepository<PresenceDemandeManuelle, UUID>,
		JpaSpecificationExecutor<PresenceDemandeManuelle> {

	/** Détail par numéro lisible PM-AAAAMM-NNNN (D-L02). */
	java.util.Optional<PresenceDemandeManuelle> findByNumero(String numero);

	/** Refus pour lesquels une demande manuelle existe déjà (éligibilité du bouton « pointage manuel »). */
	List<PresenceDemandeManuelle> findByPointageRejeteIdIn(java.util.Collection<UUID> pointageRejeteIds);

	java.util.Optional<PresenceDemandeManuelle> findByPointageRejeteId(UUID pointageRejeteId);

	/**
	 * Numéro de la demande qui a gagné une course sur la contrainte unique : lu dans une transaction à part, car celle de
	 * l'appelant est abandonnée par la base après l'erreur d'insertion.
	 */
	@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
	@Query("select d.numero from PresenceDemandeManuelle d where d.pointageRejeteId = :pointageRejeteId")
	java.util.Optional<String> numeroParPointageRejeteId(@Param("pointageRejeteId") UUID pointageRejeteId);

	/**
	 * Verrou pessimiste (SELECT ... FOR UPDATE, attente 3 s) pour valider, refuser ou expirer : le statut est relu sous
	 * verrou, une validation et une expiration simultanées ne peuvent pas toutes deux aboutir.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
	@Query("select d from PresenceDemandeManuelle d where d.id = :id")
	java.util.Optional<PresenceDemandeManuelle> findByIdPourDecision(@Param("id") UUID id);

	/** Identifiants des demandes en attente dont l'échéance est atteinte (planificateur d'expiration). */
	@Query("select d.id from PresenceDemandeManuelle d where d.statut = com.hr.presence.domain.StatutDemandeManuelle.EN_ATTENTE "
			+ "and d.echeance is not null and d.echeance <= :maintenant")
	List<UUID> idsEchues(@Param("maintenant") Instant maintenant);

	/** Demandes en attente sans échéance : à reprendre au démarrage (D-P26.a). */
	List<PresenceDemandeManuelle> findByStatutAndEcheanceIsNull(StatutDemandeManuelle statut);

	boolean existsByPointageRejeteId(UUID pointageRejeteId);

	List<PresenceDemandeManuelle> findByCollaborateurIdOrderByCreeLeDesc(String collaborateurId);

	List<PresenceDemandeManuelle> findByValideurIdAndStatutOrderByCreeLeAsc(String valideurId, StatutDemandeManuelle statut);

	List<PresenceDemandeManuelle> findAllByOrderByCreeLeDesc();

	List<PresenceDemandeManuelle> findByStatutOrderByCreeLeDesc(StatutDemandeManuelle statut);

	/** Pointages manuels validés d'un collaborateur sur une période (alerte RH, D-P04). */
	@Query("select count(d) from PresenceDemandeManuelle d where d.collaborateurId = :collab "
			+ "and d.statut = com.hr.presence.domain.StatutDemandeManuelle.VALIDEE "
			+ "and d.heureDeclaree >= :du and d.heureDeclaree < :au")
	long compterValideesSurPeriode(@Param("collab") String collaborateurId,
			@Param("du") Instant du, @Param("au") Instant au);
}
