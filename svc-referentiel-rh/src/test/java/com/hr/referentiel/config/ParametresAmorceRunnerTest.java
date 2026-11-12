package com.hr.referentiel.config;

import com.hr.referentiel.domain.ValeursInitialesReferentiel;
import com.hr.referentiel.entity.ParametreJournal;
import com.hr.referentiel.entity.ParametreValeur;
import com.hr.referentiel.repository.ParametreJournalRepository;
import com.hr.referentiel.repository.ParametreValeurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Amorçage rejouable (ADR-UAT-lot3 § 8) : idempotence, reprise des familles, orphelins recréés inactifs. */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:amorce3b;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ParametresAmorceRunnerTest.Config.class)
class ParametresAmorceRunnerTest {

	@Configuration
	@EnableJpaRepositories(basePackageClasses = ParametreValeurRepository.class, excludeFilters =
	@ComponentScan.Filter(type = FilterType.REGEX,
			pattern = "com\\.hr\\.referentiel\\.repository\\.(?!Parametre).*"))
	static class Config {
		@Bean
		PersistenceManagedTypes managedTypes() {
			return PersistenceManagedTypes.of(ParametreValeur.class.getName(), ParametreJournal.class.getName());
		}
	}

	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	PlatformTransactionManager tm;
	@Autowired
	ParametreValeurRepository repository;
	@Autowired
	ParametreJournalRepository journalRepository;

	private ParametresAmorceRunner runner() {
		return new ParametresAmorceRunner(jdbc, tm, new ReferentielEvenementsProperties(), JsonMapper.builder().build());
	}

	@BeforeEach
	void init() {
		journalRepository.deleteAll();
		repository.deleteAll();
		for (String t : List.of("rh_collaborateur", "rh_demande_document_administratif", "rh_demande_administrative",
				"rh_famille_metier")) {
			jdbc.execute("drop table if exists " + t);
		}
		jdbc.execute("create table rh_collaborateur (id int, niveau_seniorite varchar(32), famille_metier_code varchar(64))");
		jdbc.execute("create table rh_demande_document_administratif (id int, type_document varchar(40))");
		jdbc.execute("create table rh_demande_administrative (id int, type_demande varchar(40), contenu varchar(2000))");
		jdbc.execute("create table rh_famille_metier (code varchar(64), libelle varchar(255), actif boolean, systeme boolean)");
	}

	private long compter() {
		return repository.count();
	}

	@Test
	@DisplayName("valeurs initiales insérées : types de document avec SLA, congés (MALADIE verrouillé), statuts")
	void valeursInitiales() {
		runner().amorcer();

		ParametreValeur att = repository.findByReferentielAndCode("TYPE_DOCUMENT", "ATTESTATION_TRAVAIL").orElseThrow();
		assertThat(att.getSlaHeures()).isEqualTo(24);
		assertThat(att.getLibelle()).isEqualTo("Attestation de travail");
		assertThat(att.getPremiereUtilisationLe()).isNotNull();
		ParametreValeur maladie = repository.findByReferentielAndCode("TYPE_CONGE", "MALADIE").orElseThrow();
		assertThat(maladie.getPieceJointeObligatoire()).isTrue();
		assertThat(maladie.getPieceJointeVerrouillee()).isTrue();
		assertThat(repository.findByReferentielAndCode("TYPE_CONGE", "ANNUEL").orElseThrow().isSysteme()).isTrue();
		assertThat(repository.findByReferentielAndCode("STATUT_DEMANDE", "EN_VALIDATION_RRH").orElseThrow().isSysteme())
				.isTrue();
		int attendues = ValeursInitialesReferentiel.toutes((c, d) -> d).size();
		assertThat(compter()).isEqualTo(attendues);
	}

	@Test
	@DisplayName("surcharge de propriété du SLA lue au premier amorçage seulement")
	void surchargeSla() {
		ReferentielEvenementsProperties props = new ReferentielEvenementsProperties();
		props.setDocumentSlaHeuresParType(Map.of("ATTESTATION_TRAVAIL", 72));
		new ParametresAmorceRunner(jdbc, tm, props, JsonMapper.builder().build()).amorcer();
		assertThat(repository.findByReferentielAndCode("TYPE_DOCUMENT", "ATTESTATION_TRAVAIL").orElseThrow()
				.getSlaHeures()).isEqualTo(72);
	}

	@Test
	@DisplayName("rejoué deux fois : aucun effet, une ligne modifiée par la RH n'est jamais réécrite")
	void idempotent() {
		runner().amorcer();
		long avant = compter();
		long journalAvant = journalRepository.count();
		ParametreValeur p = repository.findByReferentielAndCode("STATUT_DEMANDE", "EN_VALIDATION_RRH").orElseThrow();
		p.setLibelle("En attente de la RH");
		p.setLibelleNormalise("en attente de la rh");
		repository.save(p);

		runner().amorcer();
		runner().amorcer();

		assertThat(compter()).isEqualTo(avant);
		assertThat(journalRepository.count()).isEqualTo(journalAvant);
		assertThat(repository.findByReferentielAndCode("STATUT_DEMANDE", "EN_VALIDATION_RRH").orElseThrow()
				.getLibelle()).isEqualTo("En attente de la RH");
	}

