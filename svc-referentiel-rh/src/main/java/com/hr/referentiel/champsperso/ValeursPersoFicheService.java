package com.hr.referentiel.champsperso;

import com.hr.referentiel.champsperso.DefinitionsPerso.Def;
import com.hr.referentiel.champsperso.ValeursPersoDtos.ChampDesactive;
import com.hr.referentiel.champsperso.ValeursPersoDtos.ChampMoi;
import com.hr.referentiel.champsperso.ValeursPersoDtos.ChampValeur;
import com.hr.referentiel.champsperso.ValeursPersoDtos.ChoixListe;
import com.hr.referentiel.champsperso.ValeursPersoDtos.EcrireValeurs;
import com.hr.referentiel.champsperso.ValeursPersoDtos.LigneJournalValeur;
import com.hr.referentiel.champsperso.ValeursPersoDtos.MesChamps;
import com.hr.referentiel.champsperso.ValeursPersoDtos.PageJournalValeurs;
import com.hr.referentiel.champsperso.ValeursPersoDtos.ValeursFiche;
import com.hr.referentiel.champsperso.ValeursPersoJdbc.Ecriture;
import com.hr.referentiel.champsperso.ValeursPersoJdbc.LigneJournal;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.importation.parametrage.ImportParametrageVues;
import com.hr.referentiel.importation.parametrage.ParametrageImport.ValeurListe;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.service.AuteurParametreResolver;
import com.hr.referentiel.service.CollaborateurConnecteService;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Valeurs des champs personnalisés d'une fiche, V1 à V4 (ADR partie 2, P2-6) et valeurs initiales de la création. Les
 * définitions sont relues en base sous le verrou {@code FOR SHARE} du type ; la version des valeurs de la fiche
 * ({@code rh_valeur_perso_fiche_etat}) est verrouillée {@code FOR UPDATE}. Identité toujours tirée du JWT.
 */
@Service
public class ValeursPersoFicheService {

	public static final String TYPE = "COLLABORATEURS";
	static final String RAISON_SOI = "Vous ne pouvez pas modifier vos propres informations.";
	static final String RAISON_LECTURE = "Consultation uniquement.";

	private final CollaborateurRepository collaborateurRepository;
	private final CollaborateurConnecteService connecteService;
	private final AuteurParametreResolver auteurResolver;
	private final DefinitionsPerso definitions;
	private final ValeursPersoJdbc jdbc;
	private final JdbcTemplate sql;
	private final Clock clock;

	public ValeursPersoFicheService(CollaborateurRepository collaborateurRepository, CollaborateurConnecteService connecteService,
			AuteurParametreResolver auteurResolver, DefinitionsPerso definitions, ValeursPersoJdbc jdbc, JdbcTemplate sql, Clock clock) {
		this.collaborateurRepository = collaborateurRepository;
		this.connecteService = connecteService;
		this.auteurResolver = auteurResolver;
		this.definitions = definitions;
		this.jdbc = jdbc;
		this.sql = sql;
		this.clock = clock;
	}

	// ─── V1 : lire (back-office) ───────────────────────────────────────────────

	@Transactional(readOnly = true)
	public ValeursFiche lire(UUID id, Jwt jwt, boolean peutEcrire) {
		exiger(id);
		Collaborateur moi = connecteService.resoudre(jwt).orElse(null);
		return vue(id, definitions.lire(TYPE), moi != null && moi.getId().equals(id), peutEcrire);
	}

	private ValeursFiche vue(UUID id, Map<String, Def> defs, boolean soi, boolean peutEcrire) {
		Map<String, ValeurTypee> valeurs = jdbc.fiche(id);
		List<ChampValeur> champs = new ArrayList<>();
		List<ChampDesactive> desactives = new ArrayList<>();
		for (Def d : defs.values()) {
			ValeurTypee v = valeurs.get(d.champ().getCode());
			if (d.champ().isActif()) {
				champs.add(champ(d, v));
			} else if (v != null) {
				desactives.add(new ChampDesactive(d.champ().getCode(), d.champ().getLibelle(), d.champ().getTypeValeur().name(),
						FormatValeurPerso.canonique(v), FormatValeurPerso.affichage(v, d.definition())));
			}
		}
		boolean modifiable = peutEcrire && !soi;
		return new ValeursFiche(id, jdbc.version(id), modifiable, modifiable ? null : soi ? RAISON_SOI : RAISON_LECTURE, champs,
				desactives);
	}

	private static ChampValeur champ(Def d, ValeurTypee v) {
		ChampPerso c = d.champ();
		List<ChoixListe> choix = null;
		boolean inactive = false;
		if (c.getTypeValeur() == TypeValeurPerso.LISTE) {
			String courant = v == null ? null : v.listeCode();
			choix = new ArrayList<>();
			for (ValeurListe l : d.definition().valeursListe()) {
				if (l.actif() || l.code().equals(courant)) {
					choix.add(new ChoixListe(l.code(), l.libelle(), l.actif()));
				}
				inactive |= !l.actif() && l.code().equals(courant);
			}
		}
		return new ChampValeur(c.getCode(), c.getLibelle(), c.getTypeValeur().name(), c.getTypeValeur().libelle(),
				ImportParametrageVues.reglesVue(c.regles()), choix, v == null ? null : FormatValeurPerso.canonique(v),
				v == null ? null : FormatValeurPerso.affichage(v, d.definition()), inactive);
	}

