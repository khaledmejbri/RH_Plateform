package com.hr.referentiel.importation.parametrage;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Colonne varchar sans contrainte CHECK ; valeur inconnue lue comme {@code RH} (supprimable) avec un WARN. */
@Converter
public class OrigineSynonymeConverter implements AttributeConverter<OrigineSynonyme, String> {

	private static final Logger log = LoggerFactory.getLogger(OrigineSynonymeConverter.class);

	@Override
	public String convertToDatabaseColumn(OrigineSynonyme attribut) {
		return attribut == null ? null : attribut.name();
	}

	@Override
	public OrigineSynonyme convertToEntityAttribute(String valeur) {
		if (valeur == null) {
			return null;
		}
		for (OrigineSynonyme o : OrigineSynonyme.values()) {
			if (o.name().equals(valeur)) {
				return o;
			}
		}
		log.warn("Origine de synonyme inconnue en base : « {} » (lue comme RH)", valeur);
		return OrigineSynonyme.RH;
	}
}
