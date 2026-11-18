package com.hr.commun.societe;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Propagation de l'identifiant de société dans les en-têtes Kafka ({@code societe_id}, UUID en texte UTF-8).
 */
public final class SocieteKafka {

	public static final String ENTETE_SOCIETE_ID = "societe_id";

	private SocieteKafka() {
	}

	/**
	 * Ajoute l'en-tête {@code societe_id} à partir de {@link SocieteContexte#courante()}.
	 */
	public static <K, V> ProducerRecord<K, V> ajouterEntete(ProducerRecord<K, V> record) {
		return ajouterEntete(record, SocieteContexte.courante());
	}

	/**
	 * Ajoute l'en-tête {@code societe_id} avec l'identifiant fourni.
	 */
	public static <K, V> ProducerRecord<K, V> ajouterEntete(ProducerRecord<K, V> record, UUID societeId) {
		Objects.requireNonNull(record, "record ne doit pas être nul");
		Objects.requireNonNull(societeId, "societeId ne doit pas être nul");
		if (SocieteContexte.AUCUNE.equals(societeId)) {
			throw new SocieteAbsenteException("La société sentinelle AUCUNE ne peut pas être propagée sur Kafka");
		}
		record.headers().remove(ENTETE_SOCIETE_ID);
		record.headers().add(ENTETE_SOCIETE_ID, societeId.toString().getBytes(StandardCharsets.UTF_8));
		return record;
	}

	/**
	 * Lit l'en-tête {@code societe_id} d'un enregistrement consommé.
	 * Renvoie {@link Optional#empty()} si l'en-tête est absent, vide, mal formé ou égal à {@link SocieteContexte#AUCUNE}.
	 */
	public static Optional<UUID> lireEntete(ConsumerRecord<?, ?> record) {
		if (record == null || record.headers() == null) {
			return Optional.empty();
		}
		Header header = record.headers().lastHeader(ENTETE_SOCIETE_ID);
		if (header == null || header.value() == null) {
			return Optional.empty();
		}
		String texte = new String(header.value(), StandardCharsets.UTF_8).trim();
		if (texte.isEmpty()) {
			return Optional.empty();
		}
		try {
			UUID id = UUID.fromString(texte);
			return SocieteContexte.AUCUNE.equals(id) ? Optional.empty() : Optional.of(id);
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
