package com.hr.referentiel.web;

import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.DemandeAdministrativeRhCreationRequest;
import com.hr.referentiel.dto.DemandeAdministrativeRhResponse;
import com.hr.referentiel.dto.DemandeAdministrativeSuiviResponse;
import com.hr.referentiel.dto.DemandeRefusRequest;
import com.hr.referentiel.service.DemandeAdministrativeRhService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

@RestController
@RequestMapping("/api/rh/v1/demandes-administratives")
public class DemandeAdministrativeRhController {

	private final DemandeAdministrativeRhService demandeAdministrativeRhService;

	public DemandeAdministrativeRhController(DemandeAdministrativeRhService demandeAdministrativeRhService) {
		this.demandeAdministrativeRhService = demandeAdministrativeRhService;
	}

	@PostMapping
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<DemandeAdministrativeRhResponse> creer(
			@Valid @RequestBody DemandeAdministrativeRhCreationRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(demandeAdministrativeRhService.creer(requete, principal.getToken()));
	}

	@GetMapping
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<List<DemandeAdministrativeRhResponse>> mesDemandes(
			@RequestParam(name = "type_demande", required = false) TypeDemandeAdministrativeRh typeDemande,
			@RequestParam(name = "couvre_jour", required = false) LocalDate couvreJour,
			@RequestParam(name = "statut", required = false) StatutDemandeAdministrativeRh statut,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeAdministrativeRhService.mesDemandes(principal.getToken(), typeDemande,
				couvreJour, statut));
	}

	@GetMapping("/liste")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<List<DemandeAdministrativeRhResponse>> listerPourRh(
			@RequestParam(name = "type_demande", required = false) TypeDemandeAdministrativeRh typeDemande,
			@RequestParam(name = "couvre_jour", required = false) LocalDate couvreJour,
			@RequestParam(name = "statut", required = false) StatutDemandeAdministrativeRh statut) {
		return ResponseEntity.ok(demandeAdministrativeRhService.listerToutPourRh(typeDemande, couvreJour, statut));
	}

	@PostMapping("/{identifiant}/valider-superieur")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<DemandeAdministrativeRhResponse> validerSuperieur(@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeAdministrativeRhService.validerSuperieur(identifiant, principal.getToken()));
	}

	@PostMapping("/{identifiant}/refuser-superieur")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<DemandeAdministrativeRhResponse> refuserSuperieur(@PathVariable UUID identifiant,
			@Valid @RequestBody DemandeRefusRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(
				demandeAdministrativeRhService.refuserSuperieur(identifiant, principal.getToken(), requete));
	}

	@PostMapping("/{identifiant}/valider-rrh")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<DemandeAdministrativeRhResponse> validerRrh(@PathVariable UUID identifiant) {
		return ResponseEntity.ok(demandeAdministrativeRhService.validerRrh(identifiant));
	}

	@PostMapping("/{identifiant}/refuser-rrh")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<DemandeAdministrativeRhResponse> refuserRrh(@PathVariable UUID identifiant,
			@Valid @RequestBody DemandeRefusRequest requete) {
		return ResponseEntity.ok(demandeAdministrativeRhService.refuserRrh(identifiant, requete));
	}

	@GetMapping("/{identifiant}/suivi")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<DemandeAdministrativeSuiviResponse> suivi(@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeAdministrativeRhService.suivi(identifiant, principal.getToken(),
				ReferentielApiSecurity.aAutoriteRh()));
	}

	@GetMapping("/{identifiant}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<DemandeAdministrativeRhResponse> obtenir(@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		Jwt jwt = principal.getToken();
		return ResponseEntity.ok(
				demandeAdministrativeRhService.obtenir(identifiant, jwt, ReferentielApiSecurity.aAutoriteRh()));
	}
}
