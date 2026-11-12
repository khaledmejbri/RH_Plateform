package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.domain.ActionParametre;
import com.hr.presence.domain.NatureParametre;
import com.hr.presence.domain.ReglagePresence;
import com.hr.presence.dto.ParametresDtos.Attribut;
import com.hr.presence.dto.ParametresDtos.DescripteurReferentiel;
import com.hr.presence.dto.ParametresDtos.Journal;
import com.hr.presence.dto.ParametresDtos.Libelles;
import com.hr.presence.dto.ParametresDtos.Operations;
import com.hr.presence.dto.ParametresDtos.PageJournal;
import com.hr.presence.dto.ParametresDtos.Reglage;
import com.hr.presence.dto.ParametresDtos.Valeur;
import com.hr.presence.dto.ParametresDtos.ValeurLibelle;
import com.hr.presence.dto.ParametresDtos.ValeurRequest;
import com.hr.presence.entity.PresenceParametreJournal;
import com.hr.presence.entity.PresenceParametreValeur;
import com.hr.presence.entity.PresenceParametres;
import com.hr.presence.repository.PresenceParametreJournalRepository;
import com.hr.presence.repository.PresenceParametreValeurRepository;
import com.hr.presence.repository.PresenceParametresRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PresenceMetierException;
import org.springframework.data.domain.Page;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Écriture des paramètres de présence (ADR lot 3 §§ 2, 5, 7). Les référentiels de présence sont des codes de
 * comportement : seuls le libellé, l'aide, la couleur et l'ordre se modifient (création, suppression et
 * désactivation : 405). Les réglages numériques sont bornés dans le code. Chaque changement est écrit au journal
 * dans la même transaction, avec l'auteur lu dans le JWT, et vide le cache après commit.
 */
@Service
public class PresenceParametresAdminService {

	/** Référentiel fictif sous lequel les changements de réglages sont journalisés. */
	public static final String REFERENTIEL_REGLAGES = "REGLAGES";

	static final Set<String> PALETTE = Set.of("NEUTRE", "BLEU", "CYAN", "VERT", "AMBRE", "ORANGE", "ROUGE", "VIOLET");

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private record Definition(String referentiel, String libelle) {
	}

	private static final List<Definition> DEFINITIONS = List.of(
			new Definition(PresenceParametresService.STATUT_POINTAGE, "Statuts et motifs de refus des pointages"),
			new Definition(PresenceParametresService.STATUT_DEMANDE_MANUELLE, "Statuts des pointages manuels"),
			new Definition(PresenceParametresService.STATUT_HEURES_SUP, "Statuts des heures supplémentaires"));

	private final PresenceParametreValeurRepository valeurRepository;
	private final PresenceParametreJournalRepository journalRepository;
	private final PresenceParametresRepository parametresRepository;
	private final PresenceParametresService parametresService;
	private final PresenceAccessService accessService;
	private final ReferentielClient referentielClient;
	private final PresenceParametreUtilisations utilisations;

	public PresenceParametresAdminService(
			PresenceParametreValeurRepository valeurRepository,
			PresenceParametreJournalRepository journalRepository,
			PresenceParametresRepository parametresRepository,
			PresenceParametresService parametresService,
			PresenceAccessService accessService,
			ReferentielClient referentielClient,
			PresenceParametreUtilisations utilisations) {
		this.valeurRepository = valeurRepository;
		this.journalRepository = journalRepository;
		this.parametresRepository = parametresRepository;
		this.parametresService = parametresService;
		this.accessService = accessService;
		this.referentielClient = referentielClient;
		this.utilisations = utilisations;
	}

	// ------------------------------------------------------------------ libellés (tout utilisateur authentifié)

