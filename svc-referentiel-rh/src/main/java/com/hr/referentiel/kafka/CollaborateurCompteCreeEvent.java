package com.hr.referentiel.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record CollaborateurCompteCreeEvent(

		@JsonProperty("collaborateur_identifiant") UUID collaborateurIdentifiant,

		@JsonProperty("compte_utilisateur_identifiant") UUID compteUtilisateurIdentifiant
) {
}
