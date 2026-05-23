package com.hr.evaluation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonIgnore
    private EvaluationTemplate template;

    @Column(name = "libelle", nullable = false, columnDefinition = "TEXT")
    private String libelle;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_question", nullable = false, length = 30)
    private QuestionType typeQuestion;

    @Column(name = "ordre", nullable = false)
    private Integer ordre;

    @Column(name = "obligatoire", nullable = false)
    private boolean obligatoire = false;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "options_reponses", columnDefinition = "TEXT")
    private java.util.List<String> optionsReponses; // JSON array for multiple choice options

    @Column(name = "section_code", length = 80)
    private String sectionCode;

    @Column(name = "section_libelle", length = 255)
    private String sectionLibelle;

    @Column(name = "poids")
    private java.math.BigDecimal poids = java.math.BigDecimal.ONE;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "labels_echelle", columnDefinition = "TEXT")
    private java.util.List<String> labelsEchelle;

    @Column(name = "valeur_minimale")
    private java.math.BigDecimal valeurMinimale; // For scale/rating questions

    @Column(name = "valeur_maximale")
    private java.math.BigDecimal valeurMaximale; // For scale/rating questions

    @Column(name = "unite_mesure", length = 50)
    private String uniteMesure;

    @Column(name = "placeholder", length = 500)
    private String placeholder;

    @Column(name = "regex_pattern", length = 500)
    private String regexPattern;

    @Column(name = "min_longueur")
    private Integer minLongueur;

    @Column(name = "max_longueur")
    private Integer maxLongueur;

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
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public QuestionType getTypeQuestion() { return typeQuestion; }
    public void setTypeQuestion(QuestionType typeQuestion) { this.typeQuestion = typeQuestion; }
    
    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }
    
    public boolean isObligatoire() { return obligatoire; }
    public void setObligatoire(boolean obligatoire) { this.obligatoire = obligatoire; }
    
    public java.util.List<String> getOptionsReponses() { return optionsReponses; }
    public void setOptionsReponses(java.util.List<String> optionsReponses) { this.optionsReponses = optionsReponses; }

    public String getSectionCode() { return sectionCode; }
    public void setSectionCode(String sectionCode) { this.sectionCode = sectionCode; }

    public String getSectionLibelle() { return sectionLibelle; }
    public void setSectionLibelle(String sectionLibelle) { this.sectionLibelle = sectionLibelle; }

    public java.math.BigDecimal getPoids() { return poids; }
    public void setPoids(java.math.BigDecimal poids) { this.poids = poids; }

    public java.util.List<String> getLabelsEchelle() { return labelsEchelle; }
    public void setLabelsEchelle(java.util.List<String> labelsEchelle) { this.labelsEchelle = labelsEchelle; }
    
    public java.math.BigDecimal getValeurMinimale() { return valeurMinimale; }
    public void setValeurMinimale(java.math.BigDecimal valeurMinimale) { this.valeurMinimale = valeurMinimale; }
    
    public java.math.BigDecimal getValeurMaximale() { return valeurMaximale; }
    public void setValeurMaximale(java.math.BigDecimal valeurMaximale) { this.valeurMaximale = valeurMaximale; }
    
    public String getUniteMesure() { return uniteMesure; }
    public void setUniteMesure(String uniteMesure) { this.uniteMesure = uniteMesure; }
    
    public String getPlaceholder() { return placeholder; }
    public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }
    
    public String getRegexPattern() { return regexPattern; }
    public void setRegexPattern(String regexPattern) { this.regexPattern = regexPattern; }
    
    public Integer getMinLongueur() { return minLongueur; }
    public void setMinLongueur(Integer minLongueur) { this.minLongueur = minLongueur; }
    
    public Integer getMaxLongueur() { return maxLongueur; }
    public void setMaxLongueur(Integer maxLongueur) { this.maxLongueur = maxLongueur; }
    
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    
    public Instant getCreeLe() { return creeLe; }
    public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
    
    public Instant getModifieLe() { return modifieLe; }
    public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
    
    public UUID getCreePar() { return creePar; }
    public void setCreePar(UUID creePar) { this.creePar = creePar; }
}
