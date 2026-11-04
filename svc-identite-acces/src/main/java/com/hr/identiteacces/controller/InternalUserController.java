package com.hr.identiteacces.controller;

import com.hr.identiteacces.dto.UserResponse;
import com.hr.identiteacces.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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

    private boolean jetonValide(String token) {
        return token != null && internalApiToken != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), internalApiToken.getBytes(StandardCharsets.UTF_8));
    }
}
