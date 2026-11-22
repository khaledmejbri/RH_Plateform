package com.hr.presence.service;

import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.dto.DemandeManuelleCreationRequest;
import com.hr.presence.dto.DemandeManuelleResponse;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.hr.presence.service.ManuelTestSupport.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PresenceDemandeManuelleMoteurTest {

	private ManuelTestSupport t;
	private PresenceWorkflowActivationService activationService;
	private PresenceWorkflowDemarrageService demarrageService;
	private PresenceDemandeManuelleService service;

	private static PlatformTransactionManager txManagerAvecFlag() {
		return new PlatformTransactionManager() {
			@Override
			public TransactionStatus getTransaction(TransactionDefinition definition) {
				TransactionSynchronizationManager.setActualTransactionActive(true);
				return new SimpleTransactionStatus();
			}

			@Override
			public void commit(TransactionStatus status) {
				TransactionSynchronizationManager.setActualTransactionActive(false);
			}

			@Override
			public void rollback(TransactionStatus status) {
				TransactionSynchronizationManager.setActualTransactionActive(false);
			}
		};
	}

	@BeforeEach
	void setUp() {
		t = new ManuelTestSupport();
		t.siteSansHoraire();
		activationService = mock(PresenceWorkflowActivationService.class);
		demarrageService = mock(PresenceWorkflowDemarrageService.class);
		service = new PresenceDemandeManuelleService(
				t.demandeRepository,
				t.pointageRepository,
				t.referentielClient,
				t.accessService,
				new FacialIdentiteService(t.accessService, t.referentielClient),
				t.eventPublisher,
				t.parametres,
				t.siteRepository,
				t.fenetreHoraire,
				new com.hr.presence.numero.NumeroDemandeService(cle -> t.rangs.merge(cle, 1L, Long::sum)),
				t.eligibilite,
				t.mapper,
				t.entityManager,
				t.clock,
				activationService,
				demarrageService,
				txManagerAvecFlag());
	}

	@Test
	void creerEnModeMoteur_enCoursSansValideurNiNotif_etDemarreHorsTx() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);
		when(activationService.estMoteur("POINTAGE_MANUEL")).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return true;
		});

		DemandeManuelleResponse r = service.creer(
				new DemandeManuelleCreationRequest(t.rejet.getId(), null, null, "Problème de portique"));

		assertThat(r.statut()).isEqualTo(StatutDemandeManuelle.EN_COURS);
		assertThat(r.workflowMode()).isEqualTo("MOTEUR");
		assertThat(r.valideurNom()).isNull();
		verifyNoInteractions(t.eventPublisher);
		verify(demarrageService).demarrerPointageManuel(r.id());
	}

	@Test
	void aValider_exclutDemandesMoteur() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());
		PresenceDemandeManuelle herite = t.demandeEnAttente();
		PresenceDemandeManuelle moteur = t.demandeEnAttente();
		moteur.setId(UUID.randomUUID());
		moteur.setWorkflowMode("MOTEUR");
		when(t.demandeRepository.findByValideurIdAndStatutOrderByCreeLeAsc(
				t.ficheRo.toString(), StatutDemandeManuelle.EN_ATTENTE))
				.thenReturn(List.of(herite, moteur));

		List<DemandeManuelleResponse> file = service.aValider();

		assertThat(file).extracting(DemandeManuelleResponse::id).containsExactly(herite.getId());
	}

	@Test
	void validerEtRefuserAnciennesRoutes_403Avant409DemandeSousWorkflow() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setStatut(StatutDemandeManuelle.EN_COURS);
		d.setWorkflowMode("MOTEUR");
		d.setValideurId(null);
		d.setValideurCompteId(null);
		d.setValideurNom(null);

		// Utilisateur non autorisé → 403 avant 409
		when(t.accessService.isBackofficeEcriture()).thenReturn(false);
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());
		assertThatThrownBy(() -> service.valider(d.getId(), null))
				.isInstanceOf(SecurityException.class);

		// RH autorisé → 409 DEMANDE_SOUS_WORKFLOW avant identites / findByIdPourDecision
		when(t.accessService.isBackofficeEcriture()).thenReturn(true);
		when(t.accessService.currentCompteId()).thenReturn(UUID.randomUUID().toString());
		assertThatThrownBy(() -> service.valider(d.getId(), null))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(ex -> {
					PresenceMetierException p = (PresenceMetierException) ex;
					assertThat(p.getHttpStatus()).isEqualTo(409);
					assertThat(p.getCode()).isEqualTo("DEMANDE_SOUS_WORKFLOW");
				});
		assertThatThrownBy(() -> service.refuser(d.getId(), "Motif valide"))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(ex -> assertThat(((PresenceMetierException) ex).getCode()).isEqualTo("DEMANDE_SOUS_WORKFLOW"));
		verify(t.demandeRepository, never()).findByIdPourDecision(any());
	}

	@Test
	void expirer_ignoreDemandesMoteur() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setWorkflowMode("MOTEUR");
		d.setEcheance(MAINTENANT.minusSeconds(60));
		when(t.demandeRepository.findByIdPourDecision(d.getId())).thenReturn(Optional.of(d));

		assertThat(service.expirer(d.getId())).isFalse();
		assertThat(d.getStatut()).isEqualTo(StatutDemandeManuelle.EN_ATTENTE);
		verifyNoInteractions(t.eventPublisher);
	}
}
