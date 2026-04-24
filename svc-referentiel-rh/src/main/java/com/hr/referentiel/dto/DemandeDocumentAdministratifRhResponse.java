package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.StatutDocumentAdministratifDemandeRh;
import com.hr.referentiel.domain.TypeDocumentAdministratifDemandeRh;

import java.time.Instant;
import java.util.UUID;

public class DemandeDocumentAdministratifRhResponse {

	@JsonProperty("identifiant")
	private UUID identifiant;

	@JsonProperty("demandeur_identifiant")
	private UUID demandeurIdentifiant;

	@JsonProperty("type_document")
	private TypeDocumentAdministratifDemandeRh typeDocument;

	@JsonProperty("statut")
	private StatutDocumentAdministratifDemandeRh statut;

	/** Toujours FIFO : premier enregistré, premier traité par le RRH (file unique). */
	@JsonProperty("regle_priorite")
	private String reglePriorite;

	@JsonProperty("delai_sla_heures")
	private int delaiSlaHeures;

	@JsonProperty("date_echeance_traitement")
	private Instant dateEcheanceTraitement;

	@JsonProperty("commentaire_demandeur")
	private String commentaireDemandeur;

	@JsonProperty("commentaire_rh")
	private String commentaireRh;

	@JsonProperty("reference_livrable")
	private String referenceLivrable;

	@JsonProperty("cree_le")
	private Instant creeLe;

	@JsonProperty("modifie_le")
	private Instant modifieLe;

	@JsonProperty("rang_dans_file")
	private Integer rangDansFile;

	@JsonProperty("en_retard")
	private boolean enRetard;

	public DemandeDocumentAdministratifRhResponse() {
	}

	public DemandeDocumentAdministratifRhResponse(UUID identifiant, UUID demandeurIdentifiant,
			TypeDocumentAdministratifDemandeRh typeDocument, StatutDocumentAdministratifDemandeRh statut,
			String reglePriorite, int delaiSlaHeures, Instant dateEcheanceTraitement, String commentaireDemandeur,
			String commentaireRh, String referenceLivrable, Instant creeLe, Instant modifieLe,
			Integer rangDansFile, boolean enRetard) {
		this.identifiant = identifiant;
		this.demandeurIdentifiant = demandeurIdentifiant;
		this.typeDocument = typeDocument;
		this.statut = statut;
		this.reglePriorite = reglePriorite;
		this.delaiSlaHeures = delaiSlaHeures;
		this.dateEcheanceTraitement = dateEcheanceTraitement;
		this.commentaireDemandeur = commentaireDemandeur;
		this.commentaireRh = commentaireRh;
		this.referenceLivrable = referenceLivrable;
		this.creeLe = creeLe;
		this.modifieLe = modifieLe;
		this.rangDansFile = rangDansFile;
		this.enRetard = enRetard;
	}

	public UUID getIdentifiant() {
		return identifiant;
	}

	public UUID getDemandeurIdentifiant() {
		return demandeurIdentifiant;
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

	public int getDelaiSlaHeures() {
		return delaiSlaHeures;
	}

	public Instant getDateEcheanceTraitement() {
		return dateEcheanceTraitement;
	}

	public String getCommentaireDemandeur() {
		return commentaireDemandeur;
	}

	public String getCommentaireRh() {
		return commentaireRh;
	}

	public String getReferenceLivrable() {
		return referenceLivrable;
	}

	public Instant getCreeLe() {
		return creeLe;
	}

	public Instant getModifieLe() {
		return modifieLe;
	}

	public Integer getRangDansFile() {
		return rangDansFile;
	}

	public boolean isEnRetard() {
		return enRetard;
	}
}
