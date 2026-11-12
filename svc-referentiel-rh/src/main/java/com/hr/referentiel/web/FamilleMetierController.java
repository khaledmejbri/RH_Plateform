package com.hr.referentiel.web;

import com.hr.referentiel.dto.FamilleMetierCreationRequest;
import com.hr.referentiel.dto.FamilleMetierMiseAJourRequest;
import com.hr.referentiel.dto.FamilleMetierResponse;
import com.hr.referentiel.dto.NiveauSenioriteResponse;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.service.AuteurParametreResolver;
import com.hr.referentiel.service.FamilleMetierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Façade des anciens endpoints familles / niveaux sur le paramétrage générique (ADR-UAT-lot3 § 3). Les nouveaux
 * clients utilisent {@code /api/referentiel/v1/parametres} ; ce contrôleur disparaîtra au lot suivant.
 * Le {@code code} fourni à la création est ignoré : il est généré depuis le libellé.
 */
@RestController
@RequestMapping("/api/referentiel/v1")
public class FamilleMetierController {

	private final FamilleMetierService familleMetierService;
	private final AuteurParametreResolver auteurResolver;

	public FamilleMetierController(FamilleMetierService familleMetierService, AuteurParametreResolver auteurResolver) {
		this.familleMetierService = familleMetierService;
		this.auteurResolver = auteurResolver;
	}

	@GetMapping("/familles-metier")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<List<FamilleMetierResponse>> lister(
			@RequestParam(name = "actif", required = false) Boolean actif) {
		return ResponseEntity.ok(familleMetierService.lister(actif));
	}

	@PostMapping("/familles-metier")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<FamilleMetierResponse> creer(@Valid @RequestBody FamilleMetierCreationRequest requete,
			JwtAuthenticationToken principal) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(familleMetierService.creer(requete, auteurResolver.depuis(principal.getToken())));
	}

	@PutMapping("/familles-metier/{code}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<FamilleMetierResponse> mettreAJour(
			@PathVariable String code,
			@Valid @RequestBody FamilleMetierMiseAJourRequest requete,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(
				familleMetierService.mettreAJour(code, requete, auteurResolver.depuis(principal.getToken())));
	}

	@GetMapping("/niveaux-seniorite")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<List<NiveauSenioriteResponse>> niveaux() {
		return ResponseEntity.ok(familleMetierService.niveaux());
	}
}
