package com.hr.referentiel.web;

import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.service.ReferentielRhService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/referentiel/v1")
public class ReferentielController {

	private final ReferentielRhService referentielRhService;
	private final com.hr.referentiel.service.CollaborateurConnecteService collaborateurConnecteService;

	public ReferentielController(ReferentielRhService referentielRhService, 
			com.hr.referentiel.service.CollaborateurConnecteService collaborateurConnecteService) {
		this.referentielRhService = referentielRhService;
		this.collaborateurConnecteService = collaborateurConnecteService;
	}

	@GetMapping("/unites")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<List<UniteResponse>> listerUnitesActives() {
		return ResponseEntity.ok(referentielRhService.listerUnitesActives());
	}

	@GetMapping("/unites/{identifiant}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<Object> obtenirUnite(@PathVariable UUID identifiant) {
		return referentielRhService.obtenirUnite(identifiant)
				.<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("erreur", "Unité introuvable.")));
	}

	@PostMapping("/unites")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<UniteResponse> creerUnite(@Valid @RequestBody UniteCreationRequest requete) {
		return ResponseEntity.status(HttpStatus.CREATED).body(referentielRhService.creerUnite(requete));
	}

	@PutMapping("/unites/{identifiant}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<Object> mettreAJourUnite(@PathVariable UUID identifiant,
			@Valid @RequestBody UniteMiseAJourRequest requete) {
		return referentielRhService.mettreAJourUnite(identifiant, requete)
				.<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("erreur", "Unité introuvable.")));
	}

	@GetMapping("/collaborateurs")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<PageReferentielResponse<CollaborateurResponse>> listerCollaborateurs(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int taille,
			@RequestParam(required = false) String statut,
			@RequestParam(name = "unite_identifiant", required = false) UUID uniteIdentifiant) {
		return ResponseEntity.ok(
				referentielRhService.listerCollaborateurs(page, taille, statut, uniteIdentifiant));
	}

	@GetMapping("/collaborateurs/moi")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<CollaborateurResponse> obtenirMoi(
			org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken principal) {
		return ResponseEntity.ok(referentielRhService.obtenirMonCollaborateur(principal.getToken()));
	}

	@GetMapping("/collaborateurs/{identifiant}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<Object> obtenirCollaborateur(@PathVariable UUID identifiant) {
		return referentielRhService.obtenirCollaborateur(identifiant)
				.<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("erreur", "Collaborateur introuvable.")));
	}

	@GetMapping("/collaborateurs/matricule/{matricule}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<Object> obtenirCollaborateurParMatricule(@PathVariable String matricule) {
		return referentielRhService.obtenirCollaborateurParMatricule(matricule)
				.<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("erreur", "Collaborateur introuvable.")));
	}

	@PostMapping("/collaborateurs")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<CollaborateurResponse> creerCollaborateur(
			@Valid @RequestBody CollaborateurCreationRequest requete) {
		return ResponseEntity.status(HttpStatus.CREATED).body(referentielRhService.creerCollaborateur(requete));
	}

	@PutMapping("/collaborateurs/{identifiant}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<Object> mettreAJourCollaborateur(@PathVariable UUID identifiant,
			@Valid @RequestBody CollaborateurMiseAJourRequest requete) {
		return referentielRhService.mettreAJourCollaborateur(identifiant, requete)
				.<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("erreur", "Collaborateur introuvable.")));
	}
}
