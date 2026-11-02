package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.dto.DemandeManuelleCreationRequest;
import com.hr.presence.dto.DemandeManuelleResponse;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-P04 (SCRUM-38) : demande, validation et refus d'un pointage manuel.
 */
@ExtendWith(MockitoExtension.class)
class PresenceDemandeManuelleServiceTest {

	@Mock
	private PresenceDemandeManuelleRepository demandeRepository;
	@Mock
	private PresencePointageRepository pointageRepository;
	@Mock
	private ReferentielClient referentielClient;
	@Mock
	private PresenceAccessService accessService;
	@Mock
	private PresenceEventPublisher eventPublisher;
	@Mock
	private com.hr.presence.repository.PresenceSiteRepository siteRepository;
	@Mock
	private FenetreHoraireService fenetreHoraire;

	private PresenceDemandeManuelleService service;

	private final UUID ficheCollab = UUID.randomUUID();
	private final UUID compteCollab = UUID.randomUUID();
	private final UUID ficheRo = UUID.randomUUID();
	private final UUID compteRo = UUID.randomUUID();
	private PresencePointage rejet;

	@BeforeEach
	void setUp() {
		service = new PresenceDemandeManuelleService(demandeRepository, pointageRepository, referentielClient,
				accessService, eventPublisher, new PresenceProperties(), siteRepository, fenetreHoraire);
		rejet = new PresencePointage();
		rejet.setId(UUID.randomUUID());
		rejet.setCollaborateurId(ficheCollab.toString());
		rejet.setType(PointageType.ENTREE);
		rejet.setStatut(PointageStatut.REJETE_PRECISION_GPS);
		rejet.setServerTs(Instant.now().minus(10, ChronoUnit.MINUTES));
		rejet.setLatitude(36.8);
		rejet.setLongitude(10.18);
		rejet.setMotifRejet("Signal GPS trop faible");
		lenient().when(pointageRepository.findById(rejet.getId())).thenReturn(Optional.of(rejet));
		lenient().when(demandeRepository.save(any(PresenceDemandeManuelle.class))).thenAnswer(inv -> {
			PresenceDemandeManuelle d = inv.getArgument(0);
			if (d.getId() == null) {
				d.setId(UUID.randomUUID());
			}
			return d;
		});
		lenient().when(pointageRepository.save(any(PresencePointage.class))).thenAnswer(inv -> {
			PresencePointage p = inv.getArgument(0);
			if (p.getId() == null) {
				p.setId(UUID.randomUUID());
			}
			return p;
		});
	}

	private ValideurPointage collab(UUID valideurFiche, UUID valideurCompte) {
		return new ValideurPointage(ficheCollab, compteCollab, "Sarra Khemiri", valideurFiche, valideurCompte,
				valideurFiche != null ? "Omar Belhadj" : null);
	}

	private ValideurPointage ro() {
		return new ValideurPointage(ficheRo, compteRo, "Omar Belhadj", UUID.randomUUID(), UUID.randomUUID(), "Chef");
	}

	private PresenceDemandeManuelle demandeEnAttente() {
		PresenceDemandeManuelle d = new PresenceDemandeManuelle();
		d.setId(UUID.randomUUID());
		d.setPointageRejeteId(rejet.getId());
		d.setCollaborateurId(ficheCollab.toString());
		d.setCollaborateurCompteId(compteCollab.toString());
		d.setCollaborateurNom("Sarra Khemiri");
		d.setType(PointageType.ENTREE);
		d.setHeureDeclaree(rejet.getServerTs());
		d.setJustification("GPS faible au rez-de-chaussée");
		d.setValideurId(ficheRo.toString());
		d.setValideurCompteId(compteRo.toString());
		when(demandeRepository.findById(d.getId())).thenReturn(Optional.of(d));
		return d;
	}

