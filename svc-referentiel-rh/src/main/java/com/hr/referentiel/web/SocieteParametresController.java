package com.hr.referentiel.web;

import com.hr.referentiel.dto.SocieteParametresDtos.SocieteParametresJournalItem;
import com.hr.referentiel.dto.SocieteParametresDtos.SocieteParametresResponse;
import com.hr.referentiel.dto.SocieteParametresDtos.SocieteParametresUpdateRequest;
import com.hr.referentiel.security.PreAuthorizeExpressions;
import com.hr.referentiel.service.AuteurParametreResolver;
import com.hr.referentiel.service.SocieteParametresService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paramètres de fonctionnement d'une société (§ 4.2, US-04).
 */
@RestController
@RequestMapping("/api/rh/v1/admin/societe/parametres")
public class SocieteParametresController {

	private final SocieteParametresService service;
	private final AuteurParametreResolver auteurResolver;

	public SocieteParametresController(SocieteParametresService service, AuteurParametreResolver auteurResolver) {
		this.service = service;
		this.auteurResolver = auteurResolver;
	}

	@GetMapping
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public SocieteParametresResponse obtenir() {
		return service.obtenir();
	}

	@PutMapping
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_ECRITURE)
	public SocieteParametresResponse modifier(
			@Valid @RequestBody SocieteParametresUpdateRequest body,
			@AuthenticationPrincipal Jwt jwt) {
		return service.modifier(body, auteurResolver.depuis(jwt));
	}

	@GetMapping("/journal")
	@PreAuthorize(PreAuthorizeExpressions.BACKOFFICE_LECTURE)
	public Page<SocieteParametresJournalItem> journal(@PageableDefault(size = 20) Pageable pageable) {
		return service.journal(pageable);
	}
}
