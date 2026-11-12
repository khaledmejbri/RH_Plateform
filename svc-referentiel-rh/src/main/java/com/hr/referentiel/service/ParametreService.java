package com.hr.referentiel.service;

import com.hr.referentiel.domain.ActionJournalParametre;
import com.hr.referentiel.domain.NatureParametre;
import com.hr.referentiel.domain.ReferentielParametre;
import com.hr.referentiel.domain.ValeursInitialesReferentiel;
import com.hr.referentiel.dto.ParametreDto;
import com.hr.referentiel.entity.ParametreJournal;
import com.hr.referentiel.entity.ParametreValeur;
import com.hr.referentiel.repository.ParametreJournalRepository;
import com.hr.referentiel.repository.ParametreValeurRepository;
import com.hr.referentiel.service.ParametresCache.Instantane;
import com.hr.referentiel.service.ParametresCache.ParametreVue;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.hr.referentiel.dto.ParametreDto.*;

/**
 * Paramétrage des référentiels du service (ADR-UAT-lot3 § 2 à § 7) : création, modification, désactivation, ordre,
 * suppression si jamais utilisée, journal. Les règles communes (D-R02) sont ici ; les règles propres à un
 * référentiel (SLA d'un type de document, pièce jointe d'un type de congé) restent localisées dans les méthodes
 * {@code valider*} / {@code appliquer*}.
 */
@Service
public class ParametreService {

	public static final int LIBELLE_MIN = 2;
	public static final int LIBELLE_MAX = 80;
	public static final int DESCRIPTION_MAX = 300;
	public static final int SLA_MIN = 1;
	public static final int SLA_MAX = 720;
	public static final int ORDRE_MAX = 9999;
	public static final int JOURNAL_TAILLE_MAX = 100;

	/** Palette fixe (§ 5) ; le rendu AA est à la charge des fronts. */
	public static final Set<String> PALETTE = Set.of(
			"NEUTRE", "BLEU", "CYAN", "VERT", "AMBRE", "ORANGE", "ROUGE", "VIOLET");

	private final ParametreValeurRepository repository;
	private final ParametreJournalRepository journalRepository;
	private final ParametresCache cache;
	private final ParametreUtilisationCompteur compteur;
	private final JsonMapper jsonMapper;

	public ParametreService(ParametreValeurRepository repository, ParametreJournalRepository journalRepository,
			ParametresCache cache, ParametreUtilisationCompteur compteur, JsonMapper jsonMapper) {
		this.repository = repository;
		this.journalRepository = journalRepository;
		this.cache = cache;
		this.compteur = compteur;
		this.jsonMapper = jsonMapper;
	}

	// ─── Lecture administrative ─────────────────────────────────────────────────

	public List<DescripteurReferentiel> descripteurs() {
		List<DescripteurReferentiel> liste = new ArrayList<>();
		for (ReferentielParametre r : ReferentielParametre.values()) {
			boolean valeur = !r.estComportement();
			liste.add(new DescripteurReferentiel(r.name(), r.getLibelle(), r.getDomaine(), r.getNature().name(),
					new Operations(valeur, valeur, valeur),
					r.getAttributs().stream().map(a -> new AttributVue(a.nom(), a.type())).toList()));
		}
		return liste;
	}

	@Transactional(readOnly = true)
	public List<ValeurResponse> valeurs(String referentielBrut) {
		ReferentielParametre ref = exigerReferentiel(referentielBrut);
		return versReponses(ref, repository.findByReferentielOrderByOrdreAscLibelleAsc(ref.name()));
	}

	@Transactional(readOnly = true)
	public PageJournal journal(String referentielBrut, int page, int taille) {
		ReferentielParametre ref = exigerReferentiel(referentielBrut);
		int t = Math.min(JOURNAL_TAILLE_MAX, Math.max(1, taille));
		int p = Math.max(0, page);
		Page<ParametreJournal> resultat = journalRepository.findByReferentielOrderByHorodatageDesc(
				ref.name(), PageRequest.of(p, t));
		List<JournalEntree> contenu = resultat.getContent().stream().map(this::versEntree).toList();
		return new PageJournal(contenu, p, t, resultat.getTotalElements());
	}

