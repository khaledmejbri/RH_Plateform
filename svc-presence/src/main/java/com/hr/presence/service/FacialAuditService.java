package com.hr.presence.service;

import com.hr.presence.domain.ActionAuditFacial;
import com.hr.presence.domain.StatutEnrolement;
import com.hr.presence.entity.PresenceAuditFacial;
import com.hr.presence.repository.AuditFacialRepository;
import com.hr.presence.security.PresenceAccessService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Écriture de l'audit facial (ajout seul). L'acteur est lu dans le JWT (compte) et ses rôles, jamais dans le corps
 * de la requête.
 */
@Service
public class FacialAuditService {

	private final AuditFacialRepository auditRepository;
	private final PresenceAccessService accessService;
	private final Clock clock;

	public FacialAuditService(AuditFacialRepository auditRepository, PresenceAccessService accessService, Clock clock) {
		this.auditRepository = auditRepository;
		this.accessService = accessService;
		this.clock = clock;
	}

	public void tracer(ActionAuditFacial action, UUID enrolementId, StatutEnrolement avant, StatutEnrolement apres,
			String deviceId, String version, String motif) {
		tracer(action, enrolementId, avant, apres, deviceId, version, motif, null);
	}

	public void tracer(ActionAuditFacial action, UUID enrolementId, StatutEnrolement avant, StatutEnrolement apres,
			String deviceId, String version, String motif, String acteurNom) {
		auditRepository.save(new PresenceAuditFacial(
				action, enrolementId, avant, apres,
				accessService.currentCompteId(), acteurNom, accessService.currentActorRole(),
				deviceId, version, motif, clock.instant()));
	}
}
