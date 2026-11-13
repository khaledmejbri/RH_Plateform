package com.hr.referentiel.importation;

import com.hr.referentiel.domain.OrigineMouvementSolde;
import com.hr.referentiel.domain.TypeMouvementSolde;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.MouvementSoldeConge;
import com.hr.referentiel.entity.SoldeConge;
import com.hr.referentiel.importation.ImportModeles.Colonne;
import com.hr.referentiel.importation.ImportModeles.Evaluation;
import com.hr.referentiel.importation.ImportModeles.LigneResultat;
import com.hr.referentiel.importation.lecture.Fichier.LigneBrute;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.service.SoldeCongeService;
import com.hr.referentiel.service.SoldeLotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C3-08 à C3-13 : évaluation et application de l'import des soldes (cible absolue, écart recalculé, erreurs par ligne). */
class ImportSoldesServiceTest {

	private ImportSoldesService service;
	private ImportSoldesService.Contexte ctx;
	private SoldeCongeService soldeService;
	private SoldeLotService lotService;
	private RhNotificationPublisher publisher;
	private Collaborateur colWeb;
	private Collaborateur colMob;
	private Collaborateur drh;
	private Collaborateur ancien;
	private Clock horloge = Clock.fixed(Instant.parse("2026-12-10T10:00:00Z"), ZoneOffset.UTC);

	@BeforeEach
	void setUp() {
		soldeService = mock(SoldeCongeService.class);
		lotService = mock(SoldeLotService.class);
		publisher = mock(RhNotificationPublisher.class);
		service = new ImportSoldesService(mock(CollaborateurRepository.class), soldeService, publisher, lotService, horloge);
		ctx = new ImportSoldesService.Contexte();
		colWeb = fiche("COL-WEB", "ACTIF");
		colMob = fiche("COL-MOB", "ACTIF");
		drh = fiche("DRH01", "ACTIF");
		ancien = fiche("ANCIEN", "ARCHIVE");
		for (Collaborateur c : List.of(colWeb, colMob, drh, ancien)) {
			ctx.parMatricule.put(c.getMatricule().toLowerCase(Locale.ROOT), c);
			ctx.acquis.put(c.getId(), new BigDecimal("10.0"));
		}
		ctx.reserves.put(colMob.getId(), new BigDecimal("5.0"));
	}

	private static Collaborateur fiche(String matricule, String statut) {
		Collaborateur c = new Collaborateur();
		c.setId(UUID.randomUUID());
		c.setMatricule(matricule);
		c.setPrenom("P");
		c.setNom(matricule);
		c.setStatut(statut);
		return c;
	}

	private static List<Colonne> colonnes(String... entetes) {
		ReconnaissanceEnTetes reco = new ReconnaissanceEnTetes(List.of(ChampImport.Solde.values()));
		List<Colonne> r = new ArrayList<>();
		for (int i = 0; i < entetes.length; i++) {
			String champ = reco.reconnaitre(entetes[i]).map(ChampImport::name).orElse(null);
			r.add(new Colonne(i, entetes[i], champ, champ == null ? ImportModeles.IGNOREE : ImportModeles.RECONNUE));
		}
		return r;
	}

	private static List<LigneBrute> lignes(String[]... v) {
		List<LigneBrute> r = new ArrayList<>();
		for (int i = 0; i < v.length; i++) {
			r.add(new LigneBrute(i + 2, List.of(v[i])));
		}
		return r;
	}

	private static String[] l(String... v) {
		return v;
	}

	private Evaluation evaluer(List<Colonne> c, List<LigneBrute> lignes, String motifGlobal) {
		return service.evaluer(lignes, c, motifGlobal, drh.getId(), ctx);
	}

	private static boolean aErreur(LigneResultat l, String code) {
		return l.erreurs().stream().anyMatch(e -> e.code().equals(code));
	}

	@Test
	@DisplayName("critère 10 / C3-09 : « 12.5 », « 12,5 », « 12,50 », « 0 », « 365 » acceptés ; « 2,3 » (pas d'arrondi), « 365,5 », « abc », « 1 200 », « -1 », « 400 » -> SOLDE_INVALIDE ; vide -> SOLDE_OBLIGATOIRE")
	void valeurs() {
		List<Colonne> c = colonnes("N° matricule", "Solde au 30/11", "Observation");
		for (String ok : new String[] {"12.5", "12,5", "12,50", "0", "365"}) {
			Evaluation e = evaluer(c, lignes(l("COL-WEB", ok, "")), "Reprise des soldes");
			assertThat(e.lignes().get(0).enErreur()).as(ok).isFalse();
		}
		for (String ko : new String[] {"2,3", "365,5", "abc", "1 200", "-1", "400"}) {
			Evaluation e = evaluer(c, lignes(l("COL-WEB", ko, "")), "Reprise des soldes");
			assertThat(aErreur(e.lignes().get(0), "SOLDE_INVALIDE")).as(ko).isTrue();
		}
		assertThat(aErreur(evaluer(c, lignes(l("COL-WEB", "", "")), "Reprise").lignes().get(0), "SOLDE_OBLIGATOIRE")).isTrue();
	}

