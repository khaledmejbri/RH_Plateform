package com.hr.evaluation.web;

import com.hr.evaluation.config.SecurityConfig;
import com.hr.evaluation.dto.AlerteEcartCompteur;
import com.hr.evaluation.dto.AlerteEcartDetail;
import com.hr.evaluation.dto.AlerteEcartPage;
import com.hr.evaluation.repository.EvaluationCampaignRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.security.EvaluationAccessService;
import com.hr.evaluation.security.IdentiteAppelant;
import com.hr.evaluation.security.IdentiteAppelantResolver;
import com.hr.evaluation.service.AlerteEcartTraitementService;
import com.hr.evaluation.service.EvaluationAnalyseAdminService;
import com.hr.evaluation.service.EvaluationCampaignService;
import com.hr.evaluation.service.EvaluationScoringService;
import com.hr.evaluation.service.EvaluationTemplateService;
import com.hr.evaluation.service.EvaluationWorkflowService;
import com.hr.evaluation.service.TechnicalTemplateService;
import com.hr.evaluation.service.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-54 : matrice @PreAuthorize endpoint x rôle, validation des paramètres, et non-régression des chemins
 * littéraux {@code alertes-ecart} face à {@code /{id}} de EvaluationAdminController.
 */
@WebMvcTest(controllers = {AlerteEcartController.class, EvaluationAdminController.class})
@Import(SecurityConfig.class)
class AlerteEcartControllerSecurityTest {

