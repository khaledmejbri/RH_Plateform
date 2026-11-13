package com.hr.referentiel.importation;

import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.importation.ImportModeles.Colonne;
import com.hr.referentiel.importation.ImportModeles.Evaluation;
import com.hr.referentiel.importation.ImportModeles.LigneResultat;
import com.hr.referentiel.importation.ImportModeles.Probleme;
import com.hr.referentiel.importation.ImportModeles.Totaux;
import com.hr.referentiel.importation.lecture.Fichier.LigneBrute;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.service.ApresCommit;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.service.FormatJours;
import com.hr.referentiel.service.SoldeCongeService;
import com.hr.referentiel.service.SoldeLotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.hr.referentiel.importation.ChampImport.Solde.MATRICULE;
import static com.hr.referentiel.importation.ChampImport.Solde.MOTIF;
import static com.hr.referentiel.importation.ChampImport.Solde.SOLDE;

/**
 * Import des soldes de congé (T3, D-S09) : la valeur du fichier est le solde acquis cible absolu, l'ajustement vaut
 * cible - actuel recalculé à la confirmation sous verrou. Lignes sans erreur importées seules ; ligne devenue égale :
 * « Inchangé », aucun mouvement. Ordre des verrous : soldes verrouillés dans l'ordre croissant des identifiants.
 */
@Service
public class ImportSoldesService {

	public static final int MOTIF_MIN = 5;
	public static final int MOTIF_MAX = 500;
	static final BigDecimal MAX = new BigDecimal("365");
	private static final Pattern DECIMAL = Pattern.compile("^\\d{1,4}([.,]\\d{1,3})?$");

	private final CollaborateurRepository collaborateurRepository;
	private final SoldeCongeService soldeService;
	private final RhNotificationPublisher notificationPublisher;
	private final SoldeLotService lotService;
	private final Clock clock;

	public ImportSoldesService(CollaborateurRepository collaborateurRepository, SoldeCongeService soldeService,
			RhNotificationPublisher notificationPublisher, SoldeLotService lotService, Clock clock) {
		this.lotService = lotService;
		this.collaborateurRepository = collaborateurRepository;
		this.soldeService = soldeService;
		this.notificationPublisher = notificationPublisher;
		this.clock = clock;
	}

	/** Fiches, acquis et réservés lus une fois (requêtes groupées). */
	public static final class Contexte {
		final Map<String, Collaborateur> parMatricule = new HashMap<>();
		final Map<UUID, BigDecimal> acquis = new HashMap<>();
		final Map<UUID, BigDecimal> reserves = new HashMap<>();
	}

	@Transactional(readOnly = true)
	public Contexte charger() {
		Contexte ctx = new Contexte();
		for (Collaborateur c : collaborateurRepository.findAllWithUnite()) {
			if (c.getMatricule() != null) {
				ctx.parMatricule.put(c.getMatricule().trim().toLowerCase(Locale.ROOT), c);
			}
		}
		List<UUID> ids = ctx.parMatricule.values().stream().map(Collaborateur::getId).toList();
		// projection d'acquis (aucune entité de solde gérée par le contexte) : voir SoldeLotService
		ctx.acquis.putAll(soldeService.acquisGroupes(ids));
		ctx.reserves.putAll(soldeService.reservesGroupes(ids));
		return ctx;
	}

	/** Avertissement global du 1er au 5 du mois, heure de Tunis (D-S09.d). */
	public List<Probleme> avertissementsFichier() {
		LocalDate aujourdhui = LocalDate.now(clock.withZone(FormatJours.TUNIS));
		if (aujourdhui.getDayOfMonth() <= 5) {
			return List.of(Probleme.fichier("ACQUISITION_RECENTE", "L'acquisition mensuelle du 1er du mois est peut-être "
					+ "déjà comptée dans les soldes : vérifiez que les soldes du fichier en tiennent compte."));
		}
		return List.of();
	}

