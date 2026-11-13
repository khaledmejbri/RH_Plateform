package com.hr.referentiel.importation;

import com.hr.referentiel.config.CacheConfig;
import com.hr.referentiel.domain.ProfilAccesCollaborateur;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.importation.ImportModeles.Colonne;
import com.hr.referentiel.importation.ImportModeles.Evaluation;
import com.hr.referentiel.importation.ImportModeles.LigneResultat;
import com.hr.referentiel.importation.ImportModeles.Modification;
import com.hr.referentiel.importation.ImportModeles.Probleme;
import com.hr.referentiel.importation.ImportModeles.Totaux;
import com.hr.referentiel.importation.lecture.Fichier.LigneBrute;
import com.hr.referentiel.kafka.CollaborateurCompteDemandeEvent;
import com.hr.referentiel.kafka.CollaborateurCompteDemandePublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.service.ApresCommit;
import com.hr.referentiel.service.FormatJours;
import com.hr.referentiel.service.NormalisationLibelle;
import com.hr.referentiel.service.ParametreService;
import com.hr.referentiel.service.ParametresCache;
import com.hr.referentiel.service.ParametresCache.ParametreVue;
import com.hr.referentiel.service.SuperieurHierarchieRules;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.hr.referentiel.importation.ChampImport.Fiche.*;

/**
 * Import des fiches collaborateurs (T2) : évaluation d'un fichier sans aucune écriture (D-I04 à D-I08) puis application
 * des lignes importables dans la transaction de confirmation (D-I08, D-I12). Toutes les fiches, unités et valeurs
 * paramétrées sont lues une seule fois par évaluation : jamais une requête par ligne.
 */
@Service
public class ImportCollaborateursService {

	static final Map<ChampImport.Fiche, Integer> LONGUEURS = new EnumMap<>(ChampImport.Fiche.class);

	static {
		LONGUEURS.put(MATRICULE, 64);
		LONGUEURS.put(PRENOM, 120);
		LONGUEURS.put(NOM, 120);
		LONGUEURS.put(COURRIEL, 255);
		LONGUEURS.put(POSTE, 255);
		LONGUEURS.put(FONCTION, 255);
		LONGUEURS.put(QUALITE, 255);
		LONGUEURS.put(AFFECTATION, 500);
	}

	private static final Pattern COURRIEL_VALIDE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final DateTimeFormatter JJMMAAAA = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
	private static final Set<String> PROFILS_IMPORTABLES = Set.of("COLLABORATEUR", "RO", "RESPONSABLE");
	private static final Set<String> PROFILS_PROTEGES = Set.of("RH", "DIRECTION", "ADMIN");
	private static final String DEFAUT_STATUT = "ACTIF";

	private final CollaborateurRepository collaborateurRepository;
	private final UniteOrganisationRepository uniteRepository;
	private final ParametresCache parametresCache;
	private final ParametreService parametreService;
	private final ObjectProvider<CollaborateurCompteDemandePublisher> publisherProvider;
	private final Clock clock;

	public ImportCollaborateursService(CollaborateurRepository collaborateurRepository,
			UniteOrganisationRepository uniteRepository, ParametresCache parametresCache, ParametreService parametreService,
			ObjectProvider<CollaborateurCompteDemandePublisher> publisherProvider, Clock clock) {
		this.collaborateurRepository = collaborateurRepository;
		this.uniteRepository = uniteRepository;
		this.parametresCache = parametresCache;
		this.parametreService = parametreService;
		this.publisherProvider = publisherProvider;
		this.clock = clock;
	}

	/** Données lues une fois pour toute une évaluation. */
	public static final class Contexte {
		final Map<String, Collaborateur> parMatricule = new HashMap<>();
		final Map<String, Collaborateur> parCourriel = new HashMap<>();
		final List<UniteOrganisation> unites = new ArrayList<>();
		final Map<UUID, UniteOrganisation> unitesParId = new HashMap<>();
		ParametresCache.Instantane referentiels;
	}

