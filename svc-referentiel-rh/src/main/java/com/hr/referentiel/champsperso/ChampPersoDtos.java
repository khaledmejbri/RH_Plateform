package com.hr.referentiel.champsperso;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Corps d'entrée des définitions de champs personnalisés (annexe A bis.2, C1 à C6). */
public final class ChampPersoDtos {

	private ChampPersoDtos() {
	}

	public record ReglesRequest(
			@JsonProperty("longueur_max") Integer longueurMax,
			@JsonProperty("decimales") Integer decimales,
			@JsonProperty("minimum") BigDecimal minimum,
			@JsonProperty("maximum") BigDecimal maximum,
			@JsonProperty("date_min") LocalDate dateMin,
			@JsonProperty("date_max") LocalDate dateMax) {
	}

	public record ValeurListeRequest(@JsonProperty("libelle") String libelle) {
	}

	/** C1. */
	public record CreerChampPerso(
			@JsonProperty("version") Long version,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("finalite") String finalite,
			@JsonProperty("type_valeur") String typeValeur,
			@JsonProperty("regles") ReglesRequest regles,
			@JsonProperty("valeurs_liste") List<ValeurListeRequest> valeursListe,
			@JsonProperty("visible_collaborateur") Boolean visibleCollaborateur,
			@JsonProperty("confirmation_donnees_non_sensibles") Boolean confirmation) {
	}

	/** C2 : état voulu de tous les attributs ; {@code valeurs_liste} seulement si le type devient LISTE. */
	public record ModifierChampPerso(
			@JsonProperty("version") Long version,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("finalite") String finalite,
			@JsonProperty("type_valeur") String typeValeur,
			@JsonProperty("regles") ReglesRequest regles,
			@JsonProperty("valeurs_liste") List<ValeurListeRequest> valeursListe,
			@JsonProperty("visible_collaborateur") Boolean visibleCollaborateur) {
	}

	/** C5a (libellé) et C5b (libellé et position). */
	public record ValeurRequest(
			@JsonProperty("libelle") String libelle,
			@JsonProperty("ordre") Integer ordre,
			@JsonProperty("version") Long version) {
	}
}
