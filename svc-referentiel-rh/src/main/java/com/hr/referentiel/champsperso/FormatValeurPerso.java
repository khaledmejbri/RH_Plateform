package com.hr.referentiel.champsperso;

import com.hr.referentiel.importation.parametrage.ParametrageImport.DefinitionPerso;
import com.hr.referentiel.importation.parametrage.ParametrageImport.ValeurListe;
import com.hr.referentiel.service.FormatJours;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Formats d'une valeur : canonique (stockage, instantané) et d'affichage (journal, écran, export). */
public final class FormatValeurPerso {

	private FormatValeurPerso() {
	}

	/** Texte tel que saisi, nombre en décimal plat, date ISO, code de la valeur de liste. */
	public static String canonique(ValeurTypee v) {
		return switch (v.type()) {
			case TEXTE -> v.texte();
			case NOMBRE -> v.nombre().toPlainString();
			case DATE -> v.date().toString();
			case LISTE -> v.listeCode();
		};
	}

	/** Relecture d'une forme canonique ; {@code null} si illisible. */
	public static ValeurTypee depuisCanonique(TypeValeurPerso type, String canonique) {
		if (canonique == null) {
			return null;
		}
		try {
			return switch (type) {
				case TEXTE -> ValeurTypee.texte(canonique);
				case NOMBRE -> ValeurTypee.nombre(new BigDecimal(canonique));
				case DATE -> ValeurTypee.date(LocalDate.parse(canonique));
				case LISTE -> ValeurTypee.liste(canonique);
			};
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Texte, virgule décimale, {@code jj/mm/aaaa}, libellé de la valeur de liste (repli : le code). */
	public static String affichage(ValeurTypee v, DefinitionPerso def) {
		return switch (v.type()) {
			case TEXTE -> v.texte();
			case NOMBRE -> v.nombre().toPlainString().replace('.', ',');
			case DATE -> v.date().format(FormatJours.JOUR);
			case LISTE -> {
				ValeurListe l = def == null ? null : def.valeur(v.listeCode());
				yield l == null ? v.listeCode() : l.libelle();
			}
		};
	}
}
