package com.hr.evaluation.entity;

import com.hr.evaluation.domain.QuestionType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluation_question")
public class EvaluationQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "identifiant", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_identifiant", nullable = false)
    private EvaluationTemplate template;

    @Column(name = "libelle", nullable = false, length = 1000)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_question", nullable = false, length = 30)
    private QuestionType typeQuestion;

    @Column(name = "ordre", nullable = false)
    private Integer ordre;

    @Column(name = "obligatoire", nullable = false)
    private boolean obligatoire = true;

    @Column(name = "options_reponses", length = 2000)
    private String optionsReponses; // JSON string for multiple choice options

    @Column(name = "valeur_minimale")
    private Integer valeurMinimale; // For scale questions

    @Column(name = "valeur_maximale")
    private Integer valeurMaximale; // For scale questions

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    @Column(name = "cree_le", nullable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "modifie_le")
    private Instant modifieLe;

    @Column(name = "cree_par")
    private UUID creePar;

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
    
    public EvaluationTemplate getTemplate() { return template; }
    public void setTemplate(EvaluationTemplate template) { this.template = template; }
    
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    
    public QuestionType getTypeQuestion() { return typeQuestion; }
    public void setTypeQuestion(QuestionType typeQuestion) { this.typeQuestion = typeQuestion; }
    
    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }
    
    public boolean isObligatoire() { return obligatoire; }
    public void setObligatoire(boolean obligatoire) { this.obligatoire = obligatoire; }
    
    public String getOptionsReponses() { return optionsReponses; }
    public void setOptionsReponses(String optionsReponses) { this.optionsReponses = optionsReponses; }
    
    public Integer getValeurMinimale() { return valeurMinimale; }
    public void setValeurMinimale(Integer valeurMinimale) { this.valeurMinimale = valeurMinimale; }
    
    public Integer getValeurMaximale() { return valeurMaximale; }
    public void setValeurMaximale(Integer valeurMaximale) { this.valeurMaximale = valeurMaximale; }
    
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    
    public Instant getCreeLe() { return creeLe; }
    public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
    
    public Instant getModifieLe() { return modifieLe; }
    public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
    
    public UUID getCreePar() { return creePar; }
    public void setCreePar(UUID creePar) { this.creePar = creePar; }
}
