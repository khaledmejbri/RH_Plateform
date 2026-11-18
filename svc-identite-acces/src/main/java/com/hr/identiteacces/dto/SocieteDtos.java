package com.hr.identiteacces.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.identiteacces.domain.StatutSociete;
import com.hr.identiteacces.entity.Societe;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SocieteDtos {

	private SocieteDtos() {
	}

	public record SocieteResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("code") String code,
			@JsonProperty("raison_sociale") String raisonSociale,
			@JsonProperty("statut") StatutSociete statut,
			@JsonProperty("fuseau") String fuseau,
			@JsonProperty("cree_le") Instant creeLe,
			@JsonProperty("modifie_le") Instant modifieLe,
			@JsonProperty("version") Long version
	) {
		public static SocieteResponse from(Societe s) {
			return new SocieteResponse(
					s.getId(),
					s.getCode(),
					s.getRaisonSociale(),
					s.getStatut(),
					s.getFuseau(),
					s.getCreeLe(),
					s.getModifieLe(),
					s.getVersion()
			);
		}
	}

	public record PageResponse<T>(
			@JsonProperty("elements") List<T> elements,
			@JsonProperty("page") int page,
			@JsonProperty("taille") int taille,
			@JsonProperty("total") long total
	) {
	}

	public record CreerSocieteRequest(
			@JsonProperty("code") String code,
			@JsonProperty("raison_sociale") String raisonSociale,
			@JsonProperty("fuseau") String fuseau
	) {
	}

	public record ModifierSocieteRequest(
			@JsonProperty("raison_sociale") String raisonSociale,
			@JsonProperty("fuseau") String fuseau,
			@JsonProperty("version") Long version
	) {
	}

	public record MotifSocieteRequest(
			@JsonProperty("motif") String motif
	) {
	}

	public record AdminInitialRequest(
			@JsonProperty("matricule") String matricule,
			@JsonProperty("prenom") String prenom,
			@JsonProperty("nom") String nom,
			@JsonProperty("courriel") String courriel
	) {
	}
}
