package com.hr.referentiel.config;

import com.hr.referentiel.importation.ChampImport;
import com.hr.referentiel.importation.ReconnaissanceEnTetes;
import com.hr.referentiel.importation.parametrage.ImportChampJournal;
import com.hr.referentiel.importation.parametrage.ImportChampRegle;
import com.hr.referentiel.importation.parametrage.ImportParametrage;
import com.hr.referentiel.importation.parametrage.ImportParametrageRepository;
import com.hr.referentiel.importation.parametrage.ImportSynonyme;
import com.hr.referentiel.importation.parametrage.ParametrageImportCache;
import com.hr.referentiel.importation.parametrage.TypeImportParametrable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Amorçage rejouable du paramétrage des champs d'import (ADR-IMPORT-CHAMPS § 4) : T1-1, T1-2. */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:amorceimport;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ImportChampsAmorceRunnerTest.Config.class)
class ImportChampsAmorceRunnerTest {

	@Configuration
	@EnableJpaRepositories(basePackageClasses = {ImportParametrageRepository.class,
			com.hr.referentiel.champsperso.ChampPersoRepository.class})
	static class Config {
		@Bean
		PersistenceManagedTypes managedTypes() {
			return PersistenceManagedTypes.of(ImportParametrage.class.getName(), ImportChampRegle.class.getName(),
					ImportSynonyme.class.getName(), ImportChampJournal.class.getName(),
					com.hr.referentiel.champsperso.ChampPerso.class.getName(),
					com.hr.referentiel.champsperso.ChampPersoValeurListe.class.getName(),
					com.hr.referentiel.champsperso.ValeurPersoFiche.class.getName(),
					com.hr.referentiel.champsperso.ValeurPersoMouvement.class.getName());
		}
	}

	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	PlatformTransactionManager tm;
	@Autowired
	com.hr.referentiel.champsperso.ChampPersoRepository champPersoRepository;

	private final ParametrageImportCache cache = mock(ParametrageImportCache.class);

	private ImportChampsAmorceRunner runner() {
		return new ImportChampsAmorceRunner(jdbc, tm, cache);
	}

	@BeforeEach
	void vider() {
		jdbc.update("delete from rh_champ_perso");
		jdbc.update("delete from rh_import_champ_journal");
		jdbc.update("delete from rh_import_synonyme");
		jdbc.update("delete from rh_import_champ");
		jdbc.update("delete from rh_import_parametrage");
	}

	private long compter(String table) {
		Long n = jdbc.queryForObject("select count(*) from " + table, Long.class);
		return n == null ? 0 : n;
	}

	@Test
	@DisplayName("T1-1 : base neuve -> 2 types à la version 0, 14 + 3 champs dans l'ordre du code, tous dans le modèle, exigences et synonymes du code actifs d'origine DEFAUT")
	void baseNeuve() {
		runner().amorcer();

		assertThat(compter("rh_import_parametrage")).isEqualTo(2);
		assertThat(jdbc.queryForObject("select max(version) from rh_import_parametrage", Long.class)).isZero();
		List<String> codes = jdbc.queryForList("select champ_code from rh_import_champ where type_import = 'COLLABORATEURS' order by ordre",
				String.class);
		assertThat(codes).containsExactly("MATRICULE", "PRENOM", "NOM", "COURRIEL", "UNITE", "POSTE", "FONCTION", "QUALITE",
				"AFFECTATION", "DATE_RECRUTEMENT", "STATUT", "PROFIL_ACCES", "FAMILLE_METIER", "NIVEAU_SENIORITE");
		assertThat(jdbc.queryForList("select champ_code from rh_import_champ where type_import = 'SOLDES' order by ordre", String.class))
				.containsExactly("MATRICULE", "SOLDE", "MOTIF");
		assertThat(jdbc.queryForObject("select count(*) from rh_import_champ where dans_modele = false", Long.class)).isZero();
		assertThat(jdbc.queryForObject("select exigence from rh_import_champ where type_import = 'COLLABORATEURS' and champ_code = 'PRENOM'",
				String.class)).isEqualTo("CREATION");
		assertThat(jdbc.queryForObject("select en_tete from rh_import_champ where type_import = 'COLLABORATEURS' and champ_code = 'COURRIEL'",
				String.class)).isEqualTo("Courriel");
		long attendus = TypeImportParametrable.COLLABORATEURS.champs().stream().mapToLong(c -> c.synonymes().size()).sum()
				+ TypeImportParametrable.SOLDES.champs().stream().mapToLong(c -> c.synonymes().size()).sum();
		assertThat(compter("rh_import_synonyme")).isEqualTo(attendus);
		assertThat(jdbc.queryForObject("select count(*) from rh_import_synonyme where actif = false or origine <> 'DEFAUT'", Long.class)).isZero();
		assertThat(compter("rh_import_champ_journal")).as("l'amorçage n'écrit pas au journal").isZero();
		verify(cache).invalider(TypeImportParametrable.COLLABORATEURS);
		verify(cache).invalider(TypeImportParametrable.SOLDES);
	}

	@Test
	@DisplayName("T1-2 : deuxième passage -> aucun doublon et aucune version incrémentée")
	void doublePassage() {
		runner().amorcer();
		long champs = compter("rh_import_champ");
		long synonymes = compter("rh_import_synonyme");
		runner().amorcer();
		assertThat(compter("rh_import_champ")).isEqualTo(champs);
		assertThat(compter("rh_import_synonyme")).isEqualTo(synonymes);
		assertThat(compter("rh_import_parametrage")).isEqualTo(2);
		assertThat(jdbc.queryForObject("select max(version) from rh_import_parametrage", Long.class)).isZero();
	}

