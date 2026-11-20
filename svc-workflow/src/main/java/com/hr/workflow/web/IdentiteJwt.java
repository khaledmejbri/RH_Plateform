package com.hr.workflow.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/**
 * Extraction sécurisée de l'identité de l'appelant depuis le JWT (§ 9.10).
 */
public final class IdentiteJwt {

	private IdentiteJwt() {
	}

	public static UUID compteId(Authentication auth) {
		if (auth instanceof JwtAuthenticationToken jwtAuth) {
			Jwt jwt = jwtAuth.getToken();
			String idClaim = jwt.getClaimAsString("identifiant_utilisateur");
			if (idClaim != null && !idClaim.isBlank()) {
				try {
					return UUID.fromString(idClaim.trim());
				} catch (IllegalArgumentException ignored) {
				}
			}
			String sub = jwt.getSubject();
			if (sub != null && !sub.isBlank()) {
				try {
					return UUID.fromString(sub.trim());
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
		throw WorkflowMetierException.forbidden("IDENTITE_ABSENTE",
				"Identifiant utilisateur absent du jeton d'authentification.");
	}

	public static String nomAffiche(Authentication auth) {
		if (auth instanceof JwtAuthenticationToken jwtAuth) {
			Jwt jwt = jwtAuth.getToken();
			String nom = jwt.getClaimAsString("nom_complet");
			if (nom != null && !nom.isBlank()) {
				return nom.trim();
			}
			String sub = jwt.getSubject();
			if (sub != null && !sub.isBlank()) {
				return sub.trim();
			}
		}
		return "Utilisateur";
	}

	public static boolean aRole(Authentication auth, String roleSansPrefixe) {
		if (auth == null) {
			return false;
		}
		String attendu = "ROLE_" + roleSansPrefixe;
		for (GrantedAuthority a : auth.getAuthorities()) {
			if (attendu.equals(a.getAuthority()) || roleSansPrefixe.equals(a.getAuthority())) {
				return true;
			}
		}
		return false;
	}

	public static boolean estBackofficeLecture(Authentication auth) {
		return aRole(auth, "RH") || aRole(auth, "ADMIN") || aRole(auth, "DIRECTION");
	}

	public static boolean estBackofficeEcriture(Authentication auth) {
		return aRole(auth, "RH") || aRole(auth, "ADMIN");
	}
}
