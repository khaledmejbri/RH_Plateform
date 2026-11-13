package com.hr.referentiel.importation;

import com.hr.referentiel.dto.PageReferentielResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.importation.ImportDtos.Analyse;
import com.hr.referentiel.importation.ImportDtos.ChampDisponible;
import com.hr.referentiel.importation.ImportDtos.ColonneChoix;
import com.hr.referentiel.importation.ImportDtos.ColonneVue;
import com.hr.referentiel.importation.ImportDtos.CompteRendu;
import com.hr.referentiel.importation.ImportDtos.ConfirmationRequest;
import com.hr.referentiel.importation.ImportDtos.CorrespondanceRequest;
import com.hr.referentiel.importation.ImportDtos.ImportResume;
import com.hr.referentiel.importation.ImportDtos.LigneAnalyse;
import com.hr.referentiel.importation.ImportDtos.LigneHistorique;
import com.hr.referentiel.importation.ImportDtos.ModificationVue;
import com.hr.referentiel.importation.ImportDtos.ProblemeVue;
import com.hr.referentiel.importation.ImportDtos.TotauxVue;
import com.hr.referentiel.importation.ImportModeles.Colonne;
import com.hr.referentiel.importation.ImportModeles.Evaluation;
import com.hr.referentiel.importation.ImportModeles.LigneResultat;
import com.hr.referentiel.importation.ImportModeles.Modification;
import com.hr.referentiel.importation.ImportModeles.Probleme;
import com.hr.referentiel.importation.ImportModeles.Totaux;
import com.hr.referentiel.importation.lecture.Fichier;
import com.hr.referentiel.importation.lecture.Fichier.LigneBrute;
import com.hr.referentiel.importation.lecture.LectureFichier;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.service.AuteurParametreResolver;
import com.hr.referentiel.service.CollaborateurConnecteService;
import com.hr.referentiel.web.ReferentielMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Parcours d'un import (T2, T3) : analyse sans écriture métier, correspondance corrigée sans renvoi du fichier,
 * confirmation revalidée sur l'état courant sous verrou unique. Le fichier brut n'est jamais écrit ; le contenu extrait
 * (JSON gzip) est gardé 30 minutes, visible de son seul auteur. Aucune valeur de cellule dans les journaux INFO.
 */
@Service
public class ImportService {

	private static final Logger log = LoggerFactory.getLogger(ImportService.class);

	public static final int PAGE_TAILLE_DEFAUT = 50;
	public static final int PAGE_TAILLE_MAX = 100;
	public static final int MOTIF_MIN = 5;
	public static final int MOTIF_MAX = 500;
	static final int ECHEANCE_ANALYSE_NON_CONFIRMEE_HEURES = 24;
	static final int CONFIRMATION_TIMEOUT_SECONDES = 120;

	/** Contenu extrait et résultat de la dernière évaluation (JSON gzip dans {@code rh_import_contenu}). */
	record ResultatStocke(Integer ligneEnTete, List<Colonne> colonnes, List<String> colonnesIgnorees, Evaluation evaluation,
			Set<String> comptesExistants, List<Probleme> avertissementsEnPlus) {
	}

	private final ImportAnalyseRepository analyseRepository;
	private final ImportContenuRepository contenuRepository;
	private final ImportLigneRepository ligneRepository;
	private final ImportCollaborateursService collaborateursService;
	private final ImportSoldesService soldesService;
	private final IdentiteInterneClient identiteClient;
	private final ImportVerrou verrou;
	private final AuteurParametreResolver auteurResolver;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final JsonMapper jsonMapper;
	private final Clock clock;
	private final PlatformTransactionManager transactionManager;
	private final int expirationMinutes;

	public ImportService(ImportAnalyseRepository analyseRepository, ImportContenuRepository contenuRepository,
			ImportLigneRepository ligneRepository, ImportCollaborateursService collaborateursService,
			ImportSoldesService soldesService, IdentiteInterneClient identiteClient, ImportVerrou verrou,
			AuteurParametreResolver auteurResolver, CollaborateurConnecteService collaborateurConnecteService,
			JsonMapper jsonMapper, Clock clock, PlatformTransactionManager transactionManager,
			@Value("${referentiel.imports.expiration-minutes:30}") int expirationMinutes) {
		this.analyseRepository = analyseRepository;
		this.contenuRepository = contenuRepository;
		this.ligneRepository = ligneRepository;
		this.collaborateursService = collaborateursService;
		this.soldesService = soldesService;
		this.identiteClient = identiteClient;
		this.verrou = verrou;
		this.auteurResolver = auteurResolver;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
		this.transactionManager = transactionManager;
		this.expirationMinutes = expirationMinutes;
	}

	public int expirationMinutes() {
		return expirationMinutes;
	}

	// ─── Analyse (I1, I2) ──────────────────────────────────────────────────────

	public Analyse analyserCollaborateurs(MultipartFile fichier, boolean creerComptes, Jwt jwt) {
		return analyser(ImportAnalyse.TYPE_COLLABORATEURS, fichier, creerComptes, null, jwt);
	}

	public Analyse analyserSoldes(MultipartFile fichier, String motifGlobal, Jwt jwt) {
		return analyser(ImportAnalyse.TYPE_SOLDES, fichier, false, motifGlobal, jwt);
	}

