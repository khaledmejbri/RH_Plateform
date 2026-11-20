package com.hr.referentiel.repository;

import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.config.SocieteRepriseRunner;
import com.hr.referentiel.entity.AcquisitionMois;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.NumeroSequence;
import com.hr.referentiel.entity.ParametresConges;
import com.hr.referentiel.entity.SoldeConge;
import com.hr.referentiel.entity.UniteOrganisation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Garde-fou B4 sur base PostgreSQL réelle ({@code referentiel_rh_db_test}) :
 * <ul>
 * <li>exécution des requêtes SQL natives multi-société sur PostgreSQL ;</li>
 * <li>inspection par réflexion de tous les {@code @Query(nativeQuery = true)}
 * et des appels {@code JdbcTemplate} ;</li>
 * <li>auto-test négatif sur un dépôt volontairement fautif sans
 * {@code societe_id}.</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(RequetesNativesSocieteTest.Config.class)
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
class RequetesNativesSocieteTest {

	private static final Set<String> WHITELIST_FICHIERS_JDBC = Set.of(
			"MultiSocieteConfig.java",
			"SocieteRepriseRunner.java",
			"ImportVerrou.java");

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) throws Exception {
		String password = System.getenv("TEST_DB_PASSWORD");
		String baseUrl = System.getenv().getOrDefault("TEST_DB_URL",
				"jdbc:postgresql://localhost:5444/referentiel_rh_db_test");
		String user = System.getenv().getOrDefault("TEST_DB_USER", "postgres");
		try (Connection c = DriverManager.getConnection(baseUrl, user, password);
				Statement st = c.createStatement()) {
			st.execute("CREATE SCHEMA IF NOT EXISTS test_natives_w1");
		}
		String sep = baseUrl.contains("?") ? "&" : "?";
		registry.add("spring.datasource.url", () -> baseUrl + sep + "currentSchema=test_natives_w1");
		registry.add("spring.datasource.username", () -> user);
		registry.add("spring.datasource.password", () -> password);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
	}

	@Configuration
	@EnableJpaRepositories(basePackageClasses = SoldeCongeRepository.class, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.hr\\.referentiel\\.repository\\.(?!(AcquisitionMois|ReglagesConges|SoldeConge|Collaborateur|UniteOrganisation|NumeroSequence)).*"))
	static class Config {
		@Bean
		PersistenceManagedTypes managedTypes() {
			return PersistenceManagedTypes.of(
					AcquisitionMois.class.getName(),
					ParametresConges.class.getName(),
					SoldeConge.class.getName(),
					Collaborateur.class.getName(),
					UniteOrganisation.class.getName(),
					NumeroSequence.class.getName());
		}
	}

	private static final UUID SOC_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID SOC_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	AcquisitionMoisRepository acquisitionMoisRepository;
	@Autowired
	ReglagesCongesRepository reglagesRepository;
	@Autowired
	SoldeCongeRepository soldeRepository;
	@Autowired
	CollaborateurRepository collaborateurRepository;
	@Autowired
	UniteOrganisationRepository uniteRepository;
	@Autowired
	NumeroSequenceRepository numeroSequenceRepository;

	@Test
	@DisplayName("PostgreSQL : AcquisitionMois, ReglagesConges, SoldeConge et NumeroSequence isolés par société")
	void requetesNativesPostgreSqlIsoleesParSociete() {
		jdbc.execute("ALTER TABLE rh_acquisition_mois DROP CONSTRAINT IF EXISTS rh_acquisition_mois_pkey");
		jdbc.execute("ALTER TABLE rh_acquisition_mois ADD PRIMARY KEY (societe_id, mois)");
		jdbc.execute("ALTER TABLE rh_numero_sequence DROP CONSTRAINT IF EXISTS rh_numero_sequence_pkey");
		jdbc.execute("ALTER TABLE rh_numero_sequence ADD PRIMARY KEY (societe_id, cle)");

		acquisitionMoisRepository.figerSiAbsent(SOC_A, "2026-11", new BigDecimal("1.5"));
		acquisitionMoisRepository.figerSiAbsent(SOC_B, "2026-11", new BigDecimal("2.0"));
		acquisitionMoisRepository.figerSiAbsent(SOC_A, "2026-11", new BigDecimal("9.9"));

		SocieteContexte.executer(SOC_A, () -> assertThat(acquisitionMoisRepository.findById("2026-11")).get()
				.extracting(AcquisitionMois::getJoursParMois).isEqualTo(new BigDecimal("1.5")));
		SocieteContexte.executer(SOC_B, () -> assertThat(acquisitionMoisRepository.findById("2026-11")).get()
				.extracting(AcquisitionMois::getJoursParMois).isEqualTo(new BigDecimal("2.0")));

		reglagesRepository.creerSiAbsente(SOC_A, new BigDecimal("1.5"), LocalDate.of(2026, 1, 1));
		reglagesRepository.creerSiAbsente(SOC_B, new BigDecimal("2.0"), LocalDate.of(2026, 1, 1));
		SocieteContexte.executer(SOC_A, () -> assertThat(reglagesRepository.findFirstByOrderByIdAsc()).get()
				.extracting(ParametresConges::getJoursAcquisParMois).isEqualTo(new BigDecimal("1.5")));
		SocieteContexte.executer(SOC_B, () -> assertThat(reglagesRepository.findFirstByOrderByIdAsc()).get()
				.extracting(ParametresConges::getJoursAcquisParMois).isEqualTo(new BigDecimal("2.0")));

		assertThat(numeroSequenceRepository.incrementer(SOC_A, "CONGE")).isEqualTo(1);
		assertThat(numeroSequenceRepository.incrementer(SOC_A, "CONGE")).isEqualTo(2);
		assertThat(numeroSequenceRepository.incrementer(SOC_B, "CONGE")).isEqualTo(1);

		UUID collabA = creerCollaborateur(SOC_A, "MAT-NAT-A");
		soldeRepository.creerSiAbsent(SOC_A, collabA);
		SocieteContexte.executer(SOC_A, () -> {
			assertThat(soldeRepository.findById(collabA)).isPresent();
			assertThat(soldeRepository.acquisParIds(List.of(collabA))).hasSize(1);
		});
		SocieteContexte.executer(SOC_B, () -> {
			assertThat(soldeRepository.findById(collabA)).isEmpty();
			assertThat(soldeRepository.acquisParIds(List.of(collabA))).isEmpty();
		});
	}

	@Test
	@DisplayName("Garde-fou statique : tous les @Query(nativeQuery=true) et classes JdbcTemplate filtrent sur societe_id")
	void gardeFouStatiqueToutesRequetesNatives() throws Exception {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
			@Override
			protected boolean isCandidateComponent(
					org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
				return beanDefinition.getMetadata().isInterface();
			}
		};
		scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));

		int nbRequetesControlees = 0;
		for (BeanDefinition bd : scanner.findCandidateComponents("com.hr.referentiel")) {
			Class<?> repoClass = Class.forName(bd.getBeanClassName());
			if (repoClass.equals(RepositoryFautifPourTest.class)) {
				continue;
			}
			for (Method m : repoClass.getDeclaredMethods()) {
				Query q = m.getAnnotation(Query.class);
				if (q != null && q.nativeQuery()) {
					nbRequetesControlees++;
					verifierRequeteNative(repoClass, m, q.value());
				}
			}
		}
		assertThat(nbRequetesControlees).isGreaterThanOrEqualTo(4);

		// Contrôle de chaque requête passée à JdbcTemplate / NamedParameterJdbcTemplate dans src/main
		Path mainJava = Path.of("src/main/java");
		int nbAppelsJdbcControles = 0;
		try (Stream<Path> paths = Files.walk(mainJava)) {
			List<Path> fichiersJdbc = paths
					.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(".java"))
					.toList();
			for (Path p : fichiersJdbc) {
				String nom = p.getFileName().toString();
				if (WHITELIST_FICHIERS_JDBC.contains(nom)) {
					continue;
				}
				String contenu = Files.readString(p);
				nbAppelsJdbcControles += verifierAppelsJdbc(nom, contenu);
			}
		}
		assertThat(nbAppelsJdbcControles).isGreaterThanOrEqualTo(20);
	}

	@Test
	@DisplayName("Auto-test négatif : le garde-fou échoue sur un repository fautif sans societe_id")
	void detecteRequeteNativeFautiveSansSocieteId() throws Exception {
		Method fautive = RepositoryFautifPourTest.class.getDeclaredMethod("toutLireSansSociete");
		Query q = fautive.getAnnotation(Query.class);
		assertThatThrownBy(() -> verifierRequeteNative(RepositoryFautifPourTest.class, fautive, q.value()))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("societe_id");
	}

	@Test
	@DisplayName("Auto-test négatif JDBC : le garde-fou détecte une requête JdbcTemplate sans societe_id même si le fichier en contient d'autres valides")
	void detecteRequeteJdbcFautiveSansSocieteId() {
		String classeFautive = """
				package com.hr.referentiel.champsperso;
				import org.springframework.jdbc.core.JdbcTemplate;
				class JdbcFautifPourTest {
					private final JdbcTemplate jdbc = null;
					void valide() {
						jdbc.queryForList("select champ_code from rh_valeur_perso_fiche where societe_id = ?", String.class);
					}
					void fautive(java.util.UUID id) {
						jdbc.queryForList("select champ_code from rh_valeur_perso_fiche where collaborateur_id = ?", String.class, id);
					}
				}
				""";
		assertThatThrownBy(() -> verifierAppelsJdbc("JdbcFautifPourTest.java", classeFautive))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("societe_id")
				.hasMessageContaining("rh_valeur_perso_fiche");
	}

	private static int verifierAppelsJdbc(String nomFichier, String source) {
		String sansCommentaires = supprimerCommentaires(source);
		Set<String> varsJdbc = new LinkedHashSet<>();
		Matcher varMatcher = Pattern.compile("\\b(?:NamedParameter)?JdbcTemplate\\s+(\\w+)\\b").matcher(sansCommentaires);
		while (varMatcher.find()) {
			varsJdbc.add(varMatcher.group(1));
		}
		if (varsJdbc.isEmpty()) {
			return 0;
		}
		Set<String> tablesMetier = new LinkedHashSet<>(SocieteRepriseRunner.TABLES_METIER);
		tablesMetier.addAll(List.of(
				"rh_societe_parametres",
				"rh_societe_parametres_journal",
				"rh_fonction",
				"rh_fonction_affectation"));

		String patternVars = String.join("|", varsJdbc);
		Pattern callPattern = Pattern.compile(
				"\\b(?:" + patternVars + ")\\.(query(?:ForList|ForObject|ForMap|ForRowSet|ForStream)?|update|batchUpdate|execute)\\s*\\(");
		Matcher callMatcher = callPattern.matcher(sansCommentaires);
		int controles = 0;
		while (callMatcher.find()) {
			int openParen = callMatcher.end() - 1;
			String premierArg = extrairePremierArgument(sansCommentaires, openParen);
			if (premierArg == null || premierArg.isBlank()) {
				continue;
			}
			// Ignorer les lambdas ConnectionCallback / StatementCallback (ex. jdbc.execute((Connection c) -> ...))
			if (premierArg.contains("->")) {
				continue;
			}
			String sql = resoudreSql(premierArg, sansCommentaires, callMatcher.start());
			if (sql == null || sql.isBlank()) {
				continue;
			}
			String sqlMin = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
			if (estRequeteJdbcWhitelistee(nomFichier, sqlMin)) {
				continue;
			}
			boolean viseTableMetier = tablesMetier.stream()
					.anyMatch(t -> Pattern.compile("\\b" + t + "\\b").matcher(sqlMin).find())
					|| sqlMin.contains("from  +") || sqlMin.contains("update  +");
			if (viseTableMetier) {
				controles++;
				assertThat(sqlMin)
						.as("Requête JDBC sans societe_id dans %s : %s", nomFichier, sql)
						.contains("societe_id");
			}
		}
		return controles;
	}

	private static boolean estRequeteJdbcWhitelistee(String nomFichier, String sqlMin) {
		if (sqlMin.startsWith("alter table ") || sqlMin.startsWith("drop table") || sqlMin.startsWith("create table")
				|| sqlMin.startsWith("select pg_advisory_xact_lock") || sqlMin.contains("information_schema.")
				|| sqlMin.contains("pg_catalog.") || sqlMin.contains("rh_evenement_traite")) {
			return true;
		}
		// Table globale historique antérieure au lot 3 (lue uniquement pour la société par défaut, D-SOC22)
		if ("ParametresAmorceRunner.java".equals(nomFichier) && sqlMin.contains("rh_famille_metier")) {
			return true;
		}
		// Nettoyage de la contrainte globale avant reprise dans SoldesCongeAmorceRunner
		if ("SoldesCongeAmorceRunner.java".equals(nomFichier) && sqlMin.contains("pg_constraint")) {
			return true;
		}
		return false;
	}

	private static String resoudreSql(String argExpr, String source, int posAppel) {
		String trimmed = argExpr.trim();
		if (trimmed.contains("\"")) {
			return concatenerLitteraux(trimmed);
		}
		// Si l'argument est un nom de variable locale (ex. "sql"), remonter à son assignation la plus proche
		if (trimmed.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			String avant = source.substring(0, posAppel);
			Pattern assign = Pattern.compile("\\b" + Pattern.quote(trimmed) + "\\s*=\\s*([^;]+);", Pattern.DOTALL);
			Matcher m = assign.matcher(avant);
			String derniereExpr = null;
			while (m.find()) {
				derniereExpr = m.group(1);
			}
			if (derniereExpr != null && derniereExpr.contains("\"")) {
				return concatenerLitteraux(derniereExpr);
			}
		}
		return null;
	}

	private static String concatenerLitteraux(String expr) {
		StringBuilder sb = new StringBuilder();
		boolean inString = false;
		boolean escape = false;
		for (int i = 0; i < expr.length(); i++) {
			char c = expr.charAt(i);
			if (inString) {
				if (escape) {
					sb.append(c);
					escape = false;
				} else if (c == '\\') {
					escape = true;
				} else if (c == '"') {
					inString = false;
					sb.append(' ');
				} else {
					sb.append(c);
				}
			} else if (c == '"') {
				inString = true;
			}
		}
		return sb.toString();
	}

	private static String extrairePremierArgument(String source, int openParenIndex) {
		int depth = 0;
		boolean inString = false;
		boolean escape = false;
		StringBuilder sb = new StringBuilder();
		for (int i = openParenIndex + 1; i < source.length(); i++) {
			char c = source.charAt(i);
			if (inString) {
				sb.append(c);
				if (escape) {
					escape = false;
				} else if (c == '\\') {
					escape = true;
				} else if (c == '"') {
					inString = false;
				}
				continue;
			}
			if (c == '"') {
				inString = true;
				sb.append(c);
			} else if (c == '(') {
				depth++;
				sb.append(c);
			} else if (c == ')') {
				if (depth == 0) {
					return sb.toString();
				}
				depth--;
				sb.append(c);
			} else if (c == ',' && depth == 0) {
				return sb.toString();
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static String supprimerCommentaires(String source) {
		StringBuilder sb = new StringBuilder(source.length());
		boolean inString = false;
		boolean escape = false;
		int i = 0;
		while (i < source.length()) {
			char c = source.charAt(i);
			if (inString) {
				sb.append(c);
				if (escape) {
					escape = false;
				} else if (c == '\\') {
					escape = true;
				} else if (c == '"') {
					inString = false;
				}
				i++;
			} else if (c == '"') {
				inString = true;
				sb.append(c);
				i++;
			} else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
				while (i < source.length() && source.charAt(i) != '\n') {
					i++;
				}
			} else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
				i += 2;
				while (i + 1 < source.length() && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
					i++;
				}
				i += 2;
			} else {
				sb.append(c);
				i++;
			}
		}
		return sb.toString();
	}

	private static void verifierRequeteNative(Class<?> repoClass, Method m, String sql) {
		assertThat(sql.toLowerCase(Locale.ROOT))
				.as("Requête native %s.%s doit contenir societe_id", repoClass.getSimpleName(), m.getName())
				.contains("societe_id");
	}

	interface RepositoryFautifPourTest extends Repository<ParametresConges, Short> {
		@Query(value = "SELECT * FROM rh_parametres", nativeQuery = true)
		List<ParametresConges> toutLireSansSociete();
	}

	private UUID creerCollaborateur(UUID societeId, String matricule) {
		return SocieteContexte.executer(societeId, () -> {
			UniteOrganisation u = new UniteOrganisation();
			u.setCode("U-" + matricule);
			u.setLibelle("Unité " + matricule);
			u.setTypeNoeud("SERVICE");
			u.setActif(true);
			u = uniteRepository.saveAndFlush(u);

			Collaborateur c = new Collaborateur();
			c.setMatricule(matricule);
			c.setPrenom("Test");
			c.setNom("Nat");
			c.setStatut("ACTIF");
			c.setProfilAcces("COLLABORATEUR");
			c.setUnite(u);
			c.setCreeLe(Instant.now());
			return collaborateurRepository.saveAndFlush(c).getId();
		});
	}
}