	@Test
	void creation_notifieLeCompteDuResponsable() {
		when(referentielClient.monValideurPointage()).thenReturn(collab(ficheRo, compteRo));

		DemandeManuelleResponse r = service.creer(new DemandeManuelleCreationRequest(
				rejet.getId(), null, null, "GPS faible au rez-de-chaussée"));

		assertThat(r.statut()).isEqualTo(StatutDemandeManuelle.EN_ATTENTE);
		assertThat(r.valideurNom()).isEqualTo("Omar Belhadj");
		assertThat(r.heureDeclaree()).isEqualTo(rejet.getServerTs());
		verify(eventPublisher).notifier(eq(compteRo.toString()), startsWith("Pointage manuel à valider"), anyString());
	}

	@Test
	void creationSansResponsable_notifieLesRh() {
		when(referentielClient.monValideurPointage()).thenReturn(collab(null, null));

		DemandeManuelleResponse r = service.creer(new DemandeManuelleCreationRequest(
				rejet.getId(), null, null, "GPS faible au rez-de-chaussée"));

		assertThat(r.validationParRh()).isTrue();
		verify(eventPublisher).notifier(eq(PresenceEventPublisher.DESTINATAIRE_RH), anyString(), anyString());
	}

	@Test
	void creationSurLePointageDunAutre_403() {
		rejet.setCollaborateurId(UUID.randomUUID().toString());
		when(referentielClient.monValideurPointage()).thenReturn(collab(ficheRo, compteRo));

		assertThatThrownBy(() -> service.creer(new DemandeManuelleCreationRequest(
				rejet.getId(), null, null, "GPS faible au rez-de-chaussée")))
				.isInstanceOf(SecurityException.class);
	}

	@Test
	void creationSurUnPointageValide_refusee() {
		rejet.setStatut(PointageStatut.VALIDE);
		when(referentielClient.monValideurPointage()).thenReturn(collab(ficheRo, compteRo));

		assertThatThrownBy(() -> service.creer(new DemandeManuelleCreationRequest(
				rejet.getId(), null, null, "GPS faible au rez-de-chaussée")))
				.isInstanceOf(PresenceMetierException.class);
	}

	@Test
	void secondeDemandePourLeMemeRejet_409() {
		when(referentielClient.monValideurPointage()).thenReturn(collab(ficheRo, compteRo));
		when(demandeRepository.existsByPointageRejeteId(rejet.getId())).thenReturn(true);

		assertThatThrownBy(() -> service.creer(new DemandeManuelleCreationRequest(
				rejet.getId(), null, null, "GPS faible au rez-de-chaussée")))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(e -> assertThat(((PresenceMetierException) e).getHttpStatus()).isEqualTo(409));
	}

	@Test
	void heureFuture_refusee() {
		when(referentielClient.monValideurPointage()).thenReturn(collab(ficheRo, compteRo));

		assertThatThrownBy(() -> service.creer(new DemandeManuelleCreationRequest(
				rejet.getId(), null, Instant.now().plus(2, ChronoUnit.HOURS), "GPS faible au rez-de-chaussée")))
				.isInstanceOf(PresenceMetierException.class);
	}

	@Test
	void validationParLeResponsable_creeUnPointageValideManuel() {
		PresenceDemandeManuelle d = demandeEnAttente();
		when(referentielClient.monValideurPointage()).thenReturn(ro());

		DemandeManuelleResponse r = service.valider(d.getId());

		assertThat(r.statut()).isEqualTo(StatutDemandeManuelle.VALIDEE);
		assertThat(r.decidePar()).isEqualTo("Omar Belhadj");
		ArgumentCaptor<PresencePointage> p = ArgumentCaptor.forClass(PresencePointage.class);
		verify(pointageRepository).save(p.capture());
		assertThat(p.getValue().getStatut()).isEqualTo(PointageStatut.VALIDE_MANUEL);
		assertThat(p.getValue().getServerTs()).isEqualTo(d.getHeureDeclaree());
		assertThat(p.getValue().getDemandeManuelleId()).isEqualTo(d.getId());
		verify(eventPublisher).notifier(eq(compteCollab.toString()), eq("Pointage manuel validé"), anyString());
	}

