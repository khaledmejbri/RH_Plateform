package com.hr.referentiel.service;

import com.hr.referentiel.champsperso.ChampPersoDtos.ModifierChampPerso;
import com.hr.referentiel.champsperso.ChampsPersoH2Base;
import com.hr.referentiel.champsperso.ValeurTypee;
import com.hr.referentiel.champsperso.ValeursPersoJdbc.Ecriture;
import com.hr.referentiel.dto.CollaborateurResponse;
import com.hr.referentiel.dto.PageReferentielResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** T6 règles 7 et 8 : liste filtrée côté serveur (V5) et export CSV (V6), base H2 réelle. */
class RechercheEtExportCollaborateursTest extends ChampsPersoH2Base {

	@Autowired
	CollaborateurRepository collaborateurRepository;
	@Autowired
	UniteOrganisationRepository uniteRepository;

	private RechercheCollaborateursService recherche;
	private ExportCollaborateursService export;
	private UniteOrganisation direction;
	private UniteOrganisation serviceWeb;
	private UniteOrganisation serviceMobile;
	private String taille;
	private String code;
	private String badge;
	private String pointure;

	@BeforeEach
	void init() {
		ReferentielRhService rh = mock(ReferentielRhService.class);
		when(rh.versReponse(any())).thenAnswer(i -> {
			Collaborateur c = i.getArgument(0);
			CollaborateurResponse r = new CollaborateurResponse();
			r.setIdentifiant(c.getId());
			r.setMatricule(c.getMatricule());
			r.setPrenom(c.getPrenom());
			r.setNom(c.getNom());
			r.setStatut(c.getStatut());
			r.setProfilAcces(c.getProfilAcces());
			r.setPosteLibelle(c.getPosteLibelle());
			return r;
		});
		ParametreService parametres = mock(ParametreService.class);
		when(parametres.libelleOuInconnu(any(), any())).thenAnswer(i -> "Libellé " + i.getArgument(1));
		recherche = new RechercheCollaborateursService(collaborateurRepository, uniteRepository, definitionsLecture, rh);
		export = new ExportCollaborateursService(collaborateurRepository, recherche, rh, parametres, definitionsLecture, valeursJdbc, clock);
		direction = unite("DIR-T", "Direction Technique", null);
		serviceWeb = unite("SERV-WEB", "Service web", direction);
		serviceMobile = unite("SERV-MOB", "Service mobile", direction);
		taille = creer(COL, "Taille de tenue", "LISTE", SANS_REGLE, "S", "M", "L");
		code = creer(COL, "Code vestiaire", "TEXTE", regles(100, null, null, null));
		badge = creer(COL, "Date de badge", "DATE", SANS_REGLE);
		pointure = creer(COL, "Pointure", "NOMBRE", regles(null, 0, null, null));
	}

	private UniteOrganisation unite(String code, String libelle, UniteOrganisation parent) {
		UniteOrganisation u = new UniteOrganisation();
		u.setCode(code);
		u.setLibelle(libelle);
		u.setActif(true);
		u.setParent(parent);
		return uniteRepository.saveAndFlush(u);
	}

	private Collaborateur fiche(String matricule, String prenom, String nom, UniteOrganisation unite) {
		Collaborateur c = new Collaborateur();
		c.setMatricule(matricule);
		c.setPrenom(prenom);
		c.setNom(nom);
		c.setStatut("ACTIF");
		c.setProfilAcces("COLLABORATEUR");
		c.setUnite(unite);
		return collaborateurRepository.saveAndFlush(c);
	}

	private void valeur(Collaborateur c, String champ, ValeurTypee v) {
		valeursJdbc.inserer(List.of(new Ecriture(c.getId(), champ, v)), clock.instant());
	}

	private Collaborateur sarra;
	private Collaborateur omar;
	private Collaborateur mariem;

