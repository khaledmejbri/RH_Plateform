package com.hr.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public class CreateQuestionRequest {
    
    @NotBlank(message = "Question label is required")
    private String libelle;
    
    private String description;
    
    @NotNull(message = "Question type is required")
    private String typeQuestion;
    
    @NotNull(message = "Order is required")
    private Integer ordre;
    
    private Boolean obligatoire = false;
    
    private List<String> optionsReponses;  // For MULTIPLE_CHOICE, CHECKBOX
    private String sectionCode;
    private String sectionLibelle;
    private BigDecimal poids = BigDecimal.ONE;
    private List<String> labelsEchelle;
    
    private BigDecimal valeurMinimale;     // For RATING, SCALE, NUMBER
    private BigDecimal valeurMaximale;     // For RATING, SCALE, NUMBER
    
    private String uniteMesure;            // For NUMBER
    private String placeholder;
    
    // Validation rules
    private String regexPattern;
    private Integer minLongueur;
    private Integer maxLongueur;

    // Getters and Setters
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getTypeQuestion() { return typeQuestion; }
    public void setTypeQuestion(String typeQuestion) { this.typeQuestion = typeQuestion; }
    
    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }
    
    public Boolean getObligatoire() { return obligatoire; }
    public void setObligatoire(Boolean obligatoire) { this.obligatoire = obligatoire; }
    
    public List<String> getOptionsReponses() { return optionsReponses; }
    public void setOptionsReponses(List<String> optionsReponses) { this.optionsReponses = optionsReponses; }

    public String getSectionCode() { return sectionCode; }
    public void setSectionCode(String sectionCode) { this.sectionCode = sectionCode; }

    public String getSectionLibelle() { return sectionLibelle; }
    public void setSectionLibelle(String sectionLibelle) { this.sectionLibelle = sectionLibelle; }

    public BigDecimal getPoids() { return poids; }
    public void setPoids(BigDecimal poids) { this.poids = poids; }

    public List<String> getLabelsEchelle() { return labelsEchelle; }
    public void setLabelsEchelle(List<String> labelsEchelle) { this.labelsEchelle = labelsEchelle; }
    
    public BigDecimal getValeurMinimale() { return valeurMinimale; }
    public void setValeurMinimale(BigDecimal valeurMinimale) { this.valeurMinimale = valeurMinimale; }
    
    public BigDecimal getValeurMaximale() { return valeurMaximale; }
    public void setValeurMaximale(BigDecimal valeurMaximale) { this.valeurMaximale = valeurMaximale; }
    
    public String getUniteMesure() { return uniteMesure; }
    public void setUniteMesure(String uniteMesure) { this.uniteMesure = uniteMesure; }
    
    public String getPlaceholder() { return placeholder; }
    public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }
    
    public String getRegexPattern() { return regexPattern; }
    public void setRegexPattern(String regexPattern) { this.regexPattern = regexPattern; }
    
    public Integer getMinLongueur() { return minLongueur; }
    public void setMinLongueur(Integer minLongueur) { this.minLongueur = minLongueur; }
    
    public Integer getMaxLongueur() { return maxLongueur; }
    public void setMaxLongueur(Integer maxLongueur) { this.maxLongueur = maxLongueur; }
}
