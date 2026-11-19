package com.hr.referentiel.importation.parametrage;

import com.hr.referentiel.champsperso.ChampPerso;
import com.hr.referentiel.champsperso.ChampPersoUtilisations;
import com.hr.referentiel.champsperso.ChampPersoUtilisations.Compte;
import com.hr.referentiel.champsperso.ChampPersoValeurListe;
import com.hr.referentiel.champsperso.ReglesPerso;
import com.hr.referentiel.champsperso.TypeValeurPerso;
import com.hr.referentiel.importation.ChampImport;
import com.hr.referentiel.importation.ChampPersoImport;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.ChampVue;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.ExigenceVue;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.Parametrage;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.ReglesVue;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.SynonymeVue;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.ValeurListeVue;
import com.hr.referentiel.service.FormatJours;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Construction de la réponse {@code Parametrage} (P1 à P6, C1 à C6) à partir de l'état lu en base. */
public final class ImportParametrageVues {

	private ImportParametrageVues() {
	}

	static Parametrage construire(EtatParametrage etat, ChampPersoUtilisations utilisations) {
		TypeImportParametrable type = etat.type;
		Map<String, List<ImportSynonyme>> parChamp = etat.synonymes.stream()
				.collect(Collectors.groupingBy(ImportSynonyme::getChampCode));
		List<String> codesPerso = etat.persos.stream().map(ChampPerso::getCode).toList();
		Map<String, Compte> comptes = utilisations.parChamp(type.name(), codesPerso);
		Map<String, Integer> parValeur = utilisations.parValeurListe(type.name(), codesPerso);

		List<ChampVue> actifs = new ArrayList<>();
		for (ImportChampRegle r : etat.regles.stream().sorted(Comparator.comparingInt(ImportChampRegle::getOrdre)).toList()) {
			List<ImportSynonyme> syn = parChamp.getOrDefault(r.getChampCode(), List.of());
			ChampPerso p = etat.perso(r.getChampCode());
			if (p != null) {
				continue; // les champs personnalisés sont ajoutés ensuite : actifs dans l'ordre réglé, puis inactifs
			}
			if (type.champ(r.getChampCode()).isPresent()) {
				actifs.add(fixe(type, r, syn));
			}
		}
		List<ChampVue> persos = new ArrayList<>();
		for (ImportChampRegle r : etat.regles.stream().sorted(Comparator.comparingInt(ImportChampRegle::getOrdre)).toList()) {
			ChampPerso p = etat.perso(r.getChampCode());
			if (p != null && p.isActif()) {
				persos.add(perso(etat, p, r, parChamp.getOrDefault(p.getCode(), List.of()),
						comptes.getOrDefault(p.getCode(), Compte.AUCUN), parValeur));
			}
		}
		// l'ordre réglé mêle fixes et personnalisés : tri global par ordre
		List<ChampVue> champs = new ArrayList<>(actifs);
		champs.addAll(persos);
		champs.sort(Comparator.comparing(ChampVue::ordre));
		etat.persos.stream().filter(p -> !p.isActif()).sorted(Comparator.comparing(ChampPerso::getCreeLe)).forEach(p -> {
			ImportChampRegle r = etat.regle(p.getCode());
			if (r != null) {
				champs.add(perso(etat, p, r, parChamp.getOrDefault(p.getCode(), List.of()),
						comptes.getOrDefault(p.getCode(), Compte.AUCUN), parValeur));
			}
		});
		return new Parametrage(type.chemin(), type.libelle(), etat.parametrage.getVersion(), etat.parametrage.getModifieLe(),
				etat.parametrage.getModifieParNom(), champs);
	}

	private static List<SynonymeVue> synonymes(List<ImportSynonyme> synonymes) {
		return synonymes.stream()
				.sorted(Comparator.comparing((ImportSynonyme s) -> s.getOrigine().ordinal())
						.thenComparing(ImportSynonyme::getTexte, String.CASE_INSENSITIVE_ORDER))
				.map(s -> new SynonymeVue(s.getId(), s.getTexte(), s.getFormeNormalisee(), s.getOrigine().name(),
						s.getOrigine().libelle(), s.isActif(), s.getOrigine() == OrigineSynonyme.RH))
				.toList();
	}

