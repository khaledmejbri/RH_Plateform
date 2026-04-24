package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.StatutPlainteRh;
import com.hr.referentiel.domain.TypePlainteRh;

import java.time.Instant;
import java.util.UUID;

public class PlainteRhResponse {

	@JsonProperty("identifiant")
	private UUID identifiant;

	@JsonProperty("type_plainte")
	private TypePlainteRh typePlainte;

	@JsonProperty("auteur_collaborateur_identifiant")
	private UUID auteurCollaborateurIdentifiant;

	@JsonProperty("titre")
	private String titre;

	@JsonProperty("description")
	private String description;

	@JsonProperty("statut")
	private StatutPlainteRh statut;

	@JsonProperty("commentaire_rh")
	private String commentaireRh;

	@JsonProperty("cree_le")
	private Instant creeLe;

	@JsonProperty("modifie_le")
	private Instant modifieLe;

	public PlainteRhResponse() {
	}

	public PlainteRhResponse(UUID identifiant, TypePlainteRh typePlainte, UUID auteurCollaborateurIdentifiant,
			String titre, String description, StatutPlainteRh statut, String commentaireRh,
			Instant creeLe, Instant modifieLe) {
		this.identifiant = identifiant;
		this.typePlainte = typePlainte;
		this.auteurCollaborateurIdentifiant = auteurCollaborateurIdentifiant;
		this.titre = titre;
		this.description = description;
		this.statut = statut;
		this.commentaireRh = commentaireRh;
		this.creeLe = creeLe;
		this.modifieLe = modifieLe;
	}

	public UUID getIdentifiant() {
		return identifiant;
	}

	public TypePlainteRh getTypePlainte() {
		return typePlainte;
	}

	public UUID getAuteurCollaborateurIdentifiant() {
		return auteurCollaborateurIdentifiant;
	}

	public String getTitre() {
		return titre;
	}

	public String getDescription() {
		return description;
	}

	public StatutPlainteRh getStatut() {
		return statut;
	}

	public String getCommentaireRh() {
		return commentaireRh;
	}

	public Instant getCreeLe() {
		return creeLe;
	}

	public Instant getModifieLe() {
		return modifieLe;
	}
}
