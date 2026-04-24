package com.hr.referentiel.web;

import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.dto.PlainteRhCreationRequest;
import com.hr.referentiel.dto.PlainteRhResponse;
import com.hr.referentiel.dto.PlainteRhStatutMiseAJourRequest;
import com.hr.referentiel.dto.PlainteSuiviResponse;
import com.hr.referentiel.service.PlainteRhService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rh/v1/plaintes")
public class PlainteRhController {

	private final PlainteRhService plainteRhService;

	public PlainteRhController(PlainteRhService plainteRhService) {
		this.plainteRhService = plainteRhService;
	}

	@PostMapping
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<PlainteRhResponse> creer(@Valid @RequestBody PlainteRhCreationRequest requete,
			JwtAuthenticationToken principal) {
		return ResponseEntity.status(HttpStatus.CREATED).body(plainteRhService.creer(requete, principal.getToken()));
	}

	@GetMapping
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<List<PlainteRhResponse>> mesPlaintes(JwtAuthenticationToken principal) {
		return ResponseEntity.ok(plainteRhService.mesPlaintes(principal.getToken()));
	}

	@GetMapping("/liste")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<List<PlainteRhResponse>> listerPourRh() {
		return ResponseEntity.ok(plainteRhService.listerToutPourRh());
	}

	@PatchMapping("/{identifiant}/statut")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<PlainteRhResponse> mettreAJourStatut(@PathVariable UUID identifiant,
			@Valid @RequestBody PlainteRhStatutMiseAJourRequest requete) {
		return ResponseEntity.ok(plainteRhService.mettreAJourStatut(identifiant, requete));
	}

	@GetMapping("/{identifiant}/suivi")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<PlainteSuiviResponse> suivi(@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(
				plainteRhService.suivi(identifiant, principal.getToken(), ReferentielApiSecurity.aAutoriteRh()));
	}

	@GetMapping("/{identifiant}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<PlainteRhResponse> obtenir(@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		Jwt jwt = principal.getToken();
		return ResponseEntity.ok(plainteRhService.obtenir(identifiant, jwt, ReferentielApiSecurity.aAutoriteRh()));
	}
}
