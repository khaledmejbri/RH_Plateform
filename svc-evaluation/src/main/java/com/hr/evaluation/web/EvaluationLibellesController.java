package com.hr.evaluation.web;

import com.hr.evaluation.dto.ParametreDto;
import com.hr.evaluation.service.ParametreService;
import com.hr.evaluation.service.ParametresEvaluation;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Libellés paramétrés des référentiels d'évaluation, un seul appel par service (ADR lot 3 § 7). Tout utilisateur
 * authentifié : un non back-office ne reçoit que les valeurs actives de la liste blanche, sans aide. Réponse
 * {@code Cache-Control: no-cache} avec {@code ETag} : le client revalide (304) sans retélécharger.
 * Route gateway existante {@code /api/rh/v1/evaluations/**}.
 */
@RestController
@RequestMapping("/api/rh/v1/evaluations/parametres")
public class EvaluationLibellesController {

    private static final Set<String> ROLES_BACKOFFICE = Set.of("ROLE_RH", "ROLE_DIRECTION", "ROLE_ADMIN");

    private final ParametreService parametreService;
    private final ParametresEvaluation parametres;

    public EvaluationLibellesController(ParametreService parametreService, ParametresEvaluation parametres) {
        this.parametreService = parametreService;
        this.parametres = parametres;
    }

    @GetMapping("/libelles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ParametreDto.Libelles> libelles(
            Authentication authentication,
            @RequestParam(name = "actifs", defaultValue = "false") boolean actifs,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String siEtagDifferent) {
        boolean backoffice = authentication.getAuthorities().stream()
                .anyMatch(a -> ROLES_BACKOFFICE.contains(a.getAuthority()));
        ParametreDto.Libelles corps = parametreService.libelles(backoffice, actifs, parametres.instantane());
        if (corps.version().equals(siEtagDifferent)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(corps.version()).cacheControl(CacheControl.noCache()).build();
        }
        return ResponseEntity.ok().eTag(corps.version()).cacheControl(CacheControl.noCache()).body(corps);
    }
}
