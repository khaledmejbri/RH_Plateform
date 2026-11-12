package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.domain.NatureParametre;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Contrat JSON (snake_case) des paramètres de présence, identique à l'ADR lot 3 § 7. */
public final class ParametresDtos {

	private ParametresDtos() {
	}

	/** Réponse de {@code GET .../parametres/libelles} : un appel par service, fusionné par le front. */
	public record Libelles(
			@JsonProperty("version") String version,
			@JsonProperty("referentiels") Map<String, List<ValeurLibelle>> referentiels) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ValeurLibelle(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("description") String description,
			@JsonProperty("couleur") String couleur,
			@JsonProperty("ordre") int ordre,
			@JsonProperty("actif") boolean actif) {
	}

	public record Operations(
			@JsonProperty("creer") boolean creer,
			@JsonProperty("supprimer") boolean supprimer,
			@JsonProperty("desactiver") boolean desactiver) {
	}

	public record Attribut(
			@JsonProperty("nom") String nom,
			@JsonProperty("type") String type) {
	}

	public record DescripteurReferentiel(
			@JsonProperty("referentiel") String referentiel,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("domaine") String domaine,
			@JsonProperty("nature") NatureParametre nature,
			@JsonProperty("operations") Operations operations,
			@JsonProperty("attributs") List<Attribut> attributs) {
	}

	/** Valeur vue par le back-office (actives et inactives). */
	public record Valeur(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("description") String description,
			@JsonProperty("couleur") String couleur,
			@JsonProperty("ordre") int ordre,
			@JsonProperty("actif") boolean actif,
			@JsonProperty("systeme") boolean systeme,
			@JsonProperty("nature") NatureParametre nature,
			@JsonProperty("version") long version,
			@JsonProperty("utilisations") int utilisations,
			@JsonProperty("deja_utilisee") boolean dejaUtilisee) {
	}

	/** Corps d'une modification : libellé et aide, couleur, ordre ; {@code version} obligatoire (D-R02.h). */
	public record ValeurRequest(
			@JsonProperty("libelle") String libelle,
			@JsonProperty("description") String description,
			@JsonProperty("couleur") String couleur,
			@JsonProperty("ordre") Integer ordre,
			@JsonProperty("version") Long version) {
	}

	public record VersionRequest(@JsonProperty("version") Long version) {
	}

	public record OrdreRequest(@JsonProperty("codes") List<String> codes) {
	}

	public record ReglageRequest(
			@NotNull @JsonProperty("valeur") Integer valeur,
			@NotNull @JsonProperty("version") Long version) {
	}

	public record Reglage(
			@JsonProperty("cle") String cle,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("valeur") int valeur,
			@JsonProperty("min") int min,
			@JsonProperty("max") int max,
			@JsonProperty("unite") String unite,
			@JsonProperty("defaut") int defaut,
			@JsonProperty("version") long version) {
	}

	public record Journal(
			@JsonProperty("action") String action,
			@JsonProperty("code") String code,
			@JsonProperty("auteur_nom") String auteurNom,
			@JsonProperty("horodatage") Instant horodatage,
			@JsonProperty("avant") JsonNode avant,
			@JsonProperty("apres") JsonNode apres) {
	}

	public record PageJournal(
			@JsonProperty("contenu") List<Journal> contenu,
			@JsonProperty("page") int page,
			@JsonProperty("taille") int taille,
			@JsonProperty("total") long total) {
	}
}
