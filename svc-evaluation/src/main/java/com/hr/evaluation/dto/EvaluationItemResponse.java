package com.hr.evaluation.dto;

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
    
    @JsonProperty("scoreSur20")
    Integer scoreSur20,

    @JsonProperty("familleMetierCode")
    String familleMetierCode,

    @JsonProperty("niveauSeniorite")
    String niveauSeniorite,

    @JsonProperty("validationCollaborateurLe")
    Instant validationCollaborateurLe,

    @JsonProperty("validationSuperieurLe")
    Instant validationSuperieurLe,
    
    @JsonProperty("creeLe")
    Instant creeLe
) {}
