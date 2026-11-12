package com.hr.evaluation.dto;

import java.util.List;

/**
 * D-E12.c : alertes d'écart ouvertes (OUVERTE + EN_COURS) par niveau. Depuis le lot 3 (D-R04) les niveaux sont
 * paramétrés : une entrée par niveau actif (même à 0) et par niveau désactivé encore porté par une alerte, du seuil le
 * plus bas au plus haut. Aucun code de niveau n'est figé dans le contrat.
 */
public record AlerteEcartOuvertesParNiveau(List<Niveau> niveaux, long total) {

	/** {@code seuil} = écart minimal en étoiles ; {@code couleur} = code de palette. */
	public record Niveau(String code, String libelle, String couleur, int seuil, long nombre) {
	}

	public static AlerteEcartOuvertesParNiveau de(List<Niveau> niveaux) {
		return new AlerteEcartOuvertesParNiveau(List.copyOf(niveaux), niveaux.stream().mapToLong(Niveau::nombre).sum());
	}
}
