package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.presence.domain.StatutEnrolement;
import com.hr.presence.domain.StatutReglageFacial;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contrat Plateforme RH de la vérification faciale (ADR M09 face, révision 2). */
public final class FacialAdminDtos {

	private FacialAdminDtos() {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ReglageResponse(
			@JsonProperty("statut") StatutReglageFacial statut,
			@JsonProperty("date_mise_en_service") LocalDate dateMiseEnService,
			@JsonProperty("seuil_score") int seuilScore,
			@JsonProperty("version_consentement_courante") String versionConsentementCourante,
			@JsonProperty("controle_exige") boolean controleExige,
			@JsonProperty("cadre_legal_confirme_par") String cadreLegalConfirmePar,
			@JsonProperty("cadre_legal_confirme_le") Instant cadreLegalConfirmeLe,
			@JsonProperty("modifie_par") String modifiePar,
			@JsonProperty("modifie_le") Instant modifieLe
	) {
	}

	/** Le seuil est contrôlé par le service (400 SEUIL_HORS_PLAGE) : pas de contrainte de bean ici. */
	public record ReglageRequest(
			@NotNull @JsonProperty("statut") StatutReglageFacial statut,
			@JsonProperty("date_mise_en_service") LocalDate dateMiseEnService,
			@JsonProperty("seuil_score") Integer seuilScore,
			@JsonProperty("cadre_legal_valide") Boolean cadreLegalValide
	) {
	}

	public record ConsentementPublicationRequest(
			@NotBlank @Size(max = 32) @JsonProperty("version") String version,
			@NotBlank @Size(min = 50, max = 20000) @JsonProperty("texte") String texte
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ConsentementVersionResume(
			@JsonProperty("version") String version,
			@JsonProperty("publie_le") Instant publieLe,
			@JsonProperty("publie_par") String publiePar,
			@JsonProperty("courante") boolean courante
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ConsentementVersionDetail(
			@JsonProperty("version") String version,
			@JsonProperty("texte") String texte,
			@JsonProperty("publie_le") Instant publieLe,
			@JsonProperty("publie_par") String publiePar
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EnrolementLigne(
			@JsonProperty("id") UUID id,
			@JsonProperty("compte_id") String compteId,
			@JsonProperty("collaborateur_id") String collaborateurId,
			@JsonProperty("collaborateur_nom") String collaborateurNom,
			@JsonProperty("statut") StatutEnrolement statut,
			@JsonProperty("version_consentement") String versionConsentement,
			@JsonProperty("decide_le") Instant decideLe,
			@JsonProperty("enrole_le") Instant enroleLe,
			@JsonProperty("demande_reenrolement_le") Instant demandeReenrolementLe,
			@JsonProperty("device_id_masque") String deviceIdMasque,
			@JsonProperty("device_id_demande_masque") String deviceIdDemandeMasque,
			@JsonProperty("appareil_incompatible") boolean appareilIncompatible,
			@JsonProperty("echecs_consecutifs") int echecsConsecutifs,
			@JsonProperty("bloque_jusqu_a") Instant bloqueJusqua
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record HistoriqueFacial(
			@JsonProperty("action") String action,
			@JsonProperty("statut_avant") StatutEnrolement statutAvant,
			@JsonProperty("statut_apres") StatutEnrolement statutApres,
			@JsonProperty("acteur_nom_ou_compte") String acteurNomOuCompte,
			@JsonProperty("acteur_role") String acteurRole,
			@JsonProperty("motif") String motif,
			@JsonProperty("version") String version,
			@JsonProperty("horodatage") Instant horodatage
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EnrolementDetail(
			@JsonProperty("id") UUID id,
			@JsonProperty("compte_id") String compteId,
			@JsonProperty("collaborateur_id") String collaborateurId,
			@JsonProperty("collaborateur_nom") String collaborateurNom,
			@JsonProperty("statut") StatutEnrolement statut,
			@JsonProperty("version_consentement") String versionConsentement,
			@JsonProperty("decide_le") Instant decideLe,
			@JsonProperty("enrole_le") Instant enroleLe,
			@JsonProperty("demande_reenrolement_le") Instant demandeReenrolementLe,
			@JsonProperty("device_id_masque") String deviceIdMasque,
			@JsonProperty("device_id_demande_masque") String deviceIdDemandeMasque,
			@JsonProperty("appareil_incompatible") boolean appareilIncompatible,
			@JsonProperty("echecs_consecutifs") int echecsConsecutifs,
			@JsonProperty("bloque_jusqu_a") Instant bloqueJusqua,
			@JsonProperty("historique") List<HistoriqueFacial> historique
	) {
	}

	public record DeblocageRequest(
			@NotBlank @Size(min = 5, max = 500) @JsonProperty("motif") String motif
	) {
	}
}
