package com.hr.referentiel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contrat JSON (snake_case) des soldes de congé (ADR soldes, annexe A, A1 à A3 et B1 à B6). */
public final class SoldeDtos {

	private SoldeDtos() {
	}

	public record Solde(
			@JsonProperty("acquis") BigDecimal acquis,
			@JsonProperty("reserve") BigDecimal reserve,
			@JsonProperty("disponible") BigDecimal disponible) {
	}

	public record Mouvement(
			@JsonProperty("type") String type,
			@JsonProperty("type_libelle") String typeLibelle,
			@JsonProperty("nombre") BigDecimal nombre,
			@JsonProperty("solde_apres") BigDecimal soldeApres,
			@JsonProperty("cree_le") Instant creeLe,
			@JsonProperty("auteur_nom") String auteurNom,
			@JsonProperty("origine") String origine,
			@JsonProperty("origine_libelle") String origineLibelle,
			@JsonProperty("motif") String motif,
			@JsonProperty("demande_id") UUID demandeId,
			@JsonProperty("demande_numero") String demandeNumero,
			@JsonProperty("mois_acquisition") String moisAcquisition,
			@JsonProperty("mois_acquisition_libelle") String moisAcquisitionLibelle) {
	}

	public record JourFerieSimule(
			@JsonProperty("date") LocalDate date,
			@JsonProperty("libelle") String libelle) {
	}

	public record Blocage(
			@JsonProperty("code") String code,
			@JsonProperty("libelle") String libelle) {
	}

	public record Simulation(
			@JsonProperty("jours_decomptes") BigDecimal joursDecomptes,
			@JsonProperty("disponible") BigDecimal disponible,
			@JsonProperty("disponible_apres") BigDecimal disponibleApres,
			@JsonProperty("jours_feries") List<JourFerieSimule> joursFeries,
			@JsonProperty("blocage") Blocage blocage) {
	}

	public record DernierMouvement(
			@JsonProperty("date") Instant date,
			@JsonProperty("type_libelle") String typeLibelle,
			@JsonProperty("nombre") BigDecimal nombre) {
	}

	public record SoldeLigne(
			@JsonProperty("collaborateur_id") UUID collaborateurId,
			@JsonProperty("matricule") String matricule,
			@JsonProperty("nom_complet") String nomComplet,
			@JsonProperty("unite_libelle") String uniteLibelle,
			@JsonProperty("statut") String statut,
			@JsonProperty("statut_libelle") String statutLibelle,
			@JsonProperty("acquis") BigDecimal acquis,
			@JsonProperty("reserve") BigDecimal reserve,
			@JsonProperty("disponible") BigDecimal disponible,
			@JsonProperty("dernier_mouvement") DernierMouvement dernierMouvement,
			@JsonProperty("version") long version,
			@JsonProperty("est_moi") boolean estMoi) {
	}

	/** Ajustement : {@code ecart} ou {@code nouveau_solde} (jamais les deux), motif et version obligatoires. */
	public record AjustementRequest(
			@JsonProperty("ecart") Object ecart,
			@JsonProperty("nouveau_solde") Object nouveauSolde,
			@JsonProperty("motif") String motif,
			@JsonProperty("version") Long version) {
	}

	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record AjustementResponse(
			@JsonProperty("solde") SoldeLigne solde,
			@JsonProperty("mouvement") Mouvement mouvement,
			@JsonProperty("avertissement") String avertissement) {
	}
}
