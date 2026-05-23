package com.hr.evaluation.entity;

import com.hr.evaluation.domain.TemplateType;
import com.hr.evaluation.domain.TemplateStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "evaluation_template")
public class EvaluationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "identifiant", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "nom", nullable = false, length = 255)
    private String nom;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TemplateType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private TemplateStatus statut = TemplateStatus.DRAFT;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    // Technical template specific fields
    @Column(name = "niveau_seniorite", length = 50)
    private String niveauSeniorite;

    @Column(name = "role", length = 100)
    private String role;

    @Column(name = "domaine", length = 100)
    private String domaine;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "modifie_le")
    private Instant modifieLe;

    @Column(name = "cree_par", nullable = false)
    private UUID creePar;

    @Column(name = "publie_le")
    private Instant publieLe;

    @Column(name = "publie_par")
    private UUID publiePar;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<EvaluationQuestion> questions = new ArrayList<>();

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
    
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public TemplateType getType() { return type; }
    public void setType(TemplateType type) { this.type = type; }
    
    public TemplateStatus getStatut() { return statut; }
    public void setStatut(TemplateStatus statut) { this.statut = statut; }
    
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    
    public String getNiveauSeniorite() { return niveauSeniorite; }
    public void setNiveauSeniorite(String niveauSeniorite) { this.niveauSeniorite = niveauSeniorite; }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public String getDomaine() { return domaine; }
    public void setDomaine(String domaine) { this.domaine = domaine; }
    
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    
    public Instant getCreeLe() { return creeLe; }
    public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
    
    public Instant getModifieLe() { return modifieLe; }
    public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
    
    public UUID getCreePar() { return creePar; }
    public void setCreePar(UUID creePar) { this.creePar = creePar; }
    
    public Instant getPublieLe() { return publieLe; }
    public void setPublieLe(Instant publieLe) { this.publieLe = publieLe; }
    
    public UUID getPubliePar() { return publiePar; }
    public void setPubliePar(UUID publiePar) { this.publiePar = publiePar; }
    
    public List<EvaluationQuestion> getQuestions() { return questions; }
    public void setQuestions(List<EvaluationQuestion> questions) { this.questions = questions; }
}