	@Test
	@DisplayName("T1-2 : une valeur réglée (en-tête, présence, exigence, ordre, synonyme désactivé) est conservée au redémarrage")
	void valeurReglee() {
		runner().amorcer();
		jdbc.update("update rh_import_champ set en_tete = 'E-mail', dans_modele = false, exigence = 'TOUJOURS', ordre = 20 "
				+ "where type_import = 'COLLABORATEURS' and champ_code = 'COURRIEL'");
		jdbc.update("update rh_import_synonyme set actif = false where type_import = 'COLLABORATEURS' and champ_code = 'UNITE' "
				+ "and forme_normalisee = 'service'");
		jdbc.update("update rh_import_parametrage set version = 5 where type_import = 'COLLABORATEURS'");

		runner().amorcer();

		assertThat(jdbc.queryForObject("select en_tete from rh_import_champ where champ_code = 'COURRIEL' and type_import = 'COLLABORATEURS'",
				String.class)).isEqualTo("E-mail");
		assertThat(jdbc.queryForObject("select dans_modele from rh_import_champ where champ_code = 'COURRIEL' and type_import = 'COLLABORATEURS'",
				Boolean.class)).isFalse();
		assertThat(jdbc.queryForObject("select exigence from rh_import_champ where champ_code = 'COURRIEL' and type_import = 'COLLABORATEURS'",
				String.class)).isEqualTo("TOUJOURS");
		assertThat(jdbc.queryForObject("select ordre from rh_import_champ where champ_code = 'COURRIEL' and type_import = 'COLLABORATEURS'",
				Integer.class)).isEqualTo(20);
		assertThat(jdbc.queryForObject("select actif from rh_import_synonyme where type_import = 'COLLABORATEURS' and champ_code = 'UNITE' "
				+ "and forme_normalisee = 'service'", Boolean.class)).isFalse();
		assertThat(jdbc.queryForObject("select version from rh_import_parametrage where type_import = 'COLLABORATEURS'", Long.class))
				.isEqualTo(5);
	}

	@Test
	@DisplayName("ADR § 4 : un champ du code sans ligne est ajouté en fin d'ordre, sans toucher aux autres ; un synonyme par défaut supprimé en base est recréé")
	void champEtSynonymeManquants() {
		runner().amorcer();
		jdbc.update("delete from rh_import_champ where type_import = 'SOLDES' and champ_code = 'MOTIF'");
		jdbc.update("delete from rh_import_synonyme where type_import = 'SOLDES' and champ_code = 'MOTIF'");
		jdbc.update("delete from rh_import_synonyme where type_import = 'COLLABORATEURS' and champ_code = 'POSTE' and forme_normalisee = 'job title'");

		runner().amorcer();

		assertThat(jdbc.queryForObject("select ordre from rh_import_champ where type_import = 'SOLDES' and champ_code = 'MOTIF'", Integer.class))
				.isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from rh_import_synonyme where type_import = 'SOLDES' and champ_code = 'MOTIF'",
				Long.class)).isEqualTo(ChampImport.Solde.MOTIF.synonymes().size());
		assertThat(jdbc.queryForObject("select count(*) from rh_import_synonyme where type_import = 'COLLABORATEURS' "
				+ "and champ_code = 'POSTE' and forme_normalisee = 'job title'", Long.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("ADR § 4 point 3 : un synonyme par défaut dont la forme est déjà un nom actif d'un autre champ est inséré inactif (jamais de conflit créé)")
	void synonymeEnConflitInsereInactif() {
		runner().amorcer();
		// la RH a pris « job title » comme en-tête de Fonction, puis le synonyme par défaut de Poste disparaît de la base
		jdbc.update("update rh_import_champ set en_tete = 'Job Title' where type_import = 'COLLABORATEURS' and champ_code = 'FONCTION'");
		jdbc.update("delete from rh_import_synonyme where type_import = 'COLLABORATEURS' and champ_code = 'POSTE' and forme_normalisee = 'job title'");

		runner().amorcer();

		assertThat(jdbc.queryForObject("select actif from rh_import_synonyme where type_import = 'COLLABORATEURS' "
				+ "and champ_code = 'POSTE' and forme_normalisee = 'job title'", Boolean.class)).isFalse();
	}

	@Test
	@DisplayName("ADR § 4 : les formes sont calculées par la même normalisation que la reconnaissance")
	void formeNormalisee() {
		runner().amorcer();
		List<String> formes = jdbc.queryForList("select forme_normalisee from rh_import_synonyme where type_import = 'COLLABORATEURS' "
				+ "and champ_code = 'UNITE'", String.class);
		assertThat(formes).contains(ReconnaissanceEnTetes.normaliser("unité d'affectation")).contains("unite d affectation");
	}

	@Test
	@DisplayName("Un code PERSO_ présent dans rh_champ_perso n'est pas signalé « absent du code applicatif » ; un code orphelin l'est")
	void champsPersoNonSignalesCommeDisparus() {
		runner().amorcer();
		champPersoRepository.save(new com.hr.referentiel.champsperso.ChampPerso("COLLABORATEURS", "PERSO_PRIME", "Prime",
				"Prime annuelle", com.hr.referentiel.champsperso.TypeValeurPerso.TEXTE, false, java.time.Instant.now(), "Test"));
		for (String code : List.of("PERSO_PRIME", "ORPHELIN")) {
			jdbc.update("insert into rh_import_champ (id, type_import, champ_code, en_tete, dans_modele, exigence, ordre) "
					+ "values (?, 'COLLABORATEURS', ?, ?, true, 'FACULTATIF', 90)", java.util.UUID.randomUUID(), code, code);
		}

		assertThat(runner().codesDisparus(TypeImportParametrable.COLLABORATEURS)).containsExactly("ORPHELIN");
	}
}
