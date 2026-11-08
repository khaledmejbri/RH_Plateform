package com.hr.notification.service;

import com.hr.notification.dto.UserIdentityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Résout le destinataire spécial « RH » en comptes, auprès de svc-identite-acces (appel interne). */
@Service
@RequiredArgsConstructor
public class AnnuaireComptesClient {

    private final RestTemplate restTemplate;

    @Value("${app.internal-api.token}")
    private String internalApiToken;

    /** Comptes de rôle RH. Une erreur réseau remonte à l'appelant : le message Kafka est alors rejoué. */
    public List<UUID> comptesRh() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalApiToken);
        UserIdentityResponse[] users = restTemplate.exchange("http://svc-identite-acces/internal/users/by-role?role=RH",
                HttpMethod.GET, new HttpEntity<Void>(headers), UserIdentityResponse[].class).getBody();
        if (users == null) {
            return List.of();
        }
        return Arrays.stream(users).map(UserIdentityResponse::getId).filter(Objects::nonNull).distinct().toList();
    }
}
