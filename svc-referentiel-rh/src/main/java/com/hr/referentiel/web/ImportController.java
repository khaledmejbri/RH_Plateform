package com.hr.referentiel.web;

import com.hr.referentiel.dto.PageReferentielResponse;
import com.hr.referentiel.importation.ImportDtos.Analyse;
import com.hr.referentiel.importation.ImportDtos.CompteRendu;
import com.hr.referentiel.importation.ImportDtos.ConfirmationRequest;
import com.hr.referentiel.importation.ImportDtos.CorrespondanceRequest;
import com.hr.referentiel.importation.ImportDtos.ImportResume;
import com.hr.referentiel.importation.ImportDtos.LigneAnalyse;
import com.hr.referentiel.importation.ImportDtos.LigneHistorique;
import com.hr.referentiel.importation.ImportService;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Imports en masse des fiches collaborateurs et des soldes (I1 à I12). Écriture (analyse, correspondance, confirmation,
 * fichiers) : RH et ADMIN seulement, sur leurs propres analyses (404 pour l'analyse d'un autre, même RH). Historique : RH,
 * ADMIN et DIRECTION en lecture.
 */
@RestController
@RequestMapping("/api/rh/v1/admin/imports")
public class ImportController {

	private static final MediaType XLSX = MediaType.parseMediaType(
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final ImportService service;

	public ImportController(ImportService service) {
		this.service = service;
	}

	@PostMapping(value = "/collaborateurs/analyses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Analyse> analyserCollaborateurs(@RequestPart("fichier") MultipartFile fichier,
			@RequestParam(name = "creer_comptes", defaultValue = "false") boolean creerComptes,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.analyserCollaborateurs(fichier, creerComptes, principal.getToken()));
	}

	@PostMapping(value = "/soldes/analyses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Analyse> analyserSoldes(@RequestPart("fichier") MultipartFile fichier,
			@RequestParam(name = "motif_global", required = false) String motifGlobal, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.analyserSoldes(fichier, motifGlobal, principal.getToken()));
	}

	@GetMapping("/analyses/{analyseId}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Analyse> obtenir(@PathVariable UUID analyseId, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.obtenir(analyseId, principal.getToken()));
	}

	@GetMapping("/analyses/{analyseId}/lignes")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<PageReferentielResponse<LigneAnalyse>> lignes(@PathVariable UUID analyseId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "50") int taille,
			@RequestParam(name = "erreurs_seulement", defaultValue = "false") boolean erreursSeulement,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.lignes(analyseId, page, taille, erreursSeulement, principal.getToken()));
	}

	/** Correspondance corrigée : le fichier n'est pas renvoyé, l'analyse garde son identifiant. */
	@PutMapping("/analyses/{analyseId}/correspondance")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Analyse> corriger(@PathVariable UUID analyseId, @RequestBody CorrespondanceRequest requete,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.corriger(analyseId, requete, principal.getToken()));
	}

	@PostMapping("/analyses/{analyseId}/confirmation")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<CompteRendu> confirmer(@PathVariable UUID analyseId,
			@RequestBody(required = false) ConfirmationRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.confirmer(analyseId, requete, principal.getToken()));
	}

	@GetMapping("/analyses/{analyseId}/lignes-en-erreur")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<byte[]> lignesEnErreur(@PathVariable UUID analyseId, JwtAuthenticationToken principal) {
		return xlsx("lignes-en-erreur.xlsx", service.fichierErreurs(analyseId, principal.getToken()));
	}

	@GetMapping("/collaborateurs/modele")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<byte[]> modeleCollaborateurs() {
		return xlsx("modele-import-collaborateurs.xlsx", service.modeleCollaborateurs());
	}

	@GetMapping("/soldes/modele")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<byte[]> modeleSoldes() {
		return xlsx("modele-import-soldes.xlsx", service.modeleSoldes());
	}

	@GetMapping("/historique")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<PageReferentielResponse<ImportResume>> historique(
			@RequestParam(name = "type", required = false) String type,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille) {
		return ResponseEntity.ok(service.historique(type, page, taille));
	}

	@GetMapping("/historique/{importId}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<ImportResume> resume(@PathVariable UUID importId) {
		return ResponseEntity.ok(service.resume(importId));
	}

	@GetMapping("/historique/{importId}/lignes")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<PageReferentielResponse<LigneHistorique>> lignesHistorique(@PathVariable UUID importId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "50") int taille) {
		return ResponseEntity.ok(service.lignesHistorique(importId, page, taille));
	}

	private static ResponseEntity<byte[]> xlsx(String nom, byte[] contenu) {
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
				.contentType(XLSX).body(contenu);
	}
}
