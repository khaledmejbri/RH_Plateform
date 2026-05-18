package com.hr.evaluation.config;

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
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers("/api/rh/v1/evaluations/**").authenticated()
						.requestMatchers("/api/rh/v1/admin/evaluations/**").authenticated()
						.anyRequest().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
		return http.build();
	}

	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(new EvaluationJwtAuthoritiesConverter());
		return converter;
	}

	private static final class EvaluationJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
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
