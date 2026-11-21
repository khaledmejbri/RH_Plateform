package com.hr.workflow.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Sécurité HTTP de {@code svc-workflow} : JWT par JWKS (comme {@code svc-presence}) et
 * {@link JetonInterneFiltre} sur {@code /internal/**} (D-W3-02).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

	@Value("${app.internal-api.token:change-me-internal-token}")
	private String internalApiToken;

	@Bean
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ObjectProvider<DemarrageOrchestrateur> demarrageOrchestrateurProvider) throws Exception {
		JetonInterneFiltre jetonInterneFiltre = new JetonInterneFiltre(internalApiToken);

		http
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers("/internal/workflow/**").permitAll()
						.requestMatchers("/api/rh/v1/workflow/**").authenticated()
						.requestMatchers("/api/rh/v1/admin/workflow/**").authenticated()
						.anyRequest().denyAll())
				.addFilterBefore(jetonInterneFiltre, BearerTokenAuthenticationFilter.class)
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

		DemarrageOrchestrateur orchestrateur = demarrageOrchestrateurProvider.getIfAvailable();
		if (orchestrateur != null) {
			http.addFilterBefore(orchestrateur.filtreDisponibiliteHttp(), JetonInterneFiltre.class);
		}
		return http.build();
	}

	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(new WorkflowJwtAuthoritiesConverter());
		return converter;
	}

	private static final class WorkflowJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
		@Override
		public Collection<GrantedAuthority> convert(Jwt jwt) {
			Set<GrantedAuthority> out = new LinkedHashSet<>();
			addFromDelimitedString(jwt.getClaimAsString("scope"), out);
			addFromStringList(jwt.getClaimAsStringList("scope"), out);
			addFromStringList(jwt.getClaimAsStringList("scp"), out);
			addRolesClaim(jwt, out);
			return new ArrayList<>(out);
		}

		private static void addFromDelimitedString(String raw, Set<GrantedAuthority> out) {
			if (raw == null || raw.isBlank()) {
				return;
			}
			for (String part : raw.split("\\s+")) {
				if (!part.isBlank()) {
					addAuthority(out, part.trim());
				}
			}
		}

		private static void addFromStringList(List<String> list, Set<GrantedAuthority> out) {
			if (list == null) {
				return;
			}
			for (String s : list) {
				if (s != null && !s.isBlank()) {
					addAuthority(out, s.trim());
				}
			}
		}

		private static void addRolesClaim(Jwt jwt, Set<GrantedAuthority> out) {
			List<String> roles = jwt.getClaimAsStringList("roles");
			if (roles == null) {
				return;
			}
			for (String r : roles) {
				if (r != null && !r.isBlank()) {
					addAuthority(out, r.trim());
				}
			}
		}

		private static void addAuthority(Set<GrantedAuthority> out, String value) {
			String authority = value.startsWith("ROLE_") ? value : "ROLE_" + value;
			out.add(new SimpleGrantedAuthority(authority));
		}
	}
}
