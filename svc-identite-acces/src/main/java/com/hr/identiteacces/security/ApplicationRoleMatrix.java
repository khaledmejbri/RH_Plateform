package com.hr.identiteacces.security;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Matrice applicative : certains rôles métier incluent les droits du rôle collaborateur de base (USER).
 * <ul>
 *   <li>RH, DIRECTION, ADMIN : accès back-office ; incluent aussi USER pour les parcours collaborateur.</li>
 *   <li>RO / RESPONSABLE : idem (responsable organisationnel).</li>
 * </ul>
 * Les API réservées au back-office web sont protégées par {@code hasAnyRole('RH','DIRECTION','ADMIN')}.
 */
public final class ApplicationRoleMatrix {

	private static final Set<String> ROLES_IMPLYING_USER = Set.of(
			"RH", "DIRECTION", "ADMIN", "RESPONSABLE", "RO");

	private ApplicationRoleMatrix() {
	}

	/**
	 * Retourne les rôles effectifs pour l'authentification et le JWT (ajoute USER si pertinent).
	 */
	public static Set<String> expandWithImplicitUser(Set<String> roles) {
		if (roles == null || roles.isEmpty()) {
			return Set.of("USER");
		}
		LinkedHashSet<String> out = new LinkedHashSet<>();
		for (String r : roles) {
			if (r != null && !r.isBlank()) {
				out.add(r.trim().toUpperCase(Locale.ROOT));
			}
		}
		if (out.isEmpty()) {
			return Set.of("USER");
		}
		boolean needsUser = out.stream().anyMatch(ROLES_IMPLYING_USER::contains);
		if (needsUser) {
			out.add("USER");
		}
		return out;
	}
}
