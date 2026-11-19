package com.hr.referentiel.importation;

import com.hr.referentiel.importation.parametrage.ExigenceChamp;

import java.util.List;

/**
 * Champ reconnu dans une colonne d'import : code stable (comportement), libellé français, synonymes d'en-tête par défaut
 * (D-I03) et valeurs par défaut / verrous de l'exigence (D-I15, D-I16). La liste des champs est fixe (D-I14) ; la RH ne
 * règle que les en-têtes, l'ordre, l'exigence et les synonymes (tables {@code rh_import_*}).
 */
public interface ChampImport {

	String name();

	/** Nom du champ de la fiche : en-tête par défaut, et nom stable dans l'historique des imports (D-I26). */
	String libelle();

	/** Synonymes par défaut (amorçage seulement). */
	List<String> synonymes();

	ExigenceChamp exigenceDefaut();

	/** Verrou D-I16 : exigence en dessous de laquelle la RH ne peut pas descendre. */
	ExigenceChamp exigenceMinimale();

	/** Libellé du cadenas affiché à l'écran ; {@code null} si le champ n'est pas verrouillé. */
	String verrouLibelle();

	/** Un champ obligatoire par construction ne peut pas être retiré du modèle (D-I17). */
	default boolean retirableDuModele() {
		return exigenceMinimale() == ExigenceChamp.FACULTATIF;
	}

	String VERROU_MATRICULE = "Toujours obligatoire : sert à retrouver la fiche";
	String VERROU_CREATION = "Exigé pour créer une fiche";
	String VERROU_SOLDE = "Exigé pour chaque ligne";

	/** Les champs d'un type d'import, dans l'ordre d'affichage. */
	enum Fiche implements ChampImport {
		MATRICULE("Matricule", ExigenceChamp.TOUJOURS, VERROU_MATRICULE, "matricule", "mat", "n matricule", "no matricule",
				"num matricule", "numero matricule", "numero de matricule", "id employe", "identifiant"),
		PRENOM("Prénom", ExigenceChamp.CREATION, VERROU_CREATION, "prenom", "first name", "firstname", "given name"),
		NOM("Nom", ExigenceChamp.CREATION, VERROU_CREATION, "nom", "nom de famille", "last name", "lastname",
				"family name", "surname"),
		COURRIEL("Courriel", ExigenceChamp.FACULTATIF, null, "courriel", "email", "e mail", "mail", "adresse email",
				"adresse mail", "adresse e mail", "courrier electronique", "courriel professionnel", "email professionnel"),
		UNITE("Unité", ExigenceChamp.CREATION, VERROU_CREATION, "unite", "code unite", "libelle unite",
				"unite d affectation", "unite organisationnelle", "service", "departement"),
		POSTE("Poste", ExigenceChamp.FACULTATIF, null, "poste", "intitule du poste", "intitule poste", "libelle poste",
				"job title"),
		FONCTION("Fonction", ExigenceChamp.FACULTATIF, null, "fonction"),
		QUALITE("Qualité", ExigenceChamp.FACULTATIF, null, "qualite"),
		AFFECTATION("Affectation", ExigenceChamp.FACULTATIF, null, "affectation", "lieu de travail", "lieu d affectation",
				"site"),
		DATE_RECRUTEMENT("Date de recrutement", ExigenceChamp.FACULTATIF, null, "date de recrutement", "date recrutement",
				"recrutement", "date d embauche", "date embauche", "embauche", "date d entree", "date entree"),
		STATUT("Statut", ExigenceChamp.FACULTATIF, null, "statut", "etat", "statut collaborateur"),
		PROFIL_ACCES("Profil d'accès", ExigenceChamp.FACULTATIF, null, "profil", "profil d acces", "profil acces", "role",
				"niveau d acces"),
		FAMILLE_METIER("Famille métier", ExigenceChamp.FACULTATIF, null, "famille", "famille metier", "famille de metier",
				"filiere"),
		NIVEAU_SENIORITE("Niveau de séniorité", ExigenceChamp.FACULTATIF, null, "niveau", "niveau de seniorite",
				"niveau seniorite", "seniorite");

		private final String libelle;
		private final ExigenceChamp exigence;
		private final String verrou;
		private final List<String> synonymes;

		/** Champ fixe de ce code ; {@code null} pour un champ personnalisé, une colonne ignorée ou un code inconnu. */
		public static Fiche parCode(String code) {
			for (Fiche f : values()) {
				if (f.name().equals(code)) {
					return f;
				}
			}
			return null;
		}

		/** Défaut = minimum pour les champs du code livré (un champ verrouillé démarre à son verrou). */
		Fiche(String libelle, ExigenceChamp exigence, String verrou, String... synonymes) {
			this.libelle = libelle;
			this.exigence = exigence;
			this.verrou = verrou;
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
		public ExigenceChamp exigenceDefaut() {
			return exigence;
		}

		@Override
		public ExigenceChamp exigenceMinimale() {
			return exigence;
		}

		@Override
		public String verrouLibelle() {
			return verrou;
		}
	}

	enum Solde implements ChampImport {
		MATRICULE("Matricule", ExigenceChamp.TOUJOURS, VERROU_MATRICULE, "matricule", "mat", "n matricule", "no matricule",
				"num matricule", "numero matricule", "numero de matricule"),
		SOLDE("Solde", ExigenceChamp.TOUJOURS, VERROU_SOLDE, "solde", "solde conge", "solde de conge", "solde cible",
				"solde au", "solde acquis", "jours restants", "reliquat", "droit restant", "droits restants",
				"solde conges"),
		MOTIF("Motif", ExigenceChamp.FACULTATIF, null, "motif", "commentaire", "observation", "observations", "remarque",
				"raison");

		private final String libelle;
		private final ExigenceChamp exigence;
		private final String verrou;
		private final List<String> synonymes;

		/** Champ fixe de ce code ; {@code null} pour un champ personnalisé, une colonne ignorée ou un code inconnu. */
		public static Solde parCode(String code) {
			for (Solde f : values()) {
				if (f.name().equals(code)) {
					return f;
				}
			}
			return null;
		}

		Solde(String libelle, ExigenceChamp exigence, String verrou, String... synonymes) {
			this.libelle = libelle;
			this.exigence = exigence;
			this.verrou = verrou;
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
		public ExigenceChamp exigenceDefaut() {
			return exigence;
		}

		@Override
		public ExigenceChamp exigenceMinimale() {
			return exigence;
		}

		@Override
		public String verrouLibelle() {
			return verrou;
		}
	}
}
