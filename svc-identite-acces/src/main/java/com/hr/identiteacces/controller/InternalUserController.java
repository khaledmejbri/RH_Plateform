package com.hr.identiteacces.controller;

import com.hr.identiteacces.dto.UserResponse;
import com.hr.identiteacces.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Appels de service à service sans utilisateur connecté (destinataires des alertes d'évaluation M07, SCRUM-53 ;
 * notifications). Ouvert dans la chaîne Spring Security (pas de JWT) : la protection est le jeton partagé
 * {@code app.internal-api.token}, comparé à temps constant. La gateway ne route pas {@code /internal/**}.
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    /** Rôle attendu : majuscules et tiret bas (RH, ADMIN, DIRECTION…). */
    static final int IDENTIFIANTS_MAX = 5000;

    private static final Pattern ROLE_VALIDE = Pattern.compile("^[A-Z_]{2,30}$");

    private final UserService userService;
    private final String internalApiToken;

    public InternalUserController(UserService userService,
                                  @Value("${app.internal-api.token}") String internalApiToken) {
        this.userService = userService;
        this.internalApiToken = internalApiToken;
    }

    @GetMapping("/by-role")
    public List<UserResponse> getUsersByRole(@RequestParam String role,
                                             @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!jetonValide(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
        }
        if (role == null || !ROLE_VALIDE.matcher(role).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role");
        }
        return userService.getUsersByRole(role);
    }

    /**
     * Import des collaborateurs (D-I07) : lesquels de ces identifiants ont déjà un compte ? Comparaison sans casse,
     * réponse en minuscules. Jeton interne obligatoire ; au plus {@value #IDENTIFIANTS_MAX} identifiants.
     */
    @PostMapping("/identifiants-existants")
    public IdentifiantsExistantsResponse identifiantsExistants(
            @RequestBody IdentifiantsRequest requete,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (!jetonValide(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
        }
        List<String> ids = requete == null || requete.identifiants() == null ? List.of() : requete.identifiants();
        if (ids.size() > IDENTIFIANTS_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many identifiers");
        }
        List<String> minuscules = ids.stream().filter(java.util.Objects::nonNull).map(s -> s.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(s -> !s.isEmpty()).distinct().toList();
        return new IdentifiantsExistantsResponse(userService.identifiantsExistants(minuscules));
    }

    public record IdentifiantsRequest(@com.fasterxml.jackson.annotation.JsonProperty("identifiants") List<String> identifiants) {
    }

    public record IdentifiantsExistantsResponse(@com.fasterxml.jackson.annotation.JsonProperty("existants") List<String> existants) {
    }

    private boolean jetonValide(String token) {
        return token != null && internalApiToken != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), internalApiToken.getBytes(StandardCharsets.UTF_8));
    }
}
