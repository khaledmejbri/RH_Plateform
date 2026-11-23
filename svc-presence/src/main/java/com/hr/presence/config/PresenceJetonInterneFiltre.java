package com.hr.presence.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Filtre technique d'authentification inter-services sur {@code /internal/presence/v1/workflow/**}.
 * Ne doit pas être annoté {@code @Component} pour éviter un double enregistrement hors chaîne de sécurité.
 */
public class PresenceJetonInterneFiltre extends OncePerRequestFilter {

	public static final String ENTETE_JETON = "X-Internal-Token";
	private static final String PREFIXE_WORKFLOW_INTERNE = "/internal/presence/v1/workflow/";

	private final String jetonAttendu;

	public PresenceJetonInterneFiltre(String jetonAttendu) {
		this.jetonAttendu = jetonAttendu == null ? "" : jetonAttendu;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String contextPath = request.getContextPath();
		String chemin = (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
				? uri.substring(contextPath.length())
				: uri;
		return !chemin.startsWith(PREFIXE_WORKFLOW_INTERNE);
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String jetonRecu = request.getHeader(ENTETE_JETON);
		if (jetonAttendu.isBlank() || jetonRecu == null || !correspondent(jetonAttendu, jetonRecu)) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType("application/json;charset=UTF-8");
			response.getWriter().write("{\"code\":\"JETON_INTERNE_INVALIDE\",\"message\":\"Jeton interne invalide\"}");
			return;
		}
		var auth = new UsernamePasswordAuthenticationToken(
				"workflow-interne",
				null,
				List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
		SecurityContextHolder.getContext().setAuthentication(auth);
		filterChain.doFilter(request, response);
	}

	private static boolean correspondent(String attendu, String recu) {
		return MessageDigest.isEqual(
				attendu.getBytes(StandardCharsets.UTF_8),
				recu.getBytes(StandardCharsets.UTF_8));
	}
}
