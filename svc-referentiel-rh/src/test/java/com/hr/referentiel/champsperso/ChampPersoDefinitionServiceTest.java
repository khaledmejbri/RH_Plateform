package com.hr.referentiel.champsperso;

import com.hr.referentiel.champsperso.ChampPersoDtos.CreerChampPerso;
import com.hr.referentiel.champsperso.ChampPersoDtos.ModifierChampPerso;
import com.hr.referentiel.champsperso.ChampPersoDtos.ValeurRequest;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.ChampRequest;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.ChampVue;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.EnregistrerRequest;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.LigneJournal;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.Parametrage;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.SynonymeRequest;
import com.hr.referentiel.importation.parametrage.TypeImportParametrable;
import com.hr.referentiel.importation.parametrage.ParametrageImport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** T5 (SCRUM-99) : définitions des champs personnalisés C1 à C6, sur base H2 réelle. */
class ChampPersoDefinitionServiceTest extends ChampsPersoH2Base {

	private void donnerValeurFiche(String code) {
		jdbc.update("insert into rh_valeur_perso_fiche (id, societe_id, collaborateur_id, champ_code, valeur_texte, modifie_le) values (?, ?, ?, ?, 'x', current_timestamp)",
				UUID.randomUUID(), com.hr.commun.societe.SocieteContexte.courante(), UUID.randomUUID(), code);
	}

	private List<LigneJournal> journalDuType(String type) {
		return parametrage.lireJournal(type, 0, 100).contenu();
	}

	@Test
	@DisplayName("T5-1 : le code est généré en majuscules ASCII sans accent, préfixé PERSO_")
	void codeGenereDepuisLeLibelle() {
		String code = creer(COL, "Pointure d'été (EU)", "TEXTE", SANS_REGLE);
		assertThat(code).isEqualTo("PERSO_POINTURE_D_ETE_EU");
	}

	@Test
	@DisplayName("T5-1 : un code n'est jamais réattribué, même après suppression (suffixe _2)")
	void codeJamaisReattribue() {
		String premier = creer(COL, "Casier", "TEXTE", SANS_REGLE);
		definitions.supprimer(COL, premier, version(COL), DRH01);
		String second = creer(COL, "Casier", "TEXTE", SANS_REGLE);
		assertThat(premier).isEqualTo("PERSO_CASIER");
		assertThat(second).isEqualTo("PERSO_CASIER_2");
	}

	@Test
	@DisplayName("T5-2 : confirmation absente -> 400 CONFIRMATION_OBLIGATOIRE, rien n'est écrit")
	void confirmationObligatoire() {
		long v = version(COL);
		Throwable t = echec(() -> definitions.creer(COL, new CreerChampPerso(v, "Casier", "Usage du casier", "TEXTE", SANS_REGLE,
				null, false, false), DRH01));
		erreur(t, 400, "CONFIRMATION_OBLIGATOIRE");
		assertThat(version(COL)).isEqualTo(v);
		assertThat(persoRepository.count()).isZero();
	}

	@Test
	@DisplayName("T5-3 : version absente -> 400 ; périmée -> 409 PARAMETRE_MODIFIE_ENTRE_TEMPS sans écriture")
	void versionAbsenteOuPerimee() {
		erreur(echec(() -> definitions.creer(COL, new CreerChampPerso(null, "Casier", "Usage du casier", "TEXTE", SANS_REGLE, null,
				false, true), DRH01)), 400, "VERSION_OBLIGATOIRE");
		long v = version(COL);
		erreur(echec(() -> definitions.creer(COL, new CreerChampPerso(v - 1, "Casier", "Usage du casier", "TEXTE", SANS_REGLE, null,
				false, true), DRH01)), 409, "PARAMETRE_MODIFIE_ENTRE_TEMPS");
		assertThat(persoRepository.count()).isZero();
	}

	@Test
	@DisplayName("T5-4 : finalité hors 5-200 -> 400 CORPS_INVALIDE ; libellé « =1+1 » -> 400 LIBELLE_INVALIDE")
	void finaliteEtLibelleControles() {
		long v = version(COL);
		erreur(echec(() -> definitions.creer(COL, new CreerChampPerso(v, "Casier", "abc", "TEXTE", SANS_REGLE, null, false, true),
				DRH01)), 400, "CORPS_INVALIDE");
		erreur(echec(() -> definitions.creer(COL, new CreerChampPerso(v, "=1+1", "Finalité valable", "TEXTE", SANS_REGLE, null, false,
				true), DRH01)), 400, "LIBELLE_INVALIDE");
		erreur(echec(() -> definitions.creer(COL, new CreerChampPerso(v, "Casier", "Finalité valable", "BOOLEEN", SANS_REGLE, null,
				false, true), DRH01)), 400, "CORPS_INVALIDE");
	}

