package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.domain.BaseDecompteConge;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SocieteParametresDtos {

	private SocieteParametresDtos() {
	}

	public record SocieteParametresResponse(
			@JsonProperty("id") UUID id,
			@JsonProperty("societe_id") UUID societeId,
			@JsonProperty("jours_travailles") List<String> joursTravailles,
			@JsonProperty("duree_hebdo_minutes") int dureeHebdoMinutes,
			@JsonProperty("duree_jour_minutes") Map<String, Integer> dureeJourMinutes,
			@JsonProperty("heure_debut_journee") String heureDebutJournee,
			@JsonProperty("heure_fin_journee") String heureFinJournee,
			@JsonProperty("base_decompte_conge") BaseDecompteConge baseDecompteConge,
			@JsonProperty("jours_acquis_par_mois") BigDecimal joursAcquisParMois,
			@JsonProperty("duree_max_sortie_minutes") int dureeMaxSortieMinutes,
			@JsonProperty("delegation_auto_conges") boolean delegationAutoConges,
			@JsonProperty("publication_quatre_yeux") boolean publicationQuatreYeux,
			@JsonProperty("version") Long version,
			@JsonProperty("modifie_le") Instant modifieLe,
			@JsonProperty("modifie_par_nom") String modifieParNom
	) {
	}

	public record SocieteParametresUpdateRequest(
			@NotEmpty @JsonProperty("jours_travailles") List<String> joursTravailles,
			@NotNull @JsonProperty("duree_hebdo_minutes") Integer dureeHebdoMinutes,
			@NotNull @JsonProperty("duree_jour_minutes") Map<String, Integer> dureeJourMinutes,
			@NotNull @JsonProperty("heure_debut_journee") String heureDebutJournee,
			@NotNull @JsonProperty("heure_fin_journee") String heureFinJournee,
			@NotNull @JsonProperty("base_decompte_conge") BaseDecompteConge baseDecompteConge,
			@JsonProperty("jours_acquis_par_mois") BigDecimal joursAcquisParMois,
			@NotNull @JsonProperty("duree_max_sortie_minutes") Integer dureeMaxSortieMinutes,
			@NotNull @JsonProperty("delegation_auto_conges") Boolean delegationAutoConges,
			@NotNull @JsonProperty("publication_quatre_yeux") Boolean publicationQuatreYeux,
			@NotNull @JsonProperty("version") Long version
	) {
	}

	public record SocieteParametresJournalItem(
			@JsonProperty("id") UUID id,
			@JsonProperty("auteur_compte_id") UUID auteurCompteId,
			@JsonProperty("auteur_nom") String auteurNom,
			@JsonProperty("avant_json") String avantJson,
			@JsonProperty("apres_json") String apresJson,
			@JsonProperty("horodatage") Instant horodatage
	) {
	}
}
