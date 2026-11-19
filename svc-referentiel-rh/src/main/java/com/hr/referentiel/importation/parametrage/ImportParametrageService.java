package com.hr.referentiel.importation.parametrage;

import com.hr.referentiel.champsperso.ChampPerso;
import com.hr.referentiel.importation.ChampImport;
import com.hr.referentiel.importation.ReconnaissanceEnTetes;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.ChampRequest;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.EnregistrerRequest;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.PageJournalImport;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.Parametrage;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.SynonymeRequest;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.hr.referentiel.importation.parametrage.ImportParametrageValidation.SYNONYMES_MAX;
import static com.hr.referentiel.importation.parametrage.ImportParametrageValidation.erreur;

/**
 * Paramétrage des champs de l'import Excel (D-I14 à D-I24, ADR-IMPORT-CHAMPS § 5). Toute écriture suit le même gabarit,
 * dans une transaction : verrou {@code FOR UPDATE} sur la ligne du type, contrôles dans l'ordre 404, 400, 422, puis 409 sur
 * la version (en dernier), application, version + 1 (seulement si quelque chose a changé), journal, invalidation du cache
 * après commit. Toute exception annule la transaction : rien n'est écrit.
 */
@Service
public class ImportParametrageService {

	private final ParametrageEcriture ecriture;
	private final ImportSynonymeRepository synonymeRepository;
	private final ImportChampsJournal journal;
	private final Clock clock;

	public ImportParametrageService(ParametrageEcriture ecriture, ImportSynonymeRepository synonymeRepository,
			ImportChampsJournal journal, Clock clock) {
		this.ecriture = ecriture;
		this.synonymeRepository = synonymeRepository;
		this.journal = journal;
		this.clock = clock;
	}

	// ─── Lecture (P1, P7) ──────────────────────────────────────────────────────

	/** P1 : lecture directe en base (version exacte), sans passer par le cache. */
	@Transactional(readOnly = true)
	public Parametrage lire(String chemin) {
		return ecriture.vue(ecriture.lire(resoudre(chemin)));
	}

