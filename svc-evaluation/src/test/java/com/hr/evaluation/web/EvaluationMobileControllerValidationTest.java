package com.hr.evaluation.web;

import com.hr.evaluation.config.SecurityConfig;
import com.hr.evaluation.domain.EvaluationStep;
import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.repository.SkillAnswerRepository;
import com.hr.evaluation.repository.TechnicalQuestionRepository;
import com.hr.evaluation.security.EvaluationAccessService;
import com.hr.evaluation.security.IdentiteAppelant;
import com.hr.evaluation.security.IdentiteAppelantResolver;
import com.hr.evaluation.service.EvaluationAnalyseAdminService;
import com.hr.evaluation.service.EvaluationValidationSuperieurService;
import com.hr.evaluation.service.EvaluationWorkflowService;
import com.hr.evaluation.service.LibellesProfilService;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCRUM-56 : contrat HTTP de la validation RO (200, 401, 403 / 404 / 409 / 422 avec leur code et leurs données). */
@WebMvcTest(controllers = EvaluationMobileController.class)
@Import(SecurityConfig.class)
class EvaluationMobileControllerValidationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EvaluationWorkflowService workflowService;
	@MockitoBean
	private EvaluationQuestionRepository questionRepository;
	@MockitoBean
	private TechnicalQuestionRepository technicalQuestionRepository;
	@MockitoBean
	private SkillAnswerRepository skillAnswerRepository;
	@MockitoBean
	private EvaluationAnalyseAdminService analyseService;
	@MockitoBean
	private EvaluationAccessService evaluationAccess;
	@MockitoBean
	private TemplateTechniqueEvaluation templateTechnique;
	@MockitoBean
	private EvaluationValidationSuperieurService validationService;
	@MockitoBean
	private IdentiteAppelantResolver identiteResolver;
	@MockitoBean
	private LibellesProfilService libellesProfil;
	@MockitoBean
	private com.hr.evaluation.service.EvaluationMobileLectureService lectureService;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	private final UUID id = UUID.randomUUID();
	private static final String URL = "/api/rh/v1/mobile/evaluations/{id}/validate/manager";

	@BeforeEach
	void setUp() {
		when(identiteResolver.courant()).thenReturn(new IdentiteAppelant(UUID.randomUUID(), "cs-web", false, Optional::empty));
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor utilisateur() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	private Evaluation validee() {
		EvaluationCampaign campagne = new EvaluationCampaign();
		campagne.setNom("Campagne S2");
		Evaluation e = new Evaluation();
		e.setId(id);
		e.setCampaign(campagne);
		e.setCollaborateurIdentifiant(UUID.randomUUID());
		e.setSuperieurIdentifiant(UUID.randomUUID());
		e.setStatut(StatutEvaluationRh.VALIDEE);
		e.setEtapeActuelle(EvaluationStep.EVALUATION_TECHNIQUE);
		e.setValidationSuperieurLe(Instant.now());
		return e;
	}

	@Test
	void validation200_statutValidee_sansAucunChampDAlerte() throws Exception {
		when(validationService.validerParSuperieur(eq(id), any())).thenReturn(validee());

		mockMvc.perform(post(URL, id).with(utilisateur()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statut").value("VALIDEE"))
				.andExpect(jsonPath("$.alerte").doesNotExist())
				.andExpect(jsonPath("$.niveauEcart").doesNotExist());
	}

	@Test
	void sansJeton_401() throws Exception {
		mockMvc.perform(post(URL, id)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(URL + "/etat", id)).andExpect(status().isUnauthorized());
	}

	@Test
	void erreurs404_403_409_422_gardentCodeEtDonnees() throws Exception {
		when(validationService.validerParSuperieur(eq(id), any()))
				.thenThrow(EvaluationMetierException.notFound("EVALUATION_INTROUVABLE", "absente"));
		mockMvc.perform(post(URL, id).with(utilisateur()))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EVALUATION_INTROUVABLE"));

		when(validationService.validerParSuperieur(eq(id), any()))
				.thenThrow(EvaluationMetierException.forbidden("PAS_RO_EVALUATION", "pas le RO"));
		mockMvc.perform(post(URL, id).with(utilisateur()))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PAS_RO_EVALUATION"));

		when(validationService.validerParSuperieur(eq(id), any()))
				.thenThrow(EvaluationMetierException.conflict("COLLABORATEUR_NON_VALIDE", "pas validé"));
		mockMvc.perform(post(URL, id).with(utilisateur()))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COLLABORATEUR_NON_VALIDE"));

		when(validationService.validerParSuperieur(eq(id), any())).thenThrow(
				EvaluationMetierException.unprocessable("COMPETENCES_NON_NOTEES", "non notées",
						Map.of("competencesManquantes", List.of(Map.of("questionId", "q1", "libelle", "SQL")))));
		mockMvc.perform(post(URL, id).with(utilisateur()))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("COMPETENCES_NON_NOTEES"))
				.andExpect(jsonPath("$.donnees.competencesManquantes[0].libelle").value("SQL"));
	}

	@Test
	void etat200_contratDuMobile() throws Exception {
		when(validationService.etatValidationSuperieur(eq(id), any())).thenReturn(
				new EvaluationValidationSuperieurService.EtatValidation(id, false, true, true,
						List.of(new EvaluationValidationSuperieurService.CompetenceManquante(UUID.randomUUID(), "SQL")),
						1, false));

		mockMvc.perform(get(URL + "/etat", id).with(utilisateur()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.evaluationId").value(id.toString()))
				.andExpect(jsonPath("$.dejaValidee").value(false))
				.andExpect(jsonPath("$.collaborateurAValide").value(true))
				.andExpect(jsonPath("$.avecCompetencesTechniques").value(true))
				.andExpect(jsonPath("$.nbCompetencesManquantes").value(1))
				.andExpect(jsonPath("$.competencesManquantes[0].libelle").value("SQL"))
				.andExpect(jsonPath("$.peutValider").value(false));
	}

	@Test
	void utilisateurSansRoleUser_403() throws Exception {
		mockMvc.perform(post(URL, id).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUTRE"))))
				.andExpect(status().isForbidden());
	}
}
