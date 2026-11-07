package com.hr.presence.numero;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-L02 : reprise des demandes existantes, par date de création, idempotente. */
class NumeroRepriseRunnerTest {

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
	private final Map<String, Long> rangs = new HashMap<>();
	private final NumeroDemandeService numeros = new NumeroDemandeService(cle -> rangs.merge(cle, 1L, Long::sum));
	private final NumeroRepriseRunner runner = new NumeroRepriseRunner(jdbc, numeros, mock(PlatformTransactionManager.class));

	@SuppressWarnings("unchecked")
	@Test
	void numeroteDansLOrdreDeCreationEtAligneLaReferenceDesHeuresSup() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		when(jdbc.query(contains("presence_heures_sup_demande"), any(RowMapper.class))).thenReturn(List.of(
				new NumeroRepriseRunner.Ligne(a, Instant.parse("2026-10-30T10:00:00Z")),
				new NumeroRepriseRunner.Ligne(b, Instant.parse("2026-11-02T10:00:00Z")),
				new NumeroRepriseRunner.Ligne(c, Instant.parse("2026-11-03T10:00:00Z"))));
		when(jdbc.query(contains("presence_demande_manuelle"), any(RowMapper.class))).thenReturn(List.of(
				new NumeroRepriseRunner.Ligne(UUID.randomUUID(), Instant.parse("2026-11-04T10:00:00Z"))));

		runner.afterSingletonsInstantiated();

		verify(jdbc).update(contains("presence_heures_sup_demande"), eq("HS-202610-0001"), eq("HS-202610-0001"), eq(a));
		verify(jdbc).update(contains("presence_heures_sup_demande"), eq("HS-202611-0001"), eq("HS-202611-0001"), eq(b));
		verify(jdbc).update(contains("presence_heures_sup_demande"), eq("HS-202611-0002"), eq("HS-202611-0002"), eq(c));
		verify(jdbc).update(contains("presence_demande_manuelle"), ArgumentMatchers.startsWith("PM-202611-0001"), any(UUID.class));
	}

	@SuppressWarnings("unchecked")
	@Test
	void sansLigneSansNumeroNeModifieRien() {
		when(jdbc.query(contains("presence_"), any(RowMapper.class))).thenReturn(List.of());

		runner.afterSingletonsInstantiated();

		verify(jdbc, never()).update(ArgumentMatchers.anyString(), ArgumentMatchers.<Object[]>any());
		org.assertj.core.api.Assertions.assertThat(rangs).isEmpty();
	}
}
