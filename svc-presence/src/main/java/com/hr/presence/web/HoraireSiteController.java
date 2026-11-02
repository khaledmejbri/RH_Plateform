package com.hr.presence.web;

import com.hr.presence.dto.HoraireDtos.HoraireHistoriqueResponse;
import com.hr.presence.dto.HoraireDtos.HoraireSiteRequest;
import com.hr.presence.dto.HoraireDtos.HoraireSiteResponse;
import com.hr.presence.dto.PageResponse;
import com.hr.presence.service.HoraireSiteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Horaire hebdomadaire d'un site (D-P11, SCRUM-48) : lecture RH / DIRECTION / ADMIN, écriture RH / ADMIN. */
@RestController
@RequestMapping("/api/rh/v1/admin/presence/sites/{siteId}/horaire")
public class HoraireSiteController {

	private final HoraireSiteService service;

	public HoraireSiteController(HoraireSiteService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public HoraireSiteResponse lire(@PathVariable UUID siteId) {
		return service.lire(siteId);
	}

	@PutMapping
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public HoraireSiteResponse remplacer(@PathVariable UUID siteId, @RequestBody HoraireSiteRequest request) {
		return service.remplacer(siteId, request);
	}

	@DeleteMapping
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Void> supprimer(@PathVariable UUID siteId) {
		service.supprimer(siteId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/historique")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public PageResponse<HoraireHistoriqueResponse> historique(
			@PathVariable UUID siteId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.historique(siteId, page, size);
	}
}
