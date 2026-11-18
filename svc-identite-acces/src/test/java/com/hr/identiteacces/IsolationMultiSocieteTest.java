package com.hr.identiteacces;

import com.hr.commun.societe.SocieteContexte;
import com.hr.identiteacces.config.SocieteContraintesRunner;
import com.hr.identiteacces.dto.AuthResponse;
import com.hr.identiteacces.dto.SigninRequest;
import com.hr.identiteacces.dto.SocieteDtos.AdminInitialRequest;
import com.hr.identiteacces.dto.SocieteDtos.CreerSocieteRequest;
import com.hr.identiteacces.dto.SocieteDtos.ModifierSocieteRequest;
import com.hr.identiteacces.dto.SocieteDtos.MotifSocieteRequest;
import com.hr.identiteacces.dto.SocieteDtos.SocieteResponse;
import com.hr.identiteacces.dto.UserResponse;
import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.kafka.SocieteEvenementPublisher;
import com.hr.identiteacces.repository.SocieteRepository;
import com.hr.identiteacces.repository.UserRepository;
import com.hr.identiteacces.service.AuthService;
import com.hr.identiteacces.service.SocieteService;
import com.hr.identiteacces.service.UserService;
import com.hr.identiteacces.web.IdentiteMetierException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration d'isolation multi-société (§ 4.4 et § 9.14) sur PostgreSQL dédié ({@code authentication_db_test}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:postgresql://localhost:5444/authentication_db_test",
		"app.internal-api.token=jeton-interne-it",
		"app.mail.collaborateur-welcome-enabled=false",
		"eureka.client.enabled=false"
})
class IsolationMultiSocieteTest {

	@MockitoBean
	private SocieteEvenementPublisher societeEvenementPublisher;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SocieteService societeService;

	@Autowired
	private AuthService authService;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SocieteRepository societeRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private SocieteContraintesRunner societeContraintesRunner;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private UUID societeAId;
	private UUID societeBId;
	private UUID userAId;
	private UUID userBId;
	private String codeA;
	private String codeB;

	@BeforeEach
	void setUp() {
		String suffixe = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
		codeA = "WFA-" + suffixe;
		codeB = "WFB-" + suffixe;

		SocieteResponse socA = societeService.creer(
				new CreerSocieteRequest(codeA, "[RECETTE-WF] Société A " + suffixe, "Africa/Tunis"));
		SocieteResponse socB = societeService.creer(
				new CreerSocieteRequest(codeB, "[RECETTE-WF] Société B " + suffixe, "Africa/Tunis"));
		societeAId = socA.id();
		societeBId = socB.id();

		// Même matricule COL-ISO dans les deux sociétés (CA-01.3)
		User uA = User.builder()
				.societeId(societeAId)
				.username("COL-ISO")
				.email("col.a." + suffixe.toLowerCase() + "@rh-evenement.test")
				.password(passwordEncoder.encode("MotDePasseA1!"))
				.roles(new HashSet<>(Set.of("USER", "RH")))
				.build();
		userAId = userRepository.saveAndFlush(uA).getId();

		User uB = User.builder()
				.societeId(societeBId)
				.username("COL-ISO")
				.email("col.b." + suffixe.toLowerCase() + "@rh-evenement.test")
				.password(passwordEncoder.encode("MotDePasseB1!"))
				.roles(new HashSet<>(Set.of("USER", "RH")))
				.build();
		userBId = userRepository.saveAndFlush(uB).getId();
	}

	@AfterEach
	void tearDown() {
		SocieteContexte.effacer();
		if (userAId != null) {
			userRepository.deleteById(userAId);
		}
		if (userBId != null) {
			userRepository.deleteById(userBId);
		}
		if (societeAId != null) {
			societeRepository.findById(societeAId).ifPresent(s -> {
				userRepository.findBySocieteIdAndRolesContaining(societeAId, "ADMIN")
						.forEach(u -> userRepository.deleteById(u.getId()));
				societeRepository.deleteById(societeAId);
			});
		}
		if (societeBId != null) {
			societeRepository.findById(societeBId).ifPresent(s -> {
				userRepository.findBySocieteIdAndRolesContaining(societeBId, "ADMIN")
						.forEach(u -> userRepository.deleteById(u.getId()));
				societeRepository.deleteById(societeBId);
			});
		}
	}

