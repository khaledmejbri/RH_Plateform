package com.hr.commun.societe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SocieteRestClientIntercepteurTest {

	private final SocieteRestClientIntercepteur intercepteur = new SocieteRestClientIntercepteur();

	@AfterEach
	void tearDown() {
		SocieteContexte.effacer();
	}

	@Test
	@DisplayName("Ajoute X-Societe-Id sur /internal/** uniquement lorsque SocieteContexte est renseigné")
	void intercepteAppelsInternal() throws Exception {
		UUID societeId = UUID.randomUUID();
		SocieteContexte.definir(societeId);

		HttpHeaders headersInterne = new HttpHeaders();
		HttpRequest reqInterne = creerRequete(URI.create("http://localhost:8083/internal/referentiel/v1/test"), headersInterne);
		ClientHttpRequestExecution exec = (req, body) -> mock(ClientHttpResponse.class);

		intercepteur.intercept(reqInterne, new byte[0], exec);
		assertThat(headersInterne.getFirst("X-Societe-Id")).isEqualTo(societeId.toString());

		HttpHeaders headersPublic = new HttpHeaders();
		HttpRequest reqPublic = creerRequete(URI.create("http://localhost:8083/api/rh/v1/test"), headersPublic);
		intercepteur.intercept(reqPublic, new byte[0], exec);
		assertThat(headersPublic.getFirst("X-Societe-Id")).isNull();
	}

	private static HttpRequest creerRequete(URI uri, HttpHeaders headers) {
		return new HttpRequest() {
			@Override
			public HttpMethod getMethod() {
				return HttpMethod.GET;
			}

			@Override
			public URI getURI() {
				return uri;
			}

			@Override
			public Map<String, Object> getAttributes() {
				return Map.of();
			}

			@Override
			public HttpHeaders getHeaders() {
				return headers;
			}
		};
	}
}
