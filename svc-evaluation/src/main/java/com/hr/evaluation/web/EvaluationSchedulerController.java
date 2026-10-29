package com.hr.evaluation.web;

import com.hr.evaluation.service.CampagneCycleScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Déclenchement immédiat du cycle de vie des campagnes (recette RH) : même traitement que la tâche planifiée
 * (lancement à la date de début, clôture à la date de fin — SCRUM-42).
 */
@RestController
@RequestMapping("/api/rh/v1/evaluations/admin")
public class EvaluationSchedulerController {

    private final CampagneCycleScheduler cycleScheduler;

    public EvaluationSchedulerController(CampagneCycleScheduler cycleScheduler) {
        this.cycleScheduler = cycleScheduler;
    }

    @PostMapping("/trigger-evaluation-cycle")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_ECRITURE)
    public ResponseEntity<Map<String, Object>> triggerEvaluationCycle() {
        CampagneCycleScheduler.Bilan bilan = cycleScheduler.executer(Instant.now());
        return ResponseEntity.ok(Map.of(
                "campagnesLancees", bilan.lancees(),
                "campagnesBloquees", bilan.bloquees(),
                "campagnesCloturees", bilan.cloturees()));
    }
}