	@Test
	@DisplayName("T5-5 : règles par type (nombre : 0 à 4 décimales, bornes inversées ; règle d'un autre type ; liste vide ou > 100)")
	void reglesParType() {
		erreur(echec(() -> creer(COL, "Pointure", "NOMBRE", regles(null, 5, null, null))), 400, "REGLE_INVALIDE");
		erreur(echec(() -> creer(COL, "Pointure", "NOMBRE", regles(null, 0, "50", "30"))), 400, "REGLE_INVALIDE");
		erreur(echec(() -> creer(COL, "Code", "TEXTE", regles(null, 2, null, null))), 400, "REGLE_INVALIDE");
		erreur(echec(() -> creer(COL, "Code", "TEXTE", regles(501, null, null, null))), 400, "REGLE_INVALIDE");
		erreur(echec(() -> creer(COL, "Taille", "LISTE", SANS_REGLE)), 400, "REGLE_INVALIDE");
		String[] cent_un = new String[101];
		for (int i = 0; i < 101; i++) {
			cent_un[i] = "V" + i;
		}
		erreur(echec(() -> creer(COL, "Taille", "LISTE", SANS_REGLE, cent_un)), 400, "REGLE_INVALIDE");
		assertThat(creer(COL, "Pointure", "NOMBRE", regles(null, 0, "30", "50"))).isEqualTo("PERSO_POINTURE");
	}

	@Test
	@DisplayName("T5-6 : espace de noms commun en forme complète : « Adresse 1 » et « Adresse 2 » coexistent, « Prime » = « Primé » refusé")
	void espaceDeNomsCommunEnFormeComplete() {
		creer(COL, "Adresse 1", "TEXTE", SANS_REGLE);
		creer(COL, "Adresse 2", "TEXTE", SANS_REGLE);
		creer(COL, "Prime", "TEXTE", SANS_REGLE);
		erreur(echec(() -> creer(COL, "Primé", "TEXTE", SANS_REGLE)), 400, "LIBELLE_EN_DOUBLE");
		erreur(echec(() -> creer(COL, "Courriel", "TEXTE", SANS_REGLE)), 400, "LIBELLE_EN_DOUBLE"); // libellé d'un champ fixe
		erreur(echec(() -> creer(COL, "Mail", "TEXTE", SANS_REGLE)), 400, "LIBELLE_EN_DOUBLE"); // forme d'un synonyme de COURRIEL
		assertThat(champ(parametrage.lire(COL), "PERSO_ADRESSE_1").enTete()).isEqualTo("Adresse 1");
	}

	@Test
	@DisplayName("T5-7 : au plus 30 champs personnalisés par type (422 CHAMPS_PERSO_MAX), l'autre type n'est pas concerné")
	void trenteChampsAuMaximum() {
		for (int i = 0; i < 30; i++) {
			creer(COL, "Champ numero " + (char) ('a' + i % 26) + (char) ('a' + i / 26), "TEXTE", SANS_REGLE);
		}
		erreur(echec(() -> creer(COL, "Trente et un", "TEXTE", SANS_REGLE)), 422, "CHAMPS_PERSO_MAX");
		assertThat(creer(SOL, "Reference", "TEXTE", SANS_REGLE)).isEqualTo("PERSO_REFERENCE");
	}

	@Test
	@DisplayName("T5-8 : la création est dans le modèle, facultative, dernière dans l'ordre ; la version monte de exactement 1")
	void creationDansLeModeleEtVersionPlusUn() {
		long avant = version(COL);
		String code = creer(COL, "Pointure", "NOMBRE", regles(null, 0, null, null));
		Parametrage p = parametrage.lire(COL);
		ChampVue c = champ(p, code);
		assertThat(p.version()).isEqualTo(avant + 1);
		assertThat(c.dansModele()).isTrue();
		assertThat(c.exigence()).isEqualTo("FACULTATIF");
		assertThat(c.typeValeurLibelle()).isEqualTo("Nombre");
		assertThat(c.ordre()).isEqualTo(p.champs().stream().filter(x -> x.actif()).mapToInt(ChampVue::ordre).max().orElseThrow());
		assertThat(c.supprimable()).isTrue();
		assertThat(c.exigencesPossibles()).hasSize(3);
		String reference = creer(SOL, "Reference", "TEXTE", SANS_REGLE);
		assertThat(champ(parametrage.lire(SOL), reference).exigencesPossibles()).hasSize(2);
	}

