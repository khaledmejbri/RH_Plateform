package com.hr.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO representing a general evaluation question.
 */
public record QuestionResponse(
    @JsonProperty("identifiant")
    String id,
    
    @JsonProperty("libelle")
    String libelle,

    @JsonProperty("intitule")
    String intitule,

    @JsonProperty("typeQuestion")
    String typeQuestion,

    @JsonProperty("type")
    String type,
    
    @JsonProperty("obligatoire")
    boolean obligatoire,
    
    @JsonProperty("ordre")
    Integer ordre,
    
    @JsonProperty("options")
    String options,

    @JsonProperty("optionsReponses")
    java.util.List<String> optionsReponses,

    @JsonProperty("valeurMinimale")
    java.math.BigDecimal valeurMinimale,

    @JsonProperty("valeurMaximale")
    java.math.BigDecimal valeurMaximale,

    @JsonProperty("sectionCode")
    String sectionCode,

    @JsonProperty("sectionLibelle")
    String sectionLibelle,

    @JsonProperty("poids")
    java.math.BigDecimal poids,

    @JsonProperty("labelsEchelle")
    java.util.List<String> labelsEchelle,
    
    @JsonProperty("reponseExistante")
    String reponseExistante,
    
    @JsonProperty("noteExistante")
    Integer noteExistante
) {}
