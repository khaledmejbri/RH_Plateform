package com.hr.referentiel.importation;

import com.hr.referentiel.importation.parametrage.ParametrageImport;
import com.hr.referentiel.importation.parametrage.TypeImportParametrable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private static final Logger log = LoggerFactory.getLogger(ReconnaissanceEnTetes.class);

	/** Nombre de lignes examinées pour trouver la ligne d'en-tête. */
	public static final int LIGNES_EXAMINEES = 10;
	/** Nombre minimal d'en-têtes reconnus pour retenir une ligne d'en-tête. */
	public static final int MINIMUM_RECONNUS = 2;

	private static final Pattern OBLIGATOIRE = Pattern.compile("\\(\\s*obligatoire\\s*\\)");
	private static final Pattern NON_ALPHANUM = Pattern.compile("[^a-z0-9]+");
	private static final Pattern MARQUES = Pattern.compile("\\p{M}+");
	private static final Pattern CHIFFRES = Pattern.compile("[0-9]+");

	private final Map<String, ChampImport> parSynonyme = new HashMap<>();
	/** Champs personnalisés actifs par forme complète (chiffres gardés). */
	private final Map<String, ChampImport> parPerso = new HashMap<>();
	private final List<? extends ChampImport> champs;

	/** Reconnaissance par défaut (synonymes du code), pour les tests et le repli. */
	public ReconnaissanceEnTetes(List<? extends ChampImport> champs) {
		this.champs = champs;
		for (ChampImport c : champs) {
			for (String s : c.synonymes()) {
				parSynonyme.put(normaliser(s), c);
			}
		}
	}

	/**
	 * Reconnaissance selon le paramétrage figé (D-I19, D-I20) : en-tête réglé de chaque champ (même hors modèle) et ses
	 * synonymes actifs ; les synonymes inactifs sont ignorés. Collision (impossible si les invariants tiennent) :
	 * l'en-tête l'emporte sur un synonyme, puis le premier champ dans l'ordre réglé.
	 */
	public ReconnaissanceEnTetes(ParametrageImport parametrage) {
		TypeImportParametrable type = TypeImportParametrable.parTypeImport(parametrage.typeImport()).orElseThrow();
		List<ChampImport> tous = new ArrayList<>(type.champs());
		List<ParametrageImport.ChampRegle> ordonnes = parametrage.champs().stream()
				.sorted(java.util.Comparator.comparingInt(ParametrageImport.ChampRegle::ordre)).toList();
		Map<String, ChampImport> persos = new HashMap<>();
		for (ParametrageImport.ChampRegle r : ordonnes) {
			if (r.personnalise()) {
				ParametrageImport.DefinitionPerso d = parametrage.definition(r.code());
				ChampImport c = new ChampPersoImport(r.code(), d != null ? d.libelle() : r.enTete());
				persos.put(r.code(), c);
				tous.add(c);
			}
		}
		this.champs = tous;
		// reconnaissance en deux temps (P2-4) : champs personnalisés (forme complète), puis champs fixes (forme coupée)
		for (ParametrageImport.ChampRegle r : ordonnes) {
			ajouter(r.personnalise() ? parPerso : parSynonyme, r.personnalise() ? normaliserComplet(r.enTete())
					: normaliser(r.enTete()), r.code(), r.personnalise() ? persos.get(r.code()) : type.champ(r.code()).orElse(null));
		}
		for (ParametrageImport.ChampRegle r : ordonnes) {
			for (String forme : r.formesActives()) {
				ajouter(r.personnalise() ? parPerso : parSynonyme, forme, r.code(),
						r.personnalise() ? persos.get(r.code()) : type.champ(r.code()).orElse(null));
			}
		}
	}

	private void ajouter(Map<String, ChampImport> cible, String forme, String code, ChampImport champ) {
		if (forme == null || forme.isEmpty() || champ == null) {
			return;
		}
		ChampImport deja = cible.putIfAbsent(forme, champ);
		if (deja != null && !deja.name().equals(code)) {
			log.warn("Paramétrage d'import : la forme « {} » désigne les champs {} et {} (le premier l'emporte)", forme,
					deja.name(), code);
		}
	}

	/** Forme d'un champ fixe (coupée au premier mot numérique) ou personnalisé (complète). */
	public static String forme(boolean personnalise, String texte) {
		return personnalise ? normaliserComplet(texte) : normaliser(texte);
	}

	/** Mêmes opérations que {@link #normaliser} sans la coupure au premier mot numérique (champs personnalisés). */
	public static String normaliserComplet(String brut) {
		return nettoyer(brut);
	}

	private static String nettoyer(String brut) {
		if (brut == null) {
			return "";
		}
		String s = brut.toLowerCase(Locale.ROOT);
		s = OBLIGATOIRE.matcher(s).replaceAll(" ");
		s = s.replace('*', ' ');
		s = MARQUES.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
		return NON_ALPHANUM.matcher(s).replaceAll(" ").trim();
	}

	public static String normaliser(String brut) {
		if (brut == null) {
			return "";
		}
		String s = nettoyer(brut);
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
		String complet = normaliserComplet(enTete);
		if (!complet.isEmpty() && parPerso.containsKey(complet)) {
			return Optional.of(parPerso.get(complet));
		}
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
