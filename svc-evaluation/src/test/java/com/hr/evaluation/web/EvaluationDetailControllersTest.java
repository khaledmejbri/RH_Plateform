package com.hr.evaluation.web;

import com.hr.evaluation.config.SecurityConfig;
import com.hr.evaluation.dto.CampagneDetail;
import com.hr.evaluation.dto.EvaluationDetail;
import com.hr.evaluation.dto.EvaluationDetailMobile;
import com.hr.evaluation.dto.PageEvaluations;
import com.hr.evaluation.dto.ReponseMobile;
import com.hr.evaluation.repository.EvaluationCampaignRepository;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.SkillAnswerRepository;
import com.hr.evaluation.repository.TechnicalQuestionRepository;
import com.hr.evaluation.security.EvaluationAccessService;
import com.hr.evaluation.security.IdentiteAppelant;
import com.hr.evaluation.security.IdentiteAppelantResolver;
import com.hr.evaluation.service.AlerteEcartTraitementService;
import com.hr.evaluation.service.EvaluationAnalyseAdminService;
import com.hr.evaluation.service.EvaluationCampaignService;
import com.hr.evaluation.service.EvaluationDetailService;
import com.hr.evaluation.service.EvaluationMobileLectureService;
import com.hr.evaluation.service.EvaluationScoringService;
import com.hr.evaluation.service.EvaluationTemplateService;
import com.hr.evaluation.service.EvaluationValidationSuperieurService;
import com.hr.evaluation.service.EvaluationWorkflowService;
import com.hr.evaluation.service.LibellesProfilService;
import com.hr.evaluation.service.TechnicalTemplateService;
import com.hr.evaluation.service.TemplateService;
import com.hr.evaluation.service.TemplateTechniqueEvaluation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 2B : RBAC (200 / 403 / 404 / 400) des lectures de détail, back-office et mobile. DIRECTION lit, un
 * collaborateur ou un RO sur l'URL back-office est refusé, un non-participant est refusé sur le mobile.
 */
@WebMvcTest(controllers = {EvaluationAdminController.class, EvaluationMobileController.class})
@Import(SecurityConfig.class)
class EvaluationDetailControllersTest {

