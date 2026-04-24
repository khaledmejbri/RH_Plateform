package com.hr.referentiel.web;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

final class ReferentielApiSecurity {

	private ReferentielApiSecurity() {
	}

	/** Vrai si l'utilisateur a un rôle back-office (même logique que {@code hasAnyRole('RH','DIRECTION','ADMIN')}). */
	static boolean aAutoriteRh() {
		return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.anyMatch(ReferentielApiSecurity::estRoleBackoffice);
	}

	private static boolean estRoleBackoffice(String authority) {
		return switch (authority) {
			case "ROLE_RH", "ROLE_DIRECTION", "ROLE_ADMIN", "RH", "DIRECTION", "ADMIN" -> true;
			default -> false;
		};
	}
}
