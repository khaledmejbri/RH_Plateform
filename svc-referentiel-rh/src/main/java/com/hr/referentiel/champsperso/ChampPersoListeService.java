package com.hr.referentiel.champsperso;

import com.hr.referentiel.champsperso.ChampPersoDtos.ValeurRequest;
import com.hr.referentiel.importation.parametrage.EtatParametrage;
import com.hr.referentiel.importation.parametrage.ImportChampsJournal;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.Parametrage;
import com.hr.referentiel.importation.parametrage.ParametrageEcriture;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hr.referentiel.champsperso.ChampPersoDefinitionService.exiger;
import static com.hr.referentiel.champsperso.ChampPersoDefinitionService.exigerVersion;
import static com.hr.referentiel.champsperso.ChampPersoValidation.erreur;
import static com.hr.referentiel.importation.ReconnaissanceEnTetes.normaliserComplet;

/** Valeurs de liste d'un champ personnalisé, C5 et C6 (ADR partie 2, P2-5). Même gabarit d'écriture que C1 à C4. */
@Service
public class ChampPersoListeService {

	private final ParametrageEcriture ecriture;
	private final ChampPersoValeurListeRepository valeurRepository;
	private final ChampPersoUtilisations utilisations;
	private final Clock clock;

	public ChampPersoListeService(ParametrageEcriture ecriture, ChampPersoValeurListeRepository valeurRepository,
			ChampPersoUtilisations utilisations, Clock clock) {
		this.ecriture = ecriture;
		this.valeurRepository = valeurRepository;
		this.utilisations = utilisations;
		this.clock = clock;
	}

	/** C5a. */
	@Transactional
	public Parametrage ajouter(String chemin, String code, ValeurRequest req, AuteurParametre auteur) {
		EtatParametrage etat = ecriture.ouvrir(ecriture.resoudre(chemin));
		ChampPerso p = exiger(etat, code);
		exigerVersion(req == null ? null : req.version(), code);
		exigerListe(p, code);
		String libelle = ChampPersoValidation.libelleValeur(req.libelle(), code);
		List<ChampPersoValeurListe> valeurs = new ArrayList<>(etat.valeursDe(p));
		controlerDoublon(valeurs, libelle, null, code);
		if (valeurs.size() >= ChampPersoValidation.VALEURS_MAX) {
			throw erreur(422, "REGLE_INVALIDE", "Une liste comporte au plus " + ChampPersoValidation.VALEURS_MAX + " valeurs.", code);
		}
		ecriture.controlerVersion(etat, req.version());
		Set<String> pris = new HashSet<>();
		valeurs.forEach(v -> pris.add(v.getCode()));
		ChampPersoValeurListe v = new ChampPersoValeurListe(p.getId(), CodesChampPerso.valeur(libelle, pris), libelle, valeurs.size());
		valeurRepository.save(v);
		etat.valeursListe.computeIfAbsent(p.getId(), k -> new ArrayList<>()).add(v);
		return ecriture.terminer(etat, List.<String[]>of(new String[] {code, ImportChampsJournal.VALEUR_LISTE_AJOUTEE, null,
				libelle}), auteur, clock.instant());
	}

	/** C5b : libellé et/ou position (0..n-1). */
	@Transactional
	public Parametrage modifier(String chemin, String code, String codeValeur, ValeurRequest req, AuteurParametre auteur) {
		EtatParametrage etat = ecriture.ouvrir(ecriture.resoudre(chemin));
		ChampPerso p = exiger(etat, code);
		ChampPersoValeurListe v = valeur(etat, p, codeValeur);
		exigerVersion(req == null ? null : req.version(), code);
		List<ChampPersoValeurListe> valeurs = new ArrayList<>(etat.valeursDe(p));
		if (req.libelle() == null) {
			throw erreur(400, "CORPS_INVALIDE", "Le libellé est obligatoire.", code);
		}
		if (req.ordre() != null && (req.ordre() < 0 || req.ordre() >= valeurs.size())) {
			throw erreur(400, "CORPS_INVALIDE", "La position doit être comprise entre 0 et " + (valeurs.size() - 1) + ".", code);
		}
		String libelle = ChampPersoValidation.libelleValeur(req.libelle(), code);
		controlerDoublon(valeurs, libelle, v, code);
		ecriture.controlerVersion(etat, req.version());
		List<String[]> lignes = new ArrayList<>();
		if (!v.getLibelle().equals(libelle)) {
			lignes.add(new String[] {code, ImportChampsJournal.VALEUR_LISTE_MODIFIEE, v.getLibelle(), libelle});
			v.setLibelle(libelle);
		}
		if (req.ordre() != null && req.ordre() != v.getOrdre()) {
			lignes.add(new String[] {code, ImportChampsJournal.VALEUR_LISTE_MODIFIEE, libelle + " : position " + (v.getOrdre() + 1),
					libelle + " : position " + (req.ordre() + 1)});
			valeurs.remove(v);
			valeurs.add(req.ordre(), v);
			renumeroter(valeurs);
		}
		valeurRepository.saveAll(valeurs);
		return ecriture.terminer(etat, lignes, auteur, clock.instant());
	}