	private static final String ADMIN = "/api/rh/v1/admin/evaluations";
	private static final String MOBILE = "/api/rh/v1/mobile/evaluations";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EvaluationDetailService detailService;
	@MockitoBean
	private EvaluationMobileLectureService lectureService;
	@MockitoBean
	private EvaluationAccessService evaluationAccess;
	@MockitoBean
	private IdentiteAppelantResolver identiteResolver;
	// dépendances des deux contrôleurs, sans intérêt ici
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
	private EvaluationAnalyseAdminService analyseAdminService;
	@MockitoBean
	private EvaluationQuestionRepository questionRepository;
	@MockitoBean
	private TechnicalQuestionRepository technicalQuestionRepository;
	@MockitoBean
	private SkillAnswerRepository skillAnswerRepository;
	@MockitoBean
	private TemplateTechniqueEvaluation templateTechnique;
	@MockitoBean
	private EvaluationValidationSuperieurService validationService;
	@MockitoBean
	private LibellesProfilService libellesProfil;
	@MockitoBean
	private AlerteEcartTraitementService alerteService;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	private final UUID id = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		when(identiteResolver.courant()).thenReturn(new IdentiteAppelant(UUID.randomUUID(), "x", false, Optional::empty));
	}

	private static RequestPostProcessor avec(String... roles) {
		SimpleGrantedAuthority[] a = new SimpleGrantedAuthority[roles.length];
		for (int i = 0; i < roles.length; i++) {
			a[i] = new SimpleGrantedAuthority("ROLE_" + roles[i]);
		}
		return jwt().authorities(a);
	}

	// ---------------------------------------------------------------------------------------- back-office

	@Test
	void campagne_rhEtDirectionLisent_200() throws Exception {
		when(detailService.campagne(id)).thenReturn(new CampagneDetail(id, "T4", null, "TRIMESTRIELLE", "ACTIVE", 2026,
				null, null, null, null, null, 20, null, null, CampagneDetail.Compteurs.de(36, 6, 0), null, null));
		for (String role : List.of("RH", "DIRECTION", "ADMIN")) {
			mockMvc.perform(get(ADMIN + "/campaigns/" + id).with(avec("USER", role)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.compteurs.total").value(36))
					.andExpect(jsonPath("$.compteurs.enCours").value(30))
					.andExpect(jsonPath("$.compteurs.validees").value(6))
					.andExpect(jsonPath("$.compteurs.tauxCompletion").value(17));
		}
	}

	@Test
	void campagne_collaborateurEtRo_403_sansToucherAuService() throws Exception {
		mockMvc.perform(get(ADMIN + "/campaigns/" + id).with(avec("USER"))).andExpect(status().isForbidden());
		mockMvc.perform(get(ADMIN + "/campaigns/" + id + "/evaluations").with(avec("USER")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(ADMIN + "/" + id + "/detail").with(avec("USER"))).andExpect(status().isForbidden());
		verifyNoInteractions(detailService);
	}

	@Test
	void campagne_inconnue_404_etIdMalForme_400() throws Exception {
		when(detailService.campagne(id)).thenThrow(
				EvaluationMetierException.notFound("CAMPAGNE_INTROUVABLE", "Campagne introuvable"));
		mockMvc.perform(get(ADMIN + "/campaigns/" + id).with(avec("RH")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CAMPAGNE_INTROUVABLE"));
		mockMvc.perform(get(ADMIN + "/campaigns/pas-un-uuid").with(avec("RH"))).andExpect(status().isBadRequest());
	}

	@Test
	void evaluationsDeCampagne_vide200_parametresTransmis_etPageInvalide400() throws Exception {
		when(detailService.evaluationsDeCampagne(eq(id), any(), eq(0), eq(50)))
				.thenReturn(new PageEvaluations(List.of(), 0, 0, 50));
		mockMvc.perform(get(ADMIN + "/campaigns/" + id + "/evaluations").with(avec("DIRECTION")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(0));
		when(detailService.evaluationsDeCampagne(eq(id), any(), eq(-1), eq(50))).thenThrow(
				EvaluationMetierException.badRequest("PARAMETRE_INVALIDE", "page"));
		mockMvc.perform(get(ADMIN + "/campaigns/" + id + "/evaluations").param("page", "-1").with(avec("RH")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get(ADMIN + "/campaigns/" + id + "/evaluations").param("statut", "INCONNU").with(avec("RH")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void detailEvaluation_directionLit200_inconnue404() throws Exception {
		when(detailService.detail(id)).thenReturn(new EvaluationDetail(null, List.of(), List.of(), null, null, List.of()));
		mockMvc.perform(get(ADMIN + "/" + id + "/detail").with(avec("USER", "DIRECTION"))).andExpect(status().isOk());
		UUID inconnue = UUID.randomUUID();
		when(detailService.detail(inconnue)).thenThrow(
				EvaluationMetierException.notFound("EVALUATION_INTROUVABLE", "Évaluation introuvable"));
		mockMvc.perform(get(ADMIN + "/" + inconnue + "/detail").with(avec("RH")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVALUATION_INTROUVABLE"));
	}

	@Test
	void listesFiltrees_collaborateurId_transmis_etCollaborateurSimple403() throws Exception {
		UUID collaborateur = UUID.randomUUID();
		when(detailService.lister(any(), any(), any(), any())).thenReturn(List.of());
		mockMvc.perform(get(ADMIN).param("collaborateurId", collaborateur.toString()).with(avec("DIRECTION")))
				.andExpect(status().isOk());
		verify(detailService).lister(eq(null), eq(collaborateur), eq(null), eq(null));
		mockMvc.perform(get(ADMIN + "/collaborateur/" + collaborateur).with(avec("RH"))).andExpect(status().isOk());
		mockMvc.perform(get(ADMIN + "/collaborateur/" + collaborateur).with(avec("USER")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(ADMIN).param("superieurId", "x").with(avec("RH"))).andExpect(status().isBadRequest());
	}

	// ----------------------------------------------------------------------------------------------- mobile

	@Test
	void detailMobile_participant200() throws Exception {
		when(lectureService.detail(eq(id), any())).thenReturn(new EvaluationDetailMobile(id, "T4", "Sarra", null, null,
				"Omar", "VALIDEE_COLLABORATEUR", null, null, null, "COLLABORATEUR", "En attente", null, null, null,
				null, null, null, null));
		mockMvc.perform(get(MOBILE + "/" + id + "/detail").with(avec("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messageAttente").value("En attente"))
				.andExpect(jsonPath("$.scoreSur20").doesNotExist())
				.andExpect(jsonPath("$.scores").doesNotExist());
		verify(evaluationAccess).requireParticipantEvaluation(id);
	}

	@Test
	void detailMobile_nonParticipant403_inconnue404_sansRole403() throws Exception {
		doThrow(new SecurityException("Accès non autorisé")).when(evaluationAccess).requireParticipantEvaluation(id);
		mockMvc.perform(get(MOBILE + "/" + id + "/detail").with(avec("USER"))).andExpect(status().isForbidden());

		UUID inconnue = UUID.randomUUID();
		doThrow(EvaluationMetierException.notFound("EVALUATION_INTROUVABLE", "absente"))
				.when(lectureService).verifierExiste(inconnue);
		mockMvc.perform(get(MOBILE + "/" + inconnue + "/detail").with(avec("USER"))).andExpect(status().isNotFound());

		mockMvc.perform(get(MOBILE + "/" + id + "/detail").with(avec("RH"))).andExpect(status().isForbidden());
		mockMvc.perform(get(MOBILE + "/" + id + "/detail")).andExpect(status().isUnauthorized());
	}

	@Test
	void reponsesMobile_dto_sansChampsResponsable_etNonParticipant403() throws Exception {
		when(lectureService.reponses(eq(id), any())).thenReturn(List.of(
				new ReponseMobile(UUID.randomUUID(), UUID.randomUUID(), "Q", "r", 4, null, null, null, null, null)));
		mockMvc.perform(get(MOBILE + "/" + id + "/reponses").with(avec("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].noteCollaborateur").value(4))
				.andExpect(jsonPath("$[0].noteManager").doesNotExist())
				.andExpect(jsonPath("$[0].commentaireManager").doesNotExist());

		doThrow(new SecurityException("non")).when(evaluationAccess).requireParticipantEvaluation(id);
		mockMvc.perform(get(MOBILE + "/" + id + "/reponses").with(avec("USER"))).andExpect(status().isForbidden());
	}

	private com.hr.evaluation.entity.Evaluation evaluationAvecScore(com.hr.evaluation.domain.StatutEvaluationRh statut) {
		com.hr.evaluation.entity.Evaluation e = new com.hr.evaluation.entity.Evaluation();
		e.setId(UUID.randomUUID());
		e.setCampaign(new com.hr.evaluation.entity.EvaluationCampaign());
		e.setCollaborateurIdentifiant(UUID.randomUUID());
		e.setSuperieurIdentifiant(UUID.randomUUID());
		e.setStatut(statut);
		e.setScoreSur20(14);
		return e;
	}

	@Test
	void moi_scoreSur20_cleAbsenteAvantValidee_presenteApres() throws Exception {
		when(workflowService.listerEvaluationsCollaborateur(any())).thenReturn(List.of(
				evaluationAvecScore(com.hr.evaluation.domain.StatutEvaluationRh.VALIDEE_COLLABORATEUR),
				evaluationAvecScore(com.hr.evaluation.domain.StatutEvaluationRh.VALIDEE)));
		mockMvc.perform(get(MOBILE + "/moi").with(avec("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].scoreSur20").doesNotExist())
				.andExpect(jsonPath("$[0].statut").value("VALIDEE_COLLABORATEUR"))
				.andExpect(jsonPath("$[1].scoreSur20").value(14));
	}
}