	@Test
	@DisplayName("Isolation + amélioration 10 : même matricule dans deux sociétés, by-role HTTP et identifiants-existants étanches, connexion isolée")
	void isolationCompleteEntreDeuxSocietes() throws Exception {
		// 1. Liste des comptes par rôle RH via service et via HTTP MockMvc avec X-Societe-Id
		List<UserResponse> rhA = SocieteContexte.executer(societeAId, () -> userService.getUsersByRole("RH"));
		assertThat(rhA).extracting(UserResponse::getId).contains(userAId).doesNotContain(userBId);

		List<UserResponse> rhB = SocieteContexte.executer(societeBId, () -> userService.getUsersByRole("RH"));
		assertThat(rhB).extracting(UserResponse::getId).contains(userBId).doesNotContain(userAId);

		mockMvc.perform(get("/internal/users/by-role")
						.param("role", "RH")
						.header("X-Internal-Token", "jeton-interne-it")
						.header("X-Societe-Id", societeAId.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].identifiant").value(userAId.toString()));

		mockMvc.perform(get("/internal/users/by-role")
						.param("role", "RH")
						.header("X-Internal-Token", "jeton-interne-it")
						.header("X-Societe-Id", societeBId.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].identifiant").value(userBId.toString()));

		// 1 bis. identifiants-existants étanche par société
		User propreA = User.builder()
				.societeId(societeAId)
				.username("ONLY-A")
				.email("only.a." + UUID.randomUUID().toString().substring(0, 6) + "@rh-evenement.test")
				.password(passwordEncoder.encode("MotDePasseA1!"))
				.roles(new HashSet<>(Set.of("USER")))
				.build();
		propreA = userRepository.saveAndFlush(propreA);
		try {
			List<String> existantsDansA = SocieteContexte.executer(societeAId,
					() -> userService.identifiantsExistants(List.of("only-a", "col-iso")));
			List<String> existantsDansB = SocieteContexte.executer(societeBId,
					() -> userService.identifiantsExistants(List.of("only-a", "col-iso")));

			assertThat(existantsDansA).containsExactlyInAnyOrder("only-a", "col-iso");
			assertThat(existantsDansB).containsExactly("col-iso").doesNotContain("only-a");

			mockMvc.perform(post("/internal/users/identifiants-existants")
							.header("X-Internal-Token", "jeton-interne-it")
							.header("X-Societe-Id", societeBId.toString())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"identifiants\":[\"ONLY-A\",\"COL-ISO\"]}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.existants.length()").value(1))
					.andExpect(jsonPath("$.existants[0]").value("col-iso"));
		} finally {
			userRepository.deleteById(propreA.getId());
		}

		// 2. Connexion : le mot de passe de A ne permet pas de se connecter sur B et inversement
		AuthResponse authA = authService.signin(new SigninRequest("COL-ISO", "MotDePasseA1!", codeA));
		assertThat(authA.getUser().getId()).isEqualTo(userAId);

		assertThatThrownBy(() -> authService.signin(new SigninRequest("COL-ISO", "MotDePasseA1!", codeB)))
				.isInstanceOf(IdentiteMetierException.class)
				.satisfies(e -> assertThat(((IdentiteMetierException) e).getCode()).isEqualTo("IDENTIFIANTS_INVALIDES"));

		// 3. Suspension de A bloque la connexion sur A sans affecter B
		societeService.suspendre(societeAId, new MotifSocieteRequest("[RECETTE-WF] Test suspension"));
		assertThatThrownBy(() -> authService.signin(new SigninRequest("COL-ISO", "MotDePasseA1!", codeA)))
				.isInstanceOf(IdentiteMetierException.class)
				.satisfies(e -> assertThat(((IdentiteMetierException) e).getCode()).isEqualTo("SOCIETE_SUSPENDUE"));

		AuthResponse authB = authService.signin(new SigninRequest("COL-ISO", "MotDePasseB1!", codeB));
		assertThat(authB.getUser().getId()).isEqualTo(userBId);

		// 4. Identifiant de société inexistant sur les routes plateforme / internes → 404
		UUID inconnu = UUID.randomUUID();
		assertThatThrownBy(() -> societeService.trouverParId(inconnu))
				.isInstanceOf(IdentiteMetierException.class)
				.satisfies(e -> assertThat(((IdentiteMetierException) e).getHttpStatus()).isEqualTo(404));
		assertThatThrownBy(() -> societeService.modifier(inconnu, new ModifierSocieteRequest("Test", "Africa/Tunis", 0L)))
				.isInstanceOf(IdentiteMetierException.class)
				.satisfies(e -> assertThat(((IdentiteMetierException) e).getHttpStatus()).isEqualTo(404));
	}

	@Test
	@DisplayName("Amélioration 1 : deux appels simultanés à creerAdminInitial sur la même société → une seule création, un 409 ADMIN_INITIAL_EXISTANT")
	void creerAdminInitial_concurrenceDeuxFils_unSeulCreeEtUn409() throws Exception {
		CountDownLatch pret = new CountDownLatch(2);
		CountDownLatch depart = new CountDownLatch(1);
		AtomicInteger succes = new AtomicInteger(0);
		AtomicInteger conflitsAdminExistant = new AtomicInteger(0);

		String sfx = UUID.randomUUID().toString().substring(0, 6);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Callable<Void>> taches = new ArrayList<>();
			for (int i = 1; i <= 2; i++) {
				final int idx = i;
				taches.add(() -> {
					pret.countDown();
					depart.await(5, TimeUnit.SECONDS);
					try {
						societeService.creerAdminInitial(societeAId, new AdminInitialRequest(
								"ADM-CONC-" + idx,
								"Nizar",
								"Concurrent" + idx,
								"adm.conc." + idx + "." + sfx + "@rh-evenement.test"));
						succes.incrementAndGet();
					} catch (IdentiteMetierException e) {
						if (e.getHttpStatus() == 409 && "ADMIN_INITIAL_EXISTANT".equals(e.getCode())) {
							conflitsAdminExistant.incrementAndGet();
						} else {
							throw e;
						}
					}
					return null;
				});
			}

			Future<Void> f1 = pool.submit(taches.get(0));
			Future<Void> f2 = pool.submit(taches.get(1));
			assertThat(pret.await(5, TimeUnit.SECONDS)).isTrue();
			depart.countDown();

			f1.get(10, TimeUnit.SECONDS);
			f2.get(10, TimeUnit.SECONDS);

			assertThat(succes.get()).isEqualTo(1);
			assertThat(conflitsAdminExistant.get()).isEqualTo(1);
			assertThat(userRepository.findBySocieteIdAndRolesContaining(societeAId, "ADMIN")).hasSize(1);
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	@DisplayName("Amélioration 2 : SocieteContraintesRunner supprime une contrainte unique sur username quel que soit son nom et est idempotent")
	void societeContraintesRunner_supprimeContrainteUsernameNomPersonnalise_etEstIdempotent() {
		// Supprimer temporairement userB pour pouvoir poser une contrainte unique mono-colonne sur users(username)
		userRepository.deleteById(userBId);
		userBId = null;

		jdbcTemplate.execute("ALTER TABLE users ADD CONSTRAINT contrainte_test_username_unique_seul UNIQUE (username)");
		Integer avant = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM pg_constraint WHERE conname = 'contrainte_test_username_unique_seul'",
				Integer.class);
		assertThat(avant).isEqualTo(1);

		// 1er passage : retrouve et supprime la contrainte par le catalogue PostgreSQL
		societeContraintesRunner.recreer();
		// 2e passage : idempotent
		societeContraintesRunner.recreer();

		Integer apres = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM pg_constraint WHERE conname = 'contrainte_test_username_unique_seul'",
				Integer.class);
		assertThat(apres).isZero();
	}
}
