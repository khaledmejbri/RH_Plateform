package com.hr.referentiel.service;

import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Component
public class DemandeAdministrativeValidationService {

	/** Durée maximale autorisée pour une autorisation de sortie : 4 heures (moitié d'une journée de 8h). */
	private static final long DUREE_MAX_SORTIE_MINUTES = 4 * 60;

	public void validerContenu(TypeDemandeAdministrativeRh type, Map<String, Object> contenu) {
		if (contenu == null || contenu.isEmpty()) {
			throw new IllegalArgumentException("Le contenu de la demande est obligatoire.");
		}
		switch (type) {
			case CONGE -> {
				exigerCle(contenu, "date_debut");
				exigerCle(contenu, "date_fin");
				exigerCle(contenu, "type_conge");
			}
			case AUTORISATION_SORTIE -> {
				exigerCle(contenu, "date_jour");
				exigerCle(contenu, "heure_debut");
				exigerCle(contenu, "heure_fin");
				exigerCle(contenu, "motif");

				LocalTime heureDebut = parseHeure(contenu, "heure_debut");
				LocalTime heureFin = parseHeure(contenu, "heure_fin");

				if (!heureFin.isAfter(heureDebut)) {
					throw new IllegalArgumentException(
							"L'heure de fin doit être postérieure à l'heure de début.");
				}

				long dureeMinutes = ChronoUnit.MINUTES.between(heureDebut, heureFin);
				if (dureeMinutes > DUREE_MAX_SORTIE_MINUTES) {
					throw new IllegalArgumentException(
							"La durée d'une autorisation de sortie ne peut pas dépasser 4 heures (moitié de journée). "
							+ "Durée demandée : " + dureeMinutes + " minutes.");
				}
			}
			case ORDRE_MISSION -> {
				exigerCle(contenu, "lieu");
				exigerCle(contenu, "date_debut");
				exigerCle(contenu, "date_fin");
				exigerCle(contenu, "motif");
			}
		}
	}

	private static LocalTime parseHeure(Map<String, Object> contenu, String cle) {
		Object v = contenu.get(cle);
		if (v == null || String.valueOf(v).isBlank()) {
			throw new IllegalArgumentException("Champ obligatoire manquant dans contenu : " + cle);
		}
		try {
			return LocalTime.parse(String.valueOf(v).trim());
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException(
					"Format d'heure invalide pour '" + cle + "' (attendu HH:mm) : " + v);
		}
	}

	private static void exigerCle(Map<String, Object> contenu, String cle) {
		if (!contenu.containsKey(cle) || contenu.get(cle) == null
				|| String.valueOf(contenu.get(cle)).isBlank()) {
			throw new IllegalArgumentException("Champ obligatoire manquant dans contenu : " + cle);
		}
	}
}
