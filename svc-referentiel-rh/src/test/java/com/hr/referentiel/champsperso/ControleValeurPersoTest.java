package com.hr.referentiel.champsperso;

import com.hr.referentiel.champsperso.ControleValeurPerso.Mode;
import com.hr.referentiel.champsperso.ControleValeurPerso.Resultat;
import com.hr.referentiel.importation.parametrage.ParametrageImport.DefinitionPerso;
import com.hr.referentiel.importation.parametrage.ParametrageImport.ValeurListe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** T6 règles 1 et 2 : contrôle pur par type (modes saisie et import) et formats canonique / d'affichage. */
class ControleValeurPersoTest {

	private static final DefinitionPerso POINTURE = new DefinitionPerso("PERSO_POINTURE", "Pointure", TypeValeurPerso.NOMBRE,
			new ReglesPerso(null, 0, new BigDecimal("30"), new BigDecimal("50"), null, null), List.of());
	private static final DefinitionPerso PRIX = new DefinitionPerso("PERSO_PRIX", "Prix", TypeValeurPerso.NOMBRE,
			new ReglesPerso(null, 2, null, null, null, null), List.of());
	private static final DefinitionPerso CODE = new DefinitionPerso("PERSO_CODE", "Code vestiaire", TypeValeurPerso.TEXTE,
			new ReglesPerso(5, null, null, null, null, null), List.of());
	private static final DefinitionPerso BADGE = new DefinitionPerso("PERSO_BADGE", "Date de badge", TypeValeurPerso.DATE,
			new ReglesPerso(null, null, null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)), List.of());
	private static final DefinitionPerso TAILLE = new DefinitionPerso("PERSO_TAILLE", "Taille de tenue", TypeValeurPerso.LISTE,
			ReglesPerso.AUCUNE, List.of(new ValeurListe("S", "Small", true), new ValeurListe("M", "Médium", true),
					new ValeurListe("XL", "Extra large", false)));

	private static Resultat saisie(DefinitionPerso d, String brut) {
		return ControleValeurPerso.lire(d, brut, Mode.SAISIE, d.libelle());
	}

	private static String code(Resultat r) {
		return r.probleme() == null ? null : r.probleme().code();
	}

	@Test
	@DisplayName("T6-3 : « 4 5 » vaut 45, « 42,0 » et « 42.00 » valent 42, « 42,01 » est refusé sans arrondi")
	void nombreEspacesZerosFinauxEtDecimales() {
		assertThat(saisie(POINTURE, "4 5").valeur().nombre()).isEqualByComparingTo("45");
		assertThat(saisie(POINTURE, "42,0").valeur().nombre()).isEqualByComparingTo("42");
		assertThat(saisie(POINTURE, "42.00").valeur().nombre()).isEqualByComparingTo("42");
		assertThat(saisie(POINTURE, "42 ").valeur().nombre()).isEqualByComparingTo("42");
		Resultat r = saisie(POINTURE, "42,01");
		assertThat(code(r)).isEqualTo("VALEUR_INVALIDE");
		assertThat(r.probleme().message()).isEqualTo("Pointure : 0 décimale autorisée.");
	}

	@Test
	@DisplayName("T6-3 : « 1 000 » (espace retiré) vaut 1000 et dépasse le maximum ; « 12 » sous le minimum ; « abc » et « 1,5,5 » sont des formats invalides")
	void nombreBornesEtFormat() {
		assertThat(code(saisie(POINTURE, "1 000"))).isEqualTo("VALEUR_HORS_BORNES");
		assertThat(code(saisie(POINTURE, "12"))).isEqualTo("VALEUR_HORS_BORNES");
		assertThat(code(saisie(POINTURE, "abc"))).isEqualTo("FORMAT_INVALIDE");
		assertThat(code(saisie(POINTURE, "1,5,5"))).isEqualTo("FORMAT_INVALIDE");
		assertThat(code(saisie(POINTURE, "1.000,5"))).isEqualTo("FORMAT_INVALIDE");
	}

	@Test
	@DisplayName("T6-3 : un nombre issu d'Excel (artefact flottant) est arrondi à 9 décimales en import, jamais en saisie")
	void nombreExcelEnImport() {
		assertThat(ControleValeurPerso.lire(PRIX, "12.340000000001", Mode.IMPORT, "Prix").valeur().nombre()).isEqualByComparingTo("12.34");
		assertThat(code(saisie(PRIX, "12.340000000001"))).isEqualTo("VALEUR_INVALIDE");
		assertThat(FormatValeurPerso.canonique(saisie(PRIX, "12,50").valeur())).isEqualTo("12.5");
		assertThat(FormatValeurPerso.affichage(saisie(PRIX, "12.5").valeur(), PRIX)).isEqualTo("12,5");
	}

	@Test
	@DisplayName("T6-2 : texte rogné, trop long refusé, caractères de contrôle refusés")
	void texte() {
		assertThat(saisie(CODE, "  B-12 ").valeur().texte()).isEqualTo("B-12");
		assertThat(code(saisie(CODE, "ABCDEF"))).isEqualTo("VALEUR_TROP_LONGUE");
		assertThat(code(saisie(CODE, "B\u0007-1"))).isEqualTo("VALEUR_INVALIDE");
		assertThat(saisie(CODE, "   ").vide()).isTrue();
		assertThat(saisie(CODE, null).vide()).isTrue();
	}

	@Test
	@DisplayName("T6-2 : date jj/mm/aaaa ou aaaa-mm-jj ; 31/02 refusé ; hors bornes refusé ; date Excel acceptée en import seulement")
	void date() {
		assertThat(saisie(BADGE, "15/11/2026").valeur().date()).isEqualTo(LocalDate.of(2026, 11, 15));
		assertThat(saisie(BADGE, "2026-11-15").valeur().date()).isEqualTo(LocalDate.of(2026, 11, 15));
		assertThat(code(saisie(BADGE, "31/02/2026"))).isEqualTo("DATE_INVALIDE");
		assertThat(code(saisie(BADGE, "15/11/2027"))).isEqualTo("VALEUR_HORS_BORNES");
		assertThat(code(saisie(BADGE, "46341"))).isEqualTo("DATE_INVALIDE"); // numéro de série : import seulement
		assertThat(ControleValeurPerso.lire(BADGE, "46341", Mode.IMPORT, "Date de badge").valeur().date())
				.isEqualTo(LocalDate.of(1899, 12, 30).plusDays(46341));
		assertThat(FormatValeurPerso.affichage(saisie(BADGE, "2026-11-05").valeur(), BADGE)).isEqualTo("05/11/2026");
	}

	@Test
	@DisplayName("T6-2 : liste : le code exact en saisie, libellé ou code sans casse ni accents en import ; valeur inconnue ou désactivée refusée")
	void liste() {
		assertThat(saisie(TAILLE, "M").valeur().listeCode()).isEqualTo("M");
		assertThat(code(saisie(TAILLE, "m"))).isEqualTo("VALEUR_INCONNUE");
		assertThat(ControleValeurPerso.lire(TAILLE, "medium", Mode.IMPORT, "Taille").valeur().listeCode()).isEqualTo("M");
		assertThat(ControleValeurPerso.lire(TAILLE, "s", Mode.IMPORT, "Taille").valeur().listeCode()).isEqualTo("S");
		assertThat(code(saisie(TAILLE, "XXS"))).isEqualTo("VALEUR_INCONNUE");
		Resultat desactivee = saisie(TAILLE, "XL");
		assertThat(code(desactivee)).isEqualTo("VALEUR_INCONNUE");
		assertThat(desactivee.probleme().message()).contains("désactivée");
		assertThat(FormatValeurPerso.affichage(ValeurTypee.liste("M"), TAILLE)).isEqualTo("Médium");
	}

	@Test
	@DisplayName("Comparaison typée : 42 = 42,0 ; deux valeurs de types différents ne sont jamais égales ; canonique relisible")
	void comparaisonTypee() {
		assertThat(ValeurTypee.nombre(new BigDecimal("42")).memeValeur(ValeurTypee.nombre(new BigDecimal("42.0")))).isTrue();
		assertThat(ValeurTypee.texte("42").memeValeur(ValeurTypee.nombre(new BigDecimal("42")))).isFalse();
		assertThat(ValeurTypee.nombre(new BigDecimal("1E+2")).nombre().toPlainString()).isEqualTo("100");
		ValeurTypee d = ValeurTypee.date(LocalDate.of(2026, 5, 4));
		assertThat(FormatValeurPerso.depuisCanonique(TypeValeurPerso.DATE, FormatValeurPerso.canonique(d)).memeValeur(d)).isTrue();
		assertThat(FormatValeurPerso.depuisCanonique(TypeValeurPerso.NOMBRE, "abc")).isNull();
	}
}
