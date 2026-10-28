package com.hr.presence.service;

import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.ModeQr;
import com.hr.presence.domain.QrCredentialStatut;
import com.hr.presence.domain.TypeLieu;
import com.hr.presence.dto.CreateSiteRequest;
import com.hr.presence.dto.EmplacementRequest;
import com.hr.presence.dto.PageResponse;
import com.hr.presence.dto.PatchSiteRequest;
import com.hr.presence.dto.SiteResponse;
import com.hr.presence.entity.PresenceQrCredential;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceQrCredentialRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PresenceMetierException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class PresenceSiteService {

	private final PresenceSiteRepository siteRepository;
	private final PresenceQrCredentialRepository qrRepository;
	private final PresenceProperties properties;
	private final PresenceAccessService accessService;

	public PresenceSiteService(
			PresenceSiteRepository siteRepository,
			PresenceQrCredentialRepository qrRepository,
			PresenceProperties properties,
			PresenceAccessService accessService) {
		this.siteRepository = siteRepository;
		this.qrRepository = qrRepository;
		this.properties = properties;
		this.accessService = accessService;
	}

	@Transactional(readOnly = true)
	public PageResponse<SiteResponse> lister(int page, int size) {
		int pageSize = Math.clamp(size, 1, 50);
		Page<PresenceSite> result = siteRepository.findAll(
				PageRequest.of(Math.max(page, 0), pageSize, Sort.by("libelle").ascending()));
		List<SiteResponse> content = result.getContent().stream().map(this::toResponse).toList();
		return new PageResponse<>(content, result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public SiteResponse get(UUID siteId) {
		return toResponse(requireSite(siteId));
	}

	@Transactional
	public SiteResponse create(CreateSiteRequest request) {
		String code = request.code().trim();
		if (siteRepository.existsByCodeIgnoreCase(code)) {
			throw PresenceMetierException.conflict("SITE_CODE_DUPLICATE", "Un site avec ce code existe déjà");
		}
		validateOptionalCoords(request.latitude(), request.longitude());

		PresenceSite site = new PresenceSite();
		site.setCode(code);
		site.setLibelle(request.libelle().trim());
		site.setLatitude(request.latitude());
		site.setLongitude(request.longitude());
		TypeLieu typeLieu = request.typeLieu() == null ? TypeLieu.EXTERIEUR : exigerTypeLieu(request.typeLieu());
		site.setTypeLieu(typeLieu);
		site.setRayonMetres(request.rayonMetres() != null
				? exigerRayon(request.rayonMetres())
				: properties.getGeofence().defaultRadius(typeLieu));
		site.setActif(true);
		site.setUpdatedBy(accessService.currentActorLabel());
		if (request.latitude() != null && request.longitude() != null) {
			site.setEmplacementUpdatedAt(Instant.now());
			site.setEmplacementUpdatedBy(accessService.currentActorLabel());
		}
		return toResponse(siteRepository.save(site));
	}

	@Transactional
	public SiteResponse patch(UUID siteId, PatchSiteRequest request) {
		PresenceSite site = requireSite(siteId);
		if (request.libelle() != null) {
			String libelle = request.libelle().trim();
			if (libelle.isEmpty()) {
				throw PresenceMetierException.unprocessable("SITE_LIBELLE_INVALIDE", "Le libellé ne peut pas être vide");
			}
			site.setLibelle(libelle);
		}
		if (request.actif() != null) {
			site.setActif(request.actif());
		}
		if (request.typeLieu() != null) {
			TypeLieu typeLieu = exigerTypeLieu(request.typeLieu());
			if (typeLieu != site.getTypeLieu() && request.rayonMetres() == null) {
				site.setRayonMetres(properties.getGeofence().defaultRadius(typeLieu));
			}
			site.setTypeLieu(typeLieu);
		}
		if (request.rayonMetres() != null) {
			site.setRayonMetres(exigerRayon(request.rayonMetres()));
		}
		if (request.modeQr() != null) {
			site.setModeQr(ModeQr.parse(request.modeQr()).orElseThrow(() -> PresenceMetierException.unprocessable(
					"MODE_QR_INVALIDE", "mode_qr doit être PAPIER ou ECRAN")));
		}
		site.setUpdatedBy(accessService.currentActorLabel());
		return toResponse(siteRepository.save(site));
	}

	@Transactional
	public SiteResponse updateEmplacement(UUID siteId, EmplacementRequest request) {
		PresenceSite site = requireSite(siteId);
		if (Boolean.TRUE.equals(request.positionSimulee())) {
			throw PresenceMetierException.unprocessable("POSITION_SIMULEE",
					"Position GPS simulée détectée : désactivez toute application de localisation fictive");
		}
		double precisionRequise = properties.getGeofence().anchorAccuracy(site.getTypeLieu());
		if (request.precisionMetres() > precisionRequise) {
			throw PresenceMetierException.unprocessable("PRECISION_ANCRAGE_INSUFFISANTE", String.format(Locale.ROOT,
					"Signal GPS trop faible (±%.0f m, %.0f m maximum) : placez-vous à découvert et réessayez",
					request.precisionMetres(), precisionRequise));
		}
		site.setLatitude(request.latitude());
		site.setLongitude(request.longitude());
		site.setPrecisionAncrageMetres(request.precisionMetres());
		site.setNbMesuresAncrage(request.nbMesures());
		site.setEmplacementUpdatedAt(Instant.now());
		site.setEmplacementUpdatedBy(accessService.currentActorLabel());
		site.setUpdatedBy(accessService.currentActorLabel());
		return toResponse(siteRepository.save(site));
	}

	public PresenceSite requireSite(UUID siteId) {
		return siteRepository.findById(siteId)
				.orElseThrow(() -> PresenceMetierException.notFound("SITE_NOT_FOUND", "Site introuvable"));
	}

	SiteResponse toResponse(PresenceSite site) {
		Optional<PresenceQrCredential> actif = qrRepository.findBySiteIdAndStatut(site.getId(), QrCredentialStatut.ACTIF);
		boolean qrActif = actif.isPresent();
		Integer qrVersion = actif.map(PresenceQrCredential::getQrVersion).orElse(null);
		Instant validFrom = actif.map(PresenceQrCredential::getValidFrom).orElse(null);
		Instant validUntil = actif.map(PresenceQrCredential::getValidUntil).orElse(null);
		boolean expireBientot = false;
		if (validUntil != null) {
			Instant now = Instant.now();
			Instant j30 = now.plus(30, ChronoUnit.DAYS);
			expireBientot = !validUntil.isBefore(now) && !validUntil.isAfter(j30);
		}
		return new SiteResponse(
				site.getId(),
				site.getCode(),
				site.getLibelle(),
				site.getLatitude(),
				site.getLongitude(),
				site.getRayonMetres(),
				site.getTypeLieu(),
				site.getModeQr(),
				site.getBorneJetonHash() != null,
				site.getBorneCreeLe(),
				site.getPrecisionAncrageMetres(),
				site.getNbMesuresAncrage(),
				site.getEmplacementUpdatedAt(),
				site.getEmplacementUpdatedBy(),
				site.isActif(),
				qrActif,
				qrVersion,
				validFrom,
				validUntil,
				expireBientot,
				site.getCreatedAt(),
				site.getUpdatedAt()
		);
	}

	private static TypeLieu exigerTypeLieu(String raw) {
		return TypeLieu.parse(raw).orElseThrow(() -> PresenceMetierException.unprocessable(
				"TYPE_LIEU_INVALIDE", "type_lieu doit être EXTERIEUR ou INTERIEUR"));
	}

	private int exigerRayon(int rayon) {
		int min = properties.getGeofence().getMinRadiusMetres();
		int max = properties.getGeofence().getMaxRadiusMetres();
		if (rayon < min || rayon > max) {
			throw PresenceMetierException.unprocessable(
					"RAYON_INVALIDE", "Le rayon doit être compris entre " + min + " et " + max + " m");
		}
		return rayon;
	}

	private static void validateOptionalCoords(Double lat, Double lon) {
		if (lat == null && lon == null) {
			return;
		}
		if (lat == null || lon == null) {
			throw PresenceMetierException.unprocessable(
					"EMPLACEMENT_INCOMPLET", "latitude et longitude doivent être fournies ensemble");
		}
		if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
			throw PresenceMetierException.unprocessable("EMPLACEMENT_INVALIDE", "Coordonnées GPS hors bornes WGS84");
		}
	}
}
