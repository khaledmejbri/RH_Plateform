package com.hr.evaluation.web;

import com.hr.evaluation.config.SecurityConfig;
import com.hr.evaluation.security.EvaluationAccessService;
import com.hr.evaluation.service.EvaluationCampaignService;
import com.hr.evaluation.service.EvaluationScoringService;
import com.hr.evaluation.service.EvaluationTemplateService;
import com.hr.evaluation.service.EvaluationWorkflowService;
import com.hr.evaluation.service.TechnicalTemplateService;
import com.hr.evaluation.service.TemplateService;
import com.hr.evaluation.repository.EvaluationCampaignRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.domain.EvaluationCampaignType;
import com.hr.evaluation.entity.EvaluationCampaign;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EvaluationAdminController.class)
@Import(SecurityConfig.class)
class EvaluationAdminControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

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
	private JwtDecoder jwtDecoder;

	@Test
	void directionPeutLireCampagnes() throws Exception {
		when(campaignService.listerCampagnes(null)).thenReturn(List.of());

		mockMvc.perform(get("/api/rh/v1/admin/evaluations/campaigns")
						.with(jwt().authorities(
								new SimpleGrantedAuthority("ROLE_USER"),
								new SimpleGrantedAuthority("ROLE_DIRECTION"))))
				.andExpect(status().isOk());
	}

	@Test
	void directionNePeutPasCreerCampagne() throws Exception {
		mockMvc.perform(post("/api/rh/v1/admin/evaluations/campaigns")
						.contentType("application/json")
						.content("""
								{"nom":"C","description":"d","type":"ANNUELLE","annee":2026,"moisDebut":1,"moisFin":12,"creePar":"11111111-1111-1111-1111-111111111111"}
								""")
						.with(jwt().authorities(
								new SimpleGrantedAuthority("ROLE_USER"),
								new SimpleGrantedAuthority("ROLE_DIRECTION"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void rhCreeCampagneCreateurLuDansLeJwt() throws Exception {
		UUID compte = UUID.fromString("22222222-2222-2222-2222-222222222222");
		EvaluationCampaign campagne = new EvaluationCampaign();
		campagne.setId(UUID.randomUUID());
		campagne.setNom("C");
		campagne.setStatut(EvaluationCampaignStatus.PLANIFIEE);
		when(evaluationAccess.requireJwtActorId()).thenReturn(compte);
		when(campaignService.creerCampagne(eq("C"), eq("d"), eq(EvaluationCampaignType.ANNUELLE), eq(2027),
				eq(12), eq(12), eq(compte))).thenReturn(campagne);

		// SCRUM-26 : creePar envoyé par le client est ignoré ; seul le JWT fait foi.
		mockMvc.perform(post("/api/rh/v1/admin/evaluations/campaigns")
						.contentType("application/json")
						.content("""
								{"nom":"C","description":"d","type":"ANNUELLE","annee":2027,"moisDebut":12,"moisFin":12,"creePar":"11111111-1111-1111-1111-111111111111"}
								""")
						.with(jwt().authorities(
								new SimpleGrantedAuthority("ROLE_USER"),
								new SimpleGrantedAuthority("ROLE_RH"))))
				.andExpect(status().isCreated());
		verify(campaignService).creerCampagne(any(), any(), any(), any(), any(), any(), eq(compte));
	}

	@Test
	void corpsIncompletRetourne400EtPas500() throws Exception {
		mockMvc.perform(post("/api/rh/v1/admin/evaluations/campaigns")
						.contentType("application/json")
						.content("""
								{"description":"d","type":"ANNUELLE"}
								""")
						.with(jwt().authorities(
								new SimpleGrantedAuthority("ROLE_USER"),
								new SimpleGrantedAuthority("ROLE_RH"))))
				.andExpect(status().isBadRequest());
		verifyNoInteractions(campaignService);
	}

	@Test
	void roleNonAutoriseAvecCorpsIncompletRetourne403AvantValidation() throws Exception {
		for (String role : List.of("ROLE_DIRECTION", "ROLE_RO")) {
			mockMvc.perform(post("/api/rh/v1/admin/evaluations/campaigns")
							.contentType("application/json")
							.content("""
									{"nom":"C","type":"ANNUELLE","annee":2027}
									""")
							.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority(role))))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void userSansBackofficeRefuseLectureAdmin() throws Exception {
		mockMvc.perform(get("/api/rh/v1/admin/evaluations/campaigns")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void roleFantomeCollaboratorRefuse() throws Exception {
		mockMvc.perform(get("/api/rh/v1/admin/evaluations/v2")
						.with(jwt().authorities(
								new SimpleGrantedAuthority("ROLE_USER"),
								new SimpleGrantedAuthority("ROLE_COLLABORATOR"))))
				.andExpect(status().isForbidden());
	}
}
