package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.StatutDocumentAdministratifDemandeRh;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Suivi d'avancement : file FIFO unique côté RH, SLA et étapes métier (CDC M2).
 */
public class DemandeDocumentSuiviResponse {

	@JsonProperty("identifiant")
	private UUID identifiant;

	/** D-L02 : numéro lisible (DEM-/DOC-/FOR-AAAAMM-NNNN). */
	@JsonProperty("numero")
	private String numero;

	@JsonProperty("type_document")
	private String typeDocument;

	@JsonProperty("type_document_libelle")
	private String typeDocumentLibelle;

	@JsonProperty("statut")
	private StatutDocumentAdministratifDemandeRh statut;

	/** Libellé courant du statut (lot 3, paramétrable). */
	@JsonProperty("statut_libelle")
	private String statutLibelle;

	@JsonProperty("regle_priorite")
	private String reglePriorite;

	@JsonProperty("rang_dans_file")
	private Integer rangDansFile;

	@JsonProperty("nombre_en_attente_devant")
	private Integer nombreEnAttenteDevant;

	@JsonProperty("delai_sla_heures")
	private int delaiSlaHeures;

	@JsonProperty("date_echeance_traitement")
	private Instant dateEcheanceTraitement;

	@JsonProperty("en_retard")
	private boolean enRetard;

	@JsonProperty("etapes")
	private List<WorkflowEtapeResponse> etapes;

	@JsonProperty("workflow_mode")
	private String workflowMode;

	@JsonProperty("workflow_instance_id")
	private UUID workflowInstanceId;

	@JsonProperty("etape_courante_libelle")
	private String etapeCouranteLibelle;

	@JsonProperty("echeance_le")
	private Instant echeanceLe;

	@JsonProperty("message_explication")
	private String messageExplication;

	public DemandeDocumentSuiviResponse() {
	}

	public DemandeDocumentSuiviResponse(UUID identifiant, String typeDocument,
			StatutDocumentAdministratifDemandeRh statut, String reglePriorite, Integer rangDansFile,
			Integer nombreEnAttenteDevant, int delaiSlaHeures, Instant dateEcheanceTraitement, boolean enRetard,
			List<WorkflowEtapeResponse> etapes) {
		this.identifiant = identifiant;
		this.typeDocument = typeDocument;
		this.statut = statut;
		this.reglePriorite = reglePriorite;
		this.rangDansFile = rangDansFile;
		this.nombreEnAttenteDevant = nombreEnAttenteDevant;
		this.delaiSlaHeures = delaiSlaHeures;
		this.dateEcheanceTraitement = dateEcheanceTraitement;
		this.enRetard = enRetard;
		this.etapes = etapes;
	}

	public String getNumero() {
		return numero;
	}

	public void setNumero(String numero) {
		this.numero = numero;
	}

	public UUID getIdentifiant() {
		return identifiant;
	}

	public String getTypeDocument() {
		return typeDocument;
	}

	public String getTypeDocumentLibelle() {
		return typeDocumentLibelle;
	}

	public void setTypeDocumentLibelle(String typeDocumentLibelle) {
		this.typeDocumentLibelle = typeDocumentLibelle;
	}

	public String getStatutLibelle() {
		return statutLibelle;
	}

	public void setStatutLibelle(String statutLibelle) {
		this.statutLibelle = statutLibelle;
	}

	public StatutDocumentAdministratifDemandeRh getStatut() {
		return statut;
	}

	public String getReglePriorite() {
		return reglePriorite;
	}

	public Integer getRangDansFile() {
		return rangDansFile;
	}

	public Integer getNombreEnAttenteDevant() {
		return nombreEnAttenteDevant;
	}

	public int getDelaiSlaHeures() {
		return delaiSlaHeures;
	}

	public Instant getDateEcheanceTraitement() {
		return dateEcheanceTraitement;
	}

	public boolean isEnRetard() {
		return enRetard;
	}

	public List<WorkflowEtapeResponse> getEtapes() {
		return etapes;
	}

	public String getWorkflowMode() {
		return workflowMode;
	}

	public void setWorkflowMode(String workflowMode) {
		this.workflowMode = workflowMode;
	}

	public UUID getWorkflowInstanceId() {
		return workflowInstanceId;
	}

	public void setWorkflowInstanceId(UUID workflowInstanceId) {
		this.workflowInstanceId = workflowInstanceId;
	}

	public String getEtapeCouranteLibelle() {
		return etapeCouranteLibelle;
	}

	public void setEtapeCouranteLibelle(String etapeCouranteLibelle) {
		this.etapeCouranteLibelle = etapeCouranteLibelle;
	}

	public Instant getEcheanceLe() {
		return echeanceLe;
	}

	public void setEcheanceLe(Instant echeanceLe) {
		this.echeanceLe = echeanceLe;
	}

	public String getMessageExplication() {
		return messageExplication;
	}

	public void setMessageExplication(String messageExplication) {
		this.messageExplication = messageExplication;
	}
}
