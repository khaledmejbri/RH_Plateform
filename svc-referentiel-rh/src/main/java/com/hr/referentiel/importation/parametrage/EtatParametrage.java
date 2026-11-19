package com.hr.referentiel.importation.parametrage;

import com.hr.referentiel.champsperso.ChampPerso;
import com.hr.referentiel.champsperso.ChampPersoValeurListe;
import com.hr.referentiel.importation.ChampImport;
import com.hr.referentiel.importation.ReconnaissanceEnTetes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * État d'un type d'import lu sous verrou : la ligne de version, les réglages de champ, tous les synonymes, les
 * définitions des champs personnalisés et leurs valeurs de liste. Partagé par les écritures du paramétrage
 * ({@link ImportParametrageService}) et des définitions ({@code ChampPersoDefinitionService}).
 */
public final class EtatParametrage {

	public final TypeImportParametrable type;
	public final ImportParametrage parametrage;
	public final List<ImportChampRegle> regles;
	public final List<ImportSynonyme> synonymes;
	public final List<ChampPerso> persos;
	public final Map<UUID, List<ChampPersoValeurListe>> valeursListe = new HashMap<>();

	EtatParametrage(TypeImportParametrable type, ImportParametrage parametrage, List<ImportChampRegle> regles,
			List<ImportSynonyme> synonymes, List<ChampPerso> persos, List<ChampPersoValeurListe> valeurs) {
		this.type = type;
		this.parametrage = parametrage;
		this.regles = new ArrayList<>(regles);
		this.synonymes = new ArrayList<>(synonymes);
		this.persos = new ArrayList<>(persos);
		for (ChampPersoValeurListe v : valeurs) {
			valeursListe.computeIfAbsent(v.getChampPersoId(), k -> new ArrayList<>()).add(v);
		}
	}

	public ImportChampRegle regle(String code) {
		return regles.stream().filter(r -> r.getChampCode().equals(code)).findFirst().orElse(null);
	}

	public List<ImportSynonyme> synonymesDe(String code) {
		return synonymes.stream().filter(s -> s.getChampCode().equals(code)).toList();
	}

	/** Définition d'un champ personnalisé (actif ou non) ; null pour un champ fixe ou inconnu. */
	public ChampPerso perso(String code) {
		return persos.stream().filter(p -> p.getCode().equals(code)).findFirst().orElse(null);
	}

	public boolean estPerso(String code) {
		return perso(code) != null;
	}

	/** Un champ fixe est toujours actif ; un champ personnalisé l'est selon sa définition. */
	public boolean actif(String code) {
		ChampPerso p = perso(code);
		return p == null || p.isActif();
	}

	/** Champ connu : fixe du code ou personnalisé (actif ou non). */
	public boolean connu(String code) {
		return type.champ(code).isPresent() || estPerso(code);
	}

	public List<ChampPersoValeurListe> valeursDe(ChampPerso p) {
		return valeursListe.getOrDefault(p.getId(), new ArrayList<>()).stream()
				.sorted(java.util.Comparator.comparingInt(ChampPersoValeurListe::getOrdre)).toList();
	}

	/** Nombre de champs attendus par P2 : champs fixes et champs personnalisés actifs. */
	public int nombreAttendu() {
		return type.champs().size() + (int) persos.stream().filter(ChampPerso::isActif).count();
	}

	public String libelle(String code) {
		ChampPerso p = perso(code);
		if (p != null) {
			return p.getLibelle();
		}
		return type.champ(code).map(ChampImport::libelle).orElse(code);
	}

	/** Forme d'un texte pour ce champ : complète pour un champ personnalisé, coupée pour un champ fixe. */
	public String forme(String code, String texte) {
		return ReconnaissanceEnTetes.forme(estPerso(code), texte);
	}

	/**
	 * Champ qui reconnaît déjà cette forme (en-tête réglé ou synonyme actif), hors le synonyme ignoré ; sinon null.
	 * Un champ personnalisé inactif ne reconnaît rien (hors modèle, absent de l'instantané).
	 */
	public String proprietaire(String forme, UUID synonymeIgnore) {
		for (ImportChampRegle r : regles) {
			if (actif(r.getChampCode()) && forme(r.getChampCode(), r.getEnTete()).equals(forme)) {
				return r.getChampCode();
			}
		}
		for (ImportSynonyme s : synonymes) {
			if (s.isActif() && actif(s.getChampCode()) && s.getFormeNormalisee().equals(forme)
					&& !s.getId().equals(synonymeIgnore)) {
				return s.getChampCode();
			}
		}
		return null;
	}
}
