package com.hr.referentiel.champsperso;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Règles d'un champ personnalisé (annexe A bis : nombres en chaîne via {@code toPlainString}, dates ISO). Sert aussi à
 * l'instantané figé dans l'analyse (JSON). Les clés absentes ou nulles sont lues comme « sans règle ».
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReglesPerso(
		@JsonProperty("longueur_max") Integer longueurMax,
		@JsonProperty("decimales") Integer decimales,
		@JsonProperty("minimum") BigDecimal minimum,
		@JsonProperty("maximum") BigDecimal maximum,
		@JsonProperty("date_min") LocalDate dateMin,
		@JsonProperty("date_max") LocalDate dateMax) {

	public static final ReglesPerso AUCUNE = new ReglesPerso(null, null, null, null, null, null);
}
