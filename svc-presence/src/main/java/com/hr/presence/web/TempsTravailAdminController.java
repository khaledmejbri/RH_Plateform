package com.hr.presence.web;

import com.hr.presence.domain.EtatJourHeuresSup;
import com.hr.presence.dto.TempsTravailDtos.TempsTravailResponse;
import com.hr.presence.service.TempsTravailExcelService;
import com.hr.presence.service.TempsTravailService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Temps de travail par collaborateur × journée × site (D-P17), lecture seule : RH, DIRECTION et ADMIN consultent et
 * exportent ; tout autre rôle reçoit 403 (D-P17.k). Calcul à la volée, rien n'est écrit.
 */
@RestController
@RequestMapping("/api/rh/v1/admin/presence/temps-travail")
public class TempsTravailAdminController {

	private static final MediaType MEDIA_TYPE_XLSX = MediaType.parseMediaType(
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final TempsTravailService service;
	private final TempsTravailExcelService excelService;

	public TempsTravailAdminController(TempsTravailService service, TempsTravailExcelService excelService) {
		this.service = service;
		this.excelService = excelService;
	}

	@GetMapping
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public TempsTravailResponse consulter(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
			@RequestParam(name = "collaborateurId", required = false) UUID collaborateurId,
			@RequestParam(name = "siteId", required = false) UUID siteId,
			@RequestParam(name = "uniteId", required = false) UUID uniteId,
			@RequestParam(name = "etat", required = false) EtatJourHeuresSup etat,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return service.consulter(du, au, collaborateurId, siteId, uniteId, etat, page, size);
	}

	@GetMapping("/export")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<byte[]> exporter(
			@RequestParam(name = "mois", required = false) String mois,
			@RequestParam(name = "collaborateurIds", required = false) List<UUID> collaborateurIds,
			@RequestParam(name = "siteId", required = false) UUID siteId,
			@RequestParam(name = "uniteId", required = false) UUID uniteId,
			@RequestParam(name = "etat", required = false) EtatJourHeuresSup etat) {
		YearMonth ym = TempsTravailExcelService.validerMois(mois);
		List<UUID> idsValides = TempsTravailExcelService.validerCollaborateurs(collaborateurIds);
		byte[] xlsx = excelService.exporter(ym.toString(), idsValides, siteId, uniteId, etat);
		String filename = TempsTravailExcelService.nomFichier(ym);
		return ResponseEntity.ok()
				.contentType(MEDIA_TYPE_XLSX)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.body(xlsx);
	}
}