	@Transactional(readOnly = true)
	public Contexte charger() {
		Contexte ctx = new Contexte();
		for (Collaborateur c : collaborateurRepository.findAllWithUnite()) {
			if (c.getMatricule() != null) {
				ctx.parMatricule.put(c.getMatricule().trim().toLowerCase(Locale.ROOT), c);
			}
			if (c.getCourrielProfessionnel() != null && !c.getCourrielProfessionnel().isBlank()) {
				ctx.parCourriel.put(c.getCourrielProfessionnel().trim().toLowerCase(Locale.ROOT), c);
			}
		}
		for (UniteOrganisation u : uniteRepository.findAllWithParentAndManager()) {
			ctx.unites.add(u);
			ctx.unitesParId.put(u.getId(), u);
		}
		ctx.referentiels = parametresCache.instantane();
		return ctx;
	}

	/** Matricules (minuscules) des lignes qui créeraient une fiche : interrogés auprès de l'identité (D-I07). */
	public Set<String> matriculesACreer(List<LigneBrute> donnees, List<Colonne> colonnes, Contexte ctx) {
		Set<String> r = new HashSet<>();
		for (LigneBrute l : donnees) {
			String m = valeurs(l, colonnes).get(MATRICULE);
			if (m != null && !ctx.parMatricule.containsKey(m.toLowerCase(Locale.ROOT)) && m.length() <= LONGUEURS.get(MATRICULE)) {
				r.add(m.toLowerCase(Locale.ROOT));
			}
		}
		return r;
	}

	// ─── Évaluation (aucune écriture) ──────────────────────────────────────────

	public Evaluation evaluer(List<LigneBrute> donnees, List<Colonne> colonnes, boolean creerComptes,
			Set<String> comptesExistants, Contexte ctx) {
		Map<ChampImport.Fiche, String> enTetes = new EnumMap<>(ChampImport.Fiche.class);
		for (Colonne c : colonnes) {
			if (c.champ() != null) {
				enTetes.putIfAbsent(ChampImport.Fiche.valueOf(c.champ()), c.enTete());
			}
		}
		List<Map<ChampImport.Fiche, String>> lignes = new ArrayList<>();
		Map<String, Integer> matricules = new HashMap<>();
		Map<String, Integer> courriels = new HashMap<>();
		for (LigneBrute l : donnees) {
			Map<ChampImport.Fiche, String> v = valeurs(l, colonnes);
			lignes.add(v);
			compter(matricules, v.get(MATRICULE));
			compter(courriels, v.get(COURRIEL));
		}
		LocalDate aujourdhui = LocalDate.now(clock.withZone(FormatJours.TUNIS));
		List<LigneResultat> resultats = new ArrayList<>();
		for (int i = 0; i < donnees.size(); i++) {
			resultats.add(evaluerLigne(donnees.get(i).numero(), lignes.get(i), enTetes, creerComptes, comptesExistants, ctx,
					matricules, courriels, aujourdhui));
		}
		return new Evaluation(resultats, List.of(), List.of(), totaliser(resultats));
	}

	private static void compter(Map<String, Integer> m, String valeur) {
		if (valeur != null) {
			m.merge(valeur.toLowerCase(Locale.ROOT), 1, Integer::sum);
		}
	}

	/** Valeurs d'une ligne par champ : texte rogné, cellule vide = absente, première colonne gagnante. */
	static Map<ChampImport.Fiche, String> valeurs(LigneBrute ligne, List<Colonne> colonnes) {
		Map<ChampImport.Fiche, String> v = new EnumMap<>(ChampImport.Fiche.class);
		for (Colonne c : colonnes) {
			if (c.champ() == null || c.index() >= ligne.cellules().size()) {
				continue;
			}
			String brut = ligne.cellules().get(c.index());
			String t = brut == null ? "" : brut.strip();
			if (!t.isEmpty()) {
				v.putIfAbsent(ChampImport.Fiche.valueOf(c.champ()), t);
			}
		}
		return v;
	}

