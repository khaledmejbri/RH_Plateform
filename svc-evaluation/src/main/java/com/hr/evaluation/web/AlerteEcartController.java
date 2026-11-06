package com.hr.evaluation.web;

import com.hr.evaluation.domain.NiveauEcart;
import com.hr.evaluation.domain.StatutAlerteEcart;
import com.hr.evaluation.dto.AlerteEcartActionRequest;
import com.hr.evaluation.dto.AlerteEcartCompteur;
import com.hr.evaluation.dto.AlerteEcartDetail;
import com.hr.evaluation.dto.AlerteEcartOuvertesParNiveau;
import com.hr.evaluation.dto.AlerteEcartPage;
import com.hr.evaluation.security.IdentiteAppelantResolver;
import com.hr.evaluation.service.AlerteEcartTraitementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Alertes d'écart d'étoiles M07 (SCRUM-54, D-E08 / D-E09). Lecture : RH, ADMIN, DIRECTION. Actions (prise en charge,
 * clôture) : RH et ADMIN, hors personnes concernées par l'évaluation. Contrôleur mince : identité lue dans le JWT,
 * règles et précédence des erreurs dans {@link AlerteEcartTraitementService}. Couvert par la route gateway
 * {@code /api/rh/v1/admin/evaluations/**}.
 */
@RestController
@RequestMapping("/api/rh/v1/admin/evaluations/alertes-ecart")
@PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_LECTURE)
public class AlerteEcartController {

    private final AlerteEcartTraitementService traitementService;
    private final IdentiteAppelantResolver identiteResolver;

    public AlerteEcartController(AlerteEcartTraitementService traitementService,
                                 IdentiteAppelantResolver identiteResolver) {
        this.traitementService = traitementService;
        this.identiteResolver = identiteResolver;
    }

    @GetMapping
    public ResponseEntity<AlerteEcartPage> lister(
            @RequestParam(required = false) StatutAlerteEcart statut,
            @RequestParam(required = false) NiveauEcart niveau,
            @RequestParam(required = false) UUID campagneId,
            @RequestParam(required = false) UUID uniteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + AlerteEcartTraitementService.TAILLE_PAR_DEFAUT) int taille) {
        return ResponseEntity.ok(traitementService.lister(statut, niveau, campagneId, uniteId, page, taille));
    }

    @GetMapping("/compteur")
    public ResponseEntity<AlerteEcartCompteur> compteur() {
        return ResponseEntity.ok(traitementService.compteur());
    }

    /** D-E12.c : cartes de Suivi & scores — alertes OUVERTE + EN_COURS par niveau (campagne facultative). */
    @GetMapping("/ouvertes-par-niveau")
    public ResponseEntity<AlerteEcartOuvertesParNiveau> ouvertesParNiveau(
            @RequestParam(required = false) UUID campagneId) {
        return ResponseEntity.ok(traitementService.ouvertesParNiveau(campagneId));
    }

    @GetMapping("/{alerteId}")
    public ResponseEntity<AlerteEcartDetail> detail(@PathVariable UUID alerteId) {
        return ResponseEntity.ok(traitementService.detail(alerteId, identiteResolver.courant()));
    }

    @PostMapping("/{alerteId}/prise-en-charge")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_ECRITURE)
    public ResponseEntity<AlerteEcartDetail> priseEnCharge(
            @PathVariable UUID alerteId,
            @RequestBody(required = false) AlerteEcartActionRequest corps) {
        String commentaire = corps != null ? corps.commentaire() : null;
        return ResponseEntity.ok(traitementService.prendreEnCharge(alerteId, commentaire, identiteResolver.courant()));
    }

    @PostMapping("/{alerteId}/cloture")
    @PreAuthorize(EvaluationSecurityExpressions.BACKOFFICE_ECRITURE)
    public ResponseEntity<AlerteEcartDetail> cloture(
            @PathVariable UUID alerteId,
            @RequestBody(required = false) AlerteEcartActionRequest corps) {
        String conclusion = corps != null ? corps.conclusion() : null;
        String commentaire = corps != null ? corps.commentaire() : null;
        return ResponseEntity.ok(traitementService.cloturer(alerteId, conclusion, commentaire, identiteResolver.courant()));
    }
}
