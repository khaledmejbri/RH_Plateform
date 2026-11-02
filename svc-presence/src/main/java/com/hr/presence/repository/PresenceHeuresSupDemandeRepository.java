package com.hr.presence.repository;

import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PresenceHeuresSupDemandeRepository extends JpaRepository<PresenceHeuresSupDemande, UUID>,
		JpaSpecificationExecutor<PresenceHeuresSupDemande> {

	List<PresenceHeuresSupDemande> findByStatutAndDebutPlageTsLessThanEqual(StatutHeuresSup statut, Instant limite);

	List<PresenceHeuresSupDemande> findByDemandeurCompteIdOrderByCreeLeDesc(String demandeurCompteId);

	List<PresenceHeuresSupDemande> findByDemandeurCompteIdAndStatutOrderByCreeLeDesc(
			String demandeurCompteId, StatutHeuresSup statut);
}
