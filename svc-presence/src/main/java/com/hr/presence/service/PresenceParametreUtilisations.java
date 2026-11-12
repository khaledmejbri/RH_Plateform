package com.hr.presence.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilisations locales des codes de statut (ADR lot 3 § 6) : nombre de pointages, de demandes de pointage manuel et de
 * demandes d'heures supplémentaires par statut. Une seule requête groupée par référentiel (pas une par code).
 */
@Component
public class PresenceParametreUtilisations {

	@PersistenceContext
	private EntityManager em;

	/** Nombre d'objets par code de statut pour un référentiel de présence ; code absent = 0 utilisation. */
	@Transactional(readOnly = true)
	public Map<String, Long> parCode(String referentiel) {
		String entite = switch (referentiel) {
			case PresenceParametresService.STATUT_POINTAGE -> "PresencePointage";
			case PresenceParametresService.STATUT_DEMANDE_MANUELLE -> "PresenceDemandeManuelle";
			case PresenceParametresService.STATUT_HEURES_SUP -> "PresenceHeuresSupDemande";
			default -> null;
		};
		Map<String, Long> out = new HashMap<>();
		if (entite == null) {
			return out;
		}
		List<Object[]> lignes = em.createQuery("select x.statut, count(x) from " + entite + " x group by x.statut",
				Object[].class).getResultList();
		for (Object[] l : lignes) {
			if (l[0] != null) {
				out.put(((Enum<?>) l[0]).name(), ((Number) l[1]).longValue());
			}
		}
		return out;
	}
}
