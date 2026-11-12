package com.hr.referentiel.domain;

import java.util.List;
import java.util.Optional;

/**
 * Catalogue des référentiels paramétrables du service référentiel (ADR-UAT-lot3 § 1 et § 7).
 * Le {@link #name()} est la clé {@code referentiel} de la table ; les fronts construisent l'écran Paramètres depuis
 * {@link #descripteur} sans liste figée de leur côté.
 */
public enum ReferentielParametre {

	FAMILLE_METIER("Familles métier", Domaines.ORGANISATION, NatureParametre.VALEUR, true, List.of()),
	NIVEAU_SENIORITE("Niveaux de séniorité", Domaines.ORGANISATION, NatureParametre.VALEUR, true, List.of()),
	PROFIL_ACCES("Profils d'accès", Domaines.ORGANISATION, NatureParametre.COMPORTEMENT, false, List.of()),
	STATUT_COLLABORATEUR("Statuts des collaborateurs", Domaines.ORGANISATION, NatureParametre.COMPORTEMENT, false, List.of()),

	TYPE_CONGE("Types de congé", Domaines.DEMANDES, NatureParametre.VALEUR, true,
			List.of(new Attribut("piece_jointe_obligatoire", "BOOLEEN"))),
	TYPE_DEMANDE("Types de demande", Domaines.DEMANDES, NatureParametre.COMPORTEMENT, true, List.of()),
	STATUT_DEMANDE("Statuts des demandes", Domaines.DEMANDES, NatureParametre.COMPORTEMENT, true, List.of()),

	TYPE_DOCUMENT("Types de document", Domaines.DOCUMENTS, NatureParametre.VALEUR, true,
			List.of(new Attribut("sla_heures", "ENTIER"))),
	STATUT_DOCUMENT("Statuts des demandes de document", Domaines.DOCUMENTS, NatureParametre.COMPORTEMENT, true, List.of()),

	TYPE_PLAINTE("Types de plainte", Domaines.PLAINTES_FORMATIONS, NatureParametre.COMPORTEMENT, true, List.of()),
	STATUT_PLAINTE("Statuts des plaintes", Domaines.PLAINTES_FORMATIONS, NatureParametre.COMPORTEMENT, true, List.of()),
	STATUT_FORMATION("Statuts des demandes de formation", Domaines.PLAINTES_FORMATIONS, NatureParametre.COMPORTEMENT, true,
			List.of());

	/** Domaines de l'écran (D-R10). */
	public static final class Domaines {
		public static final String ORGANISATION = "ORGANISATION";
		public static final String DEMANDES = "DEMANDES";
		public static final String DOCUMENTS = "DOCUMENTS";
		public static final String PLAINTES_FORMATIONS = "PLAINTES_FORMATIONS";

		private Domaines() {
		}
	}

	/** Attribut propre à un référentiel (en plus de libellé, description, couleur, ordre). */
	public record Attribut(String nom, String type) {
	}

	private final String libelle;
	private final String domaine;
	private final NatureParametre nature;
	private final boolean lisiblePartout;
	private final List<Attribut> attributs;

	ReferentielParametre(String libelle, String domaine, NatureParametre nature, boolean lisiblePartout,
			List<Attribut> attributs) {
		this.libelle = libelle;
		this.domaine = domaine;
		this.nature = nature;
		this.lisiblePartout = lisiblePartout;
		this.attributs = attributs;
	}

	public String getLibelle() {
		return libelle;
	}

	public String getDomaine() {
		return domaine;
	}

	public NatureParametre getNature() {
		return nature;
	}

	/** Vrai : les valeurs actives sont servies à tout utilisateur authentifié (liste blanche du mobile, ADR § 7). */
	public boolean isLisiblePartout() {
		return lisiblePartout;
	}

	public List<Attribut> getAttributs() {
		return attributs;
	}

	public boolean estComportement() {
		return nature == NatureParametre.COMPORTEMENT;
	}

	public static Optional<ReferentielParametre> parse(String brut) {
		if (brut == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(valueOf(brut.trim().toUpperCase(java.util.Locale.ROOT)));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
