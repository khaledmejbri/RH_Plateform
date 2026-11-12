package com.hr.referentiel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Nombre d'utilisations locales d'un code (ADR-UAT-lot3 § 6) : un {@code group by} par référentiel, sans appel à un
 * autre service. Les expressions SQL sont des constantes (aucune saisie n'y entre).
 */
@Component
public class ParametreUtilisationCompteur {

	private record Source(String table, String expression, String filtre) {
	}

	private static final Map<String, Source> SOURCES = Map.ofEntries(
			Map.entry("FAMILLE_METIER", new Source("rh_collaborateur", "famille_metier_code", null)),
			Map.entry("NIVEAU_SENIORITE", new Source("rh_collaborateur", "niveau_seniorite", null)),
			Map.entry("STATUT_COLLABORATEUR", new Source("rh_collaborateur", "statut", null)),
			Map.entry("PROFIL_ACCES", new Source("rh_collaborateur", "profil_acces", null)),
			Map.entry("TYPE_DOCUMENT", new Source("rh_demande_document_administratif", "type_document", null)),
			Map.entry("STATUT_DOCUMENT", new Source("rh_demande_document_administratif", "statut", null)),
			// type_conge est une clé du jsonb contenu (saisie historique parfois en minuscules)
			Map.entry("TYPE_CONGE", new Source("rh_demande_administrative",
					"upper(trim(contenu->>'type_conge'))", "type_demande = 'CONGE'")),
			Map.entry("TYPE_DEMANDE", new Source("rh_demande_administrative", "type_demande", null)),
			Map.entry("STATUT_DEMANDE", new Source("rh_demande_administrative", "statut", null)),
			Map.entry("TYPE_PLAINTE", new Source("rh_plainte", "type_plainte", null)),
			Map.entry("STATUT_PLAINTE", new Source("rh_plainte", "statut", null)),
			Map.entry("STATUT_FORMATION", new Source("rh_demande_formation", "statut", null)));

	private final JdbcTemplate jdbcTemplate;

	public ParametreUtilisationCompteur(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** {@code code -> nombre d'objets} pour tout un référentiel. Référentiel sans source : vide. */
	@Transactional(readOnly = true)
	public Map<String, Long> compter(String referentiel) {
		Source s = SOURCES.get(referentiel);
		Map<String, Long> resultat = new HashMap<>();
		if (s == null) {
			return resultat;
		}
		String sql = "select " + s.expression() + " as code, count(*) as n from " + s.table()
				+ " where " + (s.filtre() != null ? s.filtre() + " and " : "") + s.expression() + " is not null"
				+ " group by " + s.expression();
		jdbcTemplate.query(sql, rs -> {
			resultat.merge(rs.getString("code"), rs.getLong("n"), Long::sum);
		});
		return resultat;
	}

	@Transactional(readOnly = true)
	public long compter(String referentiel, String code) {
		return compter(referentiel).getOrDefault(code, 0L);
	}
}
