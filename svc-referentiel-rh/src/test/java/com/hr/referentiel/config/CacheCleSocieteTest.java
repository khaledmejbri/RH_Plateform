package com.hr.referentiel.config;

import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.config.CacheConfig.CleSociete;
import com.hr.referentiel.repository.JourFerieRepository;
import com.hr.referentiel.repository.ParametreValeurRepository;
import com.hr.referentiel.service.JourFerieService;
import com.hr.referentiel.service.JournalParametres;
import com.hr.referentiel.service.ParametresCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vérifie que les caches Caffeine de {@code svc-referentiel-rh} sont cloisonnés par {@link CleSociete} (§ 10.3).
 */
class CacheCleSocieteTest {

	private static final UUID SOC_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID SOC_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-19T09:00:00Z"), ZoneOffset.UTC);

	@Test
	@DisplayName("Jours fériés et paramètres : deux sociétés ont des entrées de cache distinctes et une invalidation sur A n'évince pas B")
	void cachesIsolesParSociete() {
		CacheManager cacheManager = new CacheConfig().cacheManager(30L);
		JourFerieRepository jfRepo = mock(JourFerieRepository.class);

		when(jfRepo.datesActives()).thenAnswer(inv ->
				SOC_A.equals(SocieteContexte.courante())
						? List.of(LocalDate.of(2026, 3, 20))
						: List.of(LocalDate.of(2026, 5, 1)));

		JourFerieService jfService = new JourFerieService(
				jfRepo, mock(JournalParametres.class), HORLOGE, 30L);

		Set<LocalDate> datesA = SocieteContexte.executer(SOC_A, jfService::datesActives);
		Set<LocalDate> datesB = SocieteContexte.executer(SOC_B, jfService::datesActives);

		assertThat(datesA).containsExactly(LocalDate.of(2026, 3, 20));
		assertThat(datesB).containsExactly(LocalDate.of(2026, 5, 1));

		// Deuxième lecture : servie depuis le cache Caffeine sans rappeler le dépôt
		SocieteContexte.executer(SOC_A, jfService::datesActives);
		SocieteContexte.executer(SOC_B, jfService::datesActives);
		verify(jfRepo, times(2)).datesActives();

		// ParametresCache : invalidation ciblée sur SOC_A conserve l'entrée de SOC_B
		ParametreValeurRepository pvRepo = mock(ParametreValeurRepository.class);
		when(pvRepo.findAll()).thenReturn(List.of());
		ParametresCache pCache = new ParametresCache(cacheManager, pvRepo);

		SocieteContexte.executer(SOC_A, pCache::instantane);
		SocieteContexte.executer(SOC_B, pCache::instantane);
		verify(pvRepo, times(2)).findAll();

		SocieteContexte.executer(SOC_A, (Runnable) pCache::invalider);
		SocieteContexte.executer(SOC_B, pCache::instantane); // toujours en cache pour SOC_B
		verify(pvRepo, times(2)).findAll();

		SocieteContexte.executer(SOC_A, pCache::instantane); // rechargé pour SOC_A
		verify(pvRepo, times(3)).findAll();
	}
}
