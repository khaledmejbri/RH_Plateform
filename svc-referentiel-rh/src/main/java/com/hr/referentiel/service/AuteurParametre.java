package com.hr.referentiel.service;

import java.util.UUID;

/** Auteur d'une modification de paramètre : compte lu dans le JWT, nom de la fiche (jamais un identifiant brut). */
public record AuteurParametre(UUID compteId, String nom) {

	public static final String NOM_SYSTEME = "Système (automatique)";

	public static AuteurParametre systeme() {
		return new AuteurParametre(null, NOM_SYSTEME);
	}
}
