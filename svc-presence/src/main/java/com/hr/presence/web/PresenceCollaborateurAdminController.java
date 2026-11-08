package com.hr.presence.web;

import com.hr.presence.dto.PresenceCollaborateurDtos.PresenceCollaborateur;
import com.hr.presence.dto.PresenceCollaborateurDtos.SynthesePresence;
import com.hr.presence.dto.PresenceCollaborateurDtos.SynthesePresenceSite;
import com.hr.presence.service.PresenceCollaborateurService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Présence d'un collaborateur ou d'un site pour la Plateforme RH (D-N01.b, D-P19.g) : lecture seule, RH, DIRECTION et
 * ADMIN ; tout autre rôle reçoit 403. Période : 31 jours par défaut et au plus.
 */
@RestController
@RequestMapping("/api/rh/v1/admin/presence")
public class PresenceCollaborateurAdminController {

	private final PresenceCollaborateurService service;

	public PresenceCollaborateurAdminController(PresenceCollaborateurService service) {
		this.service = service;
	}

	@GetMapping("/collaborateurs/{collaborateurId}/synthese")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public SynthesePresence synthese(
			@PathVariable UUID collaborateurId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
		return service.syntheseAdmin(collaborateurId, du, au);
	}

	@GetMapping("/collaborateurs/{collaborateurId}/presence")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public PresenceCollaborateur presence(
			@PathVariable UUID collaborateurId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
		return service.presenceAdmin(collaborateurId, du, au);
	}

	@GetMapping("/sites/{siteId}/synthese")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public SynthesePresenceSite syntheseSite(
			@PathVariable UUID siteId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
		return service.syntheseSite(siteId, du, au);
	}
}
