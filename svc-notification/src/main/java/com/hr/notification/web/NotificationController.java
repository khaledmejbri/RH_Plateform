package com.hr.notification.web;

import com.hr.notification.dto.CompteurNotifications;
import com.hr.notification.dto.PageNotifications;
import com.hr.notification.service.NotificationStockageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * « Mes notifications » (D-NOT01). Le compte est lu dans le JWT, jamais dans un paramètre ni un en-tête :
 * aucun rôle ne lit les notifications d'un autre compte.
 */
@RestController
@RequestMapping("/api/rh/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationStockageService stockage;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageNotifications lister(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(required = false) Boolean lu,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int taille) {
        return stockage.lister(compteDe(jwt), lu, page, taille);
    }

    @GetMapping("/compteur")
    @PreAuthorize("isAuthenticated()")
    public CompteurNotifications compteur(@AuthenticationPrincipal Jwt jwt) {
        return stockage.compter(compteDe(jwt));
    }

    @PostMapping("/{id}/lue")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> marquerLue(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        stockage.marquerLue(id, compteDe(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lues")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> toutMarquerLu(@AuthenticationPrincipal Jwt jwt) {
        stockage.marquerToutesLues(compteDe(jwt));
        return ResponseEntity.noContent().build();
    }

    /** Compte de l'appelant (claim {@code identifiant_utilisateur}) ; jeton sans compte exploitable : 403. */
    static UUID compteDe(Jwt jwt) {
        String brut = jwt == null ? null : jwt.getClaimAsString("identifiant_utilisateur");
        try {
            if (brut != null) {
                return UUID.fromString(brut);
            }
        } catch (IllegalArgumentException ignored) {
            // traité ci-dessous
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Compte introuvable dans le jeton");
    }
}
