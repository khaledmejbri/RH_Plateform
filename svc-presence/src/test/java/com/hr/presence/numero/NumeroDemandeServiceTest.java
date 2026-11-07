package com.hr.presence.numero;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** D-L02 : format, rang par mois (Africa/Tunis), préfixes indépendants, aucun doublon sous concurrence. */
class NumeroDemandeServiceTest {

	private final Map<String, AtomicLong> compteurs = new ConcurrentHashMap<>();
	private final NumeroDemandeService service =
			new NumeroDemandeService(cle -> compteurs.computeIfAbsent(cle, k -> new AtomicLong()).incrementAndGet());

	@Test
	void formatPrefixeMoisRangSurQuatreChiffres() {
		assertThat(service.attribuer(PrefixeNumero.HS, Instant.parse("2026-11-06T10:00:00Z"))).isEqualTo("HS-202611-0001");
		assertThat(service.attribuer(PrefixeNumero.HS, Instant.parse("2026-11-07T10:00:00Z"))).isEqualTo("HS-202611-0002");
	}

	@Test
	void prefixesEtMoisAvecCompteursIndependants() {
		service.attribuer(PrefixeNumero.HS, Instant.parse("2026-11-06T10:00:00Z"));
		assertThat(service.attribuer(PrefixeNumero.PM, Instant.parse("2026-11-06T10:00:00Z"))).isEqualTo("PM-202611-0001");
		assertThat(service.attribuer(PrefixeNumero.HS, Instant.parse("2026-12-02T10:00:00Z"))).isEqualTo("HS-202612-0001");
	}

	@Test
	void moisCalculeEnHeureDeTunis() {
		// 23:30 UTC le 31/10 = 00:30 le 01/11 à Tunis (UTC+1)
		assertThat(service.attribuer(PrefixeNumero.PM, Instant.parse("2026-10-31T23:30:00Z"))).isEqualTo("PM-202611-0001");
		assertThat(service.attribuer(PrefixeNumero.PM, Instant.parse("2026-10-31T22:30:00Z"))).isEqualTo("PM-202610-0001");
	}

	@Test
	void cinqChiffresAuDelaDeNeufMilleNeufCentQuatreVingtDixNeuf() {
		assertThat(NumeroDemandeService.formater(PrefixeNumero.HS, java.time.YearMonth.of(2026, 11), 9999)).isEqualTo("HS-202611-9999");
		assertThat(NumeroDemandeService.formater(PrefixeNumero.HS, java.time.YearMonth.of(2026, 11), 10000)).isEqualTo("HS-202611-10000");
	}

	@Test
	void creationsSimultanees_aucunDoublon() throws Exception {
		int n = 200;
		ExecutorService pool = Executors.newFixedThreadPool(16);
		CountDownLatch depart = new CountDownLatch(1);
		List<Future<String>> futurs = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			futurs.add(pool.submit(() -> {
				depart.await();
				return service.attribuer(PrefixeNumero.HS, Instant.parse("2026-11-06T10:00:00Z"));
			}));
		}
		depart.countDown();
		Set<String> numeros = new HashSet<>();
		for (Future<String> f : futurs) {
			numeros.add(f.get());
		}
		pool.shutdown();
		assertThat(numeros).hasSize(n);
		assertThat(numeros).contains("HS-202611-0001", "HS-202611-0200");
	}

	@Test
	void attributionExigeUneTransactionExistante() throws Exception {
		Transactional t = NumeroDemandeService.class
				.getMethod("attribuer", PrefixeNumero.class, Instant.class).getAnnotation(Transactional.class);
		assertThat(t.propagation()).isEqualTo(Propagation.MANDATORY);
	}
}
