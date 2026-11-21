package com.hr.workflow.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.exc.MismatchedInputException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arbre de condition d'une définition de workflow (§ 9.3.1, § 9.3.2).
 * Interface scellée : {@link ConditionEt}, {@link ConditionOu}, {@link ConditionNon}, {@link ConditionFeuille}.
 */
@JsonDeserialize(using = Condition.ConditionDeserializer.class)
public sealed interface Condition permits Condition.ConditionEt, Condition.ConditionOu, Condition.ConditionNon, Condition.ConditionFeuille {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record ConditionEt(@JsonProperty("et") List<Condition> et) implements Condition {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record ConditionOu(@JsonProperty("ou") List<Condition> ou) implements Condition {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record ConditionNon(@JsonProperty("non") Condition non) implements Condition {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record ConditionFeuille(
			@JsonProperty("champ") String champ,
			@JsonProperty("op") String op,
			@JsonProperty("valeur") Object valeur
	) implements Condition {
	}

	final class ConditionDeserializer extends ValueDeserializer<Condition> {

		private static final Set<String> CHAMPS_FEUILLE = Set.of("champ", "op", "valeur");

		@Override
		public Condition deserialize(JsonParser p, DeserializationContext ctxt) {
			JsonNode node = p.readValueAsTree();
			if (node == null || node.isNull()) {
				return null;
			}
			if (!node.isObject()) {
				throw MismatchedInputException.from(p, Condition.class, "Une condition doit être un objet JSON");
			}
			return parserNoeud(node, p, ctxt);
		}

		private Condition parserNoeud(JsonNode node, JsonParser p, DeserializationContext ctxt) {
			if (!node.isObject()) {
				throw MismatchedInputException.from(p, Condition.class, "Un nœud de condition doit être un objet JSON");
			}
			int nbCles = node.size();
			if (node.has("et")) {
				if (nbCles != 1) {
					throw MismatchedInputException.from(p, Condition.class, "Propriété inconnue sur condition 'et'");
				}
				return new ConditionEt(parserListe(node.get("et"), p, ctxt));
			}
			if (node.has("ou")) {
				if (nbCles != 1) {
					throw MismatchedInputException.from(p, Condition.class, "Propriété inconnue sur condition 'ou'");
				}
				return new ConditionOu(parserListe(node.get("ou"), p, ctxt));
			}
			if (node.has("non")) {
				if (nbCles != 1) {
					throw MismatchedInputException.from(p, Condition.class, "Propriété inconnue sur condition 'non'");
				}
				JsonNode child = node.get("non");
				if (child == null || child.isNull()) {
					throw MismatchedInputException.from(p, Condition.class, "L'enfant de 'non' est obligatoire");
				}
				return new ConditionNon(parserNoeud(child, p, ctxt));
			}
			// Feuille : vérifier qu'aucune propriété inconnue n'est présente
			for (Iterator<Map.Entry<String, JsonNode>> it = node.properties().iterator(); it.hasNext(); ) {
				String key = it.next().getKey();
				if (!CHAMPS_FEUILLE.contains(key)) {
					throw MismatchedInputException.from(p, Condition.class, "Propriété inconnue dans la condition : " + key);
				}
			}
			String champ = node.has("champ") && !node.get("champ").isNull() ? node.get("champ").asString() : null;
			String op = node.has("op") && !node.get("op").isNull() ? node.get("op").asString() : null;
			Object valeur = node.has("valeur") ? convertirValeur(node.get("valeur")) : null;
			return new ConditionFeuille(champ, op, valeur);
		}

		private List<Condition> parserListe(JsonNode arr, JsonParser p, DeserializationContext ctxt) {
			if (arr == null || !arr.isArray()) {
				throw MismatchedInputException.from(p, Condition.class, "Les enfants de 'et'/'ou' doivent être une liste");
			}
			List<Condition> list = new ArrayList<>();
			for (JsonNode child : arr) {
				list.add(parserNoeud(child, p, ctxt));
			}
			return list;
		}

		private static Object convertirValeur(JsonNode val) {
			if (val == null || val.isNull()) {
				return null;
			}
			if (val.isBoolean()) {
				return val.asBoolean();
			}
			if (val.isIntegralNumber()) {
				return val.bigIntegerValue();
			}
			if (val.isFloatingPointNumber() || val.isNumber()) {
				return val.decimalValue();
			}
			if (val.isString()) {
				return val.asString();
			}
			if (val.isArray()) {
				List<Object> list = new ArrayList<>();
				for (JsonNode item : val) {
					list.add(convertirValeur(item));
				}
				return list;
			}
			return val.toString();
		}
	}
}
