package com.hr.presence.service;

import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.ModeQr;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.QrCredentialStatut;
import com.hr.presence.domain.VerificationFaciale;
import com.hr.presence.dto.CreatePointageRequest;
import com.hr.presence.dto.PageResponse;
import com.hr.presence.dto.PointageResponse;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceQrCredential;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceQrCredentialRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.web.PresenceMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class PresencePointageService {

	private static final Logger log = LoggerFactory.getLogger(PresencePointageService.class);

	private final PresencePointageRepository pointageRepository;
	private final PresenceSiteRepository siteRepository;
	private final PresenceQrCredentialRepository qrRepository;
	private final QrTokenService qrTokenService;
	private final HaversineService haversineService;
	private final PresenceProperties properties;
	private final PresenceQrService qrService;
	private final QrDynamiqueService qrDynamiqueService;
	private final VerificationFacialePointageService verificationFaciale;
	private final Clock clock;

	/** QR résolu : site et credential statique (null pour une borne écran), ou rejet déjà enregistré. */
	private record QrResolu(PresenceSite site, UUID credentialId, PointageResponse rejet) {
		static QrResolu rejete(PointageResponse rejet) {
			return new QrResolu(null, null, rejet);
		}
	}

	public PresencePointageService(
			PresencePointageRepository pointageRepository,
			PresenceSiteRepository siteRepository,
			PresenceQrCredentialRepository qrRepository,
			QrTokenService qrTokenService,
			HaversineService haversineService,
			PresenceProperties properties,
			PresenceQrService qrService,
			QrDynamiqueService qrDynamiqueService,
			VerificationFacialePointageService verificationFaciale,
			Clock clock) {
		this.pointageRepository = pointageRepository;
		this.siteRepository = siteRepository;
		this.qrRepository = qrRepository;
		this.qrTokenService = qrTokenService;
		this.haversineService = haversineService;
		this.properties = properties;
		this.qrService = qrService;
		this.qrDynamiqueService = qrDynamiqueService;
		this.verificationFaciale = verificationFaciale;
		this.clock = clock;
	}

	@Transactional
	public PointageResponse pointer(String collaborateurDemande, CreatePointageRequest request) {
		Instant serverTs = clock.instant();
		// Étape visage, premier temps (D-P08.a) : preuve mal formée (400), identité (403) ; puis rejeu d'idempotence, puis blocage (409).
		// Rien n'est persisté ni compté ; sans contrôle exigé (INACTIVE, ou avant la date), aucun effet.
		VerificationFacialePointageService.Preparation visage =
				verificationFaciale.preparer(collaborateurDemande, request, serverTs);
		String collaborateurId = visage.collaborateurId();

		if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
			Optional<PresencePointage> existing = pointageRepository
					.findByCollaborateurIdAndIdempotencyKey(collaborateurId, request.idempotencyKey().trim());
			if (existing.isPresent()) {
				return toResponse(existing.get());
			}
		}
		// Blocage en cours : 409 avant tout QR / GPS, mais après identité et rejeu d'idempotence.
		verificationFaciale.controlerBlocage(visage, serverTs);

		PointageType type = parseType(request.type());

		QrResolu qr = QrDynamiqueService.estDynamique(request.qrToken())
				? resoudreQrDynamique(collaborateurId, type, request, serverTs)
				: resoudreQrStatique(collaborateurId, type, request, serverTs);
		if (qr.rejet() != null) {
			return qr.rejet();
		}
		PresenceSite site = qr.site();
		UUID credentialId = qr.credentialId();

		if (!site.hasEmplacement()) {
			return persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_EMPLACEMENT_ABSENT, "Emplacement GPS du site non configuré",
					site.getId(), credentialId, null);
		}
		return verifierPosition(collaborateurId, type, request, serverTs, site, credentialId, visage);
	}

	/** Borne écran (D-P05) : signature, borne non remplacée, fenêtre de 30 s ± 1. */
	private QrResolu resoudreQrDynamique(
			String collaborateurId, PointageType type, CreatePointageRequest request, Instant serverTs) {
		QrDynamiqueService.Resultat r = qrDynamiqueService.verifier(request.qrToken(), serverTs);
		if (r.verdict() == QrDynamiqueService.Verdict.INVALIDE) {
			return QrResolu.rejete(persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_QR_INVALIDE, "Jeton QR invalide ou signature incorrecte", null, null, null));
		}
		PresenceSite site = siteRepository.findById(r.siteId()).orElse(null);
		if (site == null || !site.isActif()) {
			return QrResolu.rejete(persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_SITE_INACTIF, "Site de pointage inactif ou introuvable", r.siteId(), null, null));
		}
		if (site.getModeQr() != ModeQr.ECRAN || site.getBorneJetonHash() == null
				|| site.getBorneVersion() != r.borneVersion()) {
			return QrResolu.rejete(persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_QR_REVOQUE, "Cette borne a été remplacée ou désactivée par les RH",
					site.getId(), null, null));
		}
		if (r.verdict() == QrDynamiqueService.Verdict.EXPIRE) {
			return QrResolu.rejete(persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_QR_EXPIRE,
					"QR de la borne expiré : scannez le QR actuellement affiché à l'écran",
					site.getId(), null, null));
		}
		return new QrResolu(site, null, null);
	}

	/** QR papier statique 90 j (vague Must) ; refusé sur un site équipé d'une borne écran (D-P05). */
	private QrResolu resoudreQrStatique(
			String collaborateurId, PointageType type, CreatePointageRequest request, Instant serverTs) {
		Optional<QrTokenService.QrClaims> claimsOpt = qrTokenService.parseAndVerify(request.qrToken());
		if (claimsOpt.isEmpty()) {
			return QrResolu.rejete(persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_QR_INVALIDE, "Jeton QR invalide ou signature incorrecte",
					null, null, null));
		}

		QrTokenService.QrClaims claims = claimsOpt.get();
		Optional<PresenceQrCredential> credOpt = qrRepository.findByJti(claims.jti());
		if (credOpt.isEmpty()) {
			return QrResolu.rejete(persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_QR_INVALIDE, "Credential QR inconnu",
					claims.siteId(), null, null));
		}

		PresenceQrCredential credential = credOpt.get();
		qrService.expireIfNeeded(credential);

		if (credential.getStatut() == QrCredentialStatut.REVOQUE) {
			return QrResolu.rejete(persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_QR_REVOQUE, "Ce code QR a été révoqué",
					claims.siteId(), credential.getId(), null));
		}
		if (credential.getStatut() == QrCredentialStatut.EXPIRE
				|| claims.exp().isBefore(serverTs)
				|| credential.getValidUntil().isBefore(serverTs)) {
			if (credential.getStatut() == QrCredentialStatut.ACTIF) {
				credential.setStatut(QrCredentialStatut.EXPIRE);
				qrRepository.save(credential);
			}
			return QrResolu.rejete(persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_QR_EXPIRE, "Ce code QR a expiré",
					claims.siteId(), credential.getId(), null));
		}
		if (credential.getStatut() != QrCredentialStatut.ACTIF
				|| credential.getQrVersion() != claims.qrVersion()
				|| !credential.getSite().getId().equals(claims.siteId())) {
			return QrResolu.rejete(persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_QR_INVALIDE, "Credential QR non actif pour ce site",
					claims.siteId(), credential.getId(), null));
		}

		PresenceSite site = siteRepository.findById(claims.siteId())
				.orElse(null);
		if (site == null || !site.isActif()) {
			return QrResolu.rejete(persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_SITE_INACTIF, "Site de pointage inactif ou introuvable",
					claims.siteId(), credential.getId(), null));
		}
		if (site.getModeQr() == ModeQr.ECRAN) {
			return QrResolu.rejete(persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_QR_INVALIDE,
					"Ce site utilise une borne écran : scannez le QR affiché à l'écran",
					site.getId(), credential.getId(), null));
		}
		return new QrResolu(site, credential.getId(), null);
	}

	/** Règles GPS D-P01, communes au QR papier et à la borne écran. */
	private PointageResponse verifierPosition(String collaborateurId, PointageType type, CreatePointageRequest request,
			Instant serverTs, PresenceSite site, UUID credentialId,
			VerificationFacialePointageService.Preparation visage) {

		if (Boolean.TRUE.equals(request.positionSimulee())) {
			log.warn("Position simulée détectée collab={} siteId={} device={}",
					collaborateurId, site.getId(), request.deviceId());
			return persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_POSITION_SIMULEE,
					"Position GPS simulée détectée : désactivez toute application de localisation fictive",
					site.getId(), credentialId, null);
		}

		int rayon = site.getRayonMetres() > 0
				? site.getRayonMetres()
				: properties.getGeofence().defaultRadius(site.getTypeLieu());

		// D-P01 : précision obligatoire et au plus égale au rayon (sinon la position ne prouve rien)
		double precisionMax = Math.min(rayon, properties.getGeofence().getMaxAccuracyMetres());
		if (request.accuracyMetres() == null || request.accuracyMetres() > precisionMax) {
			String detail = request.accuracyMetres() == null
					? "précision non fournie"
					: String.format(Locale.ROOT, "±%.0f m > %.0f m", request.accuracyMetres(), precisionMax);
			return persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_PRECISION_GPS,
					"Signal GPS trop faible (" + detail + "), placez-vous à découvert et réessayez",
					site.getId(), credentialId, null);
		}

		double distance = haversineService.distanceMetres(
				site.getLatitude(), site.getLongitude(),
				request.latitude(), request.longitude());

		if (!haversineService.withinRadius(distance, rayon)) {
			return persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_HORS_ZONE,
					"Hors zone de pointage (" + String.format(Locale.ROOT, "%.1f", distance) + " m > " + rayon + " m)",
					site.getId(), credentialId, distance);
		}

		// Étape visage, second temps : QR et GPS sont valides, la table de décision tranche (D-P08, D-P10).
		VerificationFacialePointageService.Decision decision = verificationFaciale.decider(visage, request, serverTs);
		PresencePointage pointage = basePointage(collaborateurId, type, request, serverTs);
		pointage.setSiteId(site.getId());
		pointage.setQrCredentialId(credentialId);
		pointage.setStatut(PointageStatut.VALIDE);
		pointage.setDistanceMetres(distance);
		decision.appliquerA(pointage);
		PresencePointage saved = pointageRepository.save(pointage);
		log.info("Pointage {} collab={} siteId={} distance={}", saved.getStatut(), collaborateurId, site.getId(), distance);
		return toResponse(saved, decision.echecs(), decision.bloqueJusqua());
	}

	@Transactional(readOnly = true)
	public PageResponse<PointageResponse> historiqueMoi(String collaborateurId, int page, int size) {
		int pageSize = Math.clamp(size, 1, 50);
		Page<PresencePointage> result = pointageRepository.findByCollaborateurIdOrderByServerTsDesc(
				collaborateurId, PageRequest.of(Math.max(page, 0), pageSize));
		return toPage(result);
	}

	@Transactional(readOnly = true)
	public PageResponse<PointageResponse> listerAdmin(
			UUID siteId, PointageStatut statut, PointageType type, Instant from, Instant to,
			List<VerificationFaciale> verificationFaciale, int page, int size) {
		int pageSize = Math.clamp(size, 1, 50);
		Page<PresencePointage> result = pointageRepository.findAll(
				PresencePointageRepository.filtreAdmin(siteId, statut, type, from, to, verificationFaciale),
				PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "serverTs")));
		return toPage(result);
	}

	private PointageResponse persistRejet(
			String collaborateurId,
			PointageType type,
			CreatePointageRequest request,
			Instant serverTs,
			PointageStatut statut,
			String motif,
			UUID siteId,
			UUID credentialId,
			Double distance) {
		PresencePointage pointage = basePointage(collaborateurId, type, request, serverTs);
		pointage.setSiteId(siteId);
		pointage.setQrCredentialId(credentialId);
		pointage.setStatut(statut);
		pointage.setMotifRejet(motif);
		pointage.setDistanceMetres(distance);
		PresencePointage saved = pointageRepository.save(pointage);
		log.info("Pointage {} collab={} motif={}", statut, collaborateurId, motif);
		return toResponse(saved);
	}

	private PresencePointage basePointage(
			String collaborateurId,
			PointageType type,
			CreatePointageRequest request,
			Instant serverTs) {
		PresencePointage p = new PresencePointage();
		p.setCollaborateurId(collaborateurId);
		p.setType(type);
		p.setServerTs(serverTs);
		p.setClientTs(request.clientTimestamp());
		p.setLatitude(request.latitude());
		p.setLongitude(request.longitude());
		p.setAccuracyMetres(request.accuracyMetres());
		p.setPositionSimulee(request.positionSimulee());
		p.setDeviceId(request.deviceId());
		if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
			p.setIdempotencyKey(request.idempotencyKey().trim());
		}
		return p;
	}

	private static PointageType parseType(String raw) {
		if (raw == null || raw.isBlank()) {
			throw PresenceMetierException.unprocessable("TYPE_OBLIGATOIRE", "type ENTREE ou SORTIE requis");
		}
		try {
			return PointageType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw PresenceMetierException.unprocessable("TYPE_INVALIDE", "type doit être ENTREE ou SORTIE");
		}
	}

	private static PageResponse<PointageResponse> toPage(Page<PresencePointage> page) {
		List<PointageResponse> content = page.getContent().stream().map(PresencePointageService::toResponse).toList();
		return new PageResponse<>(content, page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

	static PointageResponse toResponse(PresencePointage p) {
		return toResponse(p, null, null);
	}

	/** Réponse d'un pointage ; {@code echecs} et {@code bloqueJusqua} ne sont connus qu'à la création (D-P10). */
	static PointageResponse toResponse(PresencePointage p, Integer echecs, Instant bloqueJusqua) {
		return new PointageResponse(
				p.getId(),
				p.getStatut(),
				p.getType(),
				p.getServerTs(),
				p.getSiteId(),
				p.getDistanceMetres(),
				p.getAccuracyMetres(),
				p.getMotifRejet(),
				p.getCollaborateurId(),
				p.getVerificationFaciale(),
				p.getScoreFacial() != null ? p.getScoreFacial().intValue() : null,
				p.getVivaciteReussie(),
				echecs,
				bloqueJusqua
		);
	}
}