	@Test
	@DisplayName("un nouveau code de comportement apparaît automatiquement (ligne manquante recréée)")
	void ligneManquanteRecreee() {
		runner().amorcer();
		repository.delete(repository.findByReferentielAndCode("STATUT_PLAINTE", "FERME").orElseThrow());

		runner().amorcer();

		assertThat(repository.findByReferentielAndCode("STATUT_PLAINTE", "FERME")).isPresent();
	}

	@Test
	@DisplayName("familles reprises de rh_famille_metier : libellé et état conservés, marquées utilisées")
	void reprendreFamilles() {
		jdbc.update("insert into rh_famille_metier values ('DEV_BACKEND', 'Développement backend', true, false)");
		jdbc.update("insert into rh_famille_metier values ('ANCIENNE', 'Ancienne famille', false, true)");

		runner().amorcer();

		ParametreValeur dev = repository.findByReferentielAndCode("FAMILLE_METIER", "DEV_BACKEND").orElseThrow();
		assertThat(dev.getLibelle()).isEqualTo("Développement backend");
		assertThat(dev.isActif()).isTrue();
		assertThat(dev.getPremiereUtilisationLe()).isNotNull();
		ParametreValeur ancienne = repository.findByReferentielAndCode("FAMILLE_METIER", "ANCIENNE").orElseThrow();
		assertThat(ancienne.isActif()).isFalse();
		assertThat(ancienne.isSysteme()).isFalse();
	}

	@Test
	@DisplayName("orphelins : un code stocké absent de la table est recréé INACTIF, marqué utilisé et journalisé")
	void orphelinRecreeInactif() {
		jdbc.update("insert into rh_demande_document_administratif values (1, 'ATTESTATION_STAGE')");
		jdbc.update("insert into rh_collaborateur values (1, 'EXPERT', 'FAMILLE_DISPARUE')");
		jdbc.update("insert into rh_demande_administrative values (1, 'CONGE', '{\"type_conge\": \"mariage\"}')");

		runner().amorcer();

		for (String[] o : new String[][] {{"TYPE_DOCUMENT", "ATTESTATION_STAGE"}, {"NIVEAU_SENIORITE", "EXPERT"},
				{"FAMILLE_METIER", "FAMILLE_DISPARUE"}, {"TYPE_CONGE", "MARIAGE"}}) {
			ParametreValeur p = repository.findByReferentielAndCode(o[0], o[1]).orElseThrow();
			assertThat(p.isActif()).as(o[1]).isFalse();
			assertThat(p.getPremiereUtilisationLe()).isNotNull();
			assertThat(p.getLibelle()).isNotBlank().isNotEqualTo(o[1]);
		}
		assertThat(journalRepository.findAll()).hasSize(4)
				.allMatch(j -> "Système (automatique)".equals(j.getAuteurNom()));

		long total = compter();
		runner().amorcer();
		assertThat(compter()).isEqualTo(total);
		assertThat(journalRepository.count()).isEqualTo(4);
	}

	@Test
	@DisplayName("colonne niveau_seniorite élargie à 40 caractères, une seule fois")
	void colonneElargie() {
		runner().amorcer();
		runner().amorcer();
		Integer n = jdbc.queryForObject("select character_maximum_length from information_schema.columns "
				+ "where lower(table_name) = 'rh_collaborateur' and lower(column_name) = 'niveau_seniorite'",
				Integer.class);
		assertThat(n).isEqualTo(40);
	}

	@Test
	@DisplayName("libellé par défaut déjà pris par une autre valeur : créée sous « Libellé (CODE) », pas ignorée")
	void libelleParDefautDejaPris() {
		runner().amorcer();
		// une autre valeur occupe le libellé de ATTESTATION_TRAVAIL, qui a disparu de la table
		repository.delete(repository.findByReferentielAndCode("TYPE_DOCUMENT", "ATTESTATION_TRAVAIL").orElseThrow());
		ParametreValeur autre = repository.findByReferentielAndCode("TYPE_DOCUMENT", "DOCUMENT_INTERNE").orElseThrow();
		autre.setLibelle("Attestation de travail");
		autre.setLibelleNormalise(com.hr.referentiel.service.NormalisationLibelle.normaliser("Attestation de travail"));
		repository.save(autre);

		runner().amorcer();

		ParametreValeur recree = repository.findByReferentielAndCode("TYPE_DOCUMENT", "ATTESTATION_TRAVAIL").orElseThrow();
		assertThat(recree.getLibelle()).isEqualTo("Attestation de travail (ATTESTATION_TRAVAIL)");
		assertThat(recree.isActif()).isTrue();
	}
}
