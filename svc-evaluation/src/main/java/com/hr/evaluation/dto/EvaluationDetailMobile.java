package com.hr.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Détail d'une évaluation pour le mobile (ticket 2B, B7). D-E15 : pour le collaborateur, tant que l'évaluation n'est
 * pas validée, {@code reponseResponsable}, {@code noteResponsable}, {@code commentaireResponsable}, {@code scores}
 * et {@code scoreSur20} sont absents et {@code messageAttente} explique pourquoi. Jamais de champ d'alerte d'écart
 * ni de niveau d'écart (D-E09), quel que soit l'appelant.
 *
 * @param monRole COLLABORATEUR ou RESPONSABLE
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationDetailMobile(
		UUID identifiant,
		String campagneNom,
		String collaborateurNom,
		String collaborateurMatricule,
		String collaborateurPoste,
		String superieurNom,
		String statut,
		String etapeActuelle,
		String familleMetierLibelle,
		String niveauSenioriteLibelle,
		String monRole,
		String messageAttente,
		Integer scoreSur20,
		Scores scores,
		List<Question> questionsGenerales,
		List<Question> competencesTechniques,
		Instant validationCollaborateurLe,
		Instant validationSuperieurLe,
		Instant creeLe) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Question(
			UUID questionId,
			String libelle,
			String section,
			String type,
			String reponseCollaborateur,
			Integer noteCollaborateur,
			String reponseResponsable,
			Integer noteResponsable,
			String commentaireResponsable) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Scores(BigDecimal moyenneCollaborateur, BigDecimal moyenneResponsable, String appreciation,
			String appreciationLibelle) {
	}
}
