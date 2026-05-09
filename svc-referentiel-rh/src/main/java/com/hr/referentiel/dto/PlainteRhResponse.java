package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.StatutPlainteRh;
import com.hr.referentiel.domain.TypePlainteRh;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlainteRhResponse {

	@JsonProperty("identifiant")
	private final UUID identifiant;

	@JsonProperty("numero_ticket")
	private final String numeroTicket;

	@JsonProperty("type_plainte")
	private final TypePlainteRh typePlainte;

	@JsonProperty("auteur_identifiant")
	private final UUID auteurIdentifiant;

	@JsonProperty("titre")
	private final String titre;

	@JsonProperty("description")
	private final String description;

	@JsonProperty("pieces_jointes")
	private final List<String> piecesJointes;

	@JsonProperty("transcription_audio")
	private final String transcriptionAudio;

	@JsonProperty("statut")
	private final StatutPlainteRh statut;

	@JsonProperty("commentaire_rh")
	private final String commentaireRh;

	@JsonProperty("log_actions")
	private final List<Map<String, String>> logActions;

	@JsonProperty("cree_le")
	private final Instant creeLe;

	@JsonProperty("modifie_le")
	private final Instant modifieLe;

	public PlainteRhResponse(UUID identifiant, String numeroTicket, TypePlainteRh typePlainte,
			UUID auteurIdentifiant, String titre, String description,
			List<String> piecesJointes, String transcriptionAudio,
			StatutPlainteRh statut, String commentaireRh,
			List<Map<String, String>> logActions, Instant creeLe, Instant modifieLe) {
		this.identifiant = identifiant;
		this.numeroTicket = numeroTicket;
		this.typePlainte = typePlainte;
		this.auteurIdentifiant = auteurIdentifiant;
		this.titre = titre;
		this.description = description;
		this.piecesJointes = piecesJointes;
		this.transcriptionAudio = transcriptionAudio;
		this.statut = statut;
		this.commentaireRh = commentaireRh;
		this.logActions = logActions;
		this.creeLe = creeLe;
		this.modifieLe = modifieLe;
	}

	public UUID getIdentifiant() { return identifiant; }
	public String getNumeroTicket() { return numeroTicket; }
	public TypePlainteRh getTypePlainte() { return typePlainte; }
	public UUID getAuteurIdentifiant() { return auteurIdentifiant; }
	public String getTitre() { return titre; }
	public String getDescription() { return description; }
	public List<String> getPiecesJointes() { return piecesJointes; }
	public String getTranscriptionAudio() { return transcriptionAudio; }
	public StatutPlainteRh getStatut() { return statut; }
	public String getCommentaireRh() { return commentaireRh; }
	public List<Map<String, String>> getLogActions() { return logActions; }
	public Instant getCreeLe() { return creeLe; }
	public Instant getModifieLe() { return modifieLe; }
}
