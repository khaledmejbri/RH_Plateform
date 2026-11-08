package com.hr.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API REST protégée par le JWT de svc-identite-acces (signature vérifiée par JWKS). Le handshake WebSocket est
 * ouvert : l'authentification se fait à la trame STOMP CONNECT ({@link StompAuthInterceptor}).
 */
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
						.requestMatchers("/ws", "/ws/**", "/ws-sockjs", "/ws-sockjs/**").permitAll()
						.requestMatchers("/error").permitAll()
						.requestMatchers("/api/rh/v1/notifications/**").authenticated()
						.anyRequest().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> { }));
		return http.build();
	}
}
