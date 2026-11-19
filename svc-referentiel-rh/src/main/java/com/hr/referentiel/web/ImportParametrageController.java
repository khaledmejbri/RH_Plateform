package com.hr.referentiel.web;

import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.EnregistrerRequest;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.PageJournalImport;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.Parametrage;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.SynonymeRequest;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.VersionRequest;
import com.hr.referentiel.importation.parametrage.ImportParametrageService;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.service.AuteurParametreResolver;
import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Paramétrage des champs de l'import Excel (ADR-IMPORT-CHAMPS, annexe A, P1 à P7 et 405). Droits : lecture RH /
 * DIRECTION / ADMIN, écriture RH / ADMIN. Jamais de logique ici : le service contrôle, écrit et journalise.
 * Auteur toujours tiré du JWT.
 */
@RestController
@RequestMapping("/api/referentiel/v1/parametres/imports/{type}")
public class ImportParametrageController {

	private final ImportParametrageService service;
	private final AuteurParametreResolver auteurResolver;

	public ImportParametrageController(ImportParametrageService service, AuteurParametreResolver auteurResolver) {
		this.service = service;
		this.auteurResolver = auteurResolver;
	}

	/** P1 : paramétrage et version courante ({@code ETag}), lu directement en base. */
	@GetMapping
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<Parametrage> lire(@PathVariable String type,
			@RequestHeader(name = "If-None-Match", required = false) String siAucuneCorrespondance) {
		Parametrage p = service.lire(type);
		String etag = "\"" + p.version() + "\"";
		if (siAucuneCorrespondance != null && siAucuneCorrespondance.contains(etag)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(CacheControl.noCache()).build();
		}
		return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache()).body(p);
	}

	/** P2 : enregistre tout le tableau (champs dans le nouvel ordre). */
	@PutMapping
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Parametrage> enregistrer(@PathVariable String type,
			@RequestBody(required = false) EnregistrerRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.enregistrer(type, requete, auteurResolver.depuis(principal.getToken())));
	}

	/** P3. */
	@PostMapping("/champs/{code}/synonymes")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Parametrage> ajouterSynonyme(@PathVariable String type, @PathVariable String code,
			@RequestBody(required = false) SynonymeRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(service.ajouterSynonyme(type, code, requete, auteurResolver.depuis(principal.getToken())));
	}

	/** P4a. */
	@PostMapping("/synonymes/{id}/desactiver")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Parametrage> desactiver(@PathVariable String type, @PathVariable UUID id,
			@RequestBody(required = false) VersionRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.desactiver(type, id, version(requete),
				auteurResolver.depuis(principal.getToken())));
	}

	/** P4b. */
	@PostMapping("/synonymes/{id}/reactiver")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Parametrage> reactiver(@PathVariable String type, @PathVariable UUID id,
			@RequestBody(required = false) VersionRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.reactiver(type, id, version(requete),
				auteurResolver.depuis(principal.getToken())));
	}

	/** P5 : 204 sans corps (l'écran relit P1 pour la nouvelle version). */
	@DeleteMapping("/synonymes/{id}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Void> supprimer(@PathVariable String type, @PathVariable UUID id,
			@RequestParam(name = "version", required = false) Long version, JwtAuthenticationToken principal) {
		service.supprimer(type, id, version, auteurResolver.depuis(principal.getToken()));
		return ResponseEntity.noContent().build();
	}

	/** P6. */
	@PostMapping("/reinitialiser")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Parametrage> reinitialiser(@PathVariable String type,
			@RequestBody(required = false) VersionRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(service.reinitialiser(type, version(requete), auteurResolver.depuis(principal.getToken())));
	}

	/** P7. */
	@GetMapping("/journal")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<PageJournalImport> journal(@PathVariable String type,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille) {
		return ResponseEntity.ok(service.lireJournal(type, page, taille));
	}

	/** P8 : la liste des champs est fixe (D-I14). */
	@PostMapping("/champs")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Void> creerChamp(@PathVariable String type) {
		throw champsFixes();
	}

	@DeleteMapping("/champs/{code}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Void> supprimerChamp(@PathVariable String type, @PathVariable String code) {
		throw champsFixes();
	}

	private static ReferentielMetierException champsFixes() {
		return ReferentielMetierException.methodNotAllowed("PARAMETRE_COMPORTEMENT", "La liste des champs d'import est fixe.");
	}

	private static Long version(VersionRequest requete) {
		return requete == null ? null : requete.version();
	}
}
