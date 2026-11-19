package com.hr.referentiel.kafka;

import com.hr.referentiel.config.ImportChampsAmorceRunner;
import com.hr.referentiel.config.ParametresAmorceRunner;
import com.hr.referentiel.config.SoldesCongeAmorceRunner;
import com.hr.referentiel.domain.RhEvenementTraite;
import com.hr.referentiel.repository.RhEvenementTraiteRepository;
import com.hr.referentiel.service.IdentiteSocieteClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Consommateur du sujet {@code rh.societes} (§ 9.8) :
 * <ul>
 *   <li>{@code SOCIETE_CREEE} : amorce les paramètres, jours fériés et paramètres société par défaut de la nouvelle
 *       société (de façon idempotente par {@code evenement_id}) et invalide le cache des sociétés actives.</li>
 *   <li>{@code SOCIETE_SUSPENDUE} / {@code SOCIETE_REACTIVEE} : invalide le cache des sociétés actives.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class SocietesKafkaListener {

	private static final Logger log = LoggerFactory.getLogger(SocietesKafkaListener.class);

	private final JsonMapper jsonMapper;
	private final RhEvenementTraiteRepository evenementTraiteRepository;
	private final ParametresAmorceRunner parametresAmorceRunner;
	private final SoldesCongeAmorceRunner soldesCongeAmorceRunner;
	private final ImportChampsAmorceRunner importChampsAmorceRunner;
	private final IdentiteSocieteClient identiteSocieteClient;
	private final TransactionTemplate transactionTemplate;

	public SocietesKafkaListener(
			JsonMapper jsonMapper,
			RhEvenementTraiteRepository evenementTraiteRepository,
			ParametresAmorceRunner parametresAmorceRunner,
			SoldesCongeAmorceRunner soldesCongeAmorceRunner,
			ImportChampsAmorceRunner importChampsAmorceRunner,
			IdentiteSocieteClient identiteSocieteClient,
			TransactionTemplate transactionTemplate) {
		this.jsonMapper = jsonMapper;
		this.evenementTraiteRepository = evenementTraiteRepository;
		this.parametresAmorceRunner = parametresAmorceRunner;
		this.soldesCongeAmorceRunner = soldesCongeAmorceRunner;
		this.importChampsAmorceRunner = importChampsAmorceRunner;
		this.identiteSocieteClient = identiteSocieteClient;
		this.transactionTemplate = transactionTemplate;
	}

	@KafkaListener(
			topics = RhKafkaTopics.SOCIETES,
			containerFactory = "societesKafkaListenerContainerFactory")
	public void onSocieteEvenement(String payload) {
		if (payload == null || payload.isBlank()) {
			return;
		}
		JsonNode node = jsonMapper.readTree(payload);
		String type = texte(node, "evenement");
		UUID evenementId = uuid(node, "evenement_id");
		UUID societeId = uuid(node, "societe_id");
		if (type == null || societeId == null) {
			log.warn("Événement rh.societes ignoré (type ou societe_id manquant) : {}", payload);
			return;
		}
		if (evenementId != null && Boolean.TRUE.equals(transactionTemplate.execute(
				s -> evenementTraiteRepository.existsById(evenementId)))) {
			log.debug("Événement rh.societes {} déjà traité, ignoré", evenementId);
			return;
		}

		identiteSocieteClient.invaliderCacheActives();

		if ("SOCIETE_CREEE".equals(type)) {
			parametresAmorceRunner.amorcerPourSociete(societeId);
			soldesCongeAmorceRunner.amorcerPourSociete(societeId);
			importChampsAmorceRunner.amorcerPourSociete(societeId);
			log.info("Nouvelle société {} ({}) amorcée dans svc-referentiel-rh", societeId, texte(node, "code"));
		}

		if (evenementId != null) {
			transactionTemplate.executeWithoutResult(s ->
					evenementTraiteRepository.save(new RhEvenementTraite(evenementId, RhKafkaTopics.SOCIETES, Instant.now())));
		}
	}

	private static String texte(JsonNode node, String champ) {
		JsonNode v = node.get(champ);
		return v != null && !v.isNull() && !v.asString().isBlank() ? v.asString() : null;
	}

	private static UUID uuid(JsonNode node, String champ) {
		String s = texte(node, champ);
		if (s == null) {
			return null;
		}
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
