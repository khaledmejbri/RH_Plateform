package com.hr.evaluation.service;

import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.dto.CampaignActivationResult;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.kafka.EvaluationEventPublisher;
import com.hr.evaluation.repository.EvaluationCampaignRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Cycle de vie automatique des campagnes (D-E03, SCRUM-42) :
 * <ul>
 *   <li>une campagne PLANIFIEE est lancée à sa date de commencement (évaluations créées, visibles sur le mobile,
 *   notification « Votre évaluation est ouverte ») ;</li>
 *   <li>si elle n'est pas prête (template général manquant), elle reste planifiée et les RH sont prévenus une fois ;</li>
 *   <li>une campagne ACTIVE passe TERMINEE à sa date de fin.</li>
 * </ul>
 * Remplace l'ancien planificateur de test (toutes les 2 min, collaborateur écrit en dur).
 */
@Component
public class CampagneCycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(CampagneCycleScheduler.class);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)
            .withZone(CampagneCalendrier.ZONE);

    private final EvaluationCampaignRepository campaignRepository;
    private final EvaluationCampaignService campaignService;
    private final EvaluationEventPublisher eventPublisher;

    public CampagneCycleScheduler(EvaluationCampaignRepository campaignRepository,
            EvaluationCampaignService campaignService,
            EvaluationEventPublisher eventPublisher) {
        this.campaignRepository = campaignRepository;
        this.campaignService = campaignService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "${evaluation.campagnes.cycle-cron:0 */15 * * * *}", zone = "Africa/Tunis")
    public void executerPlanification() {
        executer(Instant.now());
    }

    /** Point d'entrée testable (et déclenchable par la RH pour une recette). */
    public Bilan executer(Instant maintenant) {
        int lancees = 0;
        int bloquees = 0;
        int cloturees = 0;

        for (EvaluationCampaign c : campaignRepository.findByStatutAndDateDebutLessThanEqual(
                EvaluationCampaignStatus.PLANIFIEE, maintenant)) {
            if (!EvaluationCampaignService.templateGeneralPret(c)) {
                bloquees++;
                if (!c.isAlerteLancementEnvoyee()) {
                    eventPublisher.notifier(EvaluationEventPublisher.DESTINATAIRE_RH,
                            "Campagne non lancée — " + c.getNom(),
                            "La campagne « " + c.getNom() + " » devait démarrer le " + JOUR.format(c.getDateDebut())
                                    + " mais aucun template général publié ne lui est associé. Associez-en un : "
                                    + "elle sera lancée automatiquement au passage suivant.");
                    campaignService.marquerAlerteLancement(c.getId());
                }
                log.warn("Campagne {} non lancée : template général manquant", c.getId());
                continue;
            }
            try {
                CampaignActivationResult r = campaignService.activerCampagne(c.getId());
                lancees++;
                log.info("Campagne {} lancée automatiquement : {} évaluation(s) créée(s)",
                        c.getId(), r.evaluationsCreees());
            } catch (RuntimeException ex) {
                // Référentiel indisponible, etc. : nouvel essai au prochain passage
                log.warn("Lancement automatique de la campagne {} reporté : {}", c.getId(), ex.getMessage());
            }
        }

        for (EvaluationCampaign c : campaignRepository.findByStatutAndDateFinBefore(
                EvaluationCampaignStatus.ACTIVE, maintenant)) {
            campaignService.terminerCampagne(c.getId());
            cloturees++;
            log.info("Campagne {} clôturée automatiquement (fin le {})", c.getId(), JOUR.format(c.getDateFin()));
        }
        return new Bilan(lancees, bloquees, cloturees);
    }

    public record Bilan(int lancees, int bloquees, int cloturees) {
    }
}
