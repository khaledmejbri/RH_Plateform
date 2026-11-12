package com.hr.referentiel.web;

import com.hr.referentiel.dto.AffectationCollaborateurResponse;
import com.hr.referentiel.dto.CollaborateurEvaluationSnapshotResponse;
import com.hr.referentiel.dto.CollaborateurPointageResponse;
import com.hr.referentiel.dto.FicheEvaluationResponse;
import com.hr.referentiel.dto.LibellesProfilResponse;
import com.hr.referentiel.dto.MembreUniteResponse;
import com.hr.referentiel.dto.ParametreDto;
import com.hr.referentiel.dto.ValideurControleResponse;
import com.hr.referentiel.service.AffectationService;
import com.hr.referentiel.service.HierarchieService;
import com.hr.referentiel.service.FamilleMetierService;
import com.hr.referentiel.service.ParametreService;
import com.hr.referentiel.service.PerimetreValidationService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.service.ReferentielRhService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Appels de service à service sans utilisateur connecté (ex. lancement automatique d'une campagne
 * d'évaluation, SCRUM-42 ; périmètre des heures supplémentaires de svc-presence, SCRUM-50). Protégés par le jeton
 * partagé {@code app.internal-api.token} ; la gateway ne route pas {@code /internal/**}, ces endpoints ne sont donc
 * pas exposés sur internet.
 */
@RestController
@RequestMapping("/internal/referentiel")
public class InterneReferentielController {

	private final ReferentielRhService referentielRhService;
	private final PerimetreValidationService perimetreValidationService;
	private final AffectationService affectationService;
	private final FamilleMetierService familleMetierService;
	private final HierarchieService hierarchieService;
	private final ParametreService parametreService;
	private final String jetonInterne;

	public InterneReferentielController(
			ReferentielRhService referentielRhService,
			PerimetreValidationService perimetreValidationService,
			AffectationService affectationService,
			FamilleMetierService familleMetierService,
			HierarchieService hierarchieService,
			ParametreService parametreService,
			@Value("${app.internal-api.token:change-me-internal-token}") String jetonInterne) {
		this.referentielRhService = referentielRhService;
		this.perimetreValidationService = perimetreValidationService;
		this.affectationService = affectationService;
		this.familleMetierService = familleMetierService;
		this.hierarchieService = hierarchieService;
		this.parametreService = parametreService;
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

	/**
	 * Résolution de 1 à 100 collaborateurs (compte, matricule, direction, valideur D-H03). Un identifiant inconnu est
	 * absent de la réponse. Liste vide ou trop longue : 400.
	 */
	@PostMapping("/collaborateurs/resolution-pointage")
	public ResponseEntity<List<CollaborateurPointageResponse>> resoudrePointage(
			@RequestHeader(value = "X-Internal-Token", required = false) String jeton,
			@RequestBody ResolutionPointageRequest requete) {
		if (!jetonValide(jeton)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		List<UUID> ids = requete == null || requete.collaborateurIds() == null
				? List.of()
				: List.copyOf(new LinkedHashSet<>(requete.collaborateurIds()));
		if (ids.isEmpty() || ids.size() > PerimetreValidationService.RESOLUTION_MAX) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(perimetreValidationService.resoudre(ids));
	}

	/** Collaborateurs actifs dont le valideur D-H03 est la fiche {@code ficheId} (aide de saisie du RO). */
	@GetMapping("/collaborateurs/{ficheId}/perimetre-validation")
	public ResponseEntity<List<CollaborateurPointageResponse>> perimetreValidation(
			@RequestHeader(value = "X-Internal-Token", required = false) String jeton,
			@PathVariable UUID ficheId) {
		if (!jetonValide(jeton)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(perimetreValidationService.perimetreDe(ficheId));
	}

	/** Fiche d'un collaborateur pour le snapshot d'une alerte d'écart d'évaluation (SCRUM-53). 404 si inconnue. */
	@GetMapping("/collaborateurs/{ficheId}/fiche-evaluation")
	public ResponseEntity<FicheEvaluationResponse> ficheEvaluation(
			@RequestHeader(value = "X-Internal-Token", required = false) String jeton,
			@PathVariable UUID ficheId) {
		if (!jetonValide(jeton)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return referentielRhService.obtenirFicheEvaluation(ficheId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	/** Collaborateurs d'une unité et de ses sous-unités (filtre d'unité du temps de travail, D-P17.g). Unité inconnue : []. */
	@GetMapping("/unites/{uniteId}/collaborateurs")
	public ResponseEntity<List<MembreUniteResponse>> membresDeUnite(
			@RequestHeader(value = "X-Internal-Token", required = false) String jeton,
			@PathVariable UUID uniteId) {
		if (!jetonValide(jeton)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(affectationService.membresDeUnite(uniteId));
	}

	/**
	 * Affectation (direction, département, service) de 1 à 100 collaborateurs désignés par fiche ou compte.
	 * Un identifiant inconnu est absent de la réponse. Liste vide ou trop longue : 400.
	 */
	@PostMapping("/collaborateurs/affectations")
	public ResponseEntity<List<AffectationCollaborateurResponse>> affectations(
			@RequestHeader(value = "X-Internal-Token", required = false) String jeton,
			@RequestBody ResolutionPointageRequest requete) {
		if (!jetonValide(jeton)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		List<UUID> ids = requete == null || requete.collaborateurIds() == null
				? List.of()
				: List.copyOf(new LinkedHashSet<>(requete.collaborateurIds()));
		if (ids.isEmpty() || ids.size() > AffectationService.RESOLUTION_MAX) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(affectationService.affectations(ids));
	}

	/** Libellés des familles métier et des niveaux de séniorité, pour les textes de svc-evaluation (lot 2). */
	@GetMapping("/libelles-profil")
	public ResponseEntity<LibellesProfilResponse> libellesProfil(
			@RequestHeader(value = "X-Internal-Token", required = false) String jeton) {
		if (!jetonValide(jeton)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(familleMetierService.libellesProfil());
	}

	/**
	 * D-N02 : le compte compte_id (claim du JWT, lu par svc-presence) est-il le valideur D-H03 du collaborateur ?
	 * Aucune mise en cache ; inconnu, inactif ou compte sans fiche : est_valideur faux.
	 */
	@GetMapping("/collaborateurs/{collaborateurId}/valideur")
	public ResponseEntity<ValideurControleResponse> valideur(
			@RequestHeader(value = "X-Internal-Token", required = false) String jeton,
			@PathVariable UUID collaborateurId,
			@RequestParam(name = "compte_id", required = false) UUID compteId) {
		if (!jetonValide(jeton)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(hierarchieService.controlerValideur(collaborateurId, compteId));
	}

	/**
	 * Lot 3 (ADR § 6) : svc-evaluation fait valider les familles et niveaux qu'un template technique cible, et pose
	 * leur marque « déjà utilisé » (une valeur ciblée ne peut plus être supprimée). Les codes inconnus ou inactifs sont
	 * rendus dans {@code inconnus} / {@code inactifs} ; l'appelant décide (400 / 422). Jeton interne obligatoire.
	 */
	@PostMapping("/parametres/utiliser")
	public ResponseEntity<ParametreDto.UtiliserResponse> utiliserParametres(
			@RequestHeader(value = "X-Internal-Token", required = false) String jeton,
			@RequestBody ParametreDto.UtiliserRequest requete) {
		if (!jetonValide(jeton)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(parametreService.utiliser(requete == null ? null : requete.references()));
	}

	private boolean jetonValide(String jeton) {
		return jeton != null && MessageDigest.isEqual(
				jeton.getBytes(StandardCharsets.UTF_8), jetonInterne.getBytes(StandardCharsets.UTF_8));
	}

	public record ResolutionPointageRequest(@JsonProperty("collaborateur_ids") List<UUID> collaborateurIds) {
	}
}
