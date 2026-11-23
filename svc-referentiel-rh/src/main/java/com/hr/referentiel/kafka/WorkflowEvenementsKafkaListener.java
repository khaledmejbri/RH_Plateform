package com.hr.referentiel.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocieteKafka;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.DemandeDocumentAdministratifRh;
import com.hr.referentiel.entity.DemandeFormationRh;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.DemandeDocumentAdministratifRhRepository;
import com.hr.referentiel.repository.DemandeFormationRhRepository;
import com.hr.referentiel.service.SocieteDefautResolver;
import com.hr.referentiel.service.WorkflowDemarrageService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Projection des événements de {@code svc-workflow} ({@code rh.workflow.evenements}) sur {@code rh_demande_administrative}
 * (D-W5-09). Ne modifie jamais {@code statut} (seul l'endpoint d'effet le fait).
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class WorkflowEvenementsKafkaListener {

	private static final Logger log = LoggerFactory.getLogger(WorkflowEvenementsKafkaListener.class);
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final Set<String> TYPES_M01 = Set.of(
			TypeDemandeAdministrativeRh.CONGE.name(),
			TypeDemandeAdministrativeRh.AUTORISATION_SORTIE.name(),
			TypeDemandeAdministrativeRh.ORDRE_MISSION.name());
	private static final String TYPE_DOCUMENT = "DOCUMENT";
	private static final String TYPE_FORMATION = "FORMATION";
	private static final Set<String> STATUTS_TERMINES = Set.of(
			"APPROUVEE", "REFUSEE", "ANNULEE", "EXPIREE", "ERREUR");

	private final DemandeAdministrativeRhRepository demandeRepo;
	private final SocieteDefautResolver societeDefautResolver;
	private final TransactionTemplate transactionTemplate;
	private final DemandeDocumentAdministratifRhRepository documentRepo;
	private final DemandeFormationRhRepository formationRepo;

	/** Constructeur historique conservé pour les tests M01 existants. */
	public WorkflowEvenementsKafkaListener(
			DemandeAdministrativeRhRepository demandeRepo,
			SocieteDefautResolver societeDefautResolver,
			TransactionTemplate transactionTemplate) {
		this(demandeRepo, societeDefautResolver, transactionTemplate, null, null);
	}

	@Autowired
	public WorkflowEvenementsKafkaListener(
			DemandeAdministrativeRhRepository demandeRepo,
			SocieteDefautResolver societeDefautResolver,
			TransactionTemplate transactionTemplate,
			DemandeDocumentAdministratifRhRepository documentRepo,
			DemandeFormationRhRepository formationRepo) {
		this.demandeRepo = demandeRepo;
		this.societeDefautResolver = societeDefautResolver;
		this.transactionTemplate = transactionTemplate;
		this.documentRepo = documentRepo;
		this.formationRepo = formationRepo;
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
			root = MAPPER.readTree(record.value());
		} catch (Exception e) {
			log.warn("Événement rh.workflow.evenements illisible : {}", e.getClass().getSimpleName());
			return;
		}

		String objetType = texte(root, "objet_type");
		boolean estM01 = objetType != null && TYPES_M01.contains(objetType);
		boolean estDocument = TYPE_DOCUMENT.equals(objetType);
		boolean estFormation = TYPE_FORMATION.equals(objetType);
		if (!estM01 && !estDocument && !estFormation) {
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

		// Aucun en-tête societe_id exigé (D-W5-09) : repli société par défaut
		Optional<UUID> societeId = SocieteKafka.lireEntete(record)
				.or(() -> societeDefautResolver.resoudrePourTransitionAvecWarn("Message Kafka rh.workflow.evenements"));
		if (societeId.isEmpty()) {
			log.warn("Impossible de déterminer la société pour projeter objetId={}", objetId);
			return;
		}

		SocieteContexte.executer(societeId.get(), () -> transactionTemplate.executeWithoutResult(status -> {
			if (estM01) {
				Optional<DemandeAdministrativeRh> opt = demandeRepo.findByIdForUpdate(objetId);
				if (opt.isEmpty()) {
					return;
				}
				DemandeAdministrativeRh d = opt.get();
				if (d.getWorkflowProjectionLe() != null && !emisLe.isAfter(d.getWorkflowProjectionLe())) {
					return;
				}
				if (d.getWorkflowInstanceId() == null && instanceId != null) {
					d.setWorkflowInstanceId(instanceId);
					if (WorkflowDemarrageService.ETAT_A_DEMARRER.equals(d.getWorkflowEtat())) {
						d.setWorkflowEtat(WorkflowDemarrageService.ETAT_DEMARRE);
					}
				}
				d.setWorkflowEtapeLibelle(terminee ? null : etapeLibelle);
				d.setWorkflowEcheanceLe(terminee ? null : echeanceLe);
				d.setWorkflowProjectionLe(emisLe);
				demandeRepo.save(d);
			} else if (estDocument && documentRepo != null) {
				Optional<DemandeDocumentAdministratifRh> opt = documentRepo.findByIdForUpdate(objetId);
				if (opt.isEmpty()) {
					return;
				}
				DemandeDocumentAdministratifRh d = opt.get();
				if (d.getWorkflowProjectionLe() != null && !emisLe.isAfter(d.getWorkflowProjectionLe())) {
					return;
				}
				if (d.getWorkflowInstanceId() == null && instanceId != null) {
					d.setWorkflowInstanceId(instanceId);
					if (WorkflowDemarrageService.ETAT_A_DEMARRER.equals(d.getWorkflowEtat())) {
						d.setWorkflowEtat(WorkflowDemarrageService.ETAT_DEMARRE);
					}
				}
				d.setWorkflowEtapeLibelle(terminee ? null : etapeLibelle);
				d.setWorkflowEcheanceLe(terminee ? null : echeanceLe);
				d.setWorkflowProjectionLe(emisLe);
				documentRepo.save(d);
			} else if (estFormation && formationRepo != null) {
				Optional<DemandeFormationRh> opt = formationRepo.findByIdForUpdate(objetId);
				if (opt.isEmpty()) {
					return;
				}
				DemandeFormationRh d = opt.get();
				if (d.getWorkflowProjectionLe() != null && !emisLe.isAfter(d.getWorkflowProjectionLe())) {
					return;
				}
				if (d.getWorkflowInstanceId() == null && instanceId != null) {
					d.setWorkflowInstanceId(instanceId);
					if (WorkflowDemarrageService.ETAT_A_DEMARRER.equals(d.getWorkflowEtat())) {
						d.setWorkflowEtat(WorkflowDemarrageService.ETAT_DEMARRE);
					}
				}
				d.setWorkflowEtapeLibelle(terminee ? null : etapeLibelle);
				d.setWorkflowEcheanceLe(terminee ? null : echeanceLe);
				d.setWorkflowProjectionLe(emisLe);
				formationRepo.save(d);
			}
		}));
	}

	private static String texte(JsonNode root, String champ) {
		JsonNode n = root.get(champ);
		if (n == null || n.isNull()) {
			return null;
		}
		String s = n.asText();
		return s == null || s.isBlank() ? null : s;
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
