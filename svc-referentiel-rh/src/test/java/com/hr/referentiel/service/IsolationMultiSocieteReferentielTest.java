package com.hr.referentiel.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.config.ImportChampsAmorceRunner;
import com.hr.referentiel.config.SocieteRepriseRunner;
import com.hr.referentiel.config.SoldesCongeAmorceRunner;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.importation.IdentiteInterneClient;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.service.IdentiteSocieteClient.SocieteResume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'isolation multi-société de bout en bout (B4, § 9.14, § 10.3) sur base PostgreSQL réelle
 * ({@code referentiel_rh_db_test}, schéma {@code test_isolation_w1}) via {@link MockMvc}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
class IsolationMultiSocieteReferentielTest {

	private static final UUID SOC_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID SOC_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
	private static final String TOKEN_INTERNE = "isolation-test-internal-token";

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) throws Exception {
		String password = System.getenv("TEST_DB_PASSWORD");
		String baseUrl = System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://localhost:5444/referentiel_rh_db_test");
		String user = System.getenv().getOrDefault("TEST_DB_USER", "postgres");
		try (Connection c = DriverManager.getConnection(baseUrl, user, password);
				Statement st = c.createStatement()) {
			st.execute("DROP SCHEMA IF EXISTS test_isolation_w1 CASCADE");
			st.execute("CREATE SCHEMA test_isolation_w1");
		}
		String sep = baseUrl.contains("?") ? "&" : "?";
		registry.add("spring.datasource.url", () -> baseUrl + sep + "currentSchema=test_isolation_w1");
		registry.add("spring.datasource.username", () -> user);
		registry.add("spring.datasource.password", () -> password);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
		registry.add("spring.kafka.listener.auto-startup", () -> "false");
		registry.add("eureka.client.enabled", () -> "false");
		registry.add("app.internal-api.token", () -> TOKEN_INTERNE);
		registry.add("referentiel.conges.acquisition.au-demarrage", () -> "false");
	}

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private JsonMapper jsonMapper;
	@Autowired
	private UniteOrganisationRepository uniteRepository;
	@Autowired
	private SocieteRepriseRunner repriseRunner;
	@Autowired
	private ImportChampsAmorceRunner importChampsAmorceRunner;
	@Autowired
	private SoldesCongeAmorceRunner soldesCongeAmorceRunner;

	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private IdentiteSocieteClient identiteSocieteClient;
	@MockitoBean
	private IdentiteInterneClient identiteInterneClient;

	private UUID uniteIdA;
	private UUID uniteIdB;

	@BeforeEach
	void initialiserSocietes() {
		when(identiteSocieteClient.lister()).thenReturn(List.of(SOC_A, SOC_B));
		when(identiteSocieteClient.trouverIdParCode("PRINCIPALE")).thenReturn(Optional.of(SOC_A));
		when(identiteSocieteClient.trouverParId(SOC_A)).thenReturn(Optional.of(
				new SocieteResume(SOC_A, "SOC_A", "Société A", "ACTIVE", "Africa/Tunis")));
		when(identiteSocieteClient.trouverParId(SOC_B)).thenReturn(Optional.of(
				new SocieteResume(SOC_B, "SOC_B", "Société B", "ACTIVE", "Europe/Paris")));

		// Reprise + amorçage pour les deux sociétés
		repriseRunner.run(null);
		importChampsAmorceRunner.amorcerPourSociete(SOC_A);
		importChampsAmorceRunner.amorcerPourSociete(SOC_B);
		soldesCongeAmorceRunner.amorcerPourSociete(SOC_A);
		soldesCongeAmorceRunner.amorcerPourSociete(SOC_B);

		uniteIdA = assurerUnite(SOC_A, "DIR");
		uniteIdB = assurerUnite(SOC_B, "DIR");
	}

	@Test
	@DisplayName("PostgreSQL MockMvc : isolation totale collaborateurs (création/409/recherche/export), fonctions, paramètres, champs perso et endpoints internes")
	void isolationCompletePostgreSqlMockMvc() throws Exception {
		// 1. Création collaborateur matricule M-ISO-01 dans SOC_A (201) puis doublon dans SOC_A (409), puis M-ISO-01 dans SOC_B (201)
		String bodyCollabA = """
				{
				  "matricule": "M-ISO-01",
				  "prenom": "Alice",
				  "nom": "TenantA",
				  "courriel_professionnel": "alice.a@example.com",
				  "unite_identifiant": "%s",
				  "compte_utilisateur_id": "%s",
				  "statut": "ACTIF"
				}
				""".formatted(uniteIdA, UUID.randomUUID());
		MvcResult resCollabA = mockMvc.perform(post("/api/referentiel/v1/collaborateurs")
						.with(jwtSociete(SOC_A, "RH", "ADMIN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(bodyCollabA))
				.andExpect(status().isCreated())
				.andReturn();
		UUID collabIdA = UUID.fromString(jsonMapper.readTree(resCollabA.getResponse().getContentAsString()).get("identifiant").asText());

		mockMvc.perform(post("/api/referentiel/v1/collaborateurs")
						.with(jwtSociete(SOC_A, "RH", "ADMIN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(bodyCollabA))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erreur").value("Matricule déjà utilisé : M-ISO-01"));

		String bodyCollabB = """
				{
				  "matricule": "M-ISO-01",
				  "prenom": "Bob",
				  "nom": "TenantB",
				  "courriel_professionnel": "bob.b@example.com",
				  "unite_identifiant": "%s",
				  "compte_utilisateur_id": "%s",
				  "statut": "ACTIF"
				}
				""".formatted(uniteIdB, UUID.randomUUID());
		MvcResult resCollabB = mockMvc.perform(post("/api/referentiel/v1/collaborateurs")
						.with(jwtSociete(SOC_B, "RH", "ADMIN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(bodyCollabB))
				.andExpect(status().isCreated())
				.andReturn();
		UUID collabIdB = UUID.fromString(jsonMapper.readTree(resCollabB.getResponse().getContentAsString()).get("identifiant").asText());
		assertThat(collabIdB).isNotEqualTo(collabIdA);

		// 2. Recherche Specifications & Export CSV : SOC_B ne voit jamais TenantA
		mockMvc.perform(get("/api/referentiel/v1/collaborateurs")
						.param("q", "TenantA")
						.with(jwtSociete(SOC_B, "RH")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contenu").isEmpty());

		MvcResult exportB = mockMvc.perform(get("/api/referentiel/v1/collaborateurs/export")
						.with(jwtSociete(SOC_B, "RH")))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(exportB.getResponse().getContentAsString())
				.contains("TenantB")
				.doesNotContain("TenantA");

		// 3. Accès croisé par ID collaborateur -> 404 (corps identique à un UUID inconnu)
		MvcResult crossCollab = mockMvc.perform(get("/api/referentiel/v1/collaborateurs/" + collabIdA)
						.with(jwtSociete(SOC_B, "RH")))
				.andExpect(status().isNotFound())
				.andReturn();
		MvcResult unknownCollab = mockMvc.perform(get("/api/referentiel/v1/collaborateurs/" + UUID.randomUUID())
						.with(jwtSociete(SOC_B, "RH")))
				.andExpect(status().isNotFound())
				.andReturn();
		assertThat(crossCollab.getResponse().getContentAsString())
				.isEqualTo(unknownCollab.getResponse().getContentAsString());

		// 4. Fonctions : même libellé autorisé dans SOC_A et SOC_B, lecture/suppression croisée -> 404 identique
		MvcResult fnA = mockMvc.perform(post("/api/rh/v1/admin/fonctions")
						.with(jwtSociete(SOC_A, "ADMIN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"libelle\":\"Auditeur Iso\",\"description\":\"A\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		UUID fnIdA = UUID.fromString(jsonMapper.readTree(fnA.getResponse().getContentAsString()).get("id").asText());

		mockMvc.perform(post("/api/rh/v1/admin/fonctions")
						.with(jwtSociete(SOC_B, "ADMIN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"libelle\":\"Auditeur Iso\",\"description\":\"B\"}"))
				.andExpect(status().isCreated());

		MvcResult crossFn = mockMvc.perform(get("/api/rh/v1/admin/fonctions/" + fnIdA)
						.with(jwtSociete(SOC_B, "ADMIN")))
				.andExpect(status().isNotFound())
				.andReturn();
		MvcResult unknownFn = mockMvc.perform(get("/api/rh/v1/admin/fonctions/" + UUID.randomUUID())
						.with(jwtSociete(SOC_B, "ADMIN")))
				.andExpect(status().isNotFound())
				.andReturn();
		assertThat(jsonMapper.readTree(crossFn.getResponse().getContentAsString()).get("code"))
				.isEqualTo(jsonMapper.readTree(unknownFn.getResponse().getContentAsString()).get("code"));

		mockMvc.perform(delete("/api/rh/v1/admin/fonctions/" + fnIdA)
						.with(jwtSociete(SOC_B, "ADMIN")))
				.andExpect(status().isNotFound());

		// 5. Paramètres société : modification dans SOC_A n'affecte pas SOC_B
		MvcResult paramsARes = mockMvc.perform(get("/api/rh/v1/admin/societe/parametres")
						.with(jwtSociete(SOC_A, "ADMIN")))
				.andExpect(status().isOk())
				.andReturn();
		long versionA = jsonMapper.readTree(paramsARes.getResponse().getContentAsString()).get("version").asLong();

		mockMvc.perform(put("/api/rh/v1/admin/societe/parametres")
						.with(jwtSociete(SOC_A, "ADMIN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "jours_travailles": ["LUN","MAR","MER","JEU","VEN"],
								  "duree_hebdo_minutes": 2100,
								  "duree_jour_minutes": {"LUN":420,"MAR":420,"MER":420,"JEU":420,"VEN":420},
								  "heure_debut_journee": "09:00",
								  "heure_fin_journee": "17:00",
								  "base_decompte_conge": "JOURS_TRAVAILLES",
								  "duree_max_sortie_minutes": 90,
								  "delegation_auto_conges": false,
								  "publication_quatre_yeux": true,
								  "version": %d
								}
								""".formatted(versionA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.duree_max_sortie_minutes").value(90));

		mockMvc.perform(get("/api/rh/v1/admin/societe/parametres")
						.with(jwtSociete(SOC_B, "ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.duree_max_sortie_minutes").value(120))
				.andExpect(jsonPath("$.societe_id").value(SOC_B.toString()));

		// 6. Champs personnalisés : créé dans SOC_A invisible dans SOC_B
		MvcResult importParamA = mockMvc.perform(get("/api/referentiel/v1/parametres/imports/collaborateurs")
						.with(jwtSociete(SOC_A, "ADMIN")))
				.andExpect(status().isOk())
				.andReturn();
		long verImpA = jsonMapper.readTree(importParamA.getResponse().getContentAsString()).get("version").asLong();

		mockMvc.perform(post("/api/referentiel/v1/parametres/imports/collaborateurs/champs-personnalises")
						.with(jwtSociete(SOC_A, "ADMIN"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "version": %d,
								  "libelle": "Badge Iso A",
								  "finalite": "Test isolation",
								  "type_valeur": "TEXTE",
								  "visible_collaborateur": true,
								  "confirmation_donnees_non_sensibles": true
								}
								""".formatted(verImpA)))
				.andExpect(status().isCreated());

		MvcResult importParamB = mockMvc.perform(get("/api/referentiel/v1/parametres/imports/collaborateurs")
						.with(jwtSociete(SOC_B, "ADMIN")))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(importParamB.getResponse().getContentAsString()).doesNotContain("Badge Iso A");

		// 7. Endpoints internes : demandeur de SOC_A appelé depuis SOC_B -> 404 COLLABORATEUR_INTROUVABLE
		mockMvc.perform(post("/internal/referentiel/v1/workflow/resolutions")
						.header("X-Internal-Token", TOKEN_INTERNE)
						.header("X-Societe-Id", SOC_B.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"demandeur_id\":\"" + collabIdA + "\",\"regles\":[{\"cle\":\"e1\",\"regle\":\"DEMANDEUR\"}]}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COLLABORATEUR_INTROUVABLE"));

		mockMvc.perform(get("/internal/referentiel/v1/collaborateurs/" + collabIdA + "/resume")
						.header("X-Internal-Token", TOKEN_INTERNE)
						.header("X-Societe-Id", SOC_B.toString()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COLLABORATEUR_INTROUVABLE"));

		mockMvc.perform(get("/internal/referentiel/v1/societe/calendrier")
						.header("X-Internal-Token", TOKEN_INTERNE)
						.header("X-Societe-Id", SOC_B.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.societe_id").value(SOC_B.toString()))
				.andExpect(jsonPath("$.fuseau").value("Europe/Paris"));
	}

	private UUID assurerUnite(UUID societeId, String code) {
		return SocieteContexte.executer(societeId, () ->
				uniteRepository.findByCodeIgnoreCase(code)
						.map(UniteOrganisation::getId)
						.orElseGet(() -> {
							UniteOrganisation u = new UniteOrganisation();
							u.setCode(code);
							u.setLibelle("Unité " + code);
							u.setTypeNoeud("DIRECTION");
							u.setActif(true);
							return uniteRepository.saveAndFlush(u).getId();
						}));
	}

	private static RequestPostProcessor jwtSociete(UUID societeId, String... roles) {
		return jwt().jwt(j -> j.claim("identifiant_utilisateur", UUID.randomUUID().toString())
						.claim("societe_id", societeId.toString()))
				.authorities(java.util.Arrays.stream(roles)
						.map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
						.toArray(GrantedAuthority[]::new));
	}
}