	/** Le service référentiel n'a pas de réglage numérique : liste vide (contrat commun aux trois services). */
	public List<Reglage> reglages() {
		return List.of();
	}

	public PageJournal journalReglages(int page, int taille) {
		return new PageJournal(List.of(), Math.max(0, page), Math.min(JOURNAL_TAILLE_MAX, Math.max(1, taille)), 0);
	}

	public Reglage modifierReglage(String cle) {
		throw ReferentielMetierException.notFound("REGLAGE_INCONNU", "Réglage inconnu : " + cle + ".");
	}

	// ─── Libellés (lecture simple, tout utilisateur authentifié) ────────────────────

	/**
	 * Table de libellés servie aux fronts. Non back-office : valeurs actives des référentiels de la liste blanche.
	 * Back-office : tout ({@code actifsSeulement} filtre les actives).
	 */
	public Libelles libelles(boolean backOffice, boolean actifsSeulement) {
		Instantane instantane = cache.instantane();
		Map<String, List<LibelleVue>> resultat = new LinkedHashMap<>();
		for (ReferentielParametre r : ReferentielParametre.values()) {
			if (!backOffice && !r.isLisiblePartout()) {
				continue;
			}
			boolean seulementActifs = !backOffice || actifsSeulement;
			List<LibelleVue> vues = instantane.liste(r.name()).stream()
					.filter(v -> !seulementActifs || v.actif())
					.map(v -> new LibelleVue(v.code(), v.libelle(), v.description(), v.couleur(), v.ordre(),
							v.actif(), v.slaHeures(), v.pieceJointeObligatoire()))
					.toList();
			resultat.put(r.name(), vues);
		}
		String audience = (backOffice ? "B" : "U") + (actifsSeulement ? "1" : "0");
		return new Libelles("W/\"" + empreinte(instantane.empreinte() + audience) + "\"", resultat);
	}

	// ─── Écriture ───────────────────────────────────────────────────────────────

	@Transactional
	public ValeurResponse creer(String referentielBrut, ValeurRequest req, AuteurParametre auteur) {
		ReferentielParametre ref = exigerReferentiel(referentielBrut);
		if (ref.estComportement()) {
			throw ReferentielMetierException.methodNotAllowed("PARAMETRE_COMPORTEMENT",
					"Cette liste est pilotée par une règle du serveur : on ne peut pas y ajouter de valeur.");
		}
		String libelle = validerLibelle(req.libelle());
		String description = validerDescription(req.description());
		String couleur = validerCouleur(req.couleur());
		String normalise = NormalisationLibelle.normaliser(libelle);
		if (repository.findByReferentielAndLibelleNormalise(ref.name(), normalise).isPresent()) {
			throw libelleEnDouble(libelle);
		}
		List<ParametreValeur> existants = repository.findByReferentielOrderByOrdreAscLibelleAsc(ref.name());
		Set<String> codesPris = existants.stream().map(ParametreValeur::getCode).collect(Collectors.toSet());

		ParametreValeur p = new ParametreValeur();
		p.setId(UUID.randomUUID());
		p.setReferentiel(ref.name());
		p.setCode(CodeParametre.generer(libelle, codesPris::contains));
		p.setNature(ref.getNature());
		p.setLibelle(libelle);
		p.setLibelleNormalise(normalise);
		p.setDescription(description);
		p.setCouleur(couleur);
		p.setActif(true);
		p.setSysteme(false);
		p.setOrdre(req.ordre() != null ? validerOrdre(req.ordre())
				: existants.stream().mapToInt(ParametreValeur::getOrdre).max().orElse(0) + 1);
		if (ref == ReferentielParametre.TYPE_DOCUMENT) {
			p.setSlaHeures(validerSla(req.slaHeures(), true));
		}
		if (ref == ReferentielParametre.TYPE_CONGE) {
			p.setPieceJointeObligatoire(Boolean.TRUE.equals(req.pieceJointeObligatoire()));
			p.setPieceJointeVerrouillee(false);
		}
		Instant maintenant = Instant.now();
		p.setCreeLe(maintenant);
		p.setModifieLe(maintenant);
		p.setModifieParCompteId(auteur.compteId());
		ParametreValeur enregistre = repository.saveAndFlush(p);
		tracer(ref, enregistre.getCode(), ActionJournalParametre.CREATION, null, etat(enregistre), auteur);
		cache.invaliderApresCommit();
		return versReponse(ref, enregistre, 0L);
	}

