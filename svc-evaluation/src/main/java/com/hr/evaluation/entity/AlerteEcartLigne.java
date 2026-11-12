package com.hr.evaluation.entity;

import com.hr.evaluation.domain.SensEcart;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Une compétence technique ayant atteint un écart d'au moins 2 étoiles. Jamais supprimée : les valeurs « détection »
 * restent figées, les valeurs « actuelles » suivent les notes enregistrées après la validation du RO (D-E06.c).
 */
@Entity
@Table(name = "evaluation_alerte_ecart_ligne",
        uniqueConstraints = @UniqueConstraint(name = "uk_alerte_ecart_ligne_question",
                columnNames = {"alerte_identifiant", "question_identifiant"}))
public class AlerteEcartLigne {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "identifiant", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alerte_identifiant", nullable = false, updatable = false)
    private AlerteEcart alerte;

    @Column(name = "question_identifiant", nullable = false, updatable = false)
    private UUID questionIdentifiant;

    @Column(name = "competence_libelle", columnDefinition = "TEXT")
    private String competenceLibelle;

    @Column(name = "ordre")
    private Integer ordre;

    @Column(name = "etoiles_collaborateur_detection", nullable = false, updatable = false)
    private int etoilesCollaborateurDetection;

    @Column(name = "etoiles_ro_detection", nullable = false, updatable = false)
    private int etoilesRoDetection;

    @Column(name = "ecart_detection", nullable = false, updatable = false)
    private int ecartDetection;

    @Column(name = "niveau_detection", nullable = false, updatable = false, length = 40)
    private String niveauDetection;

    @Enumerated(EnumType.STRING)
    @Column(name = "sens_detection", updatable = false, length = 20)
    private SensEcart sensDetection;

    @Column(name = "etoiles_collaborateur_actuelles", nullable = false)
    private int etoilesCollaborateurActuelles;

    @Column(name = "etoiles_ro_actuelles", nullable = false)
    private int etoilesRoActuelles;

    @Column(name = "ecart_actuel", nullable = false)
    private int ecartActuel;

    @Column(name = "niveau_actuel", nullable = false, length = 40)
    private String niveauActuel;

    @Enumerated(EnumType.STRING)
    @Column(name = "sens_actuel", length = 20)
    private SensEcart sensActuel;

    @Column(name = "detectee_le", nullable = false, updatable = false)
    private Instant detecteeLe;

    @Column(name = "modifiee_le")
    private Instant modifieeLe;

    public UUID getId() { return id; }

    public AlerteEcart getAlerte() { return alerte; }
    public void setAlerte(AlerteEcart alerte) { this.alerte = alerte; }

    public UUID getQuestionIdentifiant() { return questionIdentifiant; }
    public void setQuestionIdentifiant(UUID questionIdentifiant) { this.questionIdentifiant = questionIdentifiant; }

    public String getCompetenceLibelle() { return competenceLibelle; }
    public void setCompetenceLibelle(String competenceLibelle) { this.competenceLibelle = competenceLibelle; }

    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }

    public int getEtoilesCollaborateurDetection() { return etoilesCollaborateurDetection; }
    public void setEtoilesCollaborateurDetection(int v) { this.etoilesCollaborateurDetection = v; }

    public int getEtoilesRoDetection() { return etoilesRoDetection; }
    public void setEtoilesRoDetection(int v) { this.etoilesRoDetection = v; }

    public int getEcartDetection() { return ecartDetection; }
    public void setEcartDetection(int v) { this.ecartDetection = v; }

    public String getNiveauDetection() { return niveauDetection; }
    public void setNiveauDetection(String v) { this.niveauDetection = v; }

    public SensEcart getSensDetection() { return sensDetection; }
    public void setSensDetection(SensEcart v) { this.sensDetection = v; }

    public int getEtoilesCollaborateurActuelles() { return etoilesCollaborateurActuelles; }
    public void setEtoilesCollaborateurActuelles(int v) { this.etoilesCollaborateurActuelles = v; }

    public int getEtoilesRoActuelles() { return etoilesRoActuelles; }
    public void setEtoilesRoActuelles(int v) { this.etoilesRoActuelles = v; }

    public int getEcartActuel() { return ecartActuel; }
    public void setEcartActuel(int v) { this.ecartActuel = v; }

    public String getNiveauActuel() { return niveauActuel; }
    public void setNiveauActuel(String v) { this.niveauActuel = v; }

    public SensEcart getSensActuel() { return sensActuel; }
    public void setSensActuel(SensEcart v) { this.sensActuel = v; }

    public Instant getDetecteeLe() { return detecteeLe; }
    public void setDetecteeLe(Instant detecteeLe) { this.detecteeLe = detecteeLe; }

    public Instant getModifieeLe() { return modifieeLe; }
    public void setModifieeLe(Instant modifieeLe) { this.modifieeLe = modifieeLe; }
}
