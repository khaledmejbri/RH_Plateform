package com.hr.evaluation.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Statuts du référentiel : 403 et 404 = compte sans fiche, 500 = indisponible. */
class RestReferentielMoiClientTest {

    private HttpServer serveur;
    private final AtomicInteger statut = new AtomicInteger(200);
    private final UUID fiche = UUID.randomUUID();

    @BeforeEach
    void demarrer() throws Exception {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/api/referentiel/v1/collaborateurs/moi", ech -> {
            int code = statut.get();
            byte[] corps = code == 200
                    ? ("{\"identifiant\":\"" + fiche + "\",\"prenom\":\"Amel\",\"nom\":\"Test\"}")
                            .getBytes(StandardCharsets.UTF_8)
                    : new byte[0];
            ech.getResponseHeaders().add("Content-Type", "application/json");
            ech.sendResponseHeaders(code, corps.length == 0 ? -1 : corps.length);
            if (corps.length > 0) {
                ech.getResponseBody().write(corps);
            }
            ech.close();
        });
        serveur.start();
        Jwt jwt = new Jwt("jeton", Instant.now(), Instant.now().plusSeconds(60),
                java.util.Map.of("alg", "none"), java.util.Map.of("sub", "x"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void arreter() {
        serveur.stop(0);
        SecurityContextHolder.clearContext();
    }

    private RestReferentielMoiClient client() {
        return new RestReferentielMoiClient("http://127.0.0.1:" + serveur.getAddress().getPort());
    }

    @Test
    void ficheTrouvee_200() {
        Optional<MoiReferentiel> moi = client().moi();
        assertThat(moi).isPresent();
        assertThat(moi.get().identifiant()).isEqualTo(fiche);
    }

    @Test
    void compteSansFiche_403_donneVide() {
        statut.set(403);
        assertThat(client().moi()).isEmpty();
    }

    @Test
    void compteSansFiche_404_donneVide() {
        statut.set(404);
        assertThat(client().moi()).isEmpty();
    }

    @Test
    void erreurServeur_500_resteUneIndisponibilite() {
        statut.set(500);
        assertThatThrownBy(() -> client().moi()).isInstanceOf(IllegalStateException.class);
    }
}
