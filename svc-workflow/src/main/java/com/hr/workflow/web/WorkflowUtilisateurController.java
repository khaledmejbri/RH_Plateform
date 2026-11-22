package com.hr.workflow.web;

import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.config.WorkflowSecurityExpressions;
import com.hr.workflow.dto.AdminWorkflowDtos.PageResponse;
import com.hr.workflow.dto.DelegationDtos.CreerDelegationMoiRequest;
import com.hr.workflow.dto.DelegationDtos.DelegationResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.CompteurTachesResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionEnCoursResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionRequest;
import com.hr.workflow.dto.WorkflowMoteurDtos.SuiviInstanceResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.TacheDetailResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.TacheListeItemResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.TypeOuvertResponse;
import com.hr.workflow.moteur.DecisionService;
import com.hr.workflow.moteur.DecisionService.ResultatDecision;
import com.hr.workflow.service.DelegationService;
import com.hr.workflow.service.WorkflowUtilisateurService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints utilisateur du moteur de workflow (lots W3 et W4, § 9.7.3).
 */
@RestController
@RequestMapping("/api/rh/v1/workflow")
public class WorkflowUtilisateurController {

	private final WorkflowUtilisateurService utilisateurService;
	private final DecisionService decisionService;
	private final DelegationService delegationService;
	private final WorkflowReferentielClient referentielClient;

	public WorkflowUtilisateurController(
			WorkflowUtilisateurService utilisateurService,
			DecisionService decisionService,
			DelegationService delegationService,
			WorkflowReferentielClient referentielClient) {
		this.utilisateurService = utilisateurService;
		this.decisionService = decisionService;
		this.delegationService = delegationService;
		this.referentielClient = referentielClient;
	}

	@GetMapping("/types-ouverts")
	@PreAuthorize(WorkflowSecurityExpressions.UTILISATEUR)
	public List<TypeOuvertResponse> typesOuverts() {
		return utilisateurService.typesOuverts();
	}

	@GetMapping("/taches/moi")
	@PreAuthorize(WorkflowSecurityExpressions.UTILISATEUR)
	public PageResponse<TacheListeItemResponse> mesTaches(
			@RequestParam(name = "statut", defaultValue = "A_TRAITER") String statut,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille,
			Authentication auth) {
		return utilisateurService.mesTaches(IdentiteJwt.compteId(auth), statut, page, taille);
	}

	@GetMapping("/taches/moi/compteur")
	@PreAuthorize(WorkflowSecurityExpressions.UTILISATEUR)
	public CompteurTachesResponse compteurMesTaches(Authentication auth) {
		return utilisateurService.compteurMesTaches(IdentiteJwt.compteId(auth));
	}

	@GetMapping("/taches/{id}")
	@PreAuthorize(WorkflowSecurityExpressions.UTILISATEUR)
	public TacheDetailResponse detailTache(@PathVariable("id") UUID id, Authentication auth) {
		return utilisateurService.detailTache(
				id,
				IdentiteJwt.compteId(auth),
				IdentiteJwt.estBackofficeLecture(auth),
				IdentiteJwt.estBackofficeEcriture(auth));
	}

	@PostMapping("/taches/{id}/decision")
	@PreAuthorize(WorkflowSecurityExpressions.UTILISATEUR)
	public ResponseEntity<?> decider(
			@PathVariable("id") UUID id,
			@Valid @RequestBody DecisionRequest req,
			Authentication auth) {
		UUID compteId = IdentiteJwt.compteId(auth);
		ResultatDecision res = decisionService.decider(
				id,
				req,
				compteId,
				referentielClient.nomAfficheOuRepli(compteId, IdentiteJwt.nomAffiche(auth)),
				IdentiteJwt.estBackofficeLecture(auth));
		if (res.decisionEnCours()) {
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(new DecisionEnCoursResponse("DECISION_EN_COURS"));
		}
		return ResponseEntity.ok(res.reponse());
	}

	@GetMapping("/instances/{id}/suivi")
	@PreAuthorize(WorkflowSecurityExpressions.UTILISATEUR)
	public SuiviInstanceResponse suiviParInstance(@PathVariable("id") UUID id, Authentication auth) {
		return utilisateurService.suiviParInstance(
				id,
				IdentiteJwt.compteId(auth),
				IdentiteJwt.estBackofficeLecture(auth),
				IdentiteJwt.estBackofficeEcriture(auth));
	}

	@GetMapping("/objets/{objetType}/{objetId}/suivi")
	@PreAuthorize(WorkflowSecurityExpressions.UTILISATEUR)
	public SuiviInstanceResponse suiviParObjet(
			@PathVariable("objetType") String objetType,
			@PathVariable("objetId") UUID objetId,
			Authentication auth) {
		return utilisateurService.suiviParObjet(
				objetType,
				objetId,
				IdentiteJwt.compteId(auth),
				IdentiteJwt.estBackofficeLecture(auth),
				IdentiteJwt.estBackofficeEcriture(auth));
	}

	// ─── Mes délégations (D-W21, lot W4) ─────────────────────────────────────

	@GetMapping("/delegations/moi")
	@PreAuthorize(WorkflowSecurityExpressions.UTILISATEUR)
	public List<DelegationResponse> mesDelegations(
			@RequestParam(name = "role", defaultValue = "DELEGANT") String role,
			@RequestParam(name = "actives", required = false) Boolean actives,
			Authentication auth) {
		return delegationService.listerMoi(IdentiteJwt.compteId(auth), role, actives);
	}

	@PostMapping("/delegations/moi")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(WorkflowSecurityExpressions.UTILISATEUR)
	public DelegationResponse creerMaDelegation(
			@Valid @RequestBody CreerDelegationMoiRequest req,
			Authentication auth) {
		return delegationService.creerMoi(IdentiteJwt.compteId(auth), req);
	}

	@DeleteMapping("/delegations/moi/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(WorkflowSecurityExpressions.UTILISATEUR)
	public void annulerMaDelegation(@PathVariable("id") UUID id, Authentication auth) {
		delegationService.annulerMoi(id, IdentiteJwt.compteId(auth));
	}
}
