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

	/** § 9.1 / C-M04-01 : une plainte EXTERNE est déclarée par un RO ou la RH (ADMIN = même périmètre que RH). */
	static boolean peutDeclarerPlainteExterne() {
		return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.anyMatch(a -> switch (a) {
					case "ROLE_RO", "ROLE_RH", "ROLE_ADMIN", "RO", "RH", "ADMIN" -> true;
					default -> false;
				});
	}

	private static boolean estRoleBackoffice(String authority) {
		return switch (authority) {
			case "ROLE_RH", "ROLE_DIRECTION", "ROLE_ADMIN", "RH", "DIRECTION", "ADMIN" -> true;
			default -> false;
		};
	}
}