	private void jeu() {
		sarra = fiche("COL-WEB", "Sarra", "Khemiri", serviceWeb);
		omar = fiche("CS-WEB", "Omar", "Belhadj", serviceWeb);
		mariem = fiche("COL-MOB", "Mariem", "Guesmi", serviceMobile);
		fiche("COL-INF", "Éric", "Ben Salah", serviceMobile);
		valeur(sarra, taille, ValeurTypee.liste("M"));
		valeur(sarra, code, ValeurTypee.texte("B-12"));
		valeur(sarra, badge, ValeurTypee.date(LocalDate.of(2026, 11, 15)));
		valeur(sarra, pointure, ValeurTypee.nombre(new BigDecimal("42")));
		valeur(omar, taille, ValeurTypee.liste("L"));
		valeur(omar, badge, ValeurTypee.date(LocalDate.of(2026, 10, 2)));
		valeur(mariem, taille, ValeurTypee.liste("S"));
		valeur(mariem, pointure, ValeurTypee.nombre(new BigDecimal("38")));
	}

	private long total(String... kv) {
		Map<String, String> p = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			p.put(kv[i], kv[i + 1]);
		}
		return recherche.lister(FiltreCollaborateurs.lire(p), 0, 20).getTotalElements();
	}

	@Test
	@DisplayName("T6-12 : liste -> valeurs M et L (OU) ; liste + unité ; texte « b-1 » trouve « B-12 » ; date du/au ; nombre min/max ; Vide ; Renseigné")
	void criteresParType() {
		jeu();
		assertThat(total()).isEqualTo(4);
		assertThat(total("cp." + taille + ".valeurs", "M,L")).isEqualTo(2);
		assertThat(total("cp." + taille + ".valeurs", "M,L", "service", serviceWeb.getId().toString())).isEqualTo(2);
		assertThat(total("cp." + taille + ".valeurs", "S", "service", serviceWeb.getId().toString())).isZero();
		assertThat(total("cp." + code + ".contient", "b-1")).isEqualTo(1);
		assertThat(total("cp." + badge + ".du", "2026-11-01", "cp." + badge + ".au", "2026-11-30")).isEqualTo(1);
		assertThat(total("cp." + pointure + ".min", "38", "cp." + pointure + ".max", "42")).isEqualTo(2);
		assertThat(total("cp." + pointure + ".min", "40")).isEqualTo(1);
		assertThat(total("cp." + taille + ".etat", "VIDE")).isEqualTo(1);
		assertThat(total("cp." + taille + ".etat", "RENSEIGNE")).isEqualTo(3);
	}

	@Test
	@DisplayName("T6-12 : recherche q sans accents ni casse, multi-mots, unité en cascade (direction), statut et profil")
	void filtresDeLaPage() {
		jeu();
		assertThat(total("q", "eric ben")).isEqualTo(1); // « Éric Ben Salah »
		assertThat(total("q", "KHEMIRI sarra")).isEqualTo(1);
		assertThat(total("q", "service web")).isEqualTo(2); // libellé d'unité
		assertThat(total("direction", direction.getId().toString())).isEqualTo(4);
		assertThat(total("direction", direction.getId().toString(), "service", serviceMobile.getId().toString())).isEqualTo(2);
		assertThat(total("service", serviceWeb.getId().toString(), "departement", serviceMobile.getId().toString())).isZero();
		assertThat(total("statut", "actif")).isEqualTo(4);
		assertThat(total("profil", "RH")).isZero();
		assertThat(total("metier", "A_COMPLETER")).isEqualTo(4); // actives sans famille ni niveau
		assertThat(total("unite_identifiant", serviceWeb.getId().toString())).isEqualTo(2);
	}

	@Test
	@DisplayName("T6-12 : total exact et pagination stable sur plusieurs pages (tri nom, prénom, identifiant)")
	void paginationExacte() {
		for (int i = 0; i < 25; i++) {
			fiche("PAG-" + i, "Prenom", "Nom", serviceWeb); // mêmes noms : l'identifiant départage
		}
		PageReferentielResponse<CollaborateurResponse> p0 = recherche.lister(FiltreCollaborateurs.lire(Map.of()), 0, 10);
		PageReferentielResponse<CollaborateurResponse> p2 = recherche.lister(FiltreCollaborateurs.lire(Map.of()), 2, 10);
		PageReferentielResponse<CollaborateurResponse> p1 = recherche.lister(FiltreCollaborateurs.lire(Map.of()), 1, 10);
		assertThat(p0.getTotalElements()).isEqualTo(25);
		assertThat(p0.getTotalPages()).isEqualTo(3);
		assertThat(p2.getContenu()).hasSize(5);
		List<java.util.UUID> tous = new java.util.ArrayList<>();
		for (var p : List.of(p0, p1, p2)) {
			p.getContenu().forEach(c -> tous.add(c.getIdentifiant()));
		}
		assertThat(tous).doesNotHaveDuplicates().hasSize(25);
	}

	@Test
	@DisplayName("T6-7 règle 7 : champ inactif ou inconnu, critère incompatible, valeur illisible, état combiné, trop de mots -> 400 FILTRE_INVALIDE")
	void filtreInvalide() {
		jeu();
		definitions.desactiver(COL, pointure, version(COL), DRH01);
		for (String[] kv : new String[][] {
				{"cp." + pointure + ".min", "30"}, {"cp.PERSO_INCONNU.etat", "VIDE"}, {"cp." + taille + ".contient", "x"},
				{"cp." + taille + ".valeurs", "XXL"}, {"cp." + badge + ".du", "15/11/2026"}, {"cp." + code + ".etat", "PEUT-ETRE"},
				{"q", "a b c d e f g h i j k"}, {"direction", "pas-un-uuid"}, {"metier", "AUTRE"}}) {
			Throwable t = echec(() -> total(kv));
			erreur(t, 400, "FILTRE_INVALIDE");
		}
		erreur(echec(() -> total("cp." + code + ".etat", "VIDE", "cp." + code + ".contient", "x")), 400, "FILTRE_INVALIDE");
	}

	@Test
	@DisplayName("T6-13 : export CSV : BOM, « ; », colonnes fixes puis champs actifs dans l'ordre réglé, formats français, formule neutralisée, aucun code ni UUID")
	void exportCsv() {
		jeu();
		valeur(omar, code, ValeurTypee.texte("=1+1"));
		definitions.modifier(COL, pointure, new ModifierChampPerso(version(COL), "Pointure", "Finalité de test", "NOMBRE",
				regles(null, 0, null, null), null, false), DRH01);
		byte[] octets = export.exporter(FiltreCollaborateurs.lire(Map.of("service", serviceWeb.getId().toString())), "DRH01");
		String csv = new String(octets, StandardCharsets.UTF_8);
		assertThat(csv).startsWith("﻿Matricule;Prénom;Nom;Courriel;Unité;Poste;Fonction;Qualité;Affectation;Date de recrutement;"
				+ "Statut;Profil d'accès;Famille métier;Niveau de séniorité;Taille de tenue;Code vestiaire;Date de badge;Pointure\r\n");
		assertThat(csv.split("\r\n")).hasSize(3); // en-tête + 2 fiches du service web
		assertThat(csv).contains(";M;B-12;15/11/2026;42").contains("'=1+1").contains("Libellé ACTIF").contains("Libellé COLLABORATEUR");
		assertThat(csv).doesNotContain("PERSO_").doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-");
		assertThat(export.nomFichier()).isEqualTo("collaborateurs_20261113.csv");
	}

	@Test
	@DisplayName("T6-9 règle 9 : un champ désactivé n'a plus de colonne dans l'export ni de filtre ; réactivé, la valeur revient")
	void champDesactiveAbsentDeLExport() {
		jeu();
		definitions.desactiver(COL, pointure, version(COL), DRH01);
		String csv = new String(export.exporter(FiltreCollaborateurs.lire(Map.of()), "DRH01"), StandardCharsets.UTF_8);
		assertThat(csv).doesNotContain("Pointure").contains("Code vestiaire");
		definitions.reactiver(COL, pointure, version(COL), DRH01);
		assertThat(new String(export.exporter(FiltreCollaborateurs.lire(Map.of()), "DRH01"), StandardCharsets.UTF_8)).contains("Pointure")
				.contains(";42");
	}
}
