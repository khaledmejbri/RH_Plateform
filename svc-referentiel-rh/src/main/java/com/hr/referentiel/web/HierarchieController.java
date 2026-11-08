package com.hr.referentiel.web;

import com.hr.referentiel.dto.FicheAnnuaireResponse;
import com.hr.referentiel.dto.FicheEquipeResponse;
import com.hr.referentiel.dto.FicheUniteResponse;
import com.hr.referentiel.dto.HierarchieResponse;
import com.hr.referentiel.dto.PersonneResume;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.service.CollaborateurConnecteService;
import com.hr.referentiel.service.HierarchieService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lot 2 UAT (D-N01, D-N02) : hiérarchie, fiche d'unité, annuaire et équipe, en lecture seule. Les endpoints
 * « moi » lisent l'identité dans le JWT ; l'en-tête mobile {@code X-Collaborateur-Id}, s'il est présent, doit
 * correspondre à la fiche de l'appelant (403 {@code IDENTITE_INCOHERENTE}, SCRUM-52).
 */
@RestController
@RequestMapping("/api/referentiel/v1")
public class HierarchieController {

	static final String EN_TETE_COLLABORATEUR = "X-Collaborateur-Id";

	private final HierarchieService hierarchieService;
	private final CollaborateurConnecteService collaborateurConnecteService;

	public HierarchieController(HierarchieService hierarchieService,
			CollaborateurConnecteService collaborateurConnecteService) {
		this.hierarchieService = hierarchieService;
		this.collaborateurConnecteService = collaborateurConnecteService;
	}

	@GetMapping("/collaborateurs/{identifiant}/hierarchie")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<HierarchieResponse> hierarchie(@PathVariable UUID identifiant) {
		return ResponseEntity.ok(hierarchieService.hierarchieDe(identifiant));
	}

	@GetMapping("/unites/{identifiant}/fiche")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<FicheUniteResponse> ficheUnite(@PathVariable UUID identifiant) {
		return ResponseEntity.ok(hierarchieService.ficheUnite(identifiant));
	}

	@GetMapping("/collaborateurs/moi/hierarchie")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<HierarchieResponse> maHierarchie(
			@RequestHeader(value = EN_TETE_COLLABORATEUR, required = false) String enTete,
			JwtAuthenticationToken principal) {
		verifierIdentite(enTete, principal);
		return ResponseEntity.ok(hierarchieService.maHierarchie(principal.getToken()));
	}

	@GetMapping("/annuaire/{collaborateurId}")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<FicheAnnuaireResponse> annuaire(@PathVariable UUID collaborateurId) {
		return ResponseEntity.ok(hierarchieService.annuaire(collaborateurId));
	}

	@GetMapping("/collaborateurs/moi/equipe")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<List<PersonneResume>> monEquipe(
			@RequestHeader(value = EN_TETE_COLLABORATEUR, required = false) String enTete,
			JwtAuthenticationToken principal) {
		verifierIdentite(enTete, principal);
		return ResponseEntity.ok(hierarchieService.monEquipe(principal.getToken()));
	}

	@GetMapping("/collaborateurs/moi/equipe/{collaborateurId}")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<FicheEquipeResponse> ficheEquipe(@PathVariable UUID collaborateurId,
			@RequestHeader(value = EN_TETE_COLLABORATEUR, required = false) String enTete,
			JwtAuthenticationToken principal) {
		verifierIdentite(enTete, principal);
		return ResponseEntity.ok(hierarchieService.ficheEquipe(collaborateurId, principal.getToken()));
	}

	/** SCRUM-52 : l'en-tête n'est pas une preuve ; différent de la fiche du JWT (ou illisible) → 403. */
	private void verifierIdentite(String enTete, JwtAuthenticationToken principal) {
		if (enTete == null || enTete.isBlank()) {
			return;
		}
		UUID fiche = collaborateurConnecteService.exigerCollaborateur(principal.getToken()).getId();
		UUID annonce;
		try {
			annonce = UUID.fromString(enTete.trim());
		} catch (IllegalArgumentException e) {
			annonce = null;
		}
		if (!fiche.equals(annonce)) {
			throw new ReferentielMetierException("IDENTITE_INCOHERENTE",
					"L'identité annoncée ne correspond pas à votre compte.", 403);
		}
	}
}
