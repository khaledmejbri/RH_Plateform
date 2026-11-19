package com.hr.referentiel.champsperso;

import com.hr.referentiel.champsperso.ChampPersoDtos.ModifierChampPerso;
import com.hr.referentiel.champsperso.ChampPersoDtos.ValeurRequest;
import com.hr.referentiel.champsperso.ValeursPersoLot.Demande;
import com.hr.referentiel.champsperso.ValeursPersoMouvementLecture.Extras;
import com.hr.referentiel.champsperso.ValeursPersoMouvementLecture.Vue;
import com.hr.referentiel.importation.parametrage.ParametrageImport;
import com.hr.referentiel.importation.parametrage.TypeImportParametrable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** T7 / T8 : écriture groupée d'un import confirmé (fiches et mouvements) et lecture des valeurs des mouvements. */
class ValeursPersoLotEtLectureTest extends ChampsPersoH2Base {

	private ValeursPersoLot lot;
	private ValeursPersoMouvementLecture lecture;
	private String pointure;
	private String taille;
	private final UUID importId = UUID.randomUUID();

	@BeforeEach
	void init() {
		lot = new ValeursPersoLot(definitionsLecture, valeursJdbc, clock);
		lecture = new ValeursPersoMouvementLecture(definitionsLecture, valeursJdbc);
		pointure = creer(COL, "Pointure", "NOMBRE", regles(null, 0, null, null));
		taille = creer(COL, "Taille de tenue", "LISTE", SANS_REGLE, "S", "M", "L");
	}

	private ParametrageImport figee(TypeImportParametrable type) {
		cache.invalider(type);
		return cache.courant(type);
	}

	private long version(UUID fiche) {
		return valeursJdbc.version(fiche);
	}

	@Test
	@DisplayName("T7-8 : insertion groupée, journal « Import » avec identifiant d'import et auteur, version + 1 par fiche changée ; un second passage identique ne change rien")
	void ecritureGroupeeEtIdempotence() {
		ParametrageImport p = figee(TypeImportParametrable.COLLABORATEURS);
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		List<Demande> demandes = List.of(new Demande(a, 2, Map.of(pointure, "42", taille, "M")), new Demande(b, 3, Map.of(pointure, "38")));
		assertThat(lot.ecrireFiches(demandes, p, importId, DRH01)).isEmpty();
		assertThat(valeursJdbc.fiche(a)).containsKeys(pointure, taille);
		assertThat(version(a)).isEqualTo(1);
		assertThat(version(b)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from rh_valeur_perso_journal where origine = 'IMPORT' and import_id = ? "
				+ "and auteur_nom = 'Amel Trabelsi'", Long.class, importId)).isEqualTo(3);
		assertThat(lot.ecrireFiches(demandes, p, importId, DRH01)).isEmpty();
		assertThat(version(a)).isEqualTo(1); // aucune différence : ni version ni journal
		assertThat(jdbc.queryForObject("select count(*) from rh_valeur_perso_journal", Long.class)).isEqualTo(3);
		lot.ecrireFiches(List.of(new Demande(a, 2, Map.of(pointure, "43"))), p, importId, DRH01);
		assertThat(version(a)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select avant from rh_valeur_perso_journal where apres = '43'", String.class)).isEqualTo("42");
	}

	@Test
	@DisplayName("T7-9 : champ supprimé, type changé ou valeur de liste supprimée depuis l'analyse -> valeur non écrite avec avertissement ; champ ou valeur seulement désactivés -> écrite")
	void changementsDepuisLAnalyse() {
		ParametrageImport p = figee(TypeImportParametrable.COLLABORATEURS);
		UUID a = UUID.randomUUID();
		// désactivés depuis l'analyse : écrits (paramétrage figé, D-I25)
		definitions.desactiver(COL, pointure, version(COL), DRH01);
		listes.basculer(COL, taille, "M", version(COL), false, DRH01);
		List<String> avertissements = lot.ecrireFiches(List.of(new Demande(a, 2, Map.of(pointure, "42", taille, "M"))), p, importId, DRH01);
		assertThat(avertissements).isEmpty();
		assertThat(valeursJdbc.fiche(a)).containsKeys(pointure, taille);

		// valeur de liste supprimée, puis champ retypé, puis champ supprimé
		UUID b = UUID.randomUUID();
		listes.basculer(COL, taille, "S", version(COL), false, DRH01); // garde L actif : suppression de S possible
		listes.supprimer(COL, taille, "S", version(COL), DRH01);
		avertissements = lot.ecrireFiches(List.of(new Demande(b, 5, Map.of(taille, "S"))), p, importId, DRH01);
		assertThat(avertissements).singleElement().asString().contains("Ligne 5").contains("Taille de tenue")
				.contains("supprimé ou modifié depuis l'analyse");
		assertThat(valeursJdbc.fiche(b)).isEmpty();

		String champ = creer(COL, "Code vestiaire", "TEXTE", SANS_REGLE);
		ParametrageImport p2 = figee(TypeImportParametrable.COLLABORATEURS);
		definitions.modifier(COL, champ, new ModifierChampPerso(version(COL), "Code vestiaire", "Finalité de test", "NOMBRE",
				regles(null, 0, null, null), null, false), DRH01);
		assertThat(lot.ecrireFiches(List.of(new Demande(b, 6, Map.of(champ, "B12"))), p2, importId, DRH01)).hasSize(1);
		definitions.supprimer(COL, champ, version(COL), DRH01);
		assertThat(lot.ecrireFiches(List.of(new Demande(b, 7, Map.of(champ, "B12"))), p2, importId, DRH01)).hasSize(1);
		assertThat(valeursJdbc.fiche(b)).isEmpty();
	}

