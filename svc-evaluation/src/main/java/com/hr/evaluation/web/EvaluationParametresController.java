package com.hr.evaluation.web;

import com.hr.evaluation.entity.EvaluationParametres;
import com.hr.evaluation.security.EvaluationAccessService;
import com.hr.evaluation.service.CampagneCalendrier;
import com.hr.evaluation.service.EvaluationParametresService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Paramètres société des évaluations (SCRUM-41) : durée par défaut d'une campagne.
 */
@RestController
@RequestMapping("/api/rh/v1/admin/evaluations/parametres")
public class EvaluationParametresController {

    private final EvaluationParametresService parametresService;
    private final EvaluationAccessService evaluationAccess;

    public EvaluationParametresController(EvaluationParametresService parametresService,
            EvaluationAccessService evaluationAccess) {
        this.parametresService = parametresService;
        this.evaluationAccess = evaluationAccess;
    }

    @GetMapping
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_LECTURE)
    public Map<String, Object> obtenir() {
        return reponse(parametresService.obtenir());
    }

    @PutMapping
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_ECRITURE)
    public Map<String, Object> mettreAJour(@RequestBody Map<String, Object> corps) {
        Object valeur = corps == null ? null : corps.get("dureeCampagneJours");
        Integer duree = valeur instanceof Number n ? n.intValue() : null;
        return reponse(parametresService.mettreAJour(duree, evaluationAccess.requireJwtActorId()));
    }

    private static Map<String, Object> reponse(EvaluationParametres p) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dureeCampagneJours", p.getDureeCampagneJours());
        r.put("jourDebutDefaut", CampagneCalendrier.JOUR_DEBUT_DEFAUT);
        r.put("dureeMinJours", CampagneCalendrier.DUREE_MIN_JOURS);
        r.put("dureeMaxJours", CampagneCalendrier.DUREE_MAX_JOURS);
        r.put("modifieLe", p.getModifieLe());
        return r;
    }
}
