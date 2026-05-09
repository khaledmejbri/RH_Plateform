package com.hr.referentiel.web;

import com.hr.referentiel.domain.StatutPlainteRh;
import com.hr.referentiel.domain.TypePlainteRh;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.service.PlainteRhService;
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
@RequestMapping("/api/rh/v1/plaintes")
public class PlainteRhController {

	private final PlainteRhService plainteRhService;

	public PlainteRhController(PlainteRhService plainteRhService) {
		this.plainteRhService = plainteRhService;
	}

	/** CDC §M04 : créer une plainte (interne ou externe). Notification selon type. */
	@PostMapping
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<PlainteRhResponse> creer(
			@Valid @RequestBody PlainteRhCreationRequest requete,
			JwtAuthenticationToken principal) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(plainteRhService.creer(requete, principal.getToken()));
	}

	/** Mes plaintes (collaborateur connecté). */
	@GetMapping
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<List<PlainteRhResponse>> mesPlaintes(JwtAuthenticationToken principal) {
		return ResponseEntity.ok(plainteRhService.mesPlaintes(principal.getToken()));
	}

	/**
	 * Liste RH avec filtres type et statut.
	 * CDC §M04 : tableau de bord inter-services pour plaintes externes.
	 */
	@GetMapping("/liste")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<List<PlainteRhResponse>> listerPourRh(
			@RequestParam(name = "type_plainte", required = false) TypePlainteRh type,
			@RequestParam(name = "statut", required = false) StatutPlainteRh statut) {
		return ResponseEntity.ok(plainteRhService.listerToutPourRh(type, statut));
	}

	/**
	 * Mise à jour statut avec validation des transitions autorisées.
	 * CDC §M04 : transitions NOUVEAU→EN_ANALYSE→EN_TRAITEMENT→RESOLU→FERME uniquement.
	 */
	@PatchMapping("/{identifiant}/statut")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<PlainteRhResponse> mettreAJourStatut(
			@PathVariable UUID identifiant,
			@Valid @RequestBody PlainteRhStatutMiseAJourRequest requete,
			JwtAuthenticationToken principal) {
		UUID acteurId = extraireCollaborateurId(principal.getToken());
		return ResponseEntity.ok(plainteRhService.mettreAJourStatut(identifiant, requete, acteurId));
	}

	@GetMapping("/{identifiant}/suivi")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<PlainteSuiviResponse> suivi(
			@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(
				plainteRhService.suivi(identifiant, principal.getToken(), ReferentielApiSecurity.aAutoriteRh()));
	}

	@GetMapping("/{identifiant}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<PlainteRhResponse> obtenir(
			@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(
				plainteRhService.obtenir(identifiant, principal.getToken(), ReferentielApiSecurity.aAutoriteRh()));
	}

	private static UUID extraireCollaborateurId(Jwt jwt) {
		try {
			String sub = jwt.getSubject();
			return sub != null ? UUID.fromString(sub) : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
