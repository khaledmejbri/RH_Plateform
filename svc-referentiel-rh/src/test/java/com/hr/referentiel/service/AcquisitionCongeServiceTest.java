package com.hr.referentiel.service;

import com.hr.referentiel.repository.CollaborateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C1-16 : acquisition mensuelle (éligibilité, N figé, N = 0, mois de mise en service, idempotence, lot continué). */
@ExtendWith(MockitoExtension.class)
class AcquisitionCongeServiceTest {

	private static final YearMonth DECEMBRE = YearMonth.of(2026, 12);

	@Mock
	private AcquisitionFicheService ficheService;
	@Mock
	private ReglagesCongeService reglages;
	@Mock
	private CollaborateurRepository collaborateurRepository;

	private AcquisitionCongeService service;

	@BeforeEach
	void setUp() {
		service = new AcquisitionCongeService(ficheService, reglages, collaborateurRepository,
				Clock.fixed(Instant.parse("2026-11-30T23:30:00Z"), ZoneOffset.UTC));
		lenient().when(reglages.debutAcquisitions()).thenReturn(LocalDate.of(2026, 12, 1));
		lenient().when(ficheService.figerJoursDuMois("2026-12")).thenReturn(new BigDecimal("1.5"));
	}

	@Test
	@DisplayName("C1-16 : 30/11 23:30 UTC = 01/12 à Tunis -> le mois courant est 2026-12")
	void moisCourantATunis() {
		assertThat(service.moisCourant()).isEqualTo(DECEMBRE);
	}

	@Test
	@DisplayName("critère 21 : +1,5 pour chaque fiche éligible ; la limite d'éligibilité est la fin du 1er à Tunis")
	void creditEtLimite() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		when(collaborateurRepository.idsEligiblesAcquisition(any(), any())).thenReturn(List.of(a, b));
		when(ficheService.acquerir(any(), eq("2026-12"), eq(new BigDecimal("1.5")))).thenReturn(true);
		AcquisitionCongeService.Bilan bilan = service.traiter(DECEMBRE);
		assertThat(bilan.creees()).isEqualTo(2);
		assertThat(bilan.eligibles()).isEqualTo(2);
		assertThat(bilan.ignore()).isFalse();
		// fiche créée le 02/12 sans date : exclue car limite = 02/12 00:00 à Tunis (01/12 23:00 UTC)
		verify(collaborateurRepository).idsEligiblesAcquisition(eq(Instant.parse("2026-12-01T23:00:00Z")),
				eq(LocalDate.of(2026, 12, 1)));
	}

	@Test
	@DisplayName("critère 21 : relancé le même jour -> aucune seconde acquisition (déjà faite)")
	void idempotent() {
		when(collaborateurRepository.idsEligiblesAcquisition(any(), any())).thenReturn(List.of(UUID.randomUUID()));
		when(ficheService.acquerir(any(), any(), any())).thenReturn(false);
		AcquisitionCongeService.Bilan bilan = service.traiter(DECEMBRE);
		assertThat(bilan.creees()).isZero();
		assertThat(bilan.dejaFaites()).isEqualTo(1);
	}

	@Test
	@DisplayName("critère 23 : N = 0 au premier passage -> rien ce mois-ci")
	void nulNeCreeRien() {
		when(ficheService.figerJoursDuMois("2026-12")).thenReturn(new BigDecimal("0.0"));
		AcquisitionCongeService.Bilan bilan = service.traiter(DECEMBRE);
		assertThat(bilan.ignore()).isTrue();
		verify(ficheService, never()).acquerir(any(), any(), any());
	}

	@Test
	@DisplayName("D-S07.b : mois avant le début des acquisitions (mois de mise en service) -> ignoré, N non figé")
	void moisDeMiseEnService() {
		AcquisitionCongeService.Bilan bilan = service.traiter(YearMonth.of(2026, 11));
		assertThat(bilan.ignore()).isTrue();
		verify(ficheService, never()).figerJoursDuMois(any());
		verify(collaborateurRepository, never()).idsEligiblesAcquisition(any(), any());
	}

	@Test
	@DisplayName("collision d'unicité sur une fiche ou échec isolé : le lot continue avec les suivantes")
	void lotContinue() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		when(collaborateurRepository.idsEligiblesAcquisition(any(), any())).thenReturn(List.of(a, b, c));
		when(ficheService.acquerir(eq(a), any(), any())).thenThrow(new DataIntegrityViolationException("uk_mvt_solde_acquisition"));
		when(ficheService.acquerir(eq(b), any(), any())).thenThrow(new IllegalStateException("panne"));
		when(ficheService.acquerir(eq(c), any(), any())).thenReturn(true);
		AcquisitionCongeService.Bilan bilan = service.traiter(DECEMBRE);
		assertThat(bilan.creees()).isEqualTo(1);
		assertThat(bilan.dejaFaites()).isEqualTo(1);
		verify(ficheService).acquerir(eq(c), any(), any());
	}
}