	@Test
	@DisplayName("C3-09 : matricule vide, inconnu, en double à la casse près (toutes les occurrences), ligne de l'auteur -> AUTO_AJUSTEMENT_INTERDIT")
	void matricules() {
		List<Colonne> c = colonnes("Matricule", "Solde");
		Evaluation e = evaluer(c, lignes(l("", "5"), l("INCONNU", "5"), l("COL-WEB", "5"), l("col-web ", "6"), l("drh01  ", "5")), "Reprise");
		assertThat(aErreur(e.lignes().get(0), "MATRICULE_OBLIGATOIRE")).isTrue();
		assertThat(aErreur(e.lignes().get(1), "MATRICULE_INCONNU")).isTrue();
		assertThat(aErreur(e.lignes().get(2), "MATRICULE_EN_DOUBLE_FICHIER")).isTrue();
		assertThat(aErreur(e.lignes().get(3), "MATRICULE_EN_DOUBLE_FICHIER")).isTrue();
		assertThat(aErreur(e.lignes().get(4), "AUTO_AJUSTEMENT_INTERDIT")).isTrue();
	}

	@Test
	@DisplayName("C3-10 : motif de ligne de 3 ou 600 caractères -> MOTIF_INVALIDE ; sinon le motif de la ligne remplace le motif global")
	void motifs() {
		List<Colonne> c = colonnes("Matricule", "Solde", "Commentaire");
		Evaluation e = evaluer(c, lignes(l("COL-WEB", "5", "abc"), l("COL-MOB", "5", "x".repeat(600)), l("ANCIEN", "5", "Report 2025")),
				"Reprise globale");
		assertThat(aErreur(e.lignes().get(0), "MOTIF_INVALIDE")).isTrue();
		assertThat(aErreur(e.lignes().get(1), "MOTIF_INVALIDE")).isTrue();
		assertThat(e.lignes().get(2).motif()).isEqualTo("Report 2025");
		Evaluation globale = evaluer(c, lignes(l("COL-WEB", "5", "")), "Reprise globale");
		assertThat(globale.lignes().get(0).motif()).isEqualTo("Reprise globale");
	}

	@Test
	@DisplayName("critère 12 / C3-10 : 6 lignes -> 3 ajustements, 1 inchangée, 2 erreurs ; solde actuel, cible et écart calculés")
	void totauxEtEcarts() {
		ctx.acquis.put(drh.getId(), new BigDecimal("10.0"));
		Collaborateur autre = fiche("COL-X", "ACTIF");
		ctx.parMatricule.put("col-x", autre);
		ctx.acquis.put(autre.getId(), new BigDecimal("0.0"));
		List<Colonne> c = colonnes("Matricule", "Solde");
		Evaluation e = evaluer(c, lignes(l("COL-WEB", "12.5"), l("COL-MOB", "10"), l("COL-X", "3"), l("ANCIEN", "8"),
				l("INCONNU", "1"), l("COL-WEB2", "x")), "Reprise");
		assertThat(e.totaux().ajustements()).isEqualTo(3);
		assertThat(e.totaux().inchangees()).isEqualTo(1);
		assertThat(e.totaux().erreurs()).isEqualTo(2);
		LigneResultat l0 = e.lignes().get(0);
		assertThat(l0.soldeActuel()).isEqualByComparingTo("10");
		assertThat(l0.soldeCible()).isEqualByComparingTo("12.5");
		assertThat(l0.ecart()).isEqualByComparingTo("2.5");
		assertThat(e.lignes().get(1).action()).isEqualTo("INCHANGE");
	}

