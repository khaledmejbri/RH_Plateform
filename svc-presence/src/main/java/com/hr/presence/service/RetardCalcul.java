package com.hr.presence.service;

import com.hr.presence.entity.PresencePointage;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;

/**
 * Retard d'une journée de travail sur un site (D-P19), calcul pur : aucune horloge, aucun accès aux données, rien
 * n'est stocké. L'horaire en vigueur à la date est l'instantané {@code horaire_debut_ts} porté par l'ENTREE retenue
 * (D-P11.c, ADR lot 2 § 3) : une modification d'horaire ne change pas le retard des jours passés.
 */
public final class RetardCalcul {

	private RetardCalcul() {
	}

	/**
	 * @param retenus     pointages retenus (VALIDE, VALIDE_MANUEL) de la journée sur le site, dans n'importe quel ordre ;
	 *                    {@code server_ts} est l'heure déclarée pour un pointage manuel
	 * @param delaiGrace  délai de grâce ajouté au début de l'horaire (D-P19.d)
	 * @return {@code null} si le retard n'est pas calculable (site sans horaire, jour fermé, premier pointage du jour
	 *         = SORTIE, aucune ENTREE) ; 0 = à l'heure ; sinon minutes de retard tronquées (D-P19.c)
	 */
	public static Integer calculer(Collection<PresencePointage> retenus, Duration delaiGrace) {
		PresencePointage premier = retenus.stream().min(Comparator.comparing(PresencePointage::getServerTs)).orElse(null);
		if (premier == null || premier.getType() != com.hr.presence.domain.PointageType.ENTREE) {
			return null;
		}
		if (premier.getHoraireDebutTs() == null) {
			return null;
		}
		Duration ecart = Duration.between(premier.getHoraireDebutTs().plus(delaiGrace), premier.getServerTs());
		long minutes = ecart.isNegative() ? 0 : ecart.getSeconds() / 60;
		return (int) minutes;
	}
}