	/** C6a. */
	@Transactional
	public Parametrage basculer(String chemin, String code, String codeValeur, Long version, boolean activer, AuteurParametre auteur) {
		EtatParametrage etat = ecriture.ouvrir(ecriture.resoudre(chemin));
		ChampPerso p = exiger(etat, code);
		ChampPersoValeurListe v = valeur(etat, p, codeValeur);
		exigerVersion(version, code);
		if (!activer && v.isActif() && derniereActive(etat, p, v)) {
			throw erreur(422, "LISTE_SANS_VALEUR_ACTIVE", "Une liste doit garder au moins une valeur active.", code);
		}
		ecriture.controlerVersion(etat, version);
		if (v.isActif() == activer) {
			return ecriture.vue(etat);
		}
		v.setActif(activer);
		valeurRepository.save(v);
		return ecriture.terminer(etat, List.<String[]>of(new String[] {code,
				activer ? ImportChampsJournal.VALEUR_LISTE_REACTIVEE : ImportChampsJournal.VALEUR_LISTE_DESACTIVEE,
				activer ? "Désactivée" : v.getLibelle(), activer ? v.getLibelle() : "Désactivée"}), auteur, clock.instant());
	}

	/** C6b. */
	@Transactional
	public void supprimer(String chemin, String code, String codeValeur, Long version, AuteurParametre auteur) {
		EtatParametrage etat = ecriture.ouvrir(ecriture.resoudre(chemin));
		ChampPerso p = exiger(etat, code);
		ChampPersoValeurListe v = valeur(etat, p, codeValeur);
		exigerVersion(version, code);
		if (v.isActif() && derniereActive(etat, p, v)) {
			throw erreur(422, "LISTE_SANS_VALEUR_ACTIVE", "Une liste doit garder au moins une valeur active.", code);
		}
		int n = utilisations.parValeurListe(etat.type.name(), List.of(code)).getOrDefault(code + "|" + codeValeur, 0);
		if (n > 0) {
			throw new ReferentielMetierException("PARAMETRE_UTILISE", "Cette valeur est utilisée par " + n
					+ (n > 1 ? " enregistrements." : " enregistrement."), 409, Map.of("utilisations", n, "champ", code));
		}
		ecriture.controlerVersion(etat, version);
		List<ChampPersoValeurListe> valeurs = new ArrayList<>(etat.valeursDe(p));
		valeurs.remove(v);
		valeurRepository.delete(v);
		renumeroter(valeurs);
		valeurRepository.saveAll(valeurs);
		etat.valeursListe.put(p.getId(), valeurs);
		ecriture.terminer(etat, List.<String[]>of(new String[] {code, ImportChampsJournal.VALEUR_LISTE_SUPPRIMEE, v.getLibelle(),
				null}), auteur, clock.instant());
	}

	private static void renumeroter(List<ChampPersoValeurListe> valeurs) {
		for (int i = 0; i < valeurs.size(); i++) {
			valeurs.get(i).setOrdre(i);
		}
	}

	private static boolean derniereActive(EtatParametrage etat, ChampPerso p, ChampPersoValeurListe v) {
		return etat.valeursDe(p).stream().noneMatch(x -> x != v && x.isActif());
	}

	private static ChampPersoValeurListe valeur(EtatParametrage etat, ChampPerso p, String codeValeur) {
		return etat.valeursDe(p).stream().filter(v -> v.getCode().equals(codeValeur)).findFirst()
				.orElseThrow(() -> erreur(404, "VALEUR_INTROUVABLE", "Valeur de liste introuvable : " + codeValeur + ".", null));
	}

	private static void exigerListe(ChampPerso p, String code) {
		if (p.getTypeValeur() != TypeValeurPerso.LISTE) {
			throw erreur(400, "CORPS_INVALIDE", "Ce champ n'est pas une liste de valeurs.", code);
		}
	}

	private static void controlerDoublon(List<ChampPersoValeurListe> valeurs, String libelle, ChampPersoValeurListe ignore, String code) {
		String forme = normaliserComplet(libelle);
		for (ChampPersoValeurListe v : valeurs) {
			if (v != ignore && normaliserComplet(v.getLibelle()).equals(forme)) {
				throw erreur(400, "LIBELLE_EN_DOUBLE", "La valeur « " + libelle + " » existe déjà dans cette liste.", code);
			}
		}
	}
}
