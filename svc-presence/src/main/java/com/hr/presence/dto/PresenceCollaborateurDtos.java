package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.domain.EtatJourHeuresSup;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Présence d'un collaborateur sur une période de 31 jours au plus (D-N01.b, D-N02, D-P19.g), JSON en snake_case.
 * Tout est calculé à la volée ; les noms et libellés sont renvoyés par le serveur, jamais un identifiant seul.
 */
public final class PresenceCollaborateurDtos {

	private PresenceCollaborateurDtos() {
	}

	/** Totaux de la période. Un collaborateur sans pointage : tout à 0 (retards compris), jamais « — ». */
	public record SynthesePresence(
			@JsonProperty("collaborateur_id") String collaborateurId,
			@JsonProperty("collaborateur_nom") String collaborateurNom,
			@JsonProperty("collaborateur_matricule") String collaborateurMatricule,
			@JsonProperty("du") LocalDate du,
			@JsonProperty("au") LocalDate au,
			@JsonProperty("journees_pointees") int journeesPointees,
			@JsonProperty("temps_total_minutes") long tempsTotalMinutes,
			@JsonProperty("retards_nombre") int retardsNombre,
			@JsonProperty("retards_cumul_minutes") long retardsCumulMinutes,
			@JsonProperty("journees_incompletes") int journeesIncompletes,
			@JsonProperty("journees_en_cours") int journeesEnCours,
			@JsonProperty("pointages_manuels_valides") int pointagesManuelsValides,
			@JsonProperty("pointages_manuels_en_attente") int pointagesManuelsEnAttente,
			@JsonProperty("heures_sup_minutes") long heuresSupMinutes) {
	}

	/** Un pointage d'une journée, refusés compris (le mobile affiche le motif en clair). */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record PointageJournee(
			@JsonProperty("id") UUID id,
			@JsonProperty("type") PointageType type,
			@JsonProperty("type_libelle") String typeLibelle,
			@JsonProperty("statut") PointageStatut statut,
			@JsonProperty("statut_libelle") String statutLibelle,
			@JsonProperty("server_ts") Instant serverTs,
			@JsonProperty("manuel") boolean manuel,
			/** Ex. « Hors zone de pointage (179,8 m pour une zone de 50 m) » ; absent pour un pointage accepté. */
			@JsonProperty("motif_rejet") String motifRejet,
			/** Refus pour lequel une demande de pointage manuel peut être déposée (D-P04, D-P12.a), calculé ici. */
			@JsonProperty("eligible_pointage_manuel") boolean eligiblePointageManuel,
			/** D-P22.a : identique à {@code eligible_pointage_manuel} pour un refus, nul pour un pointage accepté. */
			@JsonProperty("manuel_possible") Boolean manuelPossible,
			/** Raison en clair quand le pointage manuel n'est pas possible (D-P21.d). */
			@JsonProperty("manuel_impossible_motif") String manuelImpossibleMotif) {
	}

	/**
	 * Une journée de travail sur un site. {@code etat} et {@code retard_minutes} sont nuls pour une journée qui n'a que
	 * des pointages refusés ; {@code retard_minutes} nul = « — » (non calculable), 0 = à l'heure (D-P19).
	 */
	public record JourneePresence(
			@JsonProperty("journee") LocalDate journee,
			@JsonProperty("site_id") UUID siteId,
			@JsonProperty("site_libelle") String siteLibelle,
			@JsonProperty("temps_minutes") int tempsMinutes,
			@JsonProperty("retard_minutes") Integer retardMinutes,
			@JsonProperty("etat") EtatJourHeuresSup etat,
			@JsonProperty("etat_libelle") String etatLibelle,
			@JsonProperty("pointages") List<PointageJournee> pointages) {
	}

	/** Identité lisible d'un collaborateur (nom et matricule, jamais seulement l'identifiant). */
	public record PersonneResume(
			@JsonProperty("collaborateur_id") String collaborateurId,
			@JsonProperty("nom_complet") String nomComplet,
			@JsonProperty("matricule") String matricule) {
	}

	/** Fiche d'équipe mobile (D-N02) : ni évaluation, ni document, ni plainte, ni formation. */
	public record PresenceEquipe(
			@JsonProperty("collaborateur") PersonneResume collaborateur,
			@JsonProperty("synthese") SynthesePresence synthese,
			@JsonProperty("journees") List<JourneePresence> journees,
			@JsonProperty("pointages_manuels") List<DemandeManuelleResponse> pointagesManuels) {
	}

	/** Présence d'un collaborateur pour la Plateforme RH (onglets Pointages et Temps de travail de la modale). */
	public record PresenceCollaborateur(
			@JsonProperty("collaborateur") PersonneResume collaborateur,
			@JsonProperty("synthese") SynthesePresence synthese,
			@JsonProperty("journees") List<JourneePresence> journees,
			@JsonProperty("pointages_manuels") List<DemandeManuelleResponse> pointagesManuels) {
	}

	/** Synthèse d'un site sur une période (fiche site, D-P19.g). */
	public record SynthesePresenceSite(
			@JsonProperty("site_id") UUID siteId,
			@JsonProperty("site_code") String siteCode,
			@JsonProperty("site_libelle") String siteLibelle,
			@JsonProperty("du") LocalDate du,
			@JsonProperty("au") LocalDate au,
			@JsonProperty("collaborateurs") int collaborateurs,
			@JsonProperty("journees_pointees") int journeesPointees,
			@JsonProperty("temps_total_minutes") long tempsTotalMinutes,
			@JsonProperty("retards_nombre") int retardsNombre,
			@JsonProperty("retards_cumul_minutes") long retardsCumulMinutes,
			@JsonProperty("journees_incompletes") int journeesIncompletes,
			@JsonProperty("journees_en_cours") int journeesEnCours) {
	}
}
