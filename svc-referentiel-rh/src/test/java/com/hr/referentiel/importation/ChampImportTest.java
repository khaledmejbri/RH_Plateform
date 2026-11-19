package com.hr.referentiel.importation;

import com.hr.referentiel.importation.parametrage.ExigenceChamp;
import com.hr.referentiel.importation.parametrage.TypeImportParametrable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Propriétés exigées des enums du code (ADR-IMPORT-CHAMPS § 4 point 4 et § 2) : l'amorçage et la reconnaissance en dépendent. */
class ChampImportTest {

	private static Stream<ChampImport> tous() {
		return Stream.concat(Stream.of(ChampImport.Fiche.values()), Stream.of(ChampImport.Solde.values()));
	}

	@Test
	@DisplayName("T1-25 : le libellé normalisé de chaque champ figure parmi ses synonymes par défaut (17 champs)")
	void libelleDansLesSynonymes() {
		assertThat(tous().count()).isEqualTo(17);
		tous().forEach(c -> assertThat(c.synonymes().stream().map(ReconnaissanceEnTetes::normaliser).toList())
				.as(c.name()).contains(ReconnaissanceEnTetes.normaliser(c.libelle())));
	}

	@Test
	@DisplayName("T1-25 : aucune forme normalisée par défaut n'est partagée entre deux champs d'un même type")
	void aucuneFormePartagee() {
		for (TypeImportParametrable type : TypeImportParametrable.values()) {
			Map<String, String> proprietaire = new HashMap<>();
			for (ChampImport c : type.champs()) {
				for (String s : c.synonymes()) {
					String autre = proprietaire.put(ReconnaissanceEnTetes.normaliser(s), c.name());
					assertThat(autre == null || autre.equals(c.name())).as(type + " " + s + " : " + autre + " / " + c.name()).isTrue();
				}
			}
		}
	}

	@Test
	@DisplayName("D-I14 / D-I16 : 14 champs collaborateurs et 3 champs soldes ; défauts, minimums et cadenas conformes au tableau de l'ADR")
	void exigencesParDefautEtVerrous() {
		assertThat(ChampImport.Fiche.values()).hasSize(14);
		assertThat(ChampImport.Solde.values()).hasSize(3);
		assertThat(ChampImport.Fiche.MATRICULE.exigenceDefaut()).isEqualTo(ExigenceChamp.TOUJOURS);
		assertThat(ChampImport.Fiche.MATRICULE.exigenceMinimale()).isEqualTo(ExigenceChamp.TOUJOURS);
		assertThat(ChampImport.Fiche.MATRICULE.verrouLibelle()).isEqualTo("Toujours obligatoire : sert à retrouver la fiche");
		for (ChampImport c : List.of(ChampImport.Fiche.PRENOM, ChampImport.Fiche.NOM, ChampImport.Fiche.UNITE)) {
			assertThat(c.exigenceDefaut()).isEqualTo(ExigenceChamp.CREATION);
			assertThat(c.exigenceMinimale()).isEqualTo(ExigenceChamp.CREATION);
			assertThat(c.verrouLibelle()).isEqualTo("Exigé pour créer une fiche");
			assertThat(c.retirableDuModele()).isFalse();
		}
		long libres = Stream.of(ChampImport.Fiche.values()).filter(c -> c.exigenceMinimale() == ExigenceChamp.FACULTATIF).count();
		assertThat(libres).isEqualTo(10);
		assertThat(ChampImport.Fiche.COURRIEL.verrouLibelle()).isNull();
		assertThat(ChampImport.Fiche.COURRIEL.retirableDuModele()).isTrue();
		assertThat(ChampImport.Solde.SOLDE.exigenceMinimale()).isEqualTo(ExigenceChamp.TOUJOURS);
		assertThat(ChampImport.Solde.SOLDE.verrouLibelle()).isEqualTo("Exigé pour chaque ligne");
		assertThat(ChampImport.Solde.MOTIF.exigenceDefaut()).isEqualTo(ExigenceChamp.FACULTATIF);
	}

	@Test
	@DisplayName("D-I15 : exigences possibles = autorisées par le type et au moins égales au minimum (soldes sans « à la création »)")
	void exigencesPossibles() {
		TypeImportParametrable collab = TypeImportParametrable.COLLABORATEURS;
		TypeImportParametrable soldes = TypeImportParametrable.SOLDES;
		assertThat(collab.exigencesPossibles(ChampImport.Fiche.MATRICULE)).containsExactly(ExigenceChamp.TOUJOURS);
		assertThat(collab.exigencesPossibles(ChampImport.Fiche.PRENOM)).containsExactly(ExigenceChamp.CREATION, ExigenceChamp.TOUJOURS);
		assertThat(collab.exigencesPossibles(ChampImport.Fiche.POSTE)).containsExactly(ExigenceChamp.FACULTATIF,
				ExigenceChamp.CREATION, ExigenceChamp.TOUJOURS);
		assertThat(soldes.exigencesPossibles(ChampImport.Solde.MOTIF)).containsExactly(ExigenceChamp.FACULTATIF, ExigenceChamp.TOUJOURS);
		assertThat(soldes.exigencesPossibles(ChampImport.Solde.SOLDE)).containsExactly(ExigenceChamp.TOUJOURS);
	}

	@Test
	@DisplayName("D-I14 : chemin d'API et type stocké sont liés ; chemin inconnu absent")
	void types() {
		assertThat(TypeImportParametrable.parChemin("collaborateurs")).contains(TypeImportParametrable.COLLABORATEURS);
		assertThat(TypeImportParametrable.parChemin("soldes")).contains(TypeImportParametrable.SOLDES);
		assertThat(TypeImportParametrable.parChemin("fournisseurs")).isEmpty();
		assertThat(TypeImportParametrable.COLLABORATEURS.name()).isEqualTo(ImportAnalyse.TYPE_COLLABORATEURS);
		assertThat(TypeImportParametrable.SOLDES.name()).isEqualTo(ImportAnalyse.TYPE_SOLDES);
	}
}
