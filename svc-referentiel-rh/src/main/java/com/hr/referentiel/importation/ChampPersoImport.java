package com.hr.referentiel.importation;

import com.hr.referentiel.importation.parametrage.ExigenceChamp;

import java.util.List;

/**
 * Champ personnalisé vu comme un champ d'import (ADR partie 2, P2-8) : le code ({@code PERSO_…}) tient lieu de nom,
 * le libellé est celui de la définition. Aucun synonyme par défaut, aucun verrou.
 */
public record ChampPersoImport(String name, String libelle) implements ChampImport {

	@Override
	public List<String> synonymes() {
		return List.of();
	}

	@Override
	public ExigenceChamp exigenceDefaut() {
		return ExigenceChamp.FACULTATIF;
	}

	@Override
	public ExigenceChamp exigenceMinimale() {
		return ExigenceChamp.FACULTATIF;
	}

	@Override
	public String verrouLibelle() {
		return null;
	}
}
