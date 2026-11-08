package com.hr.evaluation.service;

import com.hr.evaluation.client.PersonneResumee;
import com.hr.evaluation.domain.NiveauEcart;
import com.hr.evaluation.domain.QuestionType;
import com.hr.evaluation.domain.StatutAlerteEcart;
import com.hr.evaluation.dto.CampagneDetail;
import com.hr.evaluation.dto.EvaluationDetail;
import com.hr.evaluation.dto.EvaluationLigne;
import com.hr.evaluation.entity.AlerteEcart;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationAnswer;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.repository.AlerteEcartRepository;
import com.hr.evaluation.repository.EvaluationAnswerRepository;
import com.hr.evaluation.repository.EvaluationCampaignRepository;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.SkillAnswerRepository;
import com.hr.evaluation.web.EvaluationMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Ticket 2B : noms et libellés sans identifiant, motif de profil incomplet, écart 5 contre 1, historique, 404. */
class EvaluationDetailServiceTest {

	private EvaluationRepository evaluationRepository;
	private EvaluationCampaignRepository campagneRepository;
	private EvaluationAnswerRepository answerRepository;
	private SkillAnswerRepository skillAnswerRepository;
	private AlerteEcartRepository alerteRepository;
	private EvaluationAnalyseAdminService analyse;
	private EvaluationCampaignService campagneService;
	private AlerteEcartTraitementService alerteService;
	private TemplateTechniqueEvaluation templateTechnique;
	private LibellesProfilService libelles;
	private NomsCollaborateurs noms;
	private EvaluationDetailService service;

	private final UUID evaluationId = UUID.randomUUID();
	private final UUID ficheCollab = UUID.randomUUID();
	private final UUID ficheResp = UUID.randomUUID();
	private EvaluationCampaign campagne;
	private Evaluation evaluation;

	@BeforeEach
	void setUp() {
		evaluationRepository = mock(EvaluationRepository.class);
		campagneRepository = mock(EvaluationCampaignRepository.class);
		answerRepository = mock(EvaluationAnswerRepository.class);
		skillAnswerRepository = mock(SkillAnswerRepository.class);
		alerteRepository = mock(AlerteEcartRepository.class);
		analyse = mock(EvaluationAnalyseAdminService.class);
		campagneService = mock(EvaluationCampaignService.class);
		alerteService = mock(AlerteEcartTraitementService.class);
		templateTechnique = mock(TemplateTechniqueEvaluation.class);
		libelles = mock(LibellesProfilService.class);
		noms = mock(NomsCollaborateurs.class);
		service = new EvaluationDetailService(evaluationRepository, campagneRepository, answerRepository,
				skillAnswerRepository, mock(EvaluationQuestionRepository.class), alerteRepository, analyse,
				campagneService, alerteService, new EvaluationScoringService(3), templateTechnique, libelles, noms, 100);

		campagne = new EvaluationCampaign();
		campagne.setId(UUID.randomUUID());
		campagne.setNom("T4 démo");
		evaluation = new Evaluation();
		evaluation.setId(evaluationId);
		evaluation.setCampaign(campagne);
		evaluation.setCollaborateurIdentifiant(ficheCollab);
		evaluation.setSuperieurIdentifiant(ficheResp);
		evaluation.setCollaborateurNom("Snapshot Collab");
		evaluation.setCreeLe(Instant.parse("2026-11-01T08:00:00Z"));
		evaluation.setFamilleMetierCode("DEV_BACKEND");
		evaluation.setNiveauSeniorite("CONFIRME");
		when(evaluationRepository.findByIdWithCampaign(evaluationId)).thenReturn(Optional.of(evaluation));
		when(libelles.famille("DEV_BACKEND")).thenReturn("Dev backend");
		when(libelles.niveau("CONFIRME")).thenReturn("Confirmé");
		when(analyse.notesResponsable(any())).thenReturn(Map.of());
		when(alerteRepository.findByEvaluationIdIn(any())).thenReturn(List.of());
		when(templateTechnique.questions(evaluation)).thenReturn(List.of());
		when(answerRepository.findWithQuestionByEvaluationId(evaluationId)).thenReturn(List.of());
		when(skillAnswerRepository.findByEvaluationIdOrderByQuestionTechniqueOrdreAsc(evaluationId)).thenReturn(List.of());
	}

