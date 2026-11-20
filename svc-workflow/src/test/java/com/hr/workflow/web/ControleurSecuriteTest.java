package com.hr.workflow.web;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.config.SecurityConfig;
import com.hr.workflow.domain.ModeActivation;
import com.hr.workflow.dto.AdminWorkflowDtos.PageResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.CompteurTachesResponse;
import com.hr.workflow.moteur.DecisionService;
import com.hr.workflow.moteur.DemarrageService;
import com.hr.workflow.service.ActivationService;
import com.hr.workflow.service.InstanceInterneService;
import com.hr.workflow.service.ModeleVersionService;
import com.hr.workflow.service.SimulationService;
import com.hr.workflow.service.WorkflowUtilisateurService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matrice des rôles (§ 7) et tests anti-IDOR (D-W3-01, CA-06.8, CA-08.3) sur tous les contrôleurs W3.
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
	@MockitoBean private WorkflowUtilisateurService utilisateurService;
	@MockitoBean private DecisionService decisionService;
	@MockitoBean private DemarrageService demarrageService;
	@MockitoBean private InstanceInterneService instanceInterneService;
	@MockitoBean private JwtDecoder jwtDecoder;
	@MockitoBean private CacheManager cacheManager;

	private static RequestPostProcessor avecRoles(UUID compteId, String... roles) {
		GrantedAuthority[] auths = Arrays.stream(roles)
				.map(r -> new SimpleGrantedAuthority("ROLE_" + r))
				.toArray(GrantedAuthority[]::new);
		return jwt().jwt(j -> j.subject("user-test").claim("identifiant_utilisateur", compteId.toString()))
				.authorities(auths);
	}

	@Test
	void sansJeton_routesPubliquesRepondent401() throws Exception {
		mockMvc.perform(get("/api/rh/v1/admin/workflow/catalogue")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/rh/v1/workflow/taches/moi")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/rh/v1/workflow/types-ouverts")).andExpect(status().isUnauthorized());
	}

	@Test
	void rolePlateforme_refuse403SurToutesLesRoutesWorkflow() throws Exception {
		UUID platId = UUID.randomUUID();
		mockMvc.perform(get("/api/rh/v1/admin/workflow/catalogue").with(avecRoles(platId, "PLATEFORME")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/rh/v1/workflow/taches/moi").with(avecRoles(platId, "PLATEFORME")))
				.andExpect(status().isForbidden());
	}

	@Test
	void ca06_8_directionLitAdmin200_ecritureAdmin403() throws Exception {
		UUID dirId = UUID.randomUUID();
		when(catalogue.construireReponseCatalogue()).thenReturn(Map.of("types", List.of()));
		when(modeleVersionService.listerModeles(any(), any(), anyInt(), anyInt()))
				.thenReturn(new PageResponse<>(List.of(), 0, 20, 0));

		mockMvc.perform(get("/api/rh/v1/admin/workflow/catalogue").with(avecRoles(dirId, "DIRECTION", "USER")))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/rh/v1/admin/workflow/modeles").with(avecRoles(dirId, "DIRECTION", "USER")))
				.andExpect(status().isOk());

		// Écritures refusées 403 pour DIRECTION
		mockMvc.perform(post("/api/rh/v1/admin/workflow/modeles")
				.with(avecRoles(dirId, "DIRECTION", "USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"M1\",\"libelle\":\"Test\",\"objet_type\":\"CONGE\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/rh/v1/admin/workflow/activations/CONGE")
				.with(avecRoles(dirId, "DIRECTION", "USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mode\":\"MOTEUR\",\"version\":0}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/rh/v1/admin/workflow/versions/" + UUID.randomUUID() + "/publier")
				.with(avecRoles(dirId, "DIRECTION", "USER"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":0}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void collaborateurSimple_refuse403SurAdmin_autorise200SurMesTaches() throws Exception {
		UUID colId = UUID.randomUUID();
		when(utilisateurService.compteurMesTaches(colId)).thenReturn(new CompteurTachesResponse(2, 0));

		mockMvc.perform(get("/api/rh/v1/admin/workflow/modeles").with(avecRoles(colId, "USER")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/rh/v1/workflow/taches/moi/compteur").with(avecRoles(colId, "USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.a_traiter").value(2));
	}

	@Test
	void routesInternes_sansJetonInterne403_avecJetonValide200() throws Exception {
		when(activationService.modePour("CONGE")).thenReturn(ModeActivation.HERITE);

		mockMvc.perform(get("/internal/workflow/v1/activations/CONGE"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("JETON_INTERNE_INVALIDE"));

		mockMvc.perform(get("/internal/workflow/v1/activations/CONGE")
				.header("X-Internal-Token", "mauvais-jeton"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("JETON_INTERNE_INVALIDE"));

		mockMvc.perform(get("/internal/workflow/v1/activations/CONGE")
				.header("X-Internal-Token", "test-internal-secret"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("HERITE"));
	}

	@Test
	void antiIdor_ca08_3_utilisateurNonAutoriseRecoit404IdentiqueAInexistant() throws Exception {
		UUID autreCompte = UUID.randomUUID();
		UUID tacheId = UUID.randomUUID();
		UUID instanceId = UUID.randomUUID();

		when(utilisateurService.detailTache(eq(tacheId), eq(autreCompte), anyBoolean(), anyBoolean()))
				.thenThrow(WorkflowMetierException.notFound("TACHE_INTROUVABLE", "Tâche introuvable."));
		when(utilisateurService.suiviParInstance(eq(instanceId), eq(autreCompte), anyBoolean(), anyBoolean()))
				.thenThrow(WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable."));

		mockMvc.perform(get("/api/rh/v1/workflow/taches/" + tacheId).with(avecRoles(autreCompte, "USER")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TACHE_INTROUVABLE"));

		mockMvc.perform(get("/api/rh/v1/workflow/instances/" + instanceId + "/suivi").with(avecRoles(autreCompte, "USER")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("INSTANCE_INTROUVABLE"));
	}
}
