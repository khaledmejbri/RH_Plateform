package com.hr.referentiel.web;

import com.hr.referentiel.dto.JourFerieDtos.JourFerieActif;
import com.hr.referentiel.dto.JourFerieDtos.JourFerieRequest;
import com.hr.referentiel.dto.JourFerieDtos.JourFerieResponse;
import com.hr.referentiel.dto.JourFerieDtos.VersionJourFerieRequest;
import com.hr.referentiel.dto.ParametreDto.PageJournal;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.service.AuteurParametreResolver;
import com.hr.referentiel.service.JourFerieService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Jours fériés du paramétrage (P4 à P10) : RH et ADMIN écrivent, DIRECTION lit, les dates actives sont ouvertes à tous. */
@RestController
@RequestMapping("/api/referentiel/v1/parametres/jours-feries")
public class JourFerieController {

	private final JourFerieService service;
	private final AuteurParametreResolver auteurResolver;

	public JourFerieController(JourFerieService service, AuteurParametreResolver auteurResolver) {
		this.service = service;
		this.auteurResolver = auteurResolver;
	}

	@GetMapping
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<List<JourFerieResponse>> lister(@RequestParam(name = "annee", required = false) Integer annee) {
		return ResponseEntity.ok(service.lister(annee));
	}

	/** Dates actives seulement : lisible par tout utilisateur authentifié (écrans de saisie). */
	@GetMapping("/actifs")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<List<JourFerieActif>> actifs(@RequestParam(name = "annee", required = false) Integer annee) {
		return ResponseEntity.ok(service.actifs(annee));
	}

	@GetMapping("/journal")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<PageJournal> journal(@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille) {
		return ResponseEntity.ok(service.journal(page, taille));
	}

	@PostMapping
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<JourFerieResponse> creer(@RequestBody JourFerieRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(service.creer(requete, auteurResolver.depuis(principal.getToken())));
	}

	@PutMapping("/{id}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<JourFerieResponse> modifier(@PathVariable UUID id, @RequestBody JourFerieRequest requete,
			JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.modifier(id, requete, auteurResolver.depuis(principal.getToken())));
	}

	@PostMapping("/{id}/desactiver")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<JourFerieResponse> desactiver(@PathVariable UUID id,
			@RequestBody VersionJourFerieRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.desactiver(id, requete.version(), auteurResolver.depuis(principal.getToken())));
	}

	@PostMapping("/{id}/reactiver")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<JourFerieResponse> reactiver(@PathVariable UUID id,
			@RequestBody VersionJourFerieRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.reactiver(id, requete.version(), auteurResolver.depuis(principal.getToken())));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Void> supprimer(@PathVariable UUID id,
			@RequestParam(name = "version", required = false) Long version, JwtAuthenticationToken principal) {
		service.supprimer(id, version, auteurResolver.depuis(principal.getToken()));
		return ResponseEntity.noContent().build();
	}
}
