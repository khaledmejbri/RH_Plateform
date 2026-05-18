package com.hr.evaluation.web;

import com.hr.evaluation.service.EvaluationSchedulerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for manually triggering the evaluation scheduler.
 * Useful for testing and administrative purposes.
 */
@RestController
@RequestMapping("/api/rh/v1/evaluations/admin")
public class EvaluationSchedulerController {

    private final EvaluationSchedulerService schedulerService;

    public EvaluationSchedulerController(EvaluationSchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    /**
     * Manually trigger the evaluation creation cycle.
     * Useful for testing without waiting for the scheduled execution.
     */
    @PostMapping("/trigger-evaluation-cycle")
    public ResponseEntity<Map<String, String>> triggerEvaluationCycle() {
        schedulerService.triggerEvaluationCycle();
        
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Evaluation cycle triggered successfully. Check logs for details."
        ));
    }
}
