package com.hr.identiteacces.service;

import com.hr.identiteacces.controller.InternalUserController;
import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.kafka.CollaborateurCompteCreeEvent;
import com.hr.identiteacces.kafka.CollaborateurCompteDemandeEvent;
import com.hr.identiteacces.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** T2 (C2-20) : compte créé par import avec mot de passe généré, contrôle interne des identifiants existants. */
@ExtendWith(MockitoExtension.class)
class ImportComptesTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private KafkaTemplate<String, CollaborateurCompteCreeEvent> kafkaTemplate;
	@Mock
	private CollaborateurWelcomeMailService mailService;
	private final GenerateurMotDePasse generateur = new GenerateurMotDePasse();

	@InjectMocks
	private UserProvisioningService service;

	private CollaborateurCompteDemandeEvent evenement(String courriel, Boolean genere) {
		return new CollaborateurCompteDemandeEvent(UUID.randomUUID(), "COL-IMP", courriel, "Ada", "Lovelace",
				"COLLABORATEUR", null, CollaborateurCompteDemandeEvent.OPERATION_CREATION, null, genere);
	}

	@Test
	@DisplayName("D-I09 : mot de passe généré (>= 12 caractères) transmis au seul courriel de bienvenue, encodé en base")
	void motDePasseGenere() {
		var s = new UserProvisioningService(userRepository, passwordEncoder, kafkaTemplate, mailService, generateur);
		when(userRepository.findByUsername("COL-IMP")).thenReturn(Optional.empty());
		when(passwordEncoder.encode(any())).thenAnswer(i -> "encode:" + i.getArgument(0));
		when(userRepository.save(any(User.class))).thenAnswer(i -> {
			User u = i.getArgument(0);
			u.setId(UUID.randomUUID());
			return u;
		});

		s.provisionCollaborateurCompte(evenement("ada@rh-evenement.test", true));

		ArgumentCaptor<String> clair = ArgumentCaptor.forClass(String.class);
		verify(mailService).scheduleWelcomeEmail(eq("ada@rh-evenement.test"), eq("Ada"), eq("Lovelace"), eq("COL-IMP"),
				clair.capture());
		assertThat(clair.getValue()).hasSizeGreaterThanOrEqualTo(12);
		ArgumentCaptor<User> enregistre = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(enregistre.capture());
		assertThat(enregistre.getValue().getPassword()).isEqualTo("encode:" + clair.getValue());
		assertThat(enregistre.getValue().getUsername()).isEqualTo("COL-IMP");
	}

	@Test
	@DisplayName("compte déjà existant de même identifiant : lié comme aujourd'hui, aucun courriel, aucun mot de passe généré")
	void compteExistantLieSansCourriel() {
		var s = new UserProvisioningService(userRepository, passwordEncoder, kafkaTemplate, mailService, generateur);
		User existant = User.builder().id(UUID.randomUUID()).username("COL-IMP").email("x@x.test")
				.roles(new java.util.HashSet<>(java.util.Set.of("USER"))).build();
		when(userRepository.findByUsername("COL-IMP")).thenReturn(Optional.of(existant));
		when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

		s.provisionCollaborateurCompte(evenement("ada@rh-evenement.test", true));

		verify(mailService, never()).scheduleWelcomeEmail(any(), any(), any(), any(), any());
		verify(passwordEncoder, never()).encode(any());
	}

	@Test
	@DisplayName("D-I13.g : courriel déjà pris -> pas de compte, pas de courriel, pas d'exception (fiche sans compte)")
	void courrielDejaPris() {
		var s = new UserProvisioningService(userRepository, passwordEncoder, kafkaTemplate, mailService, generateur);
		when(userRepository.findByUsername("COL-IMP")).thenReturn(Optional.empty());
		when(userRepository.existsByEmail("ada@rh-evenement.test")).thenReturn(true);

		s.provisionCollaborateurCompte(evenement("ada@rh-evenement.test", true));

		verify(userRepository, never()).save(any());
		verifyNoInteractions(mailService);
	}

	@Test
	@DisplayName("sans mot_de_passe_genere ni mot de passe initial : création ignorée (comportement existant)")
	void sansMotDePasse() {
		var s = new UserProvisioningService(userRepository, passwordEncoder, kafkaTemplate, mailService, generateur);
		when(userRepository.findByUsername("COL-IMP")).thenReturn(Optional.empty());
		s.provisionCollaborateurCompte(evenement("ada@rh-evenement.test", null));
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("générateur : 16 caractères, minuscule + majuscule + chiffre + symbole, sans caractères ambigus, tirages différents")
	void generateur() {
		java.util.Set<String> vus = new java.util.HashSet<>();
		for (int i = 0; i < 200; i++) {
			String mdp = generateur.generer();
			vus.add(mdp);
			assertThat(mdp).hasSize(16).matches(".*[a-z].*").matches(".*[A-Z].*").matches(".*[0-9].*")
					.matches(".*[!#$%&*+=?@-].*").doesNotContain("0", "O", "1", "l", "I");
		}
		assertThat(vus).hasSize(200);
	}

	// ─── X2 ───────────────────────────────────────────────────────────────────

	private final UserService userService = mock(UserService.class);
	private final InternalUserController controller = new InternalUserController(userService, "secret-partage");

	@Test
	@DisplayName("X2 : sans jeton ou mauvais jeton -> 403, service non appelé")
	void x2Jeton() {
		var corps = new InternalUserController.IdentifiantsRequest(List.of("COL-X"));
		assertThatThrownBy(() -> controller.identifiantsExistants(corps, null)).isInstanceOf(ResponseStatusException.class)
				.satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(403));
		assertThatThrownBy(() -> controller.identifiantsExistants(corps, "autre"))
				.satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(403));
		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("X2 : comparaison sans casse, réponse en minuscules, plus de 5 000 identifiants -> 400")
	void x2Existants() {
		when(userService.identifiantsExistants(List.of("col-x", "drh01"))).thenReturn(List.of("col-x"));
		var r = controller.identifiantsExistants(new InternalUserController.IdentifiantsRequest(List.of("COL-X", " drh01 ")),
				"secret-partage");
		assertThat(r.existants()).containsExactly("col-x");
		List<String> trop = java.util.stream.IntStream.range(0, 5001).mapToObj(i -> "u" + i).toList();
		assertThatThrownBy(() -> controller.identifiantsExistants(new InternalUserController.IdentifiantsRequest(trop), "secret-partage"))
				.satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(400));
	}
}
