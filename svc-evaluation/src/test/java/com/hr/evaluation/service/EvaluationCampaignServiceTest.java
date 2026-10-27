package com.hr.evaluation.service;

import com.hr.evaluation.client.CollaborateurEvaluationSnapshot;
import com.hr.evaluation.client.ReferentielCollaborateurClient;
import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.domain.EvaluationCampaignType;
import com.hr.evaluation.domain.TemplateStatus;
import com.hr.evaluation.domain.TemplateType;
import com.hr.evaluation.dto.CampaignActivationResult;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.entity.EvaluationTemplate;
import com.hr.evaluation.kafka.EvaluationEventPublisher;
import com.hr.evaluation.repository.EvaluationCampaignRepository;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.EvaluationTemplateRepository;
import com.hr.evaluation.repository.TechnicalTemplateRepository;
import com.hr.evaluation.web.EvaluationMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationCampaignServiceTest {

    @Mock
    private EvaluationCampaignRepository campaignRepository;
    @Mock
    private EvaluationTemplateRepository templateRepository;
    @Mock
    private TechnicalTemplateRepository technicalTemplateRepository;
    @Mock
    private EvaluationRepository evaluationRepository;
    @Mock
    private EvaluationWorkflowService workflowService;
    @Mock
    private ReferentielCollaborateurClient referentielClient;
    @Mock
    private EvaluationEventPublisher eventPublisher;

    private EvaluationCampaignService campaignService;

    @BeforeEach
    void setUp() {
        campaignService = new EvaluationCampaignService(
                campaignRepository,
                templateRepository,
                technicalTemplateRepository,
                evaluationRepository,
                workflowService,
                referentielClient,
                eventPublisher);
    }

    @Test
    void creerCampagneAnnuelleJuinReussit() {
        UUID adminId = UUID.randomUUID();
        when(campaignRepository.save(any(EvaluationCampaign.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EvaluationCampaign campaign = campaignService.creerCampagne(
                "Évaluation Annuelle 2026",
                "Campagne annuelle",
                EvaluationCampaignType.ANNUELLE,
                2026,
                6,
                6,
                adminId);

        assertThat(campaign.getType()).isEqualTo(EvaluationCampaignType.ANNUELLE);
        assertThat(campaign.getMoisDebut()).isEqualTo(6);
        assertThat(campaign.getStatut()).isEqualTo(EvaluationCampaignStatus.PLANIFIEE);
    }

    @Test
    void creerCampagneTrimestrielleRefusee() {
        assertThatThrownBy(() -> EvaluationCampaignService.parseTypeStrict("TRIMESTRIELLE"))
                .isInstanceOf(EvaluationMetierException.class)
                .satisfies(ex -> assertThat(((EvaluationMetierException) ex).getCode())
                        .isEqualTo("TYPE_CAMPAGNE_INVALIDE"));
    }

    @Test
    void creerCampagneMoisInvalideEchoue() {
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> campaignService.creerCampagne(
                "Évaluation Mars",
                "Test",
                EvaluationCampaignType.ANNUELLE,
                2026,
                3,
                3,
                adminId))
                .isInstanceOf(EvaluationMetierException.class)
                .satisfies(ex -> assertThat(((EvaluationMetierException) ex).getCode())
                        .isEqualTo("CALENDRIER_INVALIDE"));
    }

    @Test
    void assignerTemplatesSurCampagneActiveRefuse() {
        UUID campaignId = UUID.randomUUID();
        EvaluationCampaign campaign = new EvaluationCampaign();
        campaign.setId(campaignId);
        campaign.setStatut(EvaluationCampaignStatus.ACTIVE);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> campaignService.assignerTemplates(campaignId, UUID.randomUUID(), null))
                .isInstanceOf(EvaluationMetierException.class)
                .satisfies(ex -> {
                    EvaluationMetierException m = (EvaluationMetierException) ex;
                    assertThat(m.getCode()).isEqualTo("CAMPAGNE_ACTIVE_TEMPLATES_FIGES");
                    assertThat(m.getHttpStatus()).isEqualTo(409);
                });
    }

    @Test
    void activerCampagneCreeEvaluationsEtIgnoreManagerManquant() {
        UUID campaignId = UUID.randomUUID();
        EvaluationTemplate generic = new EvaluationTemplate();
        generic.setId(UUID.randomUUID());
        generic.setType(TemplateType.GENERIC);
        generic.setStatut(TemplateStatus.PUBLISHED);
        generic.setActif(true);

        EvaluationCampaign campaign = new EvaluationCampaign();
        campaign.setId(campaignId);
        campaign.setNom("Campagne 2026");
        campaign.setStatut(EvaluationCampaignStatus.PLANIFIEE);
        campaign.setTemplateGeneral(generic);

        UUID collabOk = UUID.randomUUID();
        UUID manager = UUID.randomUUID();
        UUID collabSansMgr = UUID.randomUUID();
        UUID compteCollab = UUID.randomUUID();
        UUID compteManager = UUID.randomUUID();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(referentielClient.listerActifsPourEvaluation()).thenReturn(List.of(
                new CollaborateurEvaluationSnapshot(collabOk, "ACTIF", "DEV_LOGICIEL", "SENIOR", manager, "COLLABORATEUR",
                        compteCollab, "Sarra Khemiri", "Développeuse backend", compteManager, "Omar Belhadj"),
                new CollaborateurEvaluationSnapshot(collabSansMgr, "ACTIF", "GENIE_CIVIL", "JUNIOR", null, "RO",
                        null, "Sans Manager", null, null, null)
        ));
        when(evaluationRepository.findByCampaignIdAndCollaborateurIdentifiant(eq(campaignId), any()))
                .thenReturn(Optional.empty());

        Evaluation created = new Evaluation();
        created.setId(UUID.randomUUID());
        created.setCollaborateurIdentifiant(collabOk);
        created.setCampaign(campaign);
        created.setProfilMetierIncomplet(false);
        when(workflowService.creerEvaluationDepuisSnapshot(any(), eq(collabOk), eq(manager), eq("DEV_LOGICIEL"), eq("SENIOR")))
                .thenReturn(created);

        CampaignActivationResult result = campaignService.activerCampagne(campaignId);

        assertThat(result.statut()).isEqualTo("ACTIVE");
        assertThat(result.evaluationsCreees()).isEqualTo(1);
        assertThat(result.ignoresManagerManquant()).isEqualTo(1);
        verify(eventPublisher).publierEvaluationCampagneOuverte(created, "Campagne 2026");
        // SCRUM-31/33/34 : compte et noms figés à l'activation
        assertThat(created.getCollaborateurCompteId()).isEqualTo(compteCollab);
        assertThat(created.getCollaborateurNom()).isEqualTo("Sarra Khemiri");
        assertThat(created.getCollaborateurPoste()).isEqualTo("Développeuse backend");
        assertThat(created.getSuperieurCompteId()).isEqualTo(compteManager);
        assertThat(created.getSuperieurNom()).isEqualTo("Omar Belhadj");
        verify(workflowService, never()).creerEvaluationDepuisSnapshot(any(), eq(collabSansMgr), any(), any(), any());
    }

    @Test
    void activerSansGenericRefuse() {
        UUID campaignId = UUID.randomUUID();
        EvaluationCampaign campaign = new EvaluationCampaign();
        campaign.setId(campaignId);
        campaign.setStatut(EvaluationCampaignStatus.PLANIFIEE);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> campaignService.activerCampagne(campaignId))
                .isInstanceOf(EvaluationMetierException.class)
                .satisfies(ex -> assertThat(((EvaluationMetierException) ex).getCode())
                        .isEqualTo("CAMPAGNE_GENERIC_MANQUANT"));
    }
}
