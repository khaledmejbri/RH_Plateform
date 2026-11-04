package com.hr.evaluation.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code GET /internal/users/by-role?role=...} avec le jeton interne (non routé par la gateway).
 * Délais : connexion 1 s, lecture 2 s.
 */
@Component
public class RestIdentiteClient implements IdentiteClient {

	private final RestClient restClient;
	private final String jetonInterne;

	public RestIdentiteClient(
			@Value("${evaluation.identite.base-url:http://localhost:8081}") String baseUrl,
			@Value("${app.internal-api.token:change-me-internal-token}") String jetonInterne) {
		JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
		fabrique.setReadTimeout(Duration.ofSeconds(2));
		this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(fabrique).build();
		this.jetonInterne = jetonInterne;
	}

	@Override
	public List<UUID> comptesParRole(String role) {
		Dto[] corps = restClient.get()
				.uri(b -> b.path("/internal/users/by-role").queryParam("role", role).build())
				.header("X-Internal-Token", jetonInterne)
				.retrieve()
				.body(Dto[].class);
		if (corps == null) {
			return List.of();
		}
		return Arrays.stream(corps).map(d -> d.identifiant).filter(Objects::nonNull).distinct().toList();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class Dto {
		@JsonProperty("identifiant")
		UUID identifiant;
	}
}
