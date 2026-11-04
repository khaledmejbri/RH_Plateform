package com.hr.evaluation.security;

import com.hr.evaluation.client.MoiReferentiel;
import com.hr.evaluation.client.ReferentielMoiClient;
import com.hr.evaluation.web.EvaluationMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Construit l'{@link IdentiteAppelant} de la requête courante depuis le JWT (ADR M07 alertes d'écart § 6).
 * L'en-tête {@code X-Collaborateur-Id} n'est jamais une preuve d'identité.
 */
@Component
public class IdentiteAppelantResolver {

	private static final Logger log = LoggerFactory.getLogger(IdentiteAppelantResolver.class);

	private final ReferentielMoiClient moiClient;

	public IdentiteAppelantResolver(ReferentielMoiClient moiClient) {
		this.moiClient = moiClient;
	}

	public IdentiteAppelant courant() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
			throw new SecurityException("Impossible d'identifier l'acteur courant");
		}
		UUID compte = uuid(jwt.getClaimAsString("identifiant_utilisateur"));
		if (compte == null) {
			compte = uuid(jwt.getSubject());
		}
		boolean ecriture = false;
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			String a = authority.getAuthority();
			if ("ROLE_RH".equals(a) || "ROLE_ADMIN".equals(a)) {
				ecriture = true;
				break;
			}
		}
		return new IdentiteAppelant(compte, jwt.getSubject(), ecriture, this::lireMoi);
	}

	private Optional<MoiReferentiel> lireMoi() {
		try {
			return moiClient.moi();
		} catch (RuntimeException ex) {
			log.warn("Fiche de l'appelant illisible au référentiel : {}", ex.getMessage());
			throw new EvaluationMetierException("REFERENTIEL_INDISPONIBLE",
					"Le référentiel est indisponible, réessayez plus tard.", 503);
		}
	}

	private static UUID uuid(String brut) {
		if (brut == null || brut.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(brut.trim());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
