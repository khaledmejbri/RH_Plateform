package com.hr.evaluation.client;

import com.hr.evaluation.client.LibellesProfilClient.LibellesProfil;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RestLibellesProfilClientTest {

	@Test
	void echecApresSuccesGardeLesDerniersLibellesValides() throws Exception {
		AtomicInteger appels = new AtomicInteger();
		RestLibellesProfilClient client = new RestLibellesProfilClient("http://localhost:1", "x") {
			@Override
			LibellesProfil charger() {
				if (appels.incrementAndGet() == 1) {
					return new LibellesProfil(Map.of("DEV", "Développement"), Map.of());
				}
				throw new IllegalStateException("panne");
			}
		};
		assertThat(client.lire().familles()).containsEntry("DEV", "Développement");

		// force l'expiration du cache puis un échec
		var champ = RestLibellesProfilClient.class.getDeclaredField("expireLe");
		champ.setAccessible(true);
		champ.set(client, java.time.Instant.MIN);

		assertThat(client.lire().familles()).containsEntry("DEV", "Développement");
		assertThat(appels.get()).isEqualTo(2);
	}
}
