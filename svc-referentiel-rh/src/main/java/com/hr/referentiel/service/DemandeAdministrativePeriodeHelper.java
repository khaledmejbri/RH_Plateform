package com.hr.referentiel.service;

import com.hr.referentiel.entity.DemandeAdministrativeRh;

import java.time.LocalDate;
import java.util.Map;

/**
 * Duplique les bornes temporelles du JSON {@code contenu} en colonnes indexables (option B).
 */
final class DemandeAdministrativePeriodeHelper {

	private DemandeAdministrativePeriodeHelper() {
	}

	static void appliquerPeriodeIndexee(DemandeAdministrativeRh d) {
		Map<String, Object> c = d.getContenu();
		if (c == null || c.isEmpty()) {
			d.setPeriodeDebut(null);
			d.setPeriodeFin(null);
			return;
		}
		switch (d.getTypeDemande()) {
			case CONGE, ORDRE_MISSION -> {
				LocalDate debut = extraireDate(c, "date_debut");
				LocalDate fin = extraireDate(c, "date_fin");
				d.setPeriodeDebut(debut);
				d.setPeriodeFin(fin);
			}
			case AUTORISATION_SORTIE -> {
				LocalDate jour = extraireDate(c, "date_jour");
				d.setPeriodeDebut(jour);
				d.setPeriodeFin(jour);
			}
		}
	}

	static LocalDate extraireDate(Map<String, Object> map, String cle) {
		if (map == null) {
			return null;
		}
		Object v = map.get(cle);
		if (v == null) {
			return null;
		}
		if (v instanceof LocalDate ld) {
			return ld;
		}
		if (v instanceof String s) {
			String t = s.trim();
			if (t.isEmpty()) {
				return null;
			}
			return LocalDate.parse(t);
		}
		return null;
	}
}
