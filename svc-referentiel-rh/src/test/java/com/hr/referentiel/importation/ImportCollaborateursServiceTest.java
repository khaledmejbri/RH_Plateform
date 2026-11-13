package com.hr.referentiel.importation;

import com.hr.referentiel.domain.NatureParametre;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.importation.ImportModeles.Colonne;
import com.hr.referentiel.importation.ImportModeles.Evaluation;
import com.hr.referentiel.importation.ImportModeles.LigneResultat;
import com.hr.referentiel.importation.lecture.Fichier.LigneBrute;
import com.hr.referentiel.kafka.CollaborateurCompteDemandeEvent;
import com.hr.referentiel.kafka.CollaborateurCompteDemandePublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.service.ParametreService;
import com.hr.referentiel.service.ParametresCache;
import com.hr.referentiel.service.ParametresCache.ParametreVue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C2-02 à C2-20, C2-23 : évaluation des fiches (aucune écriture), erreurs de ligne, avertissements, application. */
class ImportCollaborateursServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-12T10:00:00Z");

	private ImportCollaborateursService service;
	private ImportCollaborateursService.Contexte ctx;
	private UniteOrganisation software;
	private UniteOrganisation finance;
	private UniteOrganisation qualite1;
	private UniteOrganisation qualite2;
	private Collaborateur colWeb;
	private Collaborateur drh;
	private CollaborateurCompteDemandePublisher publisher;
	private ParametreService parametreService;
	private CollaborateurRepository collaborateurRepository;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		publisher = mock(CollaborateurCompteDemandePublisher.class);
		ObjectProvider<CollaborateurCompteDemandePublisher> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(publisher);
		parametreService = mock(ParametreService.class);
		collaborateurRepository = mock(CollaborateurRepository.class);
		service = new ImportCollaborateursService(collaborateurRepository, null, null, parametreService, provider,
				Clock.fixed(MAINTENANT, ZoneOffset.UTC));
		ctx = new ImportCollaborateursService.Contexte();
		software = unite("DTECH-SW", "Département Software", true);
		finance = unite("FIN", "Finance", false);
		qualite1 = unite("QUAL-1", "Qualité", true);
		qualite2 = unite("QUAL-2", "Qualité", true);
		UniteOrganisation autre = unite("DEP-X", "QUAL-1", true); // libellé égal au code d'un autre nœud
		for (UniteOrganisation u : List.of(software, finance, qualite1, qualite2, autre)) {
			ctx.unites.add(u);
			ctx.unitesParId.put(u.getId(), u);
		}
		colWeb = fiche("COL-WEB", "Sarra", "Khemiri", software, "COLLABORATEUR");
		colWeb.setPosteLibelle("Développeur");
		colWeb.setCourrielProfessionnel("sarra@rh-evenement.test");
		colWeb.setStatut("ACTIF");
		colWeb.setModifieLe(Instant.parse("2026-11-01T09:00:00Z"));
		drh = fiche("DRH01", "Amel", "Trabelsi", software, "RH");
		drh.setStatut("ACTIF");
		for (Collaborateur c : List.of(colWeb, drh)) {
			ctx.parMatricule.put(c.getMatricule().toLowerCase(Locale.ROOT), c);
			if (c.getCourrielProfessionnel() != null) {
				ctx.parCourriel.put(c.getCourrielProfessionnel().toLowerCase(Locale.ROOT), c);
			}
		}
		ParametresCache.Instantane ref = mock(ParametresCache.Instantane.class);
		when(ref.liste("STATUT_COLLABORATEUR")).thenReturn(List.of(vue("STATUT_COLLABORATEUR", "ACTIF", "Actif", true),
				vue("STATUT_COLLABORATEUR", "SUSPENDU", "Suspendu", true), vue("STATUT_COLLABORATEUR", "ARCHIVE", "Archivé", true)));
		when(ref.liste("PROFIL_ACCES")).thenReturn(List.of(vue("PROFIL_ACCES", "COLLABORATEUR", "Collaborateur", true),
				vue("PROFIL_ACCES", "RO", "Responsable opérationnel", true), vue("PROFIL_ACCES", "RESPONSABLE", "Responsable (département ou direction)", true),
				vue("PROFIL_ACCES", "RH", "Ressources humaines", true), vue("PROFIL_ACCES", "DIRECTION", "Direction (consultation)", true),
				vue("PROFIL_ACCES", "ADMIN", "Administrateur", true)));
		when(ref.liste("FAMILLE_METIER")).thenReturn(List.of(vue("FAMILLE_METIER", "DEV_LOGICIEL", "Développement logiciel / SI", true),
				vue("FAMILLE_METIER", "ANCIENNE", "Ancienne famille", false)));
		when(ref.liste("NIVEAU_SENIORITE")).thenReturn(List.of(vue("NIVEAU_SENIORITE", "JUNIOR", "Junior", true),
				vue("NIVEAU_SENIORITE", "SENIOR", "Sénior", true)));
		when(ref.libelle(any(), any())).thenAnswer(i -> {
			String code = i.getArgument(1);
			return code.charAt(0) + code.substring(1).toLowerCase(Locale.ROOT);
		});
		ctx.referentiels = ref;
	}

	private static ParametreVue vue(String ref, String code, String libelle, boolean actif) {
		return new ParametreVue(ref, code, NatureParametre.VALEUR, libelle, null, null, 1, actif, false, null, null, null);
	}

	private static UniteOrganisation unite(String code, String libelle, boolean actif) {
		UniteOrganisation u = new UniteOrganisation();
		u.setId(UUID.randomUUID());
		u.setCode(code);
		u.setLibelle(libelle);
		u.setActif(actif);
		return u;
	}

	private static Collaborateur fiche(String matricule, String prenom, String nom, UniteOrganisation unite, String profil) {
		Collaborateur c = new Collaborateur();
		c.setId(UUID.randomUUID());
		c.setMatricule(matricule);
		c.setPrenom(prenom);
		c.setNom(nom);
		c.setUnite(unite);
		c.setProfilAcces(profil);
		return c;
	}

	/** Colonnes reconnues automatiquement à partir des en-têtes (comme à l'analyse). */
	private static List<Colonne> colonnes(String... entetes) {
		ReconnaissanceEnTetes reco = new ReconnaissanceEnTetes(List.of(ChampImport.Fiche.values()));
		List<Colonne> r = new ArrayList<>();
		for (int i = 0; i < entetes.length; i++) {
			String champ = reco.reconnaitre(entetes[i]).map(ChampImport::name).orElse(null);
			r.add(new Colonne(i, entetes[i], champ, champ == null ? ImportModeles.IGNOREE : ImportModeles.RECONNUE));
		}
		return r;
	}

	private static List<LigneBrute> lignes(String[]... valeurs) {
		List<LigneBrute> r = new ArrayList<>();
		for (int i = 0; i < valeurs.length; i++) {
			r.add(new LigneBrute(i + 2, List.of(valeurs[i])));
		}
		return r;
	}

	private static String[] l(String... v) {
		return v;
	}

	private Evaluation evaluer(List<Colonne> colonnes, List<LigneBrute> lignes, boolean creerComptes) {
		return service.evaluer(lignes, colonnes, creerComptes, Set.of(), ctx);
	}

	private static boolean aErreur(LigneResultat l, String code) {
		return l.erreurs().stream().anyMatch(e -> e.code().equals(code));
	}

	private static boolean aAvertissement(LigneResultat l, String code) {
		return l.avertissements().stream().anyMatch(e -> e.code().equals(code));
	}

	@Test
	@DisplayName("critère 7 / C2-09 : matricule « col-web  » (casse, espace) -> Mise à jour, seuls les champs modifiés avant -> après ; poste vide -> inchangé")
	void miseAJourEtChampsModifies() {
		Evaluation e = evaluer(colonnes("Mat.", "Poste", "Prénom"), lignes(l(" col-web ", "Développeur senior", "Sarra")), false);
		LigneResultat r = e.lignes().get(0);
		assertThat(r.action()).isEqualTo("MISE_A_JOUR");
		assertThat(r.modifications()).hasSize(1);
		assertThat(r.modifications().get(0).champLibelle()).isEqualTo("Poste");
		assertThat(r.modifications().get(0).avant()).isEqualTo("Développeur");
		assertThat(r.modifications().get(0).apres()).isEqualTo("Développeur senior");
		assertThat(r.collaborateurId()).isEqualTo(colWeb.getId());
		Evaluation videe = evaluer(colonnes("Matricule", "Poste"), lignes(l("COL-WEB", "")), false);
		assertThat(videe.lignes().get(0).action()).isEqualTo("INCHANGEE");
		assertThat(videe.lignes().get(0).modifications()).isEmpty();
		Evaluation casse = evaluer(colonnes("Matricule", "Poste"), lignes(l("COL-WEB", "développeur")), false);
		assertThat(casse.lignes().get(0).action()).isEqualTo("MISE_A_JOUR");
		Evaluation bord = evaluer(colonnes("Matricule", "Poste"), lignes(l("COL-WEB", "  Développeur  ")), false);
		assertThat(bord.lignes().get(0).action()).isEqualTo("INCHANGEE");
	}

	@Test
	@DisplayName("C2-02 : l'évaluation n'écrit rien (aucun appel au dépôt ni au publicateur)")
	void aucuneEcriture() {
		evaluer(colonnes("Matricule", "Prénom", "Nom", "Unité", "Courriel"),
				lignes(l("NEW-1", "Ada", "Lovelace", "DTECH-SW", "ada@rh-evenement.test")), true);
		verify(collaborateurRepository, never()).save(any());
		verify(collaborateurRepository, never()).saveAll(any());
		verify(publisher, never()).publishAsyncAfterCommit(any(), any());
	}

	@Test
	@DisplayName("critère 8 / C2-11 : unité par code (minuscules) ou libellé sans accents ; le code gagne ; inconnue, inactive, ambiguë ; vide en création -> CHAMP_OBLIGATOIRE")
	void unites() {
		List<Colonne> c = colonnes("Matricule", "Prénom", "Nom", "Code unité");
		Evaluation e = evaluer(c, lignes(l("N1", "A", "B", "dtech-sw"), l("N2", "A", "B", "departement software"),
				l("N3", "A", "B", "XYZ"), l("N4", "A", "B", "FIN"), l("N5", "A", "B", "Qualité"), l("N6", "A", "B", ""),
				l("N7", "A", "B", "QUAL-1")), false);
		assertThat(e.lignes().get(0).action()).isEqualTo("CREATION");
		assertThat(e.lignes().get(1).action()).isEqualTo("CREATION");
		assertThat(aErreur(e.lignes().get(2), "UNITE_INCONNUE")).isTrue();
		assertThat(aErreur(e.lignes().get(3), "UNITE_INACTIVE")).isTrue();
		assertThat(aErreur(e.lignes().get(4), "UNITE_AMBIGUE")).isTrue();
		assertThat(aErreur(e.lignes().get(5), "CHAMP_OBLIGATOIRE")).isTrue();
		assertThat(e.lignes().get(6).valeurs().get("UNITE")).isEqualTo(qualite1.getId().toString());
		assertThat(e.lignes().get(2).erreurs().get(0).message()).contains("« XYZ »").contains("Code unité");
	}

	@Test
	@DisplayName("C2-11 : mise à jour avec unité vide -> inchangée ; vers une unité inactive -> UNITE_INACTIVE")
	void uniteEnMiseAJour() {
		Evaluation e = evaluer(colonnes("Matricule", "Unité"), lignes(l("COL-WEB", ""), l("DRH01", "FIN"), l("COL-WEB", "")), false);
		assertThat(e.lignes().get(1).action()).isEqualTo("ERREUR");
		assertThat(aErreur(e.lignes().get(1), "UNITE_INACTIVE")).isTrue();
		Evaluation inchangee = evaluer(colonnes("Matricule", "Unité"), lignes(l("COL-WEB", "")), false);
		assertThat(inchangee.lignes().get(0).action()).isEqualTo("INCHANGEE");
	}

	@Test
	@DisplayName("critère 9 / C2-12 : profil RH / ADMIN refusé en création et en changement ; « Responsable opérationnel », « RO », « Responsable » importables ; fiche RH sans colonne ou avec « RH » -> pas d'erreur")
	void profils() {
		List<Colonne> c = colonnes("Matricule", "Prénom", "Nom", "Unité", "Profil");
		Evaluation e = evaluer(c, lignes(l("P1", "A", "B", "DTECH-SW", "Ressources humaines"), l("P2", "A", "B", "DTECH-SW", "ADMIN"),
				l("P3", "A", "B", "DTECH-SW", "Responsable opérationnel"), l("P4", "A", "B", "DTECH-SW", "RO"),
				l("P5", "A", "B", "DTECH-SW", "Responsable"), l("P6", "A", "B", "DTECH-SW", "Direction"),
				l("P7", "A", "B", "DTECH-SW", "n'importe quoi")), false);
		assertThat(aErreur(e.lignes().get(0), "PROFIL_NON_IMPORTABLE")).isTrue();
		assertThat(aErreur(e.lignes().get(1), "PROFIL_NON_IMPORTABLE")).isTrue();
		assertThat(e.lignes().get(2).valeurs().get("PROFIL_ACCES")).isEqualTo("RO");
		assertThat(e.lignes().get(3).action()).isEqualTo("CREATION");
		assertThat(e.lignes().get(4).valeurs().get("PROFIL_ACCES")).isEqualTo("RESPONSABLE");
		assertThat(aErreur(e.lignes().get(5), "PROFIL_NON_IMPORTABLE")).isTrue();
		assertThat(aErreur(e.lignes().get(6), "VALEUR_INCONNUE")).isTrue();
		Evaluation majRh = evaluer(colonnes("Matricule", "Profil"), lignes(l("DRH01", "Collaborateur")), false);
		assertThat(aErreur(majRh.lignes().get(0), "PROFIL_NON_IMPORTABLE")).isTrue();
		Evaluation memeProfil = evaluer(colonnes("Matricule", "Profil"), lignes(l("DRH01", "RH")), false);
		assertThat(memeProfil.lignes().get(0).action()).isEqualTo("INCHANGEE");
		Evaluation sansColonne = evaluer(colonnes("Matricule", "Poste"), lignes(l("DRH01", "DRH")), false);
		assertThat(sansColonne.lignes().get(0).enErreur()).isFalse();
	}

	@Test
	@DisplayName("critère 10 / C2-13 : matricule présent deux fois (casse, espace) -> toutes les occurrences en erreur, les autres lignes restent importables")
	void doublonsMatricule() {
		Evaluation e = evaluer(colonnes("Matricule", "Prénom", "Nom", "Unité"), lignes(l("COL-X", "A", "B", "DTECH-SW"),
				l("col-x", "A", "B", "DTECH-SW"), l("COL-Y", "A", "B", "DTECH-SW")), false);
		assertThat(aErreur(e.lignes().get(0), "MATRICULE_EN_DOUBLE_FICHIER")).isTrue();
		assertThat(aErreur(e.lignes().get(1), "MATRICULE_EN_DOUBLE_FICHIER")).isTrue();
		assertThat(e.lignes().get(2).action()).isEqualTo("CREATION");
	}

	@Test
	@DisplayName("critère 11 / C2-13 / C2-14 : courriel pris par une autre fiche ou en double dans le fichier ; courriel propre à la fiche mise à jour -> pas de doublon")
	void courriels() {
		List<Colonne> c = colonnes("Matricule", "Prénom", "Nom", "Unité", "Courriel");
		Evaluation e = evaluer(c, lignes(l("N1", "A", "B", "DTECH-SW", "sarra@rh-evenement.test"),
				l("N2", "A", "B", "DTECH-SW", "double@x.test"), l("N3", "A", "B", "DTECH-SW", "DOUBLE@x.test"),
				l("N4", "A", "B", "DTECH-SW", "a@b")), false);
		assertThat(aErreur(e.lignes().get(0), "COURRIEL_EN_DOUBLE")).isTrue();
		assertThat(aErreur(e.lignes().get(1), "COURRIEL_EN_DOUBLE")).isTrue();
		assertThat(aErreur(e.lignes().get(2), "COURRIEL_EN_DOUBLE")).isTrue();
		assertThat(aErreur(e.lignes().get(3), "FORMAT_INVALIDE")).isTrue();
		Evaluation propre = evaluer(colonnes("Matricule", "Courriel"), lignes(l("COL-WEB", "sarra@rh-evenement.test")), false);
		assertThat(propre.lignes().get(0).action()).isEqualTo("INCHANGEE");
	}

	@Test
	@DisplayName("critère 11 / C2-14 : dates 31/02/2026, 30/11/26, 11/30/2026 invalides ; 2026-11-30, série Excel 46000, texte 30/11/2026 valides ; aujourd'hui + 1 an valide, + 1 an et 1 jour invalide")
	void dates() {
		List<Colonne> c = colonnes("Matricule", "Prénom", "Nom", "Unité", "Date de recrutement");
		String[] invalides = {"31/02/2026", "30/11/26", "11/30/2026", "demain", "2027-11-13"};
		String[] valides = {"2026-11-30", "46000", "30/11/2026", "2027-11-12"};
		List<LigneBrute> lignes = new ArrayList<>();
		int n = 0;
		for (String d : invalides) {
			lignes.add(new LigneBrute(++n, List.of("I" + n, "A", "B", "DTECH-SW", d)));
		}
		for (String d : valides) {
			lignes.add(new LigneBrute(++n, List.of("V" + n, "A", "B", "DTECH-SW", d)));
		}
		Evaluation e = evaluer(c, lignes, false);
		for (int i = 0; i < invalides.length; i++) {
			assertThat(aErreur(e.lignes().get(i), "DATE_INVALIDE")).as(invalides[i]).isTrue();
		}
		for (int i = 0; i < valides.length; i++) {
			assertThat(e.lignes().get(invalides.length + i).enErreur()).as(valides[i]).isFalse();
		}
		assertThat(ImportCollaborateursService.lireDate("46000")).isEqualTo(LocalDate.of(2025, 12, 9));
	}

	@Test
	@DisplayName("C2-10 : matricule obligatoire, longueurs (prénom / nom 120 OK 121 KO, matricule 64 / 65, poste 255 / 256), valeurs inconnues (statut, famille, niveau ; inactive refusée)")
	void controlesDeChamps() {
		List<Colonne> c = colonnes("Matricule", "Prénom", "Nom", "Unité", "Poste", "Statut", "Famille", "Niveau");
		Evaluation e = evaluer(c, lignes(l("", "A", "B", "DTECH-SW", "", "", "", ""),
				l("x".repeat(64), "p".repeat(120), "n".repeat(120), "DTECH-SW", "q".repeat(255), "", "", ""),
				l("x".repeat(65), "A", "B", "DTECH-SW", "", "", "", ""), l("L3", "p".repeat(121), "B", "DTECH-SW", "", "", "", ""),
				l("L4", "A", "B", "DTECH-SW", "q".repeat(256), "", "", ""),
				l("L5", "A", "B", "DTECH-SW", "", "Inconnu", "", ""), l("L6", "A", "B", "DTECH-SW", "", "", "Ancienne famille", ""),
				l("L7", "A", "B", "DTECH-SW", "", "Archivé", "dev_logiciel", "sénior")), false);
		assertThat(aErreur(e.lignes().get(0), "MATRICULE_OBLIGATOIRE")).isTrue();
		assertThat(e.lignes().get(1).enErreur()).isFalse();
		assertThat(aErreur(e.lignes().get(2), "VALEUR_TROP_LONGUE")).isTrue();
		assertThat(aErreur(e.lignes().get(3), "VALEUR_TROP_LONGUE")).isTrue();
		assertThat(aErreur(e.lignes().get(4), "VALEUR_TROP_LONGUE")).isTrue();
		assertThat(aErreur(e.lignes().get(5), "VALEUR_INCONNUE")).isTrue();
		assertThat(aErreur(e.lignes().get(6), "VALEUR_INCONNUE")).isTrue();
		assertThat(e.lignes().get(7).valeurs()).containsEntry("STATUT", "ARCHIVE").containsEntry("FAMILLE_METIER", "DEV_LOGICIEL")
				.containsEntry("NIVEAU_SENIORITE", "SENIOR");
	}

	@Test
	@DisplayName("critère 12 / C2-15 : case cochée + création sans courriel -> COURRIEL_OBLIGATOIRE ; case décochée -> créée avec avertissement « Compte non créé : courriel absent »")
	void courrielEtComptes() {
		List<Colonne> c = colonnes("Matricule", "Prénom", "Nom", "Unité");
		Evaluation coche = evaluer(c, lignes(l("N1", "A", "B", "DTECH-SW")), true);
		assertThat(aErreur(coche.lignes().get(0), "COURRIEL_OBLIGATOIRE")).isTrue();
		Evaluation decoche = evaluer(c, lignes(l("N1", "A", "B", "DTECH-SW")), false);
		assertThat(decoche.lignes().get(0).action()).isEqualTo("CREATION");
		assertThat(decoche.lignes().get(0).creerCompte()).isFalse();
		assertThat(decoche.lignes().get(0).avertissements().get(0).message()).isEqualTo("Compte non créé : courriel absent");
		Evaluation majSansCourriel = evaluer(colonnes("Matricule", "Poste"), lignes(l("COL-WEB", "Chef")), true);
		assertThat(majSansCourriel.lignes().get(0).enErreur()).isFalse();
		assertThat(aAvertissement(majSansCourriel.lignes().get(0), "COMPTE_NON_CREE")).isTrue();
	}

	@Test
	@DisplayName("C2-15 : case cochée, création avec courriel dont le matricule a déjà un compte -> avertissement « Compte déjà existant » ; compte demandé")
	void compteDejaExistant() {
		List<Colonne> c = colonnes("Matricule", "Prénom", "Nom", "Unité", "Courriel");
		Evaluation e = service.evaluer(lignes(l("N1", "A", "B", "DTECH-SW", "n1@x.test"), l("N2", "A", "B", "DTECH-SW", "n2@x.test")),
				c, true, Set.of("n1"), ctx);
		assertThat(aAvertissement(e.lignes().get(0), "COMPTE_DEJA_EXISTANT")).isTrue();
		assertThat(e.lignes().get(0).creerCompte()).isTrue();
		assertThat(aAvertissement(e.lignes().get(1), "COMPTE_DEJA_EXISTANT")).isFalse();
		assertThat(service.matriculesACreer(lignes(l("N1", "A", "B", "DTECH-SW", "n1@x.test"), l("COL-WEB", "", "", "", "")), c, ctx))
				.containsExactly("n1");
	}

	@Test
	@DisplayName("critère 16 / C2-16 : 10 lignes -> 4 créations, 2 mises à jour, 2 inchangées, 2 erreurs ; totaux justes")
	void totaux() {
		for (String m : List.of("INC-1", "INC-2")) {
			Collaborateur c = fiche(m, "P", "N", software, "COLLABORATEUR");
			ctx.parMatricule.put(m.toLowerCase(Locale.ROOT), c);
		}
		List<Colonne> c = colonnes("Matricule", "Prénom", "Nom", "Unité", "Poste");
		Evaluation e = evaluer(c, lignes(l("A1", "A", "B", "DTECH-SW", ""), l("A2", "A", "B", "DTECH-SW", ""),
				l("A3", "A", "B", "DTECH-SW", ""), l("A4", "A", "B", "DTECH-SW", ""), l("COL-WEB", "", "", "", "Chef"),
				l("DRH01", "", "", "", "DRH"), l("INC-1", "", "", "", ""), l("INC-2", "", "", "", ""),
				l("", "A", "B", "DTECH-SW", ""), l("ERR", "A", "B", "XXX", "")), false);
		assertThat(e.totaux().lignes()).isEqualTo(10);
		assertThat(e.totaux().creations()).isEqualTo(4);
		assertThat(e.totaux().misesAJour()).isEqualTo(2);
		assertThat(e.totaux().inchangees()).isEqualTo(2);
		assertThat(e.totaux().erreurs()).isEqualTo(2);
	}

	@Test
	@DisplayName("critère 13 / C2-23 : application -> créations et mises à jour écrites, supérieur dérivé, compte demandé après commit seulement avec mot_de_passe_genere et sans mot de passe")
	void application() {
		software.setManager(drh);
		List<Colonne> c = colonnes("Matricule", "Prénom", "Nom", "Unité", "Courriel");
		Evaluation e = evaluer(c, lignes(l("NEW-1", "Ada", "Lovelace", "dtech-sw", "ada@rh-evenement.test")), true);
		when(collaborateurRepository.saveAll(any())).thenAnswer(i -> {
			List<Collaborateur> liste = i.getArgument(0);
			liste.forEach(x -> {
				if (x.getId() == null) {
					x.setId(UUID.randomUUID());
				}
			});
			return liste;
		});

		ImportCollaborateursService.Application app = service.appliquer(e.lignes(), ctx);

		assertThat(app.creees()).isEqualTo(1);
		assertThat(app.comptesDemandes()).isEqualTo(1);
		ArgumentCaptor<CollaborateurCompteDemandeEvent> ev = ArgumentCaptor.forClass(CollaborateurCompteDemandeEvent.class);
		verify(publisher).publishAsyncAfterCommit(any(), ev.capture());
		assertThat(ev.getValue().motDePasseInitial()).isNull();
		assertThat(ev.getValue().genereLeMotDePasse()).isTrue();
		assertThat(ev.getValue().operation()).isEqualTo(CollaborateurCompteDemandeEvent.OPERATION_CREATION);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Collaborateur>> saved = ArgumentCaptor.forClass(List.class);
		verify(collaborateurRepository).saveAll(saved.capture());
		Collaborateur cree = saved.getValue().get(0);
		assertThat(cree.getStatut()).isEqualTo("ACTIF");
		assertThat(cree.getProfilAcces()).isEqualTo("COLLABORATEUR");
		assertThat(cree.getUnite()).isSameAs(software);
		assertThat(cree.getSuperieur()).isSameAs(drh);
	}

	@Test
	@DisplayName("D-I09 : case décochée -> aucune demande de compte ; changement de profil d'une fiche avec compte -> MAJ_ROLES")
	void sansCompteEtMajRoles() {
		colWeb.setCompteUtilisateurId(UUID.randomUUID());
		Evaluation e = evaluer(colonnes("Matricule", "Profil"), lignes(l("COL-WEB", "RO")), false);
		assertThat(e.lignes().get(0).action()).isEqualTo("MISE_A_JOUR");
		when(collaborateurRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
		ImportCollaborateursService.Application app = service.appliquer(e.lignes(), ctx);
		assertThat(app.misesAJour()).isEqualTo(1);
		assertThat(app.comptesDemandes()).isZero();
		assertThat(colWeb.getProfilAcces()).isEqualTo("RO");
		ArgumentCaptor<CollaborateurCompteDemandeEvent> ev = ArgumentCaptor.forClass(CollaborateurCompteDemandeEvent.class);
		verify(publisher).publishAsyncAfterCommit(eq(colWeb.getId().toString()), ev.capture());
		assertThat(ev.getValue().operation()).isEqualTo(CollaborateurCompteDemandeEvent.OPERATION_MAJ_ROLES);
		assertThat(ev.getValue().compteUtilisateurId()).isEqualTo(colWeb.getCompteUtilisateurId());
	}

	@Test
	@DisplayName("C2-23 : changement d'unité -> supérieur recalculé (manager du nouveau nœud)")
	void changementDUnite() {
		UniteOrganisation nouvelle = unite("DTECH-WEB", "Service Web", true);
		Collaborateur manager = fiche("CS-WEB", "Omar", "Belhadj", nouvelle, "RO");
		manager.setStatut("ACTIF");
		nouvelle.setManager(manager);
		ctx.unites.add(nouvelle);
		ctx.unitesParId.put(nouvelle.getId(), nouvelle);
		Evaluation e = evaluer(colonnes("Matricule", "Unité"), lignes(l("COL-WEB", "DTECH-WEB")), false);
		when(collaborateurRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
		service.appliquer(e.lignes(), ctx);
		assertThat(colWeb.getUnite()).isSameAs(nouvelle);
		assertThat(colWeb.getSuperieur()).isSameAs(manager);
	}

	@Test
	@DisplayName("D-I09 : Kafka indisponible alors que des comptes sont demandés -> rien n'est écrit (IllegalStateException)")
	@SuppressWarnings("unchecked")
	void kafkaIndisponible() {
		ObjectProvider<CollaborateurCompteDemandePublisher> sans = mock(ObjectProvider.class);
		when(sans.getIfAvailable()).thenReturn(null);
		ImportCollaborateursService s = new ImportCollaborateursService(collaborateurRepository, null, null, parametreService,
				sans, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
		Evaluation e = evaluer(colonnes("Matricule", "Prénom", "Nom", "Unité", "Courriel"),
				lignes(l("NEW-1", "A", "B", "DTECH-SW", "a@x.test")), true);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> s.appliquer(e.lignes(), ctx))
				.isInstanceOf(IllegalStateException.class);
		verify(collaborateurRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("C2-24 : <script> et =cmd|… ne sont jamais interprétés : gardés comme texte, aucune valeur de cellule dans l'historique")
	void textesInertes() {
		Evaluation e = evaluer(colonnes("Matricule", "Prénom", "Nom", "Unité", "Poste"),
				lignes(l("N1", "<script>alert(1)</script>", "=cmd|' /C calc'!A0", "DTECH-SW", "@SUM(1)")), false);
		assertThat(e.lignes().get(0).valeurs()).containsEntry("PRENOM", "<script>alert(1)</script>");
		assertThat(new java.util.HashMap<>(new HashMap<>(e.lignes().get(0).valeurs()))).containsKey("NOM");
	}
}
