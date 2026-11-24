package com.hr.workflow.web;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.config.SecurityConfig;
import com.hr.workflow.domain.ModeActivation;
import com.hr.workflow.dto.AdminWorkflowDtos.PageResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.CompteurTachesResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.RegleActivePointageResponse;
import com.hr.workflow.moteur.DecisionService;
import com.hr.workflow.moteur.DemarrageService;
import com.hr.workflow.service.ActivationService;
import com.hr.workflow.service.DelegationService;
import com.hr.workflow.service.InstanceInterneService;
import com.hr.workflow.service.ModeleVersionService;
import com.hr.workflow.service.PointageRegleInterneService;
import com.hr.workflow.service.ReaffectationService;
import com.hr.workflow.service.SimulationService;
import com.hr.workflow.service.SupervisionService;
import com.hr.workflow.service.WorkflowUtilisateurService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matrice paramétrée route × rôle (§ 7, D-W3-01, D-W3-02, CA-06.8) sur toutes les routes admin, utilisateur et internes.
 */
@WebMvcTest(controllers = {
		WorkflowAdminController.class,
		WorkflowUtilisateurController.class,
		WorkflowInterneController.class
})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.internal-api.token=test-internal-secret")
class ControleurSecuriteTest {

	@Autowired private MockMvc mockMvc;

	@MockitoBean private CatalogueTypesObjet catalogue;
	@MockitoBean private ActivationService activationService;
	@MockitoBean private ModeleVersionService modeleVersionService;
	@MockitoBean private SimulationService simulationService;
	@MockitoBean private SupervisionService supervisionService;
	@MockitoBean private ReaffectationService reaffectationService;
	@MockitoBean private DelegationService delegationService;
	@MockitoBean private WorkflowUtilisateurService utilisateurService;
	@MockitoBean private DecisionService decisionService;
	@MockitoBean private DemarrageService demarrageService;
	@MockitoBean private InstanceInterneService instanceInterneService;
	@MockitoBean private PointageRegleInterneService pointageRegleInterneService;
	@MockitoBean private WorkflowReferentielClient referentielClient;
	@MockitoBean private JwtDecoder jwtDecoder;
	@MockitoBean private CacheManager cacheManager;

	private static final UUID ID_TEST = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID ID_TEST_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private static RequestPostProcessor avecRoles(UUID compteId, String... roles) {
		GrantedAuthority[] auths = Arrays.stream(roles)
				.map(r -> new SimpleGrantedAuthority("ROLE_" + r))
				.toArray(GrantedAuthority[]::new);
		return jwt().jwt(j -> j.subject("user-test").claim("identifiant_utilisateur", compteId.toString()))
				.authorities(auths);
	}

