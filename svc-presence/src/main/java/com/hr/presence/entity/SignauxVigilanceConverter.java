package com.hr.presence.entity;

import com.hr.presence.domain.SignalVigilance;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/** Liste de signaux <-> codes séparés par des virgules ; une colonne vide ou inconnue donne une liste vide. */
@Converter
public class SignauxVigilanceConverter implements AttributeConverter<List<SignalVigilance>, String> {

	@Override
	public String convertToDatabaseColumn(List<SignalVigilance> signaux) {
		if (signaux == null || signaux.isEmpty()) {
			return null;
		}
		return String.join(",", signaux.stream().map(Enum::name).toList());
	}

	@Override
	public List<SignalVigilance> convertToEntityAttribute(String colonne) {
		List<SignalVigilance> resultat = new ArrayList<>();
		if (colonne == null || colonne.isBlank()) {
			return resultat;
		}
		for (String code : colonne.split(",")) {
			try {
				resultat.add(SignalVigilance.valueOf(code.trim()));
			} catch (IllegalArgumentException ignore) {
				// code inconnu (version future) : ignoré plutôt que de bloquer la lecture
			}
		}
		return resultat;
	}
}
