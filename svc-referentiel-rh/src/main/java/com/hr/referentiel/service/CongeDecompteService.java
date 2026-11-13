package com.hr.referentiel.service;

import com.hr.referentiel.domain.PeriodeJour;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeMouvementSolde;
import com.hr.referentiel.dto.SoldeDtos.Blocage;
import com.hr.referentiel.dto.SoldeDtos.JourFerieSimule;
import com.hr.referentiel.dto.SoldeDtos.Simulation;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.MouvementSoldeConge;
import com.hr.referentiel.entity.SoldeConge;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.JourFerieRepository;
import com.hr.referentiel.service.DecompteJoursOuvres.Periodes;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Décompte M01 d'un congé annuel (D-S01, D-S06) : calcul serveur des jours, contrôle du solde à la création (disponible)
 * et aux validations (acquis, D-S06.b), chevauchement à la demi-journée, mouvement « Congé pris » à l'approbation.
 * Seul le type de congé {@code ANNUEL} a un solde (D-S03). Verrous : la demande est déjà verrouillée par l'appelant,
 * ce service verrouille ensuite le solde.
 */
@Service
public class CongeDecompteService {

	public static final String CODE_ANNUEL = "ANNUEL";
	static final String CLE_PREMIER = "periode_premier_jour";
	static final String CLE_DERNIER = "periode_dernier_jour";
	static final Set<StatutDemandeAdministrativeRh> STATUTS_CHEVAUCHANTS = EnumSet.of(
			StatutDemandeAdministrativeRh.SOUMISE, StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR,
			StatutDemandeAdministrativeRh.EN_VALIDATION_RRH, StatutDemandeAdministrativeRh.APPROUVEE);
	public static final String MENTION_ANTERIEURE = "Demande antérieure aux soldes : non décomptée";

	private final JourFerieService jourFerieService;
	private final SoldeCongeService soldeService;
	private final DemandeAdministrativeRhRepository demandeRepository;
	private final JourFerieRepository jourFerieRepository;

	public CongeDecompteService(JourFerieService jourFerieService, SoldeCongeService soldeService,
			DemandeAdministrativeRhRepository demandeRepository, JourFerieRepository jourFerieRepository) {
		this.jourFerieService = jourFerieService;
		this.soldeService = soldeService;
		this.demandeRepository = demandeRepository;
		this.jourFerieRepository = jourFerieRepository;
	}

	public static boolean estAnnuel(DemandeAdministrativeRh d) {
		return d.getTypeDemande() == TypeDemandeAdministrativeRh.CONGE && d.getContenu() != null
				&& CODE_ANNUEL.equals(String.valueOf(d.getContenu().get("type_conge")));
	}

	// ─── Création ─────────────────────────────────────────────────────────────

	/**
	 * Contrôles de création d'un congé (400 avant 422 avant 409, D-S15.f). Pour un congé ANNUEL : périodes normalisées
	 * dans le contenu, jours décomptés posés sur la demande, solde verrouillé. Autre type : toute période autre que la
	 * journée entière est refusée (400 {@code CORPS_INVALIDE}).
	 */
	public void preparerCreation(Collaborateur demandeur, String codeConge, DemandeAdministrativeRh d) {
		Map<String, Object> contenu = d.getContenu();
		Object brutPremier = contenu.get(CLE_PREMIER);
		Object brutDernier = contenu.get(CLE_DERNIER);
		PeriodeJour premier = brutPremier == null ? null : DecompteJoursOuvres.lirePeriode(brutPremier);
		PeriodeJour dernier = brutDernier == null ? null : DecompteJoursOuvres.lirePeriode(brutDernier);
		if (!CODE_ANNUEL.equals(codeConge)) {
			if ((premier != null && premier != PeriodeJour.JOURNEE) || (dernier != null && dernier != PeriodeJour.JOURNEE)) {
				throw ReferentielMetierException.badRequest("CORPS_INVALIDE",
						"Les demi-journées ne sont possibles que pour le congé annuel.");
			}
			return;
		}
		LocalDate debut = DemandeAdministrativePeriodeHelper.extraireDate(contenu, "date_debut");
		LocalDate fin = DemandeAdministrativePeriodeHelper.extraireDate(contenu, "date_fin");
		Periodes p = DecompteJoursOuvres.normaliser(debut, fin, premier, dernier);
		Set<LocalDate> feries = jourFerieService.datesActives();
		DecompteJoursOuvres.verifierDemiJournees(p, feries);
		BigDecimal jours = DecompteJoursOuvres.jours(p, feries);
		if (jours.signum() == 0) {
			throw aucunJourOuvre();
		}
		SoldeConge solde = soldeService.verrouiller(demandeur.getId());
		BigDecimal disponible = solde.getAcquis().subtract(soldeService.reserve(demandeur.getId()));
		if (jours.compareTo(disponible) > 0) {
			throw soldeInsuffisant(jours, disponible);
		}
		if (chevauche(demandeur.getId(), p)) {
			throw ReferentielMetierException.conflict("CONGE_CHEVAUCHEMENT", MESSAGE_CHEVAUCHEMENT);
		}
		contenu.put(CLE_PREMIER, p.premier().name());
		contenu.put(CLE_DERNIER, p.dernier().name());
		d.setJoursDecomptes(jours);
	}