	private void personnes() {
		when(noms.resoudre(any())).thenReturn(Map.of(
				ficheCollab, new PersonneResumee(ficheCollab, null, "Sarra Khemiri", "COL-WEB", true, null, null, null),
				ficheResp, new PersonneResumee(ficheResp, null, "Omar Belhadj", "CS-WEB", true, null, null, null)));
	}

	@Test
	void ligne_porteNomsMatriculesEtLibelles_sansIdentifiantAAfficher() {
		personnes();
		EvaluationLigne l = service.lignes(List.of(evaluation)).get(0);
		assertThat(l.collaborateurNom()).isEqualTo("Sarra Khemiri");
		assertThat(l.collaborateurMatricule()).isEqualTo("COL-WEB");
		assertThat(l.superieurNom()).isEqualTo("Omar Belhadj");
		assertThat(l.campagneNom()).isEqualTo("T4 démo");
		assertThat(l.familleMetierLibelle()).isEqualTo("Dev backend");
		assertThat(l.niveauSenioriteLibelle()).isEqualTo("Confirmé");
		// anciennes clés conservées pour le web actuel
		assertThat(l.campaignNom()).isEqualTo("T4 démo");
		assertThat(l.collaborateurIdentifiant()).isEqualTo(ficheCollab);
	}

	@Test
	void ligne_referentielMuet_repliSurLInstantane_etJamaisSurLIdentifiant() {
		when(noms.resoudre(any())).thenReturn(Map.of());
		EvaluationLigne l = service.lignes(List.of(evaluation)).get(0);
		assertThat(l.collaborateurNom()).isEqualTo("Snapshot Collab");
		assertThat(l.superieurNom()).isNull();
		assertThat(l.collaborateurMatricule()).isNull();
	}

	@Test
	void ligne_sansNoteDuResponsable_pasDeCouleur_D_E12b() {
		personnes();
		EvaluationLigne l = service.lignes(List.of(evaluation)).get(0);
		assertThat(l.aNoteResponsable()).isFalse();
		assertThat(l.couleurAlerte()).isNull();
	}

	@Test
	void ligne_alerteUrgente_niveauStatutEtIdentifiant() {
		personnes();
		AlerteEcart alerte = mock(AlerteEcart.class);
		UUID alerteId = UUID.randomUUID();
		when(alerte.getEvaluationId()).thenReturn(evaluationId);
		when(alerte.getId()).thenReturn(alerteId);
		when(alerte.getNiveauCourant()).thenReturn(NiveauEcart.URGENTE);
		when(alerte.getStatut()).thenReturn(StatutAlerteEcart.OUVERTE);
		when(alerteRepository.findByEvaluationIdIn(any())).thenReturn(List.of(alerte));
		EvaluationLigne l = service.lignes(List.of(evaluation)).get(0);
		assertThat(l.niveauAlerte()).isEqualTo("URGENTE");
		assertThat(l.alerteStatut()).isEqualTo("OUVERTE");
		assertThat(l.alerteId()).isEqualTo(alerteId);
	}

	@Test
	void motifProfilIncomplet_expliqueLeBadge() {
		personnes();
		evaluation.setProfilMetierIncomplet(true);
		evaluation.setFamilleMetierCode(null);
		assertThat(service.lignes(List.of(evaluation)).get(0).motifProfilIncomplet()).isEqualTo("FAMILLE_ABSENTE");
		evaluation.setFamilleMetierCode("DEV_BACKEND");
		evaluation.setNiveauSeniorite(" ");
		assertThat(service.lignes(List.of(evaluation)).get(0).motifProfilIncomplet()).isEqualTo("NIVEAU_ABSENT");
		evaluation.setFamilleMetierCode("");
		assertThat(service.lignes(List.of(evaluation)).get(0).motifProfilIncomplet()).isEqualTo("LES_DEUX");
		evaluation.setProfilMetierIncomplet(false);
		assertThat(service.lignes(List.of(evaluation)).get(0).motifProfilIncomplet()).isNull();
	}

	@Test
	void compteurs_memesDefinitionsQueSuiviEtScores() {
		CampagneDetail.Compteurs c = CampagneDetail.Compteurs.de(36, 6, 2);
		assertThat(c.total()).isEqualTo(36);
		assertThat(c.validees()).isEqualTo(6);
		assertThat(c.enCours()).isEqualTo(30);
		assertThat(c.tauxCompletion()).isEqualTo(17);
		assertThat(c.sansQuestionnaireTechnique()).isEqualTo(2);
		assertThat(CampagneDetail.Compteurs.de(0, 0, 0).tauxCompletion()).isZero();
	}

