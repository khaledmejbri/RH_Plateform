package com.hr.referentiel.kafka;

import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.service.SocieteDefautResolver;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEvenementsKafkaListenerTest {

	private static final UUID SOCIETE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Mock
	private DemandeAdministrativeRhRepository demandeRepo;
	@Mock
	private SocieteDefautResolver societeDefautResolver;

	private WorkflowEvenementsKafkaListener listener;
	private DemandeAdministrativeRh demande;

	@BeforeEach
	void setUp() {
		TransactionTemplate tx = new TransactionTemplate(new PlatformTransactionManager() {
			@Override
			public TransactionStatus getTransaction(TransactionDefinition definition) {
				return new SimpleTransactionStatus();
			}

			@Override
			public void commit(TransactionStatus status) {
			}

			@Override
			public void rollback(TransactionStatus status) {
			}
		});
		listener = new WorkflowEvenementsKafkaListener(demandeRepo, societeDefautResolver, tx);

		demande = new DemandeAdministrativeRh();
		demande.setId(UUID.randomUUID());
		demande.setTypeDemande(TypeDemandeAdministrativeRh.CONGE);
		demande.setStatut(StatutDemandeAdministrativeRh.EN_COURS);
		demande.setWorkflowMode("MOTEUR");
		demande.setWorkflowEtat("A_DEMARRER");
	}

	private ConsumerRecord<String, String> record(String json) {
		return new ConsumerRecord<>("rh.workflow.evenements", 0, 0L, "k", json);
	}

	@Test
	void projectionMetAJourEtapeEcheanceEtRattacheInstanceSansModifierStatut() {
		UUID instanceId = UUID.randomUUID();
		Instant emis = Instant.parse("2026-11-21T10:00:00Z");
		Instant echeance = Instant.parse("2026-11-24T17:00:00Z");
		when(societeDefautResolver.resoudrePourTransitionAvecWarn(any())).thenReturn(Optional.of(SOCIETE_ID));
		when(demandeRepo.findByIdForUpdate(demande.getId())).thenReturn(Optional.of(demande));

		String payload = """
				{"evenement":"ETAPE_ACTIVEE","instance_id":"%s","objet_type":"CONGE","objet_id":"%s",
				 "statut_instance":"EN_COURS","etape_code":"RH","etape_libelle":"Validation RH",
				 "echeance_le":"%s","emis_le":"%s"}
				""".formatted(instanceId, demande.getId(), echeance, emis);

		listener.onWorkflowEvenement(record(payload));

		assertThat(demande.getWorkflowInstanceId()).isEqualTo(instanceId);
		assertThat(demande.getWorkflowEtat()).isEqualTo("DEMARRE");
		assertThat(demande.getWorkflowEtapeLibelle()).isEqualTo("Validation RH");
		assertThat(demande.getWorkflowEcheanceLe()).isEqualTo(echeance);
		assertThat(demande.getWorkflowProjectionLe()).isEqualTo(emis);
		// Statut jamais modifié par le consommateur
		assertThat(demande.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_COURS);
		verify(demandeRepo, times(1)).save(demande);
	}

	@Test
	void evenementPlusAncienOuIdentiqueEstIgnore() {
		Instant t2 = Instant.parse("2026-11-21T12:00:00Z");
		Instant t1 = Instant.parse("2026-11-21T11:00:00Z");
		demande.setWorkflowEtapeLibelle("Validation RH");
		demande.setWorkflowProjectionLe(t2);
		when(societeDefautResolver.resoudrePourTransitionAvecWarn(any())).thenReturn(Optional.of(SOCIETE_ID));
		when(demandeRepo.findByIdForUpdate(demande.getId())).thenReturn(Optional.of(demande));

		String ancien = """
				{"evenement":"ETAPE_ACTIVEE","instance_id":"%s","objet_type":"CONGE","objet_id":"%s",
				 "statut_instance":"EN_COURS","etape_libelle":"Validation responsable","emis_le":"%s"}
				""".formatted(UUID.randomUUID(), demande.getId(), t1);
		String identique = """
				{"evenement":"ETAPE_ACTIVEE","instance_id":"%s","objet_type":"CONGE","objet_id":"%s",
				 "statut_instance":"EN_COURS","etape_libelle":"Autre étape","emis_le":"%s"}
				""".formatted(UUID.randomUUID(), demande.getId(), t2);

		listener.onWorkflowEvenement(record(ancien));
		listener.onWorkflowEvenement(record(identique));

		assertThat(demande.getWorkflowEtapeLibelle()).isEqualTo("Validation RH");
		verify(demandeRepo, never()).save(any());
	}

	@Test
	void instanceTermineeVideEcheanceEtEtapeSansModifierStatut() {
		Instant emis = Instant.parse("2026-11-21T15:00:00Z");
		demande.setWorkflowEtapeLibelle("Validation RH");
		demande.setWorkflowEcheanceLe(Instant.parse("2026-11-24T17:00:00Z"));
		when(societeDefautResolver.resoudrePourTransitionAvecWarn(any())).thenReturn(Optional.of(SOCIETE_ID));
		when(demandeRepo.findByIdForUpdate(demande.getId())).thenReturn(Optional.of(demande));

		String fin = """
				{"evenement":"INSTANCE_TERMINEE","instance_id":"%s","objet_type":"CONGE","objet_id":"%s",
				 "statut_instance":"APPROUVEE","etape_libelle":null,"echeance_le":null,"emis_le":"%s"}
				""".formatted(UUID.randomUUID(), demande.getId(), emis);

		listener.onWorkflowEvenement(record(fin));

		assertThat(demande.getWorkflowEtapeLibelle()).isNull();
		assertThat(demande.getWorkflowEcheanceLe()).isNull();
		assertThat(demande.getWorkflowProjectionLe()).isEqualTo(emis);
		// Le consommateur ne change jamais le statut (seul l'endpoint d'effet le fait)
		assertThat(demande.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_COURS);
	}

	@Test
	void objetHorsM01EstIgnore() {
		String doc = """
				{"evenement":"ETAPE_ACTIVEE","instance_id":"%s","objet_type":"DEMANDE_DOCUMENT","objet_id":"%s",
				 "statut_instance":"EN_COURS","etape_libelle":"Traitement RH","emis_le":"2026-11-21T15:00:00Z"}
				""".formatted(UUID.randomUUID(), UUID.randomUUID());

		listener.onWorkflowEvenement(record(doc));

		verify(demandeRepo, never()).findByIdForUpdate(any());
	}
}
