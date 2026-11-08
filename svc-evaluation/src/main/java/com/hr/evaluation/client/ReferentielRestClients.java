package com.hr.evaluation.client;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Fabrique des {@link RestClient} de service à service : tous reçoivent un délai de connexion et un délai de lecture
 * (propriétés {@code evaluation.referentiel.timeout-*}), pour qu'un référentiel lent ne bloque jamais une requête
 * (ADR lot 2 § 1). Les durées viennent de la configuration, jamais du code.
 */
public final class ReferentielRestClients {

	private ReferentielRestClients() {
	}

	public static RestClient creer(String baseUrl, int delaiConnexionMs, int delaiLectureMs) {
		JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofMillis(delaiConnexionMs)).build());
		fabrique.setReadTimeout(Duration.ofMillis(delaiLectureMs));
		return RestClient.builder().baseUrl(baseUrl).requestFactory(fabrique).build();
	}
}
