package com.hr.presence.web;

import com.hr.presence.dto.PresenceCollaborateurDtos.JourneePresence;
import com.hr.presence.dto.PresenceCollaborateurDtos.PresenceEquipe;
import com.hr.presence.dto.PresenceCollaborateurDtos.SynthesePresence;
import com.hr.presence.service.PresenceCollaborateurService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Présence côté RH Connect (D-N02) : « mes pointages » (identité lue dans le JWT, aucun identifiant de collaborateur
 * accepté pour désigner « moi ») et fiche d'équipe réservée au valideur D-H03 du collaborateur.
 */
@RestController
@RequestMapping("/api/rh/v1/mobile/presence")
@PreAuthorize(PresenceSecurityExpressions.MOBILE_USER)
public class PresenceEquipeMobileController {

	private final PresenceCollaborateurService service;

	public PresenceEquipeMobileController(PresenceCollaborateurService service) {
		this.service = service;
	}

	@GetMapping("/pointages/me/journees")
	public List<JourneePresence> mesJournees(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
		return service.mesJournees(du, au);
	}

	@GetMapping("/pointages/me/synthese")
	public SynthesePresence maSynthese(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
		return service.maSynthese(du, au);
	}

	@GetMapping("/equipe/{collaborateurId}")
	public PresenceEquipe equipe(
			@PathVariable UUID collaborateurId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
		return service.equipe(collaborateurId, du, au);
	}
}
