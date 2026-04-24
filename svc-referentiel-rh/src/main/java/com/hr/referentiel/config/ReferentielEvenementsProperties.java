package com.hr.referentiel.config;

import com.hr.referentiel.domain.TypeDocumentAdministratifDemandeRh;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "referentiel.evenements")
public class ReferentielEvenementsProperties {

	/**
	 * Clés = noms {@link TypeDocumentAdministratifDemandeRh#name()}.
	 */
	private Map<String, Integer> documentSlaHeuresParType = new HashMap<>();

	public Map<String, Integer> getDocumentSlaHeuresParType() {
		return documentSlaHeuresParType;
	}

	public void setDocumentSlaHeuresParType(Map<String, Integer> documentSlaHeuresParType) {
		this.documentSlaHeuresParType = documentSlaHeuresParType != null ? documentSlaHeuresParType : new HashMap<>();
	}

	public int delaiSlaHeuresPourDocument(String typeDocumentEnumName, int defautEnum) {
		return documentSlaHeuresParType.getOrDefault(typeDocumentEnumName, defautEnum);
	}
}
