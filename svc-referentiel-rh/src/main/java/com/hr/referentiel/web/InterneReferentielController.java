package com.hr.referentiel.web;

import com.hr.referentiel.dto.CollaborateurEvaluationSnapshotResponse;
import com.hr.referentiel.service.ReferentielRhService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Appels de service à service sans utilisateur connecté (ex. lancement automatique d'une campagne
 * d'évaluation, SCRUM-42). Protégés par le jeton partagé {@code app.internal-api.token} ;
 * la gateway ne route pas {@code /internal/**}, ces endpoints ne sont donc pas exposés sur internet.
 */
@RestController
@RequestMapping("/internal/referentiel")
public class InterneReferentielController {

	private final ReferentielRhService referentielRhService;
	private final String jetonInterne;

	public InterneReferentielController(
			ReferentielRhService referentielRhService,
			@Value("${app.internal-api.token:change-me-internal-token}") String jetonInterne) {
		this.referentielRhService = referentielRhService;
		this.jetonInterne = jetonInterne;
	}

	@GetMapping("/collaborateurs/actifs-evaluation")
	public ResponseEntity<List<CollaborateurEvaluationSnapshotResponse>> listerActifsEvaluation(
			@RequestHeader(value = "X-Internal-Token", required = false) String jeton) {
		if (!jetonValide(jeton)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(referentielRhService.listerActifsPourEvaluation());
	}

	private boolean jetonValide(String jeton) {
		return jeton != null && MessageDigest.isEqual(
				jeton.getBytes(StandardCharsets.UTF_8), jetonInterne.getBytes(StandardCharsets.UTF_8));
	}
}
