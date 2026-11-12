package com.hr.referentiel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "referentiel.evenements")
public class ReferentielEvenementsProperties {

	/**
	 * Clés = codes des types de document (ex. {@code ATTESTATION_TRAVAIL}). Lot 3 : lue seulement au premier
	 * amorçage des paramètres ; ensuite le SLA se règle dans l'écran Paramètres.
	 */
	private Map<String, Integer> documentSlaHeuresParType = new HashMap<>();

	public Map<String, Integer> getDocumentSlaHeuresParType() {
		return documentSlaHeuresParType;
	}

	public void setDocumentSlaHeuresParType(Map<String, Integer> documentSlaHeuresParType) {
		this.documentSlaHeuresParType = documentSlaHeuresParType != null ? documentSlaHeuresParType : new HashMap<>();
	}

	public int delaiSlaHeuresPourDocument(String codeTypeDocument, int defaut) {
		return documentSlaHeuresParType.getOrDefault(codeTypeDocument, defaut);
	}
}
