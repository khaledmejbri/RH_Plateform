package com.hr.workflow.config;

import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocieteFiltreInterne;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

/**
 * Filtre de sécurité sur {@code /internal/**} (D-W3-02) :
 * vérifie {@code X-Internal-Token} en temps constant ; lie {@code X-Societe-Id} optionnel dans {@link SocieteContexte}.
 * Absent ou invalide → 403 {@code JETON_INTERNE_INVALIDE} ; {@code X-Societe-Id} mal formé → 400 {@code SOCIETE_ABSENTE}.
 */
public class JetonInterneFiltre extends OncePerRequestFilter {

	public static final String ENTETE_INTERNAL_TOKEN = SocieteFiltreInterne.ENTETE_INTERNAL_TOKEN;
	private static final String PREFIXE_INTERNAL = "/internal/";
	private static final String RACINE_INTERNAL = "/internal";

	private final String internalApiToken;

	public JetonInterneFiltre(String internalApiToken) {
		this.internalApiToken = internalApiToken;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String chemin = extraireChemin(request);
		return !(RACINE_INTERNAL.equals(chemin) || chemin.startsWith(PREFIXE_INTERNAL));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String chemin = extraireChemin(request);
		String token = request.getHeader(ENTETE_INTERNAL_TOKEN);
		if (!jetonValide(token)) {
			ecrireErreur(response, HttpServletResponse.SC_FORBIDDEN, "JETON_INTERNE_INVALIDE",
					"Jeton interne absent ou invalide.", chemin);
			return;
		}
		String societeHeader = request.getHeader(SocieteFiltreInterne.ENTETE_SOCIETE_ID);
		if (societeHeader == null || societeHeader.isBlank()) {
			filterChain.doFilter(request, response);
			return;
		}
		UUID societeId = parserUuidValide(societeHeader);
		if (societeId == null) {
			ecrireErreur(response, HttpServletResponse.SC_BAD_REQUEST, "SOCIETE_ABSENTE",
					"L'en-tête X-Societe-Id est mal formé.", chemin);
			return;
		}
		try {
			SocieteContexte.definir(societeId);
			filterChain.doFilter(request, response);
		} finally {
			SocieteContexte.effacer();
		}
	}

	private static UUID parserUuidValide(String raw) {
		try {
			UUID id = UUID.fromString(raw.trim());
			return SocieteContexte.AUCUNE.equals(id) ? null : id;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private boolean jetonValide(String token) {
		return token != null && !token.isBlank()
				&& internalApiToken != null && !internalApiToken.isBlank()
				&& MessageDigest.isEqual(
						token.getBytes(StandardCharsets.UTF_8),
						internalApiToken.getBytes(StandardCharsets.UTF_8));
	}

	private static String extraireChemin(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if (uri != null && !uri.isEmpty()) {
			return uri;
		}
		String servletPath = request.getServletPath();
		return servletPath != null ? servletPath : "";
	}

	private static void ecrireErreur(HttpServletResponse response, int status, String code, String message, String path)
			throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		String msgEchappe = echapperJson(message);
		String json = "{\"code\":\"" + echapperJson(code)
				+ "\",\"message\":\"" + msgEchappe
				+ "\",\"erreur\":\"" + msgEchappe
				+ "\",\"details\":\"" + msgEchappe
				+ "\",\"timestamp\":\"" + Instant.now()
				+ "\",\"path\":\"" + echapperJson(path != null ? path : "")
				+ "\",\"http_status\":" + status + "}";
		response.getWriter().write(json);
	}

	private static String echapperJson(String valeur) {
		if (valeur == null) {
			return "";
		}
		return valeur.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
