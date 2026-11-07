package com.hr.referentiel.service;

import com.hr.referentiel.repository.NumeroSequenceRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** D-L02 : format, mois Africa/Tunis, unicité sous création concurrente (compteur atomique simulé). */
class NumeroLisibleServiceTest {

	/** Simule l'upsert atomique PostgreSQL : un compteur par clé, incrément indivisible. */
	private static NumeroSequenceRepository repoAtomique(Map<String, AtomicInteger> compteurs) {
		NumeroSequenceRepository repo = mock(NumeroSequenceRepository.class);
		when(repo.incrementer(anyString())).thenAnswer(inv ->
				compteurs.computeIfAbsent(inv.getArgument(0), k -> new AtomicInteger()).incrementAndGet());
		return repo;
	}

	@Test
	void formatQuatreChiffresEtMoisDeTunis() {
		Map<String, AtomicInteger> compteurs = new ConcurrentHashMap<>();
		NumeroLisibleService service = new NumeroLisibleService(repoAtomique(compteurs));

		assertThat(service.attribuer(PrefixeNumero.DEM, Instant.parse("2026-11-06T09:00:00Z"))).isEqualTo("DEM-202611-0001");
		assertThat(service.attribuer(PrefixeNumero.DEM, Instant.parse("2026-11-06T10:00:00Z"))).isEqualTo("DEM-202611-0002");
		assertThat(service.attribuer(PrefixeNumero.DOC, Instant.parse("2026-11-06T09:00:00Z"))).isEqualTo("DOC-202611-0001");
		assertThat(service.attribuer(PrefixeNumero.FOR, Instant.parse("2026-11-06T09:00:00Z"))).isEqualTo("FOR-202611-0001");
	}

	@Test
	void rangDuMoisSelonLeFuseauAfricaTunis() {
		NumeroLisibleService service = new NumeroLisibleService(repoAtomique(new ConcurrentHashMap<>()));
		// 30/11 23:30 UTC = 01/12 00:30 à Tunis (UTC+1) : compté en décembre
		assertThat(service.attribuer(PrefixeNumero.DEM, Instant.parse("2026-11-30T23:30:00Z"))).isEqualTo("DEM-202612-0001");
		// 30/11 22:30 UTC = 30/11 23:30 à Tunis : novembre
		assertThat(service.attribuer(PrefixeNumero.DEM, Instant.parse("2026-11-30T22:30:00Z"))).isEqualTo("DEM-202611-0001");
	}

	@Test
	void cinqChiffresAuDelaDe9999() {
		assertThat(NumeroLisibleService.formater(PrefixeNumero.DEM, "202611", 10000)).isEqualTo("DEM-202611-10000");
	}

	@Test
	void rechercheNormalisee() {
		assertThat(NumeroLisibleService.normaliserRecherche("  dem-202611-0001 ")).isEqualTo("DEM-202611-0001");
		assertThat(NumeroLisibleService.normaliserRecherche("  ")).isNull();
		assertThat(NumeroLisibleService.normaliserRecherche(null)).isNull();
	}

	@Test
	void creationsConcurrentesSansDoublon() throws Exception {
		NumeroLisibleService service = new NumeroLisibleService(repoAtomique(new ConcurrentHashMap<>()));
		int n = 100;
		ExecutorService pool = Executors.newFixedThreadPool(16);
		CountDownLatch depart = new CountDownLatch(1);
		Set<String> numeros = ConcurrentHashMap.newKeySet();
		for (int i = 0; i < n; i++) {
			pool.submit(() -> {
				depart.await();
				numeros.add(service.attribuer(PrefixeNumero.DEM, Instant.parse("2026-11-06T09:00:00Z")));
				return null;
			});
		}
		depart.countDown();
		pool.shutdown();
		assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
		assertThat(numeros).hasSize(n);
		assertThat(numeros).contains("DEM-202611-0001", "DEM-202611-0100");
	}
}