	public Evaluation evaluer(List<LigneBrute> donnees, List<Colonne> colonnes, String motifGlobal, UUID auteurFicheId,
			Contexte ctx) {
		Map<ChampImport.Solde, String> enTetes = new EnumMap<>(ChampImport.Solde.class);
		for (Colonne c : colonnes) {
			if (c.champ() != null) {
				enTetes.putIfAbsent(ChampImport.Solde.valueOf(c.champ()), c.enTete());
			}
		}
		List<Map<ChampImport.Solde, String>> valeurs = new ArrayList<>();
		Map<String, Integer> matricules = new HashMap<>();
		for (LigneBrute l : donnees) {
			Map<ChampImport.Solde, String> v = new EnumMap<>(ChampImport.Solde.class);
			for (Colonne c : colonnes) {
				if (c.champ() != null && c.index() < l.cellules().size()) {
					String t = l.cellules().get(c.index()) == null ? "" : l.cellules().get(c.index()).strip();
					if (!t.isEmpty()) {
						v.putIfAbsent(ChampImport.Solde.valueOf(c.champ()), t);
					}
				}
			}
			valeurs.add(v);
			if (v.get(MATRICULE) != null) {
				matricules.merge(v.get(MATRICULE).toLowerCase(Locale.ROOT), 1, Integer::sum);
			}
		}
		List<LigneResultat> resultats = new ArrayList<>();
		for (int i = 0; i < donnees.size(); i++) {
			resultats.add(evaluerLigne(donnees.get(i).numero(), valeurs.get(i), enTetes, motifGlobal, auteurFicheId, ctx, matricules));
		}
		return new Evaluation(resultats, List.of(), avertissementsFichier(), totaliser(resultats));
	}

	private LigneResultat evaluerLigne(int numero, Map<ChampImport.Solde, String> v, Map<ChampImport.Solde, String> enTetes,
			String motifGlobal, UUID auteurFicheId, Contexte ctx, Map<String, Integer> matricules) {
		List<Probleme> erreurs = new ArrayList<>();
		List<Probleme> avertissements = new ArrayList<>();
		String matricule = v.get(MATRICULE);
		Collaborateur c = null;
		if (matricule == null) {
			erreurs.add(new Probleme("MATRICULE_OBLIGATOIRE", col(enTetes, MATRICULE), null, "Matricule obligatoire."));
		} else {
			c = ctx.parMatricule.get(matricule.toLowerCase(Locale.ROOT));
			if (matricules.getOrDefault(matricule.toLowerCase(Locale.ROOT), 0) > 1) {
				erreurs.add(new Probleme("MATRICULE_EN_DOUBLE_FICHIER", col(enTetes, MATRICULE), court(matricule),
						"Matricule présent plusieurs fois dans le fichier : « " + court(matricule) + " »."));
			}
			if (c == null) {
				erreurs.add(new Probleme("MATRICULE_INCONNU", col(enTetes, MATRICULE), court(matricule),
						"Matricule inconnu : « " + court(matricule) + " »."));
			} else if (c.getId().equals(auteurFicheId)) {
				erreurs.add(new Probleme("AUTO_AJUSTEMENT_INTERDIT", col(enTetes, MATRICULE), court(matricule),
						"Vous ne pouvez pas ajuster votre propre solde."));
			}
		}
		BigDecimal cible = null;
		String brut = v.get(SOLDE);
		if (brut == null) {
			erreurs.add(new Probleme("SOLDE_OBLIGATOIRE", col(enTetes, SOLDE), null, "Solde obligatoire."));
		} else {
			cible = lireSolde(brut);
			if (cible == null) {
				erreurs.add(new Probleme("SOLDE_INVALIDE", col(enTetes, SOLDE), court(brut), "Solde invalide « " + court(brut)
						+ " » : un nombre de 0 à 365, par pas de 0,5."));
			}
		}
		String motifLigne = v.get(MOTIF);
		if (motifLigne != null && (motifLigne.length() < MOTIF_MIN || motifLigne.length() > MOTIF_MAX)) {
			erreurs.add(new Probleme("MOTIF_INVALIDE", col(enTetes, MOTIF), court(motifLigne),
					"Motif invalide : " + MOTIF_MIN + " à " + MOTIF_MAX + " caractères."));
		}
		BigDecimal actuel = c == null ? null : ctx.acquis.getOrDefault(c.getId(), new BigDecimal("0.0"));
		BigDecimal ecart = null;
		String action;
		if (!erreurs.isEmpty() || c == null || cible == null) {
			action = "ERREUR";
		} else {
			ecart = cible.subtract(actuel);
			action = ecart.signum() == 0 ? "INCHANGE" : "AJUSTEMENT";
			if ("ARCHIVE".equalsIgnoreCase(c.getStatut())) {
				avertissements.add(new Probleme("COLLABORATEUR_ARCHIVE", null, null, "Collaborateur archivé : le solde est ajusté malgré tout."));
			}
			BigDecimal reserve = ctx.reserves.getOrDefault(c.getId(), new BigDecimal("0.0"));
			if (cible.compareTo(reserve) < 0) {
				avertissements.add(new Probleme("CIBLE_INFERIEURE_RESERVE", null, null,
						"Le solde cible est inférieur aux jours réservés par des demandes en cours."));
			}
		}
		String motif = motifLigne != null ? motifLigne : motifGlobal;
		String nom = c == null ? null : ((c.getPrenom() == null ? "" : c.getPrenom()) + " " + (c.getNom() == null ? "" : c.getNom())).trim();
		return new LigneResultat(numero, matricule, nom, action, erreurs, avertissements, List.of(), actuel, cible, ecart,
				motif, c == null ? null : c.getId(), null, new HashMap<>(), false);
	}

