package com.hr.evaluation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Détail complet d'une évaluation pour le back-office (ticket 2B, B3) : les deux séries de notes et l'écart. */
public record EvaluationDetail(
		EvaluationLigne entete,
		List<Question> questionsGenerales,
		List<Question> competencesTechniques,
		Scores scores,
		AlerteRef alerte,
		List<Evenement> historique) {

	/**
	 * @param ecart       |note responsable - note collaborateur| des compétences techniques notées des deux côtés
	 * @param niveauEcart ALIGNE, ORANGE, ROUGE ou URGENTE (compétences techniques seulement)
	 */
	public record Question(
			UUID questionId,
			String libelle,
			String section,
			String type,
			String reponseCollaborateur,
			Integer noteCollaborateur,
			String reponseResponsable,
			Integer noteResponsable,
			String commentaireResponsable,
			Integer ecart,
			String niveauEcart) {
	}

	/** D-E14 : une moyenne est nulle tant que personne de ce côté n'a noté. */
	public record Scores(BigDecimal moyenneCollaborateur, BigDecimal moyenneResponsable, Integer scoreSur20,
			String appreciation, String appreciationLibelle) {
	}

	public record AlerteRef(UUID identifiant, String niveau, String statut) {
	}

	/** evenement : CREEE, VALIDEE_COLLABORATEUR ou VALIDEE_RESPONSABLE. */
	public record Evenement(String evenement, Instant le, String acteurNom) {
	}
}
