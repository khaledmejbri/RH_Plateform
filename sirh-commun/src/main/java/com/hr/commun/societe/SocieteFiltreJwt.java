package com.hr.commun.societe;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Filtre placé après l'authentification Spring Security :
 * lit le claim {@code societe_id} du {@link JwtAuthenticationToken}, le pose dans {@link SocieteContexte},
 * et l'efface en fin de requête.
 * <ul>
 *   <li>Jeton authentifié sans claim {@code societe_id} → 401 {@code JETON_SANS_SOCIETE}, sauf rôle {@code PLATEFORME} sur {@code /api/plateforme/**}.</li>
 *   <li>Rôle {@code PLATEFORME} hors {@code /api/plateforme/**} → 403 {@code SOCIETE_ABSENTE}.</li>
 * </ul>
 */
public class SocieteFiltreJwt extends OncePerRequestFilter {

	public static final String CLAIM_SOCIETE_ID = "societe_id";
	private static final String PREFIXE_PLATEFORME = "/api/plateforme/";
	private static final String RACINE_PLATEFORME = "/api/plateforme";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		try {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication instanceof JwtAuthenticationToken jwtAuth && authentication.isAuthenticated()) {
				String chemin = extraireChemin(request);
				if (estRolePlateforme(jwtAuth)) {
					if (estRoutePlateforme(chemin)) {
						filterChain.doFilter(request, response);
						return;
					}
					ecrireErreur(response, HttpServletResponse.SC_FORBIDDEN, "SOCIETE_ABSENTE",
							"Le compte plateforme n'a pas accès aux données d'une société.", chemin);
					return;
				}

				String societeClaim = jwtAuth.getToken().getClaimAsString(CLAIM_SOCIETE_ID);
				if (societeClaim == null || societeClaim.isBlank()) {
					ecrireErreur(response, HttpServletResponse.SC_UNAUTHORIZED, "JETON_SANS_SOCIETE",
							"Le jeton d'authentification ne porte pas d'identifiant de société.", chemin);
					return;
				}
				UUID societeId;
				try {
					societeId = UUID.fromString(societeClaim.trim());
				} catch (IllegalArgumentException e) {
					ecrireErreur(response, HttpServletResponse.SC_UNAUTHORIZED, "JETON_SANS_SOCIETE",
							"L'identifiant de société du jeton est invalide.", chemin);
					return;
				}
				if (SocieteContexte.AUCUNE.equals(societeId)) {
					ecrireErreur(response, HttpServletResponse.SC_UNAUTHORIZED, "JETON_SANS_SOCIETE",
							"L'identifiant de société du jeton est invalide.", chemin);
					return;
				}
				SocieteContexte.definir(societeId);
			}
			filterChain.doFilter(request, response);
		} finally {
			SocieteContexte.effacer();
		}
	}

	private static boolean estRolePlateforme(JwtAuthenticationToken jwtAuth) {
		for (GrantedAuthority authority : jwtAuth.getAuthorities()) {
			String a = authority.getAuthority();
			if ("ROLE_PLATEFORME".equals(a) || "PLATEFORME".equals(a)) {
				return true;
			}
		}
		Jwt jwt = jwtAuth.getToken();
		List<String> roles = jwt.getClaimAsStringList("roles");
		return roles != null && roles.stream().anyMatch(r -> "PLATEFORME".equalsIgnoreCase(r));
	}

	private static boolean estRoutePlateforme(String chemin) {
		return RACINE_PLATEFORME.equals(chemin) || chemin.startsWith(PREFIXE_PLATEFORME);
	}

	private static String extraireChemin(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if (uri != null && !uri.isEmpty()) {
			return uri;
		}
		String servletPath = request.getServletPath();
		return servletPath != null ? servletPath : "";
	}

	static void ecrireErreur(HttpServletResponse response, int status, String code, String message, String path)
			throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		String json = "{\"code\":\"" + echapperJson(code)
				+ "\",\"message\":\"" + echapperJson(message)
				+ "\",\"details\":\"" + echapperJson(message)
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
