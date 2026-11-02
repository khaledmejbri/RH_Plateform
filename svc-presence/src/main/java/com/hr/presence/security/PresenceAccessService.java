package com.hr.presence.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Rôles de l'appelant et lecture de son identité dans le JWT.
 * <p>
 * L'en-tête {@code X-Collaborateur-Id} n'est <b>pas</b> une preuve d'identité : il n'est lu que par
 * {@link #collaborateurHeaderOuNull()} pour être comparé à la fiche résolue au référentiel avec le JWT
 * (voir {@code FacialIdentiteService}). Aucun identifiant de collaborateur ne doit être déduit de lui.
 */
@Service("presenceAccess")
public class PresenceAccessService {

	public boolean isBackofficeLecture() {
		return hasAnyRole("RH", "DIRECTION", "ADMIN");
	}

	public boolean isBackofficeEcriture() {
		return hasAnyRole("RH", "ADMIN");
	}

	/** Compte de l'appelant lu uniquement dans le JWT (claim {@code identifiant_utilisateur}) ; null sinon. */
	public String currentCompteId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
			Set<String> out = new LinkedHashSet<>();
			addUuid(out, jwt.getClaimAsString("identifiant_utilisateur"));
			return out.isEmpty() ? null : out.iterator().next();
		}
		return null;
	}

	/** En-tête {@code X-Collaborateur-Id} s'il est présent (UUID normalisé), sinon null ; 403 s'il est mal formé. */
	public String collaborateurHeaderOuNull() {
		return readCollaborateurHeader();
	}

	/** Rôle métier de l'appelant pour l'audit : ADMIN, RH, DIRECTION, sinon USER. */
	public String currentActorRole() {
		for (String role : new String[] {"ADMIN", "RH", "DIRECTION"}) {
			if (hasAnyRole(role)) {
				return role;
			}
		}
		return "USER";
	}

	/** Libellé d'audit de l'acteur, lu dans le JWT (jamais dans l'en-tête client) : identifiant du compte, sinon nom. */
	public String currentActorLabel() {
		Set<String> fromJwt = jwtIdentityCandidates();
		if (!fromJwt.isEmpty()) {
			return fromJwt.iterator().next();
		}
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null ? auth.getName() : "system";
	}

	private String readCollaborateurHeader() {
		var attrs = RequestContextHolder.getRequestAttributes();
		if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
			return null;
		}
		String header = servletAttrs.getRequest().getHeader("X-Collaborateur-Id");
		if (header == null || header.isBlank()) {
			return null;
		}
		String trimmed = header.trim();
		try {
			return UUID.fromString(trimmed).toString();
		} catch (IllegalArgumentException e) {
			throw new SecurityException("X-Collaborateur-Id invalide");
		}
	}

	private Set<String> jwtIdentityCandidates() {
		Set<String> out = new LinkedHashSet<>();
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return out;
		}
		Object principal = authentication.getPrincipal();
		if (!(principal instanceof Jwt jwt)) {
			return out;
		}
		addUuid(out, jwt.getClaimAsString("identifiant_utilisateur"));
		addUuid(out, jwt.getClaimAsString("collaborateur_id"));
		addUuid(out, jwt.getSubject());
		return out;
	}

	private static void addUuid(Set<String> out, String raw) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		try {
			out.add(UUID.fromString(raw.trim()).toString());
		} catch (IllegalArgumentException ignored) {
			// subject may be matricule/username
		}
	}

	private boolean hasAnyRole(String... roles) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return false;
		}
		Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
		if (authorities == null || authorities.isEmpty()) {
			return false;
		}
		for (String role : roles) {
			String expected = "ROLE_" + role;
			for (GrantedAuthority authority : authorities) {
				if (expected.equals(authority.getAuthority())) {
					return true;
				}
			}
		}
		return false;
	}
}
