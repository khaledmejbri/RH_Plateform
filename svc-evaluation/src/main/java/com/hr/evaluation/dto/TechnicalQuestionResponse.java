package com.hr.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO representing a technical skill evaluation question.
 */
public record TechnicalQuestionResponse(
    @JsonProperty("identifiant")
    String id,
    
    @JsonProperty("competence")
    String competence,
    
    @JsonProperty("description")
    String description,
    
    @JsonProperty("niveauxAttendus")
    String niveauxAttendus,
    
    @JsonProperty("ordre")
    Integer ordre,
    
    @JsonProperty("niveauAutoEvaluation")
    String niveauAutoEvaluation,
    
    @JsonProperty("commentaire")
    String commentaire
) {}
