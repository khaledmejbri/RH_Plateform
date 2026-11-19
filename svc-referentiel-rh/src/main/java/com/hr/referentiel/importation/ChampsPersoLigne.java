package com.hr.referentiel.importation;

import com.hr.referentiel.champsperso.ControleValeurPerso;
import com.hr.referentiel.champsperso.FormatValeurPerso;
import com.hr.referentiel.champsperso.ValeurTypee;
import com.hr.referentiel.champsperso.ValeursPersoLot;
import com.hr.referentiel.importation.ImportModeles.Colonne;
import com.hr.referentiel.importation.ImportModeles.LigneResultat;
import com.hr.referentiel.importation.lecture.Fichier.LigneBrute;
import com.hr.referentiel.importation.ImportModeles.Modification;
import com.hr.referentiel.importation.ImportModeles.Probleme;
import com.hr.referentiel.importation.parametrage.ExigenceChamp;
import com.hr.referentiel.importation.parametrage.ParametrageImport;
import com.hr.referentiel.importation.parametrage.ParametrageImport.DefinitionPerso;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;

/**
 * Évaluation des colonnes personnalisées d'UNE ligne d'import (ADR partie 2, P2-8) : exigence, contrôle de la valeur
 * (mode import), comparaison typée avec la valeur actuelle, modifications formatées. Classe pure, sans base : les
 * messages citent l'en-tête réglé figé dans l'analyse. Cellule vide : rien (jamais un effacement).
 */
final class ChampsPersoLigne {

	/** Valeurs canoniques retenues (code -> forme canonique), modifications listées, erreurs de ligne. */
	record Resultat(Map<String, String> canoniques, List<Modification> modifications, List<Probleme> erreurs) {
	}

	private ChampsPersoLigne() {
	}

	/**
	 * @param brutes cellules personnalisées de la ligne (code -> texte rogné, absent = vide)
	 * @param actuelles valeurs actuelles de la fiche ({@code null} pour les soldes : aucune comparaison)
	 * @param creation ligne qui crée la fiche (exigence « à la création »)
	 * @param auteurEstCible l'auteur de l'import est le collaborateur de la ligne
	 */
	static Resultat evaluer(Map<String, String> brutes, ParametrageImport parametrage, Map<String, ValeurTypee> actuelles,
			boolean creation, boolean auteurEstCible) {
		Map<String, String> canoniques = new LinkedHashMap<>();
		List<Modification> modifications = new ArrayList<>();
		List<Probleme> erreurs = new ArrayList<>();
		boolean changement = false;
		for (DefinitionPerso def : parametrage.definitionsPerso()) {
			String enTete = parametrage.enTete(def.code());
			String brut = brutes.get(def.code());
			ControleValeurPerso.Resultat r = ControleValeurPerso.lire(def, brut, ControleValeurPerso.Mode.IMPORT, enTete);
			if (r.probleme() != null) {
				erreurs.add(new Probleme(r.probleme().code(), enTete, court(brut), r.probleme().message()));
				continue;
			}
			if (r.vide()) {
				ExigenceChamp e = parametrage.exigence(def.code());
				if (e == ExigenceChamp.TOUJOURS || (e == ExigenceChamp.CREATION && creation)) {
					erreurs.add(new Probleme("CHAMP_OBLIGATOIRE", enTete, null, enTete + " obligatoire."));
				}
				continue;
			}
			canoniques.put(def.code(), FormatValeurPerso.canonique(r.valeur()));
			if (actuelles != null) {
				ValeurTypee avant = actuelles.get(def.code());
				if (!r.valeur().memeValeur(avant)) {
					changement = true;
					modifications.add(new Modification(def.code(), def.libelle(),
							avant == null ? "" : FormatValeurPerso.affichage(avant, def), FormatValeurPerso.affichage(r.valeur(), def)));
				}
			}
		}
		if (changement && auteurEstCible) {
			erreurs.add(new Probleme("AUTO_MODIFICATION_INTERDITE", null, null,
					"Vous ne pouvez pas modifier vos propres informations complémentaires."));
		}
		return new Resultat(canoniques, modifications, erreurs);
	}

	/** Cellules des colonnes personnalisées d'une ligne (code -> texte rogné, première colonne gagnante). */
	static Map<String, String> cellules(LigneBrute ligne, List<Colonne> colonnes) {
		Map<String, String> v = new java.util.HashMap<>();
		for (Colonne c : colonnes) {
			boolean fixe = ChampImport.Fiche.parCode(c.champ()) != null || ChampImport.Solde.parCode(c.champ()) != null;
			if (c.champ() != null && !fixe && c.index() < ligne.cellules().size()) {
				String brut = ligne.cellules().get(c.index());
				if (brut != null && !brut.isBlank()) {
					v.putIfAbsent(c.champ(), brut.strip());
				}
			}
		}
		return v;
	}

	/** Demandes d'écriture en lot : valeurs personnalisées des lignes importées, par fiche (existante ou créée). */
	static List<ValeursPersoLot.Demande> demandes(List<LigneResultat> importables, Map<String, UUID> idsCreees) {
		List<ValeursPersoLot.Demande> demandes = new ArrayList<>();
		for (LigneResultat l : importables) {
			Map<String, String> perso = new java.util.HashMap<>(l.valeurs());
			perso.keySet().removeIf(k -> ChampImport.Fiche.parCode(k) != null);
			UUID cible = l.collaborateurId() != null ? l.collaborateurId()
					: idsCreees.get(l.matricule().toLowerCase(java.util.Locale.ROOT));
			if (!perso.isEmpty() && cible != null) {
				demandes.add(new ValeursPersoLot.Demande(cible, l.numero(), perso));
			}
		}
		return demandes;
	}

	private static String court(String v) {
		if (v == null) {
			return null;
		}
		return v.length() > 60 ? v.substring(0, 60) + "…" : v;
	}
}
