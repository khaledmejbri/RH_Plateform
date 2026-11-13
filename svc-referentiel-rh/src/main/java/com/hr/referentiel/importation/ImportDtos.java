package com.hr.referentiel.importation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hr.referentiel.dto.PageReferentielResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contrat JSON (snake_case) des imports (ADR soldes, annexe A, I1 à I12). */
public final class ImportDtos {

	private ImportDtos() {
	}

	public record ColonneVue(
			@JsonProperty("index") int index,
			@JsonProperty("en_tete") String enTete,
			@JsonProperty("champ") String champ,
			@JsonProperty("champ_libelle") String champLibelle,
			@JsonProperty("etat") String etat) {
	}

	public record ChampDisponible(
			@JsonProperty("champ") String champ,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("obligatoire_creation") boolean obligatoireCreation) {
	}

	public record ProblemeVue(
			@JsonProperty("code") String code,
			@JsonProperty("colonne") @JsonInclude(JsonInclude.Include.NON_NULL) String colonne,
			@JsonProperty("valeur") @JsonInclude(JsonInclude.Include.NON_NULL) String valeur,
			@JsonProperty("message") String message) {
	}

	public record ModificationVue(
			@JsonProperty("champ") String champ,
			@JsonProperty("champ_libelle") String champLibelle,
			@JsonProperty("avant") String avant,
			@JsonProperty("apres") String apres) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record LigneAnalyse(
			@JsonProperty("numero") int numero,
			@JsonProperty("matricule") String matricule,
			@JsonProperty("nom_complet") String nomComplet,
			@JsonProperty("action") String action,
			@JsonProperty("action_libelle") String actionLibelle,
			@JsonProperty("erreurs") List<ProblemeVue> erreurs,
			@JsonProperty("avertissements") List<ProblemeVue> avertissements,
			@JsonProperty("modifications") List<ModificationVue> modifications,
			@JsonProperty("solde_actuel") BigDecimal soldeActuel,
			@JsonProperty("solde_cible") BigDecimal soldeCible,
			@JsonProperty("ecart") BigDecimal ecart,
			@JsonProperty("motif") String motif) {
	}

	public record TotauxVue(
			@JsonProperty("lignes") int lignes,
			@JsonProperty("creations") @JsonInclude(JsonInclude.Include.ALWAYS) Integer creations,
			@JsonProperty("mises_a_jour") @JsonInclude(JsonInclude.Include.ALWAYS) Integer misesAJour,
			@JsonProperty("inchangees") @JsonInclude(JsonInclude.Include.ALWAYS) Integer inchangees,
			@JsonProperty("ajustements") @JsonInclude(JsonInclude.Include.ALWAYS) Integer ajustements,
			@JsonProperty("erreurs") int erreurs,
			@JsonProperty("avertissements") int avertissements) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Analyse(
			@JsonProperty("analyse_id") UUID analyseId,
			@JsonProperty("type_import") String typeImport,
			@JsonProperty("statut") String statut,
			@JsonProperty("expire_le") Instant expireLe,
			@JsonProperty("nom_fichier") String nomFichier,
			@JsonProperty("creer_comptes") Boolean creerComptes,
			@JsonProperty("motif_global") String motifGlobal,
			@JsonProperty("ligne_en_tete") Integer ligneEnTete,
			@JsonProperty("colonnes") List<ColonneVue> colonnes,
			@JsonProperty("champs_disponibles") List<ChampDisponible> champsDisponibles,
			@JsonProperty("erreurs_fichier") List<ProblemeVue> erreursFichier,
			@JsonProperty("colonnes_ignorees") List<String> colonnesIgnorees,
			@JsonProperty("avertissements_fichier") List<ProblemeVue> avertissementsFichier,
			@JsonProperty("totaux") TotauxVue totaux,
			@JsonProperty("confirmable") boolean confirmable,
			@JsonProperty("lignes") PageReferentielResponse<LigneAnalyse> lignes) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CompteRendu(
			@JsonProperty("import_id") UUID importId,
			@JsonProperty("type_import") String typeImport,
			@JsonProperty("creees") Integer creees,
			@JsonProperty("mises_a_jour") Integer misesAJour,
			@JsonProperty("inchangees") Integer inchangees,
			@JsonProperty("rejetees") int rejetees,
			@JsonProperty("comptes_demandes") Integer comptesDemandes,
			@JsonProperty("ajustements") Integer ajustements,
			@JsonProperty("avertissements") List<String> avertissements) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ImportResume(
			@JsonProperty("import_id") UUID importId,
			@JsonProperty("type_import") String typeImport,
			@JsonProperty("type_import_libelle") String typeImportLibelle,
			@JsonProperty("confirme_le") Instant confirmeLe,
			@JsonProperty("auteur_nom") String auteurNom,
			@JsonProperty("nom_fichier") String nomFichier,
			@JsonProperty("creer_comptes") Boolean creerComptes,
			@JsonProperty("motif_global") String motifGlobal,
			@JsonProperty("totaux") CompteRendu totaux) {
	}

	public record LigneHistorique(
			@JsonProperty("numero_ligne") int numeroLigne,
			@JsonProperty("matricule") String matricule,
			@JsonProperty("action") String action,
			@JsonProperty("action_libelle") String actionLibelle,
			@JsonProperty("changements") List<ModificationVue> changements,
			@JsonProperty("erreurs_libelle") List<String> erreursLibelle) {
	}

	public record ColonneChoix(
			@JsonProperty("index") int index,
			@JsonProperty("champ") String champ) {
	}

	public record CorrespondanceRequest(
			@JsonProperty("ligne_en_tete") Integer ligneEnTete,
			@JsonProperty("colonnes") List<ColonneChoix> colonnes,
			@JsonProperty("creer_comptes") Boolean creerComptes,
			@JsonProperty("motif_global") String motifGlobal) {
	}

	public record ConfirmationRequest(@JsonProperty("motif_global") String motifGlobal) {
	}
}
