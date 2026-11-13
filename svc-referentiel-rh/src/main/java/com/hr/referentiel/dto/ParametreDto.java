package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Contrat JSON (snake_case) du paramétrage, identique dans les trois services (ADR-UAT-lot3 § 7).
 */
public final class ParametreDto {

	private ParametreDto() {
	}

	/** Corps de création et de modification d'une valeur (la création ignore {@code version}). */
	public record ValeurRequest(
			@JsonProperty("libelle") String libelle,
			@JsonProperty("description") String description,
			@JsonProperty("couleur") String couleur,
			@JsonProperty("ordre") Integer ordre,
			@JsonProperty("sla_heures") Integer slaHeures,
			@JsonProperty("piece_jointe_obligatoire") Boolean pieceJointeObligatoire,
			@JsonProperty("version") Long version) {
	}

	/** Corps de désactivation et de réactivation. */
	public record VersionRequest(@NotNull @JsonProperty("version") Long version) {
	}

	public record OrdreRequest(@NotNull @JsonProperty("codes") List<String> codes) {
	}

	public record ReglageRequest(
			@NotNull @JsonProperty("valeur") Object valeur,
			@NotNull @JsonProperty("version") Long version) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ValeurResponse(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("description") String description,
			@JsonProperty("couleur") String couleur,
			@JsonProperty("ordre") int ordre,
			@JsonProperty("actif") boolean actif,
			@JsonProperty("systeme") boolean systeme,
			@JsonProperty("nature") String nature,
			@JsonProperty("version") long version,
			@JsonProperty("utilisations") long utilisations,
			@JsonProperty("deja_utilisee") boolean dejaUtilisee,
			@JsonProperty("sla_heures") Integer slaHeures,
			@JsonProperty("piece_jointe_obligatoire") Boolean pieceJointeObligatoire,
			@JsonProperty("piece_jointe_verrouillee") Boolean pieceJointeVerrouillee) {
	}

	public record Operations(
			@JsonProperty("creer") boolean creer,
			@JsonProperty("supprimer") boolean supprimer,
			@JsonProperty("desactiver") boolean desactiver) {
	}

	public record AttributVue(
			@JsonProperty("nom") String nom,
			@JsonProperty("type") String type) {
	}

	public record DescripteurReferentiel(
			@JsonProperty("referentiel") String referentiel,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("domaine") String domaine,
			@JsonProperty("nature") String nature,
			@JsonProperty("operations") Operations operations,
			@JsonProperty("attributs") List<AttributVue> attributs) {
	}

	/** Entrée de la table de libellés (lecture simple : actives pour les non back-office). */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record LibelleVue(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("description") String description,
			@JsonProperty("couleur") String couleur,
			@JsonProperty("ordre") int ordre,
			@JsonProperty("actif") boolean actif,
			@JsonProperty("sla_heures") Integer slaHeures,
			@JsonProperty("piece_jointe_obligatoire") Boolean pieceJointeObligatoire) {
	}

	public record Libelles(
			@JsonProperty("version") String version,
			@JsonProperty("referentiels") Map<String, List<LibelleVue>> referentiels) {
	}

	public record JournalEntree(
			@JsonProperty("action") String action,
			@JsonProperty("code") String code,
			@JsonProperty("auteur_nom") String auteurNom,
			@JsonProperty("horodatage") Instant horodatage,
			@JsonProperty("avant") JsonNode avant,
			@JsonProperty("apres") JsonNode apres) {
	}

	public record PageJournal(
			@JsonProperty("contenu") List<JournalEntree> contenu,
			@JsonProperty("page") int page,
			@JsonProperty("taille") int taille,
			@JsonProperty("total") long total) {
	}

	public record Reglage(
			@JsonProperty("cle") String cle,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("valeur") java.math.BigDecimal valeur,
			@JsonProperty("min") java.math.BigDecimal min,
			@JsonProperty("max") java.math.BigDecimal max,
			@JsonProperty("pas") java.math.BigDecimal pas,
			@JsonProperty("unite") String unite,
			@JsonProperty("defaut") java.math.BigDecimal defaut,
			@JsonProperty("version") long version) {
	}

	// --- Appel de service à service : POST /internal/referentiel/parametres/utiliser ---

	public record Reference(
			@JsonProperty("referentiel") String referentiel,
			@JsonProperty("code") String code) {
	}

	public record UtiliserRequest(@JsonProperty("references") List<Reference> references) {
	}

	public record UtiliserResponse(
			@JsonProperty("valides") List<Reference> valides,
			@JsonProperty("inactifs") List<Reference> inactifs,
			@JsonProperty("inconnus") List<Reference> inconnus) {
	}
}
