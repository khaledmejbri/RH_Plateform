package com.hr.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Contrat de l'écran Paramètres (ADR lot 3 § 7) : JSON {@code snake_case}, identique dans les trois services.
 * Chaque champ porte son nom JSON explicitement.
 */
public final class ParametreDto {

    private ParametreDto() {
    }

    // ------------------------------------------------------------------------------------------- réponses

    /** Réponse de {@code GET L} : un seul appel par service, fusionné par le front. */
    public record Libelles(
            @JsonProperty("version") String version,
            @JsonProperty("referentiels") Map<String, List<LibelleValeur>> referentiels) {
    }

    public record LibelleValeur(
            @JsonProperty("code") String code,
            @JsonProperty("libelle") String libelle,
            @JsonProperty("description") String description,
            @JsonProperty("couleur") String couleur,
            @JsonProperty("ordre") int ordre,
            @JsonProperty("actif") boolean actif,
            @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("seuil_ecart") Integer seuilEcart) {
    }

    /** Valeur côté administration : avec version, nombre d'utilisations et marque « déjà utilisée ». */
    public record Valeur(
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
            @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("seuil_ecart") Integer seuilEcart) {
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

    /** L'écran Paramètres se construit depuis ces descripteurs, sans liste figée. */
    public record Descripteur(
            @JsonProperty("referentiel") String referentiel,
            @JsonProperty("libelle") String libelle,
            @JsonProperty("domaine") String domaine,
            @JsonProperty("nature") String nature,
            @JsonProperty("operations") Operations operations,
            @JsonProperty("attributs") List<Attribut> attributs) {
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
            @JsonProperty("avant") Object avant,
            @JsonProperty("apres") Object apres) {
    }

    public record PageJournal(
            @JsonProperty("contenu") List<Journal> contenu,
            @JsonProperty("page") int page,
            @JsonProperty("taille") int taille,
            @JsonProperty("total") long total) {
    }

    // -------------------------------------------------------------------------------------------- requêtes

    /** Corps de {@code POST} (création) et de {@code PUT} (modification, {@code version} obligatoire). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CorpsValeur(
            @JsonProperty("libelle") String libelle,
            @JsonProperty("description") String description,
            @JsonProperty("couleur") String couleur,
            @JsonProperty("ordre") Integer ordre,
            @JsonProperty("seuil_ecart") Integer seuilEcart,
            @JsonProperty("version") Long version) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CorpsVersion(@JsonProperty("version") Long version) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CorpsOrdre(@JsonProperty("codes") List<String> codes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CorpsReglage(
            @JsonProperty("valeur") Integer valeur,
            @JsonProperty("version") Long version) {
    }
}
