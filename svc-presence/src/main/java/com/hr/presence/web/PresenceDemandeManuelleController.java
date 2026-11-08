package com.hr.presence.web;

import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.dto.DemandeManuelleCreationRequest;
import com.hr.presence.dto.DemandeManuelleRefusRequest;
import com.hr.presence.dto.DemandeManuelleDetail;
import com.hr.presence.dto.DemandeManuelleResponse;
import com.hr.presence.service.PresenceDemandeManuelleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pointage manuel validé par le responsable (D-P04 — SCRUM-38).
 * Le droit de décider (valideur attendu ou RH / ADMIN, jamais le demandeur) est vérifié dans le service.
 */
@RestController
public class PresenceDemandeManuelleController {

	private static final String MOBILE = "/api/rh/v1/mobile/presence/demandes-manuelles";
	private static final String ADMIN = "/api/rh/v1/admin/presence/demandes-manuelles";

	private final PresenceDemandeManuelleService service;

	public PresenceDemandeManuelleController(PresenceDemandeManuelleService service) {
		this.service = service;
	}

	@PostMapping(MOBILE)
	@PreAuthorize(PresenceSecurityExpressions.MOBILE_USER)
	public ResponseEntity<DemandeManuelleResponse> creer(@Valid @RequestBody DemandeManuelleCreationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(request));
	}

	@GetMapping(MOBILE + "/moi")
	@PreAuthorize(PresenceSecurityExpressions.MOBILE_USER)
	public List<DemandeManuelleResponse> mesDemandes() {
		return service.mesDemandes();
	}

	@GetMapping(MOBILE + "/a-valider")
	@PreAuthorize(PresenceSecurityExpressions.MOBILE_USER)
	public List<DemandeManuelleResponse> aValider() {
		return service.aValider();
	}

	@PostMapping({MOBILE + "/{id}/valider", ADMIN + "/{id}/valider"})
	@PreAuthorize("hasAnyRole('USER','RH','ADMIN')")
	public DemandeManuelleResponse valider(@PathVariable UUID id) {
		return service.valider(id);
	}

	@PostMapping({MOBILE + "/{id}/refuser", ADMIN + "/{id}/refuser"})
	@PreAuthorize("hasAnyRole('USER','RH','ADMIN')")
	public DemandeManuelleResponse refuser(@PathVariable UUID id, @Valid @RequestBody DemandeManuelleRefusRequest request) {
		return service.refuser(id, request.motif());
	}

	@GetMapping(ADMIN)
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public List<DemandeManuelleResponse> listerAdmin(
			@RequestParam(required = false) StatutDemandeManuelle statut,
			@RequestParam(name = "collaborateurId", required = false) UUID collaborateurId,
			@RequestParam(name = "siteId", required = false) UUID siteId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
		return service.listerAdmin(statut, collaborateurId, siteId, du, au);
	}

	@GetMapping(ADMIN + "/{id}")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public DemandeManuelleDetail detailAdmin(@PathVariable UUID id) {
		return service.detailAdmin(id);
	}

	/** Détail par numéro lisible PM-AAAAMM-NNNN (lien porté par la référence dans les tableaux). */
	@GetMapping(ADMIN + "/numero/{numero}")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public DemandeManuelleDetail detailAdminParNumero(@PathVariable String numero) {
		return service.detailAdminParNumero(numero);
	}

	/** Demandeur, valideur attendu ou RH quand la validation leur revient ; sinon 403 HORS_PERIMETRE. */
	@GetMapping(MOBILE + "/{id}")
	@PreAuthorize(PresenceSecurityExpressions.MOBILE_USER)
	public DemandeManuelleDetail detailMobile(@PathVariable UUID id) {
		return service.detailMobile(id);
	}
}
