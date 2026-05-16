package com.hr.evaluation.entity;

import com.hr.evaluation.domain.AppreciationEvaluationRh;
import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;
import com.hr.evaluation.domain.SemestreEvaluationRh;
import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.domain.TypeEvaluationRh;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rh_evaluation", indexes = {
		@Index(name = "idx_evaluation_collaborateur", columnList = "collaborateur_identifiant"),
		@Index(name = "idx_evaluation_superieur", columnList = "superieur_identifiant"),
		@Index(name = "idx_evaluation_type_statut", columnList = "type,statut"),
		@Index(name = "idx_evaluation_annee", columnList = "annee")
})
public class EvaluationRh {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "identifiant", nullable = false, updatable = false)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 30)
	private TypeEvaluationRh type;

	@Enumerated(EnumType.STRING)
	@Column(name = "statut", nullable = false, length = 50)
	private StatutEvaluationRh statut = StatutEvaluationRh.EN_ATTENTE_VALIDATION_CROISEE;

	@Column(name = "collaborateur_identifiant", nullable = false)
	private UUID collaborateurIdentifiant;

	@Column(name = "superieur_identifiant", nullable = false)
	private UUID superieurIdentifiant;

	@Column(name = "annee", nullable = false)
	private Integer annee;

	@Enumerated(EnumType.STRING)
	@Column(name = "semestre", length = 10)
	private SemestreEvaluationRh semestre;

	@Column(name = "qualite_travail")
	private Integer qualiteTravail;

	@Column(name = "rendement")
	private Integer rendement;

	@Column(name = "ponctualite")
	private Integer ponctualite;

	@Column(name = "esprit_equipe")
	private Integer espritEquipe;

	@Column(name = "savoir_technique")
	private Integer savoirTechnique;

	@Column(name = "savoir_faire")
	private Integer savoirFaire;

	@Column(name = "savoir_etre")
	private Integer savoirEtre;

	@Column(name = "score_sur_20", nullable = false)
	private Integer scoreSur20;

	@Enumerated(EnumType.STRING)
	@Column(name = "appreciation", nullable = false, length = 40)
	private AppreciationEvaluationRh appreciation;

	@Enumerated(EnumType.STRING)
	@Column(name = "couleur_alerte", nullable = false, length = 20)
	private CouleurAlerteEvaluationRh couleurAlerte;

	@Column(name = "points_forts", length = 4000)
	private String pointsForts;

	@Column(name = "points_a_ameliorer", length = 4000)
	private String pointsAAmeliorer;

	@Column(name = "plan_action_recommande", length = 4000)
	private String planActionRecommande;

	@Column(name = "recommandations_ia", length = 4000)
	private String recommandationsIa;

	@Column(name = "bilan_savoir", length = 4000)
	private String bilanSavoir;

	@Column(name = "bilan_savoir_faire", length = 4000)
	private String bilanSavoirFaire;

	@Column(name = "bilan_savoir_etre", length = 4000)
	private String bilanSavoirEtre;

	@Column(name = "resultats_objectifs_n", length = 4000)
	private String resultatsObjectifsN;

	@Column(name = "objectifs_n_plus_1", length = 4000)
	private String objectifsNPlus1;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(
			name = "rh_evaluation_formation_recommandee",
			joinColumns = @JoinColumn(name = "evaluation_identifiant"))
	@Column(name = "formation", nullable = false, length = 255)
	private List<String> formationsRecommandees = new ArrayList<>();

	@Column(name = "formations_integrees_m05", nullable = false)
	private boolean formationsIntegreesM05;

	@Column(name = "validation_collaborateur_le")
	private Instant validationCollaborateurLe;

	@Column(name = "validation_superieur_le")
	private Instant validationSuperieurLe;

	@Column(name = "pdf_object_key", length = 512)
	private String pdfObjectKey;

	@Column(name = "pdf_archive_url", length = 1024)
	private String pdfArchiveUrl;

	@Column(name = "pdf_archive_le")
	private Instant pdfArchiveLe;

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

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public TypeEvaluationRh getType() { return type; }
	public void setType(TypeEvaluationRh type) { this.type = type; }
	public StatutEvaluationRh getStatut() { return statut; }
	public void setStatut(StatutEvaluationRh statut) { this.statut = statut; }
	public UUID getCollaborateurIdentifiant() { return collaborateurIdentifiant; }
	public void setCollaborateurIdentifiant(UUID collaborateurIdentifiant) { this.collaborateurIdentifiant = collaborateurIdentifiant; }
	public UUID getSuperieurIdentifiant() { return superieurIdentifiant; }
	public void setSuperieurIdentifiant(UUID superieurIdentifiant) { this.superieurIdentifiant = superieurIdentifiant; }
	public Integer getAnnee() { return annee; }
	public void setAnnee(Integer annee) { this.annee = annee; }
	public SemestreEvaluationRh getSemestre() { return semestre; }
	public void setSemestre(SemestreEvaluationRh semestre) { this.semestre = semestre; }
	public Integer getQualiteTravail() { return qualiteTravail; }
	public void setQualiteTravail(Integer qualiteTravail) { this.qualiteTravail = qualiteTravail; }
	public Integer getRendement() { return rendement; }
	public void setRendement(Integer rendement) { this.rendement = rendement; }
	public Integer getPonctualite() { return ponctualite; }
	public void setPonctualite(Integer ponctualite) { this.ponctualite = ponctualite; }
	public Integer getEspritEquipe() { return espritEquipe; }
	public void setEspritEquipe(Integer espritEquipe) { this.espritEquipe = espritEquipe; }
	public Integer getSavoirTechnique() { return savoirTechnique; }
	public void setSavoirTechnique(Integer savoirTechnique) { this.savoirTechnique = savoirTechnique; }
	public Integer getSavoirFaire() { return savoirFaire; }
	public void setSavoirFaire(Integer savoirFaire) { this.savoirFaire = savoirFaire; }
	public Integer getSavoirEtre() { return savoirEtre; }
	public void setSavoirEtre(Integer savoirEtre) { this.savoirEtre = savoirEtre; }
	public Integer getScoreSur20() { return scoreSur20; }
	public void setScoreSur20(Integer scoreSur20) { this.scoreSur20 = scoreSur20; }
	public AppreciationEvaluationRh getAppreciation() { return appreciation; }
	public void setAppreciation(AppreciationEvaluationRh appreciation) { this.appreciation = appreciation; }
	public CouleurAlerteEvaluationRh getCouleurAlerte() { return couleurAlerte; }
	public void setCouleurAlerte(CouleurAlerteEvaluationRh couleurAlerte) { this.couleurAlerte = couleurAlerte; }
	public String getPointsForts() { return pointsForts; }
	public void setPointsForts(String pointsForts) { this.pointsForts = pointsForts; }
	public String getPointsAAmeliorer() { return pointsAAmeliorer; }
	public void setPointsAAmeliorer(String pointsAAmeliorer) { this.pointsAAmeliorer = pointsAAmeliorer; }
	public String getPlanActionRecommande() { return planActionRecommande; }
	public void setPlanActionRecommande(String planActionRecommande) { this.planActionRecommande = planActionRecommande; }
	public String getRecommandationsIa() { return recommandationsIa; }
	public void setRecommandationsIa(String recommandationsIa) { this.recommandationsIa = recommandationsIa; }
	public String getBilanSavoir() { return bilanSavoir; }
	public void setBilanSavoir(String bilanSavoir) { this.bilanSavoir = bilanSavoir; }
	public String getBilanSavoirFaire() { return bilanSavoirFaire; }
	public void setBilanSavoirFaire(String bilanSavoirFaire) { this.bilanSavoirFaire = bilanSavoirFaire; }
	public String getBilanSavoirEtre() { return bilanSavoirEtre; }
	public void setBilanSavoirEtre(String bilanSavoirEtre) { this.bilanSavoirEtre = bilanSavoirEtre; }
	public String getResultatsObjectifsN() { return resultatsObjectifsN; }
	public void setResultatsObjectifsN(String resultatsObjectifsN) { this.resultatsObjectifsN = resultatsObjectifsN; }
	public String getObjectifsNPlus1() { return objectifsNPlus1; }
	public void setObjectifsNPlus1(String objectifsNPlus1) { this.objectifsNPlus1 = objectifsNPlus1; }
	public List<String> getFormationsRecommandees() { return formationsRecommandees; }
	public void setFormationsRecommandees(List<String> formationsRecommandees) {
		this.formationsRecommandees = formationsRecommandees != null ? formationsRecommandees : new ArrayList<>();
	}
	public boolean isFormationsIntegreesM05() { return formationsIntegreesM05; }
	public void setFormationsIntegreesM05(boolean formationsIntegreesM05) { this.formationsIntegreesM05 = formationsIntegreesM05; }
	public Instant getValidationCollaborateurLe() { return validationCollaborateurLe; }
	public void setValidationCollaborateurLe(Instant validationCollaborateurLe) { this.validationCollaborateurLe = validationCollaborateurLe; }
	public Instant getValidationSuperieurLe() { return validationSuperieurLe; }
	public void setValidationSuperieurLe(Instant validationSuperieurLe) { this.validationSuperieurLe = validationSuperieurLe; }
	public String getPdfObjectKey() { return pdfObjectKey; }
	public void setPdfObjectKey(String pdfObjectKey) { this.pdfObjectKey = pdfObjectKey; }
	public String getPdfArchiveUrl() { return pdfArchiveUrl; }
	public void setPdfArchiveUrl(String pdfArchiveUrl) { this.pdfArchiveUrl = pdfArchiveUrl; }
	public Instant getPdfArchiveLe() { return pdfArchiveLe; }
	public void setPdfArchiveLe(Instant pdfArchiveLe) { this.pdfArchiveLe = pdfArchiveLe; }
	public Instant getCreeLe() { return creeLe; }
	public void setCreeLe(Instant creeLe) { this.creeLe = creeLe; }
	public Instant getModifieLe() { return modifieLe; }
	public void setModifieLe(Instant modifieLe) { this.modifieLe = modifieLe; }
}
