package com.hr.presence.numero;

/** Incrémente et renvoie un compteur nommé, sans doublon même sous concurrence. */
public interface CompteurNumero {

	/** Rang suivant de la clé (1 à la première utilisation) ; participe à la transaction de l'appelant. */
	long suivant(String cle);
}