	private static final String BASE = "/api/rh/v1/admin/evaluations/alertes-ecart";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AlerteEcartTraitementService traitementService;
	@MockitoBean
	private IdentiteAppelantResolver identiteResolver;
	// dépendances d'EvaluationAdminController
	@MockitoBean
	private EvaluationCampaignService campaignService;
	@MockitoBean
	private EvaluationTemplateService templateService;
	@MockitoBean
	private TechnicalTemplateService technicalTemplateService;
	@MockitoBean
	private EvaluationWorkflowService workflowService;
	@MockitoBean
	private TemplateService enhancedTemplateService;
	@MockitoBean
	private EvaluationScoringService scoringService;
	@MockitoBean
	private EvaluationRepository evaluationRepository;
	@MockitoBean
	private EvaluationCampaignRepository campaignRepository;
	@MockitoBean
	private EvaluationAccessService evaluationAccess;
	@MockitoBean
	private EvaluationAnalyseAdminService analyseAdminService;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	private final UUID alerteId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		when(identiteResolver.courant()).thenReturn(new IdentiteAppelant(UUID.randomUUID(), "x", true, Optional::empty));
		when(traitementService.lister(any(), any(), any(), any(), anyInt(), anyInt()))
				.thenReturn(new AlerteEcartPage(List.of(), 0, 0, 20));
		when(traitementService.compteur()).thenReturn(new AlerteEcartCompteur(0, 0));
		when(traitementService.detail(eq(alerteId), any())).thenReturn(mock());
		when(traitementService.prendreEnCharge(eq(alerteId), any(), any())).thenReturn(mock());
		when(traitementService.cloturer(eq(alerteId), any(), any(), any())).thenReturn(mock());
	}

	private static AlerteEcartDetail mock() {
		return new AlerteEcartDetail(null, null, null, null, null, null, null, null, null, null, null, null, null,
				0, 0, null, false, null, null, List.of(), null, null, List.of(), true, null);
	}

	private static RequestPostProcessor avec(String... roles) {
		SimpleGrantedAuthority[] autorites = new SimpleGrantedAuthority[roles.length + 1];
		autorites[0] = new SimpleGrantedAuthority("ROLE_USER");
		for (int i = 0; i < roles.length; i++) {
			autorites[i + 1] = new SimpleGrantedAuthority("ROLE_" + roles[i]);
		}
		return jwt().authorities(autorites);
	}

	private void lecture(RequestPostProcessor qui, int attendu) throws Exception {
		mockMvc.perform(get(BASE).with(qui)).andExpect(status().is(attendu));
		mockMvc.perform(get(BASE + "/compteur").with(qui)).andExpect(status().is(attendu));
		mockMvc.perform(get(BASE + "/" + alerteId).with(qui)).andExpect(status().is(attendu));
	}

	private void actions(RequestPostProcessor qui, int attendu) throws Exception {
		mockMvc.perform(post(BASE + "/" + alerteId + "/prise-en-charge").with(qui)
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().is(attendu));
		mockMvc.perform(post(BASE + "/" + alerteId + "/cloture").with(qui)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"conclusion\":\"AUTRE\",\"commentaire\":\"0123456789\"}")).andExpect(status().is(attendu));
	}

	@Test
	void rhEtAdmin_lisentEtAgissent() throws Exception {
		lecture(avec("RH"), 200);
		actions(avec("RH"), 200);
		lecture(avec("ADMIN"), 200);
		actions(avec("ADMIN"), 200);
	}

	@Test
	void direction_litMaisNAgitPas() throws Exception {
		lecture(avec("DIRECTION"), 200);
		actions(avec("DIRECTION"), 403);
		verify(traitementService, never()).prendreEnCharge(any(), any(), any());
		verify(traitementService, never()).cloturer(any(), any(), any(), any());
	}

	@Test
	void collaborateurRoResponsable_403PartoutServiceNonAppele() throws Exception {
		for (String role : List.of("USER", "RO", "RESPONSABLE")) {
			RequestPostProcessor qui = role.equals("USER") ? jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
					: avec(role);
			lecture(qui, 403);
			actions(qui, 403);
		}
		verifyNoInteractions(traitementService);
	}

	@Test
	void sansJeton_401() throws Exception {
		mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(BASE + "/compteur")).andExpect(status().isUnauthorized());
		mockMvc.perform(post(BASE + "/" + alerteId + "/cloture").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void identifiantNonUuid_400ParametreInvalide() throws Exception {
		mockMvc.perform(get(BASE + "/pas-un-uuid").with(avec("RH")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
		mockMvc.perform(post(BASE + "/pas-un-uuid/cloture").with(avec("RH"))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void filtreInconnuOuMalForme_400() throws Exception {
		mockMvc.perform(get(BASE).param("niveau", "VERT").with(avec("RH")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
		mockMvc.perform(get(BASE).param("statut", "FERMEE").with(avec("RH"))).andExpect(status().isBadRequest());
		mockMvc.perform(get(BASE).param("campagneId", "x").with(avec("RH"))).andExpect(status().isBadRequest());
		mockMvc.perform(get(BASE).param("page", "abc").with(avec("RH"))).andExpect(status().isBadRequest());
	}

	@Test
	void filtresValides_transmisAuService() throws Exception {
		UUID campagne = UUID.randomUUID();
		mockMvc.perform(get(BASE).param("niveau", "URGENTE").param("statut", "OUVERTE")
						.param("campagneId", campagne.toString()).param("taille", "50").with(avec("DIRECTION")))
				.andExpect(status().isOk());
		verify(traitementService).lister(eq(com.hr.evaluation.domain.StatutAlerteEcart.OUVERTE),
				eq(com.hr.evaluation.domain.NiveauEcart.URGENTE), eq(campagne), eq(null), eq(0), eq(50));
	}

	@Test
	void priseEnChargeSansCorps_acceptee() throws Exception {
		mockMvc.perform(post(BASE + "/" + alerteId + "/prise-en-charge").with(avec("RH")))
				.andExpect(status().isOk());
	}

	@Test
	void erreurMetierDuService_gardeSonStatutSonCodeEtSesDonnees() throws Exception {
		when(traitementService.cloturer(eq(alerteId), any(), any(), any())).thenThrow(
				EvaluationMetierException.conflict("ALERTE_ETAT_INVALIDE", "déjà traitée",
						java.util.Map.of("statutActuel", "TRAITEE")));

		mockMvc.perform(post(BASE + "/" + alerteId + "/cloture").with(avec("RH"))
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALERTE_ETAT_INVALIDE"))
				.andExpect(jsonPath("$.donnees.statutActuel").value("TRAITEE"));
	}

	@Test
	void cheminsLitterauxNeSontPasCapturesParLeIdDeLAdmin() throws Exception {
		// /alertes-ecart et /alertes-ecart/compteur vont au contrôleur d'alertes ; /{id} reste au contrôleur admin.
		mockMvc.perform(get(BASE).with(avec("RH"))).andExpect(status().isOk());
		verify(traitementService).lister(any(), any(), any(), any(), anyInt(), anyInt());
		verifyNoInteractions(workflowService);

		mockMvc.perform(get(BASE + "/compteur").with(avec("RH"))).andExpect(status().isOk());
		verify(traitementService).compteur();
		verifyNoInteractions(workflowService);
	}
}
