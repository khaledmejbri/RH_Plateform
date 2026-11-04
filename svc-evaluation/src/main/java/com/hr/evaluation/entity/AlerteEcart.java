package com.hr.evaluation.entity;

import com.hr.evaluation.domain.ConclusionAlerteEcart;
import com.hr.evaluation.domain.NiveauEcart;
import com.hr.evaluation.domain.StatutAlerteEcart;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Alerte d'écart d'étoiles d'une évaluation (D-E04 à D-E09) : une seule par évaluation (contrainte d'unicité).
 * Les participants (nom, matricule, unité) sont figés à la création ; les lignes ne sont jamais supprimées.
 */
@Entity
@Table(name = "evaluation_alerte_ecart",
        uniqueConstraints = @UniqueConstraint(name = "uk_alerte_ecart_evaluation", columnNames = "evaluation_identifiant"),
        indexes = {
                @Index(name = "idx_alerte_ecart_statut_niveau", columnList = "statut, niveau_courant"),
                @Index(name = "idx_alerte_ecart_campagne", columnList = "campagne_identifiant"),
                @Index(name = "idx_alerte_ecart_unite", columnList = "collaborateur_unite_id")
        })
public class AlerteEcart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "identifiant", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_identifiant", nullable = false, updatable = false)
    private Evaluation evaluation;

    @Column(name = "campagne_identifiant", nullable = false)
    private UUID campagneIdentifiant;

    @Column(name = "campagne_nom", length = 255)
    private String campagneNom;

    @Column(name = "collaborateur_fiche_id", nullable = false)
    private UUID collaborateurFicheId;

    @Column(name = "collaborateur_compte_id")
    private UUID collaborateurCompteId;

    @Column(name = "collaborateur_nom", length = 200)
    private String collaborateurNom;

    @Column(name = "collaborateur_matricule", length = 64)
    private String collaborateurMatricule;

    @Column(name = "collaborateur_unite_id")
    private UUID collaborateurUniteId;

    @Column(name = "collaborateur_unite_libelle", length = 255)
    private String collaborateurUniteLibelle;

    @Column(name = "superieur_fiche_id", nullable = false)
    private UUID superieurFicheId;

    @Column(name = "superieur_compte_id")
    private UUID superieurCompteId;

    @Column(name = "superieur_nom", length = 200)
    private String superieurNom;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_detection", nullable = false, length = 20)
    private NiveauEcart niveauDetection;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_courant", nullable = false, length = 20)
    private NiveauEcart niveauCourant;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_max_notifie", nullable = false, length = 20)
    private NiveauEcart niveauMaxNotifie;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutAlerteEcart statut = StatutAlerteEcart.OUVERTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "conclusion", length = 40)
    private ConclusionAlerteEcart conclusion;

    @Column(name = "notes_modifiees_apres_detection", nullable = false)
    private boolean notesModifieesApresDetection = false;

    @Column(name = "detectee_le", nullable = false, updatable = false)
    private Instant detecteeLe;

    @Column(name = "modifiee_le")
    private Instant modifieeLe;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToMany(mappedBy = "alerte", cascade = CascadeType.ALL)
    @OrderBy("ordre ASC, questionIdentifiant ASC")
    private List<AlerteEcartLigne> lignes = new ArrayList<>();

    @OneToMany(mappedBy = "alerte", cascade = CascadeType.ALL)
    @OrderBy("creeLe ASC")
    private List<AlerteEcartHistorique> historique = new ArrayList<>();

    public void ajouterLigne(AlerteEcartLigne ligne) {
        ligne.setAlerte(this);
        lignes.add(ligne);
    }

    public void ajouterHistorique(AlerteEcartHistorique evenement) {
        evenement.setAlerte(this);
        historique.add(evenement);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Evaluation getEvaluation() { return evaluation; }
    public void setEvaluation(Evaluation evaluation) { this.evaluation = evaluation; }

    /** Identifiant de l'évaluation sans initialiser le proxy LAZY. */
    public UUID getEvaluationId() { return evaluation != null ? evaluation.getId() : null; }

    public UUID getCampagneIdentifiant() { return campagneIdentifiant; }
    public void setCampagneIdentifiant(UUID campagneIdentifiant) { this.campagneIdentifiant = campagneIdentifiant; }

    public String getCampagneNom() { return campagneNom; }
    public void setCampagneNom(String campagneNom) { this.campagneNom = campagneNom; }

    public UUID getCollaborateurFicheId() { return collaborateurFicheId; }
    public void setCollaborateurFicheId(UUID collaborateurFicheId) { this.collaborateurFicheId = collaborateurFicheId; }

    public UUID getCollaborateurCompteId() { return collaborateurCompteId; }
    public void setCollaborateurCompteId(UUID collaborateurCompteId) { this.collaborateurCompteId = collaborateurCompteId; }

    public String getCollaborateurNom() { return collaborateurNom; }
    public void setCollaborateurNom(String collaborateurNom) { this.collaborateurNom = collaborateurNom; }

    public String getCollaborateurMatricule() { return collaborateurMatricule; }
    public void setCollaborateurMatricule(String collaborateurMatricule) { this.collaborateurMatricule = collaborateurMatricule; }

    public UUID getCollaborateurUniteId() { return collaborateurUniteId; }
    public void setCollaborateurUniteId(UUID collaborateurUniteId) { this.collaborateurUniteId = collaborateurUniteId; }

    public String getCollaborateurUniteLibelle() { return collaborateurUniteLibelle; }
    public void setCollaborateurUniteLibelle(String collaborateurUniteLibelle) { this.collaborateurUniteLibelle = collaborateurUniteLibelle; }

    public UUID getSuperieurFicheId() { return superieurFicheId; }
    public void setSuperieurFicheId(UUID superieurFicheId) { this.superieurFicheId = superieurFicheId; }

    public UUID getSuperieurCompteId() { return superieurCompteId; }
    public void setSuperieurCompteId(UUID superieurCompteId) { this.superieurCompteId = superieurCompteId; }

    public String getSuperieurNom() { return superieurNom; }
    public void setSuperieurNom(String superieurNom) { this.superieurNom = superieurNom; }

    public NiveauEcart getNiveauDetection() { return niveauDetection; }
    public void setNiveauDetection(NiveauEcart niveauDetection) { this.niveauDetection = niveauDetection; }

    public NiveauEcart getNiveauCourant() { return niveauCourant; }
    public void setNiveauCourant(NiveauEcart niveauCourant) { this.niveauCourant = niveauCourant; }

    public NiveauEcart getNiveauMaxNotifie() { return niveauMaxNotifie; }
    public void setNiveauMaxNotifie(NiveauEcart niveauMaxNotifie) { this.niveauMaxNotifie = niveauMaxNotifie; }

    public StatutAlerteEcart getStatut() { return statut; }
    public void setStatut(StatutAlerteEcart statut) { this.statut = statut; }

    public ConclusionAlerteEcart getConclusion() { return conclusion; }
    public void setConclusion(ConclusionAlerteEcart conclusion) { this.conclusion = conclusion; }

    public boolean isNotesModifieesApresDetection() { return notesModifieesApresDetection; }
    public void setNotesModifieesApresDetection(boolean notesModifieesApresDetection) {
        this.notesModifieesApresDetection = notesModifieesApresDetection;
    }

    public Instant getDetecteeLe() { return detecteeLe; }
    public void setDetecteeLe(Instant detecteeLe) { this.detecteeLe = detecteeLe; }

    public Instant getModifieeLe() { return modifieeLe; }
    public void setModifieeLe(Instant modifieeLe) { this.modifieeLe = modifieeLe; }

    public Long getVersion() { return version; }

    public List<AlerteEcartLigne> getLignes() { return lignes; }
    public List<AlerteEcartHistorique> getHistorique() { return historique; }
}