	/**
	 * Libellés pour les écrans. Back-office : tout (actifs seulement si {@code actifs}). Autre utilisateur : valeurs
	 * actives seulement et sans description (aide réservée à la RH).
	 */
	public Libelles libelles(boolean actifs) {
		boolean backoffice = accessService.isBackofficeLecture();
		PresenceParametresService.Instantane i = parametresService.instantane();
		Map<String, List<ValeurLibelle>> out = new LinkedHashMap<>();
		i.referentiels().forEach((ref, valeurs) -> out.put(ref, valeurs.stream()
				.filter(v -> v.actif() || (backoffice && !actifs))
				.map(v -> backoffice ? v : new ValeurLibelle(v.code(), v.libelle(), null, v.couleur(), v.ordre(), v.actif()))
				.toList()));
		return new Libelles(i.etag(), out);
	}

	public String etag() {
		return parametresService.instantane().etag();
	}

	// ------------------------------------------------------------------ référentiels

	public List<DescripteurReferentiel> referentiels() {
		return DEFINITIONS.stream().map(d -> new DescripteurReferentiel(d.referentiel(), d.libelle(), "POINTAGE",
				NatureParametre.COMPORTEMENT, new Operations(false, false, false), List.<Attribut>of())).toList();
	}

	@Transactional(readOnly = true)
	public List<Valeur> valeurs(String referentiel) {
		exigerReferentiel(referentiel);
		return versValeurs(referentiel, valeurRepository.findByReferentielOrderByOrdreAscCodeAsc(referentiel));
	}

	/** Création interdite : les codes de comportement viennent du code (405). */
	public Valeur creer(String referentiel) {
		exigerReferentiel(referentiel);
		throw comportement("Les valeurs de ce référentiel pilotent des règles : on ne peut pas en créer");
	}

	public void supprimer(String referentiel) {
		exigerReferentiel(referentiel);
		throw comportement("Les valeurs de ce référentiel pilotent des règles : on ne peut pas en supprimer");
	}

	public Valeur desactiver(String referentiel) {
		exigerReferentiel(referentiel);
		throw comportement("Les valeurs de ce référentiel pilotent des règles : on ne peut pas les désactiver");
	}

	@Transactional
	public Valeur modifier(String referentiel, String code, ValeurRequest request) {
		exigerReferentiel(referentiel);
		PresenceParametreValeur v = valeurRepository.findByReferentielAndCode(referentiel, code)
				.orElseThrow(() -> PresenceMetierException.notFound("PARAMETRE_INTROUVABLE", "Valeur introuvable"));
		exigerVersion(request.version(), v.getVersion());

		String libelle = libelleValide(request.libelle());
		String normalise = normaliser(libelle);
		boolean doublon = valeurRepository.findByReferentielOrderByOrdreAscCodeAsc(referentiel).stream()
				.anyMatch(autre -> !autre.getCode().equals(code) && autre.getLibelleNormalise().equals(normalise));
		if (doublon) {
			throw PresenceMetierException.badRequest("LIBELLE_EN_DOUBLE", "Ce libellé existe déjà dans ce référentiel");
		}
		// null = inchangée ; chaîne vide = effacer (même sémantique dans les trois services)
		String description = request.description() == null ? v.getDescription()
				: request.description().isBlank() ? null : request.description().trim();
		if (description != null && description.length() > 300) {
			throw PresenceMetierException.badRequest("DESCRIPTION_TROP_LONGUE", "La description dépasse 300 caractères");
		}
		String couleur = request.couleur() == null ? v.getCouleur()
				: request.couleur().isBlank() ? null : request.couleur().trim().toUpperCase(Locale.ROOT);
		if (couleur != null && !PALETTE.contains(couleur)) {
			throw PresenceMetierException.badRequest("COULEUR_INVALIDE", "Couleur hors palette : " + PALETTE);
		}

		Map<String, Object> avant = etat(v);
		v.setLibelle(libelle);
		v.setLibelleNormalise(normalise);
		v.setDescription(description);
		v.setCouleur(couleur);
		if (request.ordre() != null) {
			v.setOrdre(request.ordre());
		}
		if (avant.equals(etat(v))) {
			// Aucun changement : ni écriture, ni entrée de journal, version inchangée
			return versValeur(v, comptes(referentiel));
		}
		v.setModifieLe(Instant.now());
		v.setModifieParCompteId(accessService.currentCompteId());
		PresenceParametreValeur sauve = enregistrer(v);
		journaliser(referentiel, code, ActionParametre.MODIFICATION, avant, etat(sauve));
		parametresService.invaliderApresCommit();
		return versValeur(sauve, comptes(referentiel));
	}

