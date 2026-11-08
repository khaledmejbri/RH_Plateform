package com.hr.evaluation.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Ligne d'évaluation destinée à un écran back-office (D-L01.a, ticket 2B) : noms, matricules et libellés sont déjà
 * résolus, l'écran n'a jamais besoin d'un identifiant pour afficher. Les trois derniers champs
 * ({@code campaignNom}, {@code collaborateurIdentifiant}, {@code superieurIdentifiant}) sont les anciennes clés,
 * gardées le temps que le web migre vers {@code campagneNom}, {@code collaborateurId}, {@code superieurId}.
 *
 * @param couleurAlerte  D-E12.b : nulle tant que le responsable n'a pas noté
 * @param niveauAlerte   niveau courant de l'alerte d'écart (ORANGE, ROUGE, URGENTE), nul sans alerte
 * @param alerteStatut   statut de l'alerte (OUVERTE, EN_COURS, TRAITEE), nul sans alerte
 * @param motifProfilIncomplet FAMILLE_ABSENTE, NIVEAU_ABSENT, LES_DEUX ou nul
 */
public record EvaluationLigne(
		UUID identifiant,
		UUID campagneId,
		String campagneNom,
		UUID collaborateurId,
		String collaborateurNom,
		String collaborateurMatricule,
		String collaborateurPoste,
		UUID superieurId,
		String superieurNom,
		String superieurMatricule,
		String statut,
		String etapeActuelle,
		Integer scoreSur20,
		String couleurAlerte,
		boolean aNoteResponsable,
		String niveauAlerte,
		String alerteStatut,
		UUID alerteId,
		String familleMetierCode,
		String familleMetierLibelle,
		String niveauSeniorite,
		String niveauSenioriteLibelle,
		boolean profilMetierIncomplet,
		String motifProfilIncomplet,
		Instant validationCollaborateurLe,
		Instant validationSuperieurLe,
		Instant creeLe,
		String campaignNom,
		UUID collaborateurIdentifiant,
		UUID superieurIdentifiant) {
}