	@Test
	void validationParUnAutreResponsable_403() {
		PresenceDemandeManuelle d = demandeEnAttente();
		when(referentielClient.monValideurPointage()).thenReturn(new ValideurPointage(
				UUID.randomUUID(), UUID.randomUUID(), "Autre RO", null, null, null));

		assertThatThrownBy(() -> service.valider(d.getId())).isInstanceOf(SecurityException.class);
		verify(pointageRepository, never()).save(any());
	}

	@Test
	void validationDeSaPropreDemande_403() {
		PresenceDemandeManuelle d = demandeEnAttente();
		d.setValideurId(ficheCollab.toString());
		when(referentielClient.monValideurPointage()).thenReturn(collab(ficheRo, compteRo));

		assertThatThrownBy(() -> service.valider(d.getId())).isInstanceOf(SecurityException.class);
	}

	@Test
	void rhNeValidePasSaPropreDemande() {
		PresenceDemandeManuelle d = demandeEnAttente();
		when(accessService.isBackofficeEcriture()).thenReturn(true);
		when(accessService.currentCompteId()).thenReturn(compteCollab.toString());

		assertThatThrownBy(() -> service.valider(d.getId())).isInstanceOf(SecurityException.class);
	}

	@Test
	void rhValideUneDemandeSansResponsable() {
		PresenceDemandeManuelle d = demandeEnAttente();
		d.setValideurId(null);
		when(accessService.isBackofficeEcriture()).thenReturn(true);
		when(accessService.currentCompteId()).thenReturn(UUID.randomUUID().toString());
		when(referentielClient.monValideurPointage()).thenReturn(new ValideurPointage(
				UUID.randomUUID(), UUID.randomUUID(), "Leila DRH", null, null, null));

		DemandeManuelleResponse r = service.valider(d.getId());

		assertThat(r.decidePar()).isEqualTo("Leila DRH (RH)");
	}

	@Test
	void auDelaDuSeuilMensuel_alerteRh() {
		PresenceDemandeManuelle d = demandeEnAttente();
		when(referentielClient.monValideurPointage()).thenReturn(ro());
		when(demandeRepository.compterValideesSurPeriode(eq(ficheCollab.toString()), any(), any())).thenReturn(4L);

		DemandeManuelleResponse r = service.valider(d.getId());

		assertThat(r.alerteSeuil()).isTrue();
		verify(eventPublisher).notifier(eq(PresenceEventPublisher.DESTINATAIRE_RH), startsWith("Pointages manuels fréquents"), anyString());
	}

	@Test
	void demandeDejaTraitee_409() {
		PresenceDemandeManuelle d = demandeEnAttente();
		d.setStatut(StatutDemandeManuelle.REFUSEE);

		assertThatThrownBy(() -> service.valider(d.getId()))
				.isInstanceOf(PresenceMetierException.class)
				.satisfies(e -> assertThat(((PresenceMetierException) e).getHttpStatus()).isEqualTo(409));
	}

	@Test
	void refus_notifieLeDemandeurAvecLeMotif() {
		PresenceDemandeManuelle d = demandeEnAttente();
		when(referentielClient.monValideurPointage()).thenReturn(ro());

		DemandeManuelleResponse r = service.refuser(d.getId(), "Absent ce matin-là");

		assertThat(r.statut()).isEqualTo(StatutDemandeManuelle.REFUSEE);
		assertThat(r.motifRefus()).isEqualTo("Absent ce matin-là");
		verify(eventPublisher).notifier(eq(compteCollab.toString()), eq("Pointage manuel refusé"), anyString());
		verify(pointageRepository, never()).save(any());
	}

	// --- D-P12.c : l'heure déclarée doit tomber dans la fenêtre du site ---