	private LigneResultat evaluerLigne(int numero, Map<ChampImport.Fiche, String> v, Map<ChampImport.Fiche, String> enTetes,
			boolean creerComptes, Set<String> comptesExistants, Contexte ctx, Map<String, Integer> matricules,
			Map<String, Integer> courriels, LocalDate aujourdhui) {
		List<Probleme> erreurs = new ArrayList<>();
		List<Probleme> avertissements = new ArrayList<>();
		List<Modification> modifs = new ArrayList<>();
		Map<String, String> techniques = new LinkedHashMap<>();

		String matricule = v.get(MATRICULE);
		Collaborateur existant = null;
		if (matricule == null) {
			erreurs.add(new Probleme("MATRICULE_OBLIGATOIRE", libelleColonne(enTetes, MATRICULE), null,
					"Matricule obligatoire."));
		} else {
			if (matricule.length() > LONGUEURS.get(MATRICULE)) {
				erreurs.add(tropLong(enTetes, MATRICULE, matricule));
			} else {
				existant = ctx.parMatricule.get(matricule.toLowerCase(Locale.ROOT));
				techniques.put(MATRICULE.name(), matricule);
			}
			if (matricules.getOrDefault(matricule.toLowerCase(Locale.ROOT), 0) > 1) {
				erreurs.add(new Probleme("MATRICULE_EN_DOUBLE_FICHIER", libelleColonne(enTetes, MATRICULE), court(matricule),
						"Matricule présent plusieurs fois dans le fichier : « " + court(matricule) + " »."));
			}
		}
		boolean creation = existant == null;

		texte(PRENOM, v, enTetes, erreurs, modifs, techniques, !creation, creation ? null : existant.getPrenom());
		texte(NOM, v, enTetes, erreurs, modifs, techniques, !creation, creation ? null : existant.getNom());
		texte(POSTE, v, enTetes, erreurs, modifs, techniques, !creation, creation ? null : existant.getPosteLibelle());
		texte(FONCTION, v, enTetes, erreurs, modifs, techniques, !creation, creation ? null : existant.getFonction());
		texte(QUALITE, v, enTetes, erreurs, modifs, techniques, !creation, creation ? null : existant.getQualite());
		texte(AFFECTATION, v, enTetes, erreurs, modifs, techniques, !creation, creation ? null : existant.getAffectation());
		courriel(v, enTetes, erreurs, modifs, techniques, existant, courriels, ctx);
		unite(v, enTetes, erreurs, modifs, techniques, existant, ctx);
		date(v, enTetes, erreurs, modifs, techniques, existant, aujourdhui);
		referentiel(STATUT, "STATUT_COLLABORATEUR", v, enTetes, erreurs, modifs, techniques, !creation,
				creation ? null : existant.getStatut(), ctx);
		referentiel(FAMILLE_METIER, "FAMILLE_METIER", v, enTetes, erreurs, modifs, techniques, !creation,
				creation ? null : existant.getFamilleMetierCode(), ctx);
		referentiel(NIVEAU_SENIORITE, "NIVEAU_SENIORITE", v, enTetes, erreurs, modifs, techniques, !creation,
				creation ? null : existant.getNiveauSeniorite(), ctx);
		profil(v, enTetes, erreurs, modifs, techniques, existant, ctx);

		if (creation) {
			for (ChampImport.Fiche obligatoire : List.of(PRENOM, NOM, UNITE)) {
				if (!v.containsKey(obligatoire)) {
					erreurs.add(new Probleme("CHAMP_OBLIGATOIRE", libelleColonne(enTetes, obligatoire), null,
							"Champ obligatoire : " + obligatoire.libelle() + "."));
				}
			}
			if (creerComptes && !v.containsKey(COURRIEL)) {
				erreurs.add(new Probleme("COURRIEL_OBLIGATOIRE", libelleColonne(enTetes, COURRIEL), null,
						"Courriel obligatoire pour créer le compte d'accès."));
			}
		}

		boolean compteDemande = false;
		if (creation && !v.containsKey(COURRIEL)) {
			avertissements.add(new Probleme("COMPTE_NON_CREE", null, null, "Compte non créé : courriel absent"));
		} else if (!creation && creerComptes && !v.containsKey(COURRIEL)) {
			avertissements.add(new Probleme("COMPTE_NON_CREE", null, null, "Compte non créé : courriel absent"));
		}
		if (creation && creerComptes && v.containsKey(COURRIEL) && matricule != null) {
			compteDemande = erreurs.isEmpty();
			if (comptesExistants.contains(matricule.toLowerCase(Locale.ROOT))) {
				avertissements.add(new Probleme("COMPTE_DEJA_EXISTANT", null, null,
						"Compte déjà existant pour ce matricule : il sera lié à la fiche, sans courriel."));
			}
		}

		String action;
		if (!erreurs.isEmpty()) {
			action = "ERREUR";
		} else if (creation) {
			action = "CREATION";
		} else {
			action = modifs.isEmpty() ? "INCHANGEE" : "MISE_A_JOUR";
		}
		String nom = existant != null ? nomComplet(existant.getPrenom(), existant.getNom())
				: nomComplet(v.get(PRENOM), v.get(NOM));
		return new LigneResultat(numero, matricule, nom, action, erreurs, avertissements, modifs, null, null, null, null,
				existant == null ? null : existant.getId(), existant == null ? null : existant.getModifieLe(), techniques,
				compteDemande);
	}

