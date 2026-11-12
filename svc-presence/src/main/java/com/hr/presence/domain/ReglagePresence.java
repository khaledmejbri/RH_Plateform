package com.hr.presence.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Réglages numériques paramétrables du pointage (D-R06) : clé publique, libellé, bornes et valeur par défaut du
 * produit. Les bornes sont dans le code (non modifiables). Les réglages D-P22 / D-P23 n'en font pas partie.
 */
public enum ReglagePresence {

	SEUIL_POINTAGES_MANUELS_MOIS("seuil_pointages_manuels_mois", "Seuil de pointages manuels par mois", 1, 31, "NOMBRE", 3),
	DELAI_GRACE_RETARD_MINUTES("delai_grace_retard_minutes", "Délai de grâce des retards", 0, 60, "MINUTES", 0),
	RAYON_DEFAUT_EXTERIEUR_METRES("rayon_defaut_exterieur_metres", "Rayon par défaut d'un site extérieur", 10, 100, "METRES", 20),
	RAYON_DEFAUT_INTERIEUR_METRES("rayon_defaut_interieur_metres", "Rayon par défaut d'un site intérieur", 10, 100, "METRES", 50);

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
