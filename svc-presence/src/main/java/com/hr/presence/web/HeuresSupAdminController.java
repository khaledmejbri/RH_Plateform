package com.hr.presence.web;

import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.dto.HeuresSupDtos.AnnulationHeuresSupRequest;
import com.hr.presence.dto.HeuresSupDtos.CreationHeuresSupRequest;
import com.hr.presence.dto.HeuresSupDtos.DemandeHeuresSupResponse;
import com.hr.presence.dto.HeuresSupDtos.HeuresComptees;
import com.hr.presence.dto.HeuresSupDtos.RefusHeuresSupRequest;
import com.hr.presence.dto.PageResponse;
import com.hr.presence.service.HeuresSupCreationService;
import com.hr.presence.service.HeuresSupCsvService;
import com.hr.presence.service.HeuresSupService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Heures supplémentaires côté Plateforme RH (D-P13 à D-P16, SCRUM-50 et 51) : lecture RH / DIRECTION / ADMIN,
 * création directe, décisions et annulation RH / ADMIN. L'anti-auto-validation est vérifiée dans le service.
 */
@RestController
@RequestMapping("/api/rh/v1/admin/presence/heures-sup")
public class HeuresSupAdminController {

	private final HeuresSupService service;
	private final HeuresSupCreationService creationService;
	private final HeuresSupCsvService csvService;

	public HeuresSupAdminController(HeuresSupService service, HeuresSupCreationService creationService,
									HeuresSupCsvService csvService) {
		this.service = service;
		this.creationService = creationService;
		this.csvService = csvService;
	}

	@GetMapping("/demandes")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public PageResponse<DemandeHeuresSupResponse> lister(
			@RequestParam(required = false) StatutHeuresSup statut,
			@RequestParam(name = "site_id", required = false) UUID siteId,
			@RequestParam(name = "collaborateur_id", required = false) UUID collaborateurId,
			@RequestParam(name = "direction_id", required = false) UUID directionId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.lister(statut, siteId, collaborateurId, directionId, du, au, page, size);
	}

	@GetMapping("/demandes/{id}")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public DemandeHeuresSupResponse obtenir(@PathVariable UUID id) {
		return service.obtenir(id);
	}

	/** Détail par numéro lisible HS-AAAAMM-NNNN (lien porté par la référence dans les tableaux). */
	@GetMapping("/demandes/numero/{numero}")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public DemandeHeuresSupResponse obtenirParNumero(@PathVariable String numero) {
		return service.obtenirParNumero(numero);
	}

	/** Heures comptées de la demande, par bénéficiaire et par jour. */
	@GetMapping("/demandes/{id}/comptees")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public java.util.List<HeuresComptees> compteesDeDemande(@PathVariable UUID id) {
		return service.compteesDeDemande(id);
	}

	@PostMapping("/demandes")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<DemandeHeuresSupResponse> creer(@Valid @RequestBody CreationHeuresSupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(creationService.creerDirecte(request));
	}

	@PostMapping("/demandes/{id}/valider")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public DemandeHeuresSupResponse valider(@PathVariable UUID id) {
		return service.valider(id);
	}

	@PostMapping("/demandes/{id}/refuser")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public DemandeHeuresSupResponse refuser(@PathVariable UUID id,
											@RequestBody(required = false) RefusHeuresSupRequest request) {
		return service.refuser(id, request != null ? request.motif() : null);
	}

	@PostMapping("/demandes/{id}/annuler")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public DemandeHeuresSupResponse annuler(@PathVariable UUID id,
											@RequestBody(required = false) AnnulationHeuresSupRequest request) {
		return service.annulerParBackoffice(id, request != null ? request.motif() : null);
	}

	@GetMapping("/comptees")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public PageResponse<HeuresComptees> comptees(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
			@RequestParam(name = "site_id", required = false) UUID siteId,
			@RequestParam(name = "collaborateur_id", required = false) UUID collaborateurId,
			@RequestParam(name = "direction_id", required = false) UUID directionId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.comptees(du, au, siteId, collaborateurId, directionId, page, size);
	}

	@GetMapping("/comptees/export.csv")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<byte[]> exporter(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
			@RequestParam(name = "site_id", required = false) UUID siteId,
			@RequestParam(name = "collaborateur_id", required = false) UUID collaborateurId,
			@RequestParam(name = "direction_id", required = false) UUID directionId) {
		byte[] csv = csvService.exporter(du, au, siteId, collaborateurId, directionId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"heures-supplementaires.csv\"")
				.body(csv);
	}
}