	private void rejetSurSiteAHoraire(FenetreHoraireService.Resultat resultat) {
		UUID siteId = UUID.randomUUID();
		rejet.setSiteId(siteId);
		com.hr.presence.entity.PresenceSite site = new com.hr.presence.entity.PresenceSite();
		site.setId(siteId);
		when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));
		when(fenetreHoraire.evaluer(any(), any(), any(), any())).thenReturn(resultat);
	}

	@Test
	void creation_heureHorsFenetre_422() {
		rejetSurSiteAHoraire(new FenetreHoraireService.HorsFenetre("Pointage ouvert de 05:00 à 16:00"));
		when(referentielClient.monValideurPointage()).thenReturn(collab(ficheRo, compteRo));

		assertThatThrownBy(() -> service.creer(new DemandeManuelleCreationRequest(
				rejet.getId(), null, null, "Incident réseau")))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getCode()).isEqualTo("HEURE_HORS_FENETRE");
					assertThat(e.getHttpStatus()).isEqualTo(422);
				});
		verify(demandeRepository, never()).save(any(PresenceDemandeManuelle.class));
	}

	@Test
	void creation_heureDansLaFenetre_201() {
		rejetSurSiteAHoraire(new FenetreHoraireService.DansFenetre(java.time.LocalDate.now(), Instant.now(), Instant.now()));
		when(referentielClient.monValideurPointage()).thenReturn(collab(ficheRo, compteRo));

		DemandeManuelleResponse r = service.creer(new DemandeManuelleCreationRequest(
				rejet.getId(), null, null, "Incident réseau"));

		assertThat(r.statut()).isEqualTo(StatutDemandeManuelle.EN_ATTENTE);
	}

	@Test
	void validation_horaireModifieEntreTemps_heureDevenueHorsFenetre_422() {
		PresenceDemandeManuelle d = demandeEnAttente();
		d.setSiteId(UUID.randomUUID());
		com.hr.presence.entity.PresenceSite site = new com.hr.presence.entity.PresenceSite();
		when(siteRepository.findById(d.getSiteId())).thenReturn(Optional.of(site));
		when(fenetreHoraire.evaluer(any(), any(), any(), any()))
				.thenReturn(new FenetreHoraireService.HorsFenetre("Site fermé aujourd'hui"));
		when(accessService.isBackofficeEcriture()).thenReturn(false);
		when(referentielClient.monValideurPointage()).thenReturn(ro());
		// le responsable attendu est la fiche de ro()
		d.setValideurId(ficheRo.toString());

		assertThatThrownBy(() -> service.valider(d.getId()))
				.isInstanceOfSatisfying(PresenceMetierException.class,
						e -> assertThat(e.getCode()).isEqualTo("HEURE_HORS_FENETRE"));
		verify(pointageRepository, never()).save(any(PresencePointage.class));
	}

	@Test
	void validation_dansLaFenetre_poseLInstantaneSurLePointageManuel() {
		PresenceDemandeManuelle d = demandeEnAttente();
		d.setSiteId(UUID.randomUUID());
		com.hr.presence.entity.PresenceSite site = new com.hr.presence.entity.PresenceSite();
		when(siteRepository.findById(d.getSiteId())).thenReturn(Optional.of(site));
		java.time.LocalDate journee = java.time.LocalDate.of(2026, 11, 3);
		Instant debut = Instant.parse("2026-11-03T05:00:00Z");
		Instant fin = Instant.parse("2026-11-03T14:00:00Z");
		when(fenetreHoraire.evaluer(any(), any(), any(), any()))
				.thenReturn(new FenetreHoraireService.DansFenetre(journee, debut, fin));
		when(accessService.isBackofficeEcriture()).thenReturn(false);
		when(referentielClient.monValideurPointage()).thenReturn(ro());

		service.valider(d.getId());

		ArgumentCaptor<PresencePointage> captor = ArgumentCaptor.forClass(PresencePointage.class);
		verify(pointageRepository).save(captor.capture());
		assertThat(captor.getValue().getStatut()).isEqualTo(PointageStatut.VALIDE_MANUEL);
		assertThat(captor.getValue().getJournee()).isEqualTo(journee);
		assertThat(captor.getValue().getHoraireDebutTs()).isEqualTo(debut);
	}
}
