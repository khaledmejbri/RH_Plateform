package com.hr.referentiel.importation.parametrage;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Colonne varchar (aucune contrainte CHECK, ADR § 3). Une valeur inconnue lue en base (impossible hors SQL manuel) est
 * lue comme {@code FACULTATIF} avec un WARN ; l'instantané la ramène ensuite au défaut du champ.
 */
@Converter
public class ExigenceChampConverter implements AttributeConverter<ExigenceChamp, String> {

	private static final Logger log = LoggerFactory.getLogger(ExigenceChampConverter.class);

	@Override
	public String convertToDatabaseColumn(ExigenceChamp attribut) {
		return attribut == null ? null : attribut.name();
	}

	@Override
	public ExigenceChamp convertToEntityAttribute(String valeur) {
		if (valeur == null) {
			return null;
		}
		ExigenceChamp e = ExigenceChamp.lire(valeur);
		if (e == null) {
			log.warn("Exigence d'import inconnue en base : « {} » (lue comme Facultatif)", valeur);
			return ExigenceChamp.FACULTATIF;
		}
		return e;
	}
}
