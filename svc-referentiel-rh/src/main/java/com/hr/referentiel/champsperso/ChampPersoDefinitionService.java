package com.hr.referentiel.champsperso;

import com.hr.referentiel.champsperso.ChampPersoDtos.CreerChampPerso;
import com.hr.referentiel.champsperso.ChampPersoDtos.ModifierChampPerso;
import com.hr.referentiel.champsperso.ChampPersoUtilisations.Compte;
import com.hr.referentiel.importation.parametrage.EtatParametrage;
import com.hr.referentiel.importation.parametrage.ExigenceChamp;
import com.hr.referentiel.importation.parametrage.ImportChampRegle;
import com.hr.referentiel.importation.parametrage.ImportChampRegleRepository;
import com.hr.referentiel.importation.parametrage.ImportChampsJournal;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.Parametrage;
import com.hr.referentiel.importation.parametrage.ImportParametrageVues;
import com.hr.referentiel.importation.parametrage.ImportSynonymeRepository;
import com.hr.referentiel.importation.parametrage.ParametrageEcriture;
import com.hr.referentiel.importation.parametrage.TypeImportParametrable;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hr.referentiel.champsperso.ChampPersoValidation.erreur;
import static com.hr.referentiel.importation.ReconnaissanceEnTetes.normaliserComplet;

/**
 * Définitions des champs personnalisés, C1 à C4 (ADR partie 2, P2-5). Même gabarit que le paramétrage d'import
 * ({@link ParametrageEcriture}) : verrou {@code FOR UPDATE} du type, contrôles dans l'ordre 404, 400, 422, puis 409 sur
 * la version, version + 1 d'exactement 1, journal en clair. Auteur toujours tiré du JWT par l'appelant.
 */
@Service
public class ChampPersoDefinitionService {

	private final ParametrageEcriture ecriture;
	private final ChampPersoRepository persoRepository;
	private final ChampPersoValeurListeRepository valeurRepository;
	private final ImportChampRegleRepository regleRepository;
	private final ImportSynonymeRepository synonymeRepository;
	private final ImportChampsJournal journal;
	private final ChampPersoUtilisations utilisations;
	private final Clock clock;

	public ChampPersoDefinitionService(ParametrageEcriture ecriture, ChampPersoRepository persoRepository,
			ChampPersoValeurListeRepository valeurRepository, ImportChampRegleRepository regleRepository,
			ImportSynonymeRepository synonymeRepository, ImportChampsJournal journal, ChampPersoUtilisations utilisations,
			Clock clock) {
		this.ecriture = ecriture;
		this.persoRepository = persoRepository;
		this.valeurRepository = valeurRepository;
		this.regleRepository = regleRepository;
		this.synonymeRepository = synonymeRepository;
		this.journal = journal;
		this.utilisations = utilisations;
		this.clock = clock;
	}

	// ─── C1 : créer ────────────────────────────────────────────────────────────