	private Analyse analyser(String type, MultipartFile mp, boolean creerComptes, String motifGlobal, Jwt jwt) {
		AuteurParametre auteur = auteurResolver.depuis(jwt);
		UUID compte = exigerCompte(auteur);
		if (mp == null || mp.isEmpty()) {
			throw ReferentielMetierException.badRequest("FORMAT_NON_SUPPORTE", "Format non supporté : le fichier est vide.");
		}
		if (mp.getSize() > LectureFichier.TAILLE_MAX) {
			throw ReferentielMetierException.payloadTooLarge("FICHIER_TROP_VOLUMINEUX", "Le fichier dépasse 10 Mo.");
		}
		byte[] octets;
		try {
			octets = mp.getBytes();
		} catch (IOException e) {
			throw ReferentielMetierException.badRequest("FORMAT_NON_SUPPORTE", "Format non supporté : le fichier est illisible.");
		}
		Fichier lu = LectureFichier.lire(mp.getOriginalFilename(), octets);
		ImportAnalyse a = new ImportAnalyse();
		a.setId(UUID.randomUUID());
		a.setTypeImport(type);
		a.setStatut(ImportAnalyse.STATUT_ANALYSEE);
		a.setAuteurCompteId(compte);
		a.setAuteurNom(auteur.nom());
		a.setAuteurFicheId(collaborateurConnecteService.resoudre(jwt).map(Collaborateur::getId).orElse(null));
		a.setNomFichier(nomSansChemin(mp.getOriginalFilename()));
		a.setCreerComptes(ImportAnalyse.TYPE_COLLABORATEURS.equals(type) ? creerComptes : null);
		a.setMotifGlobal(motifGlobal == null || motifGlobal.isBlank() ? null : motifGlobal.strip());
		Instant maintenant = clock.instant();
		a.setCreeLe(maintenant);
		a.setDerniereActionLe(maintenant);
		a.setAnalyseLe(maintenant);
		ResultatStocke resultat = evaluer(a, lu, null, null, null);
		persister(a, lu, resultat, true);
		log.info("Import {} analysé : {} ligne(s)", type, resultat.evaluation().totaux().lignes());
		return versAnalyse(a, resultat, 0);
	}

	// ─── Lecture et correction (I3, I4, I5) ────────────────────────────────────

	@Transactional
	public Analyse obtenir(UUID id, Jwt jwt) {
		ImportAnalyse a = ouvrir(id, jwt, true);
		return versAnalyse(a, lireResultat(a), -1);
	}

	@Transactional
	public PageReferentielResponse<LigneAnalyse> lignes(UUID id, int page, int taille, boolean erreursSeulement, Jwt jwt) {
		ImportAnalyse a = ouvrir(id, jwt, true);
		validerTaille(taille);
		return pageLignes(lireResultat(a).evaluation().lignes(), a.getTypeImport(), page, taille, erreursSeulement);
	}