	// ─── Validations et approbation ───────────────────────────────────────────

	/** Étape responsable : jours recalculés (fériés courants), refus 422 si les jours dépassent l'acquis (D-S06.a/b). */
	public void controlerValidation(DemandeAdministrativeRh d) {
		if (d.getJoursDecomptes() != null) {
			recalculerSousVerrou(d);
		}
	}

	/**
	 * Approbation RH : mêmes contrôles, jours figés, mouvement « Congé pris » (D-S06.c). Renvoie le mouvement, ou vide
	 * pour une demande antérieure aux soldes (aucun contrôle ni mouvement, D-S06.g).
	 */
	public java.util.Optional<MouvementSoldeConge> approuver(DemandeAdministrativeRh d, AuteurParametre auteur) {
		if (d.getJoursDecomptes() == null) {
			return java.util.Optional.empty();
		}
		SoldeConge solde = recalculerSousVerrou(d);
		return java.util.Optional.of(soldeService.enregistrer(solde, TypeMouvementSolde.CONGE_PRIS,
				d.getJoursDecomptes().negate(), null, null, d.getId(), d.getNumero(), null, null, auteur));
	}

	private SoldeConge recalculerSousVerrou(DemandeAdministrativeRh d) {
		Periodes p = periodesDe(d);
		Set<LocalDate> feries = jourFerieService.datesActives();
		BigDecimal jours = DecompteJoursOuvres.jours(p, feries);
		if (jours.signum() == 0) {
			throw aucunJourOuvre();
		}
		SoldeConge solde = soldeService.verrouiller(d.getDemandeur().getId());
		if (jours.compareTo(solde.getAcquis()) > 0) {
			throw soldeInsuffisant(jours, solde.getAcquis());
		}
		d.setJoursDecomptes(jours);
		return solde;
	}

	private static Periodes periodesDe(DemandeAdministrativeRh d) {
		Map<String, Object> c = d.getContenu();
		LocalDate debut = DemandeAdministrativePeriodeHelper.extraireDate(c, "date_debut");
		LocalDate fin = DemandeAdministrativePeriodeHelper.extraireDate(c, "date_fin");
		return DecompteJoursOuvres.normaliser(debut, fin, lireSouple(c.get(CLE_PREMIER)), lireSouple(c.get(CLE_DERNIER)));
	}

	private static PeriodeJour lireSouple(Object brut) {
		try {
			return brut == null ? null : DecompteJoursOuvres.lirePeriode(brut);
		} catch (ReferentielMetierException e) {
			return PeriodeJour.JOURNEE;
		}
	}

	// ─── Simulation (A3), sans écriture ───────────────────────────────────────

