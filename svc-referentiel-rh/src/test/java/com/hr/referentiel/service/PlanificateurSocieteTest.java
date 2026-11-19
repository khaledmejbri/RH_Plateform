package com.hr.referentiel.service;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocietesActives;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests des planificateurs multi-société (§ 9.2.1, § 10.3) : boucle sur {@link SocietesActives}, verrou par société
 * et isolation des erreurs (une erreur sur la société A n'arrête pas le passage sur la société B).
 */
class PlanificateurSocieteTest {

	private static final UUID SOC_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID SOC_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	@Test
	@DisplayName("AcquisitionCongeScheduler : itère sur chaque société active, pose SocieteContexte et continue sur B si A échoue")
	void acquisitionContinueSiUneSocieteEchoue() {
		SocietesActives societesActives = () -> List.of(SOC_A, SOC_B);
		VerrouPlanificateur verrou = mock(VerrouPlanificateur.class);
		when(verrou.executerSiLibre(anyString(), any())).thenAnswer(inv -> {
			Runnable r = inv.getArgument(1);
			r.run();
			return true;
		});

		AcquisitionCongeService acquisitionService = mock(AcquisitionCongeService.class);
		when(acquisitionService.moisCourant()).thenReturn(YearMonth.of(2026, 12));

		List<UUID> societesTraitees = new ArrayList<>();
		doAnswer(inv -> {
			UUID courante = SocieteContexte.courante();
			societesTraitees.add(courante);
			if (SOC_A.equals(courante)) {
				throw new IllegalStateException("Panne simulée sur société A");
			}
			return null;
		}).when(acquisitionService).traiter(any());

		AcquisitionCongeScheduler scheduler = new AcquisitionCongeScheduler(
				acquisitionService, verrou, societesActives, false);
		scheduler.passageQuotidien();

		assertThat(societesTraitees).containsExactly(SOC_A, SOC_B);
		assertThat(SocieteContexte.couranteOuVide()).isEmpty();
	}
}
