package com.hr.presence.config;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.domain.ModeQr;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.SignalVigilance;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.service.DemandeManuelleReponseMapper;
import com.hr.presence.service.EligibiliteManuelService;
import com.hr.presence.service.FenetreHoraireService;
import com.hr.presence.service.HaversineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-P26.a : reprise des demandes en attente à la mise en service (échéance depuis le démarrage, idempotente). */
class DemandeManuelleRepriseRunnerTest {

	private static final Instant DEMARRAGE = Instant.parse("2026-11-11T09:00:00Z");

	private final PresenceDemandeManuelleRepository demandeRepository = mock(PresenceDemandeManuelleRepository.class);
	private final PresencePointageRepository pointageRepository = mock(PresencePointageRepository.class);
	private final PresenceSiteRepository siteRepository = mock(PresenceSiteRepository.class);
	private final PresenceProperties properties = new PresenceProperties();
	private DemandeManuelleRepriseRunner runner;
	private final UUID siteId = UUID.randomUUID();
	private PresencePointage rejet;

	@BeforeEach
	void setUp() {
		FenetreHoraireService fenetre = mock(FenetreHoraireService.class);
		runner = new DemandeManuelleRepriseRunner(mock(JdbcTemplate.class), mock(PlatformTransactionManager.class),
				demandeRepository, pointageRepository, siteRepository,
				new EligibiliteManuelService(properties, new HaversineService(), fenetre),
				new DemandeManuelleReponseMapper(demandeRepository, pointageRepository, mock(ReferentielClient.class), properties),
				properties, Clock.fixed(DEMARRAGE, ZoneId.of("Africa/Tunis")));

		PresenceSite site = new PresenceSite();
		site.setId(siteId);
		site.setRayonMetres(50);
		site.setModeQr(ModeQr.PAPIER);
		when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));

		rejet = new PresencePointage();
		rejet.setId(UUID.randomUUID());
		rejet.setSiteId(siteId);
		rejet.setType(PointageType.ENTREE);
		rejet.setStatut(PointageStatut.REJETE_HORS_ZONE);
		rejet.setServerTs(Instant.parse("2026-11-09T07:00:00Z")); // plus de 24 h avant : pas d'expiration rétroactive
		rejet.setDistanceMetres(180.0);
		rejet.setAccuracyMetres(20.0);
		when(pointageRepository.findById(rejet.getId())).thenReturn(Optional.of(rejet));
	}

	private PresenceDemandeManuelle enAttenteSansEcheance() {
		PresenceDemandeManuelle d = new PresenceDemandeManuelle();
		d.setId(UUID.randomUUID());
		d.setPointageRejeteId(rejet.getId());
		d.setCollaborateurId(UUID.randomUUID().toString());
		d.setStatut(StatutDemandeManuelle.EN_ATTENTE);
		d.setHeureDeclaree(rejet.getServerTs());
		d.setCreeLe(Instant.parse("2026-11-09T07:50:00Z"));
		return d;
	}

	@Test
	void reprise_echeanceDepuisLeDemarrage_signauxEtMesuresDepuisLaTentative_plafondNul() {
		PresenceDemandeManuelle d = enAttenteSansEcheance();
		when(demandeRepository.findByStatutAndEcheanceIsNull(StatutDemandeManuelle.EN_ATTENTE)).thenReturn(List.of(d));

		assertThat(runner.reprendre()).isEqualTo(1);

		assertThat(d.getEcheance()).isEqualTo(DEMARRAGE.plusSeconds(24 * 3600));
		assertThat(d.isReprise()).isTrue();
		assertThat(d.getPlafondMetres()).isNull();
		assertThat(d.getRefusStatut()).isEqualTo("REJETE_HORS_ZONE");
		assertThat(d.getDistanceMetres()).isEqualTo(180.0);
		assertThat(d.getRayonMetres()).isEqualTo(50);
		assertThat(d.getModeQr()).isEqualTo("PAPIER");
		assertThat(d.getDelaiDemandeMinutes()).isEqualTo(50L);
		assertThat(d.getSignaux()).containsExactlyInAnyOrder(SignalVigilance.DISTANCE_SUP_2X_RAYON,
				SignalVigilance.SITE_QR_PAPIER, SignalVigilance.DEMANDE_TARDIVE);
		assertThat(d.getStatut()).isEqualTo(StatutDemandeManuelle.EN_ATTENTE);
		verify(demandeRepository).save(d);
	}

	@Test
	void reprise_idempotente_uneDemandeQuiADejaUneEcheanceNEstPasSelectionnee() {
		PresenceDemandeManuelle d = enAttenteSansEcheance();
		List<PresenceDemandeManuelle> sansEcheance = new ArrayList<>(List.of(d));
		// Le dépôt ne renvoie que les demandes sans échéance : après le premier passage, plus aucune.
		when(demandeRepository.findByStatutAndEcheanceIsNull(StatutDemandeManuelle.EN_ATTENTE))
				.thenAnswer(inv -> d.getEcheance() == null ? sansEcheance : List.of());

		assertThat(runner.reprendre()).isEqualTo(1);
		Instant premiere = d.getEcheance();
		assertThat(runner.reprendre()).isZero();

		assertThat(d.getEcheance()).isEqualTo(premiere);
		verify(demandeRepository, times(1)).save(d);
	}

	@Test
	void reprise_sansDemandeAReprendre_neSauveRien() {
		when(demandeRepository.findByStatutAndEcheanceIsNull(StatutDemandeManuelle.EN_ATTENTE)).thenReturn(List.of());

		assertThat(runner.reprendre()).isZero();
		verify(demandeRepository, never()).save(any());
	}

	@Test
	void reprise_tentativeIntrouvable_echeanceQuandMeme_sansMesures() {
		PresenceDemandeManuelle d = enAttenteSansEcheance();
		d.setPointageRejeteId(UUID.randomUUID());
		when(demandeRepository.findByStatutAndEcheanceIsNull(StatutDemandeManuelle.EN_ATTENTE)).thenReturn(List.of(d));

		runner.reprendre();

		assertThat(d.getEcheance()).isEqualTo(DEMARRAGE.plusSeconds(24 * 3600));
		assertThat(d.isReprise()).isTrue();
		assertThat(d.getRefusStatut()).isNull();
		assertThat(d.getSignaux()).isEmpty();
	}

	@Test
	void reprise_delaiDeDecisionReglable_estCeluiDuDemarrage() {
		properties.getManuel().setDelaiDecisionMinutes(240);
		PresenceDemandeManuelle d = enAttenteSansEcheance();
		when(demandeRepository.findByStatutAndEcheanceIsNull(StatutDemandeManuelle.EN_ATTENTE)).thenReturn(List.of(d));

		runner.reprendre();

		assertThat(d.getEcheance()).isEqualTo(DEMARRAGE.plusSeconds(4 * 3600));
	}
}