	@Test
	@DisplayName("T5-9 : le journal est en clair, avec le libellé figé du champ et la confirmation « aucune donnée sensible »")
	void journalEnClair() {
		String code = creer(COL, "Taille de tenue", "LISTE", SANS_REGLE, "S", "M");
		LigneJournal l = journalDuType(COL).stream().filter(x -> "CHAMP_CREE".equals(x.attribut())).findFirst().orElseThrow();
		assertThat(l.champLibelle()).isEqualTo("Taille de tenue");
		assertThat(l.attributLibelle()).isEqualTo("Champ créé");
		assertThat(l.apres()).contains("Liste de valeurs").contains("aucune donnée sensible").doesNotContain("PERSO_");
		definitions.supprimer(COL, code, version(COL), DRH01);
		LigneJournal supprime = journalDuType(COL).stream().filter(x -> "CHAMP_SUPPRIME".equals(x.attribut())).findFirst().orElseThrow();
		assertThat(supprime.champLibelle()).isEqualTo("Taille de tenue"); // lisible après suppression
	}

	@Test
	@DisplayName("T5-10 : le type ne change plus dès qu'une valeur existe (409 PARAMETRE_UTILISE) ; avant, il change")
	void typeFigeDesUneValeur() {
		String code = creer(COL, "Pointure", "NOMBRE", regles(null, 0, null, null));
		ModifierChampPerso enTexte = new ModifierChampPerso(version(COL), "Pointure", "Finalité de test", "TEXTE", SANS_REGLE, null, false);
		donnerValeurFiche(code);
		erreur(echec(() -> definitions.modifier(COL, code, enTexte, DRH01)), 409, "PARAMETRE_UTILISE");
		jdbc.update("delete from rh_valeur_perso_fiche");
		ChampVue c = champ(definitions.modifier(COL, code, new ModifierChampPerso(version(COL), "Pointure", "Finalité de test", "TEXTE",
				SANS_REGLE, null, false), DRH01), code);
		assertThat(c.typeValeur()).isEqualTo("TEXTE");
	}

	@Test
	@DisplayName("T5-11 : C2 change libellé, finalité, règle et visibilité, une ligne de journal par attribut ; l'en-tête ne bouge pas")
	void modificationJournaliseParAttribut() {
		String code = creer(COL, "Pointure", "NOMBRE", regles(null, 0, "30", "50"));
		Parametrage p = definitions.modifier(COL, code, new ModifierChampPerso(version(COL), "Pointure chaussures", "Nouvelle finalité",
				"NOMBRE", regles(null, 1, "30", "50"), null, true), DRH01);
		assertThat(champ(p, code).libelle()).isEqualTo("Pointure chaussures");
		assertThat(champ(p, code).enTete()).isEqualTo("Pointure"); // l'en-tête ne suit pas le libellé
		assertThat(champ(p, code).visibleCollaborateur()).isTrue();
		assertThat(journalDuType(COL)).extracting(LigneJournal::attribut)
				.contains("LIBELLE", "FINALITE", "REGLE", "VISIBLE_COLLABORATEUR");
	}

	@Test
	@DisplayName("T5-12 : désactiver retire le champ du modèle et de l'instantané ; réactiver le remet en dernier, facultatif")
	void desactiverEtReactiver() {
		String code = creer(COL, "Pointure", "NOMBRE", regles(null, 0, null, null));
		Parametrage p = definitions.desactiver(COL, code, version(COL), DRH01);
		ChampVue c = champ(p, code);
		assertThat(c.actif()).isFalse();
		assertThat(c.dansModele()).isFalse();
		assertThat(c.ordre()).isNull();
		assertThat(p.champs().get(p.champs().size() - 1).code()).isEqualTo(code); // inactifs en dernier
		cache.invalider(TypeImportParametrable.COLLABORATEURS);
		ParametrageImport figee = cache.courant(TypeImportParametrable.COLLABORATEURS);
		assertThat(figee.champ(code)).isNull();
		assertThat(figee.definitionsPerso()).isEmpty();

		Parametrage r = definitions.reactiver(COL, code, version(COL), DRH01);
		assertThat(champ(r, code).actif()).isTrue();
		assertThat(champ(r, code).dansModele()).isTrue();
		cache.invalider(TypeImportParametrable.COLLABORATEURS);
		assertThat(cache.courant(TypeImportParametrable.COLLABORATEURS).definition(code)).isNotNull();
	}

