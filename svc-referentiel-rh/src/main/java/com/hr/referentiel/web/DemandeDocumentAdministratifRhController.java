package com.hr.referentiel.web;

import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.service.DemandeDocumentAdministratifRhService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rh/v1/demandes-documents-administratifs")
public class DemandeDocumentAdministratifRhController {

	private final DemandeDocumentAdministratifRhService demandeDocumentAdministratifRhService;

	public DemandeDocumentAdministratifRhController(
			DemandeDocumentAdministratifRhService demandeDocumentAdministratifRhService) {
		this.demandeDocumentAdministratifRhService = demandeDocumentAdministratifRhService;
	}

	@PostMapping
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<DemandeDocumentAdministratifRhResponse> soumettre(
			@Valid @RequestBody DemandeDocumentAdministratifCreationRequest requete,
			JwtAuthenticationToken principal) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(demandeDocumentAdministratifRhService.soumettre(requete, principal.getToken()));
	}

	@GetMapping
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<List<DemandeDocumentAdministratifRhResponse>> mesDemandes(
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeDocumentAdministratifRhService.mesDemandes(principal.getToken()));
	}

	@GetMapping("/file-attente")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<List<DemandeDocumentAdministratifRhResponse>> fileAttente() {
		return ResponseEntity.ok(demandeDocumentAdministratifRhService.fileAttentePourRh());
	}

	@PostMapping("/prendre-prochaine")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<DemandeDocumentAdministratifRhResponse> prendreProchaine() {
		return ResponseEntity.ok(demandeDocumentAdministratifRhService.prendreProchaineDeLaFile());
	}

	@PostMapping("/{identifiant}/accepter")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<DemandeDocumentAdministratifRhResponse> accepter(@PathVariable UUID identifiant,
			@Valid @RequestBody DemandeDocumentDisponibleRequest requete) {
		return ResponseEntity.ok(demandeDocumentAdministratifRhService.marquerDisponible(identifiant, requete));
	}

	@PostMapping("/{identifiant}/disponible")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<DemandeDocumentAdministratifRhResponse> marquerDisponible(@PathVariable UUID identifiant,
			@Valid @RequestBody DemandeDocumentDisponibleRequest requete) {
		return ResponseEntity.ok(demandeDocumentAdministratifRhService.marquerDisponible(identifiant, requete));
	}

	@PostMapping("/{identifiant}/refuser")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<DemandeDocumentAdministratifRhResponse> refuser(@PathVariable UUID identifiant,
			@Valid @RequestBody DocumentRejetRhRequest requete) {
		return ResponseEntity.ok(demandeDocumentAdministratifRhService.rejeter(identifiant, requete));
	}

	@PostMapping("/{identifiant}/rejeter")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<DemandeDocumentAdministratifRhResponse> rejeter(@PathVariable UUID identifiant,
			@Valid @RequestBody DocumentRejetRhRequest requete) {
		return ResponseEntity.ok(demandeDocumentAdministratifRhService.rejeter(identifiant, requete));
	}

	@GetMapping("/{identifiant}/suivi")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<DemandeDocumentSuiviResponse> suivi(@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeDocumentAdministratifRhService.suivi(identifiant, principal.getToken(),
				ReferentielApiSecurity.aAutoriteRh()));
	}

	@GetMapping("/{identifiant}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<DemandeDocumentAdministratifRhResponse> obtenir(@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		Jwt jwt = principal.getToken();
		return ResponseEntity.ok(
				demandeDocumentAdministratifRhService.obtenir(identifiant, jwt, ReferentielApiSecurity.aAutoriteRh()));
	}
}
