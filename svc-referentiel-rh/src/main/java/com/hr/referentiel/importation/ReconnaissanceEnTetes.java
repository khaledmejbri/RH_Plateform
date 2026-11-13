package com.hr.referentiel.importation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reconnaissance des en-têtes (D-I03) : normalisation (minuscules, sans accents, « (obligatoire) » et astérisques
 * retirés, ponctuation et symboles remplacés par une espace, espaces réduits, coupure au premier mot fait de chiffres),
 * puis égalité stricte avec un synonyme (jamais de recherche partielle).
 */
public final class ReconnaissanceEnTetes {

	/** Nombre de lignes examinées pour trouver la ligne d'en-tête. */
	public static final int LIGNES_EXAMINEES = 10;
	/** Nombre minimal d'en-têtes reconnus pour retenir une ligne d'en-tête. */
	public static final int MINIMUM_RECONNUS = 2;

	private static final Pattern OBLIGATOIRE = Pattern.compile("\\(\\s*obligatoire\\s*\\)");
	private static final Pattern NON_ALPHANUM = Pattern.compile("[^a-z0-9]+");
	private static final Pattern MARQUES = Pattern.compile("\\p{M}+");
	private static final Pattern CHIFFRES = Pattern.compile("[0-9]+");

	private final Map<String, ChampImport> parSynonyme = new HashMap<>();
	private final List<? extends ChampImport> champs;

	public ReconnaissanceEnTetes(List<? extends ChampImport> champs) {
		this.champs = champs;
		for (ChampImport c : champs) {
			for (String s : c.synonymes()) {
				parSynonyme.put(normaliser(s), c);
			}
		}
	}

	public static String normaliser(String brut) {
		if (brut == null) {
			return "";
		}
		String s = brut.toLowerCase(Locale.ROOT);
		s = OBLIGATOIRE.matcher(s).replaceAll(" ");
		s = s.replace('*', ' ');
		s = MARQUES.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
		s = NON_ALPHANUM.matcher(s).replaceAll(" ").trim();
		StringBuilder sb = new StringBuilder();
		for (String mot : s.split(" ")) {
			if (mot.isEmpty()) {
				continue;
			}
			if (CHIFFRES.matcher(mot).matches()) {
				break;
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(mot);
		}
		return sb.toString();
	}

	public Optional<ChampImport> reconnaitre(String enTete) {
		String n = normaliser(enTete);
		return n.isEmpty() ? Optional.empty() : Optional.ofNullable(parSynonyme.get(n));
	}

	/** Champ reconnu pour chaque cellule d'une ligne (null = non reconnu), sans traiter les doublons. */
	public List<ChampImport> reconnaitreLigne(List<String> cellules) {
		List<ChampImport> r = new ArrayList<>();
		for (String c : cellules) {
			r.add(reconnaitre(c).orElse(null));
		}
		return r;
	}

	/** Nombre d'en-têtes reconnus dans une ligne. */
	public int compterReconnus(List<String> cellules) {
		return (int) reconnaitreLigne(cellules).stream().filter(java.util.Objects::nonNull).count();
	}

	public List<? extends ChampImport> champs() {
		return champs;
	}

	public ChampImport parNom(String nom) {
		for (ChampImport c : champs) {
			if (c.name().equals(nom)) {
				return c;
			}
		}
		return null;
	}
}
