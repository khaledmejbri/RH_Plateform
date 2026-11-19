package com.hr.referentiel.importation.parametrage;

import com.hr.referentiel.champsperso.ReglesPerso;
import com.hr.referentiel.champsperso.TypeValeurPerso;
import com.hr.referentiel.importation.ChampImport;
import com.hr.referentiel.importation.ReconnaissanceEnTetes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Instantané immuable du paramétrage d'un type d'import (D-I25) : lu dans le cache, figé dans le résultat stocké de
 * l'analyse (JSON), repris tel quel par la correction, la confirmation et le fichier des lignes en erreur.
 *
 * @param champs réglage de chaque champ, dans l'ordre réglé
 */
public record ParametrageImport(String typeImport, long version, List<ChampRegle> champs,
		List<DefinitionPerso> definitionsPerso) {

	public ParametrageImport {
		definitionsPerso = definitionsPerso == null ? List.of() : definitionsPerso; // résultat stocké avant la partie 2
	}

	/** Sans champ personnalisé (partie 1, tests). */
	public ParametrageImport(String typeImport, long version, List<ChampRegle> champs) {
		this(typeImport, version, champs, List.of());
	}

	/**
	 * @param formesActives formes normalisées des synonymes actifs (l'en-tête réglé est reconnu en plus) ; formes
	 *        complètes (chiffres gardés) pour un champ personnalisé
	 */
	public record ChampRegle(String code, String enTete, boolean dansModele, ExigenceChamp exigence, int ordre,
			List<String> formesActives, Boolean personnalise) {

		/** Résultat stocké avant la partie 2 : composant absent, lu comme « champ fixe ». */
		public ChampRegle {
			personnalise = personnalise != null && personnalise;
		}

		public ChampRegle(String code, String enTete, boolean dansModele, ExigenceChamp exigence, int ordre,
				List<String> formesActives) {
			this(code, enTete, dansModele, exigence, ordre, formesActives, false);
		}
	}

	/** Définition figée d'un champ personnalisé actif (valeurs de liste actives et inactives, dans l'ordre). */
	public record DefinitionPerso(String code, String libelle, TypeValeurPerso typeValeur, ReglesPerso regles,
			List<ValeurListe> valeursListe) {

		public DefinitionPerso {
			regles = regles == null ? ReglesPerso.AUCUNE : regles;
			valeursListe = valeursListe == null ? List.of() : valeursListe;
		}

		public ValeurListe valeur(String code) {
			return valeursListe.stream().filter(v -> v.code().equals(code)).findFirst().orElse(null);
		}
	}

	public record ValeurListe(String code, String libelle, boolean actif) {
	}

	/** Définition d'un champ personnalisé actif ; {@code null} pour un champ fixe ou inconnu. */
	public DefinitionPerso definition(String code) {
		for (DefinitionPerso d : definitionsPerso) {
			if (d.code().equals(code)) {
				return d;
			}
		}
		return null;
	}

	/** Valeurs du code : repli d'un résultat stocké sans instantané, et base des tests. */
	public static ParametrageImport parDefaut(TypeImportParametrable type) {
		List<ChampRegle> champs = new ArrayList<>();
		int ordre = 0;
		for (ChampImport c : type.champs()) {
			Set<String> formes = new LinkedHashSet<>();
			c.synonymes().forEach(s -> formes.add(ReconnaissanceEnTetes.normaliser(s)));
			champs.add(new ChampRegle(c.name(), c.libelle(), true, c.exigenceDefaut(), ordre++, List.copyOf(formes)));
		}
		return new ParametrageImport(type.name(), 0, List.copyOf(champs));
	}

	/** Réglage d'un champ ; {@code null} si le code est inconnu. */
	public ChampRegle champ(String code) {
		for (ChampRegle c : champs) {
			if (c.code().equals(code)) {
				return c;
			}
		}
		return null;
	}

	/** En-tête réglé ; repli sur le nom du champ de la fiche. */
	public String enTete(String code) {
		ChampRegle c = champ(code);
		if (c != null && c.enTete() != null && !c.enTete().isBlank()) {
			return c.enTete();
		}
		return TypeImportParametrable.parTypeImport(typeImport).flatMap(t -> t.champ(code)).map(ChampImport::libelle)
				.orElse(code);
	}

	public ExigenceChamp exigence(String code) {
		ChampRegle c = champ(code);
		if (c != null && c.exigence() != null) {
			return c.exigence();
		}
		return TypeImportParametrable.parTypeImport(typeImport).flatMap(t -> t.champ(code))
				.map(ChampImport::exigenceDefaut).orElse(ExigenceChamp.FACULTATIF);
	}

	/** Champs présents dans le modèle, dans l'ordre réglé. */
	public List<ChampRegle> champsDuModele() {
		return champs.stream().filter(ChampRegle::dansModele).sorted(Comparator.comparingInt(ChampRegle::ordre)).toList();
	}
}
