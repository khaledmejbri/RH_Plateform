package com.hr.workflow.config;

import com.hr.workflow.domain.ActionJournalModele;
import com.hr.workflow.domain.DecisionTache;
import com.hr.workflow.domain.ModeActivation;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.OrigineDelegation;
import com.hr.workflow.domain.RaisonSautEtape;
import com.hr.workflow.domain.ResultatEtapeExecution;
import com.hr.workflow.domain.StatutAppelEffet;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.domain.TypeTache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Aligne les contraintes CHECK, index uniques partiels, index sur expressions et index de performance
 * de {@code workflow_db} (§ 9.4 et D-W3-01, sans {@code societe_id}).
 * S'exécute avant {@code ModelesAmorceRunner} et avant l'ouverture du HTTP / Kafka ({@code @Order(100)}).
 */
@Component
@Order(100)
public class WorkflowContraintesRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(WorkflowContraintesRunner.class);

	private final JdbcTemplate jdbcTemplate;

	public WorkflowContraintesRunner(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		appliquerContraintesEtIndex();
	}

	/** Visible pour les tests. */
	public void appliquerContraintesEtIndex() {
		// Contraintes CHECK alignées sur les enums
		recreerCheck("wf_activation", "wf_activation_mode_check", "mode", ModeActivation.values());
		recreerCheck("wf_modele_version", "wf_modele_version_statut_check", "statut", StatutVersion.values());
		recreerCheck("wf_journal_modele", "wf_journal_modele_action_check", "action", ActionJournalModele.values());
		recreerCheck("wf_instance", "wf_instance_statut_check", "statut", StatutInstance.values());
		recreerCheck("wf_etape_execution", "wf_etape_execution_statut_check", "statut", StatutEtapeExecution.values());
		recreerCheck("wf_etape_execution", "wf_etape_execution_raison_saut_check", "raison_saut", RaisonSautEtape.values());
		recreerCheck("wf_etape_execution", "wf_etape_execution_resultat_check", "resultat", ResultatEtapeExecution.values());
		recreerCheck("wf_tache", "wf_tache_type_check", "type", TypeTache.values());
		recreerCheck("wf_tache", "wf_tache_statut_check", "statut", StatutTache.values());
		recreerCheck("wf_tache", "wf_tache_decision_check", "decision", DecisionTache.values());
		recreerCheck("wf_tache_candidat", "wf_tache_candidat_origine_check", "origine", OrigineCandidat.values());
		recreerCheck("wf_delegation", "wf_delegation_origine_check", "origine", OrigineDelegation.values());
		recreerCheck("wf_appel_effet", "wf_appel_effet_statut_check", "statut", StatutAppelEffet.values());

		// Unicités partielles, sur expressions et composites (§ 9.4 + D-W3-01)
		executerDdl("CREATE UNIQUE INDEX IF NOT EXISTS uq_wf_modele_actif_objet_sous_type "
				+ "ON wf_modele (objet_type, COALESCE(sous_type, '')) WHERE actif = true");
		executerDdl("CREATE UNIQUE INDEX IF NOT EXISTS uq_wf_modele_version_numero "
				+ "ON wf_modele_version (modele_id, numero)");
		executerDdl("CREATE UNIQUE INDEX IF NOT EXISTS uq_wf_modele_version_publiee "
				+ "ON wf_modele_version (modele_id) WHERE statut = 'PUBLIEE'");
		executerDdl("CREATE UNIQUE INDEX IF NOT EXISTS uq_wf_modele_version_brouillon "
				+ "ON wf_modele_version (modele_id) WHERE statut = 'BROUILLON'");
		executerDdl("CREATE UNIQUE INDEX IF NOT EXISTS uq_wf_instance_objet "
				+ "ON wf_instance (objet_type, objet_id)");
		executerDdl("CREATE UNIQUE INDEX IF NOT EXISTS uq_wf_tache_cle_idempotence "
				+ "ON wf_tache (cle_idempotence) WHERE cle_idempotence IS NOT NULL");

		// Index de lecture / performance (§ 9.4 + D-W3-01)
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_journal_modele_modele_horo "
				+ "ON wf_journal_modele (modele_id, horodatage DESC)");
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_instance_statut_demarre "
				+ "ON wf_instance (statut, demarre_le)");
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_instance_demandeur "
				+ "ON wf_instance (demandeur_collaborateur_id)");
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_beneficiaire_collab "
				+ "ON wf_beneficiaire (collaborateur_id)");
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_etape_execution_inst_ordre "
				+ "ON wf_etape_execution (instance_id, ordre)");
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_tache_statut_echeance "
				+ "ON wf_tache (statut, echeance_le)");
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_tache_statut_relance "
				+ "ON wf_tache (statut, prochaine_relance_le)");
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_tache_instance "
				+ "ON wf_tache (instance_id)");
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_tache_candidat_compte_retire "
				+ "ON wf_tache_candidat (compte_id, retire_le)");
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_tache_candidat_collab "
				+ "ON wf_tache_candidat (collaborateur_id)");
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_historique_inst_horo "
				+ "ON wf_historique (instance_id, horodatage)");
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_delegation_delegant_periode "
				+ "ON wf_delegation (delegant_collaborateur_id, du, au)");
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_delegation_delegue "
				+ "ON wf_delegation (delegue_collaborateur_id)");
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_appel_effet_statut_prochaine "
				+ "ON wf_appel_effet (statut, prochaine_tentative_le)");
		executerDdl("CREATE INDEX IF NOT EXISTS idx_wf_outbox_non_publie "
				+ "ON wf_outbox (prochaine_tentative_le) WHERE publie_le IS NULL");

		log.info("Contraintes CHECK et index de workflow_db vérifiés avec succès.");
	}

	private void recreerCheck(String table, String contrainte, String colonne, Enum<?>[] valeursEnum) {
		String valeurs = Arrays.stream(valeursEnum)
				.map(v -> "'" + v.name() + "'")
				.collect(Collectors.joining(", "));
		try {
			jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + contrainte);
			jdbcTemplate.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + contrainte
					+ " CHECK (" + colonne + " IS NULL OR " + colonne + " IN (" + valeurs + "))");
		} catch (Exception e) {
			log.warn("Impossible d'aligner la contrainte {} sur {} : {}", contrainte, table, e.getMessage());
		}
	}

	private void executerDdl(String sql) {
		try {
			jdbcTemplate.execute(sql);
		} catch (Exception e) {
			log.warn("Impossible d'appliquer l'index [{}] : {}", sql, e.getMessage());
		}
	}
}
