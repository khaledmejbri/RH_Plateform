package com.hr.presence.service;

import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.kafka.ObjetNotification;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.hr.presence.service.ManuelTestSupport.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** D-P23.b / D-P23.c : expiration par le planificateur (idempotente) et paresseuse à la décision, course avec la validation. */
class DemandeManuelleExpirationTest {

	private ManuelTestSupport t;
	private PresenceDemandeManuelleService service;

	@BeforeEach
	void setUp() {
		t = new ManuelTestSupport();
		service = t.service;
		t.siteSansHoraire();
	}

	private PresenceDemandeManuelle echue() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setEcheance(MAINTENANT); // échéance incluse : 24 h pile = expirée
		return d;
	}

	// ---- Planificateur --------------------------------------------------------------------------------------------

	@Test
	void planificateur_expireLesDemandesEchues_neNotifiePasLesAutres() {
		PresenceDemandeManuelle d = echue();
		when(t.demandeRepository.idsEchues(MAINTENANT)).thenReturn(List.of(d.getId()));

		new DemandeManuelleExpirationScheduler(service).expirerLesDemandesEchues();

		assertThat(d.getStatut()).isEqualTo(StatutDemandeManuelle.EXPIREE);
		verify(t.demandeRepository).save(d);
		verify(t.pointageRepository, never()).save(any(PresencePointage.class));
	}

	@Test
	void expiration_notifieLeCollaborateurEtLeValideurFige_memeEvenement() {
		PresenceDemandeManuelle d = echue();

		assertThat(service.expirer(d.getId())).isTrue();

		ArgumentCaptor<ObjetNotification> objets = ArgumentCaptor.forClass(ObjetNotification.class);
		ArgumentCaptor<String> textes = ArgumentCaptor.forClass(String.class);
		verify(t.eventPublisher).notifier(eq(t.compteCollab.toString()), eq("Pointage manuel expiré — PM-202611-0007"),
				textes.capture(), objets.capture());
		assertThat(textes.getValue()).isEqualTo("Votre pointage manuel PM-202611-0007 n'a pas été traité à temps et a expiré. "
				+ "Contactez votre responsable ou les RH.");
		ArgumentCaptor<String> texteValideur = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<ObjetNotification> objetValideur = ArgumentCaptor.forClass(ObjetNotification.class);
		verify(t.eventPublisher).notifier(eq(t.compteRo.toString()),
				eq("Pointage manuel expiré — Sarra Khemiri (PM-202611-0007)"), texteValideur.capture(), objetValideur.capture());
		assertThat(texteValideur.getValue()).isEqualTo("La demande de pointage manuel PM-202611-0007 de Sarra Khemiri "
				+ "(entrée du 11/11 à 09:50) a expiré le 11/11 à 10:00 sans décision. Aucun pointage n'a été enregistré.");
		assertThat(objets.getValue().evenement()).isEqualTo("POINTAGE_MANUEL_EXPIRE");
		assertThat(objets.getValue().type()).isEqualTo("POINTAGE_MANUEL");
		assertThat(objetValideur.getValue().notificationId()).isEqualTo(objets.getValue().notificationId());
	}

	@Test
	void expiration_demandeAdresseeAuxRh_notifieLesRh() {
		PresenceDemandeManuelle d = echue();
		d.setValideurId(null);
		d.setValideurCompteId(null);

		service.expirer(d.getId());

		verify(t.eventPublisher).notifier(eq(PresenceEventPublisher.DESTINATAIRE_RH), anyString(), anyString(), any());
	}

	@Test
	void expiration_valideurFige_pasLeResponsableActuel() {
		PresenceDemandeManuelle d = echue();
		d.setValideurCompteId("compte-valideur-fige");

		service.expirer(d.getId());

		verify(t.eventPublisher).notifier(eq("compte-valideur-fige"), anyString(), anyString(), any());
	}

	@Test
	void expiration_idempotente_demandeDejaExpireeOuDecidee_neFaitRien() {
		PresenceDemandeManuelle expiree = echue();
		expiree.setStatut(StatutDemandeManuelle.EXPIREE);
		PresenceDemandeManuelle validee = echue();
		validee.setStatut(StatutDemandeManuelle.VALIDEE);

		assertThat(service.expirer(expiree.getId())).isFalse();
		assertThat(service.expirer(validee.getId())).isFalse();

		verify(t.demandeRepository, never()).save(any(PresenceDemandeManuelle.class));
		verifyNoInteractions(t.eventPublisher);
	}

	@Test
	void expiration_echeanceNonAtteinte_neFaitRien() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setEcheance(MAINTENANT.plusSeconds(1));

		assertThat(service.expirer(d.getId())).isFalse();
		assertThat(d.getStatut()).isEqualTo(StatutDemandeManuelle.EN_ATTENTE);
	}

	@Test
	void planificateur_deuxPassages_uneSeuleExpiration() {
		PresenceDemandeManuelle d = echue();
		when(t.demandeRepository.idsEchues(MAINTENANT)).thenReturn(List.of(d.getId()));
		DemandeManuelleExpirationScheduler scheduler = new DemandeManuelleExpirationScheduler(service);

		scheduler.expirerLesDemandesEchues();
		scheduler.expirerLesDemandesEchues();

		verify(t.demandeRepository, times(1)).save(d);
		verify(t.eventPublisher, times(2)).notifier(anyString(), anyString(), anyString(), any());
	}

	@Test
	void planificateur_uneErreurSurUneDemandeNArretePasLesAutres() {
		PresenceDemandeManuelle premiere = echue();
		PresenceDemandeManuelle seconde = echue();
		when(t.demandeRepository.idsEchues(MAINTENANT)).thenReturn(List.of(premiere.getId(), seconde.getId()));
		when(t.demandeRepository.findByIdPourDecision(premiere.getId()))
				.thenThrow(new org.springframework.dao.CannotAcquireLockException("verrou"));

		new DemandeManuelleExpirationScheduler(service).expirerLesDemandesEchues();

		assertThat(seconde.getStatut()).isEqualTo(StatutDemandeManuelle.EXPIREE);
		assertThat(premiere.getStatut()).isEqualTo(StatutDemandeManuelle.EN_ATTENTE);
	}

	// ---- Expiration paresseuse à la décision ----------------------------------------------------------------------

	@Test
	void validerApresLEcheance_statutExpireeEnregistre_puis409DemandeExpiree() {
		PresenceDemandeManuelle d = echue();
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());

		assertThatThrownBy(() -> service.valider(d.getId(), null)).isInstanceOfSatisfying(PresenceMetierException.class, e -> {
			assertThat(e.getHttpStatus()).isEqualTo(409);
			assertThat(e.getCode()).isEqualTo("DEMANDE_EXPIREE");
			assertThat(e.getMessage()).isEqualTo("Cette demande a expiré le 11/11 à 10:00 : elle ne peut plus être validée ni refusée.");
		});

		assertThat(d.getStatut()).isEqualTo(StatutDemandeManuelle.EXPIREE);
		verify(t.demandeRepository).save(d);
		verify(t.pointageRepository, never()).save(any(PresencePointage.class));
		verify(t.eventPublisher, times(2)).notifier(anyString(), anyString(), anyString(), any());
	}

	@Test
	void refuserApresLEcheance_409DemandeExpiree() {
		PresenceDemandeManuelle d = echue();
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());

		assertThatThrownBy(() -> service.refuser(d.getId(), "Absent ce matin-là"))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(409);
					assertThat(e.getCode()).isEqualTo("DEMANDE_EXPIREE");
				});
		assertThat(d.getStatut()).isEqualTo(StatutDemandeManuelle.EXPIREE);
	}

	@Test
	void echeanceUneSecondeAvant_laDecisionPasse_pileALEcheance_elleExpire() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setEcheance(MAINTENANT.plusSeconds(1));
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());
		assertThat(service.valider(d.getId(), null).statut()).isEqualTo(StatutDemandeManuelle.VALIDEE);

		PresenceDemandeManuelle pile = t.demandeEnAttente();
		pile.setEcheance(MAINTENANT);
		assertThatThrownBy(() -> service.valider(pile.getId(), null))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("DEMANDE_EXPIREE"));
	}

	@Test
	void courseValidationExpiration_leSchedulerAGagne_laValidationRecoit409_etAucunPointageNEstCree() {
		// Le verrou pessimiste fait relire à la validation le statut déjà posé par l'expiration.
		PresenceDemandeManuelle d = t.demandeEnAttente();
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());
		when(t.demandeRepository.findByIdPourDecision(d.getId())).thenAnswer(inv -> {
			d.setStatut(StatutDemandeManuelle.EXPIREE); // l'autre transaction vient de valider son expiration
			return Optional.of(d);
		});

		assertThatThrownBy(() -> service.valider(d.getId(), null)).isInstanceOfSatisfying(PresenceMetierException.class,
				e -> assertThat(e.getCode()).isEqualTo("DEMANDE_EXPIREE"));

		verify(t.pointageRepository, never()).save(any(PresencePointage.class));
		verify(t.entityManager).detach(d); // la lecture d'autorisation est détachée : la relecture sous verrou est fraîche
	}

	@Test
	void courseValidationExpiration_laValidationAGagne_leSchedulerNeTouchePas() {
		PresenceDemandeManuelle d = echue();
		d.setStatut(StatutDemandeManuelle.VALIDEE);

		assertThat(service.expirer(d.getId())).isFalse();
		assertThat(d.getStatut()).isEqualTo(StatutDemandeManuelle.VALIDEE);
	}

	@Test
	void lAutorisationPrecedeLeVerrou_unAutreResponsableNePrendPasLeVerrou() {
		PresenceDemandeManuelle d = echue();
		when(t.referentielClient.monValideurPointage()).thenReturn(new com.hr.presence.client.ReferentielClient.ValideurPointage(
				UUID.randomUUID(), UUID.randomUUID(), "Autre", null, null, null));

		assertThatThrownBy(() -> service.valider(d.getId(), null)).isInstanceOf(SecurityException.class);

		verify(t.demandeRepository, never()).findByIdPourDecision(any());
		assertThat(d.getStatut()).isEqualTo(StatutDemandeManuelle.EN_ATTENTE);
	}

	@Test
	void aValider_neListePasLesDemandesEchues() {
		PresenceDemandeManuelle vivante = t.demandeEnAttente();
		PresenceDemandeManuelle echue = echue();
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());
		when(t.demandeRepository.findByValideurIdAndStatutOrderByCreeLeAsc(t.ficheRo.toString(), StatutDemandeManuelle.EN_ATTENTE))
				.thenReturn(List.of(vivante, echue));

		assertThat(service.aValider()).extracting("id").containsExactly(vivante.getId());
	}

	@Test
	void echeanceDeLaCreation_tentativePlusDelaiDeDecisionReglable() {
		t.properties.getManuel().setDelaiDecisionMinutes(3);
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		var r = service.creer(new com.hr.presence.dto.DemandeManuelleCreationRequest(t.rejet.getId(), null, null,
				"GPS faible au rez-de-chaussée"));

		assertThat(r.echeance()).isEqualTo(t.rejet.getServerTs().plusSeconds(180));
	}

	@Test
	void idsAExpirer_lisentLHorlogeInjectee() {
		when(t.demandeRepository.idsEchues(any())).thenReturn(List.of());

		assertThat(service.idsAExpirer()).isEmpty();

		ArgumentCaptor<Instant> instant = ArgumentCaptor.forClass(Instant.class);
		verify(t.demandeRepository).idsEchues(instant.capture());
		assertThat(instant.getValue()).isEqualTo(MAINTENANT);
	}

	@Test
	void expirationSansEchance_neExpireJamais() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setEcheance(null);

		assertThat(service.expirer(d.getId())).isFalse();
	}
}