	// ─── Validations par champ ─────────────────────────────────────────────────

	private void texte(ChampImport.Fiche champ, Map<ChampImport.Fiche, String> v, Map<ChampImport.Fiche, String> enTetes,
			List<Probleme> erreurs, List<Modification> modifs, Map<String, String> techniques, boolean miseAJour,
			String actuel) {
		String valeur = v.get(champ);
		if (valeur == null) {
			return;
		}
		if (valeur.length() > LONGUEURS.get(champ)) {
			erreurs.add(tropLong(enTetes, champ, valeur));
			return;
		}
		techniques.put(champ.name(), valeur);
		// création : aucune modification listée, l'action CREATION suffit ; mise à jour : seuls les champs qui changent
		if (miseAJour && !valeur.equals(actuel == null ? "" : actuel.strip())) {
			modifs.add(new Modification(champ.name(), champ.libelle(), actuel == null ? "" : actuel, valeur));
		}
	}

	private void courriel(Map<ChampImport.Fiche, String> v, Map<ChampImport.Fiche, String> enTetes, List<Probleme> erreurs,
			List<Modification> modifs, Map<String, String> techniques, Collaborateur existant, Map<String, Integer> courriels,
			Contexte ctx) {
		String valeur = v.get(COURRIEL);
		if (valeur == null) {
			return;
		}
		if (valeur.length() > LONGUEURS.get(COURRIEL)) {
			erreurs.add(tropLong(enTetes, COURRIEL, valeur));
			return;
		}
		if (!COURRIEL_VALIDE.matcher(valeur).matches()) {
			erreurs.add(new Probleme("FORMAT_INVALIDE", libelleColonne(enTetes, COURRIEL), court(valeur),
					"Format de courriel invalide « " + court(valeur) + " » (colonne « " + libelleColonne(enTetes, COURRIEL) + " »)."));
			return;
		}
		String cle = valeur.toLowerCase(Locale.ROOT);
		Collaborateur autre = ctx.parCourriel.get(cle);
		boolean pris = autre != null && (existant == null || !autre.getId().equals(existant.getId()));
		if (pris || courriels.getOrDefault(cle, 0) > 1) {
			erreurs.add(new Probleme("COURRIEL_EN_DOUBLE", libelleColonne(enTetes, COURRIEL), court(valeur),
					"Courriel déjà utilisé : « " + court(valeur) + " »."));
			return;
		}
		techniques.put(COURRIEL.name(), valeur);
		String actuel = existant == null ? null : existant.getCourrielProfessionnel();
		if (existant != null && !valeur.equals(actuel == null ? "" : actuel.strip())) {
			modifs.add(new Modification(COURRIEL.name(), COURRIEL.libelle(), actuel == null ? "" : actuel, valeur));
		}
	}

