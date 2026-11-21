package com.hr.workflow.web;

import com.hr.workflow.domain.ModeActivation;
import com.hr.workflow.dto.WorkflowMoteurDtos.ActivationInterneResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.AnnulerInstanceRequest;
import com.hr.workflow.dto.WorkflowMoteurDtos.ComplementFourniRequest;
import com.hr.workflow.dto.WorkflowMoteurDtos.DecisionResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.DemarrerInstanceRequest;
import com.hr.workflow.dto.WorkflowMoteurDtos.DemarrerInstanceResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.MajVariablesRequest;
import com.hr.workflow.dto.WorkflowMoteurDtos.SuiviInstanceResponse;
import com.hr.workflow.moteur.DemarrageService;
import com.hr.workflow.moteur.DemarrageService.ResultatDemarrage;
import com.hr.workflow.service.ActivationService;
import com.hr.workflow.service.InstanceInterneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoints internes de {@code svc-workflow} (§ 9.7.4).
 * Protégés par {@code JetonInterneFiltre} ({@code X-Internal-Token}, D-W3-02).
 */
@RestController
@RequestMapping("/internal/workflow/v1")
@PreAuthorize("permitAll()")
public class WorkflowInterneController {

	private final DemarrageService demarrageService;
	private final InstanceInterneService instanceInterneService;
	private final ActivationService activationService;

	public WorkflowInterneController(
			DemarrageService demarrageService,
			InstanceInterneService instanceInterneService,
			ActivationService activationService) {
		this.demarrageService = demarrageService;
		this.instanceInterneService = instanceInterneService;
		this.activationService = activationService;
	}

	@PostMapping("/instances")
	public ResponseEntity<DemarrerInstanceResponse> demarrerInstance(
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody DemarrerInstanceRequest req) {
		ResultatDemarrage res = demarrageService.demarrer(idempotencyKey, req);
		return ResponseEntity.status(res.cree() ? HttpStatus.CREATED : HttpStatus.OK).body(res.reponse());
	}

	@PutMapping("/instances/{id}/variables")
	public Map<String, Object> majVariables(
			@PathVariable("id") UUID id,
			@Valid @RequestBody MajVariablesRequest req) {
		return instanceInterneService.mettreAJourVariables(id, req);
	}

	@PostMapping("/instances/{id}/annuler")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void annuler(
			@PathVariable("id") UUID id,
			@RequestBody(required = false) AnnulerInstanceRequest req) {
		instanceInterneService.annuler(id, req);
	}

	@PostMapping("/instances/{id}/complement-fourni")
	public DecisionResponse complementFourni(
			@PathVariable("id") UUID id,
			@RequestBody(required = false) ComplementFourniRequest req) {
		return instanceInterneService.complementFourni(id, req);
	}

	@GetMapping("/activations/{objetType}")
	public ActivationInterneResponse activation(@PathVariable("objetType") String objetType) {
		ModeActivation mode = activationService.modePour(objetType);
		return new ActivationInterneResponse(objetType, mode);
	}

	@GetMapping("/objets/{objetType}/{objetId}")
	public SuiviInstanceResponse suiviObjet(
			@PathVariable("objetType") String objetType,
			@PathVariable("objetId") UUID objetId) {
		return instanceInterneService.suiviInterneParObjet(objetType, objetId);
	}
}
