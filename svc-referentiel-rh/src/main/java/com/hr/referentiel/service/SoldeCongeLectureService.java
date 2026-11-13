package com.hr.referentiel.service;

import com.hr.referentiel.domain.TypeMouvementSolde;
import com.hr.referentiel.dto.PageReferentielResponse;
import com.hr.referentiel.dto.SoldeDtos.DernierMouvement;
import com.hr.referentiel.dto.SoldeDtos.Mouvement;
import com.hr.referentiel.dto.SoldeDtos.Simulation;
import com.hr.referentiel.dto.SoldeDtos.Solde;
import com.hr.referentiel.dto.SoldeDtos.SoldeLigne;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.MouvementSoldeConge;
import com.hr.referentiel.entity.SoldeConge;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.MouvementSoldeCongeRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lectures du solde (A1 à A3 côté collaborateur, B1 à B5 côté back-office). L'identité vient toujours du JWT ; la liste
 * du back-office utilise deux requêtes groupées (soldes, réservés), jamais une par ligne.
 */
@Service
public class SoldeCongeLectureService {

	public static final int TAILLE_DEFAUT = 20;
	public static final int TAILLE_MAX = 100;
	static final Set<String> TRIS = Set.of("nom", "matricule", "unite", "acquis", "reserve", "disponible", "dernier_mouvement");

	private final SoldeCongeService soldeService;
	private final CongeDecompteService decompteService;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final CollaborateurRepository collaborateurRepository;
	private final MouvementSoldeCongeRepository mouvementRepository;
	private final ParametreService parametreService;

	public SoldeCongeLectureService(SoldeCongeService soldeService, CongeDecompteService decompteService,
			CollaborateurConnecteService collaborateurConnecteService, CollaborateurRepository collaborateurRepository,
			MouvementSoldeCongeRepository mouvementRepository, ParametreService parametreService) {
		this.soldeService = soldeService;
		this.decompteService = decompteService;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.collaborateurRepository = collaborateurRepository;
		this.mouvementRepository = mouvementRepository;
		this.parametreService = parametreService;
	}

	// ─── Collaborateur (JWT seulement) ─────────────────────────────────────────

	@Transactional(readOnly = true)
	public Solde moi(Jwt jwt) {
		UUID id = ficheDe(jwt).getId();
		BigDecimal acquis = soldeService.acquis(id);
		BigDecimal reserve = soldeService.reserve(id);
		return new Solde(acquis, reserve, acquis.subtract(reserve));
	}

	@Transactional(readOnly = true)
	public PageReferentielResponse<Mouvement> mesMouvements(Jwt jwt, String type, int page, int taille) {
		return mouvements(ficheDe(jwt).getId(), type, page, taille);
	}

	@Transactional(readOnly = true)
	public Simulation simuler(Jwt jwt, LocalDate debut, LocalDate fin, String premier, String dernier) {
		return decompteService.simuler(ficheDe(jwt).getId(), debut, fin, premier, dernier);
	}

	private Collaborateur ficheDe(Jwt jwt) {
		return collaborateurConnecteService.resoudre(jwt).orElseThrow(() -> ReferentielMetierException.notFound(
				"COLLABORATEUR_INTROUVABLE", "Aucune fiche collaborateur n'est liée à ce compte."));
	}

