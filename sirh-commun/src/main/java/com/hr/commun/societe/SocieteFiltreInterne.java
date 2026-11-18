package com.hr.commun.societe;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Filtre pour les routes {@code /internal/**} :
 * <ul>
 *   <li>Exige un en-tête {@code X-Internal-Token} valide (comparaison à temps constant), sinon 403.</li>
 *   <li>Exige ensuite l'en-tête {@code X-Societe-Id} (UUID valide) et le pose dans {@link SocieteContexte},
 *       sauf sur la liste blanche {@code /internal/identite/v1/societes/**}.</li>
 *   <li>{@code X-Societe-Id} absent ou mal formé → 400 {@code SOCIETE_ABSENTE}.</li>
 * </ul>
 */
public class SocieteFiltreInterne extends OncePerRequestFilter {

	public static final String ENTETE_INTERNAL_TOKEN = "X-Internal-Token";
	public static final String ENTETE_SOCIETE_ID = "X-Societe-Id";

	private static final String PREFIXE_INTERNAL = "/internal/";
	private static final String RACINE_INTERNAL = "/internal";
	private static final String PREFIXE_LISTE_BLANCHE_SOCIETES = "/internal/identite/v1/societes/";
	private static final String RACINE_LISTE_BLANCHE_SOCIETES = "/internal/identite/v1/societes";

	private final String internalApiToken;

	public SocieteFiltreInterne(String internalApiToken) {
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
			SocieteFiltreJwt.ecrireErreur(response, HttpServletResponse.SC_FORBIDDEN, "JETON_INTERNE_INVALIDE",
					"Jeton interne absent ou invalide.", chemin);
			return;
		}

		if (estDansListeBlancheSocietes(chemin)) {
			try {
				String societeHeader = request.getHeader(ENTETE_SOCIETE_ID);
				if (societeHeader != null && !societeHeader.isBlank()) {
					UUID societeId = parserUuidValide(societeHeader);
					if (societeId == null) {
						SocieteFiltreJwt.ecrireErreur(response, HttpServletResponse.SC_BAD_REQUEST, "SOCIETE_ABSENTE",
								"L'en-tête X-Societe-Id est mal formé.", chemin);
						return;
					}
					SocieteContexte.definir(societeId);
				}
				filterChain.doFilter(request, response);
			} finally {
				SocieteContexte.effacer();
			}
			return;
		}

		String societeHeader = request.getHeader(ENTETE_SOCIETE_ID);
		UUID societeId = parserUuidValide(societeHeader);
		if (societeId == null) {
			SocieteFiltreJwt.ecrireErreur(response, HttpServletResponse.SC_BAD_REQUEST, "SOCIETE_ABSENTE",
					"L'en-tête X-Societe-Id est obligatoire et doit être un UUID valide.", chemin);
			return;
		}

		try {
			SocieteContexte.definir(societeId);
			filterChain.doFilter(request, response);
		} finally {
			SocieteContexte.effacer();
		}
	}

	private boolean jetonValide(String token) {
		return token != null && !token.isBlank()
				&& internalApiToken != null && !internalApiToken.isBlank()
				&& MessageDigest.isEqual(
						token.getBytes(StandardCharsets.UTF_8),
						internalApiToken.getBytes(StandardCharsets.UTF_8));
	}

	private static boolean estDansListeBlancheSocietes(String chemin) {
		return RACINE_LISTE_BLANCHE_SOCIETES.equals(chemin) || chemin.startsWith(PREFIXE_LISTE_BLANCHE_SOCIETES);
	}

	private static UUID parserUuidValide(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			UUID id = UUID.fromString(raw.trim());
			return SocieteContexte.AUCUNE.equals(id) ? null : id;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static String extraireChemin(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if (uri != null && !uri.isEmpty()) {
			return uri;
		}
		String servletPath = request.getServletPath();
		return servletPath != null ? servletPath : "";
	}
}
