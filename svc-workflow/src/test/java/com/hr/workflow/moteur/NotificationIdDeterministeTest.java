package com.hr.workflow.moteur;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Revue W3 n° 2 (M2) : deux notifications d'une même décision au même compte ne doivent pas se dédoublonner. */
class NotificationIdDeterministeTest {

	@Test
	void memeEvenementRejoue_memeIdentifiant() {
		UUID ev = UUID.randomUUID();
		assertThat(OutboxService.notificationIdDeterministe(ev, "c1", "WF_DECISION", "Étape validée"))
				.isEqualTo(OutboxService.notificationIdDeterministe(ev, "c1", "WF_DECISION", "Étape validée"));
	}

	@Test
	void deuxNotificationsDifferentesMemeCompteMemeDecision_identifiantsDistincts() {
		UUID ev = UUID.randomUUID();
		String etapeValidee = OutboxService.notificationIdDeterministe(ev, "c1", "WF_DECISION", "Étape validée");
		String tacheCreee = OutboxService.notificationIdDeterministe(ev, "c1", "WF_TACHE_CREEE", "Demande à traiter");
		String activation = OutboxService.notificationIdDeterministe(ev, "c1", "WF_TACHE_CREEE", "Fournir le justificatif");
		assertThat(etapeValidee).isNotEqualTo(tacheCreee);
		assertThat(tacheCreee).isNotEqualTo(activation);
	}
}
