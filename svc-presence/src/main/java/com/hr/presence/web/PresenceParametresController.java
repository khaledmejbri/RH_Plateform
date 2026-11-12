package com.hr.presence.web;

import com.hr.presence.dto.ParametresDtos.DescripteurReferentiel;
import com.hr.presence.dto.ParametresDtos.Libelles;
import com.hr.presence.dto.ParametresDtos.OrdreRequest;
import com.hr.presence.dto.ParametresDtos.PageJournal;
import com.hr.presence.dto.ParametresDtos.Reglage;
import com.hr.presence.dto.ParametresDtos.ReglageRequest;
import com.hr.presence.dto.ParametresDtos.Valeur;
import com.hr.presence.dto.ParametresDtos.ValeurRequest;
import com.hr.presence.dto.ParametresDtos.VersionRequest;
import com.hr.presence.service.PresenceParametresAdminService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Paramètres du pointage (D-R01 à D-R10, ADR lot 3 § 7) : habillage des statuts (comportement) et réglages bornés.
 * Lecture back-office RH / DIRECTION / ADMIN, écriture RH / ADMIN, libellés pour tout utilisateur authentifié.
 */
@RestController
public class PresenceParametresController {

	private static final String BASE = "/api/rh/v1/admin/presence/parametres";
	private static final String LIBELLES = "/api/rh/v1/presence/parametres/libelles";

	private final PresenceParametresAdminService service;

	public PresenceParametresController(PresenceParametresAdminService service) {
		this.service = service;
	}

	@GetMapping(LIBELLES)
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Libelles> libelles(@RequestParam(name = "actifs", defaultValue = "false") boolean actifs,
											 @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String siNonCorrespond) {
		Libelles corps = service.libelles(actifs);
		// Empreinte du contenu réellement servi (filtre et profil compris) : jamais de 304 croisé entre deux profils.
		String etag = "W/\"" + Integer.toHexString(corps.referentiels().hashCode()) + "\"";
		if (etag.equals(siNonCorrespond)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED).cacheControl(CacheControl.noCache()).eTag(etag).build();
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noCache()).eTag(etag)
				.body(new Libelles(etag, corps.referentiels()));
	}

	@GetMapping(BASE + "/referentiels")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public List<DescripteurReferentiel> referentiels() {
		return service.referentiels();
	}

	@GetMapping(BASE + "/referentiels/{ref}/valeurs")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public List<Valeur> valeurs(@PathVariable String ref) {
		return service.valeurs(ref);
	}

	/** Toujours 405 pour un référentiel de comportement (404 si le référentiel n'existe pas). */
	@PostMapping(BASE + "/referentiels/{ref}/valeurs")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public Valeur creer(@PathVariable String ref) {
		return service.creer(ref);
	}

	@PutMapping(BASE + "/referentiels/{ref}/valeurs/{code}")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public Valeur modifier(@PathVariable String ref, @PathVariable String code, @RequestBody ValeurRequest request) {
		return service.modifier(ref, code, request);
	}

	@PostMapping(BASE + "/referentiels/{ref}/valeurs/{code}/desactiver")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public Valeur desactiver(@PathVariable String ref, @PathVariable String code, @RequestBody(required = false) VersionRequest request) {
		return service.desactiver(ref);
	}

	@PostMapping(BASE + "/referentiels/{ref}/valeurs/{code}/reactiver")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public Valeur reactiver(@PathVariable String ref, @PathVariable String code, @RequestBody(required = false) VersionRequest request) {
		return service.desactiver(ref);
	}

	@PutMapping(BASE + "/referentiels/{ref}/ordre")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public List<Valeur> ordonner(@PathVariable String ref, @RequestBody OrdreRequest request) {
		return service.ordonner(ref, request.codes());
	}

	@DeleteMapping(BASE + "/referentiels/{ref}/valeurs/{code}")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public ResponseEntity<Void> supprimer(@PathVariable String ref, @PathVariable String code,
										  @RequestParam(name = "version", required = false) Long version) {
		service.supprimer(ref);
		return ResponseEntity.noContent().build();
	}

	@GetMapping(BASE + "/referentiels/{ref}/journal")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public PageJournal journal(@PathVariable String ref, @RequestParam(defaultValue = "0") int page,
							   @RequestParam(defaultValue = "20") int taille) {
		return service.journal(ref, page, taille);
	}

	@GetMapping(BASE + "/reglages")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public List<Reglage> reglages() {
		return service.reglages();
	}

	@PutMapping(BASE + "/reglages/{cle}")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_ECRITURE)
	public Reglage modifierReglage(@PathVariable String cle, @Valid @RequestBody ReglageRequest request) {
		return service.modifierReglage(cle, request.valeur(), request.version());
	}

	@GetMapping(BASE + "/reglages/journal")
	@PreAuthorize(PresenceSecurityExpressions.BACKOFFICE_LECTURE)
	public PageJournal journalReglages(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int taille) {
		return service.journalReglages(page, taille);
	}
}
