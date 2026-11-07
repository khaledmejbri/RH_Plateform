package com.hr.presence.numero;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Upsert atomique PostgreSQL : la ligne du compteur est verrouillée jusqu'à la fin de la transaction, donc deux
 * créations simultanées du même mois reçoivent deux rangs différents, et une annulation ne laisse pas de trou.
 */
@Component
public class CompteurNumeroJdbc implements CompteurNumero {

	private static final String SQL = "INSERT INTO presence_numero_sequence (cle, dernier) VALUES (?, 1) "
			+ "ON CONFLICT (cle) DO UPDATE SET dernier = presence_numero_sequence.dernier + 1 RETURNING dernier";

	private final JdbcTemplate jdbcTemplate;

	public CompteurNumeroJdbc(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public long suivant(String cle) {
		Long rang = jdbcTemplate.queryForObject(SQL, Long.class, cle);
		if (rang == null) {
			throw new IllegalStateException("Compteur de numéros indisponible : " + cle);
		}
		return rang;
	}
}
