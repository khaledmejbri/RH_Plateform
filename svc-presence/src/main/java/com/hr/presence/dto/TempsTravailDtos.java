package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.domain.EtatJourHeuresSup;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Vue « Temps de travail » (D-P17), JSON en snake_case ; les instants sont des chaînes ISO-8601 avec décalage
 * (Africa/Tunis). Calcul à la volée : aucune de ces structures n'est persistée.
 */
public final class TempsTravailDtos {

	private TempsTravailDtos() {
	}

	/** Paire ENTREE / SORTIE comptée ; {@code minutes} est la durée tronquée à la minute. */
	public record PaireDto(
			@JsonProperty("entree") String entree,
			@JsonProperty("sortie") String sortie,
			@JsonProperty("entree_manuelle") boolean entreeManuelle,
			@JsonProperty("sortie_manuelle") boolean sortieManuelle,
			@JsonProperty("minutes") int minutes) {
	}

	/** Pointage non apparié, affiché mais jamais compté. */
	public record OrphelinDto(
			@JsonProperty("type") String type,
			@JsonProperty("horodatage") String horodatage,
			@JsonProperty("manuel") boolean manuel) {
	}

	/** Une ligne par collaborateur × journée de travail × site. */
	public record LigneTempsTravail(
			@JsonProperty("collaborateur_id") String collaborateurId,
			@JsonProperty("collaborateur_nom") String collaborateurNom,
			@JsonProperty("collaborateur_matricule") String collaborateurMatricule,
			@JsonProperty("site_id") UUID siteId,
			@JsonProperty("site_code") String siteCode,
			@JsonProperty("site_libelle") String siteLibelle,
			@JsonProperty("jour") LocalDate jour,
			@JsonProperty("premiere_entree") String premiereEntree,
			@JsonProperty("derniere_sortie") String derniereSortie,
			@JsonProperty("paires") List<PaireDto> paires,
			@JsonProperty("orphelins") List<OrphelinDto> orphelins,
			@JsonProperty("minutes_travaillees") int minutesTravaillees,
			@JsonProperty("etat") EtatJourHeuresSup etat,
			@JsonProperty("direction_libelle") String directionLibelle,
			@JsonProperty("departement_libelle") String departementLibelle,
			@JsonProperty("service_libelle") String serviceLibelle,
			/** D-P18.d : instant (ISO, Africa/Tunis) où une journée « EN_COURS » basculera ; nul sinon. */
			@JsonProperty("en_cours_jusqu_a") String enCoursJusquA) {
	}

	/** Page de lignes, au format Spring {@code Page} attendu par le web. */
	public record PageLignes(
			@JsonProperty("content") List<LigneTempsTravail> content,
			@JsonProperty("totalElements") long totalElements,
			@JsonProperty("totalPages") int totalPages,
			@JsonProperty("number") int number,
			@JsonProperty("size") int size) {
	}

	/** Synthèse d'un collaborateur sur tout le résultat filtré. */
	public record SyntheseCollaborateur(
			@JsonProperty("collaborateur_id") String collaborateurId,
			@JsonProperty("collaborateur_nom") String collaborateurNom,
			@JsonProperty("collaborateur_matricule") String collaborateurMatricule,
			@JsonProperty("nb_jours") int nbJours,
			@JsonProperty("minutes") long minutes,
			@JsonProperty("nb_incompletes") int nbIncompletes) {
	}

	/** Réponse de la vue : page de lignes + totaux et synthèse calculés sur tout le résultat filtré (D-P17.h). */
	public record TempsTravailResponse(
			@JsonProperty("lignes") PageLignes lignes,
			@JsonProperty("total_minutes") long totalMinutes,
			@JsonProperty("nb_lignes") int nbLignes,
			@JsonProperty("nb_incompletes") int nbIncompletes,
			@JsonProperty("nb_en_cours") int nbEnCours,
			@JsonProperty("synthese") List<SyntheseCollaborateur> synthese) {
	}
}
