package com.hr.referentiel.importation.parametrage;

import com.hr.referentiel.importation.ChampImport;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Types d'import dont les champs sont paramétrables (D-I14) : lien entre le chemin d'API, la valeur de
 * {@code rh_import.type_import} ({@link #name()}) et la liste fermée des champs du code.
 */
public enum TypeImportParametrable {

	COLLABORATEURS("collaborateurs", "Collaborateurs", List.of(ChampImport.Fiche.values()),
			EnumSet.allOf(ExigenceChamp.class)),
	SOLDES("soldes", "Soldes de congé", List.of(ChampImport.Solde.values()),
			EnumSet.of(ExigenceChamp.FACULTATIF, ExigenceChamp.TOUJOURS));

	private final String chemin;
	private final String libelle;
	private final List<? extends ChampImport> champs;
	private final Set<ExigenceChamp> exigencesAutorisees;

	TypeImportParametrable(String chemin, String libelle, List<? extends ChampImport> champs,
			Set<ExigenceChamp> exigencesAutorisees) {
		this.chemin = chemin;
		this.libelle = libelle;
		this.champs = champs;
		this.exigencesAutorisees = exigencesAutorisees;
	}

	public String chemin() {
		return chemin;
	}

	public String libelle() {
		return libelle;
	}

	/** Champs du type, dans l'ordre par défaut. */
	public List<? extends ChampImport> champs() {
		return champs;
	}

	public boolean autorise(ExigenceChamp exigence) {
		return exigencesAutorisees.contains(exigence);
	}

	/** Exigences proposées pour un champ : autorisées par le type et au moins égales à son minimum. */
	public List<ExigenceChamp> exigencesPossibles(ChampImport champ) {
		return Arrays.stream(ExigenceChamp.values())
				.filter(e -> exigencesAutorisees.contains(e) && e.auMoins(champ.exigenceMinimale())).toList();
	}

	public Optional<ChampImport> champ(String code) {
		if (code == null) {
			return Optional.empty();
		}
		return champs.stream().filter(c -> c.name().equals(code)).map(c -> (ChampImport) c).findFirst();
	}

	public static Optional<TypeImportParametrable> parChemin(String chemin) {
		return Arrays.stream(values()).filter(t -> t.chemin.equals(chemin)).findFirst();
	}

	/** Valeur de {@code rh_import.type_import} (« COLLABORATEURS » / « SOLDES »). */
	public static Optional<TypeImportParametrable> parTypeImport(String typeImport) {
		return Arrays.stream(values()).filter(t -> t.name().equals(typeImport)).findFirst();
	}
}
