package com.hr.presence.service;

import com.hr.presence.client.PresenceWorkflowMoteurClient;
import com.hr.presence.client.PresenceWorkflowMoteurClient.DemarrerInstanceResponse;
import com.hr.presence.client.PresenceWorkflowMoteurClient.EtapeCouranteDto;
import com.hr.presence.client.PresenceWorkflowMoteurClient.IssueDemarrage;
import com.hr.presence.client.PresenceWorkflowMoteurClient.SuiviObjetInterne;
import com.hr.presence.client.ReferentielClient;
import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresenceHeuresSupBeneficiaire;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresenceHeuresSupBeneficiaireRepository;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceWorkflowDemarrageServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-22T09:00:00Z");

	@Mock
	private PresenceDemandeManuelleRepository demandeManuelleRepo;
	@Mock
	private PresenceHeuresSupDemandeRepository heuresSupRepo;
	@Mock
	private PresenceHeuresSupBeneficiaireRepository heuresSupBenefRepo;
	@Mock
	private PresenceSiteRepository siteRepo;
	@Mock
	private FenetreHoraireService fenetreService;
	@Mock
	private PresenceWorkflowMoteurClient moteurClient;
	@Mock
	private PresenceWorkflowEffetService effetService;
	@Mock
	private ReferentielClient referentielClient;
	@Mock
	private PresenceEventPublisher eventPublisher;
	@Mock
	private HeuresSupNotifier heuresSupNotifier;

	private PresenceWorkflowDemarrageService service;
	private PresenceDemandeManuelle manuel;
	private PresenceHeuresSupDemande heuresSup;
	private PresenceHeuresSupBeneficiaire beneficiaire;
	private PresenceSite site;

	private static PlatformTransactionManager txManagerAvecFlag() {
		return new PlatformTransactionManager() {
			@Override
			public TransactionStatus getTransaction(TransactionDefinition definition) {
				TransactionSynchronizationManager.setActualTransactionActive(true);
				return new SimpleTransactionStatus();
			}

			@Override
			public void commit(TransactionStatus status) {
				TransactionSynchronizationManager.setActualTransactionActive(false);
			}

			@Override
			public void rollback(TransactionStatus status) {
				TransactionSynchronizationManager.setActualTransactionActive(false);
			}
		};
	}

	@BeforeEach
	void setUp() {
		service = new PresenceWorkflowDemarrageService(
				demandeManuelleRepo,
				heuresSupRepo,
				heuresSupBenefRepo,
				siteRepo,
				fenetreService,
				moteurClient,
				new PresenceWorkflowVariablesBuilder(),
				effetService,
				referentielClient,
				eventPublisher,
				heuresSupNotifier,
				Clock.fixed(MAINTENANT, ClockConfig.ZONE_TUNIS),
				txManagerAvecFlag());

		UUID siteId = UUID.randomUUID();
		site = new PresenceSite();
		site.setId(siteId);
		site.setLibelle("Site Lac");

		manuel = new PresenceDemandeManuelle();
		manuel.setId(UUID.randomUUID());
		manuel.setNumero("PM-202611-0001");
		manuel.setCollaborateurId(UUID.randomUUID().toString());
		manuel.setCollaborateurCompteId(UUID.randomUUID().toString());
		manuel.setCollaborateurNom("Youssef Ben Amor");
		manuel.setSiteId(siteId);
		manuel.setType(PointageType.ENTREE);
		manuel.setHeureDeclaree(MAINTENANT.minusSeconds(1800));
		manuel.setJustification("Problème réseau au portique");
		manuel.setDistanceMetres(45.0);
		manuel.setStatut(StatutDemandeManuelle.EN_COURS);
		manuel.setWorkflowMode("MOTEUR");
		manuel.setWorkflowEtat(PresenceWorkflowDemarrageService.ETAT_A_DEMARRER);

		heuresSup = new PresenceHeuresSupDemande();
		heuresSup.setId(UUID.randomUUID());
		heuresSup.setNumero("HS-202611-0002");
		heuresSup.setReference("HS-202611-0002");
		heuresSup.setSiteId(siteId);
		heuresSup.setDateDebut(LocalDate.of(2026, 11, 24));
		heuresSup.setDateFin(LocalDate.of(2026, 11, 24));
		heuresSup.setOuvertureAvantMinutes(60);
		heuresSup.setOuvertureApresMinutes(0);
		heuresSup.setDebutPlageTs(MAINTENANT.plusSeconds(86_400));
		heuresSup.setDemandeurFicheId(UUID.randomUUID().toString());
		heuresSup.setDemandeurCompteId(UUID.randomUUID().toString());
		heuresSup.setDemandeurNom("Amel Trabelsi");
		heuresSup.setStatut(StatutHeuresSup.EN_COURS);
		heuresSup.setWorkflowMode("MOTEUR");
		heuresSup.setWorkflowEtat(PresenceWorkflowDemarrageService.ETAT_A_DEMARRER);

		beneficiaire = new PresenceHeuresSupBeneficiaire();
		beneficiaire.setDemandeId(heuresSup.getId());
		beneficiaire.setCollaborateurId(UUID.randomUUID());
		beneficiaire.setNom("Ali");

		lenient().when(demandeManuelleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(heuresSupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(siteRepo.findById(siteId)).thenReturn(Optional.of(site));
		lenient().when(fenetreService.horaires(siteId)).thenReturn(Map.of(
				java.time.DayOfWeek.TUESDAY, new FenetreHoraireService.Horaire(LocalTime.of(8, 0), LocalTime.of(17, 0))));
	}

	@Test
	void demarrerPointageManuel_horsTransaction_rattacheInstanceEtEtape() {
		UUID instanceId = UUID.randomUUID();
		Instant echeance = MAINTENANT.plusSeconds(86_400);
		when(demandeManuelleRepo.findById(manuel.getId())).thenReturn(Optional.of(manuel));
		when(demandeManuelleRepo.findByIdPourDecision(manuel.getId())).thenReturn(Optional.of(manuel));
		when(moteurClient.demarrer(eq("POINTAGE_MANUEL:" + manuel.getId()), any())).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return new IssueDemarrage.Demarre(201, new DemarrerInstanceResponse(
					instanceId, "EN_COURS", null, new EtapeCouranteDto("VALID_RESP", "Validation du responsable", echeance)));
		});

		service.demarrerPointageManuel(manuel.getId());

		assertThat(manuel.getWorkflowInstanceId()).isEqualTo(instanceId);
		assertThat(manuel.getWorkflowEtat()).isEqualTo(PresenceWorkflowDemarrageService.ETAT_DEMARRE);
		assertThat(manuel.getEtapeCouranteLibelle()).isEqualTo("Validation du responsable");
		assertThat(manuel.getEcheanceLe()).isEqualTo(echeance);
	}

	@Test
	void demarrerPointageManuel_instanceExistante_rattacheSansDoublon() {
		UUID instanceId = UUID.randomUUID();
		when(demandeManuelleRepo.findById(manuel.getId())).thenReturn(Optional.of(manuel));
		when(demandeManuelleRepo.findByIdPourDecision(manuel.getId())).thenReturn(Optional.of(manuel));
		when(moteurClient.demarrer(any(), any())).thenReturn(new IssueDemarrage.InstanceExistante());
		when(moteurClient.obtenirParObjet("POINTAGE_MANUEL", manuel.getId())).thenReturn(Optional.of(
				new SuiviObjetInterne(instanceId, "POINTAGE_MANUEL", manuel.getId(), "EN_COURS",
						new EtapeCouranteDto("VALID_RH", "Validation RH", null))));

		service.demarrerPointageManuel(manuel.getId());

		assertThat(manuel.getWorkflowInstanceId()).isEqualTo(instanceId);
		assertThat(manuel.getWorkflowEtat()).isEqualTo(PresenceWorkflowDemarrageService.ETAT_DEMARRE);
		assertThat(manuel.getEtapeCouranteLibelle()).isEqualTo("Validation RH");
	}

	@Test
	void demarrerPointageManuel_dixiemeEchec_repliHeriteEnAttenteEtNotifie() {
		manuel.setWorkflowTentatives(PresenceWorkflowDemarrageService.MAX_TENTATIVES - 1);
		when(demandeManuelleRepo.findById(manuel.getId())).thenReturn(Optional.of(manuel));
		when(demandeManuelleRepo.findByIdPourDecision(manuel.getId())).thenReturn(Optional.of(manuel));
		when(moteurClient.demarrer(any(), any())).thenReturn(new IssueDemarrage.Indisponible("timeout"));

		service.demarrerPointageManuel(manuel.getId());

		assertThat(manuel.getWorkflowMode()).isEqualTo("HERITE");
		assertThat(manuel.getWorkflowEtat()).isEqualTo(PresenceWorkflowDemarrageService.ETAT_ECHEC);
		assertThat(manuel.getStatut()).isEqualTo(StatutDemandeManuelle.EN_ATTENTE);
		verify(eventPublisher).notifier(eq(PresenceEventPublisher.DESTINATAIRE_RH), anyString(), anyString(), any());
	}

	@Test
	void demarrerHeuresSup_directeEffetImmediat_appliqueLocalementApprover() {
		UUID instanceId = UUID.randomUUID();
		heuresSup.setCreeeValidee(true);
		when(heuresSupRepo.findById(heuresSup.getId())).thenReturn(Optional.of(heuresSup));
		when(heuresSupRepo.findByIdPourDecision(heuresSup.getId())).thenReturn(Optional.of(heuresSup));
		when(heuresSupBenefRepo.findByDemandeId(heuresSup.getId())).thenReturn(List.of(beneficiaire));
		when(moteurClient.demarrer(eq("HEURES_SUP:" + heuresSup.getId()), any())).thenAnswer(inv -> {
			PresenceWorkflowMoteurClient.DemarrerInstanceRequest req = inv.getArgument(1);
			assertThat(req.beneficiaires()).containsExactly(beneficiaire.getCollaborateurId());
			assertThat(req.variables()).containsEntry("cree_par_rh", true);
			return new IssueDemarrage.Demarre(201, new DemarrerInstanceResponse(
					instanceId, "APPROUVEE", "APPROUVER", null));
		});

		service.demarrerHeuresSup(heuresSup.getId());

		verify(effetService).appliquer(argThat(r ->
				("demarrage:" + instanceId).equals(r.cleIdempotence())
						&& "HEURES_SUP".equals(r.objetType())
						&& "APPROUVER".equals(r.effet())));
	}

	@Test
	void demarrerHeuresSup_directeEnRepliHerite_passeValideeEtJamaisEnAttente() {
		heuresSup.setCreeeValidee(true);
		when(heuresSupRepo.findById(heuresSup.getId())).thenReturn(Optional.of(heuresSup));
		when(heuresSupRepo.findByIdPourDecision(heuresSup.getId())).thenReturn(Optional.of(heuresSup));
		when(heuresSupBenefRepo.findByDemandeId(heuresSup.getId())).thenReturn(List.of(beneficiaire));
		when(moteurClient.demarrer(any(), any())).thenReturn(new IssueDemarrage.WorkflowAbsent());

		service.demarrerHeuresSup(heuresSup.getId());

		assertThat(heuresSup.getWorkflowMode()).isEqualTo("HERITE");
		assertThat(heuresSup.getStatut()).isEqualTo(StatutHeuresSup.VALIDEE);
		assertThat(heuresSup.getDecideParNom()).isEqualTo("Amel Trabelsi");
		verify(heuresSupNotifier).creeeValidee(eq(heuresSup), anyString(), any());
		verify(heuresSupNotifier, never()).creeeEnAttente(any(), anyString());
	}

	@Test
	void demarrerHeuresSup_roEnRepliHerite_passeEnAttenteEtNotifieRh() {
		heuresSup.setCreeeValidee(false);
		when(heuresSupRepo.findById(heuresSup.getId())).thenReturn(Optional.of(heuresSup));
		when(heuresSupRepo.findByIdPourDecision(heuresSup.getId())).thenReturn(Optional.of(heuresSup));
		when(heuresSupBenefRepo.findByDemandeId(heuresSup.getId())).thenReturn(List.of(beneficiaire));
		when(moteurClient.demarrer(any(), any())).thenReturn(new IssueDemarrage.ModeHerite());

		service.demarrerHeuresSup(heuresSup.getId());

		assertThat(heuresSup.getWorkflowMode()).isEqualTo("HERITE");
		assertThat(heuresSup.getStatut()).isEqualTo(StatutHeuresSup.EN_ATTENTE);
		verify(heuresSupNotifier).creeeEnAttente(eq(heuresSup), anyString());
	}

	@Test
	void demarrerPointageManuel_echecSousSeuil_nAppellePasReferentielValideur() {
		manuel.setWorkflowTentatives(0);
		when(demandeManuelleRepo.findById(manuel.getId())).thenReturn(Optional.of(manuel));
		when(demandeManuelleRepo.findByIdPourDecision(manuel.getId())).thenReturn(Optional.of(manuel));
		when(moteurClient.demarrer(any(), any())).thenReturn(new IssueDemarrage.Indisponible("timeout"));

		org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt
				.withTokenValue("tok").header("alg", "none").claim("sub", "emp").build();
		org.springframework.security.core.context.SecurityContextHolder.getContext()
				.setAuthentication(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt));
		try {
			service.demarrerPointageManuel(manuel.getId());
		} finally {
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}

		assertThat(manuel.getWorkflowTentatives()).isEqualTo(1);
		assertThat(manuel.getWorkflowMode()).isEqualTo("MOTEUR");
		verify(referentielClient, never()).monValideurPointage();
	}

	@Test
	void demarrerPointageManuel_dixiemeEchecAvecJwt_resoutValideurHorsTransaction() {
		manuel.setWorkflowTentatives(PresenceWorkflowDemarrageService.MAX_TENTATIVES - 1);
		UUID valideurFiche = UUID.randomUUID();
		UUID valideurCompte = UUID.randomUUID();
		when(demandeManuelleRepo.findById(manuel.getId())).thenReturn(Optional.of(manuel));
		when(demandeManuelleRepo.findByIdPourDecision(manuel.getId())).thenReturn(Optional.of(manuel));
		when(moteurClient.demarrer(any(), any())).thenReturn(new IssueDemarrage.Indisponible("timeout"));
		when(referentielClient.monValideurPointage()).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return new ReferentielClient.ValideurPointage(
					UUID.fromString(manuel.getCollaborateurId()),
					UUID.fromString(manuel.getCollaborateurCompteId()),
					manuel.getCollaborateurNom(),
					valideurFiche, valideurCompte, "Omar RO");
		});

		org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt
				.withTokenValue("tok").header("alg", "none").claim("sub", "emp").build();
		org.springframework.security.core.context.SecurityContextHolder.getContext()
				.setAuthentication(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt));
		try {
			service.demarrerPointageManuel(manuel.getId());
		} finally {
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}

		assertThat(manuel.getWorkflowMode()).isEqualTo("HERITE");
		assertThat(manuel.getWorkflowEtat()).isEqualTo(PresenceWorkflowDemarrageService.ETAT_ECHEC);
		assertThat(manuel.getValideurCompteId()).isEqualTo(valideurCompte.toString());
		verify(referentielClient).monValideurPointage();
	}
}
