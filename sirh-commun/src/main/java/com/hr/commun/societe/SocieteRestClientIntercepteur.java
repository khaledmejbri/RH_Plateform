package com.hr.commun.societe;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Intercepteur HTTP qui propage la société courante ({@code X-Societe-Id}) sur tout appel {@code /internal/**}.
 */
public class SocieteRestClientIntercepteur implements ClientHttpRequestInterceptor {

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		String path = request.getURI().getPath();
		if (path != null && ("/internal".equals(path) || path.startsWith("/internal/"))) {
			SocieteContexte.couranteOuVide()
					.filter(id -> !SocieteContexte.AUCUNE.equals(id))
					.ifPresent(id -> request.getHeaders().set(SocieteFiltreInterne.ENTETE_SOCIETE_ID, id.toString()));
		}
		return execution.execute(request, body);
	}
}
