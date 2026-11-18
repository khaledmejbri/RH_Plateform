package com.hr.commun.societe;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SocieteFiltreJwtTest {

	private final SocieteFiltreJwt filtre = new SocieteFiltreJwt();

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		SocieteContexte.effacer();
	}

	@Test
	@DisplayName("Jeton avec claim societe_id valide pose la société dans SocieteContexte et l'efface en fin de requête")
	void jetonAvecClaim_poseEtEffaceSociete() throws Exception {
		UUID societeId = UUID.randomUUID();
		Jwt jwt = new Jwt("tok", Instant.now(), Instant.now().plusSeconds(3600),
				Map.of("alg", "RS256"),
				Map.of("sub", "COL-WEB", "societe_id", societeId.toString(), "roles", List.of("USER")));
		SecurityContextHolder.getContext().setAuthentication(
				new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

		MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/rh/v1/collaborateurs");
		MockHttpServletResponse res = new MockHttpServletResponse();
		AtomicReference<UUID> dansChaine = new AtomicReference<>();
		FilterChain chain = (r, s) -> dansChaine.set(SocieteContexte.courante());

		filtre.doFilter(req, res, chain);

		assertThat(res.getStatus()).isEqualTo(200);
		assertThat(dansChaine.get()).isEqualTo(societeId);
		assertThat(SocieteContexte.couranteOuVide()).isEmpty();
	}

	@Test
	@DisplayName("CA-01.5 : jeton authentifié sans claim societe_id → 401 JETON_SANS_SOCIETE")
	void jetonSansClaim_renvoie401() throws Exception {
		Jwt jwt = new Jwt("tok", Instant.now(), Instant.now().plusSeconds(3600),
				Map.of("alg", "RS256"),
				Map.of("sub", "COL-WEB", "roles", List.of("USER")));
		SecurityContextHolder.getContext().setAuthentication(
				new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

		MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/rh/v1/collaborateurs");
		MockHttpServletResponse res = new MockHttpServletResponse();
		AtomicReference<Boolean> appele = new AtomicReference<>(false);

		filtre.doFilter(req, res, (r, s) -> appele.set(true));

		assertThat(appele.get()).isFalse();
		assertThat(res.getStatus()).isEqualTo(401);
		assertThat(res.getContentAsString()).contains("JETON_SANS_SOCIETE");
	}

	@Test
	@DisplayName("Rôle PLATEFORME sur /api/plateforme/** passe sans claim societe_id")
	void rolePlateformeSurApiPlateforme_passeSansSociete() throws Exception {
		Jwt jwt = new Jwt("tok", Instant.now(), Instant.now().plusSeconds(3600),
				Map.of("alg", "RS256"),
				Map.of("sub", "plat-admin", "roles", List.of("PLATEFORME")));
		SecurityContextHolder.getContext().setAuthentication(
				new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_PLATEFORME"))));

		MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/plateforme/v1/societes");
		MockHttpServletResponse res = new MockHttpServletResponse();
		AtomicReference<Boolean> appele = new AtomicReference<>(false);

		filtre.doFilter(req, res, (r, s) -> appele.set(true));

		assertThat(appele.get()).isTrue();
		assertThat(res.getStatus()).isEqualTo(200);
		assertThat(SocieteContexte.couranteOuVide()).isEmpty();
	}

	@Test
	@DisplayName("CA-03.4 : rôle PLATEFORME hors /api/plateforme/** → 403 SOCIETE_ABSENTE")
	void rolePlateformeSurRouteRh_renvoie403() throws Exception {
		Jwt jwt = new Jwt("tok", Instant.now(), Instant.now().plusSeconds(3600),
				Map.of("alg", "RS256"),
				Map.of("sub", "plat-admin", "roles", List.of("PLATEFORME")));
		SecurityContextHolder.getContext().setAuthentication(
				new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_PLATEFORME"))));

		MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/rh/v1/collaborateurs");
		MockHttpServletResponse res = new MockHttpServletResponse();
		AtomicReference<Boolean> appele = new AtomicReference<>(false);

		filtre.doFilter(req, res, (r, s) -> appele.set(true));

		assertThat(appele.get()).isFalse();
		assertThat(res.getStatus()).isEqualTo(403);
		assertThat(res.getContentAsString()).contains("SOCIETE_ABSENTE");
	}
}
