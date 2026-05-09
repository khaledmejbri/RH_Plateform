package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.TypePlainteRh;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * CDC v2 §M04 : ajout pieces_jointes (URLs S3 pré-signées) et transcription_audio.
 * Le type_plainte détermine le circuit de notification :
 *   INTERNE → RH seul.
 *   EXTERNE → RH + Services Techniques + Direction E&S simultanément.
 */
public class PlainteRhCreationRequest {

	@NotNull
	@JsonProperty("type_plainte")
	private TypePlainteRh typePlainte;

	@NotBlank
	@Size(max = 255)
	@JsonProperty("titre")
	private String titre;

	@NotBlank
	@Size(max = 4000)
	@JsonProperty("description")
	private String description;

	/** URLs S3 des pièces jointes (photos, vidéos). Max 10 fichiers. */
	@Size(max = 10)
	@JsonProperty("pieces_jointes")
	private List<String> piecesJointes = new ArrayList<>();

	/** Texte transcrit d'un message vocal (optionnel — CDC §M04 : vocal Whisper). */
	@Size(max = 8000)
	@JsonProperty("transcription_audio")
	private String transcriptionAudio;

	public TypePlainteRh getTypePlainte() { return typePlainte; }
	public void setTypePlainte(TypePlainteRh typePlainte) { this.typePlainte = typePlainte; }

	public String getTitre() { return titre; }
	public void setTitre(String titre) { this.titre = titre; }

	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }

	public List<String> getPiecesJointes() { return piecesJointes; }
	public void setPiecesJointes(List<String> piecesJointes) {
		this.piecesJointes = piecesJointes != null ? piecesJointes : new ArrayList<>();
	}

	public String getTranscriptionAudio() { return transcriptionAudio; }
	public void setTranscriptionAudio(String transcriptionAudio) { this.transcriptionAudio = transcriptionAudio; }
}
