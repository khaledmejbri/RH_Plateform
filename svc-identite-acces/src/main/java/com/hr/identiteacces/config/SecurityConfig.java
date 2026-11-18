package com.hr.identiteacces.config;

import com.hr.commun.societe.SocieteFiltreInterne;
import com.hr.commun.societe.SocieteFiltreJwt;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

	@Value("${spring.security.oauth2.authorizationserver.issuer:http://localhost:8080}")
	private String issuer;

	@Value("${app.internal-api.token}")
	private String internalApiToken;

	@Bean
	@Order(1)
	public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
		OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
				new OAuth2AuthorizationServerConfigurer();

		http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
				.with(authorizationServerConfigurer, cfg -> cfg.oidc(Customizer.withDefaults()))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/oauth2/jwks").permitAll()
						.requestMatchers("/.well-known/oauth-authorization-server", "/.well-known/oauth-authorization-server/**")
						.permitAll()
						.requestMatchers("/.well-known/openid-configuration", "/.well-known/openid-configuration/**")
						.permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
						new LoginUrlAuthenticationEntryPoint("/login"),
						new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

		return http.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/auth/**").permitAll()
						.requestMatchers("/internal/**").permitAll()
						.requestMatchers("/oauth2/jwks").permitAll()
						.requestMatchers("/.well-known/**").permitAll()
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf.disable())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
				.addFilterBefore(new SocieteFiltreInterne(internalApiToken), BearerTokenAuthenticationFilter.class)
				.addFilterAfter(new SocieteFiltreJwt(), BearerTokenAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(new IdentiteJwtAuthoritiesConverter());
		return converter;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	public RegisteredClientRepository registeredClientRepository() {
		RegisteredClient oidcClient = RegisteredClient.withId(UUID.randomUUID().toString())
				.clientId("hr-client")
				.clientSecret("{noop}hr-secret")
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
				.redirectUri("http://127.0.0.1:8082/login/oauth2/code/hr-client")
				.redirectUri("http://localhost:8082/login/oauth2/code/hr-client")
				.postLogoutRedirectUri("http://127.0.0.1:8082/")
				.scope(OidcScopes.OPENID)
				.scope(OidcScopes.PROFILE)
				.scope("read")
				.scope("write")
				.clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
				.build();

		return new InMemoryRegisteredClientRepository(oidcClient);
	}

	@Bean
	public JWKSource<SecurityContext> jwkSource() {
		KeyPair keyPair = generateRsaKey();
		RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
		RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
		RSAKey rsaKey = new RSAKey.Builder(publicKey)
				.privateKey(privateKey)
				.keyID(UUID.randomUUID().toString())
				.build();
		JWKSet jwkSet = new JWKSet(rsaKey);
		return new ImmutableJWKSet<>(jwkSet);
	}

	private static KeyPair generateRsaKey() {
		try {
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
			keyPairGenerator.initialize(2048);
			return keyPairGenerator.generateKeyPair();
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Bean
	public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
		return NimbusJwtDecoder.withJwkSource(jwkSource).build();
	}

	@Bean
	public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
		return new NimbusJwtEncoder(jwkSource);
	}

	@Bean
	public AuthorizationServerSettings authorizationServerSettings() {
		return AuthorizationServerSettings.builder()
				.issuer(issuer)
				.build();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	private static final class IdentiteJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
		@Override
		public Collection<GrantedAuthority> convert(Jwt jwt) {
			Set<GrantedAuthority> out = new LinkedHashSet<>();
			addFromDelimitedString(jwt.getClaimAsString("scope"), out);
			addFromStringList(jwt.getClaimAsStringList("scope"), out);
			addFromStringList(jwt.getClaimAsStringList("scp"), out);
			addFromStringList(jwt.getClaimAsStringList("roles"), out);
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

		private static void addAuthority(Set<GrantedAuthority> out, String value) {
			String authority = value.startsWith("ROLE_") ? value : "ROLE_" + value;
			out.add(new SimpleGrantedAuthority(authority));
		}
	}
}
