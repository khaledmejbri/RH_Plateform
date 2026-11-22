package com.hr.presence.repository;

import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PresenceHeuresSupDemandeRepository extends JpaRepository<PresenceHeuresSupDemande, UUID>,
		JpaSpecificationExecutor<PresenceHeuresSupDemande> {

	/** Verrou pessimiste (SELECT ... FOR UPDATE, attente 3 s) pour les transitions workflow et annulations. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
	@Query("select d from PresenceHeuresSupDemande d where d.id = :id")
	Optional<PresenceHeuresSupDemande> findByIdPourDecision(@Param("id") UUID id);

	/** Lot de demandes d'heures sup MOTEUR en attente de démarrage (W6, verrou non bloquant). */
	@Query(value = "SELECT id FROM presence_heures_sup_demande "
			+ "WHERE workflow_mode = 'MOTEUR' "
			+ "AND workflow_etat = 'A_DEMARRER' AND statut = 'EN_COURS' "
			+ "ORDER BY cree_le ASC LIMIT :limite FOR UPDATE SKIP LOCKED",
			nativeQuery = true)
	List<UUID> findIdsADemarrerPourReprise(@Param("limite") int limite);

	/** Détail par numéro lisible HS-AAAAMM-NNNN (D-L02). */
	java.util.Optional<PresenceHeuresSupDemande> findByNumero(String numero);

	/** Anciennes demandes : référence « HS-XXXXXXXX » tant que la reprise des numéros n'est pas passée. */
	java.util.Optional<PresenceHeuresSupDemande> findByReference(String reference);

	List<PresenceHeuresSupDemande> findByStatutAndDebutPlageTsLessThanEqual(StatutHeuresSup statut, Instant limite);

	List<PresenceHeuresSupDemande> findByDemandeurCompteIdOrderByCreeLeDesc(String demandeurCompteId);

	List<PresenceHeuresSupDemande> findByDemandeurCompteIdAndStatutOrderByCreeLeDesc(
			String demandeurCompteId, StatutHeuresSup statut);
}