	@Test
	@DisplayName("critère 13 / C3-13 : cible inférieure au réservé -> ligne importée avec avertissement ; fiche archivée -> importée avec avertissement")
	void avertissementsDeLigne() {
		List<Colonne> c = colonnes("Matricule", "Solde");
		Evaluation e = evaluer(c, lignes(l("COL-MOB", "3"), l("ANCIEN", "8")), "Reprise");
		assertThat(e.lignes().get(0).action()).isEqualTo("AJUSTEMENT");
		assertThat(e.lignes().get(0).avertissements()).anyMatch(a -> a.code().equals("CIBLE_INFERIEURE_RESERVE"));
		assertThat(e.lignes().get(1).avertissements()).anyMatch(a -> a.code().equals("COLLABORATEUR_ARCHIVE"));
		assertThat(e.lignes().get(1).action()).isEqualTo("AJUSTEMENT");
	}

	@Test
	@DisplayName("C3-12 : analyse le 05/12 à Tunis -> avertissement global ACQUISITION_RECENTE ; le 06/12 -> aucun ; 30/11 23:30 UTC = 01/12 Tunis -> avertissement")
	void avertissementDebutDeMois() {
		horloge = Clock.fixed(Instant.parse("2026-12-05T10:00:00Z"), ZoneOffset.UTC);
		service = new ImportSoldesService(mock(CollaborateurRepository.class), soldeService, publisher, lotService, horloge);
		assertThat(service.avertissementsFichier()).extracting(p -> p.code()).containsExactly("ACQUISITION_RECENTE");
		service = new ImportSoldesService(mock(CollaborateurRepository.class), soldeService, publisher, lotService,
				Clock.fixed(Instant.parse("2026-12-06T10:00:00Z"), ZoneOffset.UTC));
		assertThat(service.avertissementsFichier()).isEmpty();
		service = new ImportSoldesService(mock(CollaborateurRepository.class), soldeService, publisher, lotService,
				Clock.fixed(Instant.parse("2026-11-30T23:30:00Z"), ZoneOffset.UTC));
		assertThat(service.avertissementsFichier()).hasSize(1);
		service = new ImportSoldesService(mock(CollaborateurRepository.class), soldeService, publisher, lotService,
				Clock.fixed(Instant.parse("2026-12-05T23:30:00Z"), ZoneOffset.UTC));
		assertThat(service.avertissementsFichier()).isEmpty(); // 06/12 00:30 à Tunis
	}

	@Test
	@DisplayName("critère 12 / C3-11 : application en lot -> écart réel et notification par collaborateur modifié (après commit), ligne devenue égale -> aucun mouvement")
	void application() {
		when(lotService.ajuster(any(), any(), any())).thenAnswer(i -> {
			List<SoldeLotService.Ajustement> demandes = i.getArgument(0);
			List<SoldeLotService.Resultat> r = new ArrayList<>();
			for (SoldeLotService.Ajustement d : demandes) {
				boolean webModifie = d.collaborateurId().equals(colWeb.getId());
				// COL-WEB : un congé pris (11 -> cible 12,5 = +1,5) entre l'analyse et la confirmation ; COL-MOB : devenu égal
				r.add(new SoldeLotService.Resultat(d.collaborateurId(), webModifie ? new BigDecimal("1.5") : null,
						d.cible(), d.motif()));
			}
			return r;
		});
		List<LigneResultat> ajustables = new ArrayList<>();
		ajustables.add(new LigneResultat(2, "COL-WEB", "P", "AJUSTEMENT", List.of(), List.of(), List.of(), new BigDecimal("10.0"),
				new BigDecimal("12.5"), new BigDecimal("2.5"), null, colWeb.getId(), null, new java.util.HashMap<>(), false));
		ajustables.add(new LigneResultat(3, "COL-MOB", "P", "AJUSTEMENT", List.of(), List.of(), List.of(), new BigDecimal("8.0"),
				new BigDecimal("10.0"), new BigDecimal("2.0"), null, colMob.getId(), null, new java.util.HashMap<>(), false));
		UUID importId = UUID.randomUUID();

		ImportSoldesService.Application app = service.appliquer(ajustables, importId, "Reprise des soldes",
				new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi"), ctx);

		assertThat(app.ajustements()).isEqualTo(1);
		assertThat(app.inchanges()).isEqualTo(1);
		assertThat(app.ecartsReels().get("col-web")).isEqualByComparingTo("1.5");
		assertThat(app.ecartsReels()).containsKey("col-mob");
		assertThat(app.ecartsReels().get("col-mob")).isNull();
		verify(publisher).notifierSoldeAjuste(eq(colWeb), eq(new BigDecimal("1.5")), eq(new BigDecimal("12.5")), eq("Reprise des soldes"));
		verify(publisher, never()).notifierSoldeAjuste(eq(colMob), any(), any(), any());
	}
}
