package com.hr.referentiel.champsperso;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.ReglesVue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contrat JSON (snake_case) des valeurs de champs personnalisés (annexe A bis, V1 à V4). */
public final class ValeursPersoDtos {

	private ValeursPersoDtos() {
	}

	/** V2 : écriture partielle, {@code null} ou chaîne vide = effacer. Les nombres JSON sont acceptés. */
	public record EcrireValeurs(
			@JsonProperty("version") Long version,
			@JsonProperty("valeurs") Map<String, Object> valeurs) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ChoixListe(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("actif") boolean actif) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ChampValeur(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("type_valeur") String typeValeur,
			@JsonProperty("type_valeur_libelle") String typeValeurLibelle,
			@JsonProperty("regles") ReglesVue regles,
			@JsonProperty("valeurs_liste") List<ChoixListe> valeursListe,
			@JsonProperty("valeur") String valeur,
			@JsonProperty("valeur_affichee") String valeurAffichee,
			@JsonProperty("valeur_inactive") boolean valeurInactive) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ChampDesactive(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("type_valeur") String typeValeur,
			@JsonProperty("valeur") String valeur,
			@JsonProperty("valeur_affichee") String valeurAffichee) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ValeursFiche(
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("version") long version,
			@JsonProperty("modifiable") boolean modifiable,
			@JsonProperty("raison_non_modifiable") String raisonNonModifiable,
			@JsonProperty("champs") List<ChampValeur> champs,
			@JsonProperty("champs_desactives") List<ChampDesactive> champsDesactives) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record LigneJournalValeur(
			@JsonProperty("id") UUID id,
			@JsonProperty("horodatage") Instant horodatage,
			@JsonProperty("auteur_nom") String auteurNom,
			@JsonProperty("champ_code") String champCode,
			@JsonProperty("champ_libelle") String champLibelle,
			@JsonProperty("avant") String avant,
			@JsonProperty("apres") String apres,
			@JsonProperty("origine") String origine,
			@JsonProperty("origine_libelle") String origineLibelle) {
	}

	public record PageJournalValeurs(
			@JsonProperty("contenu") List<LigneJournalValeur> contenu,
			@JsonProperty("page") int page,
			@JsonProperty("taille") int taille,
			@JsonProperty("total") long total) {
	}

	/** V4 et mouvements « moi » : jamais de règle, de finalité ni de champ désactivé. */
	public record ChampMoi(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("type_valeur") String typeValeur,
			@JsonProperty("valeur_affichee") String valeurAffichee) {
	}

	public record MesChamps(@JsonProperty("champs") List<ChampMoi> champs) {
	}

	/** Valeur d'un champ sur un mouvement d'ajustement (annexe A bis, ValeurAffichee). */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ValeurAffichee(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("type_valeur") String typeValeur,
			@JsonProperty("valeur") String valeur,
			@JsonProperty("valeur_affichee") String valeurAffichee) {
	}
}
