package com.hr.presence.service;

import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.SignalVigilance;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.dto.PresenceWorkflowEffetDtos.ActeurEffet;
import com.hr.presence.dto.PresenceWorkflowEffetDtos.RequeteEffet;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.entity.PresenceWorkflowEffet;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.repository.PresenceWorkflowEffetRepository;
import com.hr.presence.service.PresenceWorkflowEffetService.ResultatEffet;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceWorkflowEffetServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-22T10:00:00Z");

	@Mock
	private PresenceDemandeManuelleRepository demandeManuelleRepo;
	@Mock
	private PresencePointageRepository pointageRepo;
	@Mock
	private PresenceSiteRepository siteRepo;
	@Mock
	private FenetreHoraireService fenetreHoraire;
	@Mock
	private PresenceHeuresSupDemandeRepository heuresSupRepo;
	@Mock
	private PresenceHeuresSupJourRepository heuresSupJourRepo;
	@Mock
	private PresenceWorkflowEffetRepository effetRepo;

	private PresenceWorkflowEffetService service;
	private PresenceDemandeManuelle manuel;
	private PresenceHeuresSupDemande heuresSup;
	private PresenceSite site;

	@BeforeEach
	void setUp() {
		service = new PresenceWorkflowEffetService(
				demandeManuelleRepo,
				pointageRepo,
				siteRepo,
				fenetreHoraire,
				heuresSupRepo,
				heuresSupJourRepo,
				effetRepo,
				Clock.fixed(MAINTENANT, ZoneOffset.UTC));

		UUID siteId = UUID.randomUUID();
		site = new PresenceSite();
		site.setId(siteId);
		site.setLibelle("Entrepôt Tunis");

		manuel = new PresenceDemandeManuelle();
		manuel.setId(UUID.randomUUID());
		manuel.setNumero("PM-202611-0001");
		manuel.setCollaborateurId(UUID.randomUUID().toString());
		manuel.setPointageRejeteId(UUID.randomUUID());
		manuel.setSiteId(siteId);
		manuel.setType(PointageType.ENTREE);
		manuel.setHeureDeclaree(MAINTENANT.minusSeconds(3600));
		manuel.setJustification("Badge illisible à l'entrée");
		manuel.setStatut(StatutDemandeManuelle.EN_COURS);
		manuel.setWorkflowMode("MOTEUR");
		manuel.setEtapeCouranteLibelle("Validation du responsable");
		manuel.setEcheanceLe(MAINTENANT.plusSeconds(7200));

		heuresSup = new PresenceHeuresSupDemande();
		heuresSup.setId(UUID.randomUUID());
		heuresSup.setReference("HS-202611-0002");
		heuresSup.setNumero("HS-202611-0002");
		heuresSup.setSiteId(siteId);
		heuresSup.setDateDebut(LocalDate.of(2026, 11, 25));
		heuresSup.setDateFin(LocalDate.of(2026, 11, 25));
		heuresSup.setDebutPlageTs(MAINTENANT.plusSeconds(86_400));
		heuresSup.setDemandeurCompteId(UUID.randomUUID().toString());
		heuresSup.setDemandeurNom("Amel Trabelsi");
		heuresSup.setStatut(StatutHeuresSup.EN_COURS);
		heuresSup.setWorkflowMode("MOTEUR");
		heuresSup.setEtapeCouranteLibelle("Validation RH");

		lenient().when(effetRepo.findByCleIdempotence(any())).thenReturn(Optional.empty());
		lenient().when(demandeManuelleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(heuresSupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void pointageManuel_controlerEtAppliquerApprouver_creePointageEtEffaceEtape() {
		PresencePointage rejet = new PresencePointage();
		rejet.setId(manuel.getPointageRejeteId());
		rejet.setLatitude(36.8);
		rejet.setLongitude(10.18);
		when(siteRepo.findById(manuel.getSiteId())).thenReturn(Optional.of(site));
		when(fenetreHoraire.evaluer(any(), any(), any(), any())).thenReturn(
				new FenetreHoraireService.DansFenetre(
						LocalDate.of(2026, 11, 22),
						ZonedDateTime.now().toInstant(),
						ZonedDateTime.now().plusHours(8).toInstant()));
		when(demandeManuelleRepo.findById(manuel.getId())).thenReturn(Optional.of(manuel));
		when(demandeManuelleRepo.findByIdPourDecision(manuel.getId())).thenReturn(Optional.of(manuel));
		when(pointageRepo.findById(manuel.getPointageRejeteId())).thenReturn(Optional.of(rejet));
		when(pointageRepo.save(any())).thenAnswer(inv -> {
			PresencePointage p = inv.getArgument(0);
			p.setId(UUID.randomUUID());
			return p;
		});

		ActeurEffet acteur = new ActeurEffet(UUID.randomUUID(), UUID.randomUUID(), "Omar Ben Ali", null);
		RequeteEffet ctrl = new RequeteEffet("k1", "CONTROLER", "POINTAGE_MANUEL", manuel.getId(),
				"APPROUVER", acteur, null, UUID.randomUUID(), "Validation du responsable");
		assertThat(service.controler(ctrl).statutHttp()).isEqualTo(200);

		RequeteEffet app = new RequeteEffet("k1", "APPLIQUER", "POINTAGE_MANUEL", manuel.getId(),
				"APPROUVER", acteur, "Justification cohérente", UUID.randomUUID(), "Validation du responsable");
		ResultatEffet res = service.appliquer(app);

		assertThat(res.statutHttp()).isEqualTo(200);
		assertThat(manuel.getStatut()).isEqualTo(StatutDemandeManuelle.VALIDEE);
		assertThat(manuel.getDecidePar()).isEqualTo("Omar Ben Ali");
		assertThat(manuel.getPointageId()).isNotNull();
		assertThat(manuel.getEtapeCouranteLibelle()).isNull();
		assertThat(manuel.getEcheanceLe()).isNull();

		ArgumentCaptor<PresencePointage> pc = ArgumentCaptor.forClass(PresencePointage.class);
		verify(pointageRepo).save(pc.capture());
		assertThat(pc.getValue().getStatut()).isEqualTo(PointageStatut.VALIDE_MANUEL);
		verify(effetRepo).save(any(PresenceWorkflowEffet.class));
	}

	@Test
	void pointageManuel_horsFenetre_renvoie422EnControlerEtAppliquerSansMemoriser() {
		when(demandeManuelleRepo.findById(manuel.getId())).thenReturn(Optional.of(manuel));
		when(demandeManuelleRepo.findByIdPourDecision(manuel.getId())).thenReturn(Optional.of(manuel));
		when(siteRepo.findById(manuel.getSiteId())).thenReturn(Optional.of(site));
		when(fenetreHoraire.evaluer(any(), any(), any(), any()))
				.thenReturn(new FenetreHoraireService.HorsFenetre("Hors plage autorisée"));

		RequeteEffet ctrl = new RequeteEffet("k-hf", "CONTROLER", "POINTAGE_MANUEL", manuel.getId(),
				"APPROUVER", null, null, null, null);
		assertThatThrownBy(() -> service.controler(ctrl))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(ex -> {
					PresenceMetierException p = (PresenceMetierException) ex;
					assertThat(p.getHttpStatus()).isEqualTo(422);
					assertThat(p.getCode()).isEqualTo("HEURE_HORS_FENETRE");
				});

		RequeteEffet app = new RequeteEffet("k-hf", "APPLIQUER", "POINTAGE_MANUEL", manuel.getId(),
				"APPROUVER", null, null, null, null);
		assertThatThrownBy(() -> service.appliquer(app))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(ex -> assertThat(((PresenceMetierException) ex).getHttpStatus()).isEqualTo(422));
		verify(effetRepo, never()).save(any());
	}

	@Test
	void pointageManuel_signaleSansCommentaire_renvoie422() {
		manuel.setSignaux(List.of(SignalVigilance.DISTANCE_SUP_2X_RAYON));
		when(demandeManuelleRepo.findById(manuel.getId())).thenReturn(Optional.of(manuel));

		RequeteEffet ctrl = new RequeteEffet("k-sig", "CONTROLER", "POINTAGE_MANUEL", manuel.getId(),
				"APPROUVER", null, "  ", null, null);
		assertThatThrownBy(() -> service.controler(ctrl))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(ex -> {
					PresenceMetierException p = (PresenceMetierException) ex;
					assertThat(p.getHttpStatus()).isEqualTo(422);
					assertThat(p.getCode()).isEqualTo("COMMENTAIRE_OBLIGATOIRE");
				});
	}

	@Test
	void pointageManuel_refuserSansMotif_renvoie422_avecMotifPasseRefusee() {
		when(demandeManuelleRepo.findById(manuel.getId())).thenReturn(Optional.of(manuel));
		when(demandeManuelleRepo.findByIdPourDecision(manuel.getId())).thenReturn(Optional.of(manuel));

		RequeteEffet sansMotif = new RequeteEffet("k-ref1", "CONTROLER", "POINTAGE_MANUEL", manuel.getId(),
				"REFUSER", null, " ", null, null);
		assertThatThrownBy(() -> service.controler(sansMotif))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(ex -> assertThat(((PresenceMetierException) ex).getHttpStatus()).isEqualTo(422));

		ActeurEffet acteur = new ActeurEffet(UUID.randomUUID(), UUID.randomUUID(), "Sonia RH", null);
		RequeteEffet app = new RequeteEffet("k-ref2", "APPLIQUER", "POINTAGE_MANUEL", manuel.getId(),
				"REFUSER", acteur, "Absent du planning", UUID.randomUUID(), null);
		ResultatEffet res = service.appliquer(app);

		assertThat(res.statutHttp()).isEqualTo(200);
		assertThat(manuel.getStatut()).isEqualTo(StatutDemandeManuelle.REFUSEE);
		assertThat(manuel.getMotifRefus()).isEqualTo("Absent du planning");
		assertThat(manuel.getEtapeCouranteLibelle()).isNull();
	}

	@Test
	void pointageManuel_expirer_passeExpiree() {
		when(demandeManuelleRepo.findByIdPourDecision(manuel.getId())).thenReturn(Optional.of(manuel));
		RequeteEffet app = new RequeteEffet("k-exp", "APPLIQUER", "POINTAGE_MANUEL", manuel.getId(),
				"EXPIRER", null, null, UUID.randomUUID(), null);
		ResultatEffet res = service.appliquer(app);

		assertThat(res.statutHttp()).isEqualTo(200);
		assertThat(manuel.getStatut()).isEqualTo(StatutDemandeManuelle.EXPIREE);
	}

	@Test
	void effetInconnuOuTypeInconnu_renvoie422_jamais400() {
		when(demandeManuelleRepo.findById(manuel.getId())).thenReturn(Optional.of(manuel));
		RequeteEffet effetInconnu = new RequeteEffet("k-inc", "CONTROLER", "POINTAGE_MANUEL", manuel.getId(),
				"METTRE_A_COMPLETER", null, null, null, null);
		assertThatThrownBy(() -> service.controler(effetInconnu))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(ex -> assertThat(((PresenceMetierException) ex).getHttpStatus()).isEqualTo(422));

		RequeteEffet typeInconnu = new RequeteEffet("k-type", "CONTROLER", "CONGE", manuel.getId(),
				"APPROUVER", null, null, null, null);
		assertThatThrownBy(() -> service.controler(typeInconnu))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(ex -> assertThat(((PresenceMetierException) ex).getHttpStatus()).isEqualTo(422));
	}

	@Test
	void heuresSup_approuverRefuserExpirerAnnulerEtIdempotence() {
		heuresSup.setCreeeValidee(true);
		when(heuresSupRepo.findByIdPourDecision(heuresSup.getId())).thenReturn(Optional.of(heuresSup));

		// APPROUVER immédiat avec acteur Système sans compte → conserve le demandeur
		RequeteEffet app = new RequeteEffet("hs-app", "APPLIQUER", "HEURES_SUP", heuresSup.getId(),
				"APPROUVER", new ActeurEffet(null, null, "Système", null), null, UUID.randomUUID(), null);
		assertThat(service.appliquer(app).statutHttp()).isEqualTo(200);
		assertThat(heuresSup.getStatut()).isEqualTo(StatutHeuresSup.VALIDEE);
		assertThat(heuresSup.getDecideParNom()).isEqualTo("Amel Trabelsi");

		// ANNULER avant debutPlageTs sur VALIDEE → ANNULEE + suppression jours
		RequeteEffet ann = new RequeteEffet("hs-ann", "APPLIQUER", "HEURES_SUP", heuresSup.getId(),
				"ANNULER", new ActeurEffet(UUID.randomUUID(), null, "Amel Trabelsi", null),
				"Chantier reporté", UUID.randomUUID(), null);
		assertThat(service.appliquer(ann).statutHttp()).isEqualTo(200);
		assertThat(heuresSup.getStatut()).isEqualTo(StatutHeuresSup.ANNULEE);
		verify(heuresSupJourRepo).deleteByDemandeId(heuresSup.getId());

		// Déjà terminée avec nouvelle clé → 409 mémorisé
		RequeteEffet deja = new RequeteEffet("hs-deja", "APPLIQUER", "HEURES_SUP", heuresSup.getId(),
				"REFUSER", null, "Motif", UUID.randomUUID(), null);
		ResultatEffet res409 = service.appliquer(deja);
		assertThat(res409.statutHttp()).isEqualTo(409);
		assertThat(res409.corps()).containsEntry("code", "OBJET_DEJA_DECIDE");
		verify(effetRepo).save(org.mockito.ArgumentMatchers.argThat(e ->
				"hs-deja".equals(e.getCleIdempotence()) && e.getStatutHttp() == 409));

		// Rejeu idempotent sur clé mémorisée → renvoie la réponse cachée sans recharger la demande
		PresenceWorkflowEffet cache = new PresenceWorkflowEffet(
				"hs-replay", "HEURES_SUP", heuresSup.getId(), "APPROUVER", "VALIDEE", 200, Map.of("statut", "VALIDEE"));
		when(effetRepo.findByCleIdempotence("hs-replay")).thenReturn(Optional.of(cache));
		ResultatEffet rejeu = service.appliquer(new RequeteEffet("hs-replay", "APPLIQUER", "HEURES_SUP",
				heuresSup.getId(), "APPROUVER", null, null, null, null));
		assertThat(rejeu.statutHttp()).isEqualTo(200);
		assertThat(rejeu.corps()).containsEntry("statut", "VALIDEE");
	}

	@Test
	void heuresSup_annulerApresDebutPlage_renvoie422() {
		heuresSup.setStatut(StatutHeuresSup.VALIDEE);
		heuresSup.setDebutPlageTs(MAINTENANT.minusSeconds(60));
		when(heuresSupRepo.findById(heuresSup.getId())).thenReturn(Optional.of(heuresSup));

		RequeteEffet ann = new RequeteEffet("hs-tard", "CONTROLER", "HEURES_SUP", heuresSup.getId(),
				"ANNULER", null, null, null, null);
		assertThatThrownBy(() -> service.controler(ann))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(ex -> {
					PresenceMetierException p = (PresenceMetierException) ex;
					assertThat(p.getHttpStatus()).isEqualTo(422);
					assertThat(p.getCode()).isEqualTo("PLAGE_DEJA_COMMENCEE");
				});
	}
}
