package com.hr.presence.web;

import com.hr.presence.dto.ErrorResponse;
import com.hr.presence.dto.PresenceWorkflowEffetDtos.RequeteEffet;
import com.hr.presence.service.PresenceWorkflowEffetService;
import com.hr.presence.service.PresenceWorkflowEffetService.ResultatEffet;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Endpoint interne d'exécution d'effets métier appelé par {@code svc-workflow} (D-W6-06).
 * Protégé par {@code PresenceJetonInterneFiltre} ({@code X-Internal-Token} → {@code ROLE_INTERNAL}).
 * Ne renvoie jamais HTTP 400 pour un rejet métier ou une requête invalide : {@code EffetClient} considère 400
 * comme un état {@code Incertain} et réessaie indéfiniment.
 */
@RestController
@RequestMapping("/internal/presence/v1")
public class PresenceWorkflowEffetController {

	private final PresenceWorkflowEffetService effetService;

	public PresenceWorkflowEffetController(PresenceWorkflowEffetService effetService) {
		this.effetService = effetService;
	}

	@PostMapping("/workflow/effets")
	@PreAuthorize("hasRole('INTERNAL')")
	public ResponseEntity<Map<String, Object>> executerEffet(@RequestBody(required = false) RequeteEffet req) {
		if (req == null) {
			throw PresenceMetierException.unprocessable("CORPS_INVALIDE", "Corps de requête manquant.");
		}
		ResultatEffet res;
		if (PresenceWorkflowEffetService.MODE_CONTROLER.equals(req.mode())) {
			res = effetService.controler(req);
		} else if (PresenceWorkflowEffetService.MODE_APPLIQUER.equals(req.mode())) {
			res = effetService.appliquer(req);
		} else {
			throw PresenceMetierException.unprocessable("CORPS_INVALIDE", "Mode d'effet invalide : " + req.mode());
		}
		return ResponseEntity.status(res.statutHttp()).body(res.corps());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> corpsIllisibleEn422(HttpMessageNotReadableException ex) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
				ErrorResponse.of("CORPS_INVALIDE", "Corps de requête illisible ou valeur invalide",
						"Vérifiez les champs de la requête d'effet workflow", List.of(),
						HttpStatus.UNPROCESSABLE_ENTITY.value()));
	}
}
