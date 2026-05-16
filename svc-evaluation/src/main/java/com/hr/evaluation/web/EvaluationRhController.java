package com.hr.evaluation.web;

import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.domain.TypeEvaluationRh;
import com.hr.evaluation.dto.EvaluationAnnuelleCreationRequest;
import com.hr.evaluation.dto.EvaluationPdfArchiveResponse;
import com.hr.evaluation.dto.EvaluationRhResponse;
import com.hr.evaluation.dto.EvaluationSemestrielleCreationRequest;
import com.hr.evaluation.dto.EvaluationValidationRequest;
import com.hr.evaluation.service.EvaluationRhService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/rh/v1/evaluations")
public class EvaluationRhController {

	private final EvaluationRhService evaluationRhService;

	public EvaluationRhController(EvaluationRhService evaluationRhService) {
		this.evaluationRhService = evaluationRhService;
	}

	@PostMapping("/semestrielles")
	@PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_RH)
	public ResponseEntity<EvaluationRhResponse> creerSemestrielle(
			@Valid @RequestBody EvaluationSemestrielleCreationRequest requete) {
		return ResponseEntity.status(HttpStatus.CREATED).body(evaluationRhService.creerSemestrielle(requete));
	}

	@PostMapping("/annuelles")
	@PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_RH)
	public ResponseEntity<EvaluationRhResponse> creerAnnuelle(
			@Valid @RequestBody EvaluationAnnuelleCreationRequest requete) {
		return ResponseEntity.status(HttpStatus.CREATED).body(evaluationRhService.creerAnnuelle(requete));
	}

	@GetMapping
	@PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_RH)
	public ResponseEntity<List<EvaluationRhResponse>> lister(
			@RequestParam(name = "type", required = false) TypeEvaluationRh type,
			@RequestParam(name = "statut", required = false) StatutEvaluationRh statut) {
		return ResponseEntity.ok(evaluationRhService.lister(type, statut));
	}

	@GetMapping("/collaborateur/{collaborateurIdentifiant}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<List<EvaluationRhResponse>> listerCollaborateur(
			@PathVariable UUID collaborateurIdentifiant) {
		return ResponseEntity.ok(evaluationRhService.listerCollaborateur(collaborateurIdentifiant));
	}

	@GetMapping("/superieur/{superieurIdentifiant}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<List<EvaluationRhResponse>> listerSuperieur(
			@PathVariable UUID superieurIdentifiant) {
		return ResponseEntity.ok(evaluationRhService.listerSuperieur(superieurIdentifiant));
	}

	@GetMapping("/{identifiant}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<EvaluationRhResponse> obtenir(@PathVariable UUID identifiant) {
		return ResponseEntity.ok(evaluationRhService.obtenir(identifiant));
	}

	@PostMapping("/{identifiant}/validation-collaborateur")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<EvaluationRhResponse> validerCollaborateur(
			@PathVariable UUID identifiant,
			@RequestBody(required = false) EvaluationValidationRequest requete) {
		UUID acteur = requete != null ? requete.acteurIdentifiant() : null;
		return ResponseEntity.ok(evaluationRhService.validerCollaborateur(identifiant, acteur));
	}

	@PostMapping("/{identifiant}/validation-superieur")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<EvaluationRhResponse> validerSuperieur(
			@PathVariable UUID identifiant,
			@RequestBody(required = false) EvaluationValidationRequest requete) {
		UUID acteur = requete != null ? requete.acteurIdentifiant() : null;
		return ResponseEntity.ok(evaluationRhService.validerSuperieur(identifiant, acteur));
	}

	@PostMapping("/{identifiant}/integrer-formations-m05")
	@PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_RH)
	public ResponseEntity<EvaluationRhResponse> integrerFormationsM05(@PathVariable UUID identifiant) {
		return ResponseEntity.ok(evaluationRhService.integrerFormationsM05(identifiant));
	}

	@PostMapping("/{identifiant}/export-pdf")
	@PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_RH)
	public ResponseEntity<EvaluationPdfArchiveResponse> exporterPdf(@PathVariable UUID identifiant) {
		return ResponseEntity.ok(evaluationRhService.exporterPdf(identifiant));
	}
}