	private void unite(Map<ChampImport.Fiche, String> v, Map<ChampImport.Fiche, String> enTetes, List<Probleme> erreurs,
			List<Modification> modifs, Map<String, String> techniques, Collaborateur existant, Contexte ctx) {
		String valeur = v.get(UNITE);
		if (valeur == null) {
			return;
		}
		String col = libelleColonne(enTetes, UNITE);
		UniteOrganisation trouvee = null;
		for (UniteOrganisation u : ctx.unites) {
			if (u.getCode() != null && u.getCode().equalsIgnoreCase(valeur)) {
				trouvee = u;
				break;
			}
		}
		if (trouvee == null) {
			String n = NormalisationLibelle.normaliser(valeur);
			List<UniteOrganisation> actives = new ArrayList<>();
			List<UniteOrganisation> inactives = new ArrayList<>();
			for (UniteOrganisation u : ctx.unites) {
				if (NormalisationLibelle.normaliser(u.getLibelle()).equals(n)) {
					(u.isActif() ? actives : inactives).add(u);
				}
			}
			if (actives.size() > 1) {
				erreurs.add(new Probleme("UNITE_AMBIGUE", col, court(valeur), "Unité ambiguë « " + court(valeur)
						+ " » (colonne « " + col + " ») : plusieurs unités portent ce libellé, utilisez le code."));
				return;
			}
			trouvee = actives.isEmpty() ? (inactives.isEmpty() ? null : inactives.get(0)) : actives.get(0);
		}
		if (trouvee == null) {
			erreurs.add(new Probleme("UNITE_INCONNUE", col, court(valeur),
					"Unité inconnue « " + court(valeur) + " » (colonne « " + col + " »)."));
			return;
		}
		boolean inchangee = existant != null && existant.getUnite() != null && existant.getUnite().getId().equals(trouvee.getId());
		if (!trouvee.isActif() && !inchangee) {
			erreurs.add(new Probleme("UNITE_INACTIVE", col, court(valeur),
					"Unité inactive « " + court(valeur) + " » (colonne « " + col + " »)."));
			return;
		}
		techniques.put(UNITE.name(), trouvee.getId().toString());
		if (existant != null && !inchangee) {
			modifs.add(new Modification(UNITE.name(), UNITE.libelle(),
					existant.getUnite() == null ? "" : existant.getUnite().getLibelle(), trouvee.getLibelle()));
		}
	}

	private void date(Map<ChampImport.Fiche, String> v, Map<ChampImport.Fiche, String> enTetes, List<Probleme> erreurs,
			List<Modification> modifs, Map<String, String> techniques, Collaborateur existant, LocalDate aujourdhui) {
		String valeur = v.get(DATE_RECRUTEMENT);
		if (valeur == null) {
			return;
		}
		LocalDate d = lireDate(valeur);
		if (d == null || d.isAfter(aujourdhui.plusYears(1))) {
			erreurs.add(new Probleme("DATE_INVALIDE", libelleColonne(enTetes, DATE_RECRUTEMENT), court(valeur),
					"Date invalide « " + court(valeur) + " » (colonne « " + libelleColonne(enTetes, DATE_RECRUTEMENT)
							+ " ») : utilisez jj/mm/aaaa."));
			return;
		}
		techniques.put(DATE_RECRUTEMENT.name(), d.toString());
		if (existant != null && !d.equals(existant.getDateRecrutement())) {
			modifs.add(new Modification(DATE_RECRUTEMENT.name(), DATE_RECRUTEMENT.libelle(),
					existant.getDateRecrutement() == null ? "" : existant.getDateRecrutement().format(FormatJours.JOUR),
					d.format(FormatJours.JOUR)));
		}
	}

	/** {@code jj/mm/aaaa}, {@code aaaa-mm-jj} ou numéro de série Excel ; jamais d'interprétation approximative. */
	static LocalDate lireDate(String valeur) {
		String t = valeur.strip();
		try {
			if (t.matches("\\d{2}/\\d{2}/\\d{4}")) {
				return LocalDate.parse(t, JJMMAAAA);
			}
			if (t.matches("\\d{4}-\\d{2}-\\d{2}")) {
				return LocalDate.parse(t);
			}
			if (t.matches("\\d{1,6}(\\.0+)?")) {
				long serie = Long.parseLong(t.replaceAll("\\.0+$", ""));
				return serie < 1 ? null : LocalDate.of(1899, 12, 30).plusDays(serie);
			}
		} catch (DateTimeParseException | ArithmeticException e) {
			return null;
		}
		return null;
	}

