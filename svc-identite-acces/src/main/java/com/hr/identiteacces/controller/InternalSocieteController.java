package com.hr.identiteacces.controller;

import com.hr.identiteacces.dto.SocieteDtos.SocieteResponse;
import com.hr.identiteacces.service.SocieteService;
import com.hr.identiteacces.web.IdentiteMetierException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints internes de lecture du registre des sociétés (§ 9.7.1).
 * Protégés par {@code X-Internal-Token}, dispensés de {@code X-Societe-Id}.
 */
@RestController
@RequestMapping("/internal/identite/v1/societes")
public class InternalSocieteController {

	private final SocieteService societeService;
	private final String internalApiToken;

	public InternalSocieteController(
			SocieteService societeService,
			@Value("${app.internal-api.token}") String internalApiToken) {
		this.societeService = societeService;
		this.internalApiToken = internalApiToken;
	}

	@GetMapping
	public List<SocieteResponse> lister(
			@RequestParam(name = "statut", required = false) String statut,
			@RequestHeader(value = "X-Internal-Token", required = false) String token) {
		verifierJeton(token);
		return societeService.listerInterne(statut);
	}

	@GetMapping("/par-code/{code}")
	public SocieteResponse trouverParCode(
			@PathVariable String code,
			@RequestHeader(value = "X-Internal-Token", required = false) String token) {
		verifierJeton(token);
		return societeService.trouverParCode(code);
	}

	@GetMapping("/{id}")
	public SocieteResponse trouverParId(
			@PathVariable UUID id,
			@RequestHeader(value = "X-Internal-Token", required = false) String token) {
		verifierJeton(token);
		return societeService.trouverParId(id);
	}

	private void verifierJeton(String token) {
		if (token == null || internalApiToken == null || !MessageDigest.isEqual(
				token.getBytes(StandardCharsets.UTF_8),
				internalApiToken.getBytes(StandardCharsets.UTF_8))) {
			throw IdentiteMetierException.forbidden("JETON_INTERNE_INVALIDE", "Jeton interne absent ou invalide.");
		}
	}
}