	@Transactional
	public Parametrage creer(String chemin, CreerChampPerso req, AuteurParametre auteur) {
		TypeImportParametrable type = ecriture.resoudre(chemin);
		EtatParametrage etat = ecriture.ouvrir(type);
		if (req == null || req.version() == null) {
			throw erreur(400, "VERSION_OBLIGATOIRE", "La version du paramétrage est obligatoire.", null);
		}
		TypeValeurPerso typeValeur = ChampPersoValidation.type(req.typeValeur(), null);
		String finalite = ChampPersoValidation.finalite(req.finalite(), null);
		if (req.libelle() == null || req.libelle().isBlank()) {
			throw erreur(400, "CORPS_INVALIDE", "Le libellé est obligatoire.", null);
		}
		if (!Boolean.TRUE.equals(req.confirmation())) {
			throw erreur(400, "CONFIRMATION_OBLIGATOIRE",
					"Confirmez que ce champ ne contient aucune donnée sensible.", null);
		}
		String libelle = ChampPersoValidation.libelle(req.libelle(), null);
		ReglesPerso regles = ChampPersoValidation.regles(typeValeur, req.regles(), null);
		if (typeValeur != TypeValeurPerso.LISTE && req.valeursListe() != null && !req.valeursListe().isEmpty()) {
			throw erreur(400, "REGLE_INVALIDE", "Les valeurs de liste n'existent que pour le type LISTE.", null);
		}
		List<String> valeurs = typeValeur == TypeValeurPerso.LISTE ? ChampPersoValidation.valeursListe(req.valeursListe(), null)
				: List.of();
		controlerLibelle(etat, libelle, null);
		if (etat.persos.size() >= ChampPersoValidation.CHAMPS_MAX) {
			throw erreur(422, "CHAMPS_PERSO_MAX", "Un type d'import accepte au plus " + ChampPersoValidation.CHAMPS_MAX
					+ " champs personnalisés.", null);
		}
		ecriture.controlerVersion(etat, req.version());

		Instant maintenant = clock.instant();
		Set<String> pris = new HashSet<>(journal.codesCites(type));
		etat.persos.forEach(p -> pris.add(p.getCode()));
		boolean visible = Boolean.TRUE.equals(req.visibleCollaborateur());
		ChampPerso p = new ChampPerso(type.name(), CodesChampPerso.champ(libelle, pris), libelle, finalite, typeValeur, visible,
				maintenant, auteur.nom());
		p.regler(regles);
		persoRepository.save(p);
		etat.persos.add(p);
		int ordre = etat.regles.stream().mapToInt(ImportChampRegle::getOrdre).max().orElse(-1) + 1;
		ImportChampRegle regle = new ImportChampRegle(type.name(), p.getCode(), libelle, true, ExigenceChamp.FACULTATIF, ordre);
		regleRepository.save(regle);
		etat.regles.add(regle);
		List<ChampPersoValeurListe> creees = creerValeurs(p, valeurs);
		etat.valeursListe.put(p.getId(), creees);
		String apres = typeValeur.libelle() + " ; finalité : " + finalite + " ; visible par le collaborateur : "
				+ (visible ? "Oui" : "Non") + " ; Confirmation : aucune donnée sensible";
		return ecriture.terminer(etat, List.<String[]>of(new String[] {p.getCode(), ImportChampsJournal.CHAMP_CREE, null, apres,
				libelle}), auteur, maintenant);
	}

	private List<ChampPersoValeurListe> creerValeurs(ChampPerso p, List<String> libelles) {
		Set<String> codes = new HashSet<>();
		List<ChampPersoValeurListe> creees = new ArrayList<>();
		for (String l : libelles) {
			ChampPersoValeurListe v = new ChampPersoValeurListe(p.getId(), CodesChampPerso.valeur(l, codes), l, creees.size());
			codes.add(v.getCode());
			creees.add(v);
		}
		valeurRepository.saveAll(creees);
		return creees;
	}

	// ─── C2 : modifier ─────────────────────────────────────────────────────────

