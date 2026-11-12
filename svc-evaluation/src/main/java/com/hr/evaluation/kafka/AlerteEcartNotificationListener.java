package com.hr.evaluation.kafka;

import com.hr.evaluation.client.IdentiteClient;
import com.hr.evaluation.service.AlerteEcartANotifierEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Notification temps réel (WebSocket uniquement, aucun e-mail) des alertes d'écart, <b>après le commit</b> de la
 * validation du RO ou du recalcul (D-E07). Un rollback n'envoie rien ; une panne Kafka ou identité après commit laisse
 * la validation à 200 et l'alerte en base (avertissement dans les journaux).
 * <p>
 * Un message par compte RH (jamais le destinataire littéral {@code RH}), moins le collaborateur et le RO ; repli sur
 * les comptes ADMIN si aucun RH ne reste.
 */
@Component
public class AlerteEcartNotificationListener {

	private static final Logger log = LoggerFactory.getLogger(AlerteEcartNotificationListener.class);

	static final String ROLE_RH = "RH";
	static final String ROLE_ADMIN = "ADMIN";
	static final String EVENEMENT_ALERTE_ECART = "ALERTE_ECART_EVALUATION";

	private final IdentiteClient identiteClient;
	private final EvaluationEventPublisher eventPublisher;

	public AlerteEcartNotificationListener(IdentiteClient identiteClient, EvaluationEventPublisher eventPublisher) {
		this.identiteClient = identiteClient;
		this.eventPublisher = eventPublisher;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void surAlerteANotifier(AlerteEcartANotifierEvent evenement) {
		try {
			Set<UUID> destinataires = destinataires(evenement);
			if (destinataires.isEmpty()) {
				log.warn("Alerte {} sans destinataire (ni RH ni ADMIN hors personnes concernées)", evenement.alerteId());
				return;
			}
			String sujet = sujet(evenement);
			String contenu = contenu(evenement);
			// Un seul identifiant d'événement pour tous les destinataires de l'alerte
			ObjetNotification objet = ObjetNotification.de(ObjetNotification.ALERTE_ECART,
					EVENEMENT_ALERTE_ECART, evenement.alerteId(), null);
			for (UUID compte : destinataires) {
				eventPublisher.notifier(compte.toString(), sujet, contenu, objet);
			}
		} catch (Exception ex) {
			log.warn("Notification de l'alerte d'écart {} non envoyée : {}", evenement.alerteId(), ex.getMessage());
		}
	}

	private Set<UUID> destinataires(AlerteEcartANotifierEvent evenement) {
		Set<UUID> comptes = comptesHorsExclus(ROLE_RH, evenement.comptesExclus());
		if (comptes.isEmpty()) {
			comptes = comptesHorsExclus(ROLE_ADMIN, evenement.comptesExclus());
		}
		return comptes;
	}

	private Set<UUID> comptesHorsExclus(String role, Set<UUID> exclus) {
		List<UUID> bruts = identiteClient.comptesParRole(role);
		Set<UUID> resultat = new LinkedHashSet<>(bruts);
		resultat.removeAll(exclus);
		return resultat;
	}

	static String sujet(AlerteEcartANotifierEvent e) {
		String niveau = e.niveauLibelle() != null ? e.niveauLibelle() : e.niveau();
		return "Alerte d'écart d'évaluation — " + niveau;
	}

	static String contenu(AlerteEcartANotifierEvent e) {
		String nom = e.collaborateurNom() != null ? e.collaborateurNom() : "un collaborateur";
		String campagne = e.campagneNom() != null ? e.campagneNom() : "campagne";
		return "Évaluation de " + nom + " (" + campagne + ") : " + e.nbCompetencesEnEcart()
				+ " compétence(s) en écart, écart max " + e.ecartMax() + " étoiles.";
	}
}