	/**
	 * {@code version} {@code null} : pas de contrôle de concurrence (façade des anciens endpoints seulement).
	 * Les champs {@code null} de la requête laissent la valeur inchangée ; une chaîne vide efface description et couleur.
	 * Le code n'est jamais modifiable : un code éventuel dans le corps est ignoré.
	 */
	@Transactional
	public ValeurResponse modifier(String referentielBrut, String code, ValeurRequest req, Long version,
			AuteurParametre auteur) {
		ReferentielParametre ref = exigerReferentiel(referentielBrut);
		ParametreValeur p = exiger(ref, code);
		verifierVersion(p, version);
		Map<String, Object> avant = etat(p);

		if (req.libelle() != null) {
			String libelle = validerLibelle(req.libelle());
			String normalise = NormalisationLibelle.normaliser(libelle);
			Optional<ParametreValeur> autre = repository.findByReferentielAndLibelleNormalise(ref.name(), normalise);
			if (autre.isPresent() && !autre.get().getId().equals(p.getId())) {
				throw libelleEnDouble(libelle);
			}
			p.setLibelle(libelle);
			p.setLibelleNormalise(normalise);
		}
		if (req.description() != null) {
			p.setDescription(validerDescription(req.description()));
		}
		if (req.couleur() != null) {
			p.setCouleur(validerCouleur(req.couleur()));
		}
		if (req.ordre() != null) {
			p.setOrdre(validerOrdre(req.ordre()));
		}
		if (ref == ReferentielParametre.TYPE_DOCUMENT && req.slaHeures() != null) {
			p.setSlaHeures(validerSla(req.slaHeures(), false));
		}
		if (ref == ReferentielParametre.TYPE_CONGE && req.pieceJointeObligatoire() != null) {
			if (Boolean.TRUE.equals(p.getPieceJointeVerrouillee()) && !req.pieceJointeObligatoire()) {
				throw ReferentielMetierException.unprocessable("PIECE_JOINTE_VERROUILLEE",
						"La pièce jointe est toujours obligatoire pour « " + p.getLibelle() + " » (règle du § 6.2).");
			}
			p.setPieceJointeObligatoire(req.pieceJointeObligatoire());
		}
		Map<String, Object> apres = etat(p);
		if (avant.equals(apres)) {
			return versReponse(ref, p, null);
		}
		p.setModifieLe(Instant.now());
		p.setModifieParCompteId(auteur.compteId());
		ParametreValeur enregistre = repository.saveAndFlush(p);
		tracer(ref, enregistre.getCode(), ActionJournalParametre.MODIFICATION, avant, etat(enregistre), auteur);
		cache.invaliderApresCommit();
		return versReponse(ref, enregistre, null);
	}

	@Transactional
	public ValeurResponse desactiver(String referentielBrut, String code, Long version, AuteurParametre auteur) {
		ReferentielParametre ref = exigerReferentiel(referentielBrut);
		if (ref.estComportement()) {
			throw ReferentielMetierException.methodNotAllowed("PARAMETRE_COMPORTEMENT",
					"Cette valeur est pilotée par une règle du serveur : elle ne peut pas être désactivée.");
		}
		ParametreValeur p = exiger(ref, code);
		if (p.isSysteme()) {
			throw ReferentielMetierException.conflict("PARAMETRE_SYSTEME",
					"« " + p.getLibelle() + " » est une valeur système : elle ne peut pas être désactivée.");
		}
		verifierVersion(p, version);
		if (!p.isActif()) {
			return versReponse(ref, p, null);
		}
		Map<String, Object> avant = etat(p);
		p.setActif(false);
		return enregistrerEtTracer(ref, p, avant, ActionJournalParametre.DESACTIVATION, auteur);
	}

