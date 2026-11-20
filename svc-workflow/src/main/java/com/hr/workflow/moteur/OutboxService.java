package com.hr.workflow.moteur;

import com.hr.workflow.config.KafkaWorkflowConfig;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfOutbox;
import com.hr.workflow.repository.WfOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Écriture transactionnelle dans la table {@code wf_outbox} (§ 9.6.1, § 9.8, D-W3-01).
 * Exige une transaction active ({@link Propagation#MANDATORY}).
 */
@Service
public class OutboxService {

	private final WfOutboxRepository outboxRepository;
	private final JsonMapper jsonMapper;
	private final Clock clock;

	public OutboxService(WfOutboxRepository outboxRepository, JsonMapper jsonMapper, Clock clock) {
		this.outboxRepository = outboxRepository;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void publierEvenementWorkflow(
			UUID evenementId,
			String evenement,
			WfInstance instance,
			String etapeCode,
			String etapeLibelle,
			Instant echeanceLe) {
		Instant maintenant = clock.instant();
		Map<String, Object> charge = new LinkedHashMap<>();
		charge.put("evenement_id", evenementId.toString());
		charge.put("evenement", evenement);
		charge.put("instance_id", instance.getId().toString());
		charge.put("objet_type", instance.getObjetType());
		charge.put("objet_id", instance.getObjetId().toString());
		charge.put("statut_instance", instance.getStatut().name());
		charge.put("etape_code", etapeCode);
		charge.put("etape_libelle", etapeLibelle);
		charge.put("echeance_le", echeanceLe != null ? echeanceLe.toString() : null);
		charge.put("emis_le", maintenant.toString());

		enregistrer(KafkaWorkflowConfig.SUJET_WORKFLOW_EVENEMENTS, instance.getId().toString(), charge, maintenant);
	}

	/**
	 * Écrit une notification au format {@code NotificationMessage} de {@code svc-notification},
	 * sans {@code societe_id} (D-W3-01), avec {@code notification_id} déterministe (C-06).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void publierNotification(
			UUID evenementId,
			String recipientCompteId,
			String canal,
			String evenementNotif,
			String sujet,
			String contenu,
			String objetType,
			UUID objetId,
			String objetNumero) {
		if (recipientCompteId == null || recipientCompteId.isBlank()) {
			return;
		}
		Instant maintenant = clock.instant();
		String notifId = notificationIdDeterministe(evenementId, recipientCompteId);
		String typeCanal = ("BOTH".equalsIgnoreCase(canal)) ? "BOTH" : "WEBSOCKET";

		Map<String, Object> charge = new LinkedHashMap<>();
		charge.put("type", typeCanal);
		charge.put("recipient", recipientCompteId);
		charge.put("subject", sujet);
		charge.put("content", contenu);
		charge.put("notification_id", notifId);
		charge.put("evenement", evenementNotif);
		charge.put("objet_type", objetType);
		charge.put("objet_id", objetId != null ? objetId.toString() : null);
		charge.put("objet_numero", objetNumero);
		charge.put("emis_le", maintenant.toString());

		enregistrer(KafkaWorkflowConfig.SUJET_NOTIFICATIONS, recipientCompteId, charge, maintenant);
	}

	public static String notificationIdDeterministe(UUID evenementId, String destinataire) {
		return UUID.nameUUIDFromBytes((evenementId + ":" + destinataire).getBytes(StandardCharsets.UTF_8)).toString();
	}

	private void enregistrer(String sujet, String cle, Map<String, Object> charge, Instant maintenant) {
		WfOutbox outbox = new WfOutbox();
		outbox.setSujet(sujet);
		outbox.setCle(cle);
		outbox.setEntetes(Map.of());
		outbox.setCharge(serialiser(charge));
		outbox.setCreeLe(maintenant);
		outbox.setTentatives(0);
		outbox.setProchaineTentativeLe(maintenant);
		outboxRepository.save(outbox);
	}

	private String serialiser(Map<String, Object> charge) {
		try {
			return jsonMapper.writeValueAsString(charge);
		} catch (JacksonException e) {
			throw new IllegalStateException("Impossible de sérialiser le message outbox", e);
		}
	}
}