	@Transactional
	public Parametrage modifier(String chemin, String code, ModifierChampPerso req, AuteurParametre auteur) {
		TypeImportParametrable type = ecriture.resoudre(chemin);
		EtatParametrage etat = ecriture.ouvrir(type);
		ChampPerso p = exiger(etat, code);
		if (req == null || req.version() == null) {
			throw erreur(400, "VERSION_OBLIGATOIRE", "La version du paramétrage est obligatoire.", code);
		}
		TypeValeurPerso typeValeur = ChampPersoValidation.type(req.typeValeur(), code);
		String finalite = ChampPersoValidation.finalite(req.finalite(), code);
		if (req.libelle() == null || req.libelle().isBlank() || req.visibleCollaborateur() == null) {
			throw erreur(400, "CORPS_INVALIDE", "Le libellé et la visibilité sont obligatoires.", code);
		}
		String libelle = ChampPersoValidation.libelle(req.libelle(), code);
		ReglesPerso regles = ChampPersoValidation.regles(typeValeur, req.regles(), code);
		boolean devientListe = typeValeur == TypeValeurPerso.LISTE && p.getTypeValeur() != TypeValeurPerso.LISTE;
		if (!devientListe && req.valeursListe() != null && !req.valeursListe().isEmpty()) {
			throw erreur(400, "CORPS_INVALIDE", "Les valeurs de liste ne s'envoient que si le type devient LISTE.", code);
		}
		List<String> valeurs = devientListe ? ChampPersoValidation.valeursListe(req.valeursListe(), code) : List.of();
		controlerLibelle(etat, libelle, code);
		boolean typeChange = typeValeur != p.getTypeValeur();
		Compte compte = utilisations.duChamp(type.name(), code);
		if (typeChange && compte.total() > 0) {
			throw new ReferentielMetierException("PARAMETRE_UTILISE", "Le type ne peut plus changer : "
					+ ChampPersoUtilisations.libelle(compte, type.name()) + ".", 409,
					Map.of("utilisations", compte.total(), "champ", code));
		}
		ecriture.controlerVersion(etat, req.version());

		List<String[]> lignes = new ArrayList<>();
		String avantRegles = ImportParametrageVues.reglesLibelle(p, etat.valeursDe(p));
		if (!p.getLibelle().equals(libelle)) {
			lignes.add(new String[] {code, ImportChampsJournal.LIBELLE, p.getLibelle(), libelle, libelle});
			p.setLibelle(libelle);
		}
		if (!p.getFinalite().equals(finalite)) {
			lignes.add(new String[] {code, ImportChampsJournal.FINALITE, p.getFinalite(), finalite});
			p.setFinalite(finalite);
		}
		if (typeChange) {
			lignes.add(new String[] {code, ImportChampsJournal.TYPE_VALEUR, p.getTypeValeur().libelle(), typeValeur.libelle()});
			if (p.getTypeValeur() == TypeValeurPerso.LISTE) {
				for (ChampPersoValeurListe v : etat.valeursDe(p)) {
					lignes.add(new String[] {code, ImportChampsJournal.VALEUR_LISTE_SUPPRIMEE, v.getLibelle(), null});
				}
				valeurRepository.deleteAll(etat.valeursDe(p));
				etat.valeursListe.remove(p.getId());
			}
			p.setTypeValeur(typeValeur);
		}
		boolean reglesChangent = !p.regles().equals(regles);
		p.regler(regles);
		if (devientListe) {
			etat.valeursListe.put(p.getId(), creerValeurs(p, valeurs));
			valeurs.forEach(l -> lignes.add(new String[] {code, ImportChampsJournal.VALEUR_LISTE_AJOUTEE, null, l}));
		}
		String apresRegles = ImportParametrageVues.reglesLibelle(p, etat.valeursDe(p));
		if (!devientListe && (reglesChangent && !avantRegles.equals(apresRegles) || typeChange)) {
			lignes.add(new String[] {code, ImportChampsJournal.REGLE, avantRegles, apresRegles});
		}
		if (p.isVisibleCollaborateur() != req.visibleCollaborateur()) {
			lignes.add(new String[] {code, ImportChampsJournal.VISIBLE_COLLABORATEUR, oui(p.isVisibleCollaborateur()),
					oui(req.visibleCollaborateur())});
			p.setVisibleCollaborateur(req.visibleCollaborateur());
		}
		persoRepository.save(p);
		return ecriture.terminer(etat, lignes, auteur, clock.instant());
	}

	// ─── C3 : désactiver / réactiver ───────────────────────────────────────────

	@Transactional
	public Parametrage desactiver(String chemin, String code, Long version, AuteurParametre auteur) {
		EtatParametrage etat = ecriture.ouvrir(ecriture.resoudre(chemin));
		ChampPerso p = exiger(etat, code);
		exigerVersion(version, code);
		ecriture.controlerVersion(etat, version);
		if (!p.isActif()) {
			return ecriture.vue(etat);
		}
		p.setActif(false);
		persoRepository.save(p);
		ImportChampRegle r = etat.regle(code);
		r.setDansModele(false);
		r.setExigence(ExigenceChamp.FACULTATIF);
		return ecriture.terminer(etat, List.<String[]>of(new String[] {code, ImportChampsJournal.CHAMP_DESACTIVE, "Actif",
				"Désactivé"}), auteur, clock.instant());
	}

