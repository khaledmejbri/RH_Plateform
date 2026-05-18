package com.hr.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO representing a general evaluation question.
 */
public record QuestionResponse(
    @JsonProperty("identifiant")
    String id,
    
    @JsonProperty("intitule")
    String intitule,
    
    @JsonProperty("type")
    String type,
    
    @JsonProperty("obligatoire")
    boolean obligatoire,
    
    @JsonProperty("ordre")
    Integer ordre,
    
    @JsonProperty("options")
    String options,
    
    @JsonProperty("reponseExistante")
    String reponseExistante,
    
    @JsonProperty("noteExistante")
    Integer noteExistante
) {}
