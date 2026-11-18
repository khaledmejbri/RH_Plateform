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
	 * Catalogue {@code profil_acces} → rôles JWT (remplacement, pas union).
	 * {@code null} / blank / inconnu → {@code {USER}} (équivalent COLLABORATEUR).
	 * RO et RESPONSABLE sont distincts.
	 */
	public static Set<String> rolesPourProfil(String profilAcces) {
		if (profilAcces == null || profilAcces.isBlank()) {
			return Set.of("USER");
		}
		return switch (profilAcces.trim().toUpperCase(Locale.ROOT)) {
			case "COLLABORATEUR" -> Set.of("USER");
			case "RO" -> ordered("USER", "RO");
			case "RESPONSABLE" -> ordered("USER", "RESPONSABLE");
			case "RH" -> ordered("USER", "RH");
			case "DIRECTION" -> ordered("USER", "DIRECTION");
			case "ADMIN" -> ordered("USER", "ADMIN");
			default -> Set.of("USER");
		};
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
		if (out.contains("PLATEFORME")) {
			return Set.of("PLATEFORME");
		}
		boolean needsUser = out.stream().anyMatch(ROLES_IMPLYING_USER::contains);
		if (needsUser) {
			out.add("USER");
		}
		return out;
	}

	private static Set<String> ordered(String... roles) {
		LinkedHashSet<String> out = new LinkedHashSet<>();
		for (String role : roles) {
			out.add(role);
		}
		return out;
	}
}