	private void referentiel(ChampImport.Fiche champ, String referentiel, Map<ChampImport.Fiche, String> v,
			Map<ChampImport.Fiche, String> enTetes, List<Probleme> erreurs, List<Modification> modifs,
			Map<String, String> techniques, boolean miseAJour, String actuel, Contexte ctx) {
		String valeur = v.get(champ);
		if (valeur == null) {
			return;
		}
		ParametreVue vue = trouverActif(ctx, referentiel, valeur);
		if (vue == null) {
			erreurs.add(inconnue(enTetes, champ, valeur));
			return;
		}
		techniques.put(champ.name(), vue.code());
		if (miseAJour && !vue.code().equals(actuel)) {
			modifs.add(new Modification(champ.name(), champ.libelle(), libelleDe(ctx, referentiel, actuel), vue.libelle()));
		}
	}

	private void profil(Map<ChampImport.Fiche, String> v, Map<ChampImport.Fiche, String> enTetes, List<Probleme> erreurs,
			List<Modification> modifs, Map<String, String> techniques, Collaborateur existant, Contexte ctx) {
		String valeur = v.get(PROFIL_ACCES);
		String actuel = existant == null ? null : existant.getProfilAcces();
		if (valeur == null) {
			return;
		}
		ParametreVue vue = trouverQuelconque(ctx, "PROFIL_ACCES", valeur);
		if (vue == null) {
			erreurs.add(inconnue(enTetes, PROFIL_ACCES, valeur));
			return;
		}
		if (actuel != null && actuel.equals(vue.code())) {
			techniques.put(PROFIL_ACCES.name(), vue.code());
			return;
		}
		// profil RH / DIRECTION / ADMIN : ni attribué, ni retiré par import (D-I06)
		if (PROFILS_PROTEGES.contains(vue.code()) || (actuel != null && PROFILS_PROTEGES.contains(actuel))
				|| !PROFILS_IMPORTABLES.contains(vue.code())) {
			erreurs.add(new Probleme("PROFIL_NON_IMPORTABLE", libelleColonne(enTetes, PROFIL_ACCES), court(valeur),
					"Ce profil ne peut pas être modifié par import : « " + court(valeur) + " »."));
			return;
		}
		techniques.put(PROFIL_ACCES.name(), vue.code());
		if (existant != null) {
			modifs.add(new Modification(PROFIL_ACCES.name(), PROFIL_ACCES.libelle(),
					libelleDe(ctx, "PROFIL_ACCES", actuel), vue.libelle()));
		}
	}

	private static ParametreVue trouverActif(Contexte ctx, String referentiel, String valeur) {
		ParametreVue v = trouverQuelconque(ctx, referentiel, valeur);
		return v != null && v.actif() ? v : null;
	}

	private static ParametreVue trouverQuelconque(Contexte ctx, String referentiel, String valeur) {
		String n = NormalisationLibelle.normaliser(valeur);
		for (ParametreVue p : ctx.referentiels.liste(referentiel)) {
			if (NormalisationLibelle.normaliser(p.code()).equals(n) || NormalisationLibelle.normaliser(p.libelle()).equals(n)) {
				return p;
			}
		}
		return null;
	}

	private static String libelleDe(Contexte ctx, String referentiel, String code) {
		if (code == null) {
			return "";
		}
		String l = ctx.referentiels.libelle(referentiel, code);
		return l == null ? "" : l;
	}

	// ─── Totaux et messages ────────────────────────────────────────────────────

	static Totaux totaliser(List<LigneResultat> lignes) {
		int creations = 0;
		int majs = 0;
		int inchangees = 0;
		int erreurs = 0;
		int avertissements = 0;
		for (LigneResultat l : lignes) {
			switch (l.action()) {
				case "CREATION" -> creations++;
				case "MISE_A_JOUR" -> majs++;
				case "INCHANGEE" -> inchangees++;
				default -> erreurs++;
			}
			if (l.avertissements() != null && !l.avertissements().isEmpty()) {
				avertissements++;
			}
		}
		return new Totaux(lignes.size(), creations, majs, inchangees, null, erreurs, avertissements);
	}

