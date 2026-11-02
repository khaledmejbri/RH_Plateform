package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTO de l'horaire hebdomadaire d'un site (D-P11) ; heures « HH:mm », jours LUNDI à DIMANCHE. */
public final class HoraireDtos {

	private HoraireDtos() {
	}

	/** Un jour de la saisie ; validé par le service (codes 400 dédiés). */
	public record JourHoraireRequest(
			@JsonProperty("jour") String jour,
			@JsonProperty("debut") String debut,
			@JsonProperty("fin") String fin
	) {
	}

	public record HoraireSiteRequest(
			@JsonProperty("tolerance_avant_minutes") Integer toleranceAvantMinutes,
			@JsonProperty("tolerance_apres_minutes") Integer toleranceApresMinutes,
			@JsonProperty("jours") List<JourHoraireRequest> jours
	) {
	}

	public record JourHoraireResponse(
			@JsonProperty("jour") String jour,
			@JsonProperty("debut") String debut,
			@JsonProperty("fin") String fin,
			@JsonProperty("nuit") boolean nuit
	) {
	}

	public record HoraireSiteResponse(
			@JsonProperty("site_id") UUID siteId,
			@JsonProperty("tolerance_avant_minutes") int toleranceAvantMinutes,
			@JsonProperty("tolerance_apres_minutes") int toleranceApresMinutes,
			@JsonProperty("jours") List<JourHoraireResponse> jours,
			@JsonProperty("avec_horaire") boolean avecHoraire,
			@JsonProperty("modifie_par") String modifiePar,
			@JsonProperty("modifie_le") Instant modifieLe
	) {
	}

	/** Semaine et tolérances sérialisées dans l'historique (avant / après). */
	public record HoraireSnapshot(
			@JsonProperty("tolerance_avant_minutes") int toleranceAvantMinutes,
			@JsonProperty("tolerance_apres_minutes") int toleranceApresMinutes,
			@JsonProperty("jours") List<JourHoraireResponse> jours
	) {
	}

	public record HoraireHistoriqueResponse(
			@JsonProperty("modifie_par") String modifiePar,
			@JsonProperty("modifie_le") Instant modifieLe,
			@JsonProperty("avant") HoraireSnapshot avant,
			@JsonProperty("apres") HoraireSnapshot apres
	) {
	}
}