	// ─── V2 : écrire ───────────────────────────────────────────────────────────

	@Transactional
	public ValeursFiche ecrire(UUID id, EcrireValeurs req, Jwt jwt, boolean peutEcrire) {
		exiger(id);
		Collaborateur moi = connecteService.resoudre(jwt).orElse(null);
		if (moi != null && moi.getId().equals(id)) {
			throw ReferentielMetierException.forbidden("AUTO_MODIFICATION_INTERDITE", RAISON_SOI);
		}
		if (req == null || req.valeurs() == null) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "Les valeurs à écrire sont obligatoires.");
		}
		if (req.version() == null) {
			throw ReferentielMetierException.badRequest("VERSION_OBLIGATOIRE", "La version des valeurs est obligatoire.");
		}
		Map<String, Def> defs = definitions.lireSousVerrou(TYPE);
		Map<String, ValeurTypee> voulues = controler(req.valeurs(), defs);
		AuteurParametre auteur = auteurResolver.depuis(jwt);
		Instant maintenant = clock.instant();
		jdbc.creerEtats(List.of(id), maintenant, auteur.nom());
		long actuelle = jdbc.verrouillerEtats(List.of(id)).getOrDefault(id, 0L);
		if (actuelle != req.version()) {
			throw ReferentielMetierException.conflict("VALEURS_MODIFIEES_ENTRE_TEMPS",
					"Les informations complémentaires ont été modifiées par quelqu'un d'autre. Rechargez la fiche.");
		}
		appliquer(id, voulues, defs, OrigineValeurPerso.SAISIE, auteur, maintenant);
		return vue(id, defs, false, peutEcrire);
	}

	/**
	 * Contrôle de toutes les valeurs envoyées : champ inconnu (400), erreurs de valeur toutes listées (400), champ inactif
	 * (422). Une valeur vide = effacer ({@code null} dans le résultat).
	 */
	public Map<String, ValeurTypee> controler(Map<String, Object> brutes, Map<String, Def> defs) {
		for (String code : brutes.keySet()) {
			if (!defs.containsKey(code)) {
				throw new ReferentielMetierException("CHAMP_INCONNU", "Champ inconnu : " + code + ".", 400, Map.of("champ", code));
			}
		}
		Map<String, ValeurTypee> voulues = new HashMap<>();
		List<Map<String, String>> erreurs = new ArrayList<>();
		String inactif = null;
		for (Map.Entry<String, Object> e : brutes.entrySet()) {
			Def d = defs.get(e.getKey());
			if (!d.champ().isActif()) {
				inactif = inactif == null ? e.getKey() : inactif;
				continue;
			}
			ControleValeurPerso.Resultat r = ControleValeurPerso.lire(d.definition(), enTexte(e.getValue()),
					ControleValeurPerso.Mode.SAISIE, d.champ().getLibelle());
			if (r.probleme() != null) {
				erreurs.add(Map.of("champ", e.getKey(), "code", r.probleme().code(), "message", r.probleme().message()));
			} else {
				voulues.put(e.getKey(), r.valeur());
			}
		}
		if (!erreurs.isEmpty()) {
			Map<String, String> premiere = erreurs.get(0);
			throw new ReferentielMetierException(premiere.get("code"), premiere.get("message"), 400,
					Map.of("champ", premiere.get("champ"), "erreurs", erreurs));
		}
		if (inactif != null) {
			throw new ReferentielMetierException("CHAMP_INACTIF", "Le champ « " + defs.get(inactif).champ().getLibelle()
					+ " » est désactivé.", 422, Map.of("champ", inactif));
		}
		return voulues;
	}

	private static String enTexte(Object o) {
		if (o == null) {
			return null;
		}
		return o instanceof BigDecimal b ? b.toPlainString() : o instanceof Number n ? new BigDecimal(n.toString()).toPlainString()
				: o.toString();
	}

	/**
	 * Écrit les différences, journalise (une ligne par champ changé) et passe la version à +1 si quelque chose a changé.
	 * Les lignes d'état de la fiche doivent déjà être créées et verrouillées par l'appelant.
	 */
	public int appliquer(UUID id, Map<String, ValeurTypee> voulues, Map<String, Def> defs, OrigineValeurPerso origine,
			AuteurParametre auteur, Instant maintenant) {
		Map<String, ValeurTypee> actuelles = jdbc.fiche(id);
		List<Ecriture> ajouts = new ArrayList<>();
		List<Ecriture> modifs = new ArrayList<>();
		List<Ecriture> retraits = new ArrayList<>();
		List<LigneJournal> journal = new ArrayList<>();
		for (Map.Entry<String, ValeurTypee> e : voulues.entrySet()) {
			ValeurTypee avant = actuelles.get(e.getKey());
			ValeurTypee apres = e.getValue();
			if (apres == null ? avant == null : apres.memeValeur(avant)) {
				continue;
			}
			Def d = defs.get(e.getKey());
			if (apres == null) {
				retraits.add(new Ecriture(id, e.getKey(), null));
			} else if (avant == null) {
				ajouts.add(new Ecriture(id, e.getKey(), apres));
			} else {
				modifs.add(new Ecriture(id, e.getKey(), apres));
			}
			journal.add(new LigneJournal(id, e.getKey(), d.champ().getLibelle(),
					avant == null ? null : FormatValeurPerso.affichage(avant, d.definition()),
					apres == null ? null : FormatValeurPerso.affichage(apres, d.definition()), origine, null, auteur.compteId(),
					auteur.nom(), maintenant));
		}
		int n = journal.size();
		if (n > 0) {
			jdbc.inserer(ajouts, maintenant);
			jdbc.modifier(modifs, maintenant);
			jdbc.supprimer(retraits);
			jdbc.journaliser(journal);
			jdbc.incrementerVersions(List.of(id), maintenant, auteur.nom());
		}
		return n;
	}

	// ─── Création : valeurs initiales ──────────────────────────────────────────

	/** À appeler AVANT la création de la fiche : mêmes contrôles que V2, rien n'est écrit. */
	public Map<String, ValeurTypee> controlerCreation(Map<String, Object> valeurs, Map<String, Def> defs) {
		return controler(valeurs, defs);
	}

	/** À appeler APRÈS la création (même transaction) : écrit les valeurs, version 1, origine CREATION. */
	public void ecrireCreation(UUID id, Map<String, ValeurTypee> voulues, Map<String, Def> defs, Jwt jwt) {
		AuteurParametre auteur = auteurResolver.depuis(jwt);
		Instant maintenant = clock.instant();
		jdbc.creerEtats(List.of(id), maintenant, auteur.nom());
		jdbc.verrouillerEtats(List.of(id));
		appliquer(id, voulues, defs, OrigineValeurPerso.CREATION, auteur, maintenant);
	}

	// ─── V3 : journal ──────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public PageJournalValeurs journal(UUID id, int page, int taille) {
		exiger(id);
		if (taille < 1 || taille > 100 || page < 0) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "La taille de page doit être comprise entre 1 et 100.");
		}
		Long total = sql.queryForObject("select count(*) from rh_valeur_perso_journal where collaborateur_id = ?", Long.class, id);
		List<LigneJournalValeur> lignes = sql.query("select id, horodatage, auteur_nom, champ_code, champ_libelle, avant, apres, origine "
				+ "from rh_valeur_perso_journal where collaborateur_id = ? order by horodatage desc, id limit ? offset ?", (rs, i) -> {
					Instant h = rs.getTimestamp(2).toInstant();
					OrigineValeurPerso o = OrigineValeurPerso.lire(rs.getString(8));
					return new LigneJournalValeur(rs.getObject(1, UUID.class), h, rs.getString(3), rs.getString(4), rs.getString(5),
							rs.getString(6), rs.getString(7), o.name(), o.libelle(h));
				}, id, taille, (long) page * taille);
		return new PageJournalValeurs(lignes, page, taille, total == null ? 0 : total);
	}

	// ─── V4 : « moi » ──────────────────────────────────────────────────────────

	/**
	 * Valeurs visibles de l'appelant : fiche résolue depuis le JWT uniquement (aucun paramètre lu). Le filtrage
	 * (actif, visible, renseigné) est fait ici, avant la construction du DTO : une valeur non visible n'est jamais lue
	 * dans la réponse.
	 */
	@Transactional(readOnly = true)
	public MesChamps moi(Jwt jwt) {
		Collaborateur moi = connecteService.resoudre(jwt).orElse(null);
		if (moi == null) {
			return new MesChamps(List.of());
		}
		Map<String, ValeurTypee> valeurs = jdbc.fiche(moi.getId());
		List<ChampMoi> champs = new ArrayList<>();
		for (Def d : definitions.lire(TYPE).values()) {
			ValeurTypee v = valeurs.get(d.champ().getCode());
			if (d.champ().isActif() && d.champ().isVisibleCollaborateur() && v != null) {
				champs.add(new ChampMoi(d.champ().getCode(), d.champ().getLibelle(), d.champ().getTypeValeur().name(),
						FormatValeurPerso.affichage(v, d.definition())));
			}
		}
		return new MesChamps(champs);
	}

	private void exiger(UUID id) {
		if (!collaborateurRepository.existsById(id)) {
			throw ReferentielMetierException.notFound("COLLABORATEUR_INTROUVABLE", "Collaborateur introuvable.");
		}
	}
}
