package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.domain.RaisonIncompatibilite;
import com.hr.presence.domain.StatutEnrolement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/** Contrat mobile de la vérification faciale (ADR M09 face, révision 2). Aucun seuil n'est exposé (D-P06.d). */
public final class FacialMobileDtos {

	public static final String UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

	private FacialMobileDtos() {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EtatFacialMoi(
			@JsonProperty("verification_active") boolean verificationActive,
			@JsonProperty("controle_exige") boolean controleExige,
			@JsonProperty("date_mise_en_service") LocalDate dateMiseEnService,
			@JsonProperty("version_consentement_courante") String versionConsentementCourante,
			@JsonProperty("statut") StatutEnrolement statut,
			@JsonProperty("version_consentement_acceptee") String versionConsentementAcceptee,
			@JsonProperty("reconsentement_requis") boolean reconsentementRequis,
			@JsonProperty("ecran_consentement_requis") boolean ecranConsentementRequis,
			@JsonProperty("appareil_correspond") Boolean appareilCorrespond,
			@JsonProperty("appareil_incompatible") boolean appareilIncompatible,
			@JsonProperty("gabarit_a_effacer") boolean gabaritAEffacer,
			@JsonProperty("echecs_consecutifs") int echecsConsecutifs,
			@JsonProperty("bloque_jusqu_a") Instant bloqueJusqua,
			@JsonProperty("decide_le") Instant decideLe
	) {
	}

	public record ConsentementCourant(
			@JsonProperty("version") String version,
			@JsonProperty("texte") String texte,
			@JsonProperty("publie_le") Instant publieLe
	) {
	}

	public record EnrolementRequest(
			@NotBlank @Pattern(regexp = UUID_REGEX) @JsonProperty("device_id") String deviceId,
			@NotBlank @Size(max = 32) @JsonProperty("version_consentement") String versionConsentement,
			@NotNull @JsonProperty("vivacite_reussie") Boolean vivaciteReussie,
			@NotBlank @Size(max = 32) @JsonProperty("modele_version") String modeleVersion,
			@NotBlank @Pattern(regexp = UUID_REGEX) @JsonProperty("idempotency_key") String idempotencyKey
	) {
	}

	public record ConsentementDecisionRequest(
			@NotBlank @Size(max = 32) @JsonProperty("version_consentement") String versionConsentement
	) {
	}

	public record AppareilIncompatibleRequest(
			@NotBlank @Pattern(regexp = UUID_REGEX) @JsonProperty("device_id") String deviceId,
			@NotNull @JsonProperty("raison") RaisonIncompatibilite raison
	) {
	}

	/**
	 * {@code gabarit_present} est facultatif : l'appli le renseigne à {@code true} quand elle détient encore son gabarit
	 * sur l'appareil enrôlé, ce qui rend la demande sans objet (409 DEJA_ENROLE).
	 */
	public record DemandeReenrolementRequest(
			@NotBlank @Pattern(regexp = UUID_REGEX) @JsonProperty("device_id") String deviceId,
			@JsonProperty("gabarit_present") Boolean gabaritPresent
	) {
	}
}
