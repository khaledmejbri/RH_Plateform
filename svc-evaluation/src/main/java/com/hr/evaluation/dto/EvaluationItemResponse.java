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
    
    @JsonProperty("superieurNom")
    String superieurNom,
    
    @JsonProperty("etapeActuelle")
    String etapeActuelle,
    
    @JsonProperty("scoreSur20")
    Integer scoreSur20,
    
    @JsonProperty("creeLe")
    Instant creeLe
) {}
