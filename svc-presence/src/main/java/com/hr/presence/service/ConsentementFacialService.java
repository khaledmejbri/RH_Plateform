package com.hr.presence.service;

import com.hr.presence.domain.ActionAuditFacial;
import com.hr.presence.dto.FacialAdminDtos.ConsentementPublicationRequest;
import com.hr.presence.dto.FacialAdminDtos.ConsentementVersionDetail;
import com.hr.presence.dto.FacialAdminDtos.ConsentementVersionResume;
import com.hr.presence.dto.FacialMobileDtos.ConsentementCourant;
import com.hr.presence.entity.PresenceConsentementVersion;
import com.hr.presence.repository.ConsentementVersionRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PresenceMetierException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/** Texte de consentement versionné (D-P07.h) : publié par RH / ADMIN, servi au mobile, jamais modifié ni supprimé. */
@Service
public class ConsentementFacialService {

	private final ConsentementVersionRepository repository;
	private final FacialAuditService auditService;
	private final PresenceAccessService accessService;
	private final Clock clock;

	public ConsentementFacialService(
			ConsentementVersionRepository repository,
			FacialAuditService auditService,
			PresenceAccessService accessService,
			Clock clock) {
		this.repository = repository;
		this.auditService = auditService;
		this.accessService = accessService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Optional<PresenceConsentementVersion> courante() {
		return repository.findFirstByOrderByPublieLeDescVersionDesc();
	}

	@Transactional(readOnly = true)
	public ConsentementCourant courantePourMobile() {
		PresenceConsentementVersion v = courante().orElseThrow(() ->
				PresenceMetierException.notFound("AUCUNE_VERSION_PUBLIEE", "Aucun texte de consentement n'est publié"));
		return new ConsentementCourant(v.getVersion(), v.getTexte(), v.getPublieLe());
	}

	@Transactional(readOnly = true)
	public List<ConsentementVersionResume> lister() {
		List<PresenceConsentementVersion> versions = repository.findAllByOrderByPublieLeDescVersionDesc();
		String courante = versions.isEmpty() ? null : versions.get(0).getVersion();
		return versions.stream()
				.map(v -> new ConsentementVersionResume(v.getVersion(), v.getPublieLe(), v.getPublieParCompteId(),
						v.getVersion().equals(courante)))
				.toList();
	}

	@Transactional(readOnly = true)
	public ConsentementVersionDetail lire(String version) {
		PresenceConsentementVersion v = repository.findById(version).orElseThrow(() ->
				PresenceMetierException.notFound("VERSION_INTROUVABLE", "Version de consentement introuvable"));
		return new ConsentementVersionDetail(v.getVersion(), v.getTexte(), v.getPublieLe(), v.getPublieParCompteId());
	}

	@Transactional
	public ConsentementVersionResume publier(ConsentementPublicationRequest requete) {
		String acteur = accessService.currentCompteId();
		if (acteur == null) {
			throw new SecurityException("Impossible d'identifier le compte courant");
		}
		String version = requete.version().trim();
		if (repository.existsById(version)) {
			throw PresenceMetierException.conflict("VERSION_EXISTANTE", "Cette version du texte existe déjà");
		}
		PresenceConsentementVersion publiee = new PresenceConsentementVersion(
				version, requete.texte(), acteur, clock.instant());
		try {
			repository.saveAndFlush(publiee);
		} catch (DataIntegrityViolationException e) {
			throw PresenceMetierException.conflict("VERSION_EXISTANTE", "Cette version du texte existe déjà");
		}
		auditService.tracer(ActionAuditFacial.CONSENTEMENT_PUBLIE, null, null, null, null, version,
				"Nouvelle version du texte publiée");
		return new ConsentementVersionResume(version, publiee.getPublieLe(), acteur, true);
	}
}
