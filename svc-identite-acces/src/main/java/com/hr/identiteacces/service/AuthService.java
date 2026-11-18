package com.hr.identiteacces.service;

import com.hr.identiteacces.config.SocieteProprietes;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthService {

	public static final String CODE_RESERVE_PLATEFORME = "PLATEFORME";
	private static final String MESSAGE_IDENTIFIANTS_INVALIDES = "Identifiant ou mot de passe incorrect.";
	private static final String MESSAGE_SOCIETE_SUSPENDUE =
			"L'accès de votre société est suspendu. Contactez votre administrateur.";
	private static final String HASH_FACTICE_REPLI =
			"$2a$12$000000000000000000000u0000000000000000000000000000000";

	private final UserRepository userRepository;
	private final SocieteRepository societeRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtEncoder jwtEncoder;
	private final String societeDefautCode;
	private final long accessTtlSeconds;
	private final String issuer;
	private final String hashFactice;

	@Autowired
	public AuthService(
			UserRepository userRepository,
			SocieteRepository societeRepository,
			PasswordEncoder passwordEncoder,
			JwtEncoder jwtEncoder,
			@Value("${app.societe.defaut.code:${SOCIETE_DEFAUT_CODE:" + SocieteProprietes.CODE_DEFAUT_INITIAL + "}}") String societeDefautCode,
			@Value("${app.jwt.access-ttl-seconds:3600}") long accessTtlSeconds,
			@Value("${spring.security.oauth2.authorizationserver.issuer:http://localhost:8080}") String issuer) {
		this.userRepository = userRepository;
		this.societeRepository = societeRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
		this.societeDefautCode = (societeDefautCode == null || societeDefautCode.isBlank())
				? SocieteProprietes.CODE_DEFAUT_INITIAL
				: societeDefautCode.trim();
		this.accessTtlSeconds = accessTtlSeconds > 0 ? accessTtlSeconds : 3600L;
		this.issuer = (issuer == null || issuer.isBlank()) ? "http://localhost:8080" : issuer;
		String encodeInitial = passwordEncoder != null ? passwordEncoder.encode("timing-guard-startup") : null;
		this.hashFactice = (encodeInitial != null && !encodeInitial.isBlank())
				? encodeInitial
				: HASH_FACTICE_REPLI;
	}

	@Transactional(readOnly = true)
	public AuthResponse signin(SigninRequest request) {
		if (request == null || request.getUsername() == null || request.getUsername().isBlank()
				|| request.getPassword() == null || request.getPassword().isBlank()) {
			passwordEncoder.matches("", hashFactice);
			throw IdentiteMetierException.unauthorized("IDENTIFIANTS_INVALIDES", MESSAGE_IDENTIFIANTS_INVALIDES);
		}

		String identifiant = request.getUsername().trim();
		String motDePasse = request.getPassword();
		String codeSociete = (request.getSocieteCode() == null || request.getSocieteCode().isBlank())
				? societeDefautCode
				: request.getSocieteCode().trim();

		if (CODE_RESERVE_PLATEFORME.equalsIgnoreCase(codeSociete)) {
			Optional<User> optPlateforme = userRepository.findPlateformeByIdentifiantIgnoreCase(identifiant);
			if (optPlateforme.isEmpty()) {
				passwordEncoder.matches(motDePasse, hashFactice);
				throw IdentiteMetierException.unauthorized("IDENTIFIANTS_INVALIDES", MESSAGE_IDENTIFIANTS_INVALIDES);
			}
			User userPlateforme = optPlateforme.get();
			boolean mdpOk = passwordEncoder.matches(motDePasse, userPlateforme.getPassword());
			if (!mdpOk || userPlateforme.getRoles() == null || !userPlateforme.getRoles().contains("PLATEFORME")) {
				throw IdentiteMetierException.unauthorized("IDENTIFIANTS_INVALIDES", MESSAGE_IDENTIFIANTS_INVALIDES);
			}
			return construireReponse(userPlateforme, null);
		}

		Optional<Societe> optSociete = societeRepository.findByCodeIgnoreCase(codeSociete);
		if (optSociete.isEmpty()) {
			passwordEncoder.matches(motDePasse, hashFactice);
			throw IdentiteMetierException.unauthorized("IDENTIFIANTS_INVALIDES", MESSAGE_IDENTIFIANTS_INVALIDES);
		}

		Societe societe = optSociete.get();
		Optional<User> optUser = userRepository.findBySocieteIdAndIdentifiantIgnoreCase(societe.getId(), identifiant);
		if (optUser.isEmpty()) {
			passwordEncoder.matches(motDePasse, hashFactice);
			throw IdentiteMetierException.unauthorized("IDENTIFIANTS_INVALIDES", MESSAGE_IDENTIFIANTS_INVALIDES);
		}

		User user = optUser.get();
		if (!passwordEncoder.matches(motDePasse, user.getPassword())) {
			throw IdentiteMetierException.unauthorized("IDENTIFIANTS_INVALIDES", MESSAGE_IDENTIFIANTS_INVALIDES);
		}

		if (societe.getStatut() == StatutSociete.SUSPENDUE) {
			throw IdentiteMetierException.unauthorized("SOCIETE_SUSPENDUE", MESSAGE_SOCIETE_SUSPENDUE);
		}

		return construireReponse(user, societe);
	}

	private AuthResponse construireReponse(User user, Societe societe) {
		List<String> rolesJeton;
		String scope;
		if (societe == null) {
			rolesJeton = List.of("PLATEFORME");
			scope = "ROLE_PLATEFORME";
		} else {
			Set<String> roleSet = ApplicationRoleMatrix.expandWithImplicitUser(
					user.getRoles() != null && !user.getRoles().isEmpty()
							? user.getRoles()
							: Set.of("USER"));
			rolesJeton = new ArrayList<>(roleSet);
			scope = String.join(" ", user.getAuthoritiesAsStrings());
		}

		Instant now = Instant.now();
		JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
				.issuer(issuer)
				.subject(user.getUsername())
				.claim("scope", scope)
				.claim("roles", rolesJeton)
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
