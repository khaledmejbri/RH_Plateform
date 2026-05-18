package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DocumentRejetRhRequest {

	@NotBlank
	@Size(max = 2000)
	@JsonProperty("motif")
	private String motif;

	/** Justification obligatoire si la demande n'est pas la prochaine dans l'ordre FIFO. */
	@Size(max = 2000)
	@JsonProperty("justification_derogation_fifo")
	private String justificationDerogationFifo;

	public String getMotif() {
		return motif;
	}

	public void setMotif(String motif) {
		this.motif = motif;
	}

	public String getJustificationDerogationFifo() {
		return justificationDerogationFifo;
	}

	public void setJustificationDerogationFifo(String justificationDerogationFifo) {
		this.justificationDerogationFifo = justificationDerogationFifo;
	}
}
