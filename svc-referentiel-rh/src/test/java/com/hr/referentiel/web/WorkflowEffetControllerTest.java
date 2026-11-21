package com.hr.referentiel.web;

import com.hr.referentiel.config.SecurityConfig;
import com.hr.referentiel.service.IdentiteSocieteClient;
import com.hr.referentiel.service.WorkflowEffetService;
import com.hr.referentiel.service.WorkflowEffetService.ResultatEffet;
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
		controllers = WorkflowEffetInterneController.class,
		properties = "app.internal-api.token=test-internal-token")
@Import({SecurityConfig.class, ReferentielExceptionHandler.class})
class WorkflowEffetControllerTest {

	private static final String URL = "/internal/referentiel/v1/workflow/effets";
	private static final String TOKEN = "test-internal-token";
	private static final UUID SOCIETE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WorkflowEffetService effetService;
	@MockitoBean
	private IdentiteSocieteClient identiteSocieteClient;
	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private org.springframework.cache.CacheManager cacheManager;

	@Test
	void sansJetonInterneRenvoie403() throws Exception {
		String body = """
				{"cle_idempotence":"c1","mode":"APPLIQUER","objet_type":"CONGE",
				 "objet_id":"11111111-1111-1111-1111-111111111111","effet":"APPROUVER"}
				""";
		mockMvc.perform(post(URL)
						.header("X-Societe-Id", SOCIETE_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isForbidden());
	}

	@Test
	void avecJetonInterneModeControlerRenvoie200() throws Exception {
		when(effetService.controler(any())).thenReturn(new ResultatEffet(200, Map.of("ok", true)));
		String body = """
				{"mode":"CONTROLER","objet_type":"CONGE",
				 "objet_id":"11111111-1111-1111-1111-111111111111","effet":"APPROUVER"}
				""";
		mockMvc.perform(post(URL)
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true));
	}

	@Test
	void avecJetonInterneModeAppliquerRenvoieStatutEtCorps() throws Exception {
		when(effetService.appliquer(any())).thenReturn(new ResultatEffet(200, Map.of("statut", "APPROUVEE")));
		String body = """
				{"cle_idempotence":"c2","mode":"APPLIQUER","objet_type":"CONGE",
				 "objet_id":"11111111-1111-1111-1111-111111111111","effet":"APPROUVER"}
				""";
		mockMvc.perform(post(URL)
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statut").value("APPROUVEE"));
	}

	@Test
	void soldeInsuffisantRemonteEn422AvecCodeEtMessage() throws Exception {
		when(effetService.appliquer(any())).thenThrow(
				new ReferentielMetierException("SOLDE_INSUFFISANT", "Solde insuffisant : 3 jours demandés, 1 jour disponible", 422));
		String body = """
				{"cle_idempotence":"c3","mode":"APPLIQUER","objet_type":"CONGE",
				 "objet_id":"11111111-1111-1111-1111-111111111111","effet":"APPROUVER"}
				""";
		mockMvc.perform(post(URL)
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("SOLDE_INSUFFISANT"))
				.andExpect(jsonPath("$.message").value("Solde insuffisant : 3 jours demandés, 1 jour disponible"));
	}
}
