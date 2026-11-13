package com.hr.referentiel.importation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** C2-03, C3-08 : normalisation D-I03 et synonymes (égalité stricte, pas de recherche partielle). */
class ReconnaissanceEnTetesTest {

	private final ReconnaissanceEnTetes fiches = new ReconnaissanceEnTetes(List.of(ChampImport.Fiche.values()));
	private final ReconnaissanceEnTetes soldes = new ReconnaissanceEnTetes(List.of(ChampImport.Solde.values()));

	private static String champ(ReconnaissanceEnTetes r, String entete) {
		return r.reconnaitre(entete).map(ChampImport::name).orElse(null);
	}

	@Test
	@DisplayName("C2-03 : « MATRICULE* », « Prenom (obligatoire) », « E-mail », « e_mail », « N° matricule », « Unité d'affectation », « Mat. », « NOM », espaces")
	void reconnus() {
		assertThat(champ(fiches, "MATRICULE*")).isEqualTo("MATRICULE");
		assertThat(champ(fiches, "Prenom (obligatoire)")).isEqualTo("PRENOM");
		assertThat(champ(fiches, "E-mail")).isEqualTo("COURRIEL");
		assertThat(champ(fiches, "e_mail")).isEqualTo("COURRIEL");
		assertThat(champ(fiches, "N° matricule")).isEqualTo("MATRICULE");
		assertThat(champ(fiches, "Unité d'affectation")).isEqualTo("UNITE");
		assertThat(champ(fiches, "Mat.")).isEqualTo("MATRICULE");
		assertThat(champ(fiches, "NOM")).isEqualTo("NOM");
		assertThat(champ(fiches, "  Prénom  ")).isEqualTo("PRENOM");
		assertThat(champ(fiches, "Code unité")).isEqualTo("UNITE");
		assertThat(champ(fiches, "Service")).isEqualTo("UNITE");
	}

	@Test
	@DisplayName("C2-03 : « Matricule agent » n'est pas reconnu (pas de recherche partielle) ; « Téléphone », « Supérieur », « Nom et prénom » non plus")
	void nonReconnus() {
		assertThat(champ(fiches, "Matricule agent")).isNull();
		assertThat(champ(fiches, "Téléphone")).isNull();
		assertThat(champ(fiches, "Supérieur")).isNull();
		assertThat(champ(fiches, "Nom et prénom")).isNull();
		assertThat(champ(fiches, "")).isNull();
		assertThat(champ(fiches, null)).isNull();
	}

	@Test
	@DisplayName("C3-08 : « Solde au 30/11 » (coupure au premier mot de chiffres), « Solde congé », « Solde de congé », « Jours restants », « Reliquat », « Droit restant », « Commentaire », « N° matricule »")
	void soldes() {
		assertThat(champ(soldes, "Solde au 30/11")).isEqualTo("SOLDE");
		assertThat(champ(soldes, "Solde congé")).isEqualTo("SOLDE");
		assertThat(champ(soldes, "Solde de congé")).isEqualTo("SOLDE");
		assertThat(champ(soldes, "Jours restants")).isEqualTo("SOLDE");
		assertThat(champ(soldes, "Reliquat")).isEqualTo("SOLDE");
		assertThat(champ(soldes, "Droit restant")).isEqualTo("SOLDE");
		assertThat(champ(soldes, "Commentaire")).isEqualTo("MOTIF");
		assertThat(champ(soldes, "Observation")).isEqualTo("MOTIF");
		assertThat(champ(soldes, "N° matricule")).isEqualTo("MATRICULE");
	}

	@Test
	@DisplayName("T4 C4-09 : les 14 libellés canoniques du modèle sont tous reconnus et distincts")
	void modeleCanonique() {
		List<String> noms = new java.util.ArrayList<>();
		for (ChampImport.Fiche f : ChampImport.Fiche.values()) {
			assertThat(champ(fiches, f.libelle())).as(f.libelle()).isEqualTo(f.name());
			noms.add(f.name());
		}
		assertThat(noms).hasSize(14).doesNotHaveDuplicates();
		assertThat(fiches.compterReconnus(java.util.Arrays.stream(ChampImport.Fiche.values()).map(ChampImport::libelle).toList()))
				.isEqualTo(14);
		for (ChampImport.Solde s : ChampImport.Solde.values()) {
			assertThat(champ(soldes, s.libelle())).isEqualTo(s.name());
		}
	}

	@Test
	@DisplayName("normalisation : minuscules, sans accents, ponctuation en espace, coupure au premier mot de chiffres")
	void normalisation() {
		assertThat(ReconnaissanceEnTetes.normaliser("  Date_de-RECRUTEMENT (obligatoire) * ")).isEqualTo("date de recrutement");
		assertThat(ReconnaissanceEnTetes.normaliser("Solde 2026 final")).isEqualTo("solde");
		assertThat(ReconnaissanceEnTetes.normaliser("Été")).isEqualTo("ete");
	}
}
