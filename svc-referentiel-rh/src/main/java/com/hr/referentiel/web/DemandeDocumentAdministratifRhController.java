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

/**
 * CDC v2 §M02 — Demandes de documents administratifs.
 *
 * Corrections :
 *   - Suppression du doublon /accepter (identique à /disponible) → gardé /disponible uniquement.
 *   - Suppression du doublon /rejeter (identique à /refuser)     → gardé /refuser uniquement.
 *   - SLA par type exposé dans la réponse (FEUILLE_POINTAGE_MENSUELLE = 1h, etc.).
 */
@RestController
@RequestMapping("/api/rh/v1/demandes-documents-administratifs")
public class DemandeDocumentAdministratifRhController {

	private final DemandeDocumentAdministratifRhService demandeDocumentAdministratifRhService;

	public DemandeDocumentAdministratifRhController(
			DemandeDocumentAdministratifRhService demandeDocumentAdministratifRhService) {
		this.demandeDocumentAdministratifRhService = demandeDocumentAdministratifRhService;
	}

	/** Soumettre une demande de document. */
	@PostMapping
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<DemandeDocumentAdministratifRhResponse> soumettre(
			@Valid @RequestBody DemandeDocumentAdministratifCreationRequest requete,
			JwtAuthenticationToken principal) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(demandeDocumentAdministratifRhService.soumettre(requete, principal.getToken()));
	}

	/** Mes demandes de documents. */
	@GetMapping
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<List<DemandeDocumentAdministratifRhResponse>> mesDemandes(
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeDocumentAdministratifRhService.mesDemandes(principal.getToken()));
	}

	/** File d'attente RH triée par priorité SLA. */
	@GetMapping("/file-attente")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<List<DemandeDocumentAdministratifRhResponse>> fileAttente() {
		return ResponseEntity.ok(demandeDocumentAdministratifRhService.fileAttentePourRh());
	}

	/** Prendre la prochaine demande de la file (priorité SLA). */
	@PostMapping("/prendre-prochaine")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<DemandeDocumentAdministratifRhResponse> prendreProchaine() {
		return ResponseEntity.ok(demandeDocumentAdministratifRhService.prendreProchaineDeLaFile());
	}

	/**
	 * Marquer le document comme disponible (URL S3 du PDF).
	 * Anciens alias /accepter et /disponible fusionnés ici.
	 */
	@PostMapping("/{identifiant}/disponible")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<DemandeDocumentAdministratifRhResponse> marquerDisponible(
			@PathVariable UUID identifiant,
			@Valid @RequestBody DemandeDocumentDisponibleRequest requete) {
		return ResponseEntity.ok(
				demandeDocumentAdministratifRhService.marquerDisponible(identifiant, requete));
	}

	/**
	 * Refuser une demande de document avec motif.
	 * Ancien alias /rejeter fusionné ici.
	 */
	@PostMapping("/{identifiant}/refuser")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<DemandeDocumentAdministratifRhResponse> refuser(
			@PathVariable UUID identifiant,
			@Valid @RequestBody DocumentRejetRhRequest requete) {
		return ResponseEntity.ok(demandeDocumentAdministratifRhService.rejeter(identifiant, requete));
	}

	/** Suivi visuel avec étapes workflow et SLA restant. */
	@GetMapping("/{identifiant}/suivi")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<DemandeDocumentSuiviResponse> suivi(
			@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeDocumentAdministratifRhService.suivi(
				identifiant, principal.getToken(), ReferentielApiSecurity.aAutoriteRh()));
	}

	/** Détail d'une demande de document. */
	@GetMapping("/{identifiant}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<DemandeDocumentAdministratifRhResponse> obtenir(
			@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		Jwt jwt = principal.getToken();
		return ResponseEntity.ok(demandeDocumentAdministratifRhService.obtenir(
				identifiant, jwt, ReferentielApiSecurity.aAutoriteRh()));
	}
}