	public Analyse corriger(UUID id, CorrespondanceRequest req, Jwt jwt) {
		if (req == null || req.colonnes() == null) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "La correspondance des colonnes est obligatoire.");
		}
		ImportAnalyse a = ouvrir(id, jwt, true);
		if (ImportAnalyse.STATUT_CONFIRMEE.equals(a.getStatut())) {
			throw ReferentielMetierException.conflict("IMPORT_DEJA_CONFIRME", "Cet import est déjà confirmé.");
		}
		Fichier lu = lireContenu(a);
		if (req.creerComptes() != null && ImportAnalyse.TYPE_COLLABORATEURS.equals(a.getTypeImport())) {
			a.setCreerComptes(req.creerComptes());
		}
		if (req.motifGlobal() != null) {
			a.setMotifGlobal(req.motifGlobal().isBlank() ? null : req.motifGlobal().strip());
		}
		ResultatStocke resultat = evaluer(a, lu, req.ligneEnTete(), req.colonnes(), lireResultat(a).comptesExistants());
		persister(a, lu, resultat, false);
		return versAnalyse(a, resultat, 0);
	}

	// ─── Fichier des erreurs (I7) ──────────────────────────────────────────────

	@Transactional
	public byte[] fichierErreurs(UUID id, Jwt jwt) {
		ImportAnalyse a = ouvrir(id, jwt, true);
		Fichier lu = lireContenu(a);
		ResultatStocke r = lireResultat(a);
		Map<Integer, LigneBrute> parNumero = new LinkedHashMap<>();
		lu.lignes().forEach(l -> parNumero.put(l.numero(), l));
		LigneBrute entete = parNumero.get(r.ligneEnTete());
		List<List<String>> sortie = new ArrayList<>();
		List<String> premiere = new ArrayList<>(entete == null ? List.of() : entete.cellules());
		premiere.add("Erreurs");
		sortie.add(premiere);
		for (LigneResultat l : r.evaluation().lignes()) {
			if (!l.enErreur()) {
				continue;
			}
			LigneBrute brute = parNumero.get(l.numero());
			List<String> cellules = new ArrayList<>(brute == null ? List.of() : brute.cellules());
			while (cellules.size() < premiere.size() - 1) {
				cellules.add("");
			}
			cellules.add(l.erreurs().stream().map(Probleme::message).collect(Collectors.joining(" ; ")));
			sortie.add(cellules);
		}
		return FichiersImportXlsx.erreurs(sortie);
	}

	public byte[] modeleCollaborateurs() {
		return FichiersImportXlsx.modele(List.of(ChampImport.Fiche.values()));
	}

	public byte[] modeleSoldes() {
		return FichiersImportXlsx.modele(List.of(ChampImport.Solde.values()));
	}

	// ─── Confirmation (I6) ─────────────────────────────────────────────────────

	/**
	 * Une transaction, {@code timeout} 120 s. Ordre des contrôles : 404, 400 motif, 409 déjà confirmé, 409 expiré, 409
	 * en cours, puis 422 (seule exception assumée à l'ordre « 422 avant 409 » : ils dépendent de la réanalyse sous verrou).
	 */
	@Transactional(timeout = CONFIRMATION_TIMEOUT_SECONDES)
	public CompteRendu confirmer(UUID id, ConfirmationRequest req, Jwt jwt) {
		// verrou de ligne dès la première lecture : l'état contrôlé est celui d'après toute confirmation concurrente
		ImportAnalyse apercu = ouvrir(id, jwt, false, false, true);
		boolean soldes = ImportAnalyse.TYPE_SOLDES.equals(apercu.getTypeImport());
		String motifGlobal = null;
		if (soldes) {
			String brut = req != null && req.motifGlobal() != null ? req.motifGlobal() : apercu.getMotifGlobal();
			motifGlobal = brut == null ? "" : brut.strip();
			if (motifGlobal.length() < MOTIF_MIN || motifGlobal.length() > MOTIF_MAX) {
				throw ReferentielMetierException.badRequest("MOTIF_OBLIGATOIRE",
						"Le motif global est obligatoire (" + MOTIF_MIN + " à " + MOTIF_MAX + " caractères).");
			}
		}
		controlerEtat(apercu);
		if (!verrou.essayer()) {
			throw ReferentielMetierException.conflict("IMPORT_EN_COURS",
					"Un autre import est en cours, réessayez dans quelques instants.");
		}
		ImportAnalyse a = apercu;
		Fichier lu = lireContenu(a);
		ResultatStocke avant = lireResultat(a);
		Reevaluation reeval = soldes ? reevaluerSoldes(a, lu, avant, motifGlobal) : reevaluerFiches(a, lu, avant);
		ResultatStocke apres = reeval.resultat();
		if (!apres.evaluation().confirmable()) {
			throw ReferentielMetierException.unprocessable("ERREUR_DE_FICHIER",
					"Le fichier présente une erreur de colonnes : corrigez la correspondance avant de confirmer.");
		}
		List<LigneResultat> importables = apres.evaluation().lignes().stream()
				.filter(l -> !l.enErreur() && ("CREATION".equals(l.action()) || "MISE_A_JOUR".equals(l.action())
						|| "AJUSTEMENT".equals(l.action()))).toList();
		if (importables.isEmpty()) {
			throw ReferentielMetierException.unprocessable("AUCUNE_LIGNE_IMPORTABLE",
					"Aucune ligne à importer : toutes les lignes sont en erreur ou inchangées.");
		}
		Instant maintenant = clock.instant();
		CompteRendu compteRendu = soldes
				? appliquerSoldes(a, apres, importables, motifGlobal, jwt, (ImportSoldesService.Contexte) reeval.contexte())
				: appliquerFiches(a, apres, importables, (ImportCollaborateursService.Contexte) reeval.contexte());
		a.setStatut(ImportAnalyse.STATUT_CONFIRMEE);
		a.setConfirmeLe(maintenant);
		a.setDerniereActionLe(maintenant);
		a.setMotifGlobal(motifGlobal);
		a.setTotaux(ecrire(compteRendu));
		analyseRepository.save(a);
		ImportContenu contenu = contenuRepository.findById(id).orElseThrow(this::expire);
		contenu.setResultat(gzip(apres));
		contenuRepository.save(contenu);
		log.info("Import {} confirmé : {} ligne(s) importée(s), {} rejetée(s)", a.getTypeImport(), importables.size(),
				compteRendu.rejetees());
		return compteRendu;
	}

	/** Résultat de la réanalyse et contexte de données utilisé (entités gérées par la transaction de confirmation). */
	private record Reevaluation(ResultatStocke resultat, Object contexte) {
	}

	private Reevaluation reevaluerFiches(ImportAnalyse a, Fichier lu, ResultatStocke avant) {
		ImportCollaborateursService.Contexte ctx = collaborateursService.charger();
		List<LigneBrute> donnees = donnees(lu, avant.ligneEnTete());
		List<Colonne> colonnes = colonnesPourEvaluation(avant.colonnes());
		Evaluation e = collaborateursService.evaluer(donnees, colonnes, Boolean.TRUE.equals(a.getCreerComptes()),
				avant.comptesExistants() == null ? Set.of() : avant.comptesExistants(), ctx);
		Map<Integer, LigneResultat> precedentes = new LinkedHashMap<>();
		avant.evaluation().lignes().forEach(l -> precedentes.put(l.numero(), l));
		List<LigneResultat> lignes = new ArrayList<>();
		for (LigneResultat l : e.lignes()) {
			LigneResultat p = precedentes.get(l.numero());
			boolean change = p != null && (!p.action().equals(l.action())
					|| (p.collaborateurId() != null && !java.util.Objects.equals(p.modifieLe(), l.modifieLe())));
			// seules les lignes que l'auteur a vues importables ou inchangées peuvent « bouger » (D-I13.d)
			if (change && !"ERREUR".equals(p.action())) {
				List<Probleme> erreurs = new ArrayList<>(l.erreurs());
				erreurs.add(new Probleme("LIGNE_MODIFIEE_DEPUIS_ANALYSE", null, null,
						"La fiche a changé depuis l'analyse : relancez l'analyse."));
				lignes.add(new LigneResultat(l.numero(), l.matricule(), l.nomComplet(), "ERREUR", erreurs, l.avertissements(),
						l.modifications(), null, null, null, null, l.collaborateurId(), l.modifieLe(), l.valeurs(), false));
			} else {
				lignes.add(l);
			}
		}
		Evaluation finale = new Evaluation(lignes, avant.evaluation().erreursFichier(), avant.evaluation().avertissementsFichier(),
				totaux(lignes, false));
		return new Reevaluation(new ResultatStocke(avant.ligneEnTete(), avant.colonnes(), avant.colonnesIgnorees(), finale,
				avant.comptesExistants(), avant.avertissementsEnPlus()), ctx);
	}

	private Reevaluation reevaluerSoldes(ImportAnalyse a, Fichier lu, ResultatStocke avant, String motifGlobal) {
		ImportSoldesService.Contexte ctx = soldesService.charger();
		Evaluation e = soldesService.evaluer(donnees(lu, avant.ligneEnTete()), colonnesPourEvaluation(avant.colonnes()),
				motifGlobal, a.getAuteurFicheId(), ctx);
		return new Reevaluation(new ResultatStocke(avant.ligneEnTete(), avant.colonnes(), avant.colonnesIgnorees(),
				new Evaluation(e.lignes(), avant.evaluation().erreursFichier(), e.avertissementsFichier(), e.totaux()),
				avant.comptesExistants(), avant.avertissementsEnPlus()), ctx);
	}

	private CompteRendu appliquerFiches(ImportAnalyse a, ResultatStocke r, List<LigneResultat> importables,
			ImportCollaborateursService.Contexte ctx) {
		ImportCollaborateursService.Application app = collaborateursService.appliquer(importables, ctx);
		int inchangees = (int) r.evaluation().lignes().stream().filter(l -> "INCHANGEE".equals(l.action())).count();
		int rejetees = (int) r.evaluation().lignes().stream().filter(LigneResultat::enErreur).count();
		for (LigneResultat l : r.evaluation().lignes()) {
			if ("INCHANGEE".equals(l.action())) {
				continue;
			}
			UUID collab = l.collaborateurId() != null ? l.collaborateurId()
					: l.matricule() == null ? null : app.idsParMatricule().get(l.matricule().toLowerCase(Locale.ROOT));
			ligneRepository.save(l.enErreur()
					? new ImportLigne(a.getId(), l.numero(), l.matricule(), collab, "REJETEE", null, ecrire(erreursSansValeur(l)))
					: new ImportLigne(a.getId(), l.numero(), l.matricule(), collab, l.action(),
							ecrire(l.modifications()), null));
		}
		return new CompteRendu(a.getId(), a.getTypeImport(), app.creees(), app.misesAJour(), inchangees, rejetees,
				app.comptesDemandes(), null, avertissementsFinaux(r, importables));
	}

	private CompteRendu appliquerSoldes(ImportAnalyse a, ResultatStocke r, List<LigneResultat> importables,
			String motifGlobal, Jwt jwt, ImportSoldesService.Contexte ctx) {
		ImportSoldesService.Application app = soldesService.appliquer(importables, a.getId(), motifGlobal,
				auteurResolver.depuis(jwt), ctx);
		int inchanges = (int) r.evaluation().lignes().stream().filter(l -> "INCHANGE".equals(l.action())).count()
				+ app.inchanges();
		int rejetees = (int) r.evaluation().lignes().stream().filter(LigneResultat::enErreur).count();
		for (LigneResultat l : r.evaluation().lignes()) {
			if ("INCHANGE".equals(l.action())) {
				continue;
			}
			if (l.enErreur()) {
				ligneRepository.save(new ImportLigne(a.getId(), l.numero(), l.matricule(), l.collaborateurId(), "REJETEE", null,
						ecrire(erreursSansValeur(l))));
				continue;
			}
			BigDecimal reel = app.ecartsReels().get(l.matricule().toLowerCase(Locale.ROOT));
			if (reel == null) {
				continue; // ligne devenue égale à la confirmation : aucun mouvement
			}
			BigDecimal avantSolde = l.soldeCible().subtract(reel);
			ligneRepository.save(new ImportLigne(a.getId(), l.numero(), l.matricule(), l.collaborateurId(), "AJUSTEMENT",
					ecrire(List.of(new Modification("SOLDE", "Solde", avantSolde.toPlainString(), l.soldeCible().toPlainString()),
							new Modification("MOTIF", "Motif", "", l.motif() == null ? motifGlobal : l.motif()))), null));
		}
		return new CompteRendu(a.getId(), a.getTypeImport(), null, null, inchanges, rejetees, null, app.ajustements(),
				avertissementsFinaux(r, importables));
	}

	/** Avertissements du compte rendu : ceux du fichier, et le nombre de lignes IMPORTÉES qui en portent. */
	private static List<String> avertissementsFinaux(ResultatStocke r, List<LigneResultat> importees) {
		List<String> l = new ArrayList<>();
		r.evaluation().avertissementsFichier().forEach(p -> l.add(p.message()));
		long avertis = importees.stream().filter(x -> !x.avertissements().isEmpty()).count();
		if (avertis > 0) {
			l.add(avertis + (avertis > 1 ? " lignes importées avec avertissement." : " ligne importée avec avertissement."));
		}
		return l;
	}

	private static List<Probleme> erreursSansValeur(LigneResultat l) {
		return l.erreurs().stream().map(p -> new Probleme(p.code(), p.colonne(), null, null)).toList();
	}

	// ─── Historique (I10 à I12) ────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public PageReferentielResponse<ImportResume> historique(String type, int page, int taille) {
		validerTaille(taille);
		String t = type == null || type.isBlank() ? null : type.strip().toUpperCase(Locale.ROOT);
		if (t != null && !ImportAnalyse.TYPE_COLLABORATEURS.equals(t) && !ImportAnalyse.TYPE_SOLDES.equals(t)) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "Type d'import inconnu : " + type + ".");
		}
		Page<ImportAnalyse> p = analyseRepository.historique(t, PageRequest.of(Math.max(0, page), taille));
		return new PageReferentielResponse<>(p.getContent().stream().map(this::versResume).toList(), p.getTotalElements(),
				p.getTotalPages(), p.getNumber(), taille);
	}

	@Transactional(readOnly = true)
	public ImportResume resume(UUID importId) {
		return analyseRepository.findById(importId).filter(a -> ImportAnalyse.STATUT_CONFIRMEE.equals(a.getStatut()))
				.map(this::versResume).orElseThrow(() -> ReferentielMetierException.notFound("IMPORT_INTROUVABLE",
						"Import introuvable."));
	}

	@Transactional(readOnly = true)
	public PageReferentielResponse<LigneHistorique> lignesHistorique(UUID importId, int page, int taille) {
		resume(importId);
		validerTaille(taille);
		Page<ImportLigne> p = ligneRepository.findByImportIdOrderByNumeroLigneAsc(importId, PageRequest.of(Math.max(0, page), taille));
		List<LigneHistorique> lignes = p.getContent().stream().map(l -> new LigneHistorique(l.getNumeroLigne(), l.getMatricule(),
				l.getAction(), libelleAction(l.getAction()), changements(l.getChangements()), libellesErreurs(l.getErreurs()))).toList();
		return new PageReferentielResponse<>(lignes, p.getTotalElements(), p.getTotalPages(), p.getNumber(), taille);
	}

	private ImportResume versResume(ImportAnalyse a) {
		CompteRendu totaux = a.getTotaux() == null ? null : jsonMapper.readValue(a.getTotaux(), CompteRendu.class);
		String typeLibelle = ImportAnalyse.TYPE_SOLDES.equals(a.getTypeImport()) ? "Soldes" : "Collaborateurs";
		return new ImportResume(a.getId(), a.getTypeImport(), typeLibelle, a.getConfirmeLe(), a.getAuteurNom(), a.getNomFichier(),
				a.getCreerComptes(), a.getMotifGlobal(), totaux);
	}

	private List<ModificationVue> changements(String json) {
		if (json == null) {
			return List.of();
		}
		Modification[] m = jsonMapper.readValue(json, Modification[].class);
		return java.util.Arrays.stream(m).map(x -> new ModificationVue(x.champ(), x.champLibelle(), x.avant(), x.apres())).toList();
	}

	private List<String> libellesErreurs(String json) {
		if (json == null) {
			return List.of();
		}
		Probleme[] p = jsonMapper.readValue(json, Probleme[].class);
		return java.util.Arrays.stream(p).map(x -> LIBELLES_CODES.getOrDefault(x.code(), "Erreur")
				+ (x.colonne() == null ? "" : " (colonne « " + x.colonne() + " »)")).toList();
	}

	static final Map<String, String> LIBELLES_CODES = Map.ofEntries(
			Map.entry("MATRICULE_OBLIGATOIRE", "Matricule obligatoire"),
			Map.entry("MATRICULE_EN_DOUBLE_FICHIER", "Matricule en double dans le fichier"),
			Map.entry("MATRICULE_INCONNU", "Matricule inconnu"),
			Map.entry("CHAMP_OBLIGATOIRE", "Champ obligatoire"),
			Map.entry("COURRIEL_OBLIGATOIRE", "Courriel obligatoire"),
			Map.entry("COURRIEL_EN_DOUBLE", "Courriel déjà utilisé"),
			Map.entry("UNITE_INCONNUE", "Unité inconnue"),
			Map.entry("UNITE_INACTIVE", "Unité inactive"),
			Map.entry("UNITE_AMBIGUE", "Unité ambiguë"),
			Map.entry("VALEUR_INCONNUE", "Valeur inconnue"),
			Map.entry("PROFIL_NON_IMPORTABLE", "Profil non importable"),
			Map.entry("DATE_INVALIDE", "Date invalide"),
			Map.entry("FORMAT_INVALIDE", "Format invalide"),
			Map.entry("VALEUR_TROP_LONGUE", "Valeur trop longue"),
			Map.entry("LIGNE_MODIFIEE_DEPUIS_ANALYSE", "Fiche modifiée depuis l'analyse"),
			Map.entry("SOLDE_OBLIGATOIRE", "Solde obligatoire"),
			Map.entry("SOLDE_INVALIDE", "Solde invalide"),
			Map.entry("MOTIF_INVALIDE", "Motif invalide"),
			Map.entry("AUTO_AJUSTEMENT_INTERDIT", "Ajustement de son propre solde interdit"));

	private static String libelleAction(String action) {
		return switch (action == null ? "" : action) {
			case "CREATION" -> "Création";
			case "MISE_A_JOUR" -> "Mise à jour";
			case "INCHANGEE" -> "Inchangée";
			case "INCHANGE" -> "Inchangé";
			case "AJUSTEMENT" -> "Ajustement";
			case "REJETEE" -> "Rejetée";
			case "ERREUR" -> "Erreur";
			default -> action;
		};
	}

	// ─── Évaluation (analyse et correspondance corrigée) ───────────────────────

	/** Détecte l'en-tête, fixe la correspondance, évalue toutes les lignes. Aucune écriture. */
	private ResultatStocke evaluer(ImportAnalyse a, Fichier lu, Integer ligneEnTeteDemandee, List<ColonneChoix> choix,
			Set<String> comptesDejaConnus) {
		boolean soldes = ImportAnalyse.TYPE_SOLDES.equals(a.getTypeImport());
		ReconnaissanceEnTetes reco = new ReconnaissanceEnTetes(soldes ? List.of(ChampImport.Solde.values())
				: List.of(ChampImport.Fiche.values()));
		int idxEnTete = positionEnTete(lu, reco, ligneEnTeteDemandee);
		LigneBrute entete = lu.lignes().get(idxEnTete);
		List<LigneBrute> donnees = lu.lignes().subList(idxEnTete + 1, lu.lignes().size());
		if (lu.tronque() || donnees.size() > LectureFichier.LIGNES_DONNEES_MAX) {
			throw ReferentielMetierException.unprocessable("TROP_DE_LIGNES",
					"Le fichier dépasse " + LectureFichier.LIGNES_DONNEES_MAX + " lignes de données.");
		}
		List<Colonne> colonnes = colonnes(entete, reco, choix);
		List<String> ignorees = colonnes.stream().filter(c -> c.champ() == null && !c.enTete().isBlank())
				.map(Colonne::enTete).toList();
		List<Probleme> erreursFichier = erreursFichier(colonnes, soldes);
		List<Colonne> pourEvaluation = colonnesPourEvaluation(colonnes);
		Evaluation e;
		Set<String> comptes = comptesDejaConnus == null ? Set.of() : comptesDejaConnus;
		List<Probleme> avertissements = new ArrayList<>();
		if (soldes) {
			ImportSoldesService.Contexte ctx = soldesService.charger();
			e = soldesService.evaluer(donnees, pourEvaluation, a.getMotifGlobal(), a.getAuteurFicheId(), ctx);
		} else {
			ImportCollaborateursService.Contexte ctx = collaborateursService.charger();
			if (Boolean.TRUE.equals(a.getCreerComptes())) {
				try {
					comptes = identiteClient.identifiantsExistants(collaborateursService.matriculesACreer(donnees, pourEvaluation, ctx));
				} catch (IdentiteInterneClient.IdentiteIndisponibleException ex) {
					avertissements.add(Probleme.fichier("VERIFICATION_COMPTES_INDISPONIBLE",
							"La vérification des comptes existants est indisponible : les doublons de compte seront traités à la création."));
				}
			}
			e = collaborateursService.evaluer(donnees, pourEvaluation, Boolean.TRUE.equals(a.getCreerComptes()), comptes, ctx);
		}
		List<Probleme> avertFichier = new ArrayList<>(e.avertissementsFichier());
		avertFichier.addAll(avertissements);
		a.setLigneEnTete(entete.numero());
		Evaluation finale = new Evaluation(e.lignes(), erreursFichier, avertFichier, e.totaux());
		return new ResultatStocke(entete.numero(), colonnes, ignorees, finale, comptes, List.of());
	}

	private static int positionEnTete(Fichier lu, ReconnaissanceEnTetes reco, Integer demandee) {
		List<LigneBrute> lignes = lu.lignes();
		if (demandee != null) {
			for (int i = 0; i < lignes.size(); i++) {
				if (lignes.get(i).numero() == demandee) {
					return i;
				}
			}
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "La ligne d'en-tête demandée n'existe pas.");
		}
		for (int i = 0; i < Math.min(lignes.size(), ReconnaissanceEnTetes.LIGNES_EXAMINEES); i++) {
			if (reco.compterReconnus(lignes.get(i).cellules()) >= ReconnaissanceEnTetes.MINIMUM_RECONNUS) {
				return i;
			}
		}
		throw ReferentielMetierException.unprocessable("EN_TETES_NON_RECONNUS",
				"Aucune ligne d'en-têtes reconnue dans les " + ReconnaissanceEnTetes.LIGNES_EXAMINEES
						+ " premières lignes : utilisez le modèle.");
	}

	/** Correspondance automatique, ou choisie par l'utilisateur (colonne absente du choix = ignorée). */
	private static List<Colonne> colonnes(LigneBrute entete, ReconnaissanceEnTetes reco, List<ColonneChoix> choix) {
		List<Colonne> brutes = new ArrayList<>();
		for (int i = 0; i < entete.cellules().size(); i++) {
			String texte = entete.cellules().get(i) == null ? "" : entete.cellules().get(i).strip();
			String champ = null;
			if (choix == null) {
				champ = texte.isEmpty() ? null : reco.reconnaitre(texte).map(ChampImport::name).orElse(null);
			} else {
				final int index = i;
				for (ColonneChoix c : choix) {
					if (c.index() == index && c.champ() != null && !c.champ().isBlank()) {
						if (reco.parNom(c.champ()) == null) {
							throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "Champ inconnu : " + c.champ() + ".");
						}
						champ = c.champ();
					}
				}
			}
			brutes.add(new Colonne(i, texte, champ, champ == null ? ImportModeles.IGNOREE : ImportModeles.RECONNUE));
		}
		Map<String, Long> nombre = brutes.stream().filter(c -> c.champ() != null)
				.collect(Collectors.groupingBy(Colonne::champ, Collectors.counting()));
		List<Colonne> r = new ArrayList<>();
		for (Colonne c : brutes) {
			r.add(c.champ() != null && nombre.get(c.champ()) > 1
					? new Colonne(c.index(), c.enTete(), c.champ(), ImportModeles.EN_DOUBLE) : c);
		}
		return r;
	}

	private List<Probleme> erreursFichier(List<Colonne> colonnes, boolean soldes) {
		List<Probleme> erreurs = new ArrayList<>();
		Map<String, List<Colonne>> doubles = colonnes.stream().filter(c -> ImportModeles.EN_DOUBLE.equals(c.etat()))
				.collect(Collectors.groupingBy(Colonne::champ, LinkedHashMap::new, Collectors.toList()));
		ReconnaissanceEnTetes reco = new ReconnaissanceEnTetes(soldes ? List.of(ChampImport.Solde.values())
				: List.of(ChampImport.Fiche.values()));
		doubles.forEach((champ, liste) -> erreurs.add(Probleme.fichier("COLONNE_EN_DOUBLE", "Les colonnes "
				+ liste.stream().map(c -> "« " + c.enTete() + " »").collect(Collectors.joining(" et "))
				+ " désignent toutes deux le champ « " + reco.parNom(champ).libelle().toLowerCase(Locale.ROOT) + " ».")));
		boolean matricule = colonnes.stream().anyMatch(c -> "MATRICULE".equals(c.champ()) && !ImportModeles.EN_DOUBLE.equals(c.etat()));
		if (!matricule) {
			erreurs.add(Probleme.fichier("COLONNE_MATRICULE_ABSENTE", "Aucune colonne ne désigne le matricule."));
		}
		if (soldes && colonnes.stream().noneMatch(c -> "SOLDE".equals(c.champ()) && !ImportModeles.EN_DOUBLE.equals(c.etat()))) {
			erreurs.add(Probleme.fichier("COLONNE_SOLDE_ABSENTE", "Aucune colonne ne désigne le solde."));
		}
		return erreurs;
	}

	/** Les colonnes ambiguës (en double) sont laissées de côté : les lignes restent affichées avec les colonnes sûres. */
	private static List<Colonne> colonnesPourEvaluation(List<Colonne> colonnes) {
		return colonnes.stream().map(c -> ImportModeles.EN_DOUBLE.equals(c.etat())
				? new Colonne(c.index(), c.enTete(), null, ImportModeles.IGNOREE) : c).toList();
	}

	private static List<LigneBrute> donnees(Fichier lu, Integer ligneEnTete) {
		List<LigneBrute> lignes = lu.lignes();
		for (int i = 0; i < lignes.size(); i++) {
			if (lignes.get(i).numero() == ligneEnTete) {
				return lignes.subList(i + 1, lignes.size());
			}
		}
		return List.of();
	}

	private static Totaux totaux(List<LigneResultat> lignes, boolean soldes) {
		int c = 0;
		int m = 0;
		int i = 0;
		int e = 0;
		int w = 0;
		for (LigneResultat l : lignes) {
			switch (l.action()) {
				case "CREATION" -> c++;
				case "MISE_A_JOUR" -> m++;
				case "INCHANGEE" -> i++;
				default -> e++;
			}
			if (!l.avertissements().isEmpty()) {
				w++;
			}
		}
		return new Totaux(lignes.size(), c, m, i, null, e, w);
	}

	// ─── Persistance et mappage ────────────────────────────────────────────────

	private void persister(ImportAnalyse a, Fichier lu, ResultatStocke r, boolean nouvelle) {
		new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
			a.setDerniereActionLe(clock.instant());
			a.setCorrespondance(ecrire(r.colonnes()));
			a.setErreursFichier(ecrire(r.evaluation().erreursFichier()));
			a.setTotaux(ecrire(r.evaluation().totaux()));
			analyseRepository.save(a);
			if (nouvelle) {
				contenuRepository.save(new ImportContenu(a.getId(), gzip(lu), gzip(r)));
			} else {
				ImportContenu c = contenuRepository.findById(a.getId()).orElseThrow(this::expire);
				c.setResultat(gzip(r));
				contenuRepository.save(c);
			}
		});
	}

	private Analyse versAnalyse(ImportAnalyse a, ResultatStocke r, int page) {
		boolean soldes = ImportAnalyse.TYPE_SOLDES.equals(a.getTypeImport());
		ReconnaissanceEnTetes reco = new ReconnaissanceEnTetes(soldes ? List.of(ChampImport.Solde.values())
				: List.of(ChampImport.Fiche.values()));
		List<ColonneVue> colonnes = r.colonnes().stream().map(c -> new ColonneVue(c.index(), c.enTete(), c.champ(),
				c.champ() == null ? null : reco.parNom(c.champ()).libelle(), c.etat())).toList();
		List<ChampDisponible> champs = reco.champs().stream()
				.map(c -> new ChampDisponible(c.name(), c.libelle(), c.obligatoireCreation())).toList();
		Totaux t = r.evaluation().totaux();
		TotauxVue totaux = soldes
				? new TotauxVue(t.lignes(), null, null, t.inchangees(), t.ajustements(), t.erreurs(), t.avertissements())
				: new TotauxVue(t.lignes(), t.creations(), t.misesAJour(), t.inchangees(), null, t.erreurs(), t.avertissements());
		PageReferentielResponse<LigneAnalyse> lignes = page < 0 ? null
				: pageLignes(r.evaluation().lignes(), a.getTypeImport(), page, PAGE_TAILLE_DEFAUT, false);
		return new Analyse(a.getId(), a.getTypeImport(), a.getStatut(),
				a.getDerniereActionLe().plus(Duration.ofMinutes(expirationMinutes)), a.getNomFichier(), a.getCreerComptes(),
				a.getMotifGlobal(), a.getLigneEnTete(), colonnes, champs, vues(r.evaluation().erreursFichier()),
				r.colonnesIgnorees(), vues(r.evaluation().avertissementsFichier()), totaux, r.evaluation().confirmable(), lignes);
	}

	private PageReferentielResponse<LigneAnalyse> pageLignes(List<LigneResultat> toutes, String type, int page, int taille,
			boolean erreursSeulement) {
		List<LigneResultat> filtrees = erreursSeulement ? toutes.stream().filter(LigneResultat::enErreur).toList() : toutes;
		int p = Math.max(0, page);
		int from = Math.min(filtrees.size(), p * taille);
		int to = Math.min(filtrees.size(), from + taille);
		int pages = (int) Math.ceil(filtrees.size() / (double) taille);
		List<LigneAnalyse> contenu = filtrees.subList(from, to).stream().map(l -> versLigne(l, type)).toList();
		return new PageReferentielResponse<>(contenu, filtrees.size(), pages, p, taille);
	}

	private static LigneAnalyse versLigne(LigneResultat l, String type) {
		boolean soldes = ImportAnalyse.TYPE_SOLDES.equals(type);
		return new LigneAnalyse(l.numero(), l.matricule(), l.nomComplet(), l.action(), libelleAction(l.action()), vues(l.erreurs()),
				vues(l.avertissements()),
				l.modifications().stream().map(m -> new ModificationVue(m.champ(), m.champLibelle(), m.avant(), m.apres())).toList(),
				soldes ? l.soldeActuel() : null, soldes ? l.soldeCible() : null, soldes ? l.ecart() : null,
				soldes ? l.motif() : null);
	}

	private static List<ProblemeVue> vues(List<Probleme> l) {
		return l.stream().map(p -> new ProblemeVue(p.code(), p.colonne(), p.valeur(), p.message())).toList();
	}

	// ─── Accès, états, stockage ────────────────────────────────────────────────

	/**
	 * Ouvre l'analyse de son auteur (404 pour tout autre utilisateur, même RH / ADMIN) ; refuse une analyse expirée.
	 * {@code toucher} : met à jour la dernière action.
	 */
	private ImportAnalyse ouvrir(UUID id, Jwt jwt, boolean toucher) {
		return ouvrir(id, jwt, toucher, true);
	}

	private ImportAnalyse ouvrir(UUID id, Jwt jwt, boolean toucher, boolean verifierExpiration) {
		return ouvrir(id, jwt, toucher, verifierExpiration, false);
	}

	private ImportAnalyse ouvrir(UUID id, Jwt jwt, boolean toucher, boolean verifierExpiration, boolean verrouiller) {
		UUID compte = exigerCompte(auteurResolver.depuis(jwt));
		ImportAnalyse a = (verrouiller ? analyseRepository.findByIdForUpdate(id) : analyseRepository.findById(id))
				.filter(x -> compte.equals(x.getAuteurCompteId())).orElseThrow(this::introuvable);
		if (ImportAnalyse.STATUT_ANALYSEE.equals(a.getStatut()) && clock.instant().isAfter(
				a.getDerniereActionLe().plus(Duration.ofMinutes(expirationMinutes))
						.plus(Duration.ofHours(ECHEANCE_ANALYSE_NON_CONFIRMEE_HEURES)))) {
			throw introuvable(); // la purge n'a pas encore tourné : le contrôle à la lecture suffit (ADR § 6)
		}
		if (verifierExpiration && ImportAnalyse.STATUT_ANALYSEE.equals(a.getStatut()) && estExpiree(a)) {
			throw expire();
		}
		if (toucher) {
			a.setDerniereActionLe(clock.instant());
			analyseRepository.save(a);
		}
		return a;
	}

	private void controlerEtat(ImportAnalyse a) {
		if (ImportAnalyse.STATUT_CONFIRMEE.equals(a.getStatut())) {
			throw ReferentielMetierException.conflict("IMPORT_DEJA_CONFIRME", "Cet import est déjà confirmé.");
		}
		if (estExpiree(a)) {
			throw expire();
		}
	}

	private boolean estExpiree(ImportAnalyse a) {
		return clock.instant().isAfter(a.getDerniereActionLe().plus(Duration.ofMinutes(expirationMinutes)));
	}

	private ReferentielMetierException introuvable() {
		return ReferentielMetierException.notFound("ANALYSE_INTROUVABLE", "Analyse introuvable.");
	}

	private ReferentielMetierException expire() {
		return ReferentielMetierException.conflict("IMPORT_EXPIRE", "L'analyse a expiré, déposez à nouveau le fichier.");
	}

	private static UUID exigerCompte(AuteurParametre auteur) {
		if (auteur.compteId() == null) {
			throw ReferentielMetierException.forbidden("COMPTE_INTROUVABLE", "Le compte de l'utilisateur est introuvable.");
		}
		return auteur.compteId();
	}

	private static String nomSansChemin(String nom) {
		if (nom == null) {
			return null;
		}
		String n = nom.replace('\\', '/');
		n = n.substring(n.lastIndexOf('/') + 1);
		return n.length() > 255 ? n.substring(0, 255) : n;
	}

	private static void validerTaille(int taille) {
		if (taille < 1 || taille > PAGE_TAILLE_MAX) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE",
					"La taille de page doit être comprise entre 1 et " + PAGE_TAILLE_MAX + ".");
		}
	}

	private Fichier lireContenu(ImportAnalyse a) {
		ImportContenu c = contenuRepository.findById(a.getId()).orElseThrow(this::expire);
		return degzip(c.getCellules(), Fichier.class);
	}

	private ResultatStocke lireResultat(ImportAnalyse a) {
		ImportContenu c = contenuRepository.findById(a.getId()).orElseThrow(this::expire);
		return degzip(c.getResultat(), ResultatStocke.class);
	}

	private String ecrire(Object o) {
		return jsonMapper.writeValueAsString(o);
	}

	private byte[] gzip(Object o) {
		ByteArrayOutputStream sortie = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(sortie)) {
			gz.write(jsonMapper.writeValueAsString(o).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException("Compression impossible", e);
		}
		return sortie.toByteArray();
	}

	private <T> T degzip(byte[] octets, Class<T> type) {
		try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(octets))) {
			return jsonMapper.readValue(new String(gz.readAllBytes(), StandardCharsets.UTF_8), type);
		} catch (IOException e) {
			throw new IllegalStateException("Décompression impossible", e);
		}
	}

	/** Pour les tests : ensemble des matricules en minuscules d'une évaluation. */
	static Set<String> matricules(Evaluation e) {
		return e.lignes().stream().map(LigneResultat::matricule).filter(java.util.Objects::nonNull)
				.map(m -> m.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(HashSet::new));
	}
}
