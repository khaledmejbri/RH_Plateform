package com.hr.referentiel.web;

import com.hr.referentiel.dto.PageReferentielResponse;
import com.hr.referentiel.dto.SoldeDtos.Mouvement;
import com.hr.referentiel.dto.SoldeDtos.Simulation;
import com.hr.referentiel.dto.SoldeDtos.Solde;
import com.hr.referentiel.service.SoldeCongeLectureService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Solde de congé du collaborateur connecté (A1 à A3, mobile). Aucun identifiant n'est accepté : la fiche est résolue
 * depuis le JWT ; l'en-tête {@code X-Collaborateur-Id} et tout paramètre {@code collaborateurId} sont ignorés.
 */
@RestController
@RequestMapping("/api/rh/v1/conges")
public class CongeCollaborateurController {

	private final SoldeCongeLectureService lectureService;

	public CongeCollaborateurController(SoldeCongeLectureService lectureService) {
		this.lectureService = lectureService;
	}

	@GetMapping("/solde/moi")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<Solde> moi(JwtAuthenticationToken principal) {
		return ResponseEntity.ok(lectureService.moi(principal.getToken()));
	}

	@GetMapping("/solde/moi/mouvements")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<PageReferentielResponse<Mouvement>> mouvements(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille,
			@RequestParam(name = "type", required = false) String type,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(lectureService.mesMouvements(principal.getToken(), type, page, taille));
	}

	/** Simulation d'un congé annuel sans écriture : jours décomptés, solde après, blocage éventuel. */
	@GetMapping("/simulation")
	@PreAuthorize("hasRole('USER')")
	public ResponseEntity<Simulation> simulation(
			@RequestParam(name = "date_debut") LocalDate dateDebut,
			@RequestParam(name = "date_fin") LocalDate dateFin,
			@RequestParam(name = "periode_premier_jour", required = false) String premier,
			@RequestParam(name = "periode_dernier_jour", required = false) String dernier,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(lectureService.simuler(principal.getToken(), dateDebut, dateFin, premier, dernier));
	}
}
