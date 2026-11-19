package com.hr.referentiel.champsperso;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class OrigineValeurPersoConverter implements AttributeConverter<OrigineValeurPerso, String> {

	@Override
	public String convertToDatabaseColumn(OrigineValeurPerso attribut) {
		return attribut == null ? null : attribut.name();
	}

	@Override
	public OrigineValeurPerso convertToEntityAttribute(String valeur) {
		return valeur == null ? null : OrigineValeurPerso.lire(valeur);
	}
}
