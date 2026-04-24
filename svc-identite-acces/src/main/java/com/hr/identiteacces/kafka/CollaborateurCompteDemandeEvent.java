package com.hr.identiteacces.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record CollaborateurCompteDemandeEvent(

		@JsonProperty("collaborateur_identifiant") UUID collaborateurIdentifiant,

		@JsonProperty("matricule") String matricule,

		@JsonProperty("courriel") String courriel,

		@JsonProperty("prenom") String prenom,

		@JsonProperty("nom") String nom,

		@JsonProperty("profil_acces") String profilAcces,

		@JsonProperty("mot_de_passe_initial") String motDePasseInitial
) {
}
