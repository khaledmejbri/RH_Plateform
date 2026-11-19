package com.hr.referentiel.web;

import com.hr.referentiel.champsperso.ValeursPersoDtos.EcrireValeurs;
import com.hr.referentiel.champsperso.ValeursPersoDtos.MesChamps;
import com.hr.referentiel.champsperso.ValeursPersoDtos.PageJournalValeurs;
import com.hr.referentiel.champsperso.ValeursPersoDtos.ValeursFiche;
import com.hr.referentiel.champsperso.ValeursPersoFicheService;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Valeurs des champs personnalisés d'une fiche, V1 à V4 (annexe A bis). Lecture RH / DIRECTION / ADMIN, écriture RH /
 * ADMIN ; « moi » : tout appelant authentifié, fiche tirée du JWT uniquement (aucun identifiant lu dans la requête).
 */
@RestController
@RequestMapping("/api/referentiel/v1/collaborateurs")
public class ValeursPersoController {

	private final ValeursPersoFicheService service;

	public ValeursPersoController(ValeursPersoFicheService service) {
		this.service = service;
	}

	private static boolean peutEcrire(JwtAuthenticationToken p) {
		return p.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_RH") || a.getAuthority().equals("ROLE_ADMIN"));
	}

	@GetMapping("/{id}/champs-personnalises")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<ValeursFiche> lire(@PathVariable UUID id, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.lire(id, principal.getToken(), peutEcrire(principal)));
	}

	@PutMapping("/{id}/champs-personnalises")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<ValeursFiche> ecrire(@PathVariable UUID id, @RequestBody(required = false) EcrireValeurs requete,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.ecrire(id, requete, principal.getToken(), peutEcrire(principal)));
	}

	@GetMapping("/{id}/champs-personnalises/journal")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<PageJournalValeurs> journal(@PathVariable UUID id,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille) {
		return ResponseEntity.ok(service.journal(id, page, taille));
	}

	/** V4 : aucun paramètre lu (un {@code collaborateurId} éventuel est ignoré). */
	@GetMapping("/moi/champs-personnalises")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<MesChamps> moi(JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.moi(principal.getToken()));
	}
}
