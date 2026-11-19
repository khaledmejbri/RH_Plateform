package com.hr.referentiel.champsperso;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Génération des codes de champ et de valeur (P2-2). */
class CodesChampPersoTest {

	@Test
	@DisplayName("Code de champ : accents retirés, symboles en _, majuscules, préfixe PERSO_")
	void codeDeChamp() {
		assertThat(CodesChampPerso.champ("[RECETTE-PERSO] Taille de tenue", Set.of())).isEqualTo("PERSO_RECETTE_PERSO_TAILLE_DE_TENUE");
		assertThat(CodesChampPerso.champ("Pointure d'été", Set.of())).isEqualTo("PERSO_POINTURE_D_ETE");
		assertThat(CodesChampPerso.champ("???", Set.of())).isEqualTo("PERSO_CHAMP");
	}

	@Test
	@DisplayName("Code pris : suffixes _2, _3 ; code tronqué à 40 caractères, suffixe compris")
	void codePrisEtTronque() {
		assertThat(CodesChampPerso.champ("Casier", Set.of("PERSO_CASIER"))).isEqualTo("PERSO_CASIER_2");
		assertThat(CodesChampPerso.champ("Casier", Set.of("PERSO_CASIER", "PERSO_CASIER_2"))).isEqualTo("PERSO_CASIER_3");
		String long1 = CodesChampPerso.champ("a".repeat(70), Set.of());
		assertThat(long1).hasSize(40);
		String long2 = CodesChampPerso.champ("a".repeat(70), Set.of(long1));
		assertThat(long2).hasSize(40).endsWith("_2");
	}

	@Test
	@DisplayName("Code de valeur de liste : sans préfixe ; « XL » -> XL, « 2026 » -> 2026, vide -> VALEUR")
	void codeDeValeur() {
		assertThat(CodesChampPerso.valeur("XL", Set.of())).isEqualTo("XL");
		assertThat(CodesChampPerso.valeur("2026", Set.of())).isEqualTo("2026");
		assertThat(CodesChampPerso.valeur("!!", Set.of())).isEqualTo("VALEUR");
		assertThat(CodesChampPerso.valeur("XL", Set.of("XL"))).isEqualTo("XL_2");
	}
}
