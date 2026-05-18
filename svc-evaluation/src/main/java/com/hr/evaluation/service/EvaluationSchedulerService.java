package com.hr.evaluation.service;

import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.domain.EvaluationCampaignType;
import com.hr.evaluation.domain.TypeEvaluationRh;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.entity.EvaluationRh;
import com.hr.evaluation.kafka.EvaluationEventPublisher;
import com.hr.evaluation.repository.EvaluationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Scheduler service for automated evaluation creation and notification.
 * Runs every 2 minutes for testing purposes.
 */
@Service
public class EvaluationSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationSchedulerService.class);

    private final EvaluationCampaignService campaignService;
    private final EvaluationWorkflowService workflowService;
    private final EvaluationRepository evaluationRepository;
    private final EvaluationEventPublisher eventPublisher;

    public EvaluationSchedulerService(
            EvaluationCampaignService campaignService,
            EvaluationWorkflowService workflowService,
            EvaluationRepository evaluationRepository,
            EvaluationEventPublisher eventPublisher) {
        this.campaignService = campaignService;
        this.workflowService = workflowService;
        this.evaluationRepository = evaluationRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Runs every 2 minutes for testing.
     * In production, this should run daily or weekly.
     */
    @Scheduled(fixedRate = 120000) // 2 minutes = 120,000 milliseconds
    public void executeEvaluationCycle() {
        log.info(" [EVALUATION SCHEDULER] Starting evaluation cycle...");

        try {
            // Step 1: Check if we're in an evaluation period
            if (!campaignService.estPeriodeEvaluationActive()) {
                log.info("️  [EVALUATION SCHEDULER] No active evaluation campaign. Skipping.");
                return;
            }

            log.info("✅ [EVALUATION SCHEDULER] Active evaluation campaign found!");

            // Step 2: Get all evaluations that need to be created
            List<UUID> employeesToEvaluate = getEmployeesNeedingEvaluation();
            
            if (employeesToEvaluate.isEmpty()) {
                log.info("️  [EVALUATION SCHEDULER] All employees already have evaluations. Nothing to do.");
                return;
            }

            log.info("📊 [EVALUATION SCHEDULER] Found {} employees needing evaluation", employeesToEvaluate.size());

            // Step 3: Create evaluations for each employee
            int createdCount = 0;
            for (UUID employeeId : employeesToEvaluate) {
                try {
                    Evaluation evaluation = createEvaluationForEmployee(employeeId);
                    if (evaluation != null) {
                        createdCount++;
                                    
                        // Send notification to employee
                        sendEvaluationNotification(evaluation);
                    }
                                
                } catch (Exception e) {
                    log.error("❌ [EVALUATION SCHEDULER] Failed to create evaluation for employee {}: {}", 
                            employeeId, e.getMessage());
                }
            }

            log.info("✅ [EVALUATION SCHEDULER] Evaluation cycle completed. Created {} evaluations.", createdCount);

        } catch (Exception e) {
            log.error("❌ [EVALUATION SCHEDULER] Error in evaluation cycle: {}", e.getMessage(), e);
        }
    }

    /**
     * Get list of employees who need evaluations.
     * In a real system, this would query the employee database.
     * For testing, we'll use hardcoded test employee IDs.
     */
    private List<UUID> getEmployeesNeedingEvaluation() {
        // Get active campaign
        EvaluationCampaign campaign = campaignService.obtenirCampagneActive(
            EvaluationCampaignType.ANNUELLE, 
            LocalDate.now().getYear()
        );

        if (campaign == null) {
            return List.of();
        }

        // TODO: In production, fetch from employee service/database
        // For now, return test employee IDs
        // Replace these with actual employee IDs from your system
        return List.of(
            UUID.fromString("0a4f7069-0737-4207-97dd-7a46a45f5429") // Test employee (current user)
        );
    }

    /**
     * Create evaluation for a single employee.
     */
    @Transactional
    public Evaluation createEvaluationForEmployee(UUID employeeId) {
        // Check if evaluation already exists for this employee in current campaign
        List<Evaluation> existingEvals = evaluationRepository
            .findByCollaborateurIdentifiantOrderByCreeLeDesc(employeeId);
            
        // Check if any evaluation is in current active campaign
        EvaluationCampaign campaign = campaignService.obtenirCampagneActive(
            EvaluationCampaignType.ANNUELLE,
            LocalDate.now().getYear()
        );
            
        if (campaign != null) {
            boolean exists = existingEvals.stream()
                .anyMatch(eval -> eval.getCampaign().getId().equals(campaign.getId()));
                
            if (exists) {
                log.debug("⏭️  [EVALUATION SCHEDULER] Evaluation already exists for employee {}", employeeId);
                return null;
            }
        }
        
        // TODO: Get manager ID from employee service
        // For testing, using a placeholder manager ID
        UUID managerId = UUID.fromString("0a4f7069-0737-4207-97dd-7a46a45f5429"); // Same as employee for testing
        
        if (campaign == null) {
            throw new IllegalStateException("No active evaluation campaign found");
        }
        
        // Create evaluation
        Evaluation evaluation = workflowService.creerEvaluationPourCollaborateur(
            campaign.getId(),
            employeeId,
            managerId
        );
        
        log.info("✅ [EVALUATION SCHEDULER] Created evaluation {} for employee {}", 
                evaluation.getId(), employeeId);
            
        return evaluation;
    }

    /**
     * Send evaluation notification to employee via Kafka.
     */
    private void sendEvaluationNotification(Evaluation evaluation) {
        try {
            // Send Kafka event to notify the evaluation service and trigger notifications
            eventPublisher.publierAlerteSiNecessaire(
                mapToEvaluationRh(evaluation, "NEW_EVALUATION_CREATED")
            );
            
            log.info("📨 [EVALUATION SCHEDULER] Sent evaluation notification for evaluation {} to employee {}", 
                    evaluation.getId(), evaluation.getCollaborateurIdentifiant());

        } catch (Exception e) {
            log.error("❌ [EVALUATION SCHEDULER] Failed to send notification for evaluation {}: {}", 
                    evaluation.getId(), e.getMessage());
        }
    }

    /**
     * Manual trigger for testing (can be called from controller).
     */
    public void triggerEvaluationCycle() {
        log.info(" [EVALUATION SCHEDULER] Manual trigger invoked");
        executeEvaluationCycle();
    }

    // TODO: This is a placeholder - in production you'd have proper conversion
    private com.hr.evaluation.entity.EvaluationRh mapToEvaluationRh(Evaluation evaluation, String reason) {
        com.hr.evaluation.entity.EvaluationRh evalRh = new com.hr.evaluation.entity.EvaluationRh();
        evalRh.setId(evaluation.getId());
        evalRh.setCollaborateurIdentifiant(evaluation.getCollaborateurIdentifiant());
        evalRh.setSuperieurIdentifiant(evaluation.getSuperieurIdentifiant());
        evalRh.setType(com.hr.evaluation.domain.TypeEvaluationRh.ANNUELLE);
        evalRh.setAnnee(LocalDate.now().getYear());
        evalRh.setStatut(evaluation.getStatut());
        return evalRh;
    }
}