	@Test
	void campagne_inconnue_404() {
		UUID inconnue = UUID.randomUUID();
		when(campagneRepository.findById(inconnue)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.campagne(inconnue)).isInstanceOfSatisfying(EvaluationMetierException.class,
				e -> {
					assertThat(e.getHttpStatus()).isEqualTo(404);
					assertThat(e.getCode()).isEqualTo("CAMPAGNE_INTROUVABLE");
				});
		assertThatThrownBy(() -> service.evaluationsDeCampagne(inconnue, null, 0, 50))
				.isInstanceOfSatisfying(EvaluationMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(404));
	}

	@Test
	void evaluationsDeCampagne_pageOuTailleInvalide_400() {
		assertThatThrownBy(() -> service.evaluationsDeCampagne(campagne.getId(), null, -1, 50))
				.isInstanceOfSatisfying(EvaluationMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(400));
		assertThatThrownBy(() -> service.evaluationsDeCampagne(campagne.getId(), null, 0, 0))
				.isInstanceOfSatisfying(EvaluationMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(400));
		assertThatThrownBy(() -> service.evaluationsDeCampagne(campagne.getId(), null, 0, 101))
				.isInstanceOfSatisfying(EvaluationMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(400));
	}

	@Test
	void detail_inconnue_404() {
		assertThatThrownBy(() -> service.detail(UUID.randomUUID())).isInstanceOfSatisfying(EvaluationMetierException.class,
				e -> {
					assertThat(e.getHttpStatus()).isEqualTo(404);
					assertThat(e.getCode()).isEqualTo("EVALUATION_INTROUVABLE");
				});
	}

	@Test
	void detail_competenceCinqContreUn_ecartQuatreUrgente_etHistoriqueOrdonne() {
		personnes();
		evaluation.setValidationCollaborateurLe(Instant.parse("2026-11-03T09:00:00Z"));
		evaluation.setValidationSuperieurLe(Instant.parse("2026-11-05T09:00:00Z"));
		evaluation.setScoreSur20(10);

		EvaluationQuestion q = new EvaluationQuestion();
		UUID qid = UUID.randomUUID();
		q.setId(qid);
		q.setLibelle("Spring Boot");
		q.setTypeQuestion(QuestionType.RATING);
		EvaluationAnswer a = new EvaluationAnswer();
		a.setQuestion(q);
		a.setNoteCollaborateur(5);
		a.setNoteManager(1);
		a.setCommentaireManager("insuffisant");
		when(templateTechnique.questions(evaluation)).thenReturn(List.of(q));
		when(answerRepository.findWithQuestionByEvaluationId(evaluationId)).thenReturn(List.of(a));

		EvaluationDetail d = service.detail(evaluationId);
		assertThat(d.competencesTechniques()).hasSize(1);
		EvaluationDetail.Question c = d.competencesTechniques().get(0);
		assertThat(c.noteCollaborateur()).isEqualTo(5);
		assertThat(c.noteResponsable()).isEqualTo(1);
		assertThat(c.commentaireResponsable()).isEqualTo("insuffisant");
		assertThat(c.ecart()).isEqualTo(4);
		assertThat(c.niveauEcart()).isEqualTo("URGENTE");
		assertThat(d.questionsGenerales()).isEmpty();
		assertThat(d.scores().moyenneCollaborateur()).isNotNull();
		assertThat(d.scores().moyenneResponsable()).isNotNull();
		assertThat(d.scores().appreciation()).isEqualTo("A_AMELIORER");
		assertThat(d.scores().appreciationLibelle()).isEqualTo("À améliorer");
		assertThat(d.historique()).extracting(EvaluationDetail.Evenement::evenement)
				.containsExactly("CREEE", "VALIDEE_COLLABORATEUR", "VALIDEE_RESPONSABLE");
		assertThat(d.historique().get(1).acteurNom()).isEqualTo("Sarra Khemiri");
	}

	@Test
	void detail_sansAucuneNote_pasDeScores() {
		personnes();
		EvaluationDetail d = service.detail(evaluationId);
		assertThat(d.scores()).isNull();
		assertThat(d.alerte()).isNull();
		assertThat(d.historique()).hasSize(1);
		assertThat(ReflectionTestUtils.getField(service, "tailleMax")).isEqualTo(100);
	}
}
