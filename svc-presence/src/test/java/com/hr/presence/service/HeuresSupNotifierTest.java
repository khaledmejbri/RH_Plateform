package com.hr.presence.service;

import com.hr.presence.entity.PresenceHeuresSupBeneficiaire;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.kafka.ObjetNotification;
import com.hr.presence.kafka.PresenceEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 2F : chaque notification d'heures sup porte le type HEURES_SUP, l'UUID de la demande et son numéro HS-. */
class HeuresSupNotifierTest {

	private final PresenceEventPublisher publisher = mock(PresenceEventPublisher.class);
	private final HeuresSupNotifier notifier = new HeuresSupNotifier(publisher);
	private final UUID demandeId = UUID.randomUUID();

	private PresenceHeuresSupDemande demande() {
		PresenceHeuresSupDemande d = new PresenceHeuresSupDemande();
		d.setId(demandeId);
		d.setReference("HS-202611-0004");
		d.setDemandeurCompteId(UUID.randomUUID().toString());
		d.setMotifRefus("Charge insuffisante");
		return d;
	}

	private PresenceHeuresSupBeneficiaire beneficiaire() {
		PresenceHeuresSupBeneficiaire b = new PresenceHeuresSupBeneficiaire();
		b.setCompteId(UUID.randomUUID());
		return b;
	}

	@Test
	void creeeEnAttente_objetHeuresSupPourLesRh() {
		notifier.creeeEnAttente(demande(), "site Tunis");

		ArgumentCaptor<ObjetNotification> objet = ArgumentCaptor.forClass(ObjetNotification.class);
		verify(publisher).notifier(org.mockito.ArgumentMatchers.eq(PresenceEventPublisher.DESTINATAIRE_RH), anyString(),
				anyString(), objet.capture());
		assertThat(objet.getValue().type()).isEqualTo("HEURES_SUP");
		assertThat(objet.getValue().id()).isEqualTo(demandeId);
		assertThat(objet.getValue().numero()).isEqualTo("HS-202611-0004");
		assertThat(objet.getValue().evenement()).isEqualTo("HEURES_SUP_CREEE");
		assertThat(objet.getValue().notificationId()).isNotNull();
	}

	@Test
	void joursRetires_unSeulNotificationIdPourLeDemandeurEtLesBeneficiaires() {
		notifier.joursRetires(demande(), "site Tunis", "10/11/2026", true, true, List.of(beneficiaire(), beneficiaire()));

		ArgumentCaptor<ObjetNotification> objet = ArgumentCaptor.forClass(ObjetNotification.class);
		verify(publisher, times(3)).notifier(anyString(), anyString(), anyString(), objet.capture());
		assertThat(objet.getAllValues()).extracting(ObjetNotification::notificationId).containsOnly(
				objet.getAllValues().get(0).notificationId());
		assertThat(objet.getAllValues()).allSatisfy(o -> {
			assertThat(o.type()).isEqualTo("HEURES_SUP");
			assertThat(o.id()).isEqualTo(demandeId);
		});
	}

	@Test
	void refusee_pasDUuidDansLeTexte() {
		notifier.refusee(demande(), "site Tunis");

		ArgumentCaptor<String> sujet = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
		verify(publisher).notifier(anyString(), sujet.capture(), corps.capture(), org.mockito.ArgumentMatchers.any());
		assertThat(sujet.getValue() + corps.getValue()).doesNotContain(demandeId.toString()).doesNotContain("|");
	}
}
