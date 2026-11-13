package com.hr.referentiel.importation;

import java.util.List;

/** Champ reconnu dans une colonne d'import : code stable, libellé français et synonymes d'en-tête (D-I03). */
public interface ChampImport {

	String name();

	String libelle();

	List<String> synonymes();

	/** Obligatoire pour créer une fiche (affiché dans {@code champs_disponibles}). */
	default boolean obligatoireCreation() {
		return false;
	}

	/** Les champs d'un type d'import, dans l'ordre d'affichage. */
	enum Fiche implements ChampImport {
		MATRICULE("Matricule", true, "matricule", "mat", "n matricule", "no matricule", "num matricule",
				"numero matricule", "numero de matricule", "id employe", "identifiant"),
		PRENOM("Prénom", true, "prenom", "first name", "firstname", "given name"),
		NOM("Nom", true, "nom", "nom de famille", "last name", "lastname", "family name", "surname"),
		COURRIEL("Courriel", false, "courriel", "email", "e mail", "mail", "adresse email", "adresse mail",
				"adresse e mail", "courrier electronique", "courriel professionnel", "email professionnel"),
		UNITE("Unité", true, "unite", "code unite", "libelle unite", "unite d affectation", "unite organisationnelle",
				"service", "departement"),
		POSTE("Poste", false, "poste", "intitule du poste", "intitule poste", "libelle poste", "job title"),
		FONCTION("Fonction", false, "fonction"),
		QUALITE("Qualité", false, "qualite"),
		AFFECTATION("Affectation", false, "affectation", "lieu de travail", "lieu d affectation", "site"),
		DATE_RECRUTEMENT("Date de recrutement", false, "date de recrutement", "date recrutement", "recrutement",
				"date d embauche", "date embauche", "embauche", "date d entree", "date entree"),
		STATUT("Statut", false, "statut", "etat", "statut collaborateur"),
		PROFIL_ACCES("Profil d'accès", false, "profil", "profil d acces", "profil acces", "role", "niveau d acces"),
		FAMILLE_METIER("Famille métier", false, "famille", "famille metier", "famille de metier", "filiere"),
		NIVEAU_SENIORITE("Niveau de séniorité", false, "niveau", "niveau de seniorite", "niveau seniorite", "seniorite");

		private final String libelle;
		private final boolean obligatoireCreation;
		private final List<String> synonymes;

		Fiche(String libelle, boolean obligatoireCreation, String... synonymes) {
			this.libelle = libelle;
			this.obligatoireCreation = obligatoireCreation;
			this.synonymes = List.of(synonymes);
		}

		@Override
		public String libelle() {
			return libelle;
		}

		@Override
		public List<String> synonymes() {
			return synonymes;
		}

		@Override
		public boolean obligatoireCreation() {
			return obligatoireCreation;
		}
	}

	enum Solde implements ChampImport {
		MATRICULE("Matricule", true, "matricule", "mat", "n matricule", "no matricule", "num matricule",
				"numero matricule", "numero de matricule"),
		SOLDE("Solde", true, "solde", "solde conge", "solde de conge", "solde cible", "solde au", "solde acquis",
				"jours restants", "reliquat", "droit restant", "droits restants", "solde conges"),
		MOTIF("Motif", false, "motif", "commentaire", "observation", "observations", "remarque", "raison");

		private final String libelle;
		private final boolean obligatoire;
		private final List<String> synonymes;

		Solde(String libelle, boolean obligatoire, String... synonymes) {
			this.libelle = libelle;
			this.obligatoire = obligatoire;
			this.synonymes = List.of(synonymes);
		}

		@Override
		public String libelle() {
			return libelle;
		}

		@Override
		public List<String> synonymes() {
			return synonymes;
		}

		@Override
		public boolean obligatoireCreation() {
			return obligatoire;
		}
	}
}