	@Transactional
	public List<Valeur> ordonner(String referentiel, List<String> codes) {
		exigerReferentiel(referentiel);
		List<PresenceParametreValeur> existantes = valeurRepository.findByReferentielOrderByOrdreAscCodeAsc(referentiel);
		Set<String> attendus = new java.util.HashSet<>();
		existantes.forEach(e -> attendus.add(e.getCode()));
		if (codes == null || codes.size() != attendus.size() || !attendus.equals(new java.util.HashSet<>(codes))) {
			throw PresenceMetierException.badRequest("ORDRE_INCOMPLET", "La liste doit contenir tous les codes du référentiel, une fois chacun");
		}
		Map<String, PresenceParametreValeur> parCode = new LinkedHashMap<>();
		existantes.forEach(e -> parCode.put(e.getCode(), e));
		List<String> avant = existantes.stream().map(PresenceParametreValeur::getCode).toList();
		if (avant.equals(codes)) {
			// Ordre inchangé : rien à écrire ni à journaliser
			return versValeurs(referentiel, existantes);
		}
		int rang = 1;
		for (String code : codes) {
			PresenceParametreValeur v = parCode.get(code);
			v.setOrdre(rang++);
			v.setModifieLe(Instant.now());
			v.setModifieParCompteId(accessService.currentCompteId());
		}
		try {
			valeurRepository.saveAllAndFlush(parCode.values());
		} catch (ObjectOptimisticLockingFailureException ex) {
			throw modifieEntreTemps();
		}
		journaliser(referentiel, referentiel, ActionParametre.ORDRE, Map.of("codes", avant), Map.of("codes", codes));
		parametresService.invaliderApresCommit();
		return versValeurs(referentiel, valeurRepository.findByReferentielOrderByOrdreAscCodeAsc(referentiel));
	}

	@Transactional(readOnly = true)
	public PageJournal journal(String referentiel, int page, int taille) {
		exigerReferentiel(referentiel);
		return lireJournal(referentiel, page, taille);
	}

	// ------------------------------------------------------------------ réglages

	@Transactional(readOnly = true)
	public List<Reglage> reglages() {
		PresenceParametres ligne = parametresRepository.findById(PresenceParametres.ID_UNIQUE).orElse(null);
		return java.util.Arrays.stream(ReglagePresence.values()).map(r -> versReglage(r, ligne)).toList();
	}

