package com.hr.identiteacces.service;

import com.hr.identiteacces.domain.StatutSociete;
import com.hr.identiteacces.dto.AuthResponse;
import com.hr.identiteacces.dto.SigninRequest;
import com.hr.identiteacces.entity.Societe;
import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.repository.SocieteRepository;
import com.hr.identiteacces.repository.UserRepository;
import com.hr.identiteacces.web.IdentiteMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceSocieteTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private SocieteRepository societeRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private JwtEncoder jwtEncoder;

	private AuthService authService;

	private Societe societePrincipale;
	private Societe societeRecette2;

	@BeforeEach
	void setUp() {
		authService = new AuthService(
				userRepository,
				societeRepository,
				passwordEncoder,
				jwtEncoder,
				"PRINCIPALE",
				3600L,
				"http://localhost:8080"
		);

		societePrincipale = Societe.builder()
				.id(UUID.randomUUID())
				.code("PRINCIPALE")
				.raisonSociale("Société principale")
				.statut(StatutSociete.ACTIVE)
				.fuseau("Africa/Tunis")
				.build();

		societeRecette2 = Societe.builder()
				.id(UUID.randomUUID())
				.code("RECETTE2")
				.raisonSociale("[RECETTE-WF] Société Recette 2")
				.statut(StatutSociete.ACTIVE)
				.fuseau("Africa/Tunis")
				.build();

		lenient().when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenAnswer(inv -> {
			JwtEncoderParameters params = inv.getArgument(0);
			JwtClaimsSet claims = params.getClaims();
			return new Jwt("jeton-signe", claims.getIssuedAt(), claims.getExpiresAt(),
					Map.of("alg", "RS256"), claims.getClaims());
		});
	}

	@Test
	@DisplayName("CA-02.1 : signin sans societe_code utilise la société par défaut PRINCIPALE et renvoie 200")
	void signin_sansSocieteCode_utiliseSocieteDefaut() {
		User user = User.builder()
				.id(UUID.randomUUID())
				.societeId(societePrincipale.getId())
				.username("COL-WEB")
				.email("sarra@rh-evenement.test")
				.password("hash-ok")
				.roles(Set.of("USER"))
				.createdAt(Instant.now())
				.build();

		when(societeRepository.findByCodeIgnoreCase("PRINCIPALE")).thenReturn(Optional.of(societePrincipale));
		when(userRepository.findBySocieteIdAndIdentifiantIgnoreCase(societePrincipale.getId(), "COL-WEB"))
				.thenReturn(Optional.of(user));
		when(passwordEncoder.matches("SecretTest1!", "hash-ok")).thenReturn(true);

		AuthResponse rep = authService.signin(new SigninRequest("COL-WEB", "SecretTest1!", null));

		assertThat(rep.getAccessToken()).isEqualTo("jeton-signe");
		ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
		verify(jwtEncoder).encode(captor.capture());
		JwtClaimsSet claims = captor.getValue().getClaims();
		assertThat(claims.getClaimAsString("societe_id")).isEqualTo(societePrincipale.getId().toString());
		assertThat(claims.getClaimAsString("societe_code")).isEqualTo("PRINCIPALE");
		assertThat(claims.getClaimAsString("identifiant_utilisateur")).isEqualTo(user.getId().toString());
	}

	@Test
	@DisplayName("CA-02.2 : signin avec societe_code d'une société et matricule de cette société → 200 avec claims societe_id et societe_code")
	void signin_avecSocieteCode_inclutClaimsSociete() {
		User user = User.builder()
				.id(UUID.randomUUID())
				.societeId(societeRecette2.getId())
				.username("COL-WEB")
				.email("sarra.rec2@rh-evenement.test")
				.password("hash-rec2")
				.roles(Set.of("USER", "RH"))
				.createdAt(Instant.now())
				.build();

		when(societeRepository.findByCodeIgnoreCase("RECETTE2")).thenReturn(Optional.of(societeRecette2));
		when(userRepository.findBySocieteIdAndIdentifiantIgnoreCase(societeRecette2.getId(), "COL-WEB"))
				.thenReturn(Optional.of(user));
		when(passwordEncoder.matches("SecretTest2!", "hash-rec2")).thenReturn(true);

		AuthResponse rep = authService.signin(new SigninRequest("COL-WEB", "SecretTest2!", "RECETTE2"));

		assertThat(rep.getAccessToken()).isEqualTo("jeton-signe");
		ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
		verify(jwtEncoder).encode(captor.capture());
		JwtClaimsSet claims = captor.getValue().getClaims();
		assertThat(claims.getClaimAsString("societe_id")).isEqualTo(societeRecette2.getId().toString());
		assertThat(claims.getClaimAsString("societe_code")).isEqualTo("RECETTE2");
		assertThat(claims.getClaimAsString("identifiant_utilisateur")).isEqualTo(user.getId().toString());
	}

	@Test
	@DisplayName("CA-02.3 : code société inconnu, matricule absent de la société ou mauvais mot de passe → même 401 IDENTIFIANTS_INVALIDES")
	void signin_troisCasEchec_meme401IdentifiantsInvalides() {
		// 1. Code société inconnu
		when(societeRepository.findByCodeIgnoreCase("INCONNUE")).thenReturn(Optional.empty());
		IdentiteMetierException exCode = capturerEchec(new SigninRequest("COL-WEB", "SecretTest1!", "INCONNUE"));

		// 2. Matricule absent de cette société
		when(societeRepository.findByCodeIgnoreCase("RECETTE2")).thenReturn(Optional.of(societeRecette2));
		when(userRepository.findBySocieteIdAndIdentifiantIgnoreCase(societeRecette2.getId(), "ABSENT"))
				.thenReturn(Optional.empty());
		IdentiteMetierException exMatricule = capturerEchec(new SigninRequest("ABSENT", "SecretTest1!", "RECETTE2"));

		// 3. Mauvais mot de passe
		User user = User.builder()
				.id(UUID.randomUUID())
				.societeId(societeRecette2.getId())
				.username("COL-WEB")
				.email("sarra.rec2@rh-evenement.test")
				.password("hash-rec2")
				.roles(Set.of("USER"))
				.build();
		when(userRepository.findBySocieteIdAndIdentifiantIgnoreCase(societeRecette2.getId(), "COL-WEB"))
				.thenReturn(Optional.of(user));
		when(passwordEncoder.matches("MauvaisMdp", "hash-rec2")).thenReturn(false);
		IdentiteMetierException exMdp = capturerEchec(new SigninRequest("COL-WEB", "MauvaisMdp", "RECETTE2"));

		assertThat(exCode.getHttpStatus()).isEqualTo(401);
		assertThat(exCode.getCode()).isEqualTo("IDENTIFIANTS_INVALIDES");
		assertThat(exMatricule.getHttpStatus()).isEqualTo(401);
		assertThat(exMatricule.getCode()).isEqualTo("IDENTIFIANTS_INVALIDES");
		assertThat(exMdp.getHttpStatus()).isEqualTo(401);
		assertThat(exMdp.getCode()).isEqualTo("IDENTIFIANTS_INVALIDES");
		assertThat(exCode.getMessage())
				.isEqualTo(exMatricule.getMessage())
				.isEqualTo(exMdp.getMessage());
	}

	@Test
	@DisplayName("CA-02.4 : société suspendue → 401 SOCIETE_SUSPENDUE avec le message attendu")
	void signin_societeSuspendue_renvoie401SocieteSuspendue() {
		societeRecette2.setStatut(StatutSociete.SUSPENDUE);
		when(societeRepository.findByCodeIgnoreCase("RECETTE2")).thenReturn(Optional.of(societeRecette2));

		assertThatThrownBy(() -> authService.signin(new SigninRequest("COL-WEB", "SecretTest1!", "RECETTE2")))
				.isInstanceOf(IdentiteMetierException.class)
				.satisfies(t -> {
					IdentiteMetierException ex = (IdentiteMetierException) t;
					assertThat(ex.getHttpStatus()).isEqualTo(401);
					assertThat(ex.getCode()).isEqualTo("SOCIETE_SUSPENDUE");
					assertThat(ex.getMessage()).isEqualTo(
							"L'accès de votre société est suspendu. Contactez votre administrateur.");
				});
	}

	@Test
	@DisplayName("D-SOC03.b : connexion PLATEFORME avec societe_code=PLATEFORME émet un jeton sans claim societe_id")
	void signin_plateforme_sansClaimSocieteId() {
		User plat = User.builder()
				.id(UUID.randomUUID())
				.societeId(null)
				.username("operateur")
				.email("operateur@plateforme.local")
				.password("hash-plat")
				.roles(Set.of("PLATEFORME"))
				.createdAt(Instant.now())
				.build();

		when(userRepository.findPlateformeByIdentifiantIgnoreCase("operateur")).thenReturn(Optional.of(plat));
		when(passwordEncoder.matches("MdpPlateforme1!", "hash-plat")).thenReturn(true);

		authService.signin(new SigninRequest("operateur", "MdpPlateforme1!", "PLATEFORME"));

		ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
		verify(jwtEncoder).encode(captor.capture());
		JwtClaimsSet claims = captor.getValue().getClaims();
		assertThat(claims.getClaims()).doesNotContainKey("societe_id");
		assertThat(claims.getClaims()).doesNotContainKey("societe_code");
		assertThat(claims.getClaimAsStringList("roles")).containsExactly("PLATEFORME");
	}

	private IdentiteMetierException capturerEchec(SigninRequest req) {
		try {
			authService.signin(req);
			throw new AssertionError("Attendait IdentiteMetierException");
		} catch (IdentiteMetierException e) {
			return e;
		}
	}
}
