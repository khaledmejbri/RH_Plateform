package com.hr.evaluation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluation_answer", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_answer_question_collaborator",
                columnNames = {"question_identifiant", "evaluation_identifiant"}
        )
})
public class EvaluationAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "identifiant", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_identifiant", nullable = false)
    @JsonIgnore
    private Evaluation evaluation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_identifiant", nullable = false)
    @JsonIgnore
    private EvaluationQuestion question;

    @Column(name = "reponse_collaborateur", length = 4000)
    private String reponseCollaborateur;

    @Column(name = "reponse_manager", length = 4000)
    private String reponseManager;

    @Column(name = "commentaire_manager", length = 2000)
    private String commentaireManager;

    @Column(name = "note_attribuee")
    private Integer noteAttribuee; // For scale-type questions

    @Column(name = "note_collaborateur")
    private Integer noteCollaborateur;

    @Column(name = "note_manager")
    private Integer noteManager;

    @Column(name = "repondu_par_collaborateur_le")
    private Instant reponduParCollaborateurLe;

    @Column(name = "repondu_par_manager_le")
    private Instant reponduParManagerLe;

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
    
    public EvaluationQuestion getQuestion() { return question; }
    public void setQuestion(EvaluationQuestion question) { this.question = question; }
    
    public String getReponseCollaborateur() { return reponseCollaborateur; }
    public void setReponseCollaborateur(String reponseCollaborateur) { 
        this.reponseCollaborateur = reponseCollaborateur; 
    }
    
    public String getReponseManager() { return reponseManager; }
    public void setReponseManager(String reponseManager) { 
        this.reponseManager = reponseManager; 
    }
    
    public String getCommentaireManager() { return commentaireManager; }
    public void setCommentaireManager(String commentaireManager) { 
        this.commentaireManager = commentaireManager; 
    }
    
    public Integer getNoteAttribuee() { return noteAttribuee; }
    public void setNoteAttribuee(Integer noteAttribuee) { 
        this.noteAttribuee = noteAttribuee; 
    }

    public Integer getNoteCollaborateur() { return noteCollaborateur; }
    public void setNoteCollaborateur(Integer noteCollaborateur) { this.noteCollaborateur = noteCollaborateur; }

    public Integer getNoteManager() { return noteManager; }
    public void setNoteManager(Integer noteManager) { this.noteManager = noteManager; }
    
    public Instant getReponduParCollaborateurLe() { return reponduParCollaborateurLe; }
    public void setReponduParCollaborateurLe(Instant reponduParCollaborateurLe) { 
        this.reponduParCollaborateurLe = reponduParCollaborateurLe; 
    }
    
    public Instant getReponduParManagerLe() { return reponduParManagerLe; }
    public void setReponduParManagerLe(Instant reponduParManagerLe) { 
        this.reponduParManagerLe = reponduParManagerLe; 
    }
    
    public Instant getCreeLe() { return creeLe; }
    public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
    
    public Instant getModifieLe() { return modifieLe; }
    public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
}