	private static ChampVue fixe(TypeImportParametrable type, ImportChampRegle r, List<ImportSynonyme> synonymes) {
		ChampImport c = type.champ(r.getChampCode()).orElseThrow();
		ExigenceChamp exigence = r.getExigence() == null ? c.exigenceDefaut() : r.getExigence();
		List<ExigenceVue> possibles = type.exigencesPossibles(c).stream()
				.map(e -> new ExigenceVue(e.name(), e.libelle())).toList();
		return new ChampVue(c.name(), c.libelle(), r.getEnTete(), c.libelle(), r.isDansModele(), c.retirableDuModele(),
				exigence.name(), exigence.libelle(), c.exigenceMinimale().name(), possibles,
				c.exigenceMinimale() != ExigenceChamp.FACULTATIF, c.verrouLibelle(), r.getOrdre(), synonymes(synonymes),
				false, true, null, null, null, null, null, null, null, null, null, null, null, null, null);
	}

	private static ChampVue perso(EtatParametrage etat, ChampPerso p, ImportChampRegle r, List<ImportSynonyme> synonymes,
			Compte compte, Map<String, Integer> parValeur) {
		ChampImport champ = new ChampPersoImport(p.getCode(), p.getLibelle());
		ExigenceChamp exigence = r.getExigence() == null ? ExigenceChamp.FACULTATIF : r.getExigence();
		List<ExigenceVue> possibles = etat.type.exigencesPossibles(champ).stream()
				.map(e -> new ExigenceVue(e.name(), e.libelle())).toList();
		List<ValeurListeVue> valeurs = null;
		if (p.getTypeValeur() == TypeValeurPerso.LISTE) {
			valeurs = etat.valeursDe(p).stream().map(v -> {
				int n = parValeur.getOrDefault(p.getCode() + "|" + v.getCode(), 0);
				return new ValeurListeVue(v.getCode(), v.getLibelle(), v.getOrdre(), v.isActif(), n, n == 0);
			}).toList();
		}
		String typeImport = etat.type.name();
		return new ChampVue(p.getCode(), p.getLibelle(), r.getEnTete(), p.getLibelle(), r.isDansModele(), true,
				exigence.name(), exigence.libelle(), ExigenceChamp.FACULTATIF.name(), possibles, false, null,
				p.isActif() ? r.getOrdre() : null, synonymes(synonymes), true, p.isActif(), p.getTypeValeur().name(),
				p.getTypeValeur().libelle(), reglesVue(p.regles()), reglesLibelle(p, etat.valeursDe(p)), valeurs, p.getFinalite(),
				p.isVisibleCollaborateur(), compte.total(), ChampPersoUtilisations.libelle(compte, typeImport),
				compte.total() == 0, compte.total() == 0, p.getCreeLe(), p.getCreeParNom());
	}

	public static ReglesVue reglesVue(ReglesPerso r) {
		return new ReglesVue(r.longueurMax(), r.decimales(), chaine(r.minimum()), chaine(r.maximum()),
				r.dateMin() == null ? null : r.dateMin().toString(), r.dateMax() == null ? null : r.dateMax().toString());
	}

	private static String chaine(BigDecimal n) {
		return n == null ? null : n.stripTrailingZeros().toPlainString();
	}

	/** Résumé lisible des règles (aucun code technique). */
	public static String reglesLibelle(ChampPerso p, List<ChampPersoValeurListe> valeurs) {
		ReglesPerso r = p.regles();
		return switch (p.getTypeValeur()) {
			case TEXTE -> r.longueurMax() == null ? "Texte libre" : "Jusqu'à " + r.longueurMax() + " caractères";
			case NOMBRE -> {
				String d = r.decimales() == null ? "décimales libres" : r.decimales() + (r.decimales() > 1 ? " décimales" : " décimale");
				String b = r.minimum() != null && r.maximum() != null ? ", de " + chaine(r.minimum()) + " à " + chaine(r.maximum())
						: r.minimum() != null ? ", au moins " + chaine(r.minimum())
						: r.maximum() != null ? ", au plus " + chaine(r.maximum()) : "";
				yield d + b;
			}
			case DATE -> {
				String a = r.dateMin() == null ? "" : "du " + r.dateMin().format(FormatJours.JOUR);
				String b = r.dateMax() == null ? "" : (a.isEmpty() ? "" : " ") + "jusqu'au " + r.dateMax().format(FormatJours.JOUR);
				yield a.isEmpty() && b.isEmpty() ? "Toute date" : a + b;
			}
			case LISTE -> {
				long n = valeurs.stream().filter(ChampPersoValeurListe::isActif).count();
				yield n + (n > 1 ? " valeurs" : " valeur");
			}
		};
	}
}
