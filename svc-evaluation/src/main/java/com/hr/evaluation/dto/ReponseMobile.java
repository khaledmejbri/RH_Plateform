package com.hr.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Réponse d'une question pour le mobile (ticket 2B, B8). Remplace l'entité JPA qui était exposée telle quelle.
 * D-E15 : pour le collaborateur, les champs du responsable ({@code reponseManager}, {@code commentaireManager},
 * {@code noteManager}, {@code reponduParManagerLe}) sont <b>absents</b> (pas à zéro) tant que l'évaluation n'est pas
 * validée. Les noms de champs du responsable restent ceux que l'appli lit déjà. Aucune information d'alerte d'écart.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReponseMobile(
		UUID identifiant,
		UUID questionId,
		String libelle,
		String reponseCollaborateur,
		Integer noteCollaborateur,
		Instant reponduParCollaborateurLe,
		String reponseManager,
		String commentaireManager,
		Integer noteManager,
		Instant reponduParManagerLe) {
}
