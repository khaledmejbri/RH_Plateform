package com.hr.workflow.definition;

import com.hr.workflow.web.WorkflowMetierException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Sérialisation / désérialisation stricte d'une {@link DefinitionWorkflow} (§ 9.3.2, § 9.3.3).
 * Propriétés inconnues refusées ; nombres décimaux lus en {@code BigDecimal} ; empreinte SHA-256 canonique.
 */
@Component
public class DefinitionCodec {

	public static final int TAILLE_MAX_OCTETS = 256 * 1024;

	private final JsonMapper strictMapper;
	private final JsonMapper canoniqueMapper;

	public DefinitionCodec() {
		this.strictMapper = JsonMapper.builder()
				.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
				.build();
		this.canoniqueMapper = JsonMapper.builder()
				.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
				.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
				.build();
	}

	public DefinitionWorkflow deserialiser(String json) {
		if (json == null || json.isBlank()) {
			throw WorkflowMetierException.badRequest("CORPS_INVALIDE", "La définition JSON est obligatoire.");
		}
		if (json.getBytes(StandardCharsets.UTF_8).length > TAILLE_MAX_OCTETS) {
			throw WorkflowMetierException.badRequest("DEFINITION_TROP_GRANDE",
					"La définition dépasse la taille maximale autorisée de 256 Ko.");
		}
		try {
			return strictMapper.readValue(json, DefinitionWorkflow.class);
		} catch (JacksonException e) {
			throw WorkflowMetierException.badRequest("CORPS_INVALIDE",
					"Définition JSON illisible ou propriété inconnue : " + e.getOriginalMessage());
		}
	}

	public String serialiser(DefinitionWorkflow definition) {
		try {
			return canoniqueMapper.writeValueAsString(definition);
		} catch (JacksonException e) {
			throw new IllegalStateException("Impossible de sérialiser la définition de workflow", e);
		}
	}

	public int tailleOctets(DefinitionWorkflow definition) {
		return serialiser(definition).getBytes(StandardCharsets.UTF_8).length;
	}

	public String calculerEmpreinte(DefinitionWorkflow definition) {
		String canonique = serialiser(definition);
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(canonique.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponible", e);
		}
	}
}
