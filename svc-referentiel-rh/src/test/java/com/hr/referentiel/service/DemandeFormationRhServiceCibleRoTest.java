package com.hr.referentiel.service;

import com.hr.referentiel.domain.CibleDemandeFormationRh;
import com.hr.referentiel.domain.OrigineDemandeFormationRh;
import com.hr.referentiel.dto.DemandeFormationCreationRequest;
import com.hr.referentiel.dto.DemandeFormationRhResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeFormationRh;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeFormationRhRepository;
import com.hr.referentiel.repository.FormationWorkflowHistoryRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SCRUM-14 — § 10.1 / § 20 : un RO demande une formation « pour son unité / ses collaborateurs ». */
@ExtendWith(MockitoExtension.class)
class DemandeFormationRhServiceCibleRoTest {

	@Mock
	private DemandeFormationRhRepository demandeFormationRepository;
	@Mock
	private CollaborateurRepository collaborateurRepository;
	@Mock
	private UniteOrganisationRepository uniteRepository;
	@Mock
	private CollaborateurConnecteService collaborateurConnecteService;
	@Mock
	private RhNotificationPublisher notificationPublisher;
	@Mock
	private FormationWorkflowHistoryRepository workflowHistoryRepository;

	@InjectMocks
	private DemandeFormationRhService service;

	private Jwt jwt;
	private UniteOrganisation uniteRo;
	private Collaborateur ro;

	@BeforeEach
	void setUp() {
		jwt = Jwt.withTokenValue("t").header("alg", "none").subject("RO-1")
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
		uniteRo = new UniteOrganisation();
		uniteRo.setId(UUID.randomUUID());
		uniteRo.setLibelle("Unité Nord");
		ro = new Collaborateur();
		ro.setId(UUID.randomUUID());
		ro.setPrenom("Omar");
		ro.setNom("Test");
		ro.setProfilAcces("RO");
		ro.setUnite(uniteRo);
		when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(ro);
		when(collaborateurRepository.findDetailById(ro.getId())).thenReturn(Optional.of(ro));
	}

	private DemandeFormationCreationRequest requete() {
		DemandeFormationCreationRequest req = new DemandeFormationCreationRequest();
		req.setTypeFormation("Sécurité");
		req.setOrganisme("CNFCPP");
		req.setDureeHeures(14);
		req.setObjectifsPedagogiques("Espace confiné");
		req.setJustification("HSE");
		return req;
	}

	@Test
	@DisplayName("✅ RO cible sa propre unité → cible UNITE, origine RO")
	void roCibleSaPropreUnite() {
		when(demandeFormationRepository.save(any(DemandeFormationRh.class))).thenAnswer(i -> i.getArgument(0));
		DemandeFormationCreationRequest req = requete();
		req.setUniteCibleIdentifiant(uniteRo.getId());

		DemandeFormationRhResponse r = service.creer(req, jwt);

		assertThat(r.getCible()).isEqualTo(CibleDemandeFormationRh.UNITE);
		assertThat(r.getOrigine()).isEqualTo(OrigineDemandeFormationRh.RESPONSABLE_OPERATIONNEL);
		assertThat(r.getUniteCibleIdentifiant()).isEqualTo(uniteRo.getId());
	}

	@Test
	@DisplayName("❌ RO cible une autre unité → 403 UNITE_CIBLE_HORS_PERIMETRE")
	void roCibleAutreUniteRefuse() {
		DemandeFormationCreationRequest req = requete();
		req.setUniteCibleIdentifiant(UUID.randomUUID());

		assertThatThrownBy(() -> service.creer(req, jwt))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(e -> {
					ReferentielMetierException ex = (ReferentielMetierException) e;
					assertThat(ex.getHttpStatus()).isEqualTo(403);
					assertThat(ex.getCode()).isEqualTo("UNITE_CIBLE_HORS_PERIMETRE");
				});
		verify(demandeFormationRepository, never()).save(any());
	}

	@Test
	@DisplayName("❌ RO envoie à la fois une unité et une liste nominative → 400")
	void roDeuxCiblesRefuse() {
		DemandeFormationCreationRequest req = requete();
		req.setUniteCibleIdentifiant(uniteRo.getId());
		req.setCollaborateursCiblesIdentifiants(Set.of(UUID.randomUUID()));

		assertThatThrownBy(() -> service.creer(req, jwt)).isInstanceOf(IllegalArgumentException.class);
		verify(demandeFormationRepository, never()).save(any());
	}

	@Test
	@DisplayName("❌ RO sans aucune cible → 400")
	void roSansCibleRefuse() {
		assertThatThrownBy(() -> service.creer(requete(), jwt)).isInstanceOf(IllegalArgumentException.class);
		verify(demandeFormationRepository, never()).save(any());
	}
}
