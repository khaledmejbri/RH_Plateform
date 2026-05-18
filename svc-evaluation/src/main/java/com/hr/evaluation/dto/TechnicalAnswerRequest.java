package com.hr.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for submitting a technical skill self-assessment.
 */
public class TechnicalAnswerRequest {

    @NotBlank(message = "questionId est obligatoire")
    private String questionId;

    @NotBlank(message = "Le niveau d'auto-évaluation est obligatoire")
    private String niveau;

    private String commentaire;

    // Getters and Setters
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
    
    public String getNiveau() { return niveau; }
    public void setNiveau(String niveau) { this.niveau = niveau; }
    
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
}
