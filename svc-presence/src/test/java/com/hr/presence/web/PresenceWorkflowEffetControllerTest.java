package com.hr.presence.web;

import com.hr.presence.config.SecurityConfig;
import com.hr.presence.service.PresenceWorkflowEffetService;
import com.hr.presence.service.PresenceWorkflowEffetService.ResultatEffet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
		controllers = PresenceWorkflowEffetController.class,
		properties = "app.internal-api.token=test-internal-token")
@Import({SecurityConfig.class, PresenceExceptionHandler.class})
class PresenceWorkflowEffetControllerTest {

	private static final String URL = "/internal/presence/v1/workflow/effets";
	private static final String TOKEN = "test-internal-token";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PresenceWorkflowEffetService effetService;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void sansJetonInterne_403() throws Exception {
		mockMvc.perform(post(URL)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"mode\":\"CONTROLER\",\"objet_type\":\"POINTAGE_MANUEL\",\"objet_id\":\""
								+ UUID.randomUUID() + "\",\"effet\":\"APPROUVER\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("JETON_INTERNE_INVALIDE"));
	}

	@Test
	void mauvaisJetonInterne_403() throws Exception {
		mockMvc.perform(post(URL)
						.header("X-Internal-Token", "mauvais-jeton")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("JETON_INTERNE_INVALIDE"));
	}

	@Test
	void controlerEtAppliquer_200_et409_et422_jamais400() throws Exception {
		UUID id = UUID.randomUUID();
		when(effetService.controler(any())).thenReturn(new ResultatEffet(200, Map.of("ok", true)));
		when(effetService.appliquer(any())).thenReturn(new ResultatEffet(200, Map.of("statut", "VALIDEE")));

		mockMvc.perform(post(URL)
						.header("X-Internal-Token", TOKEN)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"mode\":\"CONTROLER\",\"objet_type\":\"POINTAGE_MANUEL\",\"objet_id\":\""
								+ id + "\",\"effet\":\"APPROUVER\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true));

		mockMvc.perform(post(URL)
						.header("X-Internal-Token", TOKEN)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"cle_idempotence\":\"c1\",\"mode\":\"APPLIQUER\",\"objet_type\":\"HEURES_SUP\",\"objet_id\":\""
								+ id + "\",\"effet\":\"APPROUVER\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statut").value("VALIDEE"));

		// Mode invalide → 422 (jamais 400)
		mockMvc.perform(post(URL)
						.header("X-Internal-Token", TOKEN)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"mode\":\"INVALIDE\",\"objet_type\":\"POINTAGE_MANUEL\",\"objet_id\":\""
								+ id + "\",\"effet\":\"APPROUVER\"}"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("CORPS_INVALIDE"));

		// JSON illisible → 422 (jamais 400)
		mockMvc.perform(post(URL)
						.header("X-Internal-Token", TOKEN)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{json-invalide"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("CORPS_INVALIDE"));

		// Rejet métier 422 propagé tel quel
		when(effetService.controler(any())).thenThrow(
				PresenceMetierException.unprocessable("HEURE_HORS_FENETRE", "Hors fenêtre"));
		mockMvc.perform(post(URL)
						.header("X-Internal-Token", TOKEN)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"mode\":\"CONTROLER\",\"objet_type\":\"POINTAGE_MANUEL\",\"objet_id\":\""
								+ id + "\",\"effet\":\"APPROUVER\"}"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("HEURE_HORS_FENETRE"));
	}
}
