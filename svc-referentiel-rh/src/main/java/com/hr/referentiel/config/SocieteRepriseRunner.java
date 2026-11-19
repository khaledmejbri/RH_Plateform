package com.hr.referentiel.config;

import com.hr.referentiel.service.IdentiteSocieteClient;
import com.hr.referentiel.service.SocieteDefautResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Reprise multi-société de {@code svc-referentiel-rh} (§ 9.2.3) : s'exécute en {@code @Order(0)} avant tout autre runner.
 * <ol>
 *   <li>Obtient l'identifiant de la société par défaut auprès de {@code svc-identite-acces} (réessais 5 s pendant 5 min).</li>
 *   <li>Sous verrou consultatif transactionnel {@code pg_advisory_xact_lock(hashtext('reprise-societe'))}, rattache toutes
 *       les lignes {@code societe_id IS NULL} à cette société, pose {@code NOT NULL}, puis remplace les contraintes
 *       d'unicité existantes découvertes dynamiquement dans le catalogue PostgreSQL.</li>
 * </ol>
 */
@Component
@Order(0)
public class SocieteRepriseRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SocieteRepriseRunner.class);

	/** Liste ordonnée des 19 tables métier existantes du référentiel à reprendre. */
	static final List<String> TABLES_METIER = List.of(
			"rh_unite_organisation",
			"rh_collaborateur",
			"rh_demande_administrative",
			"rh_demande_admin_workflow_history",
			"rh_demande_document_administratif",
			"rh_demande_formation",
			"rh_formation_workflow_history",
			"rh_plainte",
			"rh_numero_sequence",
			"rh_jour_ferie",
			"rh_parametres",
			"rh_acquisition_mois",
			"rh_solde_conge",
			"rh_mouvement_solde_conge",
			"rh_parametre_valeur",
			"rh_parametre_journal",
			"rh_import",
			"rh_import_contenu",
			"rh_import_ligne"
	);

	private final JdbcTemplate jdbc;
	private final TransactionTemplate transactionTemplate;
	private final IdentiteSocieteClient identiteSocieteClient;
	private final SocieteDefautResolver societeDefautResolver;

	public SocieteRepriseRunner(
			JdbcTemplate jdbc,
			TransactionTemplate transactionTemplate,
			IdentiteSocieteClient identiteSocieteClient,
			SocieteDefautResolver societeDefautResolver) {
		this.jdbc = jdbc;
		this.transactionTemplate = transactionTemplate;
		this.identiteSocieteClient = identiteSocieteClient;
		this.societeDefautResolver = societeDefautResolver;
	}

	@Override
	public void run(ApplicationArguments args) {
		UUID societeDefautId = identiteSocieteClient.attendreSocieteParCode(
				societeDefautResolver.getCodeDefaut(),
				Duration.ofMinutes(5),
				Duration.ofSeconds(5));
		societeDefautResolver.enregistrerIdDefaut(societeDefautId);
		executerPourSociete(societeDefautId);
	}

	public void executerPourSociete(UUID societeDefautId) {
		String produit = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
		if (produit == null || !produit.toLowerCase(Locale.ROOT).contains("postgres")) {
			log.info("Base non PostgreSQL ({}) : reprise catalogue ignorée", produit);
			return;
		}
		try {
			transactionTemplate.executeWithoutResult(status -> {
				jdbc.execute("SELECT pg_advisory_xact_lock(hashtext('reprise-societe'))");

				for (String table : TABLES_METIER) {
					if (!tableExiste(table)) {
						log.info("Reprise table {} : absente, ignorée", table);
						continue;
					}
					jdbc.execute("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS societe_id UUID");
					int maj = jdbc.update("UPDATE " + table + " SET societe_id = ? WHERE societe_id IS NULL", societeDefautId);
					jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN societe_id SET NOT NULL");
					log.info("Reprise table {} : {} ligne(s) rattachée(s) à {}", table, maj, societeDefautId);
				}

				alignerContraintesUnicite();
				reprendreTableTechniqueJourFerieAmorcage(societeDefautId);
			});
		} catch (RuntimeException e) {
			throw new IllegalStateException("Échec de la reprise multi-société de svc-referentiel-rh : " + e.getMessage(), e);
		}
	}

	private void alignerContraintesUnicite() {
		// Contraintes uniques mono-colonne remplacées par des index composites (societe_id, ...)
		supprimerUnicitesSansSociete("rh_collaborateur", List.of("matricule"));
		supprimerUnicitesSansSociete("rh_collaborateur", List.of("compte_utilisateur_id"));
		supprimerUnicitesSansSociete("rh_unite_organisation", List.of("code"));
		supprimerUnicitesSansSociete("rh_demande_administrative", List.of("numero"));
		supprimerUnicitesSansSociete("rh_demande_document_administratif", List.of("numero"));
		supprimerUnicitesSansSociete("rh_demande_formation", List.of("numero"));
		supprimerUnicitesSansSociete("rh_plainte", List.of("numero_ticket"));
		supprimerUnicitesSansSociete("rh_jour_ferie", List.of("date_jour"));

		// Contraintes multi-colonnes sans societe_id en tête
		supprimerUnicitesSansSociete("rh_mouvement_solde_conge", List.of("collaborateur_id", "mois_acquisition"));
		supprimerUnicitesSansSociete("rh_mouvement_solde_conge", List.of("demande_id", "type"));
		supprimerUnicitesSansSociete("rh_parametre_valeur", List.of("referentiel", "code"));
		supprimerUnicitesSansSociete("rh_parametre_valeur", List.of("referentiel", "libelle_normalise"));

		// Clés primaires de séquences / mois d'acquisition passées à (societe_id, ...)
		assurerClePrimaireComposee("rh_numero_sequence", "cle");
		assurerClePrimaireComposee("rh_acquisition_mois", "mois");

		// Nouveaux index uniques préfixés par societe_id
		creerIndexUniqueSiTableExiste("rh_collaborateur", "idx_collab_societe_matricule", "societe_id, matricule");
		creerIndexUniqueSiTableExiste("rh_collaborateur", "idx_collab_societe_compte", "societe_id, compte_utilisateur_id");
		creerIndexUniqueSiTableExiste("rh_unite_organisation", "idx_unite_societe_code", "societe_id, code");
		creerIndexUniqueSiTableExiste("rh_demande_administrative", "idx_demande_societe_numero", "societe_id, numero");
		creerIndexUniqueSiTableExiste("rh_demande_document_administratif", "idx_doc_dem_societe_numero", "societe_id, numero");
		creerIndexUniqueSiTableExiste("rh_demande_formation", "idx_demande_formation_societe_numero", "societe_id, numero");
		creerIndexUniqueSiTableExiste("rh_plainte", "idx_plainte_societe_numero", "societe_id, numero_ticket");
		creerIndexUniqueSiTableExiste("rh_jour_ferie", "uk_jour_ferie_societe_date", "societe_id, date_jour");
		creerIndexUniqueSiTableExiste("rh_parametres", "uk_parametres_conges_societe", "societe_id");
		creerIndexUniqueSiTableExiste("rh_mouvement_solde_conge", "uk_mvt_solde_societe_acquisition", "societe_id, collaborateur_id, mois_acquisition");
		creerIndexUniqueSiTableExiste("rh_mouvement_solde_conge", "uk_mvt_solde_societe_demande", "societe_id, demande_id, type");
		creerIndexUniqueSiTableExiste("rh_parametre_valeur", "uk_rh_parametre_societe_ref_code", "societe_id, referentiel, code");
		creerIndexUniqueSiTableExiste("rh_parametre_valeur", "uk_rh_parametre_societe_ref_libelle", "societe_id, referentiel, libelle_normalise");
	}

	private void creerIndexUniqueSiTableExiste(String table, String nomIndex, String colonnes) {
		if (tableExiste(table)) {
			jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + nomIndex + " ON " + table + " (" + colonnes + ")");
		}
	}

	private boolean tableExiste(String table) {
		Boolean exists = jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?)",
				Boolean.class, table);
		return Boolean.TRUE.equals(exists);
	}

	/**
	 * Découvre et supprime dynamiquement dans le catalogue PostgreSQL ({@code pg_constraint} et {@code pg_index})
	 * toute contrainte ou tout index UNIQUE portant exactement sur les colonnes données (sans {@code societe_id}).
	 */
	private void supprimerUnicitesSansSociete(String table, List<String> colonnes) {
		String colsCsv = String.join(",", colonnes.stream().sorted().toList());
		// 1. Contraintes UNIQUE (contype = 'u')
		List<String> contraintes = jdbc.queryForList(
				"""
				SELECT c.conname
				FROM pg_constraint c
				JOIN pg_class t ON t.oid = c.conrelid
				JOIN pg_namespace n ON n.oid = t.relnamespace
				WHERE n.nspname = current_schema()
				  AND t.relname = ?
				  AND c.contype = 'u'
				  AND (
				      SELECT string_agg(a.attname, ',' ORDER BY a.attname)
				      FROM unnest(c.conkey) k(attnum)
				      JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
				  ) = ?
				""",
				String.class, table, colsCsv);
		for (String conname : contraintes) {
			jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS \"" + conname + "\"");
			log.info("Reprise {} : contrainte unique {} ({}) supprimée", table, conname, colsCsv);
		}

		// 2. Index UNIQUE hors contrainte
		List<String> indexs = jdbc.queryForList(
				"""
				SELECT ic.relname
				FROM pg_index ix
				JOIN pg_class t ON t.oid = ix.indrelid
				JOIN pg_namespace n ON n.oid = t.relnamespace
				JOIN pg_class ic ON ic.oid = ix.indexrelid
				LEFT JOIN pg_constraint c ON c.conindid = ix.indexrelid
				WHERE n.nspname = current_schema()
				  AND t.relname = ?
				  AND ix.indisunique = true
				  AND ix.indisprimary = false
				  AND c.oid IS NULL
				  AND (
				      SELECT string_agg(a.attname, ',' ORDER BY a.attname)
				      FROM unnest(ix.indkey) k(attnum)
				      JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
				  ) = ?
				""",
				String.class, table, colsCsv);
		for (String indexName : indexs) {
			jdbc.execute("DROP INDEX IF EXISTS \"" + indexName + "\"");
			log.info("Reprise {} : index unique {} ({}) supprimé", table, indexName, colsCsv);
		}
	}

	private void reprendreTableTechniqueJourFerieAmorcage(UUID societeDefautId) {
		if (!tableExiste("rh_jour_ferie_amorcage")) {
			return;
		}
		jdbc.execute("ALTER TABLE rh_jour_ferie_amorcage ADD COLUMN IF NOT EXISTS societe_id UUID");
		jdbc.update("UPDATE rh_jour_ferie_amorcage SET societe_id = ? WHERE societe_id IS NULL", societeDefautId);
		jdbc.execute("ALTER TABLE rh_jour_ferie_amorcage ALTER COLUMN societe_id SET NOT NULL");
		assurerClePrimaireComposee("rh_jour_ferie_amorcage", "annee");
	}

	private void assurerClePrimaireComposee(String table, String colonneCle) {
		if (!tableExiste(table)) {
			return;
		}
		String pkName = jdbc.query(
				"""
				SELECT c.conname,
				       (SELECT string_agg(a.attname, ',' ORDER BY u.ord)
				        FROM unnest(c.conkey) WITH ORDINALITY u(attnum, ord)
				        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = u.attnum) AS cols
				FROM pg_constraint c
				JOIN pg_class t ON t.oid = c.conrelid
				JOIN pg_namespace n ON n.oid = t.relnamespace
				WHERE n.nspname = current_schema()
				  AND t.relname = ?
				  AND c.contype = 'p'
				""",
				rs -> rs.next() ? rs.getString("conname") + "|" + rs.getString("cols") : null,
				table);
		String attendu = "societe_id," + colonneCle;
		if (pkName != null && !pkName.endsWith("|" + attendu)) {
			String conname = pkName.substring(0, pkName.indexOf('|'));
			jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT \"" + conname + "\"");
			jdbc.execute("ALTER TABLE " + table + " ADD PRIMARY KEY (societe_id, " + colonneCle + ")");
			log.info("Reprise {} : clé primaire passée à ({})", table, attendu);
		}
	}
}
