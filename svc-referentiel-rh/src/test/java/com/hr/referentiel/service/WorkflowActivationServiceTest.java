package com.hr.referentiel.service;

import com.github.benmanes.caffeine.cache.Ticker;
import com.hr.referentiel.client.WorkflowMoteurClient;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowActivationServiceTest {

	@Mock
	private WorkflowMoteurClient client;

	@Test
	void modeEstMisEnCachePendant30Secondes() {
		AtomicLong horloge = new AtomicLong(0L);
		Ticker ticker = horloge::get;
		WorkflowActivationService service = new WorkflowActivationService(client, ticker);

		when(client.modeActivation("CONGE")).thenReturn(Optional.of("MOTEUR"));

		assertThat(service.mode(TypeDemandeAdministrativeRh.CONGE)).isEqualTo("MOTEUR");
		horloge.addAndGet(Duration.ofSeconds(29).toNanos());
		assertThat(service.mode(TypeDemandeAdministrativeRh.CONGE)).isEqualTo("MOTEUR");
		verify(client, times(1)).modeActivation("CONGE");

		// Après 30 s, le cache expire et le moteur est interrogé à nouveau
		when(client.modeActivation("CONGE")).thenReturn(Optional.of("HERITE"));
		horloge.addAndGet(Duration.ofSeconds(2).toNanos());
		assertThat(service.mode(TypeDemandeAdministrativeRh.CONGE)).isEqualTo("HERITE");
		verify(client, times(2)).modeActivation("CONGE");
	}

	@Test
	void moteurInjoignableRenvoieHeriteSansMettreEnCache() {
		AtomicLong horloge = new AtomicLong(0L);
		WorkflowActivationService service = new WorkflowActivationService(client, horloge::get);

		when(client.modeActivation("CONGE"))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of("MOTEUR"));

		// Premier appel : moteur injoignable → HERITE, pas de cache
		assertThat(service.mode("CONGE")).isEqualTo("HERITE");
		// Appel immédiat suivant : le moteur est réinterrogé et répond MOTEUR
		assertThat(service.mode("CONGE")).isEqualTo("MOTEUR");
		verify(client, times(2)).modeActivation("CONGE");
	}

	@Test
	void reponseIllisibleRenvoieHeriteSansMettreEnCache() {
		WorkflowActivationService service = new WorkflowActivationService(client, Ticker.systemTicker());
		when(client.modeActivation("ORDRE_MISSION"))
				.thenReturn(Optional.of("INCONNU"))
				.thenReturn(Optional.of("MOTEUR"));

		assertThat(service.mode(TypeDemandeAdministrativeRh.ORDRE_MISSION)).isEqualTo("HERITE");
		assertThat(service.mode(TypeDemandeAdministrativeRh.ORDRE_MISSION)).isEqualTo("MOTEUR");
		verify(client, times(2)).modeActivation("ORDRE_MISSION");
	}
}
