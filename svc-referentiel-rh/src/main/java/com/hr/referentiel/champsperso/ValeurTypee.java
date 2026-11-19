package com.hr.referentiel.champsperso;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Valeur contrôlée d'un champ personnalisé : exactement une composante renseignée, celle du type. Le nombre est
 * normalisé (zéros finals retirés) pour que la comparaison typée ne dépende pas de la mise en forme.
 */
public record ValeurTypee(TypeValeurPerso type, String texte, BigDecimal nombre, LocalDate date, String listeCode) {

	public static ValeurTypee texte(String t) {
		return new ValeurTypee(TypeValeurPerso.TEXTE, t, null, null, null);
	}

	public static ValeurTypee nombre(BigDecimal n) {
		BigDecimal s = n.stripTrailingZeros();
		return new ValeurTypee(TypeValeurPerso.NOMBRE, null, s.scale() < 0 ? s.setScale(0) : s, null, null);
	}

	public static ValeurTypee date(LocalDate d) {
		return new ValeurTypee(TypeValeurPerso.DATE, null, null, d, null);
	}

	public static ValeurTypee liste(String code) {
		return new ValeurTypee(TypeValeurPerso.LISTE, null, null, null, code);
	}

	/** Égalité typée : 42 = 42,0 ; texte sensible à la casse (la valeur est gardée telle que saisie). */
	public boolean memeValeur(ValeurTypee autre) {
		if (autre == null || type != autre.type) {
			return false;
		}
		return switch (type) {
			case TEXTE -> Objects.equals(texte, autre.texte);
			case NOMBRE -> nombre.compareTo(autre.nombre) == 0;
			case DATE -> Objects.equals(date, autre.date);
			case LISTE -> Objects.equals(listeCode, autre.listeCode);
		};
	}
}
