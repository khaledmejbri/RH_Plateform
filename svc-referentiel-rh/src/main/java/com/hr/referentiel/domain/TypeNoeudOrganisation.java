package com.hr.referentiel.domain;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * D-H04 : catalogue fermé des types de nœud, du plus haut au plus bas.
 * Un nœud n'a pour parent qu'un type de niveau strictement supérieur (ex. un service sous la DG, une direction
 * ou un département).
 */
public enum TypeNoeudOrganisation {

	DIRECTION_GENERALE("Direction Générale", 0),
	DIRECTION("Direction", 1),
	DEPARTEMENT("Département", 2),
	SERVICE("Service", 3);

	private final String libelle;
	private final int niveau;

	TypeNoeudOrganisation(String libelle, int niveau) {
		this.libelle = libelle;
		this.niveau = niveau;
	}

	public String getLibelle() {
		return libelle;
	}

	public int getNiveau() {
		return niveau;
	}

	/** Ce type peut-il être parent direct de {@code enfant} ? */
	public boolean peutContenir(TypeNoeudOrganisation enfant) {
		return enfant.niveau > niveau;
	}

	/** Reconnaît le libellé saisi (casse, accents, espaces et tirets ignorés). */
	public static Optional<TypeNoeudOrganisation> depuisLibelle(String brut) {
		if (brut == null || brut.isBlank()) {
			return Optional.empty();
		}
		String cle = normaliser(brut);
		return Arrays.stream(values()).filter(t -> normaliser(t.libelle).equals(cle)).findFirst();
	}

	public static String libellesAutorises() {
		return Arrays.stream(values()).map(TypeNoeudOrganisation::getLibelle).collect(Collectors.joining(", "));
	}

	private static String normaliser(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.replaceAll("[\\s_-]", "")
				.toLowerCase(Locale.ROOT);
	}
}
