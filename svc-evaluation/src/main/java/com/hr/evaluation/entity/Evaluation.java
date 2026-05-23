package com.hr.evaluation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hr.evaluation.domain.EvaluationStep;
import com.hr.evaluation.domain.StatutEvaluationRh;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluation", indexes = {
        @Index(name = "idx_evaluation_collaborateur", columnList = "collaborateur_identifiant"),
        @Index(name = "idx_evaluation_campaign", columnList = "campaign_identifiant"),
        @Index(name = "idx_evaluation_statut", columnList = "statut")
})
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "identifiant", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_identifiant", nullable = false)
    @JsonIgnore
    private EvaluationCampaign campaign;

    @Column(name = "collaborateur_identifiant", nullable = false)
    private UUID collaborateurIdentifiant;

    @Column(name = "superieur_identifiant", nullable = false)
    private UUID superieurIdentifiant;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 50)
    private StatutEvaluationRh statut = StatutEvaluationRh.EN_ATTENTE_VALIDATION_CROISEE;

    @Enumerated(EnumType.STRING)
    @Column(name = "etape_actuelle", nullable = false, length = 30)
    private EvaluationStep etapeActuelle = EvaluationStep.EVALUATION_GENERALE;

    @Column(name = "score_sur_20")
    private Integer scoreSur20;

    @Column(name = "validation_collaborateur_le")
    private Instant validationCollaborateurLe;

    @Column(name = "validation_superieur_le")
    private Instant validationSuperieurLe;

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
    
    public EvaluationCampaign getCampaign() { return campaign; }
    public void setCampaign(EvaluationCampaign campaign) { this.campaign = campaign; }
    
    public UUID getCollaborateurIdentifiant() { return collaborateurIdentifiant; }
    public void setCollaborateurIdentifiant(UUID collaborateurIdentifiant) { 
        this.collaborateurIdentifiant = collaborateurIdentifiant; 
    }
    
    public UUID getSuperieurIdentifiant() { return superieurIdentifiant; }
    public void setSuperieurIdentifiant(UUID superieurIdentifiant) { 
        this.superieurIdentifiant = superieurIdentifiant; 
    }
    
    public StatutEvaluationRh getStatut() { return statut; }
    public void setStatut(StatutEvaluationRh statut) { this.statut = statut; }
    
    public EvaluationStep getEtapeActuelle() { return etapeActuelle; }
    public void setEtapeActuelle(EvaluationStep etapeActuelle) { this.etapeActuelle = etapeActuelle; }
    
    public Integer getScoreSur20() { return scoreSur20; }
    public void setScoreSur20(Integer scoreSur20) { this.scoreSur20 = scoreSur20; }
    
    public Instant getValidationCollaborateurLe() { return validationCollaborateurLe; }
    public void setValidationCollaborateurLe(Instant validationCollaborateurLe) { 
        this.validationCollaborateurLe = validationCollaborateurLe; 
    }
    
    public Instant getValidationSuperieurLe() { return validationSuperieurLe; }
    public void setValidationSuperieurLe(Instant validationSuperieurLe) { 
        this.validationSuperieurLe = validationSuperieurLe; 
    }
    
    public Instant getCreeLe() { return creeLe; }
    public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
    
    public Instant getModifieLe() { return modifieLe; }
    public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
}
