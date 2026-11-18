package com.hr.identiteacces;

import com.hr.commun.societe.SocieteContexte;
import com.hr.identiteacces.dto.AuthResponse;
import com.hr.identiteacces.dto.SigninRequest;
import com.hr.identiteacces.dto.SocieteDtos.AdminInitialRequest;
import com.hr.identiteacces.dto.SocieteDtos.CreerSocieteRequest;
import com.hr.identiteacces.dto.SocieteDtos.ModifierSocieteRequest;
import com.hr.identiteacces.dto.SocieteDtos.MotifSocieteRequest;
import com.hr.identiteacces.dto.SocieteDtos.SocieteResponse;
import com.hr.identiteacces.dto.UserResponse;
import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.repository.SocieteRepository;
import com.hr.identiteacces.repository.UserRepository;
import com.hr.identiteacces.service.AuthService;
import com.hr.identiteacces.service.SocieteService;
import com.hr.identiteacces.service.UserService;
import com.hr.identiteacces.web.IdentiteMetierException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test d'intégration d'isolation multi-société (§ 4.4 et § 9.14) sur PostgreSQL dédié ({@code authentication_db_test}).
 */
@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:postgresql://localhost:5444/authentication_db_test",
		"app.mail.collaborateur-welcome-enabled=false",
		"eureka.client.enabled=false"
})
class IsolationMultiSocieteTest {

	@org.springframework.test.context.bean.override.mockito.MockitoBean
	private com.hr.identiteacces.kafka.SocieteEvenementPublisher societeEvenementPublisher;

	@Autowired
	private SocieteService societeService;

	@Autowired
	private AuthService authService;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SocieteRepository societeRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private UUID societeAId;
	private UUID societeBId;
	private UUID userAId;
	private UUID userBId;
	private String codeA;
	private String codeB;

	@BeforeEach
	void setUp() {
		String suffixe = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
		codeA = "WFA-" + suffixe;
		codeB = "WFB-" + suffixe;

		SocieteResponse socA = societeService.creer(
				new CreerSocieteRequest(codeA, "[RECETTE-WF] Société A " + suffixe, "Africa/Tunis"));
		SocieteResponse socB = societeService.creer(
				new CreerSocieteRequest(codeB, "[RECETTE-WF] Société B " + suffixe, "Africa/Tunis"));
		societeAId = socA.id();
		societeBId = socB.id();

		// Même matricule COL-ISO dans les deux sociétés (CA-01.3)
		User uA = User.builder()
				.societeId(societeAId)
				.username("COL-ISO")
				.email("col.a." + suffixe.toLowerCase() + "@rh-evenement.test")
				.password(passwordEncoder.encode("MotDePasseA1!"))
				.roles(new HashSet<>(Set.of("USER", "RH")))
				.build();
		userAId = userRepository.saveAndFlush(uA).getId();

		User uB = User.builder()
				.societeId(societeBId)
				.username("COL-ISO")
				.email("col.b." + suffixe.toLowerCase() + "@rh-evenement.test")
				.password(passwordEncoder.encode("MotDePasseB1!"))
				.roles(new HashSet<>(Set.of("USER", "RH")))
				.build();
		userBId = userRepository.saveAndFlush(uB).getId();
	}

	@AfterEach
	void tearDown() {
		SocieteContexte.effacer();
		if (userAId != null) {
			userRepository.deleteById(userAId);
		}
		if (userBId != null) {
			userRepository.deleteById(userBId);
		}
		if (societeAId != null) {
			societeRepository.findById(societeAId).ifPresent(s -> {
				userRepository.findBySocieteIdAndRolesContaining(societeAId, "ADMIN")
						.forEach(u -> userRepository.deleteById(u.getId()));
				societeRepository.deleteById(societeAId);
			});
		}
		if (societeBId != null) {
			societeRepository.findById(societeBId).ifPresent(s -> {
				userRepository.findBySocieteIdAndRolesContaining(societeBId, "ADMIN")
						.forEach(u -> userRepository.deleteById(u.getId()));
				societeRepository.deleteById(societeBId);
			});
		}
	}

	@Test
	@DisplayName("Isolation : même matricule dans deux sociétés, by-role et identifiants-existants étanches, connexion isolée")
	void isolationCompleteEntreDeuxSocietes() {
		// 1. Liste des comptes par rôle RH dans la société A ne voit pas la société B
		List<UserResponse> rhA = SocieteContexte.executer(societeAId, () -> userService.getUsersByRole("RH"));
		assertThat(rhA).extracting(UserResponse::getId).contains(userAId).doesNotContain(userBId);

		List<UserResponse> rhB = SocieteContexte.executer(societeBId, () -> userService.getUsersByRole("RH"));
		assertThat(rhB).extracting(UserResponse::getId).contains(userBId).doesNotContain(userAId);

		// 2. Connexion : le mot de passe de A ne permet pas de se connecter sur B et inversement
		AuthResponse authA = authService.signin(new SigninRequest("COL-ISO", "MotDePasseA1!", codeA));
		assertThat(authA.getUser().getId()).isEqualTo(userAId);

		assertThatThrownBy(() -> authService.signin(new SigninRequest("COL-ISO", "MotDePasseA1!", codeB)))
				.isInstanceOf(IdentiteMetierException.class)
				.satisfies(e -> assertThat(((IdentiteMetierException) e).getCode()).isEqualTo("IDENTIFIANTS_INVALIDES"));

		// 3. Suspension de A bloque la connexion sur A sans affecter B
		societeService.suspendre(societeAId, new MotifSocieteRequest("[RECETTE-WF] Test suspension"));
		assertThatThrownBy(() -> authService.signin(new SigninRequest("COL-ISO", "MotDePasseA1!", codeA)))
				.isInstanceOf(IdentiteMetierException.class)
				.satisfies(e -> assertThat(((IdentiteMetierException) e).getCode()).isEqualTo("SOCIETE_SUSPENDUE"));

		AuthResponse authB = authService.signin(new SigninRequest("COL-ISO", "MotDePasseB1!", codeB));
		assertThat(authB.getUser().getId()).isEqualTo(userBId);

		// 4. Admin initial créé sur A n'empêche pas la création de l'admin initial sur B
		String suffixeMail = UUID.randomUUID().toString().substring(0, 6);
		societeService.creerAdminInitial(societeAId,
				new AdminInitialRequest("ADM-ISO", "Nizar", "A", "adm.a." + suffixeMail + "@rh-evenement.test"));
		UserResponse adminB = societeService.creerAdminInitial(societeBId,
				new AdminInitialRequest("ADM-ISO", "Nizar", "B", "adm.b." + suffixeMail + "@rh-evenement.test"));
		assertThat(adminB.getUsername()).isEqualTo("ADM-ISO");

		// 5. Identifiant de société inexistant sur les routes plateforme / internes → 404
		UUID inconnu = UUID.randomUUID();
		assertThatThrownBy(() -> societeService.trouverParId(inconnu))
				.isInstanceOf(IdentiteMetierException.class)
				.satisfies(e -> assertThat(((IdentiteMetierException) e).getHttpStatus()).isEqualTo(404));
		assertThatThrownBy(() -> societeService.modifier(inconnu, new ModifierSocieteRequest("Test", "Africa/Tunis", 0L)))
				.isInstanceOf(IdentiteMetierException.class)
				.satisfies(e -> assertThat(((IdentiteMetierException) e).getHttpStatus()).isEqualTo(404));
	}
}
