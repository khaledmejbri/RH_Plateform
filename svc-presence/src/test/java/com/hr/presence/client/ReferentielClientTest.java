package com.hr.presence.client;

import com.hr.presence.config.PresenceProperties;
import com.hr.presence.web.PresenceMetierException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les pannes du référentiel deviennent un 503 REFERENTIEL_INDISPONIBLE ; l'absence de fiche reste un 403. */
class ReferentielClientTest {

	private HttpServer serveur;
	private int statut;
	private String corps = "";

	@BeforeEach
	void setUp() throws Exception {
		serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		serveur.createContext("/", echange -> {
			byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
			echange.getResponseHeaders().add("Content-Type", "application/json");
			echange.sendResponseHeaders(statut, octets.length == 0 ? -1 : octets.length);
			if (octets.length > 0) {
				echange.getResponseBody().write(octets);
			}
			echange.close();
		});
		serveur.start();
		Jwt jwt = Jwt.withTokenValue("jeton-de-test").header("alg", "none").subject("COL-WEB").build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

	@AfterEach
	void tearDown() {
		serveur.stop(0);
		SecurityContextHolder.clearContext();
	}

	private ReferentielClient client(String baseUrl) {
		PresenceProperties props = new PresenceProperties();
		props.getReferentiel().setBaseUrl(baseUrl);
		return new ReferentielClient(props, "jeton-interne-de-test");
	}

	private ReferentielClient client() {
		return client("http://127.0.0.1:" + serveur.getAddress().getPort());
	}

	@Test
	void reponse200_ficheEtValideurLus() {
		statut = 200;
		UUID fiche = UUID.randomUUID();
		corps = "{\"collaborateur_id\":\"" + fiche + "\",\"collaborateur_nom\":\"Sarra Khemiri\"}";

		assertThat(client().monValideurPointage().collaborateurId()).isEqualTo(fiche);
	}

	@Test
	void referentielEnErreur500_503_referentielIndisponible() {
		statut = 500;

		assertThatThrownBy(() -> client().monValideurPointage()).isInstanceOfSatisfying(PresenceMetierException.class, e -> {
			assertThat(e.getCode()).isEqualTo("REFERENTIEL_INDISPONIBLE");
			assertThat(e.getHttpStatus()).isEqualTo(503);
		});
	}

	@Test
	void referentielInjoignable_503() {
		int portFerme;
		try (java.net.ServerSocket libre = new java.net.ServerSocket(0)) {
			portFerme = libre.getLocalPort();
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}

		assertThatThrownBy(() -> client("http://127.0.0.1:" + portFerme).monValideurPointage())
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(503));
		assertThatThrownBy(() -> client("http://127.0.0.1:" + portFerme).resoudreCollaborateurs(java.util.List.of(UUID.randomUUID())))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(503));
	}

	@Test
	void ficheAbsente404_403_pasUnePanne() {
		statut = 404;

		assertThatThrownBy(() -> client().monValideurPointage()).isInstanceOf(SecurityException.class);
	}
}
