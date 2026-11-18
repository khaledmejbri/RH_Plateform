package com.hr.identiteacces.service;

import com.hr.identiteacces.domain.StatutSociete;
import com.hr.identiteacces.dto.AuthResponse;
import com.hr.identiteacces.dto.SigninRequest;
import com.hr.identiteacces.dto.UserResponse;
import com.hr.identiteacces.entity.Societe;
import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.repository.SocieteRepository;
import com.hr.identiteacces.repository.UserRepository;
import com.hr.identiteacces.security.ApplicationRoleMatrix;
import com.hr.identiteacces.web.IdentiteMetierException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;

@Service
public class AuthService {

	public static final String CODE_RESERVE_PLATEFORME = "PLATEFORME";
	private static final String MESSAGE_IDENTIFIANTS_INVALIDES = "Identifiant ou mot de passe incorrect.";
	private static final String MESSAGE_SOCIETE_SUSPENDUE =
			"L'accès de votre société est suspendu. Contactez votre administrateur.";

	private final UserRepository userRepository;
	private final SocieteRepository societeRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtEncoder jwtEncoder;
	private final String societeDefautCode;
	private final long accessTtlSeconds;
	private final String issuer;

	@Autowired
	public AuthService(
			UserRepository userRepository,
			SocieteRepository societeRepository,
			PasswordEncoder passwordEncoder,
			JwtEncoder jwtEncoder,
			@Value("${app.societe.defaut.code:${SOCIETE_DEFAUT_CODE:PRINCIPALE}}") String societeDefautCode,
			@Value("${app.jwt.access-ttl-seconds:3600}") long accessTtlSeconds,
			@Value("${spring.security.oauth2.authorizationserver.issuer:http://localhost:8080}") String issuer) {
		this.userRepository = userRepository;
		this.societeRepository = societeRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
		this.societeDefautCode = (societeDefautCode == null || societeDefautCode.isBlank())
				? "PRINCIPALE"
				: societeDefautCode.trim();
		this.accessTtlSeconds = accessTtlSeconds > 0 ? accessTtlSeconds : 3600L;
		this.issuer = (issuer == null || issuer.isBlank()) ? "http://localhost:8080" : issuer;
	}

	@Transactional(readOnly = true)
	public AuthResponse signin(SigninRequest request) {
		if (request == null || request.getUsername() == null || request.getUsername().isBlank()
				|| request.getPassword() == null || request.getPassword().isBlank()) {
			throw IdentiteMetierException.unauthorized("IDENTIFIANTS_INVALIDES", MESSAGE_IDENTIFIANTS_INVALIDES);
		}

		String identifiant = request.getUsername().trim();
		String codeSociete = (request.getSocieteCode() == null || request.getSocieteCode().isBlank())
				? societeDefautCode
				: request.getSocieteCode().trim();

		if (CODE_RESERVE_PLATEFORME.equalsIgnoreCase(codeSociete)) {
			User userPlateforme = userRepository.findPlateformeByIdentifiantIgnoreCase(identifiant)
					.orElseThrow(() -> IdentiteMetierException.unauthorized(
							"IDENTIFIANTS_INVALIDES", MESSAGE_IDENTIFIANTS_INVALIDES));
			if (userPlateforme.getRoles() == null || !userPlateforme.getRoles().contains("PLATEFORME")
					|| !passwordEncoder.matches(request.getPassword(), userPlateforme.getPassword())) {
				throw IdentiteMetierException.unauthorized("IDENTIFIANTS_INVALIDES", MESSAGE_IDENTIFIANTS_INVALIDES);
			}
			return construireReponse(userPlateforme, null);
		}

		Societe societe = societeRepository.findByCodeIgnoreCase(codeSociete)
				.orElseThrow(() -> IdentiteMetierException.unauthorized(
						"IDENTIFIANTS_INVALIDES", MESSAGE_IDENTIFIANTS_INVALIDES));

		if (societe.getStatut() == StatutSociete.SUSPENDUE) {
			throw IdentiteMetierException.unauthorized("SOCIETE_SUSPENDUE", MESSAGE_SOCIETE_SUSPENDUE);
		}

		User user = userRepository.findBySocieteIdAndIdentifiantIgnoreCase(societe.getId(), identifiant)
				.orElseThrow(() -> IdentiteMetierException.unauthorized(
						"IDENTIFIANTS_INVALIDES", MESSAGE_IDENTIFIANTS_INVALIDES));

		if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
			throw IdentiteMetierException.unauthorized("IDENTIFIANTS_INVALIDES", MESSAGE_IDENTIFIANTS_INVALIDES);
		}

		return construireReponse(user, societe);
	}

	private AuthResponse construireReponse(User user, Societe societe) {
		Set<String> roleSet = ApplicationRoleMatrix.expandWithImplicitUser(
				user.getRoles() != null && !user.getRoles().isEmpty()
						? user.getRoles()
						: Set.of("USER"));

		String scope = String.join(" ", user.getAuthoritiesAsStrings());
		Instant now = Instant.now();

		JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
				.issuer(issuer)
				.subject(user.getUsername())
				.claim("scope", scope)
				.claim("roles", new ArrayList<>(roleSet))
				.claim("email", user.getEmail())
				.claim("identifiant_utilisateur", user.getId().toString())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(accessTtlSeconds));

		if (societe != null) {
			claimsBuilder.claim("societe_id", societe.getId().toString());
			claimsBuilder.claim("societe_code", societe.getCode());
		}

		String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claimsBuilder.build())).getTokenValue();

		return AuthResponse.builder()
				.accessToken(accessToken)
				.refreshToken(null)
				.tokenType("Bearer")
				.expiresIn(accessTtlSeconds)
				.user(toUserResponse(user))
				.build();
	}

	private static UserResponse toUserResponse(User user) {
		return UserResponse.builder()
				.id(user.getId())
				.username(user.getUsername())
				.email(user.getEmail())
				.roles(user.getRoles())
				.createdAt(user.getCreatedAt())
				.build();
	}
}
