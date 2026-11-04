package com.hr.evaluation.security;

import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationRh;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.EvaluationRhRepository;
import com.hr.evaluation.web.EvaluationMetierException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationAccessServiceTest {

	@Mock
	private EvaluationRepository evaluationRepository;

	@Mock
	private EvaluationRhRepository evaluationRhRepository;

	private EvaluationAccessService access;

	private final UUID collabId = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private final UUID managerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private final UUID strangerId = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private final UUID evaluationId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

	@BeforeEach
	void setUp() {
		access = new EvaluationAccessService(evaluationRepository, evaluationRhRepository,
				new IdentiteAppelantResolver(() -> java.util.Optional.empty()));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void directionEstLectureMaisPasEcriture() {
		authenticate(jwtWithRoles("USER", "DIRECTION"), List.of("ROLE_USER", "ROLE_DIRECTION"));

		assertThat(access.isBackofficeLecture()).isTrue();
		assertThat(access.isBackofficeEcriture()).isFalse();
	}

	@Test
	void rhEstLectureEtEcriture() {
		authenticate(jwtWithRoles("USER", "RH"), List.of("ROLE_USER", "ROLE_RH"));

		assertThat(access.isBackofficeLecture()).isTrue();
		assertThat(access.isBackofficeEcriture()).isTrue();
	}

	@Test
	void collaborateurPeutAccederASonEvaluation() {
		authenticate(jwtWithUser(collabId), List.of("ROLE_USER"));
		stubHeader(collabId);
		Evaluation evaluation = evaluation(collabId, managerId);
		when(evaluationRepository.findByIdWithCampaign(evaluationId)).thenReturn(Optional.of(evaluation));

		assertThat(access.requireCollaborateurEvaluation(evaluationId)).isSameAs(evaluation);
	}

	@Test
	void collaborateurNePeutPasNoterCommeManager() {
		authenticate(jwtWithUser(collabId), List.of("ROLE_USER"));
		when(evaluationRepository.findByIdWithCampaign(evaluationId))
				.thenReturn(Optional.of(evaluation(collabId, managerId)));

		assertThatThrownBy(() -> access.requireManagerEvaluation(evaluationId))
				.isInstanceOf(SecurityException.class)
				.hasMessageContaining("supérieur");
	}

	@Test
	void managerPeutNoterSonPerimetre() {
		authenticate(jwtWithUser(managerId), List.of("ROLE_USER", "ROLE_RO"));
		stubHeader(managerId);
		Evaluation evaluation = evaluation(collabId, managerId);
		when(evaluationRepository.findByIdWithCampaign(evaluationId)).thenReturn(Optional.of(evaluation));

		assertThat(access.requireManagerEvaluation(evaluationId)).isSameAs(evaluation);
	}

	@Test
	void etrangerRefuseSurListeCollaborateur() {
		authenticate(jwtWithUser(strangerId), List.of("ROLE_USER"));
		stubHeader(strangerId);

		assertThatThrownBy(() -> access.assertCanAccessCollaborateurScope(collabId))
				.isInstanceOf(SecurityException.class);
	}

	@Test
	void directionPeutListerNimporteQuelCollaborateur() {
		authenticate(jwtWithRoles("USER", "DIRECTION"), List.of("ROLE_USER", "ROLE_DIRECTION"));

		access.assertCanAccessCollaborateurScope(collabId);
	}

	@Test
	void validationRhRefuseActeurNonOwner() {
		authenticate(jwtWithUser(strangerId), List.of("ROLE_USER"));
		stubHeader(strangerId);
		EvaluationRh rh = new EvaluationRh();
		rh.setCollaborateurIdentifiant(collabId);
		rh.setSuperieurIdentifiant(managerId);
		when(evaluationRhRepository.findById(evaluationId)).thenReturn(Optional.of(rh));

		assertThatThrownBy(() -> access.assertIsCollaborateurOfRh(evaluationId))
				.isInstanceOf(SecurityException.class);
	}

	// --- SCRUM-56 : l'en-tête X-Collaborateur-Id n'est plus une preuve d'identité ---

	@Test
	void collaborateurAvecEnteteDuRoNePeutPasEcrireLesNotesDuRo() {
		// Usurpation visée par l'ADR : le collaborateur envoie la fiche de son RO dans l'en-tête.
		authenticate(jwtWithUser(collabId), List.of("ROLE_USER"));
		stubHeader(managerId);
		when(evaluationRepository.findByIdWithCampaign(evaluationId))
				.thenReturn(Optional.of(evaluation(collabId, managerId)));

		assertThatThrownBy(() -> access.requireManagerEvaluation(evaluationId))
				.isInstanceOf(SecurityException.class);
	}

	@Test
	void collaborateurReconnuParLeCompteMaisEnteteDifferent_403IdentiteIncoherente() {
		UUID compteCollab = UUID.randomUUID();
		authenticate(jwtWithUser(compteCollab), List.of("ROLE_USER"));
		stubHeader(managerId);
		Evaluation evaluation = evaluation(collabId, managerId);
		evaluation.setCollaborateurCompteId(compteCollab);
		evaluation.setSuperieurCompteId(UUID.randomUUID());
		when(evaluationRepository.findByIdWithCampaign(evaluationId)).thenReturn(Optional.of(evaluation));

		assertThatThrownBy(() -> access.requireCollaborateurEvaluation(evaluationId))
				.isInstanceOfSatisfying(EvaluationMetierException.class, e -> {
					assertThat(e.getCode()).isEqualTo("IDENTITE_INCOHERENTE");
					assertThat(e.getHttpStatus()).isEqualTo(403);
				});
		assertThatThrownBy(() -> access.requireParticipantEvaluation(evaluationId))
				.isInstanceOf(EvaluationMetierException.class);
	}

	@Test
	void roReconnuParLeCompteSansEntetePeutNoterEtEnteteIdentiqueAccepte() {
		UUID compteRo = UUID.randomUUID();
		authenticate(jwtWithUser(compteRo), List.of("ROLE_USER", "ROLE_RO"));
		Evaluation evaluation = evaluation(collabId, managerId);
		evaluation.setSuperieurCompteId(compteRo);
		when(evaluationRepository.findByIdWithCampaign(evaluationId)).thenReturn(Optional.of(evaluation));

		assertThat(access.requireManagerEvaluation(evaluationId)).isSameAs(evaluation);

		stubHeader(managerId);
		assertThat(access.requireManagerEvaluation(evaluationId)).isSameAs(evaluation);
	}

	@Test
	void compteDifferentDuSnapshotRefuseMemeAvecLeBonEntete() {
		authenticate(jwtWithUser(strangerId), List.of("ROLE_USER"));
		stubHeader(managerId);
		Evaluation evaluation = evaluation(collabId, managerId);
		evaluation.setSuperieurCompteId(UUID.randomUUID());
		when(evaluationRepository.findByIdWithCampaign(evaluationId)).thenReturn(Optional.of(evaluation));

		assertThatThrownBy(() -> access.requireManagerEvaluation(evaluationId))
				.isInstanceOf(SecurityException.class);
	}

	// --- A1 : identité depuis le JWT, en-tête usurpé refusé ---

	private EvaluationAccessService accesAvecFiche(UUID fiche) {
		return new EvaluationAccessService(evaluationRepository, evaluationRhRepository,
				new IdentiteAppelantResolver(() -> Optional.of(
						new com.hr.evaluation.client.MoiReferentiel(fiche, "Test", "Test"))));
	}

	@Test
	void acteurCourant_enteteUsurpe_403IdentiteIncoherente() {
		UUID compte = UUID.randomUUID();
		authenticate(jwtWithUser(compte), List.of("ROLE_USER"));
		stubHeader(managerId);
		EvaluationAccessService acces = accesAvecFiche(collabId);

		assertThatThrownBy(acces::requireCurrentActorId)
				.isInstanceOfSatisfying(EvaluationMetierException.class, e -> {
					assertThat(e.getCode()).isEqualTo("IDENTITE_INCOHERENTE");
					assertThat(e.getHttpStatus()).isEqualTo(403);
				});
	}

	@Test
	void acteurCourant_sansEntete_ficheDuJwt() {
		authenticate(jwtWithUser(UUID.randomUUID()), List.of("ROLE_USER"));

		assertThat(accesAvecFiche(collabId).requireCurrentActorId()).isEqualTo(collabId);
	}

	@Test
	void acteurCourant_enteteEgalASaFiche_accepte() {
		authenticate(jwtWithUser(UUID.randomUUID()), List.of("ROLE_USER"));
		stubHeader(collabId);

		assertThat(accesAvecFiche(collabId).requireCurrentActorId()).isEqualTo(collabId);
	}

	@Test
	void evaluationRh_enteteDuCollaborateurUsurpeParUnEtranger_403() {
		authenticate(jwtWithUser(strangerId), List.of("ROLE_USER"));
		stubHeader(collabId);
		EvaluationRh rh = new EvaluationRh();
		rh.setCollaborateurIdentifiant(collabId);
		rh.setSuperieurIdentifiant(managerId);
		when(evaluationRhRepository.findById(evaluationId)).thenReturn(Optional.of(rh));
		EvaluationAccessService acces = accesAvecFiche(strangerId);

		assertThatThrownBy(() -> acces.assertIsCollaborateurOfRh(evaluationId))
				.isInstanceOf(EvaluationMetierException.class);
		assertThatThrownBy(() -> acces.assertIsSuperieurOfRh(evaluationId))
				.isInstanceOf(EvaluationMetierException.class);
		assertThatThrownBy(() -> acces.assertCanAccessEvaluationRh(evaluationId))
				.isInstanceOf(EvaluationMetierException.class);
		assertThatThrownBy(() -> acces.assertCanAccessCollaborateurScope(collabId))
				.isInstanceOf(EvaluationMetierException.class);
		assertThatThrownBy(() -> acces.assertCanAccessSuperieurScope(managerId))
				.isInstanceOf(EvaluationMetierException.class);
	}

	@Test
	void evaluationRh_proprieteReconnueParLaFicheDuJwtSansEntete() {
		authenticate(jwtWithUser(UUID.randomUUID()), List.of("ROLE_USER"));
		EvaluationRh rh = new EvaluationRh();
		rh.setCollaborateurIdentifiant(collabId);
		rh.setSuperieurIdentifiant(managerId);
		when(evaluationRhRepository.findById(evaluationId)).thenReturn(Optional.of(rh));
		EvaluationAccessService acces = accesAvecFiche(collabId);

		acces.assertIsCollaborateurOfRh(evaluationId);
		acces.assertCanAccessEvaluationRh(evaluationId);
		assertThatThrownBy(() -> acces.assertIsSuperieurOfRh(evaluationId)).isInstanceOf(SecurityException.class);

		stubHeader(collabId);
		acces.assertIsCollaborateurOfRh(evaluationId);
	}

	private Evaluation evaluation(UUID collaborateur, UUID superieur) {
		Evaluation evaluation = new Evaluation();
		evaluation.setId(evaluationId);
		evaluation.setCollaborateurIdentifiant(collaborateur);
		evaluation.setSuperieurIdentifiant(superieur);
		return evaluation;
	}

	private void stubHeader(UUID collaborateurId) {
		HttpServletRequest request = mock(HttpServletRequest.class);
		org.mockito.Mockito.lenient().when(request.getHeader("X-Collaborateur-Id")).thenReturn(collaborateurId.toString());
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	private void authenticate(Jwt jwt, List<String> roles) {
		var authorities = roles.stream().map(SimpleGrantedAuthority::new).toList();
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(jwt, null, authorities));
	}

	private Jwt jwtWithUser(UUID userId) {
		return Jwt.withTokenValue("token")
				.header("alg", "none")
				.subject(userId.toString())
				.claims(c -> c.putAll(Map.of(
						"identifiant_utilisateur", userId.toString(),
						"roles", List.of("USER"))))
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(3600))
				.build();
	}

	private Jwt jwtWithRoles(String... roles) {
		return Jwt.withTokenValue("token")
				.header("alg", "none")
				.subject(UUID.randomUUID().toString())
				.claim("roles", List.of(roles))
				.claim("identifiant_utilisateur", UUID.randomUUID().toString())
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(3600))
				.build();
	}
}
