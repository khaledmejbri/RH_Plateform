package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.StatutDocumentAdministratifDemandeRh;
import com.hr.referentiel.domain.TypeDocumentAdministratifDemandeRh;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Suivi d'avancement : file FIFO unique côté RH, SLA et étapes métier (CDC M2).
 */
public class DemandeDocumentSuiviResponse {

	@JsonProperty("identifiant")
	private UUID identifiant;

	@JsonProperty("type_document")
	private TypeDocumentAdministratifDemandeRh typeDocument;

	@JsonProperty("statut")
	private StatutDocumentAdministratifDemandeRh statut;

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

	public DemandeDocumentSuiviResponse() {
	}

	public DemandeDocumentSuiviResponse(UUID identifiant, TypeDocumentAdministratifDemandeRh typeDocument,
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

	public UUID getIdentifiant() {
		return identifiant;
	}

	public TypeDocumentAdministratifDemandeRh getTypeDocument() {
		return typeDocument;
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
}
