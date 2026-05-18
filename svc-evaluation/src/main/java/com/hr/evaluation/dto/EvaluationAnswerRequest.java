package com.hr.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for submitting a general evaluation answer.
 */
public class EvaluationAnswerRequest {

    @NotBlank(message = "questionId est obligatoire")
    private String questionId;

    @NotBlank(message = "La réponse est obligatoire")
    private String reponse;

    private Integer note;

    // Getters and Setters
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
    
    public String getReponse() { return reponse; }
    public void setReponse(String reponse) { this.reponse = reponse; }
    
    public Integer getNote() { return note; }
    public void setNote(Integer note) { this.note = note; }
}
