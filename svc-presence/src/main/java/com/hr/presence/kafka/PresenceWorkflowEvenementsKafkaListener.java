package com.hr.presence.kafka;

import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.service.PresenceWorkflowDemarrageService;
import com.hr.presence.service.PresenceWorkflowVariablesBuilder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Projection des événements de {@code svc-workflow} ({@code rh.workflow.evenements}) sur
 * {@code presence_demande_manuelle} et {@code presence_heures_sup_demande} (D-W6-06).
 * Ne modifie jamais {@code statut} (seul l'endpoint d'effet le fait).
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class PresenceWorkflowEvenementsKafkaListener {

	private static final Logger log = LoggerFactory.getLogger(PresenceWorkflowEvenementsKafkaListener.class);
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Set<String> STATUTS_TERMINES = Set.of(
			"APPROUVEE", "REFUSEE", "ANNULEE", "EXPIREE", "ERREUR");

	private final PresenceDemandeManuelleRepository demandeManuelleRepo;
	private final PresenceHeuresSupDemandeRepository heuresSupRepo;
	private final TransactionTemplate transactionTemplate;

	public PresenceWorkflowEvenementsKafkaListener(
			PresenceDemandeManuelleRepository demandeManuelleRepo,
			PresenceHeuresSupDemandeRepository heuresSupRepo,
			TransactionTemplate transactionTemplate) {
		this.demandeManuelleRepo = demandeManuelleRepo;
		this.heuresSupRepo = heuresSupRepo;
		this.transactionTemplate = transactionTemplate;
	}

	@KafkaListener(
			topics = "rh.workflow.evenements",
			containerFactory = "workflowEvenementsKafkaListenerContainerFactory")
	public void onWorkflowEvenement(ConsumerRecord<String, String> record) {
		if (record == null || record.value() == null || record.value().isBlank()) {
			return;
		}
		JsonNode root;
		try {
			root = JSON.readTree(record.value());
		} catch (Exception e) {
			log.warn("Événement rh.workflow.evenements illisible : {}", e.getClass().getSimpleName());
			return;
		}

		String objetType = texte(root, "objet_type");
		if (!PresenceWorkflowVariablesBuilder.TYPE_POINTAGE_MANUEL.equals(objetType)
				&& !PresenceWorkflowVariablesBuilder.TYPE_HEURES_SUP.equals(objetType)) {
			return;
		}
		UUID objetId = uuid(root, "objet_id");
		Instant emisLe = instant(root, "emis_le");
		if (objetId == null || emisLe == null) {
			return;
		}

		UUID instanceId = uuid(root, "instance_id");
		String statutInstance = texte(root, "statut_instance");
		String etapeLibelle = texte(root, "etape_libelle");
		Instant echeanceLe = instant(root, "echeance_le");
		boolean terminee = statutInstance != null && STATUTS_TERMINES.contains(statutInstance);

		transactionTemplate.executeWithoutResult(status -> {
			if (PresenceWorkflowVariablesBuilder.TYPE_POINTAGE_MANUEL.equals(objetType)) {
				projeterPointageManuel(objetId, instanceId, etapeLibelle, echeanceLe, emisLe, terminee);
			} else {
				projeterHeuresSup(objetId, instanceId, etapeLibelle, echeanceLe, emisLe, terminee);
			}
		});
	}

	private void projeterPointageManuel(
			UUID objetId, UUID instanceId, String etapeLibelle, Instant echeanceLe, Instant emisLe, boolean terminee) {
		Optional<PresenceDemandeManuelle> opt = demandeManuelleRepo.findByIdPourDecision(objetId);
		if (opt.isEmpty()) {
			return;
		}
		PresenceDemandeManuelle d = opt.get();
		if (d.getWorkflowProjectionLe() != null && !emisLe.isAfter(d.getWorkflowProjectionLe())) {
			return;
		}
		if (d.getWorkflowInstanceId() == null && instanceId != null) {
			d.setWorkflowInstanceId(instanceId);
			if (PresenceWorkflowDemarrageService.ETAT_A_DEMARRER.equals(d.getWorkflowEtat())) {
				d.setWorkflowEtat(PresenceWorkflowDemarrageService.ETAT_DEMARRE);
			}
		}
		d.setEtapeCouranteLibelle(terminee ? null : etapeLibelle);
		d.setEcheanceLe(terminee ? null : echeanceLe);
		d.setWorkflowProjectionLe(emisLe);
		demandeManuelleRepo.save(d);
	}

	private void projeterHeuresSup(
			UUID objetId, UUID instanceId, String etapeLibelle, Instant echeanceLe, Instant emisLe, boolean terminee) {
		Optional<PresenceHeuresSupDemande> opt = heuresSupRepo.findByIdPourDecision(objetId);
		if (opt.isEmpty()) {
			return;
		}
		PresenceHeuresSupDemande d = opt.get();
		if (d.getWorkflowProjectionLe() != null && !emisLe.isAfter(d.getWorkflowProjectionLe())) {
			return;
		}
		if (d.getWorkflowInstanceId() == null && instanceId != null) {
			d.setWorkflowInstanceId(instanceId);
			if (PresenceWorkflowDemarrageService.ETAT_A_DEMARRER.equals(d.getWorkflowEtat())) {
				d.setWorkflowEtat(PresenceWorkflowDemarrageService.ETAT_DEMARRE);
			}
		}
		d.setEtapeCouranteLibelle(terminee ? null : etapeLibelle);
		d.setEcheanceLe(terminee ? null : echeanceLe);
		d.setWorkflowProjectionLe(emisLe);
		heuresSupRepo.save(d);
	}

	private static String texte(JsonNode root, String champ) {
		JsonNode n = root.get(champ);
		if (n == null || n.isNull()) {
			return null;
		}
		String s = n.asString("");
		return s.isBlank() ? null : s;
	}

	private static UUID uuid(JsonNode root, String champ) {
		String s = texte(root, champ);
		if (s == null) {
			return null;
		}
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static Instant instant(JsonNode root, String champ) {
		String s = texte(root, champ);
		if (s == null) {
			return null;
		}
		try {
			return Instant.parse(s);
		} catch (Exception e) {
			return null;
		}
	}
}
