package com.hr.referentiel.importation;

import com.hr.referentiel.importation.ImportModeles.Colonne;
import com.hr.referentiel.importation.ImportModeles.Probleme;
import com.hr.referentiel.importation.parametrage.ExigenceChamp;
import com.hr.referentiel.importation.parametrage.ParametrageImport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Erreurs de fichier d'une correspondance de colonnes (D-I13.c, D-I18) : colonnes en double, matricule et solde absents
 * (codes inchangés), et, nouveau, tout autre champ « toujours obligatoire » sans colonne. Les messages nomment les champs
 * par l'en-tête réglé figé dans l'analyse. Classe pure : aucune lecture de base.
 */
final class ControleColonnes {

	private ControleColonnes() {
	}

	static List<Probleme> erreurs(List<Colonne> colonnes, ParametrageImport parametrage, boolean soldes) {
		List<Probleme> erreurs = new ArrayList<>();
		Map<String, List<Colonne>> doubles = colonnes.stream().filter(c -> ImportModeles.EN_DOUBLE.equals(c.etat()))
				.collect(Collectors.groupingBy(Colonne::champ, LinkedHashMap::new, Collectors.toList()));
		doubles.forEach((champ, liste) -> erreurs.add(Probleme.fichier("COLONNE_EN_DOUBLE", "Les colonnes "
				+ liste.stream().map(c -> "« " + c.enTete() + " »").collect(Collectors.joining(" et "))
				+ " désignent toutes deux le champ « " + parametrage.enTete(champ) + " ».")));
		if (!presente(colonnes, "MATRICULE")) {
			erreurs.add(Probleme.fichier("COLONNE_MATRICULE_ABSENTE", "Aucune colonne ne désigne le matricule."));
		}
		if (soldes && !presente(colonnes, "SOLDE")) {
			erreurs.add(Probleme.fichier("COLONNE_SOLDE_ABSENTE", "Aucune colonne ne désigne le solde."));
		}
		for (ParametrageImport.ChampRegle champ : parametrage.champs()) {
			String code = champ.code();
			boolean dejaTraite = "MATRICULE".equals(code) || (soldes && "SOLDE".equals(code));
			if (!dejaTraite && parametrage.exigence(code) == ExigenceChamp.TOUJOURS && !presente(colonnes, code)) {
				erreurs.add(Probleme.fichier("COLONNE_OBLIGATOIRE_ABSENTE",
						"Aucune colonne ne désigne le champ « " + parametrage.enTete(code) + " »."));
			}
		}
		return erreurs;
	}

	/** Une colonne non ambiguë désigne le champ. */
	private static boolean presente(List<Colonne> colonnes, String code) {
		return colonnes.stream().anyMatch(c -> code.equals(c.champ()) && !ImportModeles.EN_DOUBLE.equals(c.etat()));
	}
}