	@Test
	@DisplayName("T5-13 : désactiver deux fois ne change ni la version ni le journal")
	void desactiverDeuxFoisSansEffet() {
		String code = creer(COL, "Pointure", "NOMBRE", regles(null, 0, null, null));
		definitions.desactiver(COL, code, version(COL), DRH01);
		long v = version(COL);
		int lignes = journalDuType(COL).size();
		definitions.desactiver(COL, code, v, DRH01);
		assertThat(version(COL)).isEqualTo(v);
		assertThat(journalDuType(COL)).hasSize(lignes);
	}

	@Test
	@DisplayName("T5-14 : à la réactivation, un synonyme devenu en conflit pendant l'inactivité est désactivé ; en-tête en conflit -> 400")
	void reactivationAvecSynonymeEnConflit() {
		String code = creer(COL, "Pointure", "NOMBRE", regles(null, 0, null, null));
		parametrage.ajouterSynonyme(COL, code, new SynonymeRequest("taille chaussure", version(COL)), DRH01);
		definitions.desactiver(COL, code, version(COL), DRH01);
		// un autre champ prend la forme du synonyme pendant l'inactivité
		creer(COL, "Taille chaussure", "TEXTE", SANS_REGLE);
		Parametrage p = definitions.reactiver(COL, code, version(COL), DRH01);
		assertThat(champ(p, code).synonymes()).allMatch(s -> !s.actif());
		assertThat(journalDuType(COL)).extracting(LigneJournal::attribut).contains("SYNONYME_DESACTIVE", "CHAMP_REACTIVE");

		String autre = creer(COL, "Gabarit", "TEXTE", SANS_REGLE);
		definitions.desactiver(COL, autre, version(COL), DRH01);
		creer(COL, "Gabarit bis", "TEXTE", SANS_REGLE);
		// renomme l'en-tête du champ actif « Gabarit bis » en « Gabarit » (libre puisque l'autre est inactif)
		List<ChampRequest> tableau = new ArrayList<>();
		for (ChampVue c : parametrage.lire(COL).champs()) {
			if (c.actif()) {
				tableau.add(new ChampRequest(c.code(), c.libelle().equals("Gabarit bis") ? "Gabarit" : c.enTete(), c.dansModele(), c.exigence()));
			}
		}
		parametrage.enregistrer(COL, new EnregistrerRequest(version(COL), tableau), DRH01);
		erreur(echec(() -> definitions.reactiver(COL, autre, version(COL), DRH01)), 400, "LIBELLE_EN_DOUBLE");
		assertThat(champ(parametrage.lire(COL), autre).actif()).isFalse(); // laissé inactif
	}

	@Test
	@DisplayName("T5-15 : supprimer est refusé (409 PARAMETRE_UTILISE) dès qu'une valeur existe, champ inactif compris ; sinon 204 et tout part")
	void suppression() {
		String code = creer(COL, "Casier", "TEXTE", SANS_REGLE);
		donnerValeurFiche(code);
		definitions.desactiver(COL, code, version(COL), DRH01);
		erreur(echec(() -> definitions.supprimer(COL, code, version(COL), DRH01)), 409, "PARAMETRE_UTILISE");
		jdbc.update("delete from rh_valeur_perso_fiche");
		definitions.supprimer(COL, code, version(COL), DRH01);
		assertThat(persoRepository.count()).isZero();
		assertThat(champRepository.findByTypeImportOrderByOrdreAsc("COLLABORATEURS")).noneMatch(r -> r.getChampCode().equals(code));
	}