	static Stream<Arguments> matriceRoutesPubliquesEtRoles() {
		List<Object[]> routes = List.of(
				// Admin lecture (RH, ADMIN, DIRECTION -> OK ; USER, PLATEFORME -> 403 ; SANS_JETON -> 401)
				new Object[]{HttpMethod.GET, "/api/rh/v1/admin/workflow/catalogue", null, true, true, true, true},
				new Object[]{HttpMethod.GET, "/api/rh/v1/admin/workflow/activations", null, true, true, true, true},
				new Object[]{HttpMethod.GET, "/api/rh/v1/admin/workflow/modeles", null, true, true, true, true},
				new Object[]{HttpMethod.GET, "/api/rh/v1/admin/workflow/modeles/" + ID_TEST, null, true, true, true, true},
				new Object[]{HttpMethod.GET, "/api/rh/v1/admin/workflow/modeles/" + ID_TEST + "/versions", null, true, true, true, true},
				new Object[]{HttpMethod.GET, "/api/rh/v1/admin/workflow/versions/" + ID_TEST, null, true, true, true, true},
				new Object[]{HttpMethod.POST, "/api/rh/v1/admin/workflow/versions/" + ID_TEST + "/valider", null, true, true, true, true},
				new Object[]{HttpMethod.POST, "/api/rh/v1/admin/workflow/versions/" + ID_TEST + "/simuler",
						"{\"demandeur_id\":\"" + ID_TEST + "\",\"variables\":{}}", true, true, true, true},
				new Object[]{HttpMethod.GET, "/api/rh/v1/admin/workflow/versions/" + ID_TEST + "/comparer/" + ID_TEST_2, null, true, true, true, true},
				new Object[]{HttpMethod.GET, "/api/rh/v1/admin/workflow/modeles/" + ID_TEST + "/journal", null, true, true, true, true},
				new Object[]{HttpMethod.GET, "/api/rh/v1/admin/workflow/instances", null, true, true, true, true},
				new Object[]{HttpMethod.GET, "/api/rh/v1/admin/workflow/instances/" + ID_TEST, null, true, true, true, true},
				new Object[]{HttpMethod.GET, "/api/rh/v1/admin/workflow/indicateurs", null, true, true, true, true},
				new Object[]{HttpMethod.GET, "/api/rh/v1/admin/workflow/delegations", null, true, true, true, true},

				// Admin écriture (RH, ADMIN -> OK ; DIRECTION, USER, PLATEFORME -> 403 ; SANS_JETON -> 401)
				new Object[]{HttpMethod.PUT, "/api/rh/v1/admin/workflow/activations/CONGE", "{\"mode\":\"MOTEUR\",\"version\":0}", true, false, false, true},
				new Object[]{HttpMethod.POST, "/api/rh/v1/admin/workflow/modeles", "{\"code\":\"M1\",\"libelle\":\"Test\",\"objet_type\":\"CONGE\"}", true, false, false, true},
				new Object[]{HttpMethod.PUT, "/api/rh/v1/admin/workflow/modeles/" + ID_TEST, "{\"libelle\":\"Test\",\"version\":0}", true, false, false, true},
				new Object[]{HttpMethod.POST, "/api/rh/v1/admin/workflow/modeles/" + ID_TEST + "/activer", "{\"version\":0}", true, false, false, true},
				new Object[]{HttpMethod.POST, "/api/rh/v1/admin/workflow/modeles/" + ID_TEST + "/desactiver", "{\"version\":0}", true, false, false, true},
				new Object[]{HttpMethod.DELETE, "/api/rh/v1/admin/workflow/modeles/" + ID_TEST, null, true, false, false, true},
				new Object[]{HttpMethod.POST, "/api/rh/v1/admin/workflow/modeles/" + ID_TEST + "/versions", null, true, false, false, true},
				new Object[]{HttpMethod.PUT, "/api/rh/v1/admin/workflow/versions/" + ID_TEST, "{\"definition\":{},\"version\":0}", true, false, false, true},
				new Object[]{HttpMethod.POST, "/api/rh/v1/admin/workflow/versions/" + ID_TEST + "/publier", "{\"version\":0}", true, false, false, true},
				new Object[]{HttpMethod.DELETE, "/api/rh/v1/admin/workflow/versions/" + ID_TEST, null, true, false, false, true},
				new Object[]{HttpMethod.POST, "/api/rh/v1/admin/workflow/taches/" + ID_TEST + "/reaffecter",
						"{\"collaborateur_ids\":[\"" + ID_TEST + "\"],\"motif\":\"Absence valideur\",\"version\":0}", true, false, false, true},
				new Object[]{HttpMethod.POST, "/api/rh/v1/admin/workflow/delegations",
						"{\"delegant_id\":\"" + ID_TEST + "\",\"delegue_id\":\"" + ID_TEST_2 + "\",\"du\":\"2026-11-25\",\"au\":\"2026-11-30\"}", true, false, false, true},
				new Object[]{HttpMethod.DELETE, "/api/rh/v1/admin/workflow/delegations/" + ID_TEST, null, true, false, false, true},

				// Routes utilisateur (USER, RH, ADMIN, DIRECTION -> OK ; PLATEFORME -> 403 ; SANS_JETON -> 401)
				new Object[]{HttpMethod.GET, "/api/rh/v1/workflow/types-ouverts", null, true, true, true, false},
				new Object[]{HttpMethod.GET, "/api/rh/v1/workflow/taches/moi", null, true, true, true, false},
				new Object[]{HttpMethod.GET, "/api/rh/v1/workflow/taches/moi/compteur", null, true, true, true, false},
				new Object[]{HttpMethod.GET, "/api/rh/v1/workflow/taches/" + ID_TEST, null, true, true, true, false},
				new Object[]{HttpMethod.POST, "/api/rh/v1/workflow/taches/" + ID_TEST + "/decision",
						"{\"action\":\"APPROUVER\",\"version\":0,\"cle_idempotence\":\"c1\"}", true, true, true, false},
				new Object[]{HttpMethod.GET, "/api/rh/v1/workflow/instances/" + ID_TEST + "/suivi", null, true, true, true, false},
				new Object[]{HttpMethod.GET, "/api/rh/v1/workflow/objets/CONGE/" + ID_TEST + "/suivi", null, true, true, true, false},
				new Object[]{HttpMethod.GET, "/api/rh/v1/workflow/delegations/moi", null, true, true, true, false},
				new Object[]{HttpMethod.POST, "/api/rh/v1/workflow/delegations/moi",
						"{\"delegue_id\":\"" + ID_TEST_2 + "\",\"du\":\"2026-11-25\",\"au\":\"2026-11-30\"}", true, true, true, false},
				new Object[]{HttpMethod.DELETE, "/api/rh/v1/workflow/delegations/moi/" + ID_TEST, null, true, true, true, false}
		);

		List<Arguments> args = new ArrayList<>();
		String[] roles = {"USER", "RH", "ADMIN", "DIRECTION", "PLATEFORME", "SANS_JETON"};
		for (Object[] r : routes) {
			HttpMethod method = (HttpMethod) r[0];
			String path = (String) r[1];
			String body = (String) r[2];
			boolean dirOk = (boolean) r[4];
			boolean adminOnly = (boolean) r[6];
			for (String role : roles) {
				int attendu;
				if ("SANS_JETON".equals(role)) {
					attendu = 401;
				} else if ("PLATEFORME".equals(role)) {
					attendu = 403;
				} else if ("USER".equals(role)) {
					attendu = adminOnly ? 403 : 200;
				} else if ("DIRECTION".equals(role)) {
					attendu = dirOk ? 200 : 403;
				} else {
					attendu = 200;
				}
				args.add(Arguments.of(method, path, body, role, attendu));
			}
		}
		return args.stream();
	}

