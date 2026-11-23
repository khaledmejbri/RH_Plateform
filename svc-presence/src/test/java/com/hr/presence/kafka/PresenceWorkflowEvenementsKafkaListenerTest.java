package com.hr.presence.kafka;

import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceWorkflowEvenementsKafkaListenerTest {

	@Mock
	private PresenceDemandeManuelleRepository demandeManuelleRepo;
	@Mock
	private PresenceHeuresSupDemandeRepository heuresSupRepo;

	private PresenceWorkflowEvenementsKafkaListener listener;

	@BeforeEach
	void setUp() {
		TransactionTemplate tx = new TransactionTemplate() {
			@Override
			public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
				return action.doInTransaction(null);
			}
		};
		listener = new PresenceWorkflowEvenementsKafkaListener(demandeManuelleRepo, heuresSupRepo, tx);
		lenient().when(demandeManuelleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(heuresSupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void projettePointageManuelEtHeuresSupSansToucherStatut() {
		UUID pmId = UUID.randomUUID();
		UUID instPm = UUID.randomUUID();
		PresenceDemandeManuelle pm = new PresenceDemandeManuelle();
		pm.setId(pmId);
		pm.setStatut(StatutDemandeManuelle.EN_COURS);
		pm.setWorkflowMode("MOTEUR");
		pm.setWorkflowEtat("A_DEMARRER");
		when(demandeManuelleRepo.findByIdPourDecision(pmId)).thenReturn(Optional.of(pm));

		String jsonPm = "{\"objet_type\":\"POINTAGE_MANUEL\",\"objet_id\":\"" + pmId + "\","
				+ "\"instance_id\":\"" + instPm + "\",\"statut_instance\":\"EN_COURS\","
				+ "\"etape_libelle\":\"Validation du responsable\","
				+ "\"echeance_le\":\"2026-11-23T10:00:00Z\",\"emis_le\":\"2026-11-22T10:00:00Z\"}";
		listener.onWorkflowEvenement(new ConsumerRecord<>("rh.workflow.evenements", 0, 0, pmId.toString(), jsonPm));

		assertThat(pm.getStatut()).isEqualTo(StatutDemandeManuelle.EN_COURS);
		assertThat(pm.getWorkflowInstanceId()).isEqualTo(instPm);
		assertThat(pm.getWorkflowEtat()).isEqualTo("DEMARRE");
		assertThat(pm.getEtapeCouranteLibelle()).isEqualTo("Validation du responsable");
		assertThat(pm.getEcheanceLe()).isEqualTo(Instant.parse("2026-11-23T10:00:00Z"));

		// Événement plus ancien ignoré
		String jsonAncien = "{\"objet_type\":\"POINTAGE_MANUEL\",\"objet_id\":\"" + pmId + "\","
				+ "\"instance_id\":\"" + instPm + "\",\"statut_instance\":\"EN_COURS\","
				+ "\"etape_libelle\":\"Ancienne étape\",\"emis_le\":\"2026-11-22T09:00:00Z\"}";
		listener.onWorkflowEvenement(new ConsumerRecord<>("rh.workflow.evenements", 0, 1, pmId.toString(), jsonAncien));
		assertThat(pm.getEtapeCouranteLibelle()).isEqualTo("Validation du responsable");

		// Heures sup terminé → efface étape et échéance sans changer statut
		UUID hsId = UUID.randomUUID();
		UUID instHs = UUID.randomUUID();
		PresenceHeuresSupDemande hs = new PresenceHeuresSupDemande();
		hs.setId(hsId);
		hs.setStatut(StatutHeuresSup.EN_COURS);
		hs.setWorkflowMode("MOTEUR");
		hs.setEtapeCouranteLibelle("Validation RH");
		hs.setEcheanceLe(Instant.parse("2026-11-23T10:00:00Z"));
		when(heuresSupRepo.findByIdPourDecision(hsId)).thenReturn(Optional.of(hs));

		String jsonHs = "{\"objet_type\":\"HEURES_SUP\",\"objet_id\":\"" + hsId + "\","
				+ "\"instance_id\":\"" + instHs + "\",\"statut_instance\":\"APPROUVEE\","
				+ "\"etape_libelle\":\"Validation RH\",\"emis_le\":\"2026-11-22T11:00:00Z\"}";
		listener.onWorkflowEvenement(new ConsumerRecord<>("rh.workflow.evenements", 0, 2, hsId.toString(), jsonHs));

		assertThat(hs.getStatut()).isEqualTo(StatutHeuresSup.EN_COURS);
		assertThat(hs.getEtapeCouranteLibelle()).isNull();
		assertThat(hs.getEcheanceLe()).isNull();
	}

	@Test
	void autreObjetTypeIgnore() {
		UUID id = UUID.randomUUID();
		String json = "{\"objet_type\":\"CONGE\",\"objet_id\":\"" + id + "\",\"emis_le\":\"2026-11-22T10:00:00Z\"}";
		listener.onWorkflowEvenement(new ConsumerRecord<>("rh.workflow.evenements", 0, 3, id.toString(), json));

		verify(demandeManuelleRepo, never()).findByIdPourDecision(any());
		verify(heuresSupRepo, never()).findByIdPourDecision(any());
	}
}
