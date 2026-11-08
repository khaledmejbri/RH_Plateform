package com.hr.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * DTO representing an evaluation item in the list view.
 * Used by mobile app to display evaluations.
 */
public record EvaluationItemResponse(
    @JsonProperty("identifiant")
    String id,
    
    @JsonProperty("campaignNom")
    String campaignNom,
    
    @JsonProperty("statut")
    String statut,

    @JsonProperty("collaborateurIdentifiant")
    String collaborateurIdentifiant,

    @JsonProperty("collaborateurNom")
    String collaborateurNom,

    @JsonProperty("collaborateurPoste")
    String collaborateurPoste,

    @JsonProperty("superieurIdentifiant")
    String superieurIdentifiant,

    @JsonProperty("superieurNom")
    String superieurNom,
    
    @JsonProperty("etapeActuelle")
    String etapeActuelle,
    
    /** D-E15 : clé absente (pas null) pour le collaborateur avant VALIDEE. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("scoreSur20")
    Integer scoreSur20,

    @JsonProperty("familleMetierCode")
    String familleMetierCode,

    /** Libellé lisible de la famille (null si aucune famille). */
    @JsonProperty("familleMetierLibelle")
    String familleMetierLibelle,

    @JsonProperty("niveauSeniorite")
    String niveauSeniorite,

    /** Libellé lisible du niveau (null si aucun niveau). */
    @JsonProperty("niveauSenioriteLibelle")
    String niveauSenioriteLibelle,

    /** Alias de {@code niveauSenioriteLibelle}. */
    @JsonProperty("niveauLibelle")
    String niveauLibelle,

    @JsonProperty("validationCollaborateurLe")
    Instant validationCollaborateurLe,

    @JsonProperty("validationSuperieurLe")
    Instant validationSuperieurLe,
    
    @JsonProperty("creeLe")
    Instant creeLe
) {}
