package com.hr.referentiel.champsperso;

import com.hr.referentiel.champsperso.ChampPersoDtos.CreerChampPerso;
import com.hr.referentiel.champsperso.ChampPersoDtos.ReglesRequest;
import com.hr.referentiel.champsperso.ChampPersoDtos.ValeurListeRequest;
import com.hr.referentiel.config.ImportChampsAmorceRunner;
import com.hr.referentiel.importation.parametrage.ImportChampJournalRepository;
import com.hr.referentiel.importation.parametrage.ImportChampRegleRepository;
import com.hr.referentiel.importation.parametrage.ImportChampsJournal;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.ChampVue;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.Parametrage;
import com.hr.referentiel.importation.parametrage.ImportParametrageRepository;
import com.hr.referentiel.importation.parametrage.ImportParametrageService;
import com.hr.referentiel.importation.parametrage.ImportSynonymeRepository;
import com.hr.referentiel.importation.parametrage.ParametrageEcriture;
import com.hr.referentiel.importation.parametrage.ParametrageImportCache;
import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.test.AvecSocieteContexteExtension;
import com.hr.referentiel.test.SocieteContexteTestExecutionListener;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Base des tests H2 (mode PostgreSQL) des champs personnalisés : paramétrage amorcé, services réels, aucune base
 * partagée entre tests (chaque test est annulé). Les sous-classes vivent dans plusieurs paquets.
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:champsperso;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChampsPersoH2Config.class)
@ExtendWith(AvecSocieteContexteExtension.class)
@TestExecutionListeners(listeners = SocieteContexteTestExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class ChampsPersoH2Base {

	protected static final AuteurParametre DRH01 = new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi");
	protected static final String COL = "collaborateurs";
	protected static final String SOL = "soldes";

	@Autowired
	protected JdbcTemplate jdbc;
	@Autowired
	protected PlatformTransactionManager tm;
	@Autowired
	protected Clock clock;
	@Autowired
	protected ImportParametrageRepository parametrageRepository;
	@Autowired
	protected ImportChampRegleRepository champRepository;
	@Autowired
	protected ImportSynonymeRepository synonymeRepository;
	@Autowired
	protected ImportChampJournalRepository journalRepository;
	@Autowired
	protected ChampPersoRepository persoRepository;
	@Autowired
	protected ChampPersoValeurListeRepository valeurRepository;

	protected ParametrageImportCache cache;
	protected ChampPersoUtilisations utilisations;
	protected ImportChampsJournal journal;
	protected ParametrageEcriture ecriture;
	protected ChampPersoDefinitionService definitions;
	protected ChampPersoListeService listes;
	protected ImportParametrageService parametrage;
	protected DefinitionsPerso definitionsLecture;
	protected ValeursPersoJdbc valeursJdbc;

	@BeforeEach
	void initBase() {
		ParametrageImportCache factice = mock(ParametrageImportCache.class);
		new ImportChampsAmorceRunner(jdbc, tm, factice, () -> List.of(SocieteContexte.courante())).amorcer();
		cache = new ParametrageImportCache(new ConcurrentMapCacheManager(), champRepository, synonymeRepository,
				parametrageRepository, persoRepository, valeurRepository);
		utilisations = new ChampPersoUtilisations(jdbc);
		journal = new ImportChampsJournal(journalRepository);
		ecriture = new ParametrageEcriture(parametrageRepository, champRepository, synonymeRepository, persoRepository,
				valeurRepository, journal, factice, utilisations);
		definitions = new ChampPersoDefinitionService(ecriture, persoRepository, valeurRepository, champRepository,
				synonymeRepository, journal, utilisations, clock);
		listes = new ChampPersoListeService(ecriture, valeurRepository, utilisations, clock);
		parametrage = new ImportParametrageService(ecriture, synonymeRepository, journal, clock);
		definitionsLecture = new DefinitionsPerso(persoRepository, valeurRepository, champRepository, parametrageRepository);
		valeursJdbc = new ValeursPersoJdbc(jdbc);
	}

	// ─── Outils ────────────────────────────────────────────────────────────────

	protected long version(String type) {
		return parametrage.lire(type).version();
	}

	protected static ReglesRequest regles(Integer longueur, Integer decimales, String min, String max) {
		return new ReglesRequest(longueur, decimales, min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max),
				null, null);
	}

	protected static final ReglesRequest SANS_REGLE = new ReglesRequest(null, null, null, null, null, null);

	/** Crée un champ personnalisé et rend son code (le dernier champ personnalisé de la réponse). */
	protected String creer(String type, String libelle, String typeValeur, ReglesRequest regles, String... valeursListe) {
		List<ValeurListeRequest> valeurs = valeursListe.length == 0 ? null
				: java.util.Arrays.stream(valeursListe).map(ValeurListeRequest::new).toList();
		Parametrage p = definitions.creer(type, new CreerChampPerso(version(type), libelle, "Finalité de test", typeValeur, regles,
				valeurs, false, true), DRH01);
		return p.champs().stream().filter(ChampVue::personnalise).filter(c -> c.libelle().equals(libelle)).findFirst().orElseThrow()
				.code();
	}

	protected ChampVue champ(Parametrage p, String code) {
		return p.champs().stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
	}

	protected void erreur(Throwable t, int statut, String code) {
		assertThat(t).isInstanceOf(ReferentielMetierException.class);
		ReferentielMetierException e = (ReferentielMetierException) t;
		assertThat(e.getCode()).isEqualTo(code);
		assertThat(e.getHttpStatus()).isEqualTo(statut);
	}

	protected static Throwable echec(Runnable r) {
		try {
			r.run();
			return null;
		} catch (RuntimeException e) {
			return e;
		}
	}
}
