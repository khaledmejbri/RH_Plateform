package com.hr.evaluation.service;

import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.domain.EvaluationCampaignType;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.repository.EvaluationCampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationCampaignServiceTest {

    @Mock
    private EvaluationCampaignRepository campaignRepository;

    private EvaluationCampaignService campaignService;

    @BeforeEach
    void setUp() {
       // campaignService = new EvaluationCampaignService(campaignRepository);
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
                6, // June
                6, // June
                adminId
        );

        assertThat(campaign).isNotNull();
        assertThat(campaign.getNom()).isEqualTo("Évaluation Annuelle 2026");
        assertThat(campaign.getType()).isEqualTo(EvaluationCampaignType.ANNUELLE);
        assertThat(campaign.getMoisDebut()).isEqualTo(6);
        assertThat(campaign.getStatut()).isEqualTo(EvaluationCampaignStatus.PLANIFIEE);
    }

    @Test
    void creerCampagneDecembreReussit() {
        UUID adminId = UUID.randomUUID();
        
        when(campaignRepository.save(any(EvaluationCampaign.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EvaluationCampaign campaign = campaignService.creerCampagne(
                "Évaluation Décembre 2026",
                "Campagne de décembre",
                EvaluationCampaignType.ANNUELLE,
                2026,
                12, // December
                12, // December
                adminId
        );

        assertThat(campaign).isNotNull();
        assertThat(campaign.getMoisDebut()).isEqualTo(12);
    }

    @Test
    void creerCampagneMoisInvalideEchoue() {
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> campaignService.creerCampagne(
                "Évaluation Mars",
                "Test",
                EvaluationCampaignType.ANNUELLE,
                2026,
                3, // March - Invalid!
                3,
                adminId
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("juin (6) ou décembre (12)");
    }

    @Test
    void creerCampagneMoisJanvierEchoue() {
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> campaignService.creerCampagne(
                "Évaluation Janvier",
                "Test",
                EvaluationCampaignType.ANNUELLE,
                2026,
                1, // January - Invalid!
                1,
                adminId
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("juin (6) ou décembre (12)");
    }

    @Test
    void activerCampagneHorsPeriodeEchoue() {
        UUID campaignId = UUID.randomUUID();
        EvaluationCampaign campaign = new EvaluationCampaign();
        campaign.setId(campaignId);
        campaign.setStatut(EvaluationCampaignStatus.PLANIFIEE);
        campaign.setDateDebut(Instant.now().plusSeconds(86400)); // Tomorrow
        campaign.setDateFin(Instant.now().plusSeconds(172800)); // Day after tomorrow

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> campaignService.activerCampagne(campaignId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("période valide");
    }
}
