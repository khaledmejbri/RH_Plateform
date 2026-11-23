package com.hr.presence.service;

import com.hr.presence.client.PresenceWorkflowMoteurClient;
import com.hr.presence.client.PresenceWorkflowMoteurClient.IssueAnnulation;
import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.CollaborateurResolu;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.dto.HeuresSupDtos.CreationHeuresSupRequest;
import com.hr.presence.dto.HeuresSupDtos.DemandeHeuresSupResponse;
import com.hr.presence.entity.PresenceHeuresSupBeneficiaire;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.numero.NumeroDemandeService;
import com.hr.presence.repository.PresenceHeuresSupBeneficiaireRepository;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.ActeurResolver;
import com.hr.presence.security.ActeurResolver.Acteur;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.service.FenetreHoraireService.Horaire;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeuresSupMoteurTest {

	private static final Instant MAINTENANT = LocalDateTime.of(2026, 11, 2, 9, 0).atZone(ClockConfig.ZONE_TUNIS).toInstant();
	private static final LocalDate DEMAIN = LocalDate.of(2026, 11, 3);

	@Mock
	private PresenceHeuresSupDemandeRepository demandeRepository;
	@Mock
	private PresenceHeuresSupBeneficiaireRepository beneficiaireRepository;
	@Mock
	private PresenceHeuresSupJourRepository jourRepository;
	@Mock
	private PresenceSiteRepository siteRepository;
	@Mock
	private FenetreHoraireService fenetreService;
	@Mock
	private CalculHeuresSupService calculService;
	@Mock
	private ReferentielClient referentielClient;
	@Mock
	private PresenceAccessService accessService;
	@Mock
	private ActeurResolver acteurResolver;
	@Mock
	private HeuresSupNotifier notifier;
	@Mock
	private NumeroDemandeService numeroService;
	@Mock
	private PresenceWorkflowActivationService activationService;
	@Mock
	private PresenceWorkflowDemarrageService demarrageService;
	@Mock
	private PresenceWorkflowMoteurClient moteurClient;

	private HeuresSupCreationService creationService;
	private HeuresSupService service;

	private UUID siteId;
	private UUID ficheRo;
	private UUID compteRo;
	private UUID ficheBenef;
	private UUID compteBenef;
	private UUID ficheRh;
	private UUID compteRh;
	private PresenceSite site;

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
		HeuresSupReponseMapper mapper = new HeuresSupReponseMapper(
				beneficiaireRepository, jourRepository, siteRepository);
		Clock clock = Clock.fixed(MAINTENANT, ClockConfig.ZONE_TUNIS);
		PlatformTransactionManager txManager = txManagerAvecFlag();
		creationService = new HeuresSupCreationService(
				demandeRepository, beneficiaireRepository, jourRepository, siteRepository, fenetreService,
				referentielClient, accessService, acteurResolver, notifier, mapper, clock, numeroService,
				activationService, demarrageService, txManager);
		service = new HeuresSupService(
				demandeRepository, beneficiaireRepository, jourRepository, siteRepository, fenetreService,
				calculService, referentielClient, accessService, acteurResolver, notifier, mapper, clock,
				moteurClient, txManager);

		siteId = UUID.randomUUID();
		ficheRo = UUID.randomUUID();
		compteRo = UUID.randomUUID();
		ficheBenef = UUID.randomUUID();
		compteBenef = UUID.randomUUID();
		ficheRh = UUID.randomUUID();
		compteRh = UUID.randomUUID();

		site = new PresenceSite();
		site.setId(siteId);
		site.setLibelle("Site Port");
		site.setToleranceAvantMinutes(60);
		site.setToleranceApresMinutes(60);

		lenient().when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));
		lenient().when(siteRepository.findAllById(any())).thenReturn(List.of(site));
		lenient().when(fenetreService.horaires(siteId)).thenReturn(Map.of(
				DayOfWeek.TUESDAY, new Horaire(LocalTime.of(8, 0), LocalTime.of(17, 0))));
		lenient().when(numeroService.attribuer(any(), any())).thenReturn("HS-202611-0009");
		lenient().when(demandeRepository.saveAndFlush(any())).thenAnswer(inv -> {
			PresenceHeuresSupDemande d = inv.getArgument(0);
			if (d.getId() == null) d.setId(UUID.randomUUID());
			return d;
		});
		lenient().when(demandeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(beneficiaireRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(referentielClient.resoudreCollaborateurs(any())).thenReturn(List.of(
				new CollaborateurResolu(ficheBenef, compteBenef, "M001", "Ali", true, null, null, ficheRo, null)));
		lenient().when(referentielClient.monValideurPointage()).thenReturn(
				new ValideurPointage(ficheRo, compteRo, "Omar", null, null, null));
		lenient().when(accessService.currentCompteId()).thenReturn(compteRo.toString());
	}

	@Test
	void creerParDemandeurEnModeMoteur_creeEnCoursSansNotifEtDemarreHorsTx() {
		when(activationService.estMoteur("HEURES_SUP")).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return true;
		});
		CreationHeuresSupRequest req = new CreationHeuresSupRequest(
				List.of(ficheBenef), siteId, DEMAIN, DEMAIN, 60, 0, "Inventaire urgent");

		DemandeHeuresSupResponse rep = creationService.creerParDemandeur(req);

		assertThat(rep.statut()).isEqualTo(StatutHeuresSup.EN_COURS);
		assertThat(rep.workflowMode()).isEqualTo("MOTEUR");
		assertThat(rep.creeeValidee()).isFalse();
		verify(demarrageService).demarrerHeuresSup(rep.id());
		verifyNoInteractions(notifier);
	}

	@Test
	void creerDirecteEnModeMoteur_creeEnCoursCreeeValideeEtDemarre() {
		when(activationService.estMoteur("HEURES_SUP")).thenReturn(true);
		when(acteurResolver.avecFiche()).thenReturn(
				new Acteur(compteRh.toString(), ficheRh.toString(), "RH", "Amel Trabelsi"));
		CreationHeuresSupRequest req = new CreationHeuresSupRequest(
				List.of(ficheBenef), siteId, DEMAIN, DEMAIN, 0, 60, "Montage scène");

		DemandeHeuresSupResponse rep = creationService.creerDirecte(req);

		assertThat(rep.statut()).isEqualTo(StatutHeuresSup.EN_COURS);
		assertThat(rep.creeeValidee()).isTrue();
		verify(demarrageService).demarrerHeuresSup(rep.id());
		verifyNoInteractions(notifier);
	}

	@Test
	void validerEtRefuserAnciennesRoutes_403Avant409DemandeSousWorkflow() {
		PresenceHeuresSupDemande d = new PresenceHeuresSupDemande();
		d.setId(UUID.randomUUID());
		d.setStatut(StatutHeuresSup.EN_COURS);
		d.setWorkflowMode("MOTEUR");
		d.setDemandeurCompteId(compteRo.toString());
		d.setDemandeurFicheId(ficheRo.toString());
		d.setDebutPlageTs(MAINTENANT.plusSeconds(3600));
		PresenceHeuresSupBeneficiaire b = new PresenceHeuresSupBeneficiaire();
		b.setCollaborateurId(ficheBenef);
		b.setCompteId(compteBenef);
		when(demandeRepository.findById(d.getId())).thenReturn(Optional.of(d));
		when(beneficiaireRepository.findByDemandeId(d.getId())).thenReturn(List.of(b));

		// Bénéficiaire tente de valider → 403 AUTO_VALIDATION_INTERDITE avant 409
		when(acteurResolver.avecFiche()).thenReturn(
				new Acteur(compteBenef.toString(), ficheBenef.toString(), "USER", "Ali"));
		assertThatThrownBy(() -> service.valider(d.getId()))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(ex -> {
					PresenceMetierException p = (PresenceMetierException) ex;
					assertThat(p.getHttpStatus()).isEqualTo(403);
					assertThat(p.getCode()).isEqualTo("AUTO_VALIDATION_INTERDITE");
				});

		// RH non concernée → 409 DEMANDE_SOUS_WORKFLOW
		when(acteurResolver.avecFiche()).thenReturn(
				new Acteur(compteRh.toString(), ficheRh.toString(), "RH", "Amel"));
		assertThatThrownBy(() -> service.valider(d.getId()))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(ex -> {
					PresenceMetierException p = (PresenceMetierException) ex;
					assertThat(p.getHttpStatus()).isEqualTo(409);
					assertThat(p.getCode()).isEqualTo("DEMANDE_SOUS_WORKFLOW");
				});
		assertThatThrownBy(() -> service.refuser(d.getId(), "Motif valide"))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(ex -> assertThat(((PresenceMetierException) ex).getCode()).isEqualTo("DEMANDE_SOUS_WORKFLOW"));
	}

	@Test
	void annulerParDemandeurEnModeMoteur_appelleMoteurHorsTxEtLibereJours() {
		UUID instanceId = UUID.randomUUID();
		PresenceHeuresSupDemande d = new PresenceHeuresSupDemande();
		d.setId(UUID.randomUUID());
		d.setSiteId(siteId);
		d.setStatut(StatutHeuresSup.EN_COURS);
		d.setWorkflowMode("MOTEUR");
		d.setWorkflowInstanceId(instanceId);
		d.setDemandeurCompteId(compteRo.toString());
		d.setDebutPlageTs(MAINTENANT.plusSeconds(86_400));

		when(demandeRepository.findById(d.getId())).thenReturn(Optional.of(d));
		when(acteurResolver.depuisJwt()).thenReturn(
				new Acteur(compteRo.toString(), ficheRo.toString(), "RO", "Omar"));
		when(moteurClient.annuler(any(), any(), any())).thenAnswer(inv -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return IssueAnnulation.ANNULEE;
		});

		DemandeHeuresSupResponse rep = service.annulerParDemandeur(d.getId());

		assertThat(rep.statut()).isEqualTo(StatutHeuresSup.ANNULEE);
		verify(jourRepository).deleteByDemandeId(d.getId());
	}

	@Test
	void planificateurExpiration_ignoreDemandesMoteur() {
		PresenceHeuresSupDemande moteur = new PresenceHeuresSupDemande();
		moteur.setId(UUID.randomUUID());
		moteur.setStatut(StatutHeuresSup.EN_ATTENTE);
		moteur.setWorkflowMode("MOTEUR");
		moteur.setDebutPlageTs(MAINTENANT.minusSeconds(10));
		when(demandeRepository.findByStatutAndDebutPlageTsLessThanEqual(StatutHeuresSup.EN_ATTENTE, MAINTENANT))
				.thenReturn(List.of(moteur));
		when(demandeRepository.findById(moteur.getId())).thenReturn(Optional.of(moteur));

		assertThat(service.idsAExpirer()).isEmpty();
		service.expirer(moteur.getId());
		assertThat(moteur.getStatut()).isEqualTo(StatutHeuresSup.EN_ATTENTE);
		verify(jourRepository, never()).deleteByDemandeId(any());
	}

	@Test
	void annulerParBackoffice_demandeMoteurEnCours_renvoie409DemandeSousWorkflow() {
		PresenceHeuresSupDemande d = new PresenceHeuresSupDemande();
		d.setId(UUID.randomUUID());
		d.setSiteId(siteId);
		d.setDateDebut(DEMAIN);
		d.setDateFin(DEMAIN);
		d.setStatut(StatutHeuresSup.EN_COURS);
		d.setWorkflowMode("MOTEUR");
		d.setDemandeurCompteId(compteRo.toString());
		d.setDemandeurFicheId(ficheRo.toString());
		d.setDebutPlageTs(MAINTENANT.plusSeconds(86_400));
		PresenceHeuresSupBeneficiaire b = new PresenceHeuresSupBeneficiaire();
		b.setCollaborateurId(ficheBenef);
		b.setCompteId(compteBenef);
		when(demandeRepository.findById(d.getId())).thenReturn(Optional.of(d));
		when(beneficiaireRepository.findByDemandeId(d.getId())).thenReturn(List.of(b));
		when(acteurResolver.avecFiche()).thenReturn(
				new Acteur(compteRh.toString(), ficheRh.toString(), "RH", "Amel Trabelsi"));

		assertThatThrownBy(() -> service.annulerParBackoffice(d.getId(), "Annulation back-office"))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(ex -> {
					PresenceMetierException p = (PresenceMetierException) ex;
					assertThat(p.getHttpStatus()).isEqualTo(409);
					assertThat(p.getCode()).isEqualTo("DEMANDE_SOUS_WORKFLOW");
				});
		verify(jourRepository, never()).deleteByDemandeId(any());
	}

	@Test
	void annulerParBackoffice_demandeMoteurValideeAvantPlage_annuleEtSupprimeJours() {
		PresenceHeuresSupDemande d = new PresenceHeuresSupDemande();
		d.setId(UUID.randomUUID());
		d.setSiteId(siteId);
		d.setDateDebut(DEMAIN);
		d.setDateFin(DEMAIN);
		d.setOuvertureAvantMinutes(60);
		d.setOuvertureApresMinutes(0);
		d.setStatut(StatutHeuresSup.VALIDEE);
		d.setWorkflowMode("MOTEUR");
		d.setDemandeurCompteId(compteRo.toString());
		d.setDemandeurFicheId(ficheRo.toString());
		d.setDebutPlageTs(MAINTENANT.plusSeconds(86_400));
		PresenceHeuresSupBeneficiaire b = new PresenceHeuresSupBeneficiaire();
		b.setCollaborateurId(ficheBenef);
		b.setCompteId(compteBenef);
		when(demandeRepository.findById(d.getId())).thenReturn(Optional.of(d));
		when(beneficiaireRepository.findByDemandeId(d.getId())).thenReturn(List.of(b));
		when(acteurResolver.avecFiche()).thenReturn(
				new Acteur(compteRh.toString(), ficheRh.toString(), "RH", "Amel Trabelsi"));

		DemandeHeuresSupResponse rep = service.annulerParBackoffice(d.getId(), "Évènement reporté");

		assertThat(rep.statut()).isEqualTo(StatutHeuresSup.ANNULEE);
		assertThat(rep.motifAnnulation()).isEqualTo("Évènement reporté");
		verify(jourRepository).deleteByDemandeId(d.getId());
		verify(notifier).annulee(any(), any(), any());
	}

	@Test
	void annulerParDemandeurEnModeMoteur_acquiertVerrouPessimisteEnEcriture() {
		UUID instanceId = UUID.randomUUID();
		PresenceHeuresSupDemande d = new PresenceHeuresSupDemande();
		d.setId(UUID.randomUUID());
		d.setSiteId(siteId);
		d.setStatut(StatutHeuresSup.EN_COURS);
		d.setWorkflowMode("MOTEUR");
		d.setWorkflowInstanceId(instanceId);
		d.setDemandeurCompteId(compteRo.toString());
		d.setDebutPlageTs(MAINTENANT.plusSeconds(86_400));

		when(demandeRepository.findById(d.getId())).thenReturn(Optional.of(d));
		when(demandeRepository.findByIdPourDecision(d.getId())).thenReturn(Optional.of(d));
		when(acteurResolver.depuisJwt()).thenReturn(
				new Acteur(compteRo.toString(), ficheRo.toString(), "RO", "Omar"));
		when(moteurClient.annuler(any(), any(), any())).thenReturn(IssueAnnulation.ANNULEE);

		DemandeHeuresSupResponse rep = service.annulerParDemandeur(d.getId());

		assertThat(rep.statut()).isEqualTo(StatutHeuresSup.ANNULEE);
		verify(demandeRepository).findByIdPourDecision(d.getId());
		verify(jourRepository).deleteByDemandeId(d.getId());
	}
}