	@Test
	@DisplayName("T8-1 : les valeurs d'un champ « soldes » s'insèrent sur le mouvement d'ajustement ; champ supprimé depuis l'analyse -> avertissement")
	void valeursDeMouvements() {
		String reference = creer(SOL, "Reference de decision", "TEXTE", regles(50, null, null, null));
		ParametrageImport p = figee(TypeImportParametrable.SOLDES);
		UUID m1 = UUID.randomUUID();
		assertThat(lot.ecrireMouvements(List.of(new Demande(m1, 2, Map.of(reference, "DG-2026-15"))), p)).isEmpty();
		assertThat(valeursJdbc.mouvements(List.of(m1)).get(m1).get(reference).texte()).isEqualTo("DG-2026-15");
		erreurSuppression(reference);
		jdbc.update("delete from rh_valeur_perso_mouvement"); // seul un SQL manuel retire une valeur : l'API n'a aucune écriture
		definitions.supprimer(SOL, reference, version(SOL), DRH01);
		assertThat(lot.ecrireMouvements(List.of(new Demande(UUID.randomUUID(), 3, Map.of(reference, "X"))), p)).hasSize(1);
	}

	private void erreurSuppression(String champ) {
		erreur(echec(() -> definitions.supprimer(SOL, champ, version(SOL), DRH01)), 409, "PARAMETRE_UTILISE");
	}

	@Test
	@DisplayName("T8-6 : vue collaborateur = champs actifs ET visibles, sans la clé des champs désactivés ; vue back-office = actifs puis désactivés renseignés")
	void lectureDesMouvements() {
		String visible = creer(SOL, "Periode de reprise", "TEXTE", SANS_REGLE);
		String cache = creer(SOL, "Reference interne", "TEXTE", SANS_REGLE);
		String retire = creer(SOL, "Ancien champ", "TEXTE", SANS_REGLE);
		definitions.modifier(SOL, visible, new ModifierChampPerso(version(SOL), "Periode de reprise", "Finalité de test", "TEXTE",
				SANS_REGLE, null, true), DRH01);
		UUID mouvement = UUID.randomUUID();
		valeursJdbc.insererMouvements(List.of(new ValeursPersoJdbc.Ecriture(mouvement, visible, ValeurTypee.texte("2026")),
				new ValeursPersoJdbc.Ecriture(mouvement, cache, ValeurTypee.texte("REF-9")),
				new ValeursPersoJdbc.Ecriture(mouvement, retire, ValeurTypee.texte("vieux"))));
		definitions.desactiver(SOL, retire, version(SOL), DRH01);

		Extras collab = lecture.pour(List.of(mouvement), Vue.COLLABORATEUR).get(mouvement);
		assertThat(collab.champs()).extracting(v -> v.libelle()).containsExactly("Periode de reprise");
		assertThat(collab.desactives()).isNull(); // clé absente du JSON (NON_NULL)
		Extras back = lecture.pour(List.of(mouvement), Vue.BACKOFFICE).get(mouvement);
		assertThat(back.champs()).extracting(v -> v.libelle()).containsExactlyInAnyOrder("Periode de reprise", "Reference interne");
		assertThat(back.desactives()).extracting(v -> v.libelle()).containsExactly("Ancien champ");
		assertThat(lecture.champsActifs()).extracting(d -> d.champ().getLibelle()).doesNotContain("Ancien champ");
		UUID sans = UUID.randomUUID();
		assertThat(lecture.pour(List.of(sans), Vue.COLLABORATEUR).get(sans).champs()).isEmpty();
	}

	@Test
	@DisplayName("T8-6 : la table des valeurs de mouvement est immuable : aucune méthode de modification, entité @Immutable")
	void tableImmuable() throws Exception {
		assertThat(ValeurPersoMouvement.class.isAnnotationPresent(org.hibernate.annotations.Immutable.class)).isTrue();
		assertThat(ValeurPersoJournal.class.isAnnotationPresent(org.hibernate.annotations.Immutable.class)).isTrue();
		assertThat(java.util.Arrays.stream(ValeursPersoJdbc.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
				.doesNotContain("modifierMouvements", "supprimerMouvements");
		assertThat(ValeurRequest.class).isNotNull();
	}
}