	@Transactional
	public ValeurResponse reactiver(String referentielBrut, String code, Long version, AuteurParametre auteur) {
		ReferentielParametre ref = exigerReferentiel(referentielBrut);
		if (ref.estComportement()) {
			throw ReferentielMetierException.methodNotAllowed("PARAMETRE_COMPORTEMENT",
					"Cette valeur est pilotée par une règle du serveur : elle est toujours active.");
		}
		ParametreValeur p = exiger(ref, code);
		verifierVersion(p, version);
		if (p.isActif()) {
			return versReponse(ref, p, null);
		}
		Map<String, Object> avant = etat(p);
		p.setActif(true);
		return enregistrerEtTracer(ref, p, avant, ActionJournalParametre.REACTIVATION, auteur);
	}

	/** Réordonne toute la liste : {@code codes} doit contenir exactement les codes du référentiel (D-R02.f). */
	@Transactional
	public List<ValeurResponse> reordonner(String referentielBrut, List<String> codes, AuteurParametre auteur) {
		ReferentielParametre ref = exigerReferentiel(referentielBrut);
		List<ParametreValeur> toutes = repository.findByReferentielOrderByOrdreAscLibelleAsc(ref.name());
		List<String> demandes = codes == null ? List.of() : codes.stream()
				.map(c -> c == null ? "" : c.trim().toUpperCase(Locale.ROOT)).toList();
		Set<String> attendus = toutes.stream().map(ParametreValeur::getCode).collect(Collectors.toSet());
		if (demandes.size() != attendus.size() || !attendus.equals(new HashSet<>(demandes))) {
			throw ReferentielMetierException.badRequest("ORDRE_INCOMPLET",
					"La liste doit contenir une fois chacune des " + attendus.size() + " valeurs du référentiel.");
		}
		Map<String, ParametreValeur> parCode = toutes.stream()
				.collect(Collectors.toMap(ParametreValeur::getCode, p -> p));
		boolean modifie = false;
		for (int i = 0; i < demandes.size(); i++) {
			ParametreValeur p = parCode.get(demandes.get(i));
			int nouveau = i + 1;
			if (p.getOrdre() != nouveau) {
				Map<String, Object> avant = Map.of("ordre", p.getOrdre());
				p.setOrdre(nouveau);
				p.setModifieLe(Instant.now());
				p.setModifieParCompteId(auteur.compteId());
				tracer(ref, p.getCode(), ActionJournalParametre.ORDRE, avant, Map.of("ordre", nouveau), auteur);
				modifie = true;
			}
		}
		if (modifie) {
			repository.saveAllAndFlush(toutes);
			cache.invaliderApresCommit();
		}
		return versReponses(ref, repository.findByReferentielOrderByOrdreAscLibelleAsc(ref.name()));
	}

	/**
	 * Suppression d'une valeur jamais utilisée (D-R02.e). La ligne est verrouillée : une demande qui prend la valeur
	 * au même instant pose sa marque par un UPDATE sur cette ligne et attend la fin de la suppression.
	 */
	@Transactional
	public void supprimer(String referentielBrut, String code, Long version, AuteurParametre auteur) {
		ReferentielParametre ref = exigerReferentiel(referentielBrut);
		if (ref.estComportement()) {
			throw ReferentielMetierException.methodNotAllowed("PARAMETRE_COMPORTEMENT",
					"Cette valeur est pilotée par une règle du serveur : elle ne peut pas être supprimée.");
		}
		ParametreValeur p = repository.findForUpdate(ref.name(), normaliserCode(code))
				.orElseThrow(() -> introuvable(ref, code));
		if (p.isSysteme()) {
			throw ReferentielMetierException.conflict("PARAMETRE_SYSTEME",
					"« " + p.getLibelle() + " » est une valeur système : elle ne peut pas être supprimée.");
		}
		verifierVersion(p, version);
		long utilisations = compteur.compter(ref.name(), p.getCode());
		if (p.getPremiereUtilisationLe() != null || utilisations > 0) {
			String message = utilisations > 0
					? "La valeur « " + p.getLibelle() + " » est utilisée " + utilisations
							+ " fois : vous pouvez la désactiver."
					: "La valeur « " + p.getLibelle() + " » a déjà été utilisée : vous pouvez la désactiver.";
			throw new ReferentielMetierException("PARAMETRE_UTILISE", message, 409, Map.of(
					"utilisations", utilisations, "deja_utilisee", true));
		}
		Map<String, Object> avant = etat(p);
		repository.delete(p);
		repository.flush();
		tracer(ref, p.getCode(), ActionJournalParametre.SUPPRESSION, avant, null, auteur);
		cache.invaliderApresCommit();
	}