	@Transactional
	public Parametrage reactiver(String chemin, String code, Long version, AuteurParametre auteur) {
		EtatParametrage etat = ecriture.ouvrir(ecriture.resoudre(chemin));
		ChampPerso p = exiger(etat, code);
		exigerVersion(version, code);
		ImportChampRegle r = etat.regle(code);
		if (!p.isActif()) {
			String autre = etat.proprietaire(normaliserComplet(r.getEnTete()), null);
			if (autre != null) {
				throw erreur(400, "LIBELLE_EN_DOUBLE", "« " + r.getEnTete() + " » est déjà reconnu pour le champ "
						+ etat.libelle(autre) + " : renommez ce champ avant de le réactiver.", code);
			}
		}
		ecriture.controlerVersion(etat, version);
		if (p.isActif()) {
			return ecriture.vue(etat);
		}
		p.setActif(true);
		persoRepository.save(p);
		List<String[]> lignes = new ArrayList<>();
		lignes.add(new String[] {code, ImportChampsJournal.CHAMP_REACTIVE, "Désactivé", "Actif"});
		// synonymes devenus en conflit pendant l'inactivité : désactivés automatiquement
		for (var s : etat.synonymesDe(code)) {
			if (s.isActif() && etat.proprietaire(s.getFormeNormalisee(), s.getId()) != null) {
				s.setActif(false);
				lignes.add(new String[] {code, ImportChampsJournal.SYNONYME_DESACTIVE, s.getTexte(), "Désactivé"});
			}
		}
		r.setDansModele(true);
		r.setExigence(ExigenceChamp.FACULTATIF);
		r.setOrdre(etat.regles.stream().mapToInt(ImportChampRegle::getOrdre).max().orElse(-1) + 1);
		return ecriture.terminer(etat, lignes, auteur, clock.instant());
	}

	// ─── C4 : supprimer ────────────────────────────────────────────────────────

	@Transactional
	public void supprimer(String chemin, String code, Long version, AuteurParametre auteur) {
		TypeImportParametrable type = ecriture.resoudre(chemin);
		EtatParametrage etat = ecriture.ouvrir(type);
		ChampPerso p = exiger(etat, code);
		exigerVersion(version, code);
		Compte compte = utilisations.duChamp(type.name(), code); // recompté sous verrou
		if (compte.total() > 0) {
			throw new ReferentielMetierException("PARAMETRE_UTILISE", "Ce champ ne peut pas être supprimé : "
					+ ChampPersoUtilisations.libelle(compte, type.name()) + ".", 409,
					Map.of("utilisations", compte.total(), "champ", code));
		}
		ecriture.controlerVersion(etat, version);
		String libelle = p.getLibelle();
		valeurRepository.deleteAll(etat.valeursDe(p));
		var syn = etat.synonymesDe(code);
		synonymeRepository.deleteAll(syn);
		etat.synonymes.removeAll(syn);
		ImportChampRegle r = etat.regle(code);
		regleRepository.delete(r);
		etat.regles.remove(r);
		persoRepository.delete(p);
		etat.persos.remove(p);
		persoRepository.flush();
		ecriture.terminer(etat, List.<String[]>of(new String[] {code, ImportChampsJournal.CHAMP_SUPPRIME, libelle, null, libelle}),
				auteur, clock.instant());
	}

	// ─── Communs ───────────────────────────────────────────────────────────────

	static ChampPerso exiger(EtatParametrage etat, String code) {
		ChampPerso p = etat.perso(code);
		if (p == null) {
			throw erreur(404, "CHAMP_INCONNU", "Champ personnalisé inconnu : " + code + ".", null);
		}
		return p;
	}

	static void exigerVersion(Long version, String code) {
		if (version == null) {
			throw erreur(400, "VERSION_OBLIGATOIRE", "La version du paramétrage est obligatoire.", code);
		}
	}

	/** 400 LIBELLE_EN_DOUBLE : libellé d'un autre champ (fixe ou personnalisé, actif ou non) ou en-tête déjà reconnu. */
	private void controlerLibelle(EtatParametrage etat, String libelle, String codePropre) {
		String forme = normaliserComplet(libelle);
		boolean pris = etat.type.champs().stream().anyMatch(c -> normaliserComplet(c.libelle()).equals(forme))
				|| etat.persos.stream().anyMatch(q -> !q.getCode().equals(codePropre)
						&& normaliserComplet(q.getLibelle()).equals(forme));
		if (!pris && codePropre == null && etat.proprietaire(forme, null) != null) {
			pris = true;
		}
		if (pris) {
			throw erreur(400, "LIBELLE_EN_DOUBLE", "« " + libelle + " » désigne déjà un autre champ.", codePropre);
		}
	}

	private static String oui(boolean v) {
		return v ? "Oui" : "Non";
	}
}