	private static String libelleColonne(Map<ChampImport.Fiche, String> enTetes, ChampImport.Fiche champ) {
		String e = enTetes.get(champ);
		return e == null || e.isBlank() ? champ.libelle() : e;
	}

	private static Probleme tropLong(Map<ChampImport.Fiche, String> enTetes, ChampImport.Fiche champ, String valeur) {
		String col = libelleColonne(enTetes, champ);
		return new Probleme("VALEUR_TROP_LONGUE", col, court(valeur), "Valeur trop longue (maximum " + LONGUEURS.get(champ)
				+ " caractères) (colonne « " + col + " »).");
	}

	private static Probleme inconnue(Map<ChampImport.Fiche, String> enTetes, ChampImport.Fiche champ, String valeur) {
		String col = libelleColonne(enTetes, champ);
		return new Probleme("VALEUR_INCONNUE", col, court(valeur),
				"Valeur inconnue « " + court(valeur) + " » (colonne « " + col + " »).");
	}

	private static String court(String v) {
		return v.length() > 60 ? v.substring(0, 60) + "…" : v;
	}

	private static String nomComplet(String prenom, String nom) {
		String r = ((prenom == null ? "" : prenom) + " " + (nom == null ? "" : nom)).trim();
		return r.isEmpty() ? null : r;
	}

	// ─── Application (dans la transaction de confirmation) ─────────────────────

	/** Bilan de l'application : fiches touchées et comptes demandés. */
	public record Application(int creees, int misesAJour, int comptesDemandes, Map<String, UUID> idsParMatricule) {
	}

	/**
	 * Écrit les lignes importables (créations et mises à jour) : supérieur dérivé du manager de l'unité (D-I12), rôles du
	 * compte resynchronisés au changement de profil, demandes de compte publiées APRÈS le commit seulement. Une panne avant
	 * le commit n'écrit rien et ne publie rien.
	 */
	@Transactional
	@CacheEvict(value = {CacheConfig.CACHE_COLLABORATEUR_ID, CacheConfig.CACHE_COLLABORATEUR_MATRICULE}, allEntries = true)
	public Application appliquer(List<LigneResultat> importables, Contexte ctx) {
		CollaborateurCompteDemandePublisher publisher = publisherProvider.getIfAvailable();
		boolean aDesComptes = importables.stream().anyMatch(LigneResultat::creerCompte);
		if (aDesComptes && publisher == null) {
			throw new IllegalStateException("Kafka n'est pas configuré : impossible de demander la création des comptes.");
		}
		int creees = 0;
		int majs = 0;
		List<Collaborateur> aEnregistrer = new ArrayList<>();
		List<LigneResultat> pourComptes = new ArrayList<>();
		Map<String, String> profilsAvant = new HashMap<>();
		Set<String> familles = new HashSet<>();
		Set<String> niveaux = new HashSet<>();
		for (LigneResultat l : importables) {
			Map<String, String> v = l.valeurs();
			boolean creation = "CREATION".equals(l.action());
			Collaborateur c;
			if (creation) {
				c = new Collaborateur();
				c.setMatricule(v.get(MATRICULE.name()));
				c.setStatut(DEFAUT_STATUT);
				c.setProfilAcces(ProfilAccesCollaborateur.COLLABORATEUR.name());
				creees++;
			} else {
				c = ctx.parMatricule.get(l.matricule().toLowerCase(Locale.ROOT));
				profilsAvant.put(c.getMatricule(), c.getProfilAcces());
				majs++;
			}
			appliquerChamps(c, v, ctx, creation);
			if (v.get(FAMILLE_METIER.name()) != null) {
				familles.add(v.get(FAMILLE_METIER.name()));
			}
			if (v.get(NIVEAU_SENIORITE.name()) != null) {
				niveaux.add(v.get(NIVEAU_SENIORITE.name()));
			}
			aEnregistrer.add(c);
			if (creation && l.creerCompte()) {
				pourComptes.add(l);
			}
		}
		List<Collaborateur> enregistres = collaborateurRepository.saveAll(aEnregistrer);
		familles.forEach(code -> parametreService.marquerUtilisee("FAMILLE_METIER", code));
		niveaux.forEach(code -> parametreService.marquerUtilisee("NIVEAU_SENIORITE", code));

		Map<String, UUID> ids = new HashMap<>();
		for (Collaborateur c : enregistres) {
			ids.put(c.getMatricule().toLowerCase(Locale.ROOT), c.getId());
		}
		List<CollaborateurCompteDemandeEvent> evenements = new ArrayList<>();
		for (LigneResultat l : pourComptes) {
			Collaborateur c = trouver(enregistres, l.matricule());
			evenements.add(new CollaborateurCompteDemandeEvent(c.getId(), c.getMatricule(), c.getCourrielProfessionnel(),
					c.getPrenom(), c.getNom(), c.getProfilAcces(), null, CollaborateurCompteDemandeEvent.OPERATION_CREATION,
					null, true));
		}
		for (Collaborateur c : enregistres) {
			String avant = profilsAvant.get(c.getMatricule());
			if (avant != null && !Objects.equals(avant, c.getProfilAcces()) && c.getCompteUtilisateurId() != null) {
				evenements.add(new CollaborateurCompteDemandeEvent(c.getId(), c.getMatricule(), c.getCourrielProfessionnel(),
						c.getPrenom(), c.getNom(), c.getProfilAcces(), null, CollaborateurCompteDemandeEvent.OPERATION_MAJ_ROLES,
						c.getCompteUtilisateurId(), null));
			}
		}
		if (!evenements.isEmpty() && publisher != null) {
			ApresCommit.executer(() -> evenements.forEach(e -> publisher.publishAsyncAfterCommit(e.collaborateurIdentifiant().toString(), e)));
		}
		return new Application(creees, majs, pourComptes.size(), ids);
	}

