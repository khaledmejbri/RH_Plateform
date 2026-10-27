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

    /** Grade / niveau du collaborateur au moment de l'affectation (JUNIOR, SENIOR…). */
    @Column(name = "niveau_seniorite", length = 32)
    private String niveauSeniorite;

    /** Snapshot famille métier (clé matching E4) — remplace l'usage produit de role_metier. */
    @Column(name = "famille_metier_code", length = 64)
    private String familleMetierCode;

    /** Legacy — ne plus écrire ; lecture migration. */
    @Column(name = "role_metier", length = 120)
    private String roleMetier;

    /**
     * true si fiche sans famille_metier à la création (GENERIC seul).
     * Wrapper : les évaluations antérieures à la colonne ont NULL en base (SCRUM-10) — lu comme false.
     */
    @Column(name = "profil_metier_incomplet", nullable = true)
    private Boolean profilMetierIncomplet = false;

    /** Template technique résolu selon le grade (prioritaire sur celui de la campagne). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_competence_identifiant")
    @JsonIgnore
    private EvaluationTemplate templateCompetenceAssigne;

    /** Snapshot référentiel à l'activation : compte (adresse des notifications) et libellés affichés. */
    @Column(name = "collaborateur_compte_id")
    private UUID collaborateurCompteId;

    @Column(name = "collaborateur_nom", length = 200)
    private String collaborateurNom;

    @Column(name = "collaborateur_poste", length = 200)
    private String collaborateurPoste;

    @Column(name = "superieur_compte_id")
    private UUID superieurCompteId;

    @Column(name = "superieur_nom", length = 200)
    private String superieurNom;

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
    
    public UUID getCollaborateurCompteId() { return collaborateurCompteId; }
    public void setCollaborateurCompteId(UUID collaborateurCompteId) { this.collaborateurCompteId = collaborateurCompteId; }

    public String getCollaborateurNom() { return collaborateurNom; }
    public void setCollaborateurNom(String collaborateurNom) { this.collaborateurNom = collaborateurNom; }

    public String getCollaborateurPoste() { return collaborateurPoste; }
    public void setCollaborateurPoste(String collaborateurPoste) { this.collaborateurPoste = collaborateurPoste; }

    public UUID getSuperieurCompteId() { return superieurCompteId; }
    public void setSuperieurCompteId(UUID superieurCompteId) { this.superieurCompteId = superieurCompteId; }

    public String getSuperieurNom() { return superieurNom; }
    public void setSuperieurNom(String superieurNom) { this.superieurNom = superieurNom; }

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

    public String getNiveauSeniorite() { return niveauSeniorite; }
    public void setNiveauSeniorite(String niveauSeniorite) { this.niveauSeniorite = niveauSeniorite; }

    public String getFamilleMetierCode() { return familleMetierCode; }
    public void setFamilleMetierCode(String familleMetierCode) { this.familleMetierCode = familleMetierCode; }

    public String getRoleMetier() { return roleMetier; }
    public void setRoleMetier(String roleMetier) { this.roleMetier = roleMetier; }

    public boolean isProfilMetierIncomplet() { return Boolean.TRUE.equals(profilMetierIncomplet); }
    public void setProfilMetierIncomplet(boolean profilMetierIncomplet) {
        this.profilMetierIncomplet = profilMetierIncomplet;
    }

    public EvaluationTemplate getTemplateCompetenceAssigne() { return templateCompetenceAssigne; }
    public void setTemplateCompetenceAssigne(EvaluationTemplate templateCompetenceAssigne) {
        this.templateCompetenceAssigne = templateCompetenceAssigne;
    }
    
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
