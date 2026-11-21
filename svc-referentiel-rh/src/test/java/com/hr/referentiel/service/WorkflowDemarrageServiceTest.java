package com.hr.referentiel.service;

import com.hr.referentiel.client.WorkflowMoteurClient;
import com.hr.referentiel.client.WorkflowMoteurClient.DemarrerInstanceResponse;
import com.hr.referentiel.client.WorkflowMoteurClient.EtapeCouranteDto;
import com.hr.referentiel.client.WorkflowMoteurClient.IssueDemarrage;
import com.hr.referentiel.client.WorkflowMoteurClient.SuiviObjetInterne;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.WorkflowEffetDtos.RequeteEffet;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdminWorkflowHistoryRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowDemarrageServiceTest {

	@Mock
	private DemandeAdministrativeRhRepository demandeRepo;
	@Mock
	private CollaborateurRepository collaborateurRepo;
	@Mock
	private DemandeAdminWorkflowHistoryRepository historyRepo;
	@Mock
	private WorkflowMoteurClient moteurClient;
	@Mock
	private WorkflowVariablesBuilder variablesBuilder;
	@Mock
	private WorkflowEffetService effetService;
	@Mock
	private RhNotificationPublisher notificationPublisher;

	private WorkflowDemarrageService service;
	private DemandeAdministrativeRh demande;
	private Collaborateur manager;

	/** Gestionnaire de transactions minimal qui active/désactive réellement {@link TransactionSynchronizationManager}. */
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
		service = new WorkflowDemarrageService(
				demandeRepo, collaborateurRepo, historyRepo, moteurClient,
				variablesBuilder, effetService, notificationPublisher, txManagerAvecFlag());

		manager = new Collaborateur();
		manager.setId(UUID.randomUUID());
		manager.setPrenom("Omar");
		manager.setNom("Chef");
		manager.setStatut("ACTIF");

		UniteOrganisation unite = new UniteOrganisation();
		unite.setId(UUID.randomUUID());
		unite.setManager(manager);

		Collaborateur demandeur = new Collaborateur();
		demandeur.setId(UUID.randomUUID());
		demandeur.setCompteUtilisateurId(UUID.randomUUID());
		demandeur.setPrenom("Leila");
		demandeur.setNom("Collab");
		demandeur.setStatut("ACTIF");
		demandeur.setUnite(unite);

		demande = new DemandeAdministrativeRh();
		demande.setId(UUID.randomUUID());
		demande.setNumero("DEM-202611-0042");
		demande.setTypeDemande(TypeDemandeAdministrativeRh.CONGE);
		demande.setDemandeur(demandeur);
		demande.setStatut(StatutDemandeAdministrativeRh.EN_COURS);
		demande.setWorkflowMode("MOTEUR");
		demande.setWorkflowEtat("A_DEMARRER");
		demande.setJoursDecomptes(new BigDecimal("2.0"));
		demande.setPeriodeDebut(LocalDate.of(2026, 12, 10));
		demande.setPeriodeFin(LocalDate.of(2026, 12, 11));
		demande.setContenu(new HashMap<>(Map.of("type_conge", "ANNUEL")));

		when(demandeRepo.findByIdAvecDemandeur(demande.getId())).thenReturn(Optional.of(demande));
		when(demandeRepo.findByIdForUpdate(demande.getId())).thenReturn(Optional.of(demande));
		when(variablesBuilder.construire(demande)).thenReturn(Map.of("type_conge", "ANNUEL"));
		when(variablesBuilder.sousType(demande)).thenReturn("ANNUEL");
	}

	@Test
	void demarrage201RattacheInstanceEtAppelleHttpHorsTransaction() {
		UUID instanceId = UUID.randomUUID();
		Instant echeance = Instant.parse("2026-12-01T17:00:00Z");
		when(moteurClient.demarrer(eq("CONGE:" + demande.getId()), any())).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive())
					.as("Aucun appel HTTP ne doit avoir lieu dans une transaction active")
					.isFalse();
			return new IssueDemarrage.Demarre(201, new DemarrerInstanceResponse(
					instanceId, "EN_COURS", null, new EtapeCouranteDto("RO", "Validation responsable", echeance)));
		});

		service.demarrer(demande.getId());

		assertThat(demande.getWorkflowInstanceId()).isEqualTo(instanceId);
		assertThat(demande.getWorkflowEtat()).isEqualTo("DEMARRE");
		assertThat(demande.getWorkflowEtapeLibelle()).isEqualTo("Validation responsable");
		assertThat(demande.getWorkflowEcheanceLe()).isEqualTo(echeance);
		verify(effetService, never()).appliquer(any());
	}

	@Test
	void demarrageSurDemandeAnnuleePendantLAppelAnnuleLInstanceOrphelineHorsTransaction() {
		UUID instanceId = UUID.randomUUID();
		when(moteurClient.demarrer(eq("CONGE:" + demande.getId()), any())).thenAnswer(inv -> {
			// le demandeur annule pendant que le moteur crée l'instance
			demande.setStatut(StatutDemandeAdministrativeRh.ANNULEE);
			return new IssueDemarrage.Demarre(201, new DemarrerInstanceResponse(
					instanceId, "EN_COURS", "APPROUVER", null));
		});
		when(moteurClient.annuler(eq(instanceId), any(), any())).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return com.hr.referentiel.client.WorkflowMoteurClient.IssueAnnulation.ANNULEE;
		});

		service.demarrer(demande.getId());

		verify(moteurClient).annuler(eq(instanceId), any(), any());
		verify(effetService, never()).appliquer(any());
		assertThat(demande.getWorkflowInstanceId()).isNull();
	}

	@Test
	void demarrage200AvecEffetImmediatAppliqueLocalementAvecCleDemarrage() {
		UUID instanceId = UUID.randomUUID();
		when(moteurClient.demarrer(eq("CONGE:" + demande.getId()), any()))
				.thenReturn(new IssueDemarrage.Demarre(200, new DemarrerInstanceResponse(
						instanceId, "APPROUVEE", "APPROUVER", null)));

		service.demarrer(demande.getId());

		assertThat(demande.getWorkflowInstanceId()).isEqualTo(instanceId);
		assertThat(demande.getWorkflowEtat()).isEqualTo("DEMARRE");
		ArgumentCaptor<RequeteEffet> cap = ArgumentCaptor.forClass(RequeteEffet.class);
		verify(effetService).appliquer(cap.capture());
		assertThat(cap.getValue().cleIdempotence()).isEqualTo("demarrage:" + instanceId);
		assertThat(cap.getValue().effet()).isEqualTo("APPROUVER");
	}

	@Test
	void demarrage409InstanceExistanteRelitEtRattacheHorsTransaction() {
		UUID instanceId = UUID.randomUUID();
		when(moteurClient.demarrer(any(), any())).thenReturn(new IssueDemarrage.InstanceExistante());
		when(moteurClient.obtenirParObjet("CONGE", demande.getId())).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return Optional.of(new SuiviObjetInterne(
					instanceId, "CONGE", demande.getId(), "EN_COURS",
					new EtapeCouranteDto("RH", "Validation RH", null)));
		});

		service.demarrer(demande.getId());

		assertThat(demande.getWorkflowInstanceId()).isEqualTo(instanceId);
		assertThat(demande.getWorkflowEtat()).isEqualTo("DEMARRE");
		assertThat(demande.getWorkflowEtapeLibelle()).isEqualTo("Validation RH");
	}

	@Test
	void instanceExistanteSurDemandeAnnuleeEstAnnuleeHorsTransaction() {
		UUID instanceId = UUID.randomUUID();
		when(moteurClient.demarrer(any(), any())).thenAnswer(inv -> {
			demande.setStatut(StatutDemandeAdministrativeRh.ANNULEE);
			return new IssueDemarrage.InstanceExistante();
		});
		when(moteurClient.obtenirParObjet("CONGE", demande.getId())).thenReturn(Optional.of(new SuiviObjetInterne(
				instanceId, "CONGE", demande.getId(), "EN_COURS", new EtapeCouranteDto("RO", "Validation responsable", null))));
		when(moteurClient.annuler(eq(instanceId), any(), any())).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return com.hr.referentiel.client.WorkflowMoteurClient.IssueAnnulation.ANNULEE;
		});

		service.demarrer(demande.getId());

		verify(moteurClient).annuler(eq(instanceId), any(), any());
		assertThat(demande.getWorkflowInstanceId()).isNull();
	}

	@Test
	void demarrage409ModeHeriteReplieSurCircuitHeriteEtNotifie() {
		when(moteurClient.demarrer(any(), any())).thenReturn(new IssueDemarrage.ModeHerite());
		when(collaborateurRepo.findDetailById(demande.getDemandeur().getId()))
				.thenReturn(Optional.of(demande.getDemandeur()));

		service.demarrer(demande.getId());

		assertThat(demande.getWorkflowMode()).isEqualTo("HERITE");
		assertThat(demande.getWorkflowEtat()).isNull();
		assertThat(demande.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR);
		assertThat(demande.getValideurAttendu()).isEqualTo(manager);
		verify(notificationPublisher).notifierDemandeRecue(
				demande.getDemandeur(), "CONGE", demande.getId(), demande.getNumero(), manager);
		verify(notificationPublisher, never()).notifierRepliWorkflowRh(any(), any(), any());
	}

	@Test
	void demarrage422WorkflowAbsentReplieSurCircuitHerite() {
		when(moteurClient.demarrer(any(), any())).thenReturn(new IssueDemarrage.WorkflowAbsent());
		when(collaborateurRepo.findDetailById(demande.getDemandeur().getId()))
				.thenReturn(Optional.of(demande.getDemandeur()));

		service.demarrer(demande.getId());

		assertThat(demande.getWorkflowMode()).isEqualTo("HERITE");
		assertThat(demande.getWorkflowEtat()).isNull();
		assertThat(demande.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR);
	}

	@Test
	void demarrage503IncrementeTentativesSansRepliAvantDixiemeEchec() {
		demande.setWorkflowTentatives(3);
		when(moteurClient.demarrer(any(), any())).thenReturn(new IssueDemarrage.Indisponible("503"));

		service.demarrer(demande.getId());

		assertThat(demande.getWorkflowTentatives()).isEqualTo(4);
		assertThat(demande.getWorkflowMode()).isEqualTo("MOTEUR");
		assertThat(demande.getWorkflowEtat()).isEqualTo("A_DEMARRER");
		verify(notificationPublisher, never()).notifierRepliWorkflowRh(any(), any(), any());
	}

	@Test
	void demarrageDixiemeEchecReplieHeriteGardeEtatEchecEtNotifieRh() {
		demande.setWorkflowTentatives(9);
		when(moteurClient.demarrer(any(), any())).thenReturn(new IssueDemarrage.Indisponible("503"));
		when(collaborateurRepo.findDetailById(demande.getDemandeur().getId()))
				.thenReturn(Optional.of(demande.getDemandeur()));

		service.demarrer(demande.getId());

		assertThat(demande.getWorkflowTentatives()).isEqualTo(10);
		assertThat(demande.getWorkflowMode()).isEqualTo("HERITE");
		assertThat(demande.getWorkflowEtat()).isEqualTo("ECHEC");
		assertThat(demande.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR);
		verify(notificationPublisher).notifierDemandeRecue(
				demande.getDemandeur(), "CONGE", demande.getId(), demande.getNumero(), manager);
		verify(notificationPublisher).notifierRepliWorkflowRh(
				demande.getDemandeur(), demande.getId(), demande.getNumero());
	}
}