	@ParameterizedTest(name = "{0} {1} avec {3} -> {4}")
	@MethodSource("matriceRoutesPubliquesEtRoles")
	void matriceRoutesAdminEtUtilisateur(HttpMethod method, String path, String body, String role, int attendu) throws Exception {
		lenient().when(catalogue.construireReponseCatalogue()).thenReturn(Map.of("types", List.of()));
		lenient().when(modeleVersionService.listerModeles(any(), any(), anyInt(), anyInt()))
				.thenReturn(new PageResponse<>(List.of(), 0, 20, 0));
		lenient().when(modeleVersionService.listerJournal(any(), anyInt(), anyInt()))
				.thenReturn(new PageResponse<>(List.of(), 0, 20, 0));
		lenient().when(supervisionService.listerInstances(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
				.thenReturn(new PageResponse<>(List.of(), 0, 20, 0));
		lenient().when(delegationService.listerAdmin(any(), any(), any(), anyInt(), anyInt()))
				.thenReturn(new PageResponse<>(List.of(), 0, 20, 0));
		lenient().when(delegationService.listerMoi(any(), any(), any())).thenReturn(List.of());
		lenient().when(utilisateurService.mesTaches(any(), any(), anyInt(), anyInt()))
				.thenReturn(new PageResponse<>(List.of(), 0, 20, 0));
		lenient().when(utilisateurService.compteurMesTaches(any())).thenReturn(new CompteurTachesResponse(0, 0));
		lenient().when(decisionService.decider(any(), any(), any(), any(), any(Boolean.class)))
				.thenReturn(DecisionService.ResultatDecision.enCours());

		MockHttpServletRequestBuilder req = request(method, path);
		if (body != null) {
			req.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		if (!"SANS_JETON".equals(role)) {
			if ("RH".equals(role) || "ADMIN".equals(role) || "DIRECTION".equals(role)) {
				req.with(avecRoles(UUID.randomUUID(), role, "USER"));
			} else {
				req.with(avecRoles(UUID.randomUUID(), role));
			}
		}
		mockMvc.perform(req).andExpect(result -> {
			int code = result.getResponse().getStatus();
			if (attendu == 200) {
				// 200, 201, 202 ou 204 acceptés pour les appels autorisés
				org.assertj.core.api.Assertions.assertThat(code).isBetween(200, 204);
			} else {
				org.assertj.core.api.Assertions.assertThat(code).isEqualTo(attendu);
			}
		});
	}

	static Stream<Arguments> matriceRoutesInternes() {
		return Stream.of(
				Arguments.of(HttpMethod.POST, "/internal/workflow/v1/instances",
						"{\"objet_type\":\"CONGE\",\"objet_id\":\"" + ID_TEST + "\",\"demandeur_collaborateur_id\":\"" + ID_TEST + "\",\"demandeur_compte_id\":\"" + ID_TEST + "\",\"demandeur_nom\":\"Test\"}"),
				Arguments.of(HttpMethod.PUT, "/internal/workflow/v1/instances/" + ID_TEST + "/variables", "{\"variables\":{\"k\":1}}"),
				Arguments.of(HttpMethod.POST, "/internal/workflow/v1/instances/" + ID_TEST + "/annuler", null),
				Arguments.of(HttpMethod.POST, "/internal/workflow/v1/instances/" + ID_TEST + "/complement-fourni", null),
				Arguments.of(HttpMethod.GET, "/internal/workflow/v1/activations/CONGE", null),
				Arguments.of(HttpMethod.GET, "/internal/workflow/v1/objets/CONGE/" + ID_TEST, null),
				Arguments.of(HttpMethod.GET, "/internal/workflow/v1/pointage/regle-active", null)
		);
	}

	@ParameterizedTest(name = "Interne {0} {1}")
	@MethodSource("matriceRoutesInternes")
	void matriceRoutesInternes_jetonObligatoire(HttpMethod method, String path, String body) throws Exception {
		lenient().when(activationService.modePour("CONGE")).thenReturn(ModeActivation.HERITE);
		lenient().when(demarrageService.demarrer(any(), any()))
				.thenReturn(new DemarrageService.ResultatDemarrage(false, null));
		lenient().when(pointageRegleInterneService.lireRegleActive())
				.thenReturn(RegleActivePointageResponse.inactive(ModeActivation.HERITE));

		MockHttpServletRequestBuilder sansJeton = request(method, path);
		if (body != null) {
			sansJeton.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		mockMvc.perform(sansJeton)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("JETON_INTERNE_INVALIDE"));

		MockHttpServletRequestBuilder mauvaisJeton = request(method, path).header("X-Internal-Token", "mauvais");
		if (body != null) {
			mauvaisJeton.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		mockMvc.perform(mauvaisJeton)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("JETON_INTERNE_INVALIDE"));

		MockHttpServletRequestBuilder bonJeton = request(method, path).header("X-Internal-Token", "test-internal-secret");
		if (body != null) {
			bonJeton.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		mockMvc.perform(bonJeton).andExpect(result ->
				org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isBetween(200, 204));
	}

	@Test
	void ca06_8_directionLitAdmin200_ecritureAdmin403() throws Exception {
		UUID dirId = UUID.randomUUID();
		when(catalogue.construireReponseCatalogue()).thenReturn(Map.of("types", List.of()));
		mockMvc.perform(get("/api/rh/v1/admin/workflow/catalogue").with(avecRoles(dirId, "DIRECTION", "USER")))
				.andExpect(status().isOk());
	}

	@Test
	void routeInterneRegleActive_valideXSocieteId() throws Exception {
		when(pointageRegleInterneService.lireRegleActive())
				.thenReturn(RegleActivePointageResponse.inactive(ModeActivation.HERITE));

		mockMvc.perform(get("/internal/workflow/v1/pointage/regle-active")
						.header("X-Internal-Token", "test-internal-secret")
						.header("X-Societe-Id", "pas-un-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SOCIETE_ABSENTE"));

		mockMvc.perform(get("/internal/workflow/v1/pointage/regle-active")
						.header("X-Internal-Token", "test-internal-secret")
						.header("X-Societe-Id", ID_TEST.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.actif_effectif").value(false))
				.andExpect(jsonPath("$.mode").value("HERITE"));
	}
}