	@Transactional
	public Reglage modifierReglage(String cle, int valeur, Long version) {
		ReglagePresence reglage = ReglagePresence.parCle(cle)
				.orElseThrow(() -> PresenceMetierException.notFound("REGLAGE_INCONNU", "Réglage inconnu : " + cle));
		PresenceParametres ligne = parametresRepository.findById(PresenceParametres.ID_UNIQUE).orElseGet(PresenceParametres::new);
		exigerVersion(version, ligne.getVersion() == null ? Long.valueOf(0) : ligne.getVersion());
		if (!reglage.dansLesBornes(valeur)) {
			throw PresenceMetierException.badRequest("REGLAGE_HORS_BORNES", reglage.libelle() + " : la valeur doit être comprise entre "
					+ reglage.min() + " et " + reglage.max());
		}
		int avant = valeurReglage(reglage, ligne);
		if (avant == valeur) {
			// Aucun changement : ni écriture, ni entrée de journal, version inchangée
			return versReglage(reglage, ligne);
		}
		appliquer(reglage, ligne, valeur);
		ligne.setModifieLe(Instant.now());
		ligne.setModifiePar(accessService.currentCompteId());
		PresenceParametres sauve;
		try {
			sauve = parametresRepository.saveAndFlush(ligne);
		} catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException ex) {
			// Verrou optimiste, ou création simultanée de la ligne unique de réglages (clé primaire en double)
			throw modifieEntreTemps();
		}
		journaliser(REFERENTIEL_REGLAGES, cle, ActionParametre.REGLAGE, Map.of("valeur", avant), Map.of("valeur", valeur));
		parametresService.invaliderApresCommit();
		return versReglage(reglage, sauve);
	}

	@Transactional(readOnly = true)
	public PageJournal journalReglages(int page, int taille) {
		return lireJournal(REFERENTIEL_REGLAGES, page, taille);
	}

	// ------------------------------------------------------------------ utilitaires

	private static int valeurReglage(ReglagePresence r, PresenceParametres ligne) {
		Integer v = ligne == null ? null : switch (r) {
			case SEUIL_POINTAGES_MANUELS_MOIS -> ligne.getSeuilPointagesManuelsMois();
			case DELAI_GRACE_RETARD_MINUTES -> ligne.getDelaiGraceRetardMinutes();
			case RAYON_DEFAUT_EXTERIEUR_METRES -> ligne.getRayonDefautExterieurMetres();
			case RAYON_DEFAUT_INTERIEUR_METRES -> ligne.getRayonDefautInterieurMetres();
		};
		return v != null ? v : r.defaut();
	}

	private static void appliquer(ReglagePresence r, PresenceParametres ligne, int valeur) {
		switch (r) {
			case SEUIL_POINTAGES_MANUELS_MOIS -> ligne.setSeuilPointagesManuelsMois(valeur);
			case DELAI_GRACE_RETARD_MINUTES -> ligne.setDelaiGraceRetardMinutes(valeur);
			case RAYON_DEFAUT_EXTERIEUR_METRES -> ligne.setRayonDefautExterieurMetres(valeur);
			case RAYON_DEFAUT_INTERIEUR_METRES -> ligne.setRayonDefautInterieurMetres(valeur);
		}
	}

	private static Reglage versReglage(ReglagePresence r, PresenceParametres ligne) {
		long version = ligne == null || ligne.getVersion() == null ? 0 : ligne.getVersion();
		return new Reglage(r.cle(), r.libelle(), valeurReglage(r, ligne), r.min(), r.max(), r.unite(), r.defaut(), version);
	}

	/** Utilisations locales par code, en une requête groupée pour tout le référentiel. */
	private Map<String, Long> comptes(String referentiel) {
		return utilisations.parCode(referentiel);
	}

	private List<Valeur> versValeurs(String referentiel, List<PresenceParametreValeur> lignes) {
		Map<String, Long> comptes = comptes(referentiel);
		return lignes.stream().map(v -> versValeur(v, comptes)).toList();
	}

	private Valeur versValeur(PresenceParametreValeur v, Map<String, Long> comptes) {
		long n = comptes.getOrDefault(v.getCode(), 0L);
		return new Valeur(v.getCode(), v.getLibelle(), v.getDescription(), v.getCouleur(), v.getOrdre(), v.isActif(),
				v.isSysteme(), v.getNature(), v.getVersion() == null ? 0 : v.getVersion(),
				(int) Math.min(n, Integer.MAX_VALUE), v.getPremiereUtilisationLe() != null || n > 0);
	}

	private void exigerReferentiel(String referentiel) {
		if (DEFINITIONS.stream().noneMatch(d -> d.referentiel().equals(referentiel))) {
			throw PresenceMetierException.notFound("REFERENTIEL_INCONNU", "Référentiel inconnu : " + referentiel);
		}
	}

	private static PresenceMetierException comportement(String message) {
		return new PresenceMetierException("PARAMETRE_COMPORTEMENT", message, 405);
	}

	private static PresenceMetierException modifieEntreTemps() {
		return PresenceMetierException.conflict("PARAMETRE_MODIFIE_ENTRE_TEMPS",
				"Ce paramètre vient d'être modifié par quelqu'un d'autre : rechargez puis recommencez");
	}

	private static void exigerVersion(Long fournie, Long courante) {
		if (fournie == null) {
			throw PresenceMetierException.badRequest("VERSION_OBLIGATOIRE", "La version du paramètre est obligatoire");
		}
		if (!fournie.equals(courante == null ? Long.valueOf(0) : courante)) {
			throw modifieEntreTemps();
		}
	}

	private PresenceParametreValeur enregistrer(PresenceParametreValeur v) {
		try {
			return valeurRepository.saveAndFlush(v);
		} catch (ObjectOptimisticLockingFailureException ex) {
			throw modifieEntreTemps();
		}
	}

	private static String libelleValide(String libelle) {
		String l = libelle == null ? "" : libelle.trim().replaceAll("\\s+", " ");
		if (l.length() < 2 || l.length() > 80) {
			throw PresenceMetierException.badRequest("LIBELLE_INVALIDE", "Le libellé doit comporter de 2 à 80 caractères");
		}
		return l;
	}

	/** Minuscules, sans accents, espaces simples : clé d'unicité du libellé (D-R02.b). */
	static String normaliser(String libelle) {
		String sansAccents = Normalizer.normalize(libelle, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return sansAccents.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
	}

	private static Map<String, Object> etat(PresenceParametreValeur v) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("libelle", v.getLibelle());
		m.put("description", v.getDescription());
		m.put("couleur", v.getCouleur());
		m.put("ordre", v.getOrdre());
		m.put("actif", v.isActif());
		return m;
	}

	private void journaliser(String referentiel, String code, ActionParametre action, Object avant, Object apres) {
		String compte = accessService.currentCompteId();
		journalRepository.save(new PresenceParametreJournal(referentiel, code, action, JSON.writeValueAsString(avant),
				JSON.writeValueAsString(apres), compte, nomAuteur(compte)));
	}

	/**
	 * Nom lisible de l'auteur (résolu au référentiel depuis le compte du JWT, comme dans les deux autres services) ;
	 * référentiel indisponible ou compte sans fiche : repli sur l'identifiant d'audit, l'écriture n'échoue jamais pour cela.
	 */
	private String nomAuteur(String compte) {
		String repli = accessService.currentActorLabel();
		if (compte == null) {
			return repli;
		}
		try {
			return referentielClient.affectations(List.of(java.util.UUID.fromString(compte))).stream()
					.map(ReferentielClient.Affectation::nom)
					.filter(n -> n != null && !n.isBlank())
					.findFirst()
					.orElse(repli);
		} catch (RuntimeException ex) {
			return repli;
		}
	}

	private PageJournal lireJournal(String referentiel, int page, int taille) {
		int t = Math.clamp(taille, 1, 100);
		int p = Math.max(page, 0);
		Page<PresenceParametreJournal> res = journalRepository.findByReferentielOrderByHorodatageDesc(referentiel, PageRequest.of(p, t));
		List<Journal> contenu = new ArrayList<>();
		for (PresenceParametreJournal j : res.getContent()) {
			contenu.add(new Journal(j.getAction().name(), j.getCode(), j.getAuteurNom(), j.getHorodatage(),
					lireJson(j.getAvantJson()), lireJson(j.getApresJson())));
		}
		return new PageJournal(contenu, res.getNumber(), res.getSize(), res.getTotalElements());
	}

	private static JsonNode lireJson(String json) {
		if (json == null) {
			return null;
		}
		try {
			return JSON.readTree(json);
		} catch (RuntimeException ex) {
			return JSON.valueToTree(json);
		}
	}
}
