package com.hr.referentiel.config;

import com.hr.referentiel.domain.ReferentielParametre;
import com.hr.referentiel.domain.ValeursInitialesReferentiel;
import com.hr.referentiel.domain.ValeursInitialesReferentiel.Valeur;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.service.NormalisationLibelle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Amorçage et migration des paramètres sans Flyway (ADR-UAT-lot3 § 8). Rejouable à chaque démarrage : une ligne déjà
 * présente n'est jamais réécrite (la RH a pu la modifier), seuls les manques sont comblés.
 *
 * <ol>
 *   <li>élargit {@code rh_collaborateur.niveau_seniorite} à 40 caractères et supprime la contrainte CHECK du type de
 *       document (un type créé par la RH n'est plus dans une énumération) ;</li>
 *   <li>reprend les familles de l'ancienne table {@code rh_famille_metier} (code, libellé et état conservés) ;</li>
 *   <li>insère les valeurs initiales (types de document, de congé, niveaux, familles) et les codes de comportement
 *       manquants (statuts, types) ;</li>
 *   <li>recrée en inactif tout code stocké dans une fiche, un document ou une demande et absent de la table
 *       (critère « 0 objet dont le code n'existe pas »), journalisé « Système (automatique) ».</li>
 * </ol>
 *
 * Tourne avant l'ouverture du serveur web ({@link SmartInitializingSingleton}), sous verrou consultatif PostgreSQL :
 * deux instances qui démarrent ensemble ne s'amorcent pas deux fois.
 */
@Component
public class ParametresAmorceRunner implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(ParametresAmorceRunner.class);

	/** Clé du verrou consultatif PostgreSQL (propre à cet amorçage). */
	static final long VERROU_AMORCAGE = 7_001_903_001L;

	static final int LONGUEUR_CODE = 40;
	static final String CONTRAINTE_TYPE_DOCUMENT = "rh_demande_document_administratif_type_document_check";
	private static final Pattern CODE_VALIDE = Pattern.compile("[A-Z0-9_]{1,40}");

	/** Colonnes qui portent un code d'un référentiel : {@code {référentiel, table, colonne}}. */
	private static final String[][] SOURCES_ORPHELINS = {
			{"TYPE_DOCUMENT", "rh_demande_document_administratif", "type_document"},
			{"NIVEAU_SENIORITE", "rh_collaborateur", "niveau_seniorite"},
			{"FAMILLE_METIER", "rh_collaborateur", "famille_metier_code"}
	};

	private final JdbcTemplate jdbc;
	private final PlatformTransactionManager transactionManager;
	private final ReferentielEvenementsProperties evenementsProperties;
	private final JsonMapper jsonMapper;

	public ParametresAmorceRunner(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
			ReferentielEvenementsProperties evenementsProperties, JsonMapper jsonMapper) {
		this.jdbc = jdbc;
		this.transactionManager = transactionManager;
		this.evenementsProperties = evenementsProperties;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void afterSingletonsInstantiated() {
		amorcer();
	}

	/** Point d'entrée rejouable (aussi appelé par les tests). */
	public void amorcer() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			verrouiller();
			elargirColonnes();
			supprimerContraintes();
			int familles = reprendreFamilles();
			int initiales = insererValeursInitiales();
			int orphelins = rapprocherOrphelins();
			if (familles + initiales + orphelins > 0) {
				log.info("Paramètres amorcés : {} famille(s) reprise(s), {} valeur(s) initiale(s), {} code(s) orphelin(s)"
						+ " recréé(s) inactif(s)", familles, initiales, orphelins);
			}
		});
	}

	// ─── 1. Schéma ────────────────────────────────────────────────────────────

	private void verrouiller() {
		if (estPostgres()) {
			jdbc.execute("select pg_advisory_xact_lock(" + VERROU_AMORCAGE + ")");
		}
	}

	private boolean estPostgres() {
		String produit = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
		return produit != null && produit.toLowerCase(Locale.ROOT).contains("postgres");
	}

	private void elargirColonnes() {
		elargir("rh_collaborateur", "niveau_seniorite", LONGUEUR_CODE);
	}

	/** ALTER seulement si la colonne est plus étroite (idempotent) : {@code ddl-auto=update} n'élargit jamais. */
	private void elargir(String table, String colonne, int longueur) {
		if (!tableExiste(table)) {
			return;
		}
		List<Integer> actuelle = jdbc.query(
				"select character_maximum_length from information_schema.columns "
						+ "where lower(table_name) = ? and lower(column_name) = ?",
				(rs, i) -> rs.getObject(1) == null ? null : rs.getInt(1), table, colonne);
		if (!actuelle.isEmpty() && actuelle.get(0) != null && actuelle.get(0) < longueur) {
			jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN " + colonne + " TYPE varchar(" + longueur + ")");
			log.info("Colonne {}.{} élargie de {} à {}", table, colonne, actuelle.get(0), longueur);
		}
	}

	private void supprimerContraintes() {
		if (tableExiste("rh_demande_document_administratif")) {
			jdbc.execute("ALTER TABLE rh_demande_document_administratif DROP CONSTRAINT IF EXISTS "
					+ CONTRAINTE_TYPE_DOCUMENT);
		}
	}

	boolean tableExiste(String table) {
		Integer n = jdbc.queryForObject(
				"select count(*) from information_schema.tables where lower(table_name) = ?", Integer.class, table);
		return n != null && n > 0;
	}

	// ─── 2. Familles ──────────────────────────────────────────────────────────

	/**
	 * Reprend {@code rh_famille_metier} (code, libellé, état). Déviation assumée de l'ADR : le cadenas « système »
	 * n'est pas conservé, il interdirait de désactiver les familles « assainissement » que le client doit pouvoir
	 * remplacer. Elles restent non supprimables (marquées utilisées d'office).
	 */
	private int reprendreFamilles() {
		if (!tableExiste("rh_famille_metier")) {
			return 0;
		}
		List<Map<String, Object>> anciennes = jdbc.queryForList(
				"select code, libelle, actif from rh_famille_metier order by libelle");
		int rang = 1;
		int inserees = 0;
		for (Map<String, Object> f : anciennes) {
			String code = String.valueOf(f.get("code")).trim().toUpperCase(Locale.ROOT);
			if (!CODE_VALIDE.matcher(code).matches()) {
				log.warn("Famille ignorée (code hors format) : {}", code);
				continue;
			}
			boolean actif = Boolean.TRUE.equals(f.get("actif"));
			String libelle = tronquer(String.valueOf(f.get("libelle")).trim(), ParametreLimites.LIBELLE_MAX);
			if (inserer("FAMILLE_METIER", code, libelle, null, "NEUTRE", rang++, actif, false, true, null, null, null)) {
				inserees++;
			}
		}
		return inserees;
	}

	// ─── 3. Valeurs initiales et codes de comportement ──────────────────────────────

	private int insererValeursInitiales() {
		List<Valeur> valeurs = ValeursInitialesReferentiel.toutes(evenementsProperties::delaiSlaHeuresPourDocument);
		int inserees = 0;
		for (Valeur v : valeurs) {
			if (inserer(v.referentiel(), v.code(), v.libelle(), null, v.couleur(), v.ordre(), true, v.systeme(),
					v.utilisee(), v.slaHeures(), v.pieceJointeObligatoire(), v.pieceJointeVerrouillee())) {
				inserees++;
			} else if (!codeExiste(v.referentiel(), v.code())) {
				// Le code est libre mais son libellé par défaut est déjà pris (valeur saisie ou reprise) : on ne
				// l'ignore pas en silence, on lève l'ambiguïté avec le code, comme pour les orphelins.
				String avecCode = tronquer(v.libelle(), ParametreLimites.LIBELLE_MAX - v.code().length() - 3)
						+ " (" + v.code() + ")";
				log.warn("Valeur initiale {}.{} : libellé « {} » déjà pris, créée sous « {} »", v.referentiel(),
						v.code(), v.libelle(), avecCode);
				if (inserer(v.referentiel(), v.code(), avecCode, null, v.couleur(), v.ordre(), true, v.systeme(),
						v.utilisee(), v.slaHeures(), v.pieceJointeObligatoire(), v.pieceJointeVerrouillee())) {
					inserees++;
				} else {
					log.error("Valeur initiale {}.{} non créée (libellé « {} » toujours en conflit)", v.referentiel(),
							v.code(), avecCode);
				}
			}
		}
		signalerCodesDisparus(valeurs);
		return inserees;
	}

	/** Un code de comportement présent en base mais plus dans le code est laissé en place (jamais supprimé). */
	private void signalerCodesDisparus(List<Valeur> valeurs) {
		Map<String, List<String>> attendus = ValeursInitialesReferentiel.codesParReferentiel(valeurs);
		for (ReferentielParametre r : ReferentielParametre.values()) {
			if (!r.estComportement()) {
				continue;
			}
			List<String> presents = jdbc.queryForList(
					"select code from rh_parametre_valeur where referentiel = ?", String.class, r.name());
			for (String code : presents) {
				if (!attendus.getOrDefault(r.name(), List.of()).contains(code)) {
					log.warn("Code de comportement {}.{} absent du code applicatif : laissé en place", r.name(), code);
				}
			}
		}
	}

	// ─── 4. Orphelins ─────────────────────────────────────────────────────────

	private int rapprocherOrphelins() {
		int total = 0;
		for (String[] s : SOURCES_ORPHELINS) {
			if (!tableExiste(s[1])) {
				continue;
			}
			List<String> codes = jdbc.queryForList(
					"select distinct " + s[2] + " from " + s[1] + " where " + s[2] + " is not null and " + s[2] + " <> ''",
					String.class);
			total += creerOrphelins(s[0], codes);
		}
		if (tableExiste("rh_demande_administrative")) {
			total += creerOrphelins("TYPE_CONGE", typesCongeStockes());
		}
		return total;
	}

	/** Codes {@code type_conge} stockés dans le jsonb {@code contenu} (lecture indépendante du moteur SQL). */
	private List<String> typesCongeStockes() {
		Set<String> codes = new HashSet<>();
		for (String contenu : jdbc.queryForList(
				"select contenu from rh_demande_administrative where type_demande = 'CONGE'", String.class)) {
			if (contenu == null || contenu.isBlank()) {
				continue;
			}
			try {
				JsonNode n = jsonMapper.readTree(contenu);
				JsonNode t = n.get("type_conge");
				if (t != null && !t.isNull() && !t.asString().isBlank()) {
					codes.add(t.asString());
				}
			} catch (RuntimeException e) {
				log.warn("Contenu de demande illisible lors du rapprochement des types de congé : {}", e.getMessage());
			}
		}
		return new ArrayList<>(codes);
	}

	private int creerOrphelins(String referentiel, List<String> codesStockes) {
		Set<String> connus = new HashSet<>(jdbc.queryForList(
				"select code from rh_parametre_valeur where referentiel = ?", String.class, referentiel));
		Set<String> orphelins = new HashSet<>();
		for (String brut : codesStockes) {
			String code = brut.trim().toUpperCase(Locale.ROOT);
			if (!connus.contains(code)) {
				if (CODE_VALIDE.matcher(code).matches()) {
					orphelins.add(code);
				} else {
					log.warn("Code {} « {} » hors format : non rapproché", referentiel, brut);
				}
			}
		}
		int crees = 0;
		for (String code : new java.util.TreeSet<>(orphelins)) {
			String libelle = tronquer(ValeursInitialesReferentiel.libelleParDefaut(code), ParametreLimites.LIBELLE_MAX);
			boolean sla = "TYPE_DOCUMENT".equals(referentiel);
			Integer slaHeures = sla ? evenementsProperties.delaiSlaHeuresPourDocument(code, 24) : null;
			Boolean pj = "TYPE_CONGE".equals(referentiel) ? Boolean.FALSE : null;
			boolean cree = inserer(referentiel, code, libelle, null, "NEUTRE", prochainOrdre(referentiel), false, false,
					true, slaHeures, pj, pj);
			if (!cree) {
				// libellé déjà pris par une autre valeur : on lève l'ambiguïté avec le code
				String avecCode = tronquer(libelle, ParametreLimites.LIBELLE_MAX - code.length() - 3) + " (" + code + ")";
				cree = inserer(referentiel, code, avecCode, null, "NEUTRE", prochainOrdre(referentiel), false, false,
						true, slaHeures, pj, pj);
			}
			if (cree) {
				crees++;
				journaliserOrphelin(referentiel, code, libelle);
				log.warn("Code orphelin {}.{} recréé inactif (utilisé par des données existantes)", referentiel, code);
			}
		}
		return crees;
	}

	private void journaliserOrphelin(String referentiel, String code, String libelle) {
		String apres = jsonMapper.writeValueAsString(Map.of("libelle", libelle, "actif", false,
				"origine", "rapprochement automatique d'un code déjà stocké"));
		jdbc.update("insert into rh_parametre_journal (id, referentiel, code, action, avant_json, apres_json, "
						+ "auteur_compte_id, auteur_nom, horodatage) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), referentiel, code, "CREATION", null, apres, null,
				AuteurParametre.NOM_SYSTEME, Timestamp.from(Instant.now()));
	}

	// ─── Utilitaires ──────────────────────────────────────────────────────────

	private int prochainOrdre(String referentiel) {
		Integer max = jdbc.queryForObject(
				"select max(ordre) from rh_parametre_valeur where referentiel = ?", Integer.class, referentiel);
		return (max == null ? 0 : max) + 1;
	}

	/**
	 * Insère une ligne si ni son code ni son libellé normalisé n'existent dans le référentiel. Renvoie vrai si elle a
	 * été insérée. Une ligne existante n'est jamais modifiée.
	 */
	private boolean inserer(String referentiel, String code, String libelle, String description, String couleur,
			int ordre, boolean actif, boolean systeme, boolean utilisee, Integer slaHeures, Boolean pjObligatoire,
			Boolean pjVerrouillee) {
		String normalise = NormalisationLibelle.normaliser(libelle);
		Integer deja = jdbc.queryForObject(
				"select count(*) from rh_parametre_valeur where referentiel = ? and (code = ? or libelle_normalise = ?)",
				Integer.class, referentiel, code, normalise);
		if (deja != null && deja > 0) {
			return false;
		}
		Instant maintenant = Instant.now();
		ReferentielParametre ref = ReferentielParametre.parse(referentiel).orElseThrow();
		jdbc.update("insert into rh_parametre_valeur (id, referentiel, code, nature, libelle, libelle_normalise, "
						+ "description, couleur, ordre, actif, systeme, premiere_utilisation_le, version, cree_le, "
						+ "modifie_le, sla_heures, piece_jointe_obligatoire, piece_jointe_verrouillee) "
						+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), referentiel, code, ref.getNature().name(), libelle, normalise, description,
				couleur, ordre, actif, systeme, utilisee ? Timestamp.from(maintenant) : null,
				Timestamp.from(maintenant), Timestamp.from(maintenant), slaHeures, pjObligatoire, pjVerrouillee);
		return true;
	}

	private boolean codeExiste(String referentiel, String code) {
		Integer n = jdbc.queryForObject(
				"select count(*) from rh_parametre_valeur where referentiel = ? and code = ?", Integer.class,
				referentiel, code);
		return n != null && n > 0;
	}

	private static String tronquer(String texte, int max) {
		return texte.length() <= max ? texte : texte.substring(0, max).trim();
	}

	/** Limites partagées avec le service (évite une dépendance du runner vers le service). */
	static final class ParametreLimites {
		static final int LIBELLE_MAX = 80;

		private ParametreLimites() {
		}
	}
}
