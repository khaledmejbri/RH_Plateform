package com.hr.presence.web;

import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.dto.CreateSiteRequest;
import com.hr.presence.dto.EmplacementRequest;
import com.hr.presence.dto.PageResponse;
import com.hr.presence.dto.PatchSiteRequest;
import com.hr.presence.dto.PointageResponse;
import com.hr.presence.dto.QrGenerateResponse;
import com.hr.presence.dto.SiteResponse;
import com.hr.presence.service.PresencePointageService;
import com.hr.presence.service.PresenceQrService;
import com.hr.presence.service.PresenceSiteService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@RestController
@RequestMapping("/api/rh/v1/admin/presence")
public class PresenceAdminController {

	private final PresenceSiteService siteService;
	private final PresenceQrService qrService;
	private final PresencePointageService pointageService;

	public PresenceAdminController(
			PresenceSiteService siteService,
			PresenceQrService qrService,
			PresencePointageService pointageService) {
		this.siteService = siteService;
		this.qrService = qrService;
		this.pointageService = pointageService;
	}

	@GetMapping("/sites")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public PageResponse<SiteResponse> listerSites(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return siteService.lister(page, size);
	}

	@PostMapping("/sites")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<SiteResponse> creerSite(@Valid @RequestBody CreateSiteRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(siteService.create(request));
	}

	@GetMapping("/sites/{siteId}")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public SiteResponse getSite(@PathVariable UUID siteId) {
		return siteService.get(siteId);
	}

	@PatchMapping("/sites/{siteId}")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public SiteResponse patchSite(@PathVariable UUID siteId, @Valid @RequestBody PatchSiteRequest request) {
		return siteService.patch(siteId, request);
	}

	@PutMapping("/sites/{siteId}/emplacement")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public SiteResponse emplacement(
			@PathVariable UUID siteId,
			@Valid @RequestBody EmplacementRequest request) {
		return siteService.updateEmplacement(siteId, request);
	}

	@PostMapping("/sites/{siteId}/qr/generate")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<QrGenerateResponse> generateQr(@PathVariable UUID siteId) {
		QrGenerateResponse body = qrService.generate(siteId);
		return ResponseEntity.status(HttpStatus.CREATED).body(body);
	}

	@GetMapping("/sites/{siteId}/qr/download")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<byte[]> downloadQr(@PathVariable UUID siteId) {
		byte[] png = qrService.downloadPng(siteId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"presence-qr-" + siteId + ".png\"")
				.contentType(MediaType.IMAGE_PNG)
				.body(png);
	}

	@PostMapping("/sites/{siteId}/revoke-qr")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Void> revokeQr(@PathVariable UUID siteId) {
		qrService.revoke(siteId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/pointages")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public PageResponse<PointageResponse> listerPointages(
			@RequestParam(required = false) UUID siteId,
			@RequestParam(required = false) PointageStatut statut,
			@RequestParam(required = false) PointageType type,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			// Filtres jour envoyés par la Plateforme RH (input date) — bornes en heure de Tunis, « au » inclus.
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Instant debut = from != null ? from : du != null ? du.atStartOfDay(ZONE_TUNIS).toInstant() : null;
		Instant fin = to != null ? to : au != null ? au.plusDays(1).atStartOfDay(ZONE_TUNIS).toInstant() : null;
		return pointageService.listerAdmin(siteId, statut, type, debut, fin, page, size);
	}

	private static final ZoneId ZONE_TUNIS = ZoneId.of("Africa/Tunis");
}
