package com.hr.evaluation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.domain.EvaluationCampaignType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluation_campaign")
public class EvaluationCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "identifiant", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private EvaluationCampaignType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private EvaluationCampaignStatus statut = EvaluationCampaignStatus.PLANIFIEE;

    @Column(name = "nom", nullable = false, length = 255)
    private String nom;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "date_debut", nullable = false)
    private Instant dateDebut;

    @Column(name = "date_fin", nullable = false)
    private Instant dateFin;

    @Column(name = "annee", nullable = false)
    private Integer annee;

    @Column(name = "mois_debut", nullable = false)
    private Integer moisDebut;

    @Column(name = "mois_fin", nullable = false)
    private Integer moisFin;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "modifie_le")
    private Instant modifieLe;

    @Column(name = "cree_par")
    private UUID creePar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_general_identifiant")
    @JsonIgnore
    private EvaluationTemplate templateGeneral;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_technique_identifiant")
    @JsonIgnore
    private TechnicalTemplate templateTechnique;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_competence_identifiant")
    @JsonIgnore
    private EvaluationTemplate templateCompetence;

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
    
    public EvaluationCampaignType getType() { return type; }
    public void setType(EvaluationCampaignType type) { this.type = type; }
    
    public EvaluationCampaignStatus getStatut() { return statut; }
    public void setStatut(EvaluationCampaignStatus statut) { this.statut = statut; }
    
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Instant getDateDebut() { return dateDebut; }
    public void setDateDebut(Instant dateDebut) { this.dateDebut = dateDebut; }
    
    public Instant getDateFin() { return dateFin; }
    public void setDateFin(Instant dateFin) { this.dateFin = dateFin; }
    
    public Integer getAnnee() { return annee; }
    public void setAnnee(Integer annee) { this.annee = annee; }
    
    public Integer getMoisDebut() { return moisDebut; }
    public void setMoisDebut(Integer moisDebut) { this.moisDebut = moisDebut; }
    
    public Integer getMoisFin() { return moisFin; }
    public void setMoisFin(Integer moisFin) { this.moisFin = moisFin; }
    
    public Instant getCreeLe() { return creeLe; }
    public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
    
    public Instant getModifieLe() { return modifieLe; }
    public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
    
    public UUID getCreePar() { return creePar; }
    public void setCreePar(UUID creePar) { this.creePar = creePar; }
    
    public EvaluationTemplate getTemplateGeneral() { return templateGeneral; }
    public void setTemplateGeneral(EvaluationTemplate templateGeneral) { this.templateGeneral = templateGeneral; }
    
    public TechnicalTemplate getTemplateTechnique() { return templateTechnique; }
    public void setTemplateTechnique(TechnicalTemplate templateTechnique) { this.templateTechnique = templateTechnique; }

    public EvaluationTemplate getTemplateCompetence() { return templateCompetence; }
    public void setTemplateCompetence(EvaluationTemplate templateCompetence) { this.templateCompetence = templateCompetence; }
}
