package com.hr.referentiel.web;

import com.hr.referentiel.dto.ParametreDto.DescripteurReferentiel;
import com.hr.referentiel.dto.ParametreDto.Libelles;
import com.hr.referentiel.dto.ParametreDto.OrdreRequest;
import com.hr.referentiel.dto.ParametreDto.PageJournal;
import com.hr.referentiel.dto.ParametreDto.Reglage;
import com.hr.referentiel.dto.ParametreDto.ReglageRequest;
import com.hr.referentiel.dto.ParametreDto.ValeurRequest;
import com.hr.referentiel.dto.ParametreDto.ValeurResponse;
import com.hr.referentiel.dto.ParametreDto.VersionRequest;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.service.AuteurParametreResolver;
import com.hr.referentiel.service.ParametreService;
import com.hr.referentiel.service.ReglagesCongeService;
import jakarta.validation.Valid;
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

import java.util.List;

/**
 * Écran Paramètres, service référentiel (ADR-UAT-lot3 § 7, D-R01 à D-R10). Droits : lecture complète RH / DIRECTION /
 * ADMIN, écriture RH / ADMIN (mêmes droits), libellés actifs pour tout utilisateur authentifié.
 *
 * <p>Contrat : JSON snake_case. Une erreur porte {@code code}, {@code erreur} et {@code message} (même texte).
 * {@code PUT} d'une valeur : {@code version} obligatoire ; un champ absent ou {@code null} laisse la valeur
 * inchangée, une chaîne vide efface description et couleur ; un {@code code} éventuel dans le corps est
 * <b>ignoré</b> (le code est immuable, D-R02.a). {@code POST} / {@code DELETE} sur un référentiel de comportement
 * (statuts, types pilotés par une règle) : 405 {@code PARAMETRE_COMPORTEMENT}.
 */
@RestController
@RequestMapping("/api/referentiel/v1/parametres")
public class ParametreController {

	private final ParametreService parametreService;
	private final AuteurParametreResolver auteurResolver;
	private final ReglagesCongeService reglagesService;

	public ParametreController(ParametreService parametreService, AuteurParametreResolver auteurResolver,
			ReglagesCongeService reglagesService) {
		this.parametreService = parametreService;
		this.auteurResolver = auteurResolver;
		this.reglagesService = reglagesService;
	}

	/**
	 * Table de libellés (un seul appel par service, fusionné par le front). Non back-office : valeurs actives de la
	 * liste blanche seulement. Back-office : tout, {@code actifs=true} pour ne garder que les actives. Revalidation par
	 * ETag ({@code If-None-Match} donne un 304).
	 */
	@GetMapping("/libelles")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Libelles> libelles(
			@RequestParam(name = "actifs", required = false, defaultValue = "false") boolean actifs,
			@RequestHeader(name = "If-None-Match", required = false) String siAucuneCorrespondance) {
		Libelles libelles = parametreService.libelles(ReferentielApiSecurity.aAutoriteRh(), actifs);
		String etag = libelles.version();
		if (siAucuneCorrespondance != null && siAucuneCorrespondance.contains(etag)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag)
					.cacheControl(CacheControl.noCache()).build();
		}
		return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache()).body(libelles);
	}

	@GetMapping("/referentiels")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<List<DescripteurReferentiel>> referentiels() {
		return ResponseEntity.ok(parametreService.descripteurs());
	}

	@GetMapping("/referentiels/{referentiel}/valeurs")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<List<ValeurResponse>> valeurs(@PathVariable String referentiel) {
		return ResponseEntity.ok(parametreService.valeurs(referentiel));
	}

	@PostMapping("/referentiels/{referentiel}/valeurs")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<ValeurResponse> creer(@PathVariable String referentiel,
			@RequestBody ValeurRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.status(HttpStatus.CREATED).body(
				parametreService.creer(referentiel, requete, auteurResolver.depuis(principal.getToken())));
	}

	@PutMapping("/referentiels/{referentiel}/valeurs/{code}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<ValeurResponse> modifier(@PathVariable String referentiel, @PathVariable String code,
			@RequestBody ValeurRequest requete, JwtAuthenticationToken principal) {
		if (requete.version() == null) {
			throw ReferentielMetierException.badRequest("VERSION_OBLIGATOIRE",
					"La version de la valeur est obligatoire pour la modifier.");
		}
		return ResponseEntity.ok(parametreService.modifier(referentiel, code, requete, requete.version(),
				auteurResolver.depuis(principal.getToken())));
	}

	@PostMapping("/referentiels/{referentiel}/valeurs/{code}/desactiver")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<ValeurResponse> desactiver(@PathVariable String referentiel, @PathVariable String code,
			@Valid @RequestBody VersionRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(parametreService.desactiver(referentiel, code, requete.version(),
				auteurResolver.depuis(principal.getToken())));
	}

	@PostMapping("/referentiels/{referentiel}/valeurs/{code}/reactiver")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<ValeurResponse> reactiver(@PathVariable String referentiel, @PathVariable String code,
			@Valid @RequestBody VersionRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(parametreService.reactiver(referentiel, code, requete.version(),
				auteurResolver.depuis(principal.getToken())));
	}

	@PutMapping("/referentiels/{referentiel}/ordre")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<List<ValeurResponse>> reordonner(@PathVariable String referentiel,
			@Valid @RequestBody OrdreRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(parametreService.reordonner(referentiel, requete.codes(),
				auteurResolver.depuis(principal.getToken())));
	}

	@DeleteMapping("/referentiels/{referentiel}/valeurs/{code}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Void> supprimer(@PathVariable String referentiel, @PathVariable String code,
			@RequestParam(name = "version", required = false) Long version, JwtAuthenticationToken principal) {
		parametreService.supprimer(referentiel, code, version, auteurResolver.depuis(principal.getToken()));
		return ResponseEntity.noContent().build();
	}

	/** Historique des modifications (DIRECTION le lit aussi : Q11, choix par défaut). */
	@GetMapping("/referentiels/{referentiel}/journal")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<PageJournal> journal(@PathVariable String referentiel,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille) {
		return ResponseEntity.ok(parametreService.journal(referentiel, page, taille));
	}

	/** Réglage « Jours acquis par mois » (décimal : {@code pas} 0,5). */
	@GetMapping("/reglages")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<List<Reglage>> reglages() {
		return ResponseEntity.ok(reglagesService.reglages());
	}

	/** P12 : date de début des acquisitions et jours figés du mois courant (lecture seule, D-S07.b). */
	@GetMapping("/conges")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<ReglagesCongeService.InfoConges> conges() {
		return ResponseEntity.ok(reglagesService.info());
	}

	@PutMapping("/reglages/{cle}")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Reglage> modifierReglage(@PathVariable String cle,
			@Valid @RequestBody ReglageRequest requete, JwtAuthenticationToken principal) {
		return ResponseEntity.ok(reglagesService.modifier(cle, requete.valeur(), requete.version(),
				auteurResolver.depuis(principal.getToken())));
	}

	@GetMapping("/reglages/journal")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public ResponseEntity<PageJournal> journalReglages(@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "taille", defaultValue = "20") int taille) {
		return ResponseEntity.ok(reglagesService.journal(page, taille));
	}
}
