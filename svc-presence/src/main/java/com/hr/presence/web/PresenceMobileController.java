package com.hr.presence.web;

import com.hr.presence.dto.CreatePointageRequest;
import com.hr.presence.dto.PageResponse;
import com.hr.presence.dto.PointageResponse;
import com.hr.presence.service.FacialIdentiteService;
import com.hr.presence.service.PresencePointageService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rh/v1/mobile/presence")
@PreAuthorize(PresenceSecurityExpressions.MOBILE_USER)
public class PresenceMobileController {

	private final PresencePointageService pointageService;
	private final FacialIdentiteService identiteService;

	public PresenceMobileController(
			PresencePointageService pointageService,
			FacialIdentiteService identiteService) {
		this.pointageService = pointageService;
		this.identiteService = identiteService;
	}

	@PostMapping("/pointages")
	public PointageResponse pointer(@Valid @RequestBody CreatePointageRequest request) {
		// Fiche lue au référentiel avec le JWT ; X-Collaborateur-Id présent et différent : 403 avant tout traitement.
		String collaborateurId = identiteService.resoudreFiche();
		return pointageService.pointer(collaborateurId, request);
	}

	@GetMapping("/pointages/me")
	public PageResponse<PointageResponse> historique(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		String collaborateurId = identiteService.resoudreFiche();
		return pointageService.historiqueMoi(collaborateurId, page, size);
	}
}
