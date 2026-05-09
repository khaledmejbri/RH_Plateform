package com.hr.referentiel.web;

import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.service.DemandeAdministrativeRhService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * CDC v2 §M01 — Demandes administratives (congé, autorisation sortie, ordre mission).
 *
 * Workflow : demandeur → RO de son unité (si existe) → RRH.
 * Nouveaux endpoints :
 *   DELETE /{id}/annuler       — annulation par le demandeur (statut SOUMISE ou EN_VALIDATION_SUPERIEUR)
 *   GET    /en-attente-ro      — liste des demandes en attente de validation pour le RO connecté
 */
@RestController
@RequestMapping("/api/rh/v1/demandes-administratives")
public class DemandeAdministrativeRhController {

	private final DemandeAdministrativeRhService demandeAdministrativeRhService;

	public DemandeAdministrativeRhController(DemandeAdministrativeRhService demandeAdministrativeRhService) {
		this.demandeAdministrativeRhService = demandeAdministrativeRhService;
	}

	/** Créer une demande (congé, autorisation sortie, ordre mission). */
	@PostMapping
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<DemandeAdministrativeRhResponse> creer(
			@Valid @RequestBody DemandeAdministrativeRhCreationRequest requete,
			JwtAuthenticationToken principal) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(demandeAdministrativeRhService.creer(requete, principal.getToken()));
	}

	/** Mes demandes (collaborateur connecté) avec filtres optionnels. */
	@GetMapping
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<List<DemandeAdministrativeRhResponse>> mesDemandes(
			@RequestParam(name = "type_demande", required = false) TypeDemandeAdministrativeRh typeDemande,
			@RequestParam(name = "couvre_jour", required = false) LocalDate couvreJour,
			@RequestParam(name = "statut", required = false) StatutDemandeAdministrativeRh statut,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeAdministrativeRhService.mesDemandes(
				principal.getToken(), typeDemande, couvreJour, statut));
	}

	/**
	 * Demandes EN_VALIDATION_SUPERIEUR en attente du RO connecté.
	 * CDC §M01 : le RO voit toutes les demandes de son unité à valider.
	 */
	@GetMapping("/en-attente-ro")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<List<DemandeAdministrativeRhResponse>> enAttenteRo(
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(
				demandeAdministrativeRhService.demandesEnAttenteRo(principal.getToken()));
	}

	/** Liste RH complète avec filtres. */
	@GetMapping("/liste")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<List<DemandeAdministrativeRhResponse>> listerPourRh(
			@RequestParam(name = "type_demande", required = false) TypeDemandeAdministrativeRh typeDemande,
			@RequestParam(name = "couvre_jour", required = false) LocalDate couvreJour,
			@RequestParam(name = "statut", required = false) StatutDemandeAdministrativeRh statut) {
		return ResponseEntity.ok(demandeAdministrativeRhService.listerToutPourRh(typeDemande, couvreJour, statut));
	}

	/** Validation par le RO (Responsable Opérationnel) de l'unité du demandeur. */
	@PostMapping("/{identifiant}/valider-superieur")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<DemandeAdministrativeRhResponse> validerSuperieur(
			@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(
				demandeAdministrativeRhService.validerSuperieur(identifiant, principal.getToken()));
	}

	/** Refus par le RO avec motif obligatoire. */
	@PostMapping("/{identifiant}/refuser-superieur")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<DemandeAdministrativeRhResponse> refuserSuperieur(
			@PathVariable UUID identifiant,
			@Valid @RequestBody DemandeRefusRequest requete,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(
				demandeAdministrativeRhService.refuserSuperieur(identifiant, principal.getToken(), requete));
	}

	/** Approbation finale RRH. */
	@PostMapping("/{identifiant}/valider-rrh")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<DemandeAdministrativeRhResponse> validerRrh(@PathVariable UUID identifiant) {
		return ResponseEntity.ok(demandeAdministrativeRhService.validerRrh(identifiant));
	}

	/** Refus RRH avec motif obligatoire. */
	@PostMapping("/{identifiant}/refuser-rrh")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_RH)
	public ResponseEntity<DemandeAdministrativeRhResponse> refuserRrh(
			@PathVariable UUID identifiant,
			@Valid @RequestBody DemandeRefusRequest requete) {
		return ResponseEntity.ok(demandeAdministrativeRhService.refuserRrh(identifiant, requete));
	}

	/**
	 * Annulation par le demandeur lui-même.
	 * CDC §M01 : possible uniquement si statut = SOUMISE ou EN_VALIDATION_SUPERIEUR.
	 */
	@PostMapping("/{identifiant}/annuler")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<DemandeAdministrativeRhResponse> annuler(
			@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(
				demandeAdministrativeRhService.annuler(identifiant, principal.getToken()));
	}

	/** Suivi du workflow avec étapes visuelles. */
	@GetMapping("/{identifiant}/suivi")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<DemandeAdministrativeSuiviResponse> suivi(
			@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeAdministrativeRhService.suivi(
				identifiant, principal.getToken(), ReferentielApiSecurity.aAutoriteRh()));
	}

	/** Détail d'une demande. */
	@GetMapping("/{identifiant}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<DemandeAdministrativeRhResponse> obtenir(
			@PathVariable UUID identifiant,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(demandeAdministrativeRhService.obtenir(
				identifiant, principal.getToken(), ReferentielApiSecurity.aAutoriteRh()));
	}
}
