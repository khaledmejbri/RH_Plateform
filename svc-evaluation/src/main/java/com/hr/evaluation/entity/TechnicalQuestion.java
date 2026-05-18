package com.hr.evaluation.entity;

import com.hr.evaluation.domain.SkillLevel;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "technical_question")
public class TechnicalQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "identifiant", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_identifiant", nullable = false)
    private TechnicalTemplate template;

    @Column(name = "competence", nullable = false, length = 255)
    private String competence; // Java, Spring Boot, Docker, etc.

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "niveaux_permis", nullable = false, length = 100)
    private String niveauxPermis; // Comma-separated: DEBUTANT,INTERMEDIAIRE,AVANCE,EXPERT

    @Column(name = "ordre", nullable = false)
    private Integer ordre;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

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
    
    public TechnicalTemplate getTemplate() { return template; }
    public void setTemplate(TechnicalTemplate template) { this.template = template; }
    
    public String getCompetence() { return competence; }
    public void setCompetence(String competence) { this.competence = competence; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getNiveauxPermis() { return niveauxPermis; }
    public void setNiveauxPermis(String niveauxPermis) { this.niveauxPermis = niveauxPermis; }
    
    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }
    
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    
    public Instant getCreeLe() { return creeLe; }
    public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
    
    public Instant getModifieLe() { return modifieLe; }
    public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
}