	/** Cible : 12.5 ou 12,5 (pas d'arrondi), de 0 à 365, multiple de 0,5 ; {@code null} sinon. */
	static BigDecimal lireSolde(String brut) {
		String t = brut.strip();
		if (!DECIMAL.matcher(t).matches()) {
			return null;
		}
		BigDecimal v = new BigDecimal(t.replace(',', '.'));
		if (v.compareTo(MAX) > 0 || v.multiply(new BigDecimal("2")).stripTrailingZeros().scale() > 0) {
			return null;
		}
		return v.setScale(1);
	}

	private static Totaux totaliser(List<LigneResultat> lignes) {
		int ajustements = 0;
		int inchanges = 0;
		int erreurs = 0;
		int avert = 0;
		for (LigneResultat l : lignes) {
			switch (l.action()) {
				case "AJUSTEMENT" -> ajustements++;
				case "INCHANGE" -> inchanges++;
				default -> erreurs++;
			}
			if (!l.avertissements().isEmpty()) {
				avert++;
			}
		}
		return new Totaux(lignes.size(), 0, 0, inchanges, ajustements, erreurs, avert);
	}

	private static String col(Map<ChampImport.Solde, String> enTetes, ChampImport.Solde champ) {
		String e = enTetes.get(champ);
		return e == null || e.isBlank() ? champ.libelle() : e;
	}

	private static String court(String v) {
		return v.length() > 60 ? v.substring(0, 60) + "…" : v;
	}

	// ─── Application ───────────────────────────────────────────────────────────

	/** Bilan : ajustements écrits, lignes devenues égales, et résultat par matricule (écart réel ou null). */
	public record Application(int ajustements, int inchanges, Map<String, BigDecimal> ecartsReels) {
	}

	/**
	 * Applique les ajustements en lot (ADR § 4 : soldes verrouillés dans l'ordre croissant des identifiants, écart recalculé
	 * sur l'acquis frais) ; les notifications partent après le commit, sans requête par ligne.
	 */
	@Transactional
	public Application appliquer(List<LigneResultat> ajustables, UUID importId, String motifGlobal, AuteurParametre auteur,
			Contexte ctx) {
		List<SoldeLotService.Ajustement> demandes = new ArrayList<>();
		Map<UUID, LigneResultat> parFiche = new HashMap<>();
		for (LigneResultat l : ajustables) {
			demandes.add(new SoldeLotService.Ajustement(l.collaborateurId(), l.soldeCible(), l.motif() != null ? l.motif() : motifGlobal));
			parFiche.put(l.collaborateurId(), l);
		}
		List<SoldeLotService.Resultat> resultats = lotService.ajuster(demandes, importId, auteur);
		int ajustements = 0;
		int inchanges = 0;
		Map<String, BigDecimal> reels = new HashMap<>();
		List<Runnable> notifications = new ArrayList<>();
		for (SoldeLotService.Resultat r : resultats) {
			String matricule = parFiche.get(r.collaborateurId()).matricule().toLowerCase(Locale.ROOT);
			reels.put(matricule, r.ecart());
			if (r.ecart() == null) {
				inchanges++;
				continue;
			}
			ajustements++;
			Collaborateur cible = ctx.parMatricule.get(matricule);
			notifications.add(() -> notificationPublisher.notifierSoldeAjuste(cible, r.ecart(), r.soldeApres(), r.motif()));
		}
		ApresCommit.executer(() -> notifications.forEach(Runnable::run));
		return new Application(ajustements, inchanges, reels);
	}
}