	// ─── Usage par les règles métier (même transaction que l'objet créé) ───────────

	/** Lit une ligne en base (décision d'écriture : jamais le cache). Le code est normalisé en majuscules. */
	@Transactional(readOnly = true)
	public Optional<ParametreVue> lire(String referentiel, String code) {
		if (code == null || code.isBlank()) {
			return Optional.empty();
		}
		return repository.findByReferentielAndCode(referentiel, normaliserCode(code)).map(ParametreVue::de);
	}

	/** Valeur existante et active, sinon l'erreur fournie (inconnue ou inactive). */
	@Transactional(readOnly = true)
	public ParametreVue exigerActive(String referentiel, String code, Supplier<RuntimeException> siInconnue,
			Supplier<RuntimeException> siInactive) {
		ParametreVue v = lire(referentiel, code).orElseThrow(siInconnue);
		if (!v.actif()) {
			throw siInactive.get();
		}
		return v;
	}

	/** Pose la marque collante « déjà utilisée » (§ 6), dans la transaction de l'objet qui utilise la valeur. */
	@Transactional
	public void marquerUtilisee(String referentiel, String code) {
		if (code == null || code.isBlank()) {
			return;
		}
		repository.marquerUtilisee(referentiel, normaliserCode(code), Instant.now());
	}

	/**
	 * Appel interne : valide des codes ciblés par un autre service (template technique) et pose leur marque.
	 * Seuls les référentiels partagés avec l'évaluation sont acceptés ; le reste est « inconnu ».
	 */
	@Transactional
	public UtiliserResponse utiliser(List<Reference> references) {
		List<Reference> valides = new ArrayList<>();
		List<Reference> inactifs = new ArrayList<>();
		List<Reference> inconnus = new ArrayList<>();
		Instant maintenant = Instant.now();
		for (Reference r : references == null ? List.<Reference>of() : references) {
			if (r == null || r.referentiel() == null || r.code() == null) {
				inconnus.add(r == null ? new Reference(null, null) : r);
				continue;
			}
			Optional<ReferentielParametre> ref = ReferentielParametre.parse(r.referentiel());
			if (ref.isEmpty() || !(ref.get() == ReferentielParametre.FAMILLE_METIER
					|| ref.get() == ReferentielParametre.NIVEAU_SENIORITE)) {
				inconnus.add(r);
				continue;
			}
			String code = ref.get() == ReferentielParametre.NIVEAU_SENIORITE
					? ValeursInitialesReferentiel.resoudreAliasNiveau(r.code()) : normaliserCode(r.code());
			Optional<ParametreValeur> p = repository.findByReferentielAndCode(ref.get().name(), code);
			if (p.isEmpty()) {
				inconnus.add(r);
			} else if (!p.get().isActif()) {
				inactifs.add(new Reference(ref.get().name(), code));
			} else {
				repository.marquerUtilisee(ref.get().name(), code, maintenant);
				valides.add(new Reference(ref.get().name(), code));
			}
		}
		return new UtiliserResponse(valides, inactifs, inconnus);
	}

	// ─── Libellés pour les messages et réponses métier ──────────────────────────────

	/** Instantané courant (cache 30 s) : lecture sans accès base. */
	public Instantane instantane() {
		return cache.instantane();
	}

	/** Libellé courant d'un code, ou {@code null} s'il n'existe pas dans le référentiel. */
	public String libelle(String referentiel, String code) {
		return code == null ? null : cache.instantane().libelle(referentiel, code);
	}

	/** Libellé affiché quand un code n'existe plus (ou jamais) dans le référentiel. */
	public static final String LIBELLE_INCONNU = "Valeur non reconnue";

