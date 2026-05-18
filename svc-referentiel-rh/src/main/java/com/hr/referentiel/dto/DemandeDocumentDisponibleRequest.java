package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DemandeDocumentDisponibleRequest {

	@NotBlank
	@Size(max = 1024)
	@JsonProperty("reference_livrable")
	private String referenceLivrable;

	@Size(max = 2000)
	@JsonProperty("commentaire_rh")
	private String commentaireRh;

	/** Justification obligatoire si la demande n'est pas la prochaine dans l'ordre FIFO. */
	@Size(max = 2000)
	@JsonProperty("justification_derogation_fifo")
	private String justificationDerogationFifo;

	public String getReferenceLivrable() {
		return referenceLivrable;
	}

	public void setReferenceLivrable(String referenceLivrable) {
		this.referenceLivrable = referenceLivrable;
	}

	public String getCommentaireRh() {
		return commentaireRh;
	}

	public void setCommentaireRh(String commentaireRh) {
		this.commentaireRh = commentaireRh;
	}

	public String getJustificationDerogationFifo() {
		return justificationDerogationFifo;
	}

	public void setJustificationDerogationFifo(String justificationDerogationFifo) {
		this.justificationDerogationFifo = justificationDerogationFifo;
	}
}
