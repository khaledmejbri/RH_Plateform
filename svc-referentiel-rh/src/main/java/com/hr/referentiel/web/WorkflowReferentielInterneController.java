package com.hr.referentiel.web;

import com.hr.referentiel.dto.WorkflowInterneDtos.AbsenceCollaborateurInterne;
import com.hr.referentiel.dto.WorkflowInterneDtos.CalendrierSocieteInterneResponse;
import com.hr.referentiel.dto.WorkflowInterneDtos.CollaborateurResumeInterne;
import com.hr.referentiel.dto.WorkflowInterneDtos.ResolutionLotRequest;
import com.hr.referentiel.dto.WorkflowInterneDtos.ResolutionLotResponse;
import com.hr.referentiel.service.WorkflowResolutionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints internes exposés par {@code svc-referentiel-rh} pour {@code svc-workflow} (§ 9.5.3).
 * Protégés par {@code SocieteFiltreInterne} ({@code X-Internal-Token} + {@code X-Societe-Id}).
 */
@RestController
@RequestMapping("/internal/referentiel/v1")
@PreAuthorize("permitAll()")
public class WorkflowReferentielInterneController {

	private final WorkflowResolutionService resolutionService;

	public WorkflowReferentielInterneController(WorkflowResolutionService resolutionService) {
		this.resolutionService = resolutionService;
	}

	@PostMapping("/workflow/resolutions")
	public ResolutionLotResponse resoudreIntervenants(@Valid @RequestBody ResolutionLotRequest body) {
		return resolutionService.resoudreLot(body);
	}

	@GetMapping("/societe/calendrier")
	public CalendrierSocieteInterneResponse calendrierSociete(
			@RequestParam(name = "du", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(name = "au", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
		return resolutionService.calendrier(du, au);
	}

	@GetMapping("/absences")
	public List<AbsenceCollaborateurInterne> absencesDuJour(
			@RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return resolutionService.absences(date);
	}

	@GetMapping("/collaborateurs/{id}/resume")
	public CollaborateurResumeInterne resumeCollaborateur(@PathVariable("id") UUID id) {
		return resolutionService.resumeParId(id);
	}

	@GetMapping("/collaborateurs/par-compte/{compteId}")
	public CollaborateurResumeInterne resumeCollaborateurParCompte(@PathVariable("compteId") UUID compteId) {
		return resolutionService.resumeParCompte(compteId);
	}
}
