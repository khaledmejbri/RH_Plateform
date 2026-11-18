package com.hr.identiteacces.controller;

import com.hr.identiteacces.config.SecurityConfig;
import com.hr.identiteacces.domain.StatutSociete;
import com.hr.identiteacces.dto.SocieteDtos.AdminInitialRequest;
import com.hr.identiteacces.dto.SocieteDtos.CreerSocieteRequest;
import com.hr.identiteacces.dto.SocieteDtos.SocieteResponse;
import com.hr.identiteacces.dto.UserResponse;
import com.hr.identiteacces.entity.Societe;
import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.kafka.SocieteEvenementPublisher;
import com.hr.identiteacces.repository.SocieteRepository;
import com.hr.identiteacces.repository.UserRepository;
import com.hr.identiteacces.service.CollaborateurWelcomeMailService;
import com.hr.identiteacces.service.GenerateurMotDePasse;
import com.hr.identiteacces.service.SocieteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PlateformeSocieteController.class, InternalSocieteController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SocieteService.class, GenerateurMotDePasse.class})
@TestPropertySource(properties = {
		"app.internal-api.token=jeton-interne-test",
		"spring.security.oauth2.authorizationserver.issuer=http://localhost:8080"
})
class PlateformeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SocieteRepository societeRepository;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	private CollaborateurWelcomeMailService collaborateurWelcomeMailService;

	@MockitoBean
	private SocieteEvenementPublisher societeEvenementPublisher;

	@BeforeEach
	void setUp() {
		when(passwordEncoder.encode(any())).thenReturn("hash-admin");
	}

	@Test
	@DisplayName("CA-03.1 : PLATEFORME crée une société → 201 ; code en double (sans casse) → 409 SOCIETE_CODE_EXISTANT ; code invalide → 400 CORPS_INVALIDE")
	void creerSociete_201_409_400() throws Exception {
		UUID idCree = UUID.randomUUID();
		when(societeRepository.existsByCodeIgnoreCase("RECETTE2")).thenReturn(false).thenReturn(true);
		when(societeRepository.saveAndFlush(any(Societe.class))).thenAnswer(inv -> {
			Societe s = inv.getArgument(0);
			s.setId(idCree);
			s.setCreeLe(Instant.now());
			s.setModifieLe(Instant.now());
			s.setVersion(0L);
			return s;
		});

		// 1. Création nominale → 201
		mockMvc.perform(post("/api/plateforme/v1/societes")
						.with(jwt().jwt(j -> j.claim("roles", List.of("PLATEFORME")))
								.authorities(new SimpleGrantedAuthority("ROLE_PLATEFORME")))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"RECETTE2\",\"raison_sociale\":\"[RECETTE-WF] Société Recette 2\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("RECETTE2"))
				.andExpect(jsonPath("$.statut").value("ACTIVE"))
				.andExpect(jsonPath("$.fuseau").value("Africa/Tunis"));

		verify(societeEvenementPublisher).publierApresCommit(eq("SOCIETE_CREEE"), eq(idCree), eq("RECETTE2"));

		// 2. Code en double (sans casse : "recette2") → 409 SOCIETE_CODE_EXISTANT
		mockMvc.perform(post("/api/plateforme/v1/societes")
						.with(jwt().jwt(j -> j.claim("roles", List.of("PLATEFORME")))
								.authorities(new SimpleGrantedAuthority("ROLE_PLATEFORME")))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"recette2\",\"raison_sociale\":\"Doublon\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SOCIETE_CODE_EXISTANT"));

		// 3. Code réservé PLATEFORME → 409 SOCIETE_CODE_EXISTANT
		mockMvc.perform(post("/api/plateforme/v1/societes")
						.with(jwt().jwt(j -> j.claim("roles", List.of("PLATEFORME")))
								.authorities(new SimpleGrantedAuthority("ROLE_PLATEFORME")))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"PLATEFORME\",\"raison_sociale\":\"Réservé\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SOCIETE_CODE_EXISTANT"));

		// 4. Code invalide (1 caractère ou caractères interdits) → 400 CORPS_INVALIDE
		mockMvc.perform(post("/api/plateforme/v1/societes")
						.with(jwt().jwt(j -> j.claim("roles", List.of("PLATEFORME")))
								.authorities(new SimpleGrantedAuthority("ROLE_PLATEFORME")))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"A\",\"raison_sociale\":\"Code trop court\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CORPS_INVALIDE"));
	}

	@Test
	@DisplayName("CA-03.3 : PLATEFORME crée le premier ADMIN → 201 + courriel de bienvenue ; second appel → 409 ADMIN_INITIAL_EXISTANT")
	void creerAdminInitial_201_puis_409() throws Exception {
		UUID societeId = UUID.randomUUID();
		Societe societe = Societe.builder()
				.id(societeId)
				.code("RECETTE2")
				.raisonSociale("[RECETTE-WF] Société Recette 2")
				.statut(StatutSociete.ACTIVE)
				.fuseau("Africa/Tunis")
				.build();

		when(societeRepository.findById(societeId)).thenReturn(Optional.of(societe));
		when(userRepository.existsBySocieteIdAndRole(societeId, "ADMIN")).thenReturn(false).thenReturn(true);
		when(userRepository.existsByEmailIgnoreCase("admin.rec2@rh-evenement.test")).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
			User u = inv.getArgument(0);
			u.setId(UUID.randomUUID());
			u.setCreatedAt(Instant.now());
			return u;
		});

		String body = "{\"matricule\":\"ADM-REC2\",\"prenom\":\"Nizar\",\"nom\":\"Gharbi\",\"courriel\":\"admin.rec2@rh-evenement.test\"}";

		mockMvc.perform(post("/api/plateforme/v1/societes/" + societeId + "/admin-initial")
						.with(jwt().jwt(j -> j.claim("roles", List.of("PLATEFORME")))
								.authorities(new SimpleGrantedAuthority("ROLE_PLATEFORME")))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nom_utilisateur").value("ADM-REC2"))
				.andExpect(jsonPath("$.courriel").value("admin.rec2@rh-evenement.test"));

		verify(collaborateurWelcomeMailService).scheduleWelcomeEmail(
				eq("admin.rec2@rh-evenement.test"), eq("Nizar"), eq("Gharbi"), eq("ADM-REC2"), any());

		// Second appel → 409 ADMIN_INITIAL_EXISTANT
		mockMvc.perform(post("/api/plateforme/v1/societes/" + societeId + "/admin-initial")
						.with(jwt().jwt(j -> j.claim("roles", List.of("PLATEFORME")))
								.authorities(new SimpleGrantedAuthority("ROLE_PLATEFORME")))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ADMIN_INITIAL_EXISTANT"));
	}

	@Test
	@DisplayName("CA-03.4 : PLATEFORME appelle une route RH → 403 SOCIETE_ABSENTE ; un ADMIN de société appelle /api/plateforme/** → 403")
	void controleCroisePlateformeEtAdminSociete() throws Exception {
		// 1. PLATEFORME appelle une route RH → 403 SOCIETE_ABSENTE
		mockMvc.perform(get("/api/rh/v1/collaborateurs")
						.with(jwt().jwt(j -> j.claim("roles", List.of("PLATEFORME")))
								.authorities(new SimpleGrantedAuthority("ROLE_PLATEFORME"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SOCIETE_ABSENTE"));

		// 2. ADMIN de société appelle /api/plateforme/** → 403
		UUID societeId = UUID.randomUUID();
		mockMvc.perform(get("/api/plateforme/v1/societes")
						.with(jwt().jwt(j -> j.claim("societe_id", societeId.toString())
										.claim("roles", List.of("USER", "ADMIN")))
								.authorities(
										new SimpleGrantedAuthority("ROLE_USER"),
										new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Modification de société (200 et 409 MODIFIE_ENTRE_TEMPS), suspension/réactivation (200 et 409 ETAT_INCORRECT) et routes internes")
	void modifierSuspendreReactiverEtRoutesInternes() throws Exception {
		UUID societeId = UUID.randomUUID();
		Societe societe = Societe.builder()
				.id(societeId)
				.code("RECETTE2")
				.raisonSociale("[RECETTE-WF] Société Recette 2")
				.statut(StatutSociete.ACTIVE)
				.fuseau("Africa/Tunis")
				.creeLe(Instant.now())
				.modifieLe(Instant.now())
				.version(2L)
				.build();

		when(societeRepository.findById(societeId)).thenReturn(Optional.of(societe));
		when(societeRepository.findByCodeIgnoreCase("RECETTE2")).thenReturn(Optional.of(societe));
		when(societeRepository.saveAndFlush(any(Societe.class))).thenAnswer(inv -> inv.getArgument(0));

		// PUT avec mauvaise version → 409 MODIFIE_ENTRE_TEMPS
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/plateforme/v1/societes/" + societeId)
						.with(jwt().jwt(j -> j.claim("roles", List.of("PLATEFORME")))
								.authorities(new SimpleGrantedAuthority("ROLE_PLATEFORME")))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"raison_sociale\":\"Nouveau nom\",\"fuseau\":\"Africa/Tunis\",\"version\":1}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MODIFIE_ENTRE_TEMPS"));

		// Suspendre une société ACTIVE → 200, second appel → 409 ETAT_INCORRECT
		mockMvc.perform(post("/api/plateforme/v1/societes/" + societeId + "/suspendre")
						.with(jwt().jwt(j -> j.claim("roles", List.of("PLATEFORME")))
								.authorities(new SimpleGrantedAuthority("ROLE_PLATEFORME")))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"motif\":\"Maintenance\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statut").value("SUSPENDUE"));

		mockMvc.perform(post("/api/plateforme/v1/societes/" + societeId + "/suspendre")
						.with(jwt().jwt(j -> j.claim("roles", List.of("PLATEFORME")))
								.authorities(new SimpleGrantedAuthority("ROLE_PLATEFORME")))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"motif\":\"Maintenance\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ETAT_INCORRECT"));

		// Route interne /internal/identite/v1/societes/par-code/{code} avec X-Internal-Token sans X-Societe-Id → 200
		mockMvc.perform(get("/internal/identite/v1/societes/par-code/RECETTE2")
						.header("X-Internal-Token", "jeton-interne-test"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("RECETTE2"));
	}
}
