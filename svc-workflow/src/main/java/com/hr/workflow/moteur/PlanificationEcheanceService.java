package com.hr.workflow.moteur;

import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.definition.DelaiDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Calcul pur de l'échéance d'une tâche humaine (D-W17, D-W18, D-W3-04 : {@code echeance_le} uniquement en W3).
 */
@Service
public class PlanificationEcheanceService {

	private static final Logger log = LoggerFactory.getLogger(PlanificationEcheanceService.class);
	private static final ZoneId FUSEAU_DEFAUT = ZoneId.of("Africa/Tunis");
	private static final LocalTime HEURE_FIN_DEFAUT = LocalTime.of(17, 0);

	/**
	 * Calcule {@code echeance_le} en UTC à partir de l'instant d'activation, du délai, du calendrier société
	 * et des variables d'instance (pour un éventuel plafond D-W18).
	 */
	public Instant calculerEcheance(
			Instant activation,
			DelaiDefinition delai,
			CalendrierSocieteResponse calendrier,
			Map<String, Object> variables) {
		if (activation == null || delai == null || delai.duree() == null || delai.unite() == null) {
			return null;
		}
		CalendrierSocieteResponse cal = calendrier != null ? calendrier : CalendrierSocieteResponse.defaut();
		ZoneId fuseau = parserFuseau(cal.fuseau());
		LocalTime heureFin = parserHeure(cal.heureFinJournee());

		Instant echeance;
		if ("HEURES".equals(delai.unite())) {
			echeance = activation.plus(delai.duree(), ChronoUnit.HOURS);
		} else if ("JOURS_OUVRES".equals(delai.unite())) {
			Set<DayOfWeek> joursTravailles = parserJoursTravailles(cal.joursTravailles());
			Set<LocalDate> feries = cal.joursFeries() != null ? new HashSet<>(cal.joursFeries()) : Set.of();
			LocalDate date = activation.atZone(fuseau).toLocalDate();
			int restants = delai.duree();
			int garde = 0;
			while (restants > 0 && garde < 365) {
				date = date.plusDays(1);
				garde++;
				if (joursTravailles.contains(date.getDayOfWeek()) && !feries.contains(date)) {
					restants--;
				}
			}
			echeance = ZonedDateTime.of(date, heureFin, fuseau).toInstant();
		} else {
			throw new IllegalArgumentException("Unité de délai inconnue : " + delai.unite());
		}

		// D-W18 : plafond avant une variable de date ou date-heure
		if (delai.plafondAvantVariable() != null && !delai.plafondAvantVariable().isBlank() && variables != null) {
			Object valeurVar = variables.get(delai.plafondAvantVariable());
			Instant plafond = calculerPlafond(valeurVar, fuseau, heureFin);
			if (plafond != null) {
				if (plafond.isBefore(activation)) {
					return activation.plus(2, ChronoUnit.HOURS);
				}
				if (plafond.isBefore(echeance)) {
					return plafond;
				}
			}
		}

		return echeance;
	}

	/** Échéance fixe de 5 jours ouvrés pour une tâche {@code COMPLEMENT} (D-W10). */
	public Instant calculerEcheanceComplement(Instant activation, CalendrierSocieteResponse calendrier) {
		return calculerEcheance(activation, new DelaiDefinition(5, "JOURS_OUVRES", null), calendrier, Map.of());
	}

	private static Instant calculerPlafond(Object valeurVar, ZoneId fuseau, LocalTime heureFin) {
		if (valeurVar == null) {
			return null;
		}
		if (valeurVar instanceof Instant inst) {
			return inst;
		}
		if (valeurVar instanceof OffsetDateTime odt) {
			return odt.toInstant();
		}
		if (valeurVar instanceof LocalDate ld) {
			return ZonedDateTime.of(ld.minusDays(1), heureFin, fuseau).toInstant();
		}
		if (valeurVar instanceof String s && !s.isBlank()) {
			String trimmed = s.trim();
			// Date-heure ISO-8601 avec décalage ou Z (ex. debut_fenetre)
			if (trimmed.contains("T")) {
				try {
					return OffsetDateTime.parse(trimmed).toInstant();
				} catch (Exception e) {
					try {
						return Instant.parse(trimmed);
					} catch (Exception e2) {
						return null;
					}
				}
			}
			// Date ISO yyyy-MM-dd (ex. date_debut) -> veille à heure_fin_journee
			try {
				LocalDate ld = LocalDate.parse(trimmed);
				return ZonedDateTime.of(ld.minusDays(1), heureFin, fuseau).toInstant();
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}

	private static ZoneId parserFuseau(String fuseau) {
		if (fuseau == null || fuseau.isBlank()) {
			return FUSEAU_DEFAUT;
		}
		try {
			return ZoneId.of(fuseau.trim());
		} catch (Exception e) {
			return FUSEAU_DEFAUT;
		}
	}

	private static LocalTime parserHeure(String heure) {
		if (heure == null || heure.isBlank()) {
			return HEURE_FIN_DEFAUT;
		}
		try {
			return LocalTime.parse(heure.trim());
		} catch (Exception e) {
			return HEURE_FIN_DEFAUT;
		}
	}

	private static Set<DayOfWeek> parserJoursTravailles(List<String> jours) {
		if (jours == null || jours.isEmpty()) {
			return Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
		}
		Set<DayOfWeek> out = new HashSet<>();
		for (String j : jours) {
			if (j == null) {
				continue;
			}
			switch (j.trim().toUpperCase(Locale.ROOT)) {
				case "LUN", "LUNDI", "MONDAY" -> out.add(DayOfWeek.MONDAY);
				case "MAR", "MARDI", "TUESDAY" -> out.add(DayOfWeek.TUESDAY);
				case "MER", "MERCREDI", "WEDNESDAY" -> out.add(DayOfWeek.WEDNESDAY);
				case "JEU", "JEUDI", "THURSDAY" -> out.add(DayOfWeek.THURSDAY);
				case "VEN", "VENDREDI", "FRIDAY" -> out.add(DayOfWeek.FRIDAY);
				case "SAM", "SAMEDI", "SATURDAY" -> out.add(DayOfWeek.SATURDAY);
				case "DIM", "DIMANCHE", "SUNDAY" -> out.add(DayOfWeek.SUNDAY);
				default -> log.warn("Code jour travaillé inconnu dans le calendrier société : '{}'", j);
			}
		}
		return out.isEmpty()
				? Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
				: out;
	}
}
