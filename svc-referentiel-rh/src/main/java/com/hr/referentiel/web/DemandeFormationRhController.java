package com.hr.referentiel.web;

import com.hr.referentiel.domain.StatutDemandeFormationRh;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.service.DemandeFormationRhService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rh/v1/demandes-formations")
public class DemandeFormationRhController {

	private final DemandeFormationRhService demandeFormationRhService;

	public DemandeFormationRhController(DemandeFormationRhService demandeFormationRhService) {
		this.demandeFormationRhService = demandeFormationRhService;
	}

	@PostMapping
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<DemandeFormationRhResponse> creer(
			@Valid @RequestBody DemandeFormationCreationRequest requete,
			JwtAuthenticationToken principal) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(demandeFormationRhService.creer(requete, principal.getToken()));
	}

	@GetMapping
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<List<DemandeFormationRhResponse>> mesDemandes(JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeFormationRhService.mesDemandes(principal.getToken()));
	}

	@GetMapping("/liste")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<List<DemandeFormationRhResponse>> listerPourRh(
			@RequestParam(name = "statut", required = false) StatutDemandeFormationRh statut) {
		return ResponseEntity.ok(demandeFormationRhService.listerPourRh(statut));
	}

	@GetMapping("/cibles/unites")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<List<UniteResponse>> unitesCibles(JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeFormationRhService.unitesCibles(principal.getToken()));
	}

	@GetMapping("/cibles/collaborateurs")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<List<FormationCollaborateurCibleResponse>> collaborateursCibles(
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeFormationRhService.collaborateursCibles(principal.getToken()));
	}

	@PostMapping("/{identifiant}/integrer-plan")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<DemandeFormationRhResponse> integrerPlan(
			@PathVariable UUID identifiant,
			@RequestBody(required = false) DemandeFormationIntegrationRequest requete) {
		return ResponseEntity.ok(demandeFormationRhService.integrerPlan(identifiant, requete));
	}

	@PostMapping("/{identifiant}/refuser")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<DemandeFormationRhResponse> refuser(
			@PathVariable UUID identifiant,
			@Valid @RequestBody DemandeRefusRequest requete) {
		return ResponseEntity.ok(demandeFormationRhService.refuser(identifiant, requete));
	}

	@PostMapping("/{identifiant}/annuler")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<DemandeFormationRhResponse> annuler(
			@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeFormationRhService.annuler(identifiant, principal.getToken()));
	}

	@GetMapping("/{identifiant}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<DemandeFormationRhResponse> obtenir(
			@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeFormationRhService.obtenir(
				identifiant, principal.getToken(), ReferentielApiSecurity.aAutoriteRh()));
	}

	@GetMapping("/{identifiant}/historique")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<List<FormationWorkflowHistoryResponse>> historique(
			@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeFormationRhService.obtenirHistorique(
				identifiant, principal.getToken(), ReferentielApiSecurity.aAutoriteRh()));
	}

	@GetMapping("/mes-demandes-ro")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<List<DemandeFormationRhResponse>> mesDemandesRo(JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeFormationRhService.mesDemandesEnTantQueRo(principal.getToken()));
	}

	@GetMapping("/formations-cible")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<List<DemandeFormationRhResponse>> formationsOuJeSuisCible(JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeFormationRhService.formationsOuJeSuisCible(principal.getToken()));
	}
}
