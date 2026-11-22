package com.hr.workflow.web;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.config.WorkflowSecurityExpressions;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.dto.AdminWorkflowDtos.ActionVersionRequest;
import com.hr.workflow.dto.AdminWorkflowDtos.ActivationResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.ComparaisonVersionsResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.CreerModeleRequest;
import com.hr.workflow.dto.AdminWorkflowDtos.JournalModeleResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.ModeleDetailResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.ModeleResumeResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.ModifierActivationRequest;
import com.hr.workflow.dto.AdminWorkflowDtos.ModifierModeleRequest;
import com.hr.workflow.dto.AdminWorkflowDtos.ModifierVersionRequest;
import com.hr.workflow.dto.AdminWorkflowDtos.PageResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.SimulationResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.SimulerVersionRequest;
import com.hr.workflow.dto.AdminWorkflowDtos.ValidationResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.VersionDetailResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.VersionResumeResponse;
import com.hr.workflow.dto.DelegationDtos.CreerDelegationAdminRequest;
import com.hr.workflow.dto.DelegationDtos.DelegationResponse;
import com.hr.workflow.dto.DelegationDtos.ReaffecterTacheRequest;
import com.hr.workflow.dto.SupervisionDtos.IndicateursWorkflowResponse;
import com.hr.workflow.dto.SupervisionDtos.InstanceDetailAdminResponse;
import com.hr.workflow.dto.SupervisionDtos.InstanceResumeResponse;
import com.hr.workflow.dto.SupervisionDtos.ReaffecterTacheResponse;
import com.hr.workflow.service.ActivationService;
import com.hr.workflow.service.DelegationService;
import com.hr.workflow.service.ModeleVersionService;
import com.hr.workflow.service.ReaffectationService;
import com.hr.workflow.service.SimulationService;
import com.hr.workflow.service.SupervisionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints d'administration du moteur de workflow (lots W3 et W4, § 9.7.2).
 */
@RestController
@RequestMapping("/api/rh/v1/admin/workflow")
public class WorkflowAdminController {

	private final CatalogueTypesObjet catalogue;
	private final ActivationService activationService;
	private final ModeleVersionService modeleVersionService;
	private final SimulationService simulationService;
	private final SupervisionService supervisionService;
	private final ReaffectationService reaffectationService;
	private final DelegationService delegationService;
	private final WorkflowReferentielClient referentielClient;

	public WorkflowAdminController(
			CatalogueTypesObjet catalogue,
			ActivationService activationService,
			ModeleVersionService modeleVersionService,
			SimulationService simulationService,
			SupervisionService supervisionService,
			ReaffectationService reaffectationService,
			DelegationService delegationService,
			WorkflowReferentielClient referentielClient) {
		this.catalogue = catalogue;
		this.activationService = activationService;
		this.modeleVersionService = modeleVersionService;
		this.simulationService = simulationService;
		this.supervisionService = supervisionService;
		this.reaffectationService = reaffectationService;
		this.delegationService = delegationService;
		this.referentielClient = referentielClient;
	}

	private String auteurNom(Authentication auth) {
		return referentielClient.nomAfficheOuRepli(IdentiteJwt.compteId(auth), IdentiteJwt.nomAffiche(auth));
	}

