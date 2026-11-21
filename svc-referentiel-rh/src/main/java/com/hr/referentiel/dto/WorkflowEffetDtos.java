package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Contrat de l'endpoint interne d'effet (§ 9.6.5, D-W5-07), aligné champ pour champ sur {@code EffetClient} de
 * {@code svc-workflow}.
 */
public final class WorkflowEffetDtos {

	private WorkflowEffetDtos() {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RequeteEffet(
			@JsonProperty("cle_idempotence") String cleIdempotence,
			@NotBlank @JsonProperty("mode") String mode,
			@NotBlank @JsonProperty("objet_type") String objetType,
			@NotNull @JsonProperty("objet_id") UUID objetId,
			@NotBlank @JsonProperty("effet") String effet,
			@JsonProperty("acteur") ActeurEffet acteur,
			@JsonProperty("commentaire") String commentaire,
			@JsonProperty("instance_id") UUID instanceId,
			@JsonProperty("etape_libelle") String etapeLibelle
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ActeurEffet(
			@JsonProperty("compte_id") UUID compteId,
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("nom") String nom,
			@JsonProperty("pour_nom") String pourNom
	) {
	}
}
