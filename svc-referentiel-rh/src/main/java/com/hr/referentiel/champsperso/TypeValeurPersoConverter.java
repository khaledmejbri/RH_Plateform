package com.hr.referentiel.champsperso;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Colonne varchar : une valeur inconnue en base est lue comme {@code TEXTE}. */
@Converter
public class TypeValeurPersoConverter implements AttributeConverter<TypeValeurPerso, String> {

	@Override
	public String convertToDatabaseColumn(TypeValeurPerso attribut) {
		return attribut == null ? null : attribut.name();
	}

	@Override
	public TypeValeurPerso convertToEntityAttribute(String valeur) {
		if (valeur == null) {
			return null;
		}
		TypeValeurPerso t = TypeValeurPerso.lire(valeur);
		return t == null ? TypeValeurPerso.TEXTE : t;
	}
}
