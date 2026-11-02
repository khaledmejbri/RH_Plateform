package com.hr.presence.repository;

import com.hr.presence.entity.PresenceHeuresSupBeneficiaire;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PresenceHeuresSupBeneficiaireRepository extends JpaRepository<PresenceHeuresSupBeneficiaire, UUID> {

	List<PresenceHeuresSupBeneficiaire> findByDemandeId(UUID demandeId);

	List<PresenceHeuresSupBeneficiaire> findByDemandeIdIn(Collection<UUID> demandeIds);
}
