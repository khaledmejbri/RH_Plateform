package com.hr.presence.web;

/** Reconnaît la violation d'une contrainte unique nommée dans la chaîne des causes d'une exception d'intégrité. */
public final class ContraintesUniques {

	public static final String HEURES_SUP_JOUR = "uk_presence_hs_jour_collab_site_journee";
	public static final String HORAIRE_SITE_JOUR = "uk_presence_horaire_site_jour";

	private ContraintesUniques() {
	}

	public static boolean viole(Throwable erreur, String contrainte) {
		for (Throwable t = erreur; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t.getMessage() != null && t.getMessage().contains(contrainte)) {
				return true;
			}
		}
		return false;
	}
}
