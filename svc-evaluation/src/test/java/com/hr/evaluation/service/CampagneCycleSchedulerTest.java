package com.hr.evaluation.service;

import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.domain.TemplateStatus;
import com.hr.evaluation.domain.TemplateType;
import com.hr.evaluation.dto.CampaignActivationResult;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.entity.EvaluationTemplate;
import com.hr.evaluation.kafka.EvaluationEventPublisher;
import com.hr.evaluation.repository.EvaluationCampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-42 : lancement automatique à la date de début, clôture à la date de fin.
 */
@ExtendWith(MockitoExtension.class)
class CampagneCycleSchedulerTest {

    @Mock
    private EvaluationCampaignRepository campaignRepository;
    @Mock
    private EvaluationCampaignService campaignService;
    @Mock
    private EvaluationEventPublisher eventPublisher;

    private CampagneCycleScheduler scheduler;
    private final Instant maintenant = Instant.parse("2026-12-20T08:00:00Z");

    @BeforeEach
    void setUp() {
        scheduler = new CampagneCycleScheduler(campaignRepository, campaignService, eventPublisher);
        lenient().when(campaignRepository.findByStatutAndDateFinBefore(EvaluationCampaignStatus.ACTIVE, maintenant))
                .thenReturn(List.of());
    }

    private EvaluationCampaign campagne(boolean templatePret) {
        EvaluationCampaign c = new EvaluationCampaign();
        c.setId(UUID.randomUUID());
        c.setNom("Campagne annuelle 2026");
        c.setStatut(EvaluationCampaignStatus.PLANIFIEE);
        c.setDateDebut(Instant.parse("2026-12-19T23:00:00Z"));
        c.setDateFin(Instant.parse("2027-01-09T22:59:59Z"));
        if (templatePret) {
            EvaluationTemplate t = new EvaluationTemplate();
            t.setType(TemplateType.GENERIC);
            t.setStatut(TemplateStatus.PUBLISHED);
            t.setActif(true);
            c.setTemplateGeneral(t);
        }
        return c;
    }

    @Test
    void campagnePlanifieeEchue_estLancee() {
        EvaluationCampaign c = campagne(true);
        when(campaignRepository.findByStatutAndDateDebutLessThanEqual(EvaluationCampaignStatus.PLANIFIEE, maintenant))
                .thenReturn(List.of(c));
        when(campaignService.activerCampagne(c.getId()))
                .thenReturn(new CampaignActivationResult(c.getId(), "ACTIVE", 35, 0, 2, 0));

        CampagneCycleScheduler.Bilan bilan = scheduler.executer(maintenant);

        assertThat(bilan.lancees()).isEqualTo(1);
        verify(campaignService).activerCampagne(c.getId());
    }

    @Test
    void sansTemplate_resteePlanifieeEtRhPrevenusUneFois() {
        EvaluationCampaign c = campagne(false);
        when(campaignRepository.findByStatutAndDateDebutLessThanEqual(EvaluationCampaignStatus.PLANIFIEE, maintenant))
                .thenReturn(List.of(c));

        CampagneCycleScheduler.Bilan bilan = scheduler.executer(maintenant);

        assertThat(bilan.bloquees()).isEqualTo(1);
        verify(campaignService, never()).activerCampagne(any());
        verify(eventPublisher).notifier(eq(EvaluationEventPublisher.DESTINATAIRE_RH),
                startsWith("Campagne non lancée"), anyString());
        verify(campaignService).marquerAlerteLancement(c.getId());
    }

    @Test
    void alerteDejaEnvoyee_pasDeNouvelleNotification() {
        EvaluationCampaign c = campagne(false);
        c.setAlerteLancementEnvoyee(true);
        when(campaignRepository.findByStatutAndDateDebutLessThanEqual(EvaluationCampaignStatus.PLANIFIEE, maintenant))
                .thenReturn(List.of(c));

        scheduler.executer(maintenant);

        verify(eventPublisher, never()).notifier(any(), any(), any());
    }

    @Test
    void echecDuLancement_reporteSansBloquerLesAutres() {
        EvaluationCampaign c1 = campagne(true);
        EvaluationCampaign c2 = campagne(true);
        when(campaignRepository.findByStatutAndDateDebutLessThanEqual(EvaluationCampaignStatus.PLANIFIEE, maintenant))
                .thenReturn(List.of(c1, c2));
        when(campaignService.activerCampagne(c1.getId())).thenThrow(new IllegalStateException("référentiel indisponible"));
        when(campaignService.activerCampagne(c2.getId()))
                .thenReturn(new CampaignActivationResult(c2.getId(), "ACTIVE", 3, 0, 0, 0));

        CampagneCycleScheduler.Bilan bilan = scheduler.executer(maintenant);

        assertThat(bilan.lancees()).isEqualTo(1);
    }

    @Test
    void campagneActiveDontLaFinEstPassee_estCloturee() {
        EvaluationCampaign c = campagne(true);
        c.setStatut(EvaluationCampaignStatus.ACTIVE);
        when(campaignRepository.findByStatutAndDateDebutLessThanEqual(EvaluationCampaignStatus.PLANIFIEE, maintenant))
                .thenReturn(List.of());
        when(campaignRepository.findByStatutAndDateFinBefore(EvaluationCampaignStatus.ACTIVE, maintenant))
                .thenReturn(List.of(c));

        CampagneCycleScheduler.Bilan bilan = scheduler.executer(maintenant);

        assertThat(bilan.cloturees()).isEqualTo(1);
        verify(campaignService).terminerCampagne(c.getId());
    }
}