	// ─── Back-office ──────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public SoldeLigne soldeDe(UUID collaborateurId, Jwt jwt) {
		Collaborateur c = collaborateurRepository.findDetailById(collaborateurId).orElseThrow(
				() -> ReferentielMetierException.notFound("COLLABORATEUR_INTROUVABLE", "Collaborateur introuvable."));
		UUID moi = collaborateurConnecteService.resoudre(jwt).map(Collaborateur::getId).orElse(null);
		return ligne(c, moi);
	}

	/** Ligne d'un collaborateur (acquis, réservé, disponible, dernier mouvement, version). */
	@Transactional(readOnly = true)
	public SoldeLigne ligne(Collaborateur c, UUID moiId) {
		BigDecimal acquis = soldeService.acquis(c.getId());
		BigDecimal reserve = soldeService.reserve(c.getId());
		return construire(c, acquis, reserve, versionDe(c.getId()), dernierDe(c.getId()), moiId);
	}

	@Transactional(readOnly = true)
	public PageReferentielResponse<SoldeLigne> liste(String q, UUID uniteId, String statut, String tri, String sens,
			int page, int taille, Jwt jwt) {
		List<SoldeLigne> toutes = toutesLesLignes(q, uniteId, statut, tri, sens, jwt);
		validerTaille(taille);
		int p = Math.max(0, page);
		int from = Math.min(toutes.size(), p * taille);
		int to = Math.min(toutes.size(), from + taille);
		int pages = (int) Math.ceil(toutes.size() / (double) taille);
		return new PageReferentielResponse<>(toutes.subList(from, to), toutes.size(), pages, p, taille);
	}

	@Transactional(readOnly = true)
	public byte[] exporter(String q, UUID uniteId, String statut, String tri, String sens, Jwt jwt) {
		CsvFrancais csv = new CsvFrancais().ligne(List.of("Nom", "Matricule", "Unité", "Statut", "Solde acquis",
				"Réservé (demandes en cours)", "Solde disponible", "Dernier mouvement"));
		for (SoldeLigne l : toutesLesLignes(q, uniteId, statut, tri, sens, jwt)) {
			String dernier = l.dernierMouvement() == null ? "" : CsvFrancais.date(l.dernierMouvement().date()) + " "
					+ l.dernierMouvement().typeLibelle() + " " + FormatJours.signe(l.dernierMouvement().nombre());
			csv.ligne(List.of(CsvFrancais.texte(l.nomComplet()), CsvFrancais.texte(l.matricule()),
					CsvFrancais.texte(l.uniteLibelle()), CsvFrancais.texte(l.statutLibelle()),
					CsvFrancais.nombre(l.acquis()), CsvFrancais.nombre(l.reserve()), CsvFrancais.nombre(l.disponible()),
					CsvFrancais.texte(dernier.trim())));
		}
		return csv.octets();
	}

	@Transactional(readOnly = true)
	public PageReferentielResponse<Mouvement> mouvementsDe(UUID collaborateurId, String type, int page, int taille) {
		exiger(collaborateurId);
		return mouvements(collaborateurId, type, page, taille);
	}

	@Transactional(readOnly = true)
	public byte[] exporterMouvements(UUID collaborateurId) {
		exiger(collaborateurId);
		CsvFrancais csv = new CsvFrancais().ligne(List.of("Date", "Type", "Nombre", "Solde après", "Auteur", "Origine",
				"Motif", "Demande"));
		for (MouvementSoldeConge m : soldeService.mouvementsDe(collaborateurId)) {
			Mouvement v = vers(m);
			csv.ligne(List.of(CsvFrancais.date(m.getCreeLe()), CsvFrancais.texte(v.typeLibelle()),
					CsvFrancais.nombre(m.getNombre()), CsvFrancais.nombre(m.getSoldeApres()),
					CsvFrancais.texte(m.getAuteurNom()), CsvFrancais.texte(v.origineLibelle()),
					CsvFrancais.texte(m.getMotif()), CsvFrancais.texte(m.getDemandeNumero())));
		}
		return csv.octets();
	}

	// ─── Privé ────────────────────────────────────────────────────────────────

	private Collaborateur exiger(UUID id) {
		return collaborateurRepository.findById(id).orElseThrow(
				() -> ReferentielMetierException.notFound("COLLABORATEUR_INTROUVABLE", "Collaborateur introuvable."));
	}

	private PageReferentielResponse<Mouvement> mouvements(UUID collaborateurId, String type, int page, int taille) {
		validerTaille(taille);
		String code = null;
		if (type != null && !type.isBlank()) {
			try {
				code = TypeMouvementSolde.valueOf(type.trim().toUpperCase(Locale.ROOT)).name();
			} catch (IllegalArgumentException e) {
				throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "Type de mouvement inconnu : " + type + ".");
			}
		}
		PageRequest pr = PageRequest.of(Math.max(0, page), taille);
		Page<MouvementSoldeConge> r = code == null
				? mouvementRepository.findByCollaborateurIdOrderByCreeLeDescIdDesc(collaborateurId, pr)
				: mouvementRepository.findByCollaborateurIdAndTypeOrderByCreeLeDescIdDesc(collaborateurId, code, pr);
		return new PageReferentielResponse<>(r.getContent().stream().map(this::vers).toList(), r.getTotalElements(),
				r.getTotalPages(), r.getNumber(), taille);
	}

	/** Mouvement tel que servi à l'écran : libellés issus des Paramètres, aucun code ni identifiant technique. */
	public Mouvement vers(MouvementSoldeConge m) {
		String origine = m.getOrigine();
		String origineLibelle = origine == null ? null : switch (origine) {
			case "SAISIE_MANUELLE" -> "Saisie manuelle";
			case "IMPORT" -> "Import";
			case "SYSTEME" -> "Système (automatique)";
			default -> null;
		};
		String mois = m.getMoisAcquisition();
		String moisLibelle = null;
		if (mois != null) {
			YearMonth ym = YearMonth.parse(mois);
			moisLibelle = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH) + " " + ym.getYear();
		}
		return new Mouvement(m.getType(), parametreService.libelleOuInconnu("TYPE_MOUVEMENT_SOLDE", m.getType()),
				m.getNombre(), m.getSoldeApres(), m.getCreeLe(), m.getAuteurNom(), origine, origineLibelle, m.getMotif(),
				m.getDemandeId(), m.getDemandeNumero(), mois, moisLibelle);
	}

	private List<SoldeLigne> toutesLesLignes(String q, UUID uniteId, String statut, String tri, String sens, Jwt jwt) {
		String t = tri == null || tri.isBlank() ? "nom" : tri;
		if (!TRIS.contains(t) || (sens != null && !sens.isBlank() && !Set.of("asc", "desc").contains(sens.toLowerCase(Locale.ROOT)))) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "Tri ou sens de tri inconnu.");
		}
		String st = statut == null || statut.isBlank() ? "ACTIF" : statut.trim().toUpperCase(Locale.ROOT);
		if (!"TOUS".equals(st) && parametreService.libelle("STATUT_COLLABORATEUR", st) == null) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "Statut inconnu : " + statut + ".");
		}
		UUID moi = collaborateurConnecteService.resoudre(jwt).map(Collaborateur::getId).orElse(null);
		List<String> mots = q == null ? List.of() : java.util.Arrays.stream(NormalisationLibelle.normaliser(q).split(" "))
				.filter(s -> !s.isBlank()).toList();
		List<Collaborateur> fiches = collaborateurRepository.findAllWithUnite().stream()
				.filter(c -> "TOUS".equals(st) || st.equalsIgnoreCase(c.getStatut()))
				.filter(c -> uniteId == null || SuperieurHierarchieRules.descendDe(c.getUnite(), uniteId))
				.filter(c -> correspond(c, mots))
				.toList();
		List<UUID> ids = fiches.stream().map(Collaborateur::getId).toList();
		Map<UUID, SoldeConge> soldes = new java.util.HashMap<>();
		if (!ids.isEmpty()) {
			soldeService.soldes(ids).forEach(s -> soldes.put(s.getCollaborateurId(), s));
		}
		Map<UUID, BigDecimal> reserves = soldeService.reservesGroupes(ids);
		Map<UUID, MouvementSoldeConge> derniers = new java.util.HashMap<>();
		List<UUID> idsMvt = soldes.values().stream().map(SoldeConge::getDernierMouvementId)
				.filter(java.util.Objects::nonNull).toList();
		if (!idsMvt.isEmpty()) {
			mouvementRepository.findParIds(idsMvt).forEach(m -> derniers.put(m.getId(), m));
		}
		List<SoldeLigne> lignes = new ArrayList<>();
		for (Collaborateur c : fiches) {
			SoldeConge s = soldes.get(c.getId());
			BigDecimal acquis = s == null ? new BigDecimal("0.0") : s.getAcquis();
			BigDecimal reserve = reserves.getOrDefault(c.getId(), new BigDecimal("0.0"));
			MouvementSoldeConge dm = s == null || s.getDernierMouvementId() == null ? null : derniers.get(s.getDernierMouvementId());
			lignes.add(construire(c, acquis, reserve, s == null ? 0L : s.getVersion(), dm == null ? null
					: new DernierMouvement(dm.getCreeLe(), parametreService.libelleOuInconnu("TYPE_MOUVEMENT_SOLDE", dm.getType()),
							dm.getNombre()), moi));
		}
		Comparator<SoldeLigne> cmp = comparateur(t);
		if ("desc".equalsIgnoreCase(sens)) {
			cmp = cmp.reversed();
		}
		Collator collationMatricule = Collator.getInstance(Locale.FRENCH);
		collationMatricule.setStrength(Collator.PRIMARY);
		lignes.sort(cmp.thenComparing(SoldeLigne::matricule, Comparator.nullsLast(collationMatricule::compare)));
		return lignes;
	}

	private static Comparator<SoldeLigne> comparateur(String tri) {
		// collation française (accents et casse ignorés au premier niveau : « Éric » avant « Fatma »)
		Collator collation = Collator.getInstance(Locale.FRENCH);
		collation.setStrength(Collator.PRIMARY);
		Comparator<String> texte = Comparator.nullsLast(collation::compare);
		return switch (tri) {
			case "matricule" -> Comparator.comparing(SoldeLigne::matricule, texte);
			case "unite" -> Comparator.comparing(SoldeLigne::uniteLibelle, texte);
			case "acquis" -> Comparator.comparing(SoldeLigne::acquis);
			case "reserve" -> Comparator.comparing(SoldeLigne::reserve);
			case "disponible" -> Comparator.comparing(SoldeLigne::disponible);
			case "dernier_mouvement" -> Comparator.comparing((SoldeLigne l) -> l.dernierMouvement() == null ? null
					: l.dernierMouvement().date(), Comparator.nullsFirst(Comparator.naturalOrder()));
			default -> Comparator.comparing(SoldeLigne::nomComplet, texte);
		};
	}

	private static boolean correspond(Collaborateur c, List<String> mots) {
		if (mots.isEmpty()) {
			return true;
		}
		String cible = NormalisationLibelle.normaliser((c.getPrenom() == null ? "" : c.getPrenom()) + " "
				+ (c.getNom() == null ? "" : c.getNom()) + " " + (c.getMatricule() == null ? "" : c.getMatricule()));
		return mots.stream().allMatch(cible::contains);
	}

	private SoldeLigne construire(Collaborateur c, BigDecimal acquis, BigDecimal reserve, long version,
			DernierMouvement dernier, UUID moiId) {
		String nom = ((c.getPrenom() == null ? "" : c.getPrenom()) + " " + (c.getNom() == null ? "" : c.getNom())).trim();
		return new SoldeLigne(c.getId(), c.getMatricule(), nom, c.getUnite() == null ? null : c.getUnite().getLibelle(),
				c.getStatut(), parametreService.libelleOuInconnu("STATUT_COLLABORATEUR", c.getStatut()), acquis, reserve,
				acquis.subtract(reserve), dernier, version, c.getId().equals(moiId));
	}

	private long versionDe(UUID id) {
		return soldeService.soldes(List.of(id)).stream().findFirst().map(SoldeConge::getVersion).orElse(0L);
	}

	private DernierMouvement dernierDe(UUID id) {
		return soldeService.soldes(List.of(id)).stream().findFirst().map(SoldeConge::getDernierMouvementId)
				.flatMap(mid -> mid == null ? java.util.Optional.<MouvementSoldeConge>empty()
						: mouvementRepository.findById(mid))
				.map(m -> new DernierMouvement(m.getCreeLe(),
						parametreService.libelleOuInconnu("TYPE_MOUVEMENT_SOLDE", m.getType()), m.getNombre()))
				.orElse(null);
	}

	private static void validerTaille(int taille) {
		if (taille < 1 || taille > TAILLE_MAX) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE",
					"La taille de page doit être comprise entre 1 et " + TAILLE_MAX + ".");
		}
	}
}