	@GetMapping("/catalogue")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_LECTURE)
	public Map<String, Object> catalogue() {
		return catalogue.construireReponseCatalogue();
	}

	@GetMapping("/activations")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_LECTURE)
	public List<ActivationResponse> listerActivations() {
		return activationService.lister();
	}

	@PutMapping("/activations/{objetType}")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_ECRITURE)
	public ActivationResponse modifierActivation(
			@PathVariable("objetType") String objetType,
			@Valid @RequestBody ModifierActivationRequest req,
			Authentication auth) {
		return activationService.modifier(objetType, req, IdentiteJwt.compteId(auth), auteurNom(auth));
	}

	@GetMapping("/modeles")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_LECTURE)
	public PageResponse<ModeleResumeResponse> listerModeles(
			@RequestParam(name = "objet_type", required = false) String objetType,
			@RequestParam(name = "actif", required = false) Boolean actif,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille) {
		return modeleVersionService.listerModeles(objetType, actif, page, taille);
	}

	@PostMapping("/modeles")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_ECRITURE)
	public ModeleDetailResponse creerModele(@Valid @RequestBody CreerModeleRequest req, Authentication auth) {
		return modeleVersionService.creerModele(req, IdentiteJwt.compteId(auth), auteurNom(auth));
	}

	@GetMapping("/modeles/{id}")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_LECTURE)
	public ModeleDetailResponse detailModele(@PathVariable("id") UUID id) {
		return modeleVersionService.detailModele(id);
	}

	@PutMapping("/modeles/{id}")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_ECRITURE)
	public ModeleDetailResponse modifierModele(
			@PathVariable("id") UUID id,
			@Valid @RequestBody ModifierModeleRequest req,
			Authentication auth) {
		return modeleVersionService.modifierModele(id, req, IdentiteJwt.compteId(auth), auteurNom(auth));
	}

	@PostMapping("/modeles/{id}/activer")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_ECRITURE)
	public ModeleDetailResponse activerModele(
			@PathVariable("id") UUID id,
			@Valid @RequestBody ActionVersionRequest req,
			Authentication auth) {
		return modeleVersionService.activerModele(id, req, IdentiteJwt.compteId(auth), auteurNom(auth));
	}

	@PostMapping("/modeles/{id}/desactiver")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_ECRITURE)
	public ModeleDetailResponse desactiverModele(
			@PathVariable("id") UUID id,
			@Valid @RequestBody ActionVersionRequest req,
			Authentication auth) {
		return modeleVersionService.desactiverModele(id, req, IdentiteJwt.compteId(auth), auteurNom(auth));
	}

	@DeleteMapping("/modeles/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_ECRITURE)
	public void supprimerModele(@PathVariable("id") UUID id) {
		modeleVersionService.supprimerModele(id);
	}

	@GetMapping("/modeles/{id}/versions")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_LECTURE)
	public List<VersionResumeResponse> listerVersions(@PathVariable("id") UUID id) {
		return modeleVersionService.listerVersions(id);
	}

	@PostMapping("/modeles/{id}/versions")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_ECRITURE)
	public VersionDetailResponse creerBrouillon(@PathVariable("id") UUID id, Authentication auth) {
		return modeleVersionService.creerBrouillon(id, IdentiteJwt.compteId(auth), auteurNom(auth));
	}

	@GetMapping("/versions/{vid}")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_LECTURE)
	public VersionDetailResponse detailVersion(@PathVariable("vid") UUID vid) {
		return modeleVersionService.detailVersion(vid);
	}

	@PutMapping("/versions/{vid}")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_ECRITURE)
	public VersionDetailResponse modifierBrouillon(
			@PathVariable("vid") UUID vid,
			@Valid @RequestBody ModifierVersionRequest req,
			Authentication auth) {
		return modeleVersionService.modifierBrouillon(vid, req, IdentiteJwt.compteId(auth), auteurNom(auth));
	}

	@PostMapping("/versions/{vid}/valider")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_LECTURE)
	public ValidationResponse validerVersion(@PathVariable("vid") UUID vid) {
		return modeleVersionService.validerVersion(vid);
	}

	@PostMapping("/versions/{vid}/simuler")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_LECTURE)
	public SimulationResponse simulerVersion(
			@PathVariable("vid") UUID vid,
			@Valid @RequestBody SimulerVersionRequest req) {
		return simulationService.simuler(vid, req);
	}

	@PostMapping("/versions/{vid}/publier")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_ECRITURE)
	public VersionDetailResponse publierVersion(
			@PathVariable("vid") UUID vid,
			@Valid @RequestBody ActionVersionRequest req,
			Authentication auth) {
		return modeleVersionService.publier(vid, req, IdentiteJwt.compteId(auth), auteurNom(auth));
	}

	@DeleteMapping("/versions/{vid}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_ECRITURE)
	public void supprimerBrouillon(@PathVariable("vid") UUID vid, Authentication auth) {
		modeleVersionService.supprimerBrouillon(vid, IdentiteJwt.compteId(auth), auteurNom(auth));
	}

	@GetMapping("/versions/{vid}/comparer/{autreVid}")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_LECTURE)
	public ComparaisonVersionsResponse comparerVersions(
			@PathVariable("vid") UUID vid,
			@PathVariable("autreVid") UUID autreVid) {
		return modeleVersionService.comparerVersions(vid, autreVid);
	}

	@GetMapping("/modeles/{id}/journal")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_LECTURE)
	public PageResponse<JournalModeleResponse> listerJournal(
			@PathVariable("id") UUID id,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille) {
		return modeleVersionService.listerJournal(id, page, taille);
	}

	// ─── Supervision, réaffectation et délégations (lot W4) ──────────────────

	@GetMapping("/instances")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_LECTURE)
	public PageResponse<InstanceResumeResponse> listerInstances(
			@RequestParam(name = "objet_type", required = false) String objetType,
			@RequestParam(name = "statut", required = false) StatutInstance statut,
			@RequestParam(name = "etape", required = false) String etape,
			@RequestParam(name = "en_retard", required = false) Boolean enRetard,
			@RequestParam(name = "demandeur_id", required = false) UUID demandeurId,
			@RequestParam(name = "intervenant_id", required = false) UUID intervenantId,
			@RequestParam(name = "du", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(name = "au", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille) {
		return supervisionService.listerInstances(
				objetType, statut, etape, enRetard, demandeurId, intervenantId, du, au, page, taille);
	}

	@GetMapping("/instances/{id}")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_LECTURE)
	public InstanceDetailAdminResponse detailInstance(@PathVariable("id") UUID id) {
		return supervisionService.detailInstance(id);
	}

	@GetMapping("/indicateurs")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_LECTURE)
	public IndicateursWorkflowResponse indicateurs(
			@RequestParam(name = "du", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(name = "au", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
		return supervisionService.indicateurs(du, au);
	}

	@PostMapping("/taches/{id}/reaffecter")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_ECRITURE)
	public ReaffecterTacheResponse reaffecterTache(
			@PathVariable("id") UUID id,
			@Valid @RequestBody ReaffecterTacheRequest req,
			Authentication auth) {
		return reaffectationService.reaffecter(id, req, IdentiteJwt.compteId(auth), auteurNom(auth));
	}

	@GetMapping("/delegations")
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_LECTURE)
	public PageResponse<DelegationResponse> listerDelegations(
			@RequestParam(name = "actives", required = false) Boolean actives,
			@RequestParam(name = "delegant_id", required = false) UUID delegantId,
			@RequestParam(name = "delegue_id", required = false) UUID delegueId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille) {
		return delegationService.listerAdmin(actives, delegantId, delegueId, page, taille);
	}

	@PostMapping("/delegations")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_ECRITURE)
	public DelegationResponse creerDelegation(
			@Valid @RequestBody CreerDelegationAdminRequest req,
			Authentication auth) {
		return delegationService.creerAdmin(req, IdentiteJwt.compteId(auth));
	}

	@DeleteMapping("/delegations/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(WorkflowSecurityExpressions.BACKOFFICE_ECRITURE)
	public void annulerDelegation(@PathVariable("id") UUID id, Authentication auth) {
		delegationService.annulerAdmin(id, IdentiteJwt.compteId(auth));
	}
}
