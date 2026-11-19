package com.hr.referentiel.web;

import com.hr.referentiel.champsperso.ChampPersoDefinitionService;
import com.hr.referentiel.champsperso.ChampPersoDtos.CreerChampPerso;
import com.hr.referentiel.champsperso.ChampPersoDtos.ModifierChampPerso;
import com.hr.referentiel.champsperso.ChampPersoDtos.ValeurRequest;
import com.hr.referentiel.champsperso.ChampPersoListeService;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.Parametrage;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.VersionRequest;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.service.AuteurParametreResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Définitions des champs personnalisés, C1 à C6 (ADR partie 2, annexe A bis.2). Écriture : RH et ADMIN seulement
 * (DIRECTION 403). Aucune logique ici ; auteur toujours tiré du JWT.
 */
@RestController
@RequestMapping("/api/referentiel/v1/parametres/imports/{type}/champs-personnalises")
public class ChampPersoController {

	private final ChampPersoDefinitionService definitions;
	private final ChampPersoListeService listes;
	private final AuteurParametreResolver auteurResolver;

	public ChampPersoController(ChampPersoDefinitionService definitions, ChampPersoListeService listes,
			AuteurParametreResolver auteurResolver) {
		this.definitions = definitions;
		this.listes = listes;
		this.auteurResolver = auteurResolver;
	}

	private AuteurParametre auteur(JwtAuthenticationToken p) {
		return auteurResolver.depuis(p.getToken());
	}

	private static Long version(VersionRequest r) {
		return r == null ? null : r.version();
	}

	@PostMapping
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Parametrage> creer(@PathVariable String type, @RequestBody(required = false) CreerChampPerso requete,
			JwtAuthenticationToken principal) {
		return ResponseEntity.status(HttpStatus.CREATED).body(definitions.creer(type, requete, auteur(principal)));
	}

	@PutMapping("/{code}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Parametrage> modifier(@PathVariable String type, @PathVariable String code,
			@RequestBody(required = false) ModifierChampPerso requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(definitions.modifier(type, code, requete, auteur(principal)));
	}

	@PostMapping("/{code}/desactiver")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Parametrage> desactiver(@PathVariable String type, @PathVariable String code,
			@RequestBody(required = false) VersionRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(definitions.desactiver(type, code, version(requete), auteur(principal)));
	}

	@PostMapping("/{code}/reactiver")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Parametrage> reactiver(@PathVariable String type, @PathVariable String code,
			@RequestBody(required = false) VersionRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(definitions.reactiver(type, code, version(requete), auteur(principal)));
	}

	@DeleteMapping("/{code}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Void> supprimer(@PathVariable String type, @PathVariable String code,
			@RequestParam(name = "version", required = false) Long version, JwtAuthenticationToken principal) {
		definitions.supprimer(type, code, version, auteur(principal));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{code}/valeurs")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Parametrage> ajouterValeur(@PathVariable String type, @PathVariable String code,
			@RequestBody(required = false) ValeurRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.status(HttpStatus.CREATED).body(listes.ajouter(type, code, requete, auteur(principal)));
	}

	@PutMapping("/{code}/valeurs/{codeValeur}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Parametrage> modifierValeur(@PathVariable String type, @PathVariable String code,
			@PathVariable String codeValeur, @RequestBody(required = false) ValeurRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(listes.modifier(type, code, codeValeur, requete, auteur(principal)));
	}

	@PostMapping("/{code}/valeurs/{codeValeur}/desactiver")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Parametrage> desactiverValeur(@PathVariable String type, @PathVariable String code,
			@PathVariable String codeValeur, @RequestBody(required = false) VersionRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(listes.basculer(type, code, codeValeur, version(requete), false, auteur(principal)));
	}

	@PostMapping("/{code}/valeurs/{codeValeur}/reactiver")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Parametrage> reactiverValeur(@PathVariable String type, @PathVariable String code,
			@PathVariable String codeValeur, @RequestBody(required = false) VersionRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(listes.basculer(type, code, codeValeur, version(requete), true, auteur(principal)));
	}

	@DeleteMapping("/{code}/valeurs/{codeValeur}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Void> supprimerValeur(@PathVariable String type, @PathVariable String code,
			@PathVariable String codeValeur, @RequestParam(name = "version", required = false) Long version,
			JwtAuthenticationToken principal) {
		listes.supprimer(type, code, codeValeur, version, auteur(principal));
		return ResponseEntity.noContent().build();
	}
}