	private static Collaborateur trouver(List<Collaborateur> liste, String matricule) {
		return liste.stream().filter(c -> c.getMatricule().equalsIgnoreCase(matricule)).findFirst().orElseThrow();
	}

	private static void appliquerChamps(Collaborateur c, Map<String, String> v, Contexte ctx, boolean creation) {
		applique(v, PRENOM, c::setPrenom);
		applique(v, NOM, c::setNom);
		applique(v, COURRIEL, c::setCourrielProfessionnel);
		applique(v, POSTE, c::setPosteLibelle);
		applique(v, FONCTION, c::setFonction);
		applique(v, QUALITE, c::setQualite);
		applique(v, AFFECTATION, c::setAffectation);
		if (v.containsKey(DATE_RECRUTEMENT.name())) {
			c.setDateRecrutement(LocalDate.parse(v.get(DATE_RECRUTEMENT.name())));
		}
		applique(v, STATUT, c::setStatut);
		applique(v, PROFIL_ACCES, c::setProfilAcces);
		applique(v, FAMILLE_METIER, c::setFamilleMetierCode);
		applique(v, NIVEAU_SENIORITE, c::setNiveauSeniorite);
		if (v.containsKey(UNITE.name())) {
			UniteOrganisation u = ctx.unitesParId.get(UUID.fromString(v.get(UNITE.name())));
			boolean change = c.getUnite() == null || !c.getUnite().getId().equals(u.getId());
			c.setUnite(u);
			if (creation) {
				c.setSuperieur(SuperieurHierarchieRules.resoudreSuperieurFiche(c, u));
			} else if (change) {
				SuperieurHierarchieRules.appliquerSiChange(c, SuperieurHierarchieRules.resoudreSuperieurFiche(c, u));
			}
		}
	}

	private static void applique(Map<String, String> v, ChampImport.Fiche champ, java.util.function.Consumer<String> setter) {
		String valeur = v.get(champ.name());
		if (valeur != null) {
			setter.accept(valeur);
		}
	}
}
