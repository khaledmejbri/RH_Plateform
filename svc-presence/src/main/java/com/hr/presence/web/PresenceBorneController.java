package com.hr.presence.web;

import com.hr.presence.dto.BorneGenereeResponse;
import com.hr.presence.dto.BorneQrResponse;
import com.hr.presence.service.PresenceBorneService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Borne écran QR dynamique (D-P05 — SCRUM-39).
 */
@RestController
public class PresenceBorneController {

	private final PresenceBorneService borneService;

	public PresenceBorneController(PresenceBorneService borneService) {
		this.borneService = borneService;
	}

	@PostMapping("/api/rh/v1/admin/presence/sites/{siteId}/borne")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public BorneGenereeResponse generer(@PathVariable UUID siteId) {
		return borneService.generer(siteId);
	}

	@PostMapping("/api/rh/v1/admin/presence/sites/{siteId}/borne/revoke")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Void> revoquer(@PathVariable UUID siteId) {
		borneService.revoquer(siteId);
		return ResponseEntity.noContent().build();
	}

	/** Public : la borne n'a pas de compte ; le jeton long et révocable fait office d'accès. */
	@GetMapping("/api/rh/v1/borne/presence/{jeton}/qr")
	public ResponseEntity<BorneQrResponse> qrCourant(@PathVariable String jeton) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(borneService.qrCourant(jeton));
	}
}
