package com.hr.presence.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-P13.d : le planificateur expire les demandes échues, une transaction par demande, sans jamais s'arrêter. */
class HeuresSupExpirationSchedulerTest {

	private final HeuresSupService service = mock(HeuresSupService.class);
	private final HeuresSupExpirationScheduler scheduler = new HeuresSupExpirationScheduler(service);

	@Test
	void expireChaqueDemandeEchue() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		when(service.idsAExpirer()).thenReturn(List.of(a, b));

		scheduler.expirerLesDemandesEchues();

		verify(service).expirer(a);
		verify(service).expirer(b);
	}

	@Test
	void unConflitDeVersionSurUneDemande_nEmpecheLesSuivantes() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		when(service.idsAExpirer()).thenReturn(List.of(a, b));
		doThrow(new RuntimeException("version")).when(service).expirer(a);

		scheduler.expirerLesDemandesEchues();

		verify(service).expirer(b);
	}

	@Test
	void cronEnPropriete_fuseauAfricaTunis_jamaisUneValeurEnDur() throws Exception {
		Scheduled scheduled = HeuresSupExpirationScheduler.class.getMethod("expirerLesDemandesEchues")
				.getAnnotation(Scheduled.class);

		assertThat(scheduled.cron()).startsWith("${presence.heures-sup.expiration-cron:");
		assertThat(scheduled.zone()).isEqualTo("Africa/Tunis");
	}
}
