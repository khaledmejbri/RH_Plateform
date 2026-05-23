package com.hr.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class CreateTemplateRequest {
    
    @NotBlank(message = "Template name is required")
    private String nom;
    
    private String description;
    
    @NotNull(message = "Template type is required")
    private String type; // GENERIC or TECHNICAL
    
    // Technical template specific
    private String niveauSeniorite;
    private String role;
    private String domaine;
    
    private List<CreateQuestionRequest> questions;

    // Getters and Setters
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getNiveauSeniorite() { return niveauSeniorite; }
    public void setNiveauSeniorite(String niveauSeniorite) { this.niveauSeniorite = niveauSeniorite; }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public String getDomaine() { return domaine; }
    public void setDomaine(String domaine) { this.domaine = domaine; }
    
    public List<CreateQuestionRequest> getQuestions() { return questions; }
    public void setQuestions(List<CreateQuestionRequest> questions) { this.questions = questions; }
}
