package com.hr.identiteacces.service;

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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private KafkaTemplate<String, CollaborateurCompteCreeEvent> collaborateurCompteCreeKafkaTemplate;

	@Mock
	private CollaborateurWelcomeMailService collaborateurWelcomeMailService;

	@InjectMocks
	private UserProvisioningService service;

	@Test
	@DisplayName("MAJ rôles RO → COLLABORATEUR remplace les rôles (retire RO, garde USER)")
	void majRoles_roVersCollaborateur_remplaceSansUnion() {
		UUID societeId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID collaborateurId = UUID.randomUUID();
		User user = User.builder()
				.id(userId)
				.societeId(societeId)
				.username("M001")
				.email("ada@agua.test")
				.password("deja-encode")
				.roles(new HashSet<>(Set.of("USER", "RO")))
				.build();
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

		CollaborateurCompteDemandeEvent event = new CollaborateurCompteDemandeEvent(
				collaborateurId,
				"M001",
				"ada@agua.test",
				"Ada",
				"Lovelace",
				"COLLABORATEUR",
				null,
				CollaborateurCompteDemandeEvent.OPERATION_MAJ_ROLES,
				userId);

		assertThatCode(() -> com.hr.commun.societe.SocieteContexte.executer(
				societeId, () -> service.provisionCollaborateurCompte(event)))
				.doesNotThrowAnyException();

		assertThat(user.getRoles()).containsExactly("USER");
		verify(userRepository).save(user);
		verify(passwordEncoder, never()).encode(any());
		verify(collaborateurWelcomeMailService, never())
				.scheduleWelcomeEmail(any(), any(), any(), any(), any());
		verify(collaborateurCompteCreeKafkaTemplate, never()).send(any(), any(), any());
	}

	@Test
	@DisplayName("compte existant trouvé par matricule : remplacement idempotent des rôles")
	void majRoles_parMatricule_remplace() {
		UUID userId = UUID.randomUUID();
		User user = User.builder()
				.id(userId)
				.username("M002")
				.email("alan@agua.test")
				.password("deja-encode")
				.roles(new HashSet<>(Set.of("USER", "RH")))
				.build();
		when(userRepository.findBySocieteIdAndUsernameIgnoreCase(any(UUID.class), eq("M002"))).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

		CollaborateurCompteDemandeEvent event = new CollaborateurCompteDemandeEvent(
				UUID.randomUUID(),
				"M002",
				"alan@agua.test",
				"Alan",
				"Turing",
				"RO",
				null,
				CollaborateurCompteDemandeEvent.OPERATION_MAJ_ROLES,
				null);

		service.provisionCollaborateurCompte(event);

		assertThat(user.getRoles()).containsExactlyInAnyOrder("USER", "RO");
		assertThat(user.getRoles()).doesNotContain("RH");
		verify(passwordEncoder, never()).encode(any());
	}

	@Test
	@DisplayName("mot_de_passe_initial null sur une MAJ : pas de NPE, pas de réencodage")
	void majRoles_motDePasseNull_pasDeNpe() {
		UUID societeId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		User user = User.builder()
				.id(userId)
				.societeId(societeId)
				.username("M003")
				.email("grace@agua.test")
				.password("deja-encode")
				.roles(new HashSet<>(Set.of("USER")))
				.build();
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

		CollaborateurCompteDemandeEvent event = new CollaborateurCompteDemandeEvent(
				UUID.randomUUID(),
				"M003",
				"grace@agua.test",
				"Grace",
				"Hopper",
				"ADMIN",
				null,
				CollaborateurCompteDemandeEvent.OPERATION_MAJ_ROLES,
				userId);

		assertThatCode(() -> com.hr.commun.societe.SocieteContexte.executer(
				societeId, () -> service.provisionCollaborateurCompte(event)))
				.doesNotThrowAnyException();
		assertThat(user.getPassword()).isEqualTo("deja-encode");
		verify(passwordEncoder, never()).encode(any());
	}

	@Test
	@DisplayName("compte introuvable alors que l'id est renseigné : WARN, pas d'exception")
	void majRoles_compteIntrouvable_neThrowPas() {
		UUID compteId = UUID.randomUUID();
		when(userRepository.findById(compteId)).thenReturn(Optional.empty());
		when(userRepository.findBySocieteIdAndUsernameIgnoreCase(any(UUID.class), eq("M404"))).thenReturn(Optional.empty());

		CollaborateurCompteDemandeEvent event = new CollaborateurCompteDemandeEvent(
				UUID.randomUUID(),
				"M404",
				"absent@agua.test",
				"Inconnu",
				"Agent",
				"RH",
				null,
				CollaborateurCompteDemandeEvent.OPERATION_MAJ_ROLES,
				compteId);

		assertThatCode(() -> service.provisionCollaborateurCompte(event)).doesNotThrowAnyException();
		verify(userRepository, never()).save(any());
		verify(passwordEncoder, never()).encode(any());
		verify(collaborateurWelcomeMailService, never())
				.scheduleWelcomeEmail(any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("création : user déjà existant → remplace les rôles sans recréer le mot de passe")
	void creation_userExistant_remplaceRoles() {
		UUID userId = UUID.randomUUID();
		UUID collaborateurId = UUID.randomUUID();
		User user = User.builder()
				.id(userId)
				.username("M010")
				.email("exist@agua.test")
				.password("deja-encode")
				.roles(new HashSet<>(Set.of("USER")))
				.build();
		when(userRepository.findBySocieteIdAndUsernameIgnoreCase(any(UUID.class), eq("M010"))).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

		CollaborateurCompteDemandeEvent event = new CollaborateurCompteDemandeEvent(
				collaborateurId,
				"M010",
				"exist@agua.test",
				"Exist",
				"Ant",
				"DIRECTION",
				"Secret123");

		service.provisionCollaborateurCompte(event);

		assertThat(user.getRoles()).containsExactlyInAnyOrder("USER", "DIRECTION");
		verify(passwordEncoder, never()).encode(any());
		verify(collaborateurWelcomeMailService, never())
				.scheduleWelcomeEmail(any(), any(), any(), any(), any());
		ArgumentCaptor<CollaborateurCompteCreeEvent> captor =
				ArgumentCaptor.forClass(CollaborateurCompteCreeEvent.class);
		verify(collaborateurCompteCreeKafkaTemplate).send(any(), any(), captor.capture());
		assertThat(captor.getValue().compteUtilisateurIdentifiant()).isEqualTo(userId);
	}

	@Test
	@DisplayName("B4 (D-SOC03.e) : message Kafka sans en-tête societe_id crée le compte dans la société par défaut (jamais societe_id null)")
	void messageSansEnteteSociete_repliSurSocieteDefaut_jamaisSocieteNull() {
		com.hr.commun.societe.SocieteContexte.effacer();
		UUID societeDefautId = UUID.randomUUID();
		SocieteDefautResolver resolverActif = mock(SocieteDefautResolver.class);
		when(resolverActif.resoudrePourTransitionAvecWarn(any())).thenReturn(Optional.of(societeDefautId));

		UserProvisioningService serviceAvecResolver = new UserProvisioningService(
				userRepository, passwordEncoder, collaborateurCompteCreeKafkaTemplate,
				collaborateurWelcomeMailService, new GenerateurMotDePasse(), resolverActif);

		when(userRepository.findBySocieteIdAndUsernameIgnoreCase(societeDefautId, "COL-SANS-ENTETE"))
				.thenReturn(Optional.empty());
		when(userRepository.existsByEmailIgnoreCase("nouveau@agua.test")).thenReturn(false);
		when(passwordEncoder.encode(any())).thenReturn("hash");
		when(userRepository.save(any(User.class))).thenAnswer(inv -> {
			User u = inv.getArgument(0);
			u.setId(UUID.randomUUID());
			return u;
		});

		CollaborateurCompteDemandeEvent event = new CollaborateurCompteDemandeEvent(
				UUID.randomUUID(), "COL-SANS-ENTETE", "nouveau@agua.test", "Sarra", "Khemiri", "COLLABORATEUR", "MdpInit1!");
		serviceAvecResolver.provisionCollaborateurCompte(event);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		assertThat(userCaptor.getValue().getSocieteId()).isEqualTo(societeDefautId).isNotNull();

		// Si le repli de transition est désactivé (false), lève IllegalStateException au lieu d'écrire societe_id = null
		SocieteDefautResolver resolverInactif = mock(SocieteDefautResolver.class);
		when(resolverInactif.resoudrePourTransitionAvecWarn(any())).thenReturn(Optional.empty());
		UserProvisioningService serviceSansRepli = new UserProvisioningService(
				userRepository, passwordEncoder, collaborateurCompteCreeKafkaTemplate,
				collaborateurWelcomeMailService, new GenerateurMotDePasse(), resolverInactif);

		assertThatThrownBy(() -> serviceSansRepli.provisionCollaborateurCompte(event))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("B4 : même matricule dans deux sociétés (PRINCIPALE et RECETTE2) traité par société sans collision")
	void memeMatriculeDansDeuxSocietes_isoleParSociete() {
		UUID societe1 = UUID.randomUUID();
		UUID societe2 = UUID.randomUUID();
		User userSoc1 = User.builder().id(UUID.randomUUID()).societeId(societe1).username("COL-WEB")
				.email("col1@agua.test").password("h1").roles(new HashSet<>(Set.of("USER"))).build();
		User userSoc2 = User.builder().id(UUID.randomUUID()).societeId(societe2).username("COL-WEB")
				.email("col2@agua.test").password("h2").roles(new HashSet<>(Set.of("USER"))).build();

		when(userRepository.findBySocieteIdAndUsernameIgnoreCase(societe1, "COL-WEB")).thenReturn(Optional.of(userSoc1));
		when(userRepository.findBySocieteIdAndUsernameIgnoreCase(societe2, "COL-WEB")).thenReturn(Optional.of(userSoc2));
		when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

		CollaborateurCompteDemandeEvent majDirectionSoc1 = new CollaborateurCompteDemandeEvent(
				UUID.randomUUID(), "COL-WEB", "col1@agua.test", "Ali", "Ben", "DIRECTION", null,
				CollaborateurCompteDemandeEvent.OPERATION_MAJ_ROLES, null);
		CollaborateurCompteDemandeEvent majRhSoc2 = new CollaborateurCompteDemandeEvent(
				UUID.randomUUID(), "COL-WEB", "col2@agua.test", "Sarra", "Khemiri", "RH", null,
				CollaborateurCompteDemandeEvent.OPERATION_MAJ_ROLES, null);

		com.hr.commun.societe.SocieteContexte.executer(societe1, () -> service.provisionCollaborateurCompte(majDirectionSoc1));
		com.hr.commun.societe.SocieteContexte.executer(societe2, () -> service.provisionCollaborateurCompte(majRhSoc2));

		assertThat(userSoc1.getRoles()).containsExactlyInAnyOrder("USER", "DIRECTION");
		assertThat(userSoc2.getRoles()).containsExactlyInAnyOrder("USER", "RH");
	}

	@Test
	@DisplayName("D-SOC20 #1 : compte_utilisateur_id d'un compte PLATEFORME (societe_id null) ou d'une autre société est ignoré")
	void majRoles_comptePlateformeSansSociete_estIgnore() {
		UUID societeId = UUID.randomUUID();
		UUID plateformeId = UUID.randomUUID();
		User comptePlateforme = User.builder()
				.id(plateformeId)
				.societeId(null)
				.username("PLATEFORME01")
				.email("plat@agua.test")
				.password("h")
				.roles(new HashSet<>(Set.of("PLATEFORME")))
				.build();

		when(userRepository.findById(plateformeId)).thenReturn(Optional.of(comptePlateforme));
		when(userRepository.findBySocieteIdAndUsernameIgnoreCase(societeId, "PLATEFORME01")).thenReturn(Optional.empty());

		CollaborateurCompteDemandeEvent majRoles = new CollaborateurCompteDemandeEvent(
				UUID.randomUUID(), "PLATEFORME01", "plat@agua.test", "Operateur", "Plat", "RH", null,
				CollaborateurCompteDemandeEvent.OPERATION_MAJ_ROLES, plateformeId);

		com.hr.commun.societe.SocieteContexte.executer(societeId, () -> service.provisionCollaborateurCompte(majRoles));

		assertThat(comptePlateforme.getRoles()).containsExactly("PLATEFORME");
		verify(userRepository, never()).save(any());
	}
}
