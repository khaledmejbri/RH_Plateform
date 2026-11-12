package com.hr.presence.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Réglages numériques paramétrables du pointage (D-R06) : clé publique, libellé, bornes et valeur par défaut du
 * produit. Les bornes sont dans le code (non modifiables). Comprend les réglages D-P22 / D-P23 du pointage manuel.
 */
public enum ReglagePresence {

	SEUIL_POINTAGES_MANUELS_MOIS("seuil_pointages_manuels_mois", "Seuil de pointages manuels par mois", 1, 31, "NOMBRE", 3),
	DELAI_GRACE_RETARD_MINUTES("delai_grace_retard_minutes", "Délai de grâce des retards", 0, 60, "MINUTES", 0),
	RAYON_DEFAUT_EXTERIEUR_METRES("rayon_defaut_exterieur_metres", "Rayon par défaut d'un site extérieur", 10, 100, "METRES", 20),
	RAYON_DEFAUT_INTERIEUR_METRES("rayon_defaut_interieur_metres", "Rayon par défaut d'un site intérieur", 10, 100, "METRES", 50),
	/** D-P22 : plafond de distance et de précision d'un refus rattrapable ; celui en vigueur à la création s'applique. */
	PLAFOND_DISTANCE_MANUEL_METRES("plafond_distance_manuel_metres", "Plafond de distance pour un pointage manuel", 100, 1000, "METRES", 300),
	/** D-P23.a : délai pour demander un pointage manuel après le refus. */
	DELAI_DEMANDE_MANUEL_MINUTES("delai_demande_manuel_minutes", "Délai pour demander un pointage manuel", 15, 480, "MINUTES", 120),
	/** D-P23.b : délai de décision du responsable avant expiration de la demande. */
	DELAI_DECISION_MANUEL_MINUTES("delai_decision_manuel_minutes", "Délai de décision d'un pointage manuel", 240, 4320, "MINUTES", 1440);

	private final String cle;
	private final String libelle;
	private final int min;
	private final int max;
	private final String unite;
	private final int defaut;

	ReglagePresence(String cle, String libelle, int min, int max, String unite, int defaut) {
		this.cle = cle;
		this.libelle = libelle;
		this.min = min;
		this.max = max;
		this.unite = unite;
		this.defaut = defaut;
	}

	public String cle() { return cle; }
	public String libelle() { return libelle; }
	public int min() { return min; }
	public int max() { return max; }
	public String unite() { return unite; }
	public int defaut() { return defaut; }

	public boolean dansLesBornes(int valeur) {
		return valeur >= min && valeur <= max;
	}

	public static Optional<ReglagePresence> parCle(String cle) {
		return Arrays.stream(values()).filter(r -> r.cle.equals(cle)).findFirst();
	}
}