	public Simulation simuler(UUID collaborateurId, LocalDate debut, LocalDate fin, String premierBrut, String dernierBrut) {
		if (debut == null || fin == null) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "Les dates de début et de fin sont obligatoires.");
		}
		Periodes p = DecompteJoursOuvres.normaliser(debut, fin,
				premierBrut == null || premierBrut.isBlank() ? null : DecompteJoursOuvres.lirePeriode(premierBrut),
				dernierBrut == null || dernierBrut.isBlank() ? null : DecompteJoursOuvres.lirePeriode(dernierBrut));
		Set<LocalDate> feries = jourFerieService.datesActives();
		DecompteJoursOuvres.verifierDemiJournees(p, feries);
		BigDecimal jours = DecompteJoursOuvres.jours(p, feries);
		BigDecimal disponible = soldeService.acquis(collaborateurId).subtract(soldeService.reserve(collaborateurId));
		List<JourFerieSimule> vus = libellesFeries(DecompteJoursOuvres.feriesDansPeriode(p, feries));
		Blocage blocage = null;
		if (jours.signum() == 0) {
			blocage = new Blocage("AUCUN_JOUR_OUVRE", MESSAGE_AUCUN_JOUR);
		} else if (jours.compareTo(disponible) > 0) {
			blocage = new Blocage("SOLDE_INSUFFISANT", messageSolde(jours, disponible));
		} else if (chevauche(collaborateurId, p)) {
			blocage = new Blocage("CONGE_CHEVAUCHEMENT", MESSAGE_CHEVAUCHEMENT);
		}
		return new Simulation(jours, disponible, disponible.subtract(jours), vus, blocage);
	}

	private List<JourFerieSimule> libellesFeries(List<LocalDate> dates) {
		if (dates.isEmpty()) {
			return List.of();
		}
		Map<LocalDate, String> libelles = new HashMap<>();
		jourFerieRepository.findByActifTrueAndDateJourBetweenOrderByDateJourAsc(dates.get(0), dates.get(dates.size() - 1))
				.forEach(j -> libelles.put(j.getDateJour(), j.getLibelle()));
		return dates.stream().map(j -> new JourFerieSimule(j, libelles.getOrDefault(j, "Jour férié"))).toList();
	}

	// ─── Chevauchement à la demi-journée (D-S06.e) ────────────────────────────

	private boolean chevauche(UUID collaborateurId, Periodes nouvelle) {
		long[] n = DecompteJoursOuvres.intervalle(nouvelle.debut(), nouvelle.fin(), nouvelle.premier(), nouvelle.dernier());
		for (DemandeAdministrativeRh e : demandeRepository.findPourChevauchement(collaborateurId,
				TypeDemandeAdministrativeRh.CONGE, STATUTS_CHEVAUCHANTS)) {
			if (e.getPeriodeDebut() == null || e.getPeriodeFin() == null) {
				continue;
			}
			PeriodeJour premier = PeriodeJour.JOURNEE;
			PeriodeJour dernier = PeriodeJour.JOURNEE;
			if (estAnnuel(e)) {
				premier = lireSouple(e.getContenu().get(CLE_PREMIER));
				dernier = lireSouple(e.getContenu().get(CLE_DERNIER));
				premier = premier == null ? PeriodeJour.JOURNEE : premier;
				dernier = dernier == null ? premier : dernier;
			}
			long[] x = DecompteJoursOuvres.intervalle(e.getPeriodeDebut(), e.getPeriodeFin(), premier, dernier);
			if (n[0] < x[1] && x[0] < n[1]) {
				return true;
			}
		}
		return false;
	}

	// ─── Messages ─────────────────────────────────────────────────────────────

	static final String MESSAGE_AUCUN_JOUR = "Aucun jour ouvré sur cette période";
	static final String MESSAGE_CHEVAUCHEMENT = "Cette période chevauche un autre congé en cours ou approuvé.";

	static String messageSolde(BigDecimal jours, BigDecimal disponible) {
		BigDecimal dispo = disponible.max(BigDecimal.ZERO);
		return "Solde insuffisant : " + FormatJours.jours(jours) + " " + FormatJours.accord(jours, "demandé") + ", "
				+ FormatJours.jours(dispo) + " " + FormatJours.accord(dispo, "disponible");
	}

	private static ReferentielMetierException soldeInsuffisant(BigDecimal jours, BigDecimal disponible) {
		Map<String, Object> details = new HashMap<>();
		details.put("jours_demandes", jours);
		details.put("solde_disponible", disponible);
		return new ReferentielMetierException("SOLDE_INSUFFISANT", messageSolde(jours, disponible), 422, details);
	}

	private static ReferentielMetierException aucunJourOuvre() {
		return ReferentielMetierException.unprocessable("AUCUN_JOUR_OUVRE", MESSAGE_AUCUN_JOUR + ".");
	}
}
