package com.hr.presence.service;

import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.QrCredentialStatut;
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

	public PresencePointageService(
			PresencePointageRepository pointageRepository,
			PresenceSiteRepository siteRepository,
			PresenceQrCredentialRepository qrRepository,
			QrTokenService qrTokenService,
			HaversineService haversineService,
			PresenceProperties properties,
			PresenceQrService qrService) {
		this.pointageRepository = pointageRepository;
		this.siteRepository = siteRepository;
		this.qrRepository = qrRepository;
		this.qrTokenService = qrTokenService;
		this.haversineService = haversineService;
		this.properties = properties;
		this.qrService = qrService;
	}

	@Transactional
	public PointageResponse pointer(String collaborateurId, CreatePointageRequest request) {
		if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
			Optional<PresencePointage> existing = pointageRepository
					.findByCollaborateurIdAndIdempotencyKey(collaborateurId, request.idempotencyKey().trim());
			if (existing.isPresent()) {
				return toResponse(existing.get());
			}
		}

		PointageType type = parseType(request.type());
		Instant serverTs = Instant.now();

		Optional<QrTokenService.QrClaims> claimsOpt = qrTokenService.parseAndVerify(request.qrToken());
		if (claimsOpt.isEmpty()) {
			return persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_QR_INVALIDE, "Jeton QR invalide ou signature incorrecte",
					null, null, null);
		}

		QrTokenService.QrClaims claims = claimsOpt.get();
		Optional<PresenceQrCredential> credOpt = qrRepository.findByJti(claims.jti());
		if (credOpt.isEmpty()) {
			return persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_QR_INVALIDE, "Credential QR inconnu",
					claims.siteId(), null, null);
		}

		PresenceQrCredential credential = credOpt.get();
		qrService.expireIfNeeded(credential);

		if (credential.getStatut() == QrCredentialStatut.REVOQUE) {
			return persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_QR_REVOQUE, "Ce code QR a été révoqué",
					claims.siteId(), credential.getId(), null);
		}
		if (credential.getStatut() == QrCredentialStatut.EXPIRE
				|| claims.exp().isBefore(serverTs)
				|| credential.getValidUntil().isBefore(serverTs)) {
			if (credential.getStatut() == QrCredentialStatut.ACTIF) {
				credential.setStatut(QrCredentialStatut.EXPIRE);
				qrRepository.save(credential);
			}
			return persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_QR_EXPIRE, "Ce code QR a expiré",
					claims.siteId(), credential.getId(), null);
		}
		if (credential.getStatut() != QrCredentialStatut.ACTIF
				|| credential.getQrVersion() != claims.qrVersion()
				|| !credential.getSite().getId().equals(claims.siteId())) {
			return persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_QR_INVALIDE, "Credential QR non actif pour ce site",
					claims.siteId(), credential.getId(), null);
		}

		PresenceSite site = siteRepository.findById(claims.siteId())
				.orElse(null);
		if (site == null || !site.isActif()) {
			return persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_SITE_INACTIF, "Site de pointage inactif ou introuvable",
					claims.siteId(), credential.getId(), null);
		}
		if (!site.hasEmplacement()) {
			return persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_EMPLACEMENT_ABSENT, "Emplacement GPS du site non configuré",
					site.getId(), credential.getId(), null);
		}

		if (request.accuracyMetres() != null
				&& request.accuracyMetres() > properties.getGeofence().getMaxAccuracyMetres()) {
			return persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_PRECISION_GPS, "Précision GPS insuffisante",
					site.getId(), credential.getId(), null);
		}

		double distance = haversineService.distanceMetres(
				site.getLatitude(), site.getLongitude(),
				request.latitude(), request.longitude());
		int rayon = site.getRayonMetres() > 0
				? site.getRayonMetres()
				: properties.getGeofence().getDefaultRadiusMetres();

		if (!haversineService.withinRadius(distance, rayon)) {
			return persistRejet(collaborateurId, type, request, serverTs,
					PointageStatut.REJETE_HORS_ZONE,
					"Hors zone de pointage (" + String.format(Locale.ROOT, "%.1f", distance) + " m > " + rayon + " m)",
					site.getId(), credential.getId(), distance);
		}

		PresencePointage pointage = basePointage(collaborateurId, type, request, serverTs);
		pointage.setSiteId(site.getId());
		pointage.setQrCredentialId(credential.getId());
		pointage.setStatut(PointageStatut.VALIDE);
		pointage.setDistanceMetres(distance);
		PresencePointage saved = pointageRepository.save(pointage);
		log.info("Pointage VALIDE collab={} siteId={} distance={}", collaborateurId, site.getId(), distance);
		return toResponse(saved);
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
			UUID siteId, PointageStatut statut, PointageType type, Instant from, Instant to, int page, int size) {
		int pageSize = Math.clamp(size, 1, 50);
		Page<PresencePointage> result = pointageRepository.findAll(
				PresencePointageRepository.filtreAdmin(siteId, statut, type, from, to),
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
		return new PointageResponse(
				p.getId(),
				p.getStatut(),
				p.getType(),
				p.getServerTs(),
				p.getSiteId(),
				p.getDistanceMetres(),
				p.getMotifRejet(),
				p.getCollaborateurId()
		);
	}
}
