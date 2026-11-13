package com.hr.referentiel.web;

import com.hr.referentiel.dto.PageReferentielResponse;
import com.hr.referentiel.dto.SoldeDtos.AjustementRequest;
import com.hr.referentiel.dto.SoldeDtos.AjustementResponse;
import com.hr.referentiel.dto.SoldeDtos.Mouvement;
import com.hr.referentiel.dto.SoldeDtos.SoldeLigne;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.service.AjustementSoldeService;
import com.hr.referentiel.service.SoldeCongeLectureService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Soldes de congé, back-office (B1 à B6). Lecture : RH, DIRECTION, ADMIN ; ajustement : RH, ADMIN (jamais sur sa propre
 * fiche). Un collaborateur ou un responsable reçoit 403 que la fiche existe ou non.
 */
@RestController
@RequestMapping("/api/rh/v1/admin/conges/soldes")
public class SoldeCongeAdminController {

	private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");

	private final SoldeCongeLectureService lectureService;
	private final AjustementSoldeService ajustementService;

	public SoldeCongeAdminController(SoldeCongeLectureService lectureService, AjustementSoldeService ajustementService) {
		this.lectureService = lectureService;
		this.ajustementService = ajustementService;
	}

	@GetMapping
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<PageReferentielResponse<SoldeLigne>> liste(
			@RequestParam(name = "q", required = false) String q,
			@RequestParam(name = "unite_id", required = false) UUID uniteId,
			@RequestParam(name = "statut", required = false) String statut,
			@RequestParam(name = "tri", required = false) String tri,
			@RequestParam(name = "sens", required = false) String sens,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(lectureService.liste(q, uniteId, statut, tri, sens, page, taille, principal.getToken()));
	}

	@GetMapping("/export")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<byte[]> exporter(
			@RequestParam(name = "q", required = false) String q,
			@RequestParam(name = "unite_id", required = false) UUID uniteId,
			@RequestParam(name = "statut", required = false) String statut,
			@RequestParam(name = "tri", required = false) String tri,
			@RequestParam(name = "sens", required = false) String sens,
			JwtAuthenticationToken principal) {
		return csv("soldes-conges.csv", lectureService.exporter(q, uniteId, statut, tri, sens, principal.getToken()));
	}

	@GetMapping("/{collaborateurId}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<SoldeLigne> solde(@PathVariable UUID collaborateurId, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(lectureService.soldeDe(collaborateurId, principal.getToken()));
	}

	@GetMapping("/{collaborateurId}/mouvements")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<PageReferentielResponse<Mouvement>> mouvements(@PathVariable UUID collaborateurId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille,
			@RequestParam(name = "type", required = false) String type) {
		return ResponseEntity.ok(lectureService.mouvementsDe(collaborateurId, type, page, taille));
	}

	@GetMapping("/{collaborateurId}/mouvements/export")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<byte[]> exporterMouvements(@PathVariable UUID collaborateurId) {
		return csv("mouvements-solde.csv", lectureService.exporterMouvements(collaborateurId));
	}

	@PostMapping("/{collaborateurId}/ajustements")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<AjustementResponse> ajuster(@PathVariable UUID collaborateurId,
			@RequestBody AjustementRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(ajustementService.ajuster(collaborateurId, requete, principal.getToken()));
	}

	private static ResponseEntity<byte[]> csv(String nom, byte[] contenu) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
				.contentType(CSV).body(contenu);
	}
}