	/** P7. */
	@Transactional(readOnly = true)
	public PageJournalImport lireJournal(String chemin, int page, int taille) {
		TypeImportParametrable type = resoudre(chemin);
		if (taille < 1 || taille > 100 || page < 0) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "La taille de page doit être comprise entre 1 et 100.");
		}
		return journal.lire(type, page, taille);
	}

	// ─── P2 : enregistrer le tableau ───────────────────────────────────────────

	@Transactional
	public Parametrage enregistrer(String chemin, EnregistrerRequest req, AuteurParametre auteur) {
		TypeImportParametrable type = resoudre(chemin);
		EtatParametrage etat = ouvrir(type);
		List<ChampRequest> tous = controlerCorps(etat, req);
		List<ChampRequest> champs = tous.stream().filter(c -> etat.actif(c.code())).toList();
		Map<String, ExigenceChamp> exigences = controlerExigences(etat, champs);
		Map<String, String> enTetes = controlerEnTetes(etat, champs);
		controlerNoms(etat, champs, enTetes);
		if (champs.size() != tous.size()) {
			throw erreur(422, "CHAMP_INACTIF", "Un champ désactivé ne peut pas figurer dans le tableau : réactivez-le d'abord.",
					tous.stream().filter(c -> !etat.actif(c.code())).findFirst().orElseThrow().code());
		}
		controlerVerrous(etat, champs, exigences);
		List<AjoutAncienEnTete> anciens = prevoirAnciensEnTetes(etat, champs, enTetes);
		controlerVersion(etat, req.version());

		Instant maintenant = clock.instant();
		List<String[]> lignes = new ArrayList<>(); // {champ, attribut, avant, apres}
		boolean ordreChange = false;
		for (int position = 0; position < champs.size(); position++) {
			ChampRequest c = champs.get(position);
			ImportChampRegle r = etat.regle(c.code());
			String enTete = enTetes.get(c.code());
			ExigenceChamp exigence = exigences.get(c.code());
			if (!r.getEnTete().equals(enTete)) {
				lignes.add(new String[] {c.code(), ImportChampsJournal.EN_TETE, r.getEnTete(), enTete});
				r.setEnTete(enTete);
			}
			if (r.isDansModele() != c.dansModele()) {
				lignes.add(new String[] {c.code(), ImportChampsJournal.DANS_MODELE, oui(r.isDansModele()), oui(c.dansModele())});
				r.setDansModele(c.dansModele());
			}
			if (r.getExigence() != exigence) {
				lignes.add(new String[] {c.code(), ImportChampsJournal.EXIGENCE, r.getExigence().libelle(), exigence.libelle()});
				r.setExigence(exigence);
			}
			if (r.getOrdre() != position) {
				ordreChange = true;
			}
		}
		if (ordreChange) {
			lignes.add(0, new String[] {null, ImportChampsJournal.ORDRE, listeEnTetes(etat, enTetes, ancienOrdre(etat)),
					listeEnTetes(etat, enTetes, champs.stream().map(ChampRequest::code).toList())});
			for (int position = 0; position < champs.size(); position++) {
				etat.regle(champs.get(position).code()).setOrdre(position);
			}
		}
		for (AjoutAncienEnTete a : anciens) {
			if (a.aReactiver() != null) {
				a.aReactiver().setActif(true);
				lignes.add(new String[] {a.champ(), ImportChampsJournal.SYNONYME_REACTIVE, "Désactivé", a.texte()});
			} else {
				ImportSynonyme s = new ImportSynonyme(type.name(), a.champ(), a.texte(), a.forme(), OrigineSynonyme.RH, true,
						maintenant, auteur.nom());
				synonymeRepository.save(s);
				etat.synonymes.add(s);
				lignes.add(new String[] {a.champ(), ImportChampsJournal.SYNONYME_AJOUTE, null, a.texte()});
			}
		}
		return terminer(etat, lignes, auteur, maintenant);
	}

	private record AjoutAncienEnTete(String champ, String texte, String forme, ImportSynonyme aReactiver) {
	}

	/** Corps : 400 {@code CORPS_INVALIDE} ({@code VERSION_OBLIGATOIRE} si seule la version manque), puis {@code ORDRE_INCOMPLET}. */
	private List<ChampRequest> controlerCorps(EtatParametrage etat, EnregistrerRequest req) {
		if (req == null || req.champs() == null) {
			throw erreur(400, "CORPS_INVALIDE", "Le tableau des champs est obligatoire.", null);
		}
		for (ChampRequest c : req.champs()) {
			if (c == null || c.code() == null || c.dansModele() == null || c.exigence() == null) {
				throw erreur(400, "CORPS_INVALIDE", "Chaque champ doit porter son code, sa présence dans le modèle et son exigence.",
						c == null ? null : c.code());
			}
			if (!etat.connu(c.code())) {
				throw erreur(400, "CORPS_INVALIDE", "Champ inconnu : " + c.code() + ".", c.code());
			}
		}
		if (req.version() == null) {
			throw erreur(400, "VERSION_OBLIGATOIRE", "La version du paramétrage est obligatoire.", null);
		}
		Set<String> vus = new HashSet<>();
		for (ChampRequest c : req.champs()) {
			if (!vus.add(c.code())) {
				throw erreur(400, "ORDRE_INCOMPLET", "Le champ « " + etat.libelle(c.code()) + " » est répété.", c.code());
			}
		}
		long presents = req.champs().stream().filter(c -> etat.actif(c.code())).count();
		if (presents != etat.nombreAttendu()) {
			throw erreur(400, "ORDRE_INCOMPLET", "Tous les champs doivent figurer dans le tableau (" + etat.nombreAttendu() + " attendus).",
					null);
		}
		return req.champs();
	}

	private Map<String, ExigenceChamp> controlerExigences(EtatParametrage etat, List<ChampRequest> champs) {
		Map<String, ExigenceChamp> r = new HashMap<>();
		for (ChampRequest c : champs) {
			ExigenceChamp e = ExigenceChamp.lire(c.exigence());
			if (e == null || !etat.type.autorise(e)) {
				throw erreur(400, "EXIGENCE_INVALIDE", "Exigence invalide pour le champ « " + etat.libelle(c.code()) + " ».",
						c.code());
			}
			r.put(c.code(), e);
		}
		return r;
	}

	private Map<String, String> controlerEnTetes(EtatParametrage etat, List<ChampRequest> champs) {
		Map<String, String> r = new HashMap<>();
		for (ChampRequest c : champs) {
			r.put(c.code(), ImportParametrageValidation.enTete(c.enTete(), c.code(), etat.estPerso(c.code())));
		}
		return r;
	}

	/** 400 {@code LIBELLE_EN_DOUBLE} : deux en-têtes de même forme, ou forme d'un synonyme actif d'un autre champ. */
	private void controlerNoms(EtatParametrage etat, List<ChampRequest> champs, Map<String, String> enTetes) {
		Map<String, String> proprietaires = new HashMap<>();
		for (ChampRequest c : champs) {
			String texte = enTetes.get(c.code());
			String forme = etat.forme(c.code(), texte);
			String autre = proprietaires.putIfAbsent(forme, c.code());
			if (autre != null) {
				throw doublon(etat, texte, autre, c.code());
			}
		}
		for (ChampRequest c : champs) {
			String texte = enTetes.get(c.code());
			String forme = etat.forme(c.code(), texte);
			for (ImportSynonyme s : etat.synonymes) {
				if (s.isActif() && etat.actif(s.getChampCode()) && s.getFormeNormalisee().equals(forme) && !s.getChampCode().equals(c.code())) {
					throw doublon(etat, texte, s.getChampCode(), c.code());
				}
			}
		}
	}

	private ReferentielMetierException doublon(EtatParametrage etat, String texte, String proprietaire, String champVise) {
		return erreur(400, "LIBELLE_EN_DOUBLE", "« " + texte + " » est déjà reconnu pour le champ " + etat.libelle(proprietaire) + ".",
				champVise);
	}

	/** 422 {@code CHAMP_VERROUILLE} puis {@code CHAMP_OBLIGATOIRE_HORS_MODELE}. */
	private void controlerVerrous(EtatParametrage etat, List<ChampRequest> champs, Map<String, ExigenceChamp> exigences) {
		for (ChampRequest c : champs) {
			ChampImport champ = etat.type.champ(c.code()).orElse(null);
			if (champ == null) {
				continue; // champ personnalisé : aucun verrou
			}
			if (!exigences.get(c.code()).auMoins(champ.exigenceMinimale())) {
				throw erreur(422, "CHAMP_VERROUILLE", "Le champ « " + champ.libelle() + " » doit rester au moins « "
						+ champ.exigenceMinimale().libelle() + " ».", c.code());
			}
			if (!c.dansModele() && !champ.retirableDuModele()) {
				throw erreur(422, "CHAMP_VERROUILLE",
						"Le champ « " + champ.libelle() + " » est obligatoire : il ne peut pas être retiré du modèle.", c.code());
			}
		}
		for (ChampRequest c : champs) {
			if (!c.dansModele() && exigences.get(c.code()) != ExigenceChamp.FACULTATIF) {
				throw erreur(422, "CHAMP_OBLIGATOIRE_HORS_MODELE", "Le champ « " + etat.libelle(c.code())
						+ " » est obligatoire : il doit figurer dans le modèle.", c.code());
			}
		}
	}

	/**
	 * Ancien en-tête → synonyme (D-I20) : conservé comme synonyme RH, ou réactivé s'il existe inactif. Omis si sa forme est
	 * celle du nouvel en-tête, déjà un synonyme actif du champ, ou reconnue par un autre champ (échange d'en-têtes).
	 * 422 {@code SYNONYMES_MAX} si l'ajout dépasse 30.
	 */
	private List<AjoutAncienEnTete> prevoirAnciensEnTetes(EtatParametrage etat, List<ChampRequest> champs, Map<String, String> enTetes) {
		Map<String, String> formesNouvelles = new HashMap<>();
		champs.forEach(c -> formesNouvelles.put(etat.forme(c.code(), enTetes.get(c.code())), c.code()));
		List<AjoutAncienEnTete> r = new ArrayList<>();
		for (ChampRequest c : champs) {
			ImportChampRegle regle = etat.regle(c.code());
			String ancien = regle.getEnTete();
			String nouveau = enTetes.get(c.code());
			if (ancien.equals(nouveau)) {
				continue;
			}
			String fAncien = etat.forme(c.code(), ancien);
			if (fAncien.isEmpty() || fAncien.equals(etat.forme(c.code(), nouveau))) {
				continue;
			}
			String autreEnTete = formesNouvelles.get(fAncien);
			if (autreEnTete != null && !autreEnTete.equals(c.code())) {
				continue;
			}
			boolean autreSynonyme = etat.synonymes.stream().anyMatch(s -> s.isActif() && etat.actif(s.getChampCode())
					&& s.getFormeNormalisee().equals(fAncien) && !s.getChampCode().equals(c.code()));
			if (autreSynonyme) {
				continue;
			}
			ImportSynonyme existant = etat.synonymesDe(c.code()).stream()
					.filter(s -> s.getFormeNormalisee().equals(fAncien)).findFirst().orElse(null);
			if (existant != null && existant.isActif()) {
				continue;
			}
			if (existant == null && etat.synonymesDe(c.code()).size() + 1 > SYNONYMES_MAX) {
				throw erreur(422, "SYNONYMES_MAX", "Le champ « " + etat.libelle(c.code()) + " » a déjà " + SYNONYMES_MAX
						+ " synonymes : supprimez-en un avant de renommer l'en-tête.", c.code());
			}
			r.add(new AjoutAncienEnTete(c.code(), ancien, fAncien, existant));
		}
		return r;
	}

	private List<String> ancienOrdre(EtatParametrage etat) {
		return etat.regles.stream().filter(r -> etat.actif(r.getChampCode()))
				.sorted(Comparator.comparingInt(ImportChampRegle::getOrdre)).map(ImportChampRegle::getChampCode).toList();
	}

	private String listeEnTetes(EtatParametrage etat, Map<String, String> enTetes, List<String> codes) {
		return String.join(", ", codes.stream().map(c -> enTetes.getOrDefault(c, etat.regle(c).getEnTete())).toList());
	}

	// ─── P3 : ajouter un synonyme ──────────────────────────────────────────────

	@Transactional
	public Parametrage ajouterSynonyme(String chemin, String code, SynonymeRequest req, AuteurParametre auteur) {
		TypeImportParametrable type = resoudre(chemin);
		EtatParametrage etat = ouvrir(type);
		if (!etat.connu(code) || etat.regle(code) == null) {
			throw erreur(404, "CHAMP_INCONNU", "Champ inconnu : " + code + ".", null);
		}
		if (req == null || req.texte() == null) {
			throw erreur(400, "CORPS_INVALIDE", "Le texte du synonyme est obligatoire.", code);
		}
		if (req.version() == null) {
			throw erreur(400, "VERSION_OBLIGATOIRE", "La version du paramétrage est obligatoire.", code);
		}
		String texte = ImportParametrageValidation.synonyme(req.texte(), code, etat.estPerso(code));
		String forme = etat.forme(code, texte);
		ImportSynonyme memeChamp = etat.synonymesDe(code).stream().filter(s -> s.getFormeNormalisee().equals(forme)).findFirst()
				.orElse(null);
		if (memeChamp != null && !memeChamp.isActif()) {
			throw erreur(400, "SYNONYME_EN_DOUBLE", "« " + texte + " » est déjà présent pour ce champ (désactivé) : réactivez-le.",
					code);
		}
		String proprietaire = etat.proprietaire(forme, null);
		if (proprietaire != null) {
			throw erreur(400, "SYNONYME_EN_DOUBLE", "« " + texte + " » est déjà reconnu pour le champ " + etat.libelle(proprietaire)
					+ ".", code);
		}
		if (etat.synonymesDe(code).size() + 1 > SYNONYMES_MAX) {
			throw erreur(422, "SYNONYMES_MAX", "Un champ ne peut pas avoir plus de " + SYNONYMES_MAX + " synonymes.", code);
		}
		controlerVersion(etat, req.version());
		Instant maintenant = clock.instant();
		ImportSynonyme s = new ImportSynonyme(type.name(), code, texte, forme, OrigineSynonyme.RH, true, maintenant, auteur.nom());
		synonymeRepository.save(s);
		etat.synonymes.add(s);
		return terminer(etat, List.<String[]>of(new String[] {code, ImportChampsJournal.SYNONYME_AJOUTE, null, texte}), auteur,
				maintenant);
	}

	// ─── P4 : désactiver / réactiver ───────────────────────────────────────────

	@Transactional
	public Parametrage desactiver(String chemin, UUID id, Long version, AuteurParametre auteur) {
		return basculer(chemin, id, version, auteur, false);
	}

	@Transactional
	public Parametrage reactiver(String chemin, UUID id, Long version, AuteurParametre auteur) {
		return basculer(chemin, id, version, auteur, true);
	}

	private Parametrage basculer(String chemin, UUID id, Long version, AuteurParametre auteur, boolean activer) {
		TypeImportParametrable type = resoudre(chemin);
		EtatParametrage etat = ouvrir(type);
		ImportSynonyme s = synonyme(etat, id);
		if (version == null) {
			throw erreur(400, "VERSION_OBLIGATOIRE", "La version du paramétrage est obligatoire.", s.getChampCode());
		}
		if (s.isActif() == activer) {
			controlerVersion(etat, version);
			return vue(etat); // rien à changer : ni version ni journal
		}
		if (activer) {
			String proprietaire = etat.proprietaire(s.getFormeNormalisee(), s.getId());
			if (proprietaire != null && !proprietaire.equals(s.getChampCode())) {
				throw erreur(400, "SYNONYME_EN_DOUBLE", "« " + s.getTexte() + " » est déjà reconnu pour le champ "
						+ etat.libelle(proprietaire) + ".", s.getChampCode());
			}
		}
		controlerVersion(etat, version);
		s.setActif(activer);
		String[] ligne = activer
				? new String[] {s.getChampCode(), ImportChampsJournal.SYNONYME_REACTIVE, "Désactivé", s.getTexte()}
				: new String[] {s.getChampCode(), ImportChampsJournal.SYNONYME_DESACTIVE, s.getTexte(), "Désactivé"};
		return terminer(etat, List.<String[]>of(ligne), auteur, clock.instant());
	}

	// ─── P5 : supprimer ────────────────────────────────────────────────────────

	/** 204 : l'écran relit P1 pour obtenir la nouvelle version. */
	@Transactional
	public void supprimer(String chemin, UUID id, Long version, AuteurParametre auteur) {
		TypeImportParametrable type = resoudre(chemin);
		EtatParametrage etat = ouvrir(type);
		ImportSynonyme s = synonyme(etat, id);
		if (version == null) {
			throw erreur(400, "VERSION_OBLIGATOIRE", "La version du paramétrage est obligatoire.", s.getChampCode());
		}
		if (s.getOrigine() == OrigineSynonyme.DEFAUT) {
			throw erreur(409, "PARAMETRE_SYSTEME",
					"Un synonyme par défaut ne peut pas être supprimé : désactivez-le.", s.getChampCode());
		}
		controlerVersion(etat, version);
		synonymeRepository.delete(s);
		etat.synonymes.remove(s);
		terminer(etat, List.<String[]>of(new String[] {s.getChampCode(), ImportChampsJournal.SYNONYME_SUPPRIME, s.getTexte(), null}),
				auteur, clock.instant());
	}

	// ─── P6 : réinitialiser ────────────────────────────────────────────────────

	@Transactional
	public Parametrage reinitialiser(String chemin, Long version, AuteurParametre auteur) {
		TypeImportParametrable type = resoudre(chemin);
		EtatParametrage etat = ouvrir(type);
		if (version == null) {
			throw erreur(400, "VERSION_OBLIGATOIRE", "La version du paramétrage est obligatoire.", null);
		}
		controlerVersion(etat, version);
		Instant maintenant = clock.instant();
		List<ImportSynonyme> supprimes = etat.synonymes.stream().filter(s -> s.getOrigine() == OrigineSynonyme.RH).toList();
		synonymeRepository.deleteAll(supprimes);
		synonymeRepository.flush();
		etat.synonymes.removeAll(supprimes);
		// en-têtes des champs personnalisés remis à zéro d'abord : le contrôle de conflit des synonymes par défaut les lit
		for (ChampPerso p : etat.persos) {
			ImportChampRegle r = etat.regle(p.getCode());
			if (r != null) {
				r.setEnTete(p.getLibelle());
			}
		}
		int position = 0;
		List<String[]> lignes = new ArrayList<>();
		lignes.add(null); // emplacement de la ligne « Réinitialisation », renseignée à la fin
		for (ChampImport c : type.champs()) {
			ImportChampRegle r = etat.regle(c.name());
			r.setEnTete(c.libelle());
			r.setDansModele(true);
			r.setExigence(c.exigenceDefaut());
			r.setOrdre(position++);
			Set<String> presentes = new HashSet<>();
			for (ImportSynonyme s : etat.synonymesDe(c.name())) {
				// comme ChampPersoDefinitionService.reactiver : jamais de forme reconnue pour deux champs
				if (enConflitAvecPerso(etat, c.name(), s.getFormeNormalisee(), s.getId())) {
					if (s.isActif()) {
						s.setActif(false);
						lignes.add(new String[] {c.name(), ImportChampsJournal.SYNONYME_DESACTIVE, s.getTexte(), "Désactivé"});
					}
				} else {
					s.setActif(true);
				}
				presentes.add(s.getFormeNormalisee());
			}
			for (String texte : c.synonymes()) {
				String forme = ReconnaissanceEnTetes.normaliser(texte);
				if (!forme.isEmpty() && presentes.add(forme)) {
					ImportSynonyme s = new ImportSynonyme(type.name(), c.name(), texte, forme, OrigineSynonyme.DEFAUT,
							!enConflitAvecPerso(etat, c.name(), forme, null), maintenant, null);
					synonymeRepository.save(s);
					etat.synonymes.add(s);
				}
			}
		}
		// champs personnalisés : actifs après les champs fixes (par date de création), inactifs hors modèle ; définitions
		// et valeurs jamais touchées
		List<ChampPerso> persos = etat.persos.stream().sorted(Comparator.comparing(ChampPerso::getCreeLe)).toList();
		for (ChampPerso p : persos) {
			ImportChampRegle r = etat.regle(p.getCode());
			if (r == null) {
				continue;
			}
			r.setExigence(ExigenceChamp.FACULTATIF);
			r.setDansModele(p.isActif());
			r.setOrdre(position++);
		}
		String avant = supprimes.isEmpty() ? "Aucun synonyme ajouté par la RH" : supprimes.size()
				+ (supprimes.size() > 1 ? " synonymes ajoutés par la RH supprimés" : " synonyme ajouté par la RH supprimé");
		lignes.set(0, new String[] {null, ImportChampsJournal.REINITIALISATION, avant, "Valeurs par défaut"});
		return terminer(etat, lignes, auteur, maintenant);
	}

	/** Vrai si la forme est déjà reconnue (en-tête ou synonyme actif) pour un champ PERSONNALISÉ actif autre que {@code champ}. */
	private static boolean enConflitAvecPerso(EtatParametrage etat, String champ, String forme, UUID synonymeIgnore) {
		String autre = etat.proprietaire(forme, synonymeIgnore);
		return autre != null && !autre.equals(champ) && etat.perso(autre) != null;
	}

	// ─── Communs ───────────────────────────────────────────────────────────────

	private TypeImportParametrable resoudre(String chemin) {
		return ecriture.resoudre(chemin);
	}

	private EtatParametrage ouvrir(TypeImportParametrable type) {
		return ecriture.ouvrir(type);
	}

	private ImportSynonyme synonyme(EtatParametrage etat, UUID id) {
		return etat.synonymes.stream().filter(s -> s.getId().equals(id)).findFirst()
				.orElseThrow(() -> erreur(404, "SYNONYME_INTROUVABLE", "Synonyme introuvable.", null));
	}

	private void controlerVersion(EtatParametrage etat, Long recue) {
		ecriture.controlerVersion(etat, recue);
	}

	private Parametrage terminer(EtatParametrage etat, List<String[]> lignes, AuteurParametre auteur, Instant maintenant) {
		return ecriture.terminer(etat, lignes, auteur, maintenant);
	}

	private Parametrage vue(EtatParametrage etat) {
		return ecriture.vue(etat);
	}

	private static String oui(boolean v) {
		return v ? "Oui" : "Non";
	}
}
