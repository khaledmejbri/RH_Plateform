package com.hr.referentiel.web;

import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.service.FiltreCollaborateurs;
import com.hr.referentiel.service.ReferentielRhService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/referentiel/v1")
public class ReferentielController {

	private final ReferentielRhService referentielRhService;
	private final com.hr.referentiel.service.CollaborateurConnecteService collaborateurConnecteService;
	private final com.hr.referentiel.service.CreationCollaborateurService creationService;
	private final com.hr.referentiel.service.RechercheCollaborateursService rechercheService;
	private final com.hr.referentiel.service.ExportCollaborateursService exportService;

	public ReferentielController(ReferentielRhService referentielRhService,
			com.hr.referentiel.service.CollaborateurConnecteService collaborateurConnecteService,
			com.hr.referentiel.service.CreationCollaborateurService creationService,
			com.hr.referentiel.service.RechercheCollaborateursService rechercheService,
			com.hr.referentiel.service.ExportCollaborateursService exportService) {
		this.creationService = creationService;
		this.rechercheService = rechercheService;
		this.exportService = exportService;
		this.referentielRhService = referentielRhService;
		this.collaborateurConnecteService = collaborateurConnecteService;
	}

	@GetMapping("/unites")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<List<UniteResponse>> listerUnitesActives() {
		return ResponseEntity.ok(referentielRhService.listerUnitesActives());
	}

	@GetMapping("/unites/{identifiant}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<Object> obtenirUnite(@PathVariable UUID identifiant) {
		return referentielRhService.obtenirUnite(identifiant)
				.<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("erreur", "Unité introuvable.")));
	}

	@PostMapping("/unites")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<UniteResponse> creerUnite(@Valid @RequestBody UniteCreationRequest requete) {
		return ResponseEntity.status(HttpStatus.CREATED).body(referentielRhService.creerUnite(requete));
	}

	@PutMapping("/unites/{identifiant}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Object> mettreAJourUnite(@PathVariable UUID identifiant,
			@Valid @RequestBody UniteMiseAJourRequest requete) {
		return referentielRhService.mettreAJourUnite(identifiant, requete)
				.<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("erreur", "Unité introuvable.")));
	}

	@GetMapping("/collaborateurs")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<PageReferentielResponse<CollaborateurResponse>> listerCollaborateurs(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int taille,
			@RequestParam Map<String, String> filtres) {
		// V5 : tous les filtres (dont les critères personnalisés « cp.CODE.critère ») sont appliqués par le serveur
		return ResponseEntity.ok(rechercheService.lister(FiltreCollaborateurs.lire(filtres), page, taille));
	}

	/** V6 : export CSV de la liste filtrée (mêmes filtres que V5, sans pagination). */
	@GetMapping("/collaborateurs/export")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<byte[]> exporterCollaborateurs(@RequestParam Map<String, String> filtres,
			org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken principal) {
		byte[] csv = exportService.exporter(FiltreCollaborateurs.lire(filtres), principal.getName());
		return ResponseEntity.ok()
				.header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"" + exportService.nomFichier() + "\"")
				.contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8")).body(csv);
	}

	@GetMapping("/collaborateurs/moi")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<CollaborateurResponse> obtenirMoi(
			org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken principal) {
		return ResponseEntity.ok(referentielRhService.obtenirMonCollaborateur(principal.getToken()));
	}

	/** D-P04 : appelant et valideur de ses pointages manuels (svc-presence). */
	@GetMapping("/collaborateurs/moi/valideur-pointage")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<com.hr.referentiel.dto.ValideurPointageResponse> obtenirMonValideurPointage(
			org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken principal) {
		return ResponseEntity.ok(referentielRhService.obtenirMonValideurPointage(principal.getToken()));
	}

	@GetMapping("/collaborateurs/{identifiant}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<Object> obtenirCollaborateur(@PathVariable UUID identifiant) {
		return referentielRhService.obtenirCollaborateur(identifiant)
				.map(referentielRhService::avecLibelles)
				.<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("erreur", "Collaborateur introuvable.")));
	}

	@GetMapping("/collaborateurs/matricule/{matricule}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<Object> obtenirCollaborateurParMatricule(@PathVariable String matricule) {
		return referentielRhService.obtenirCollaborateurParMatricule(matricule)
				.map(referentielRhService::avecLibelles)
				.<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("erreur", "Collaborateur introuvable.")));
	}

	@PostMapping("/collaborateurs")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<CollaborateurResponse> creerCollaborateur(
			@Valid @RequestBody CollaborateurCreationRequest requete,
			org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken principal) {
		return ResponseEntity.status(HttpStatus.CREATED).body(creationService.creer(requete, principal.getToken()));
	}

	@PutMapping("/collaborateurs/{identifiant}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Object> mettreAJourCollaborateur(@PathVariable UUID identifiant,
			@Valid @RequestBody CollaborateurMiseAJourRequest requete) {
		return referentielRhService.mettreAJourCollaborateur(identifiant, requete)
				.<ResponseEntity<Object>>map(ResponseEntity::ok)
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("erreur", "Collaborateur introuvable.")));
	}

	/** Population ACTIF pour activation campagne M07 (lecture BO / inter-service). */
	@GetMapping("/collaborateurs/actifs-evaluation")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<List<CollaborateurEvaluationSnapshotResponse>> listerActifsEvaluation() {
		return ResponseEntity.ok(referentielRhService.listerActifsPourEvaluation());
	}
}