	@Test
	@DisplayName("T5-16 : valeurs de liste : ajout, ordre dense, doublon, dernière active protégée, suppression d'une valeur utilisée refusée")
	void valeursDeListe() {
		String code = creer(COL, "Taille", "LISTE", SANS_REGLE, "S", "M");
		Parametrage p = listes.ajouter(COL, code, new ValeurRequest("XL", null, version(COL)), DRH01);
		assertThat(champ(p, code).valeursListe()).extracting(v -> v.code()).containsExactly("S", "M", "XL");
		erreur(echec(() -> listes.ajouter(COL, code, new ValeurRequest("xl", null, version(COL)), DRH01)), 400, "LIBELLE_EN_DOUBLE");
		p = listes.modifier(COL, code, "XL", new ValeurRequest("XL", 0, version(COL)), DRH01);
		assertThat(champ(p, code).valeursListe()).extracting(v -> v.code()).containsExactly("XL", "S", "M");
		assertThat(champ(p, code).valeursListe()).extracting(v -> v.ordre()).containsExactly(0, 1, 2);
		erreur(echec(() -> listes.modifier(COL, code, "XL", new ValeurRequest("XL", 7, version(COL)), DRH01)), 400, "CORPS_INVALIDE");
		erreur(echec(() -> listes.modifier(COL, code, "ZZ", new ValeurRequest("ZZ", 0, version(COL)), DRH01)), 404, "VALEUR_INTROUVABLE");

		listes.basculer(COL, code, "XL", version(COL), false, DRH01);
		listes.basculer(COL, code, "S", version(COL), false, DRH01);
		erreur(echec(() -> listes.basculer(COL, code, "M", version(COL), false, DRH01)), 422, "LISTE_SANS_VALEUR_ACTIVE");
		erreur(echec(() -> listes.supprimer(COL, code, "M", version(COL), DRH01)), 422, "LISTE_SANS_VALEUR_ACTIVE");

		jdbc.update("insert into rh_valeur_perso_fiche (id, societe_id, collaborateur_id, champ_code, valeur_liste_code, modifie_le) "
				+ "values (?, ?, ?, ?, 'S', current_timestamp)",
				UUID.randomUUID(), com.hr.commun.societe.SocieteContexte.courante(), UUID.randomUUID(), code);
		erreur(echec(() -> listes.supprimer(COL, code, "S", version(COL), DRH01)), 409, "PARAMETRE_UTILISE");
		listes.supprimer(COL, code, "XL", version(COL), DRH01);
		assertThat(champ(parametrage.lire(COL), code).valeursListe()).extracting(v -> v.code()).containsExactly("S", "M");
		assertThat(champ(parametrage.lire(COL), code).valeursListe().get(0).utilisations()).isEqualTo(1);
	}

	@Test
	@DisplayName("T5-17 : le tableau P2 contient champs fixes et personnalisés actifs ; un champ inactif -> 422 CHAMP_INACTIF ; ordre incomplet -> 400")
	void tableauDeLaPartieUn() {
		String code = creer(COL, "Pointure", "NOMBRE", regles(null, 0, null, null));
		String inactif = creer(COL, "Casier", "TEXTE", SANS_REGLE);
		definitions.desactiver(COL, inactif, version(COL), DRH01);
		List<ChampRequest> tableau = new ArrayList<>();
		for (ChampVue c : parametrage.lire(COL).champs()) {
			if (c.actif()) {
				tableau.add(new ChampRequest(c.code(), c.enTete(), c.dansModele(), c.code().equals(code) ? "TOUJOURS" : c.exigence()));
			}
		}
		Parametrage ok = parametrage.enregistrer(COL, new EnregistrerRequest(version(COL), tableau), DRH01);
		assertThat(champ(ok, code).exigence()).isEqualTo("TOUJOURS");

		List<ChampRequest> avecInactif = new ArrayList<>(tableau);
		avecInactif.add(new ChampRequest(inactif, "Casier", false, "FACULTATIF"));
		erreur(echec(() -> parametrage.enregistrer(COL, new EnregistrerRequest(version(COL), avecInactif), DRH01)), 422, "CHAMP_INACTIF");
		List<ChampRequest> incomplet = new ArrayList<>(tableau);
		incomplet.remove(incomplet.size() - 1);
		erreur(echec(() -> parametrage.enregistrer(COL, new EnregistrerRequest(version(COL), incomplet), DRH01)), 400, "ORDRE_INCOMPLET");
	}

	@Test
	@DisplayName("T5-18 : réinitialiser remet les champs fixes aux valeurs du code, les personnalisés actifs après eux (facultatifs, en-tête = libellé) et garde définitions et valeurs")
	void reinitialisation() {
		String code = creer(COL, "Pointure", "NOMBRE", regles(null, 0, null, null));
		String inactif = creer(COL, "Casier", "TEXTE", SANS_REGLE);
		definitions.desactiver(COL, inactif, version(COL), DRH01);
		parametrage.ajouterSynonyme(COL, code, new SynonymeRequest("chaussure", version(COL)), DRH01);
		Parametrage p = parametrage.reinitialiser(COL, version(COL), DRH01);
		assertThat(champ(p, code).synonymes()).isEmpty();
		assertThat(champ(p, code).enTete()).isEqualTo("Pointure");
		assertThat(champ(p, code).ordre()).isGreaterThanOrEqualTo(14); // après les 14 champs fixes
		assertThat(champ(p, inactif).dansModele()).isFalse();
		assertThat(persoRepository.count()).isEqualTo(2);
	}
}
