package com.hr.evaluation.dto;

import java.time.Instant;
import java.util.UUID;

/** Détail d'une campagne pour le back-office (ticket 2B, B1). */
public record CampagneDetail(
		UUID identifiant,
		String nom,
		String description,
		String type,
		String statut,
		Integer annee,
		Integer moisDebut,
		Integer moisFin,
		Integer jourDebut,
		Instant dateDebut,
		Instant dateFin,
		Integer dureeJours,
		ModeleRef templateGeneral,
		ModeleRef templateTechnique,
		Compteurs compteurs,
		AlerteEcartOuvertesParNiveau alertesOuvertesParNiveau,
		Instant creeLe) {

	public record ModeleRef(UUID identifiant, String nom) {
	}

	/**
	 * Mêmes définitions que « Suivi & scores » : validées = statut VALIDEE, en cours = toutes les autres,
	 * taux = validées / total arrondi en pourcentage entier.
	 */
	public record Compteurs(long total, long enCours, long validees, long tauxCompletion,
			long sansQuestionnaireTechnique) {

		public static Compteurs de(long total, long validees, long sansQuestionnaireTechnique) {
			long taux = total == 0 ? 0 : Math.round(validees * 100.0 / total);
			return new Compteurs(total, total - validees, validees, taux, sansQuestionnaireTechnique);
		}
	}
}
