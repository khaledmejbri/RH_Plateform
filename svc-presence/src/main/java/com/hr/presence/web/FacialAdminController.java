package com.hr.presence.web;

import com.hr.presence.domain.StatutEnrolement;
import com.hr.presence.dto.FacialAdminDtos.ConsentementPublicationRequest;
import com.hr.presence.dto.FacialAdminDtos.ConsentementVersionDetail;
import com.hr.presence.dto.FacialAdminDtos.ConsentementVersionResume;
import com.hr.presence.dto.FacialAdminDtos.DeblocageRequest;
import com.hr.presence.dto.FacialAdminDtos.EnrolementDetail;
import com.hr.presence.dto.FacialAdminDtos.EnrolementLigne;
import com.hr.presence.dto.FacialAdminDtos.ReglageRequest;
import com.hr.presence.dto.FacialAdminDtos.ReglageResponse;
import com.hr.presence.dto.PageResponse;
import com.hr.presence.service.ConsentementFacialService;
import com.hr.presence.service.EnrolementFacialAdminService;
import com.hr.presence.service.ReglageFacialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Écran « Vérification faciale » de la Plateforme RH : lecture RH / DIRECTION / ADMIN, écriture RH / ADMIN seulement.
 * Contrôleur mince : toute la logique est dans les services.
 */
@RestController
@RequestMapping("/api/rh/v1/admin/presence/verification-faciale")
public class FacialAdminController {

	private final ReglageFacialService reglageService;
	private final ConsentementFacialService consentementService;
	private final EnrolementFacialAdminService enrolementService;

	public FacialAdminController(
			ReglageFacialService reglageService,
			ConsentementFacialService consentementService,
			EnrolementFacialAdminService enrolementService) {
		this.reglageService = reglageService;
		this.consentementService = consentementService;
		this.enrolementService = enrolementService;
	}

	@GetMapping("/reglage")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public ReglageResponse reglage() {
		return reglageService.lire();
	}

	@PutMapping("/reglage")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public ReglageResponse modifierReglage(@Valid @RequestBody ReglageRequest requete) {
		return reglageService.modifier(requete);
	}

	@GetMapping("/consentements")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public List<ConsentementVersionResume> consentements() {
		return consentementService.lister();
	}

	@GetMapping("/consentements/{version}")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public ConsentementVersionDetail consentement(@PathVariable String version) {
		return consentementService.lire(version);
	}

	@PostMapping("/consentements")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<ConsentementVersionResume> publier(@Valid @RequestBody ConsentementPublicationRequest requete) {
		return ResponseEntity.status(HttpStatus.CREATED).body(consentementService.publier(requete));
	}

	@GetMapping("/enrolements")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public PageResponse<EnrolementLigne> enrolements(
			@RequestParam(required = false) StatutEnrolement statut,
			@RequestParam(required = false) String q,
			@RequestParam(name = "appareil_incompatible", required = false) Boolean appareilIncompatible,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return enrolementService.lister(statut, q, appareilIncompatible, page, size);
	}

	@GetMapping("/enrolements/{id}")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public EnrolementDetail enrolement(@PathVariable UUID id) {
		return enrolementService.detail(id);
	}

	@PostMapping("/enrolements/{id}/deblocage")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public EnrolementDetail debloquer(@PathVariable UUID id, @Valid @RequestBody DeblocageRequest requete) {
		return enrolementService.debloquer(id, requete);
	}
}