	/**
	 * Libellé courant d'un code (y compris désactivé) lu dans l'instantané ; « Valeur non reconnue » si le code est
	 * inconnu. {@code null} seulement si le code est vide.
	 */
	public String libelleOuInconnu(String referentiel, String code) {
		if (code == null || code.isBlank()) {
			return null;
		}
		String l = cache.instantane().libelle(referentiel, code);
		return l != null ? l : LIBELLE_INCONNU;
	}

	/** Libellé courant, sinon libellé de repli lisible (jamais le code brut seul). */
	public String libelleOuDefaut(String referentiel, String code) {
		if (code == null || code.isBlank()) {
			return null;
		}
		String l = cache.instantane().libelle(referentiel, code);
		return l != null ? l : ValeursInitialesReferentiel.libelleParDefaut(code);
	}

	/** Libellé commençant par une minuscule (en préservant les sigles), pour l'insérer dans une phrase. */
	public static String enMinuscule(String libelle) {
		if (libelle == null || libelle.isEmpty()) {
			return libelle;
		}
		if (libelle.length() > 1 && Character.isUpperCase(libelle.charAt(1))) {
			return libelle;
		}
		return Character.toLowerCase(libelle.charAt(0)) + libelle.substring(1);
	}

	// ─── Interne ────────────────────────────────────────────────────────────────

	private ValeurResponse enregistrerEtTracer(ReferentielParametre ref, ParametreValeur p, Map<String, Object> avant,
			ActionJournalParametre action, AuteurParametre auteur) {
		p.setModifieLe(Instant.now());
		p.setModifieParCompteId(auteur.compteId());
		ParametreValeur enregistre = repository.saveAndFlush(p);
		tracer(ref, enregistre.getCode(), action, avant, etat(enregistre), auteur);
		cache.invaliderApresCommit();
		return versReponse(ref, enregistre, null);
	}

	private ReferentielParametre exigerReferentiel(String brut) {
		return ReferentielParametre.parse(brut).orElseThrow(() -> ReferentielMetierException.notFound(
				"REFERENTIEL_INCONNU", "Référentiel inconnu : " + brut + "."));
	}

	private ParametreValeur exiger(ReferentielParametre ref, String code) {
		return repository.findByReferentielAndCode(ref.name(), normaliserCode(code))
				.orElseThrow(() -> introuvable(ref, code));
	}

	private static ReferentielMetierException introuvable(ReferentielParametre ref, String code) {
		return ReferentielMetierException.notFound("PARAMETRE_INTROUVABLE",
				"Valeur introuvable dans « " + ref.getLibelle() + " » : " + code + ".");
	}

	private static String normaliserCode(String code) {
		return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
	}

	private static void verifierVersion(ParametreValeur p, Long version) {
		if (version != null && version != p.getVersion()) {
			throw ReferentielMetierException.conflict("PARAMETRE_MODIFIE_ENTRE_TEMPS",
					"« " + p.getLibelle() + " » a été modifiée par un autre utilisateur. "
							+ "Rechargez la liste puis recommencez.");
		}
	}

	private static String validerLibelle(String brut) {
		String l = brut == null ? "" : brut.trim();
		if (l.length() < LIBELLE_MIN || l.length() > LIBELLE_MAX) {
			throw ReferentielMetierException.badRequest("LIBELLE_INVALIDE",
					"Le libellé doit faire entre " + LIBELLE_MIN + " et " + LIBELLE_MAX + " caractères.");
		}
		return l.replaceAll("\\s+", " ");
	}

	private static String validerDescription(String brut) {
		if (brut == null) {
			return null;
		}
		String d = brut.trim();
		if (d.length() > DESCRIPTION_MAX) {
			throw ReferentielMetierException.badRequest("DESCRIPTION_TROP_LONGUE",
					"La description ne peut pas dépasser " + DESCRIPTION_MAX + " caractères.");
		}
		return d.isEmpty() ? null : d;
	}

