package com.hr.referentiel.web;

import com.hr.referentiel.dto.WorkflowEffetDtos.RequeteEffet;
import com.hr.referentiel.service.WorkflowEffetService;
import com.hr.referentiel.service.WorkflowEffetService.ResultatEffet;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint interne d'exécution d'effets métier appelé par {@code svc-workflow} (§ 9.6.5, D-W5-07).
 * Protégé par {@code SocieteFiltreInterne} ({@code X-Internal-Token}, {@code X-Societe-Id} facultatif).
 */
@RestController
@RequestMapping("/internal/referentiel/v1")
@PreAuthorize("permitAll()")
public class WorkflowEffetInterneController {

	private final WorkflowEffetService effetService;

	public WorkflowEffetInterneController(WorkflowEffetService effetService) {
		this.effetService = effetService;
	}

	@PostMapping("/workflow/effets")
	public ResponseEntity<Map<String, Object>> executerEffet(@Valid @RequestBody RequeteEffet req) {
		ResultatEffet res;
		if (WorkflowEffetService.MODE_CONTROLER.equals(req.mode())) {
			res = effetService.controler(req);
		} else if (WorkflowEffetService.MODE_APPLIQUER.equals(req.mode())) {
			res = effetService.appliquer(req);
		} else {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "Mode d'effet invalide : " + req.mode());
		}
		return ResponseEntity.status(res.statutHttp()).body(res.corps());
	}
}
