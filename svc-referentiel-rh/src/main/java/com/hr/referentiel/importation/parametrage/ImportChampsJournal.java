package com.hr.referentiel.importation.parametrage;

import com.hr.referentiel.importation.ChampImport;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.LigneJournal;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.PageJournalImport;
import com.hr.referentiel.service.AuteurParametre;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Écriture et lecture du journal du paramétrage des champs d'import (D-I23). Les textes {@code avant} / {@code apres}
 * sont écrits en clair par l'appelant ; les libellés d'attribut viennent d'une table fixe (annexe A.1).
 */
@Component
public class ImportChampsJournal {

	public static final String EN_TETE = "EN_TETE";
	public static final String DANS_MODELE = "DANS_MODELE";
	public static final String ORDRE = "ORDRE";
	public static final String EXIGENCE = "EXIGENCE";
	public static final String SYNONYME_AJOUTE = "SYNONYME_AJOUTE";
	public static final String SYNONYME_DESACTIVE = "SYNONYME_DESACTIVE";
	public static final String SYNONYME_REACTIVE = "SYNONYME_REACTIVE";
	public static final String SYNONYME_SUPPRIME = "SYNONYME_SUPPRIME";
	public static final String REINITIALISATION = "REINITIALISATION";

	// partie 2 : définitions des champs personnalisés (annexe A bis.1)
	public static final String CHAMP_CREE = "CHAMP_CREE";
	public static final String LIBELLE = "LIBELLE";
	public static final String FINALITE = "FINALITE";
	public static final String TYPE_VALEUR = "TYPE_VALEUR";
	public static final String REGLE = "REGLE";
	public static final String VISIBLE_COLLABORATEUR = "VISIBLE_COLLABORATEUR";
	public static final String VALEUR_LISTE_AJOUTEE = "VALEUR_LISTE_AJOUTEE";
	public static final String VALEUR_LISTE_MODIFIEE = "VALEUR_LISTE_MODIFIEE";
	public static final String VALEUR_LISTE_DESACTIVEE = "VALEUR_LISTE_DESACTIVEE";
	public static final String VALEUR_LISTE_REACTIVEE = "VALEUR_LISTE_REACTIVEE";
	public static final String VALEUR_LISTE_SUPPRIMEE = "VALEUR_LISTE_SUPPRIMEE";
	public static final String CHAMP_DESACTIVE = "CHAMP_DESACTIVE";
	public static final String CHAMP_REACTIVE = "CHAMP_REACTIVE";
	public static final String CHAMP_SUPPRIME = "CHAMP_SUPPRIME";

	static final String TOUS_LES_CHAMPS = "Tous les champs";
	static final int LONGUEUR_MAX = 2000;

	private static final Map<String, String> LIBELLES_ATTRIBUT = Map.ofEntries(
			Map.entry(EN_TETE, "En-tête"),
			Map.entry(DANS_MODELE, "Dans le modèle"),
			Map.entry(ORDRE, "Ordre"),
			Map.entry(EXIGENCE, "Exigence"),
			Map.entry(SYNONYME_AJOUTE, "Synonyme ajouté"),
			Map.entry(SYNONYME_DESACTIVE, "Synonyme désactivé"),
			Map.entry(SYNONYME_REACTIVE, "Synonyme réactivé"),
			Map.entry(SYNONYME_SUPPRIME, "Synonyme supprimé"),
			Map.entry(REINITIALISATION, "Réinitialisation"),
			Map.entry(CHAMP_CREE, "Champ créé"),
			Map.entry(LIBELLE, "Libellé"),
			Map.entry(FINALITE, "Finalité"),
			Map.entry(TYPE_VALEUR, "Type"),
			Map.entry(REGLE, "Règle"),
			Map.entry(VISIBLE_COLLABORATEUR, "Visible par le collaborateur"),
			Map.entry(VALEUR_LISTE_AJOUTEE, "Valeur de liste ajoutée"),
			Map.entry(VALEUR_LISTE_MODIFIEE, "Valeur de liste modifiée"),
			Map.entry(VALEUR_LISTE_DESACTIVEE, "Valeur de liste désactivée"),
			Map.entry(VALEUR_LISTE_REACTIVEE, "Valeur de liste réactivée"),
			Map.entry(VALEUR_LISTE_SUPPRIMEE, "Valeur de liste supprimée"),
			Map.entry(CHAMP_DESACTIVE, "Champ désactivé"),
			Map.entry(CHAMP_REACTIVE, "Champ réactivé"),
			Map.entry(CHAMP_SUPPRIME, "Champ supprimé"));

	private final ImportChampJournalRepository repository;

	public ImportChampsJournal(ImportChampJournalRepository repository) {
		this.repository = repository;
	}

	/** Une ligne, dans la transaction de l'écriture. {@code version} = version du paramétrage après l'écriture. */
	public void ecrire(TypeImportParametrable type, long version, String champCode, String attribut, String avant,
			String apres, AuteurParametre auteur, Instant horodatage) {
		ecrire(type, version, champCode, attribut, avant, apres, auteur, horodatage, null);
	}

	/** {@code champLibelle} : libellé figé d'un champ personnalisé (null pour un champ fixe). */
	public void ecrire(TypeImportParametrable type, long version, String champCode, String attribut, String avant,
			String apres, AuteurParametre auteur, Instant horodatage, String champLibelle) {
		repository.save(new ImportChampJournal(type.name(), version, champCode, attribut, tronquer(avant), tronquer(apres),
				auteur.compteId(), auteur.nom(), horodatage, champLibelle));
	}

	/** Codes de champ déjà cités au journal du type (jamais réattribués). */
	public java.util.List<String> codesCites(TypeImportParametrable type) {
		return repository.codesCites(type.name());
	}

	public PageJournalImport lire(TypeImportParametrable type, int page, int taille) {
		Page<ImportChampJournal> p = repository.findByTypeImportOrderByHorodatageDescIdAsc(type.name(),
				PageRequest.of(Math.max(0, page), taille));
		return new PageJournalImport(p.getContent().stream().map(j -> versLigne(type, j)).toList(), p.getNumber(),
				taille, p.getTotalElements());
	}

	private static LigneJournal versLigne(TypeImportParametrable type, ImportChampJournal j) {
		String champLibelle = j.getChampCode() == null ? TOUS_LES_CHAMPS
				: j.getChampLibelle() != null ? j.getChampLibelle()
				: type.champ(j.getChampCode()).map(ChampImport::libelle).orElse("Champ retiré");
		return new LigneJournal(j.getId(), j.getHorodatage(), j.getAuteurNom(), j.getChampCode(), champLibelle,
				j.getAttribut(), LIBELLES_ATTRIBUT.getOrDefault(j.getAttribut(), "Modification"), j.getAvant(), j.getApres());
	}

	private static String tronquer(String texte) {
		return texte == null || texte.length() <= LONGUEUR_MAX ? texte : texte.substring(0, LONGUEUR_MAX);
	}
}
