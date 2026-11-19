package com.hr.referentiel.web;

import com.hr.referentiel.dto.FonctionDtos.AffectationRequest;
import com.hr.referentiel.dto.FonctionDtos.AffectationResponse;
import com.hr.referentiel.dto.FonctionDtos.FonctionRequest;
import com.hr.referentiel.dto.FonctionDtos.FonctionResponse;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.service.FonctionAffectationService;
import com.hr.referentiel.service.FonctionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints REST de gestion des fonctions transverses par société (§ 4.3, US-05).
 */
@RestController
@RequestMapping({"/api/rh/v1/admin/fonctions", "/api/rh/v1/fonctions"})
public class FonctionController {

	private final FonctionService fonctionService;
	private final FonctionAffectationService affectationService;

	public FonctionController(FonctionService fonctionService, FonctionAffectationService affectationService) {
		this.fonctionService = fonctionService;
		this.affectationService = affectationService;
	}

	@GetMapping
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public List<FonctionResponse> listerFonctions(
			@RequestParam(name = "actifs", required = false, defaultValue = "false") boolean actifsSeulement) {
		return fonctionService.lister(actifsSeulement);
	}

	@GetMapping("/{id}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public FonctionResponse consulterFonction(@PathVariable("id") UUID id) {
		return fonctionService.obtenir(id);
	}

	@GetMapping("/{id}/affectations")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public List<AffectationResponse> listerAffectationsFonction(@PathVariable("id") UUID id) {
		return affectationService.lister(id);
	}

	@PostMapping
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<FonctionResponse> creerFonction(@Valid @RequestBody FonctionRequest payload) {
		return ResponseEntity.status(HttpStatus.CREATED).body(fonctionService.creer(payload));
	}

	@PutMapping("/{id}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public FonctionResponse modifierFonction(@PathVariable("id") UUID id, @Valid @RequestBody FonctionRequest payload) {
		return fonctionService.modifier(id, payload);
	}

	@PostMapping("/{id}/activer")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public FonctionResponse activerFonction(@PathVariable("id") UUID id) {
		return fonctionService.changerActivation(id, true);
	}

	@PostMapping("/{id}/desactiver")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public FonctionResponse desactiverFonction(@PathVariable("id") UUID id) {
		return fonctionService.changerActivation(id, false);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Void> supprimerFonction(@PathVariable("id") UUID id) {
		fonctionService.supprimer(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/affectations")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<AffectationResponse> ajouterAffectationFonction(
			@PathVariable("id") UUID id,
			@Valid @RequestBody AffectationRequest payload) {
		return ResponseEntity.status(HttpStatus.CREATED).body(affectationService.affecter(id, payload));
	}

	@DeleteMapping("/{id}/affectations/{affectationId}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Void> retirerAffectationFonction(
			@PathVariable("id") UUID id,
			@PathVariable("affectationId") UUID affectationId) {
		affectationService.retirer(id, affectationId);
		return ResponseEntity.noContent().build();
	}
}
