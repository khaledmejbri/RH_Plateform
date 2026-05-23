package com.hr.evaluation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hr.evaluation.domain.SkillLevel;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skill_answer", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_skill_answer_question_evaluation",
                columnNames = {"question_technique_identifiant", "evaluation_identifiant"}
        )
})
public class SkillAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "identifiant", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_identifiant", nullable = false)
    @JsonIgnore
    private Evaluation evaluation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_technique_identifiant", nullable = false)
    @JsonIgnore
    private TechnicalQuestion questionTechnique;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_auto_evaluation", length = 30)
    private SkillLevel niveauAutoEvaluation; // Collaborator's self-assessment

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_manager", length = 30)
    private SkillLevel niveauManager; // Manager's assessment

    @Column(name = "commentaire_collaborateur", length = 2000)
    private String commentaireCollaborateur;

    @Column(name = "commentaire_manager", length = 2000)
    private String commentaireManager;

    @Column(name = "evalue_par_collaborateur_le")
    private Instant evalueParCollaborateurLe;

    @Column(name = "evalue_par_manager_le")
    private Instant evalueParManagerLe;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "modifie_le")
    private Instant modifieLe;

    @PrePersist
    public void prePersist() {
        if (creeLe == null) {
            creeLe = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        modifieLe = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public Evaluation getEvaluation() { return evaluation; }
    public void setEvaluation(Evaluation evaluation) { this.evaluation = evaluation; }
    
    public TechnicalQuestion getQuestionTechnique() { return questionTechnique; }
    public void setQuestionTechnique(TechnicalQuestion questionTechnique) { 
        this.questionTechnique = questionTechnique; 
    }
    
    public SkillLevel getNiveauAutoEvaluation() { return niveauAutoEvaluation; }
    public void setNiveauAutoEvaluation(SkillLevel niveauAutoEvaluation) { 
        this.niveauAutoEvaluation = niveauAutoEvaluation; 
    }
    
    public SkillLevel getNiveauManager() { return niveauManager; }
    public void setNiveauManager(SkillLevel niveauManager) { 
        this.niveauManager = niveauManager; 
    }
    
    public String getCommentaireCollaborateur() { return commentaireCollaborateur; }
    public void setCommentaireCollaborateur(String commentaireCollaborateur) { 
        this.commentaireCollaborateur = commentaireCollaborateur; 
    }
    
    public String getCommentaireManager() { return commentaireManager; }
    public void setCommentaireManager(String commentaireManager) { 
        this.commentaireManager = commentaireManager; 
    }
    
    public Instant getEvalueParCollaborateurLe() { return evalueParCollaborateurLe; }
    public void setEvalueParCollaborateurLe(Instant evalueParCollaborateurLe) { 
        this.evalueParCollaborateurLe = evalueParCollaborateurLe; 
    }
    
    public Instant getEvalueParManagerLe() { return evalueParManagerLe; }
    public void setEvalueParManagerLe(Instant evalueParManagerLe) { 
        this.evalueParManagerLe = evalueParManagerLe; 
    }
    
    public Instant getCreeLe() { return creeLe; }
    public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
    
    public Instant getModifieLe() { return modifieLe; }
    public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
}
