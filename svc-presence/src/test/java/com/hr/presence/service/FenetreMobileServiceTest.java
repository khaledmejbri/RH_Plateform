package com.hr.presence.service;

import com.hr.presence.config.ClockConfig;
import com.hr.presence.dto.HeuresSupDtos.FenetreDuJourResponse;
import com.hr.presence.entity.PresenceHoraireSite;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.repository.PresenceHoraireSiteRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** D-P16.c : écran de pointage, horaire du jour, fenêtre et journée choisie comme le fait le pointage. */
class FenetreMobileServiceTest {

	private static final String QR = "p1.signe.token";

	private final PresenceSiteRepository siteRepository = mock(PresenceSiteRepository.class);
	private final PresenceHoraireSiteRepository horaireRepository = mock(PresenceHoraireSiteRepository.class);
	private final PresenceHeuresSupJourRepository jourRepository = mock(PresenceHeuresSupJourRepository.class);
	private final PresenceHeuresSupDemandeRepository demandeRepository = mock(PresenceHeuresSupDemandeRepository.class);
	private final QrTokenService qrTokenService = mock(QrTokenService.class);
	private final QrDynamiqueService qrDynamiqueService = mock(QrDynamiqueService.class);
	private final PresenceAccessService accessService = mock(PresenceAccessService.class);
	private PresenceSite site;

	@BeforeEach
	void setUp() {
		site = new PresenceSite();
		site.setId(UUID.randomUUID());
		site.setLibelle("Siège");
		site.setActif(true);
		when(siteRepository.findById(site.getId())).thenReturn(Optional.of(site));
		when(qrTokenService.parseAndVerify(QR)).thenReturn(Optional.of(
				new QrTokenService.QrClaims(site.getId(), 1, UUID.randomUUID(), Instant.now(), Instant.now())));
		when(accessService.currentCompteId()).thenReturn(UUID.randomUUID().toString());
		when(jourRepository.findPourPointeur(any(), anyCollection(), anyString())).thenReturn(List.of());
	}

	private void horaires(LocalTime debut, LocalTime fin) {
		List<PresenceHoraireSite> lignes = new ArrayList<>();
		for (DayOfWeek jour : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
				DayOfWeek.FRIDAY)) {
			PresenceHoraireSite l = new PresenceHoraireSite();
			l.setSiteId(site.getId());
			l.setJourSemaine(jour.getValue());
			l.setHeureDebut(debut);
			l.setHeureFin(fin);
			lignes.add(l);
		}
		when(horaireRepository.findBySiteIdOrderByJourSemaine(site.getId())).thenReturn(lignes);
	}

	private FenetreDuJourResponse a(int jour, int heure, int minute) {
		Instant maintenant = LocalDateTime.of(2026, 11, jour, heure, minute).atZone(ClockConfig.ZONE_TUNIS).toInstant();
		FenetreHoraireService fenetre = new FenetreHoraireService(horaireRepository, jourRepository, demandeRepository);
		return new FenetreMobileService(siteRepository, horaireRepository, qrTokenService, qrDynamiqueService, fenetre,
				accessService, Clock.fixed(maintenant, ClockConfig.ZONE_TUNIS)).fenetreDuJour(QR);
	}

	@Test
	void horaireDeJour_fenetreEtHoraireDuJourScanne() {
		horaires(LocalTime.of(6, 0), LocalTime.of(15, 0));

		FenetreDuJourResponse r = a(3, 10, 0);

		assertThat(r.avecHoraire()).isTrue();
		assertThat(r.ferme()).isFalse();
		assertThat(r.journee()).isEqualTo(LocalDate.of(2026, 11, 3));
		assertThat(r.horaireDebut()).isEqualTo("06:00");
		assertThat(r.horaireFin()).isEqualTo("15:00");
		assertThat(r.ouvertureDebut()).isEqualTo("05:00");
		assertThat(r.ouvertureFin()).isEqualTo("16:00");
	}

	@Test
	void jourFerme_ferme_vrai_siteSansHoraire_ferme_faux() {
		horaires(LocalTime.of(6, 0), LocalTime.of(15, 0));
		FenetreDuJourResponse samedi = a(7, 10, 0);
		assertThat(samedi.ferme()).isTrue();
		assertThat(samedi.horaireDebut()).isNull();

		when(horaireRepository.findBySiteIdOrderByJourSemaine(site.getId())).thenReturn(List.of());
		FenetreDuJourResponse sans = a(3, 10, 0);
		assertThat(sans.avecHoraire()).isFalse();
		assertThat(sans.ferme()).isFalse();
	}

	@Test
	void horaireDeNuit_a02h00_lajourneeEstCelleDeLaVeille() {
		horaires(LocalTime.of(22, 0), LocalTime.of(6, 0));

		// mercredi 4 novembre, 02:00 : la fenêtre de nuit du mardi 3 (21:00 - 07:00) est ouverte
		FenetreDuJourResponse r = a(4, 2, 0);

		assertThat(r.journee()).isEqualTo(LocalDate.of(2026, 11, 3));
		assertThat(r.ferme()).isFalse();
		assertThat(r.horaireDebut()).isEqualTo("22:00");
		assertThat(r.ouvertureDebut()).isEqualTo("21:00");
		assertThat(r.ouvertureFin()).isEqualTo("07:00");
	}

	@Test
	void horaireDeNuit_enJournee_lajourneeEstCelleDAujourdhui() {
		horaires(LocalTime.of(22, 0), LocalTime.of(6, 0));

		FenetreDuJourResponse r = a(4, 12, 0);

		assertThat(r.journee()).isEqualTo(LocalDate.of(2026, 11, 4));
		assertThat(r.ouvertureDebut()).isEqualTo("21:00");
	}

	@Test
	void qrInvalide_422() {
		when(qrTokenService.parseAndVerify("p1.faux")).thenReturn(Optional.empty());
		FenetreHoraireService fenetre = new FenetreHoraireService(horaireRepository, jourRepository, demandeRepository);
		FenetreMobileService service = new FenetreMobileService(siteRepository, horaireRepository, qrTokenService,
				qrDynamiqueService, fenetre, accessService, Clock.systemUTC());

		assertThatThrownBy(() -> service.fenetreDuJour("p1.faux")).isInstanceOfSatisfying(PresenceMetierException.class, e -> {
			assertThat(e.getCode()).isEqualTo("QR_INVALIDE");
			assertThat(e.getHttpStatus()).isEqualTo(422);
		});
	}
}
