package com.hr.identiteacces.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CollaborateurCompteDemandeEvent(

		@JsonProperty("collaborateur_identifiant") UUID collaborateurIdentifiant,

		@JsonProperty("matricule") String matricule,

		@JsonProperty("courriel") String courriel,

		@JsonProperty("prenom") String prenom,

		@JsonProperty("nom") String nom,

		@JsonProperty("profil_acces") String profilAcces,

		@JsonProperty("mot_de_passe_initial") String motDePasseInitial,

		@JsonProperty("operation") String operation,

		@JsonProperty("compte_utilisateur_id") UUID compteUtilisateurId,

		/** Import : le mot de passe initial est généré par svc-identite-acces (jamais transporté par l'événement). */
		@JsonProperty("mot_de_passe_genere") Boolean motDePasseGenere
) {

	public static final String OPERATION_CREATION = "CREATION";
	public static final String OPERATION_MAJ_ROLES = "MAJ_ROLES";

	public CollaborateurCompteDemandeEvent {
		if (operation == null || operation.isBlank()) {
			operation = OPERATION_CREATION;
		}
	}

	/** Rétrocompat : événements à 9 composants (sans {@code mot_de_passe_genere}). */
	public CollaborateurCompteDemandeEvent(
			UUID collaborateurIdentifiant,
			String matricule,
			String courriel,
			String prenom,
			String nom,
			String profilAcces,
			String motDePasseInitial,
			String operation,
			UUID compteUtilisateurId) {
		this(collaborateurIdentifiant, matricule, courriel, prenom, nom, profilAcces, motDePasseInitial, operation,
				compteUtilisateurId, null);
	}

	public boolean genereLeMotDePasse() {
		return Boolean.TRUE.equals(motDePasseGenere);
	}

	/** Rétrocompat : messages sans {@code operation} ni {@code compte_utilisateur_id}. */
	public CollaborateurCompteDemandeEvent(
			UUID collaborateurIdentifiant,
			String matricule,
			String courriel,
			String prenom,
			String nom,
			String profilAcces,
			String motDePasseInitial) {
		this(collaborateurIdentifiant, matricule, courriel, prenom, nom, profilAcces,
				motDePasseInitial, OPERATION_CREATION, null, null);
	}
}
