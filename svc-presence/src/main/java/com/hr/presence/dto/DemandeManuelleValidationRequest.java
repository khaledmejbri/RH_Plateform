package com.hr.presence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Corps facultatif de la validation : commentaire du valideur (10 à 500 caractères, obligatoire si signal, D-P24.b). */
public record DemandeManuelleValidationRequest(@JsonProperty("commentaire") String commentaire) {
}
