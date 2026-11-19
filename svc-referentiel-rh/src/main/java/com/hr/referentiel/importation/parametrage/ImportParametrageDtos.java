package com.hr.referentiel.importation.parametrage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contrat JSON (snake_case) du paramétrage des champs d'import (ADR-IMPORT-CHAMPS, annexe A, P1 à P7). */
public final class ImportParametrageDtos {

	private ImportParametrageDtos() {
	}

	// ─── Entrées ───────────────────────────────────────────────────────────────

	/** P2 : tous les champs, dans le nouvel ordre. */
	public record EnregistrerRequest(
			@JsonProperty("version") Long version,
			@JsonProperty("champs") List<ChampRequest> champs) {
	}

	public record ChampRequest(
			@JsonProperty("code") String code,
			@JsonProperty("en_tete") String enTete,
			@JsonProperty("dans_modele") Boolean dansModele,
			@JsonProperty("exigence") String exigence) {
	}

	/** P3. */
	public record SynonymeRequest(
			@JsonProperty("texte") String texte,
			@JsonProperty("version") Long version) {
	}

	/** P4a, P4b, P6. */
	public record VersionRequest(@JsonProperty("version") Long version) {
	}

	// ─── Sorties ───────────────────────────────────────────────────────────────

	public record ExigenceVue(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle) {
	}

	public record SynonymeVue(
			@JsonProperty("id") UUID id,
			@JsonProperty("texte") String texte,
			@JsonProperty("forme_normalisee") String formeNormalisee,
			@JsonProperty("origine") String origine,
			@JsonProperty("origine_libelle") String origineLibelle,
			@JsonProperty("actif") boolean actif,
			@JsonProperty("supprimable") boolean supprimable) {
	}

	/** Règles d'un champ personnalisé : nombres et dates en chaîne (annexe A bis, aucun arrondi flottant). */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ReglesVue(
			@JsonProperty("longueur_max") Integer longueurMax,
			@JsonProperty("decimales") Integer decimales,
			@JsonProperty("minimum") String minimum,
			@JsonProperty("maximum") String maximum,
			@JsonProperty("date_min") String dateMin,
			@JsonProperty("date_max") String dateMax) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ValeurListeVue(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("ordre") int ordre,
			@JsonProperty("actif") boolean actif,
			@JsonProperty("utilisations") int utilisations,
			@JsonProperty("supprimable") boolean supprimable) {
	}

	/** Champ fixe ({@code personnalise=false}, attributs personnalisés à null) ou personnalisé (annexe A bis.2). */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ChampVue(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("en_tete") String enTete,
			@JsonProperty("en_tete_defaut") String enTeteDefaut,
			@JsonProperty("dans_modele") boolean dansModele,
			@JsonProperty("retirable_du_modele") boolean retirableDuModele,
			@JsonProperty("exigence") String exigence,
			@JsonProperty("exigence_libelle") String exigenceLibelle,
			@JsonProperty("exigence_minimale") String exigenceMinimale,
			@JsonProperty("exigences_possibles") List<ExigenceVue> exigencesPossibles,
			@JsonProperty("verrouille") boolean verrouille,
			@JsonProperty("verrou_libelle") String verrouLibelle,
			@JsonProperty("ordre") Integer ordre,
			@JsonProperty("synonymes") List<SynonymeVue> synonymes,
			@JsonProperty("personnalise") boolean personnalise,
			@JsonProperty("actif") boolean actif,
			@JsonProperty("type_valeur") String typeValeur,
			@JsonProperty("type_valeur_libelle") String typeValeurLibelle,
			@JsonProperty("regles") ReglesVue regles,
			@JsonProperty("regles_libelle") String reglesLibelle,
			@JsonProperty("valeurs_liste") List<ValeurListeVue> valeursListe,
			@JsonProperty("finalite") String finalite,
			@JsonProperty("visible_collaborateur") Boolean visibleCollaborateur,
			@JsonProperty("utilisations") Integer utilisations,
			@JsonProperty("utilisations_libelle") String utilisationsLibelle,
			@JsonProperty("type_modifiable") Boolean typeModifiable,
			@JsonProperty("supprimable") Boolean supprimable,
			@JsonProperty("cree_le") Instant creeLe,
			@JsonProperty("cree_par_nom") String creeParNom) {
	}

	public record Parametrage(
			@JsonProperty("type") String type,
			@JsonProperty("type_libelle") String typeLibelle,
			@JsonProperty("version") long version,
			@JsonProperty("modifie_le") @JsonInclude(JsonInclude.Include.ALWAYS) Instant modifieLe,
			@JsonProperty("modifie_par_nom") @JsonInclude(JsonInclude.Include.ALWAYS) String modifieParNom,
			@JsonProperty("champs") List<ChampVue> champs) {
	}

	public record LigneJournal(
			@JsonProperty("id") UUID id,
			@JsonProperty("horodatage") Instant horodatage,
			@JsonProperty("auteur_nom") String auteurNom,
			@JsonProperty("champ") @JsonInclude(JsonInclude.Include.ALWAYS) String champ,
			@JsonProperty("champ_libelle") String champLibelle,
			@JsonProperty("attribut") String attribut,
			@JsonProperty("attribut_libelle") String attributLibelle,
			@JsonProperty("avant") @JsonInclude(JsonInclude.Include.ALWAYS) String avant,
			@JsonProperty("apres") @JsonInclude(JsonInclude.Include.ALWAYS) String apres) {
	}

	public record PageJournalImport(
			@JsonProperty("contenu") List<LigneJournal> contenu,
			@JsonProperty("page") int page,
			@JsonProperty("taille") int taille,
			@JsonProperty("total") long total) {
	}
}