	private static String validerCouleur(String brut) {
		if (brut == null || brut.isBlank()) {
			return null;
		}
		String c = brut.trim().toUpperCase(Locale.ROOT);
		if (!PALETTE.contains(c)) {
			throw ReferentielMetierException.badRequest("COULEUR_INVALIDE",
					"Couleur inconnue : choisissez dans la palette (" + String.join(", ", PALETTE.stream().sorted().toList())
							+ ").");
		}
		return c;
	}

	private static int validerOrdre(int ordre) {
		if (ordre < 0 || ordre > ORDRE_MAX) {
			throw ReferentielMetierException.badRequest("ORDRE_INVALIDE",
					"L'ordre doit être compris entre 0 et " + ORDRE_MAX + ".");
		}
		return ordre;
	}

	private static int validerSla(Integer sla, boolean obligatoire) {
		if (sla == null) {
			if (obligatoire) {
				throw ReferentielMetierException.badRequest("SLA_INVALIDE",
						"Le délai de traitement (en heures) est obligatoire.");
			}
			return 0;
		}
		if (sla < SLA_MIN || sla > SLA_MAX) {
			throw ReferentielMetierException.badRequest("SLA_INVALIDE",
					"Le délai de traitement doit être compris entre " + SLA_MIN + " et " + SLA_MAX + " heures.");
		}
		return sla;
	}

	private static ReferentielMetierException libelleEnDouble(String libelle) {
		return ReferentielMetierException.badRequest("LIBELLE_EN_DOUBLE",
				"Le libellé « " + libelle + " » existe déjà dans ce référentiel (valeurs inactives comprises).");
	}

	private List<ValeurResponse> versReponses(ReferentielParametre ref, List<ParametreValeur> lignes) {
		Map<String, Long> usages = compteur.compter(ref.name());
		return lignes.stream().map(p -> versReponse(ref, p, usages.getOrDefault(p.getCode(), 0L))).toList();
	}

	/** {@code utilisations} {@code null} : calculé à la demande (réponse à une écriture). */
	private ValeurResponse versReponse(ReferentielParametre ref, ParametreValeur p, Long utilisations) {
		long n = utilisations != null ? utilisations : compteur.compter(ref.name(), p.getCode());
		return new ValeurResponse(p.getCode(), p.getLibelle(), p.getDescription(), p.getCouleur(), p.getOrdre(),
				p.isActif(), p.isSysteme(), p.getNature().name(), p.getVersion(), n,
				p.getPremiereUtilisationLe() != null || n > 0,
				p.getSlaHeures(), p.getPieceJointeObligatoire(), p.getPieceJointeVerrouillee());
	}

	private Map<String, Object> etat(ParametreValeur p) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("libelle", p.getLibelle());
		m.put("description", p.getDescription());
		m.put("couleur", p.getCouleur());
		m.put("ordre", p.getOrdre());
		m.put("actif", p.isActif());
		if (p.getSlaHeures() != null) {
			m.put("sla_heures", p.getSlaHeures());
		}
		if (p.getPieceJointeObligatoire() != null) {
			m.put("piece_jointe_obligatoire", p.getPieceJointeObligatoire());
		}
		return m;
	}

	private void tracer(ReferentielParametre ref, String code, ActionJournalParametre action, Map<String, Object> avant,
			Map<String, Object> apres, AuteurParametre auteur) {
		journalRepository.save(new ParametreJournal(ref.name(), code, action, json(avant), json(apres),
				auteur.compteId(), auteur.nom(), Instant.now()));
	}

	private String json(Map<String, Object> m) {
		return m == null ? null : jsonMapper.writeValueAsString(m);
	}

	private JournalEntree versEntree(ParametreJournal j) {
		return new JournalEntree(j.getAction().name(), j.getCode(), j.getAuteurNom(), j.getHorodatage(),
				lireJson(j.getAvantJson()), lireJson(j.getApresJson()));
	}

	private JsonNode lireJson(String texte) {
		return texte == null || texte.isBlank() ? null : jsonMapper.readTree(texte);
	}

	private static String empreinte(String texte) {
		try {
			byte[] h = MessageDigest.getInstance("SHA-256").digest(texte.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 8; i++) {
				hex.append(String.format("%02x", h[i]));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
