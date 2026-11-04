package com.hr.evaluation.client;

import java.util.UUID;

/** Fiche de l'appelant (JWT) lue au référentiel : sert au repli de comparaison et au libellé d'auteur. */
public record MoiReferentiel(UUID identifiant, String prenom, String nom) {

	public String nomComplet() {
		String complet = ((prenom != null ? prenom : "") + " " + (nom != null ? nom : "")).trim();
		return complet.isEmpty() ? null : complet;
	}
}
