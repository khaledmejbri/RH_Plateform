package com.hr.referentiel.importation;

import com.hr.referentiel.champsperso.ReglesPerso;
import com.hr.referentiel.champsperso.TypeValeurPerso;
import com.hr.referentiel.champsperso.ValeursPersoLot;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.importation.ImportModeles.Colonne;
import com.hr.referentiel.importation.ImportModeles.Evaluation;
import com.hr.referentiel.importation.ImportModeles.LigneResultat;
import com.hr.referentiel.importation.lecture.Fichier.LigneBrute;
import com.hr.referentiel.importation.parametrage.ExigenceChamp;
import com.hr.referentiel.importation.parametrage.ParametrageImport;
import com.hr.referentiel.importation.parametrage.ParametrageImport.ChampRegle;
import com.hr.referentiel.importation.parametrage.ParametrageImport.DefinitionPerso;
import com.hr.referentiel.importation.parametrage.TypeImportParametrable;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.service.SoldeCongeService;
import com.hr.referentiel.service.SoldeLotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T8 (SCRUM-102) : colonnes personnalisées de l'import des soldes (analyse, ligne « Inchangé », application en lot). */
class ImportSoldesChampsPersoTest {

	private static final String REFERENCE = "PERSO_REFERENCE_DECISION";

	private ImportSoldesService service;
	private ImportSoldesService.Contexte ctx;
	private SoldeLotService lotService;
	private ValeursPersoLot valeursLot;
	private Collaborateur colWeb;
	private Collaborateur colMob;

	@BeforeEach
	void setUp() {
		lotService = mock(SoldeLotService.class);
		valeursLot = mock(ValeursPersoLot.class);
		service = new ImportSoldesService(mock(CollaborateurRepository.class), mock(SoldeCongeService.class),
				mock(RhNotificationPublisher.class), lotService, Clock.fixed(Instant.parse("2026-12-10T10:00:00Z"), ZoneOffset.UTC), valeursLot);
		ctx = new ImportSoldesService.Contexte();
		colWeb = fiche("COL-WEB");
		colMob = fiche("COL-MOB");
		for (Collaborateur c : List.of(colWeb, colMob)) {
			ctx.parMatricule.put(c.getMatricule().toLowerCase(Locale.ROOT), c);
			ctx.acquis.put(c.getId(), new BigDecimal("10.0"));
		}
	}

	private static Collaborateur fiche(String matricule) {
		Collaborateur c = new Collaborateur();
		c.setId(UUID.randomUUID());
		c.setMatricule(matricule);
		c.setPrenom("P");
		c.setNom(matricule);
		c.setStatut("ACTIF");
		return c;
	}

	private ParametrageImport snapshot(ExigenceChamp exigence) {
		ParametrageImport defaut = ParametrageImport.parDefaut(TypeImportParametrable.SOLDES);
		List<ChampRegle> champs = new ArrayList<>(defaut.champs());
		champs.add(new ChampRegle(REFERENCE, "Reference de decision", true, exigence, champs.size(), List.of(), true));
		return new ParametrageImport("SOLDES", 3, champs, List.of(new DefinitionPerso(REFERENCE, "Reference de decision",
				TypeValeurPerso.TEXTE, new ReglesPerso(10, null, null, null, null, null), List.of())));
	}

	private List<Colonne> colonnes(ParametrageImport p, String... entetes) {
		ReconnaissanceEnTetes reco = new ReconnaissanceEnTetes(p);
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

	private Evaluation evaluer(ParametrageImport p, List<Colonne> c, List<LigneBrute> l) {
		return service.evaluer(l, c, "Reprise des soldes", null, ctx, p);
	}

	@Test
	@DisplayName("T8-1 : la colonne personnalisée est reconnue, sa valeur canonique accompagne la ligne d'ajustement")
	void valeurSurLaLigneDAjustement() {
		ParametrageImport p = snapshot(ExigenceChamp.FACULTATIF);
		LigneResultat r = evaluer(p, colonnes(p, "Matricule", "Solde", "Reference de decision"), lignes(new String[] {"COL-WEB", "12", "DG-2026-15"}))
				.lignes().get(0);
		assertThat(r.action()).isEqualTo("AJUSTEMENT");
		assertThat(r.valeurs()).containsEntry(REFERENCE, "DG-2026-15");
	}

	@Test
	@DisplayName("T8-2 : valeur trop longue -> VALEUR_TROP_LONGUE (colonne = en-tête figé) ; exigence « toujours obligatoire » -> CHAMP_OBLIGATOIRE")
	void erreursEtExigence() {
		ParametrageImport p = snapshot(ExigenceChamp.TOUJOURS);
		List<Colonne> c = colonnes(p, "Matricule", "Solde", "Reference de decision");
		LigneResultat longue = evaluer(p, c, lignes(new String[] {"COL-WEB", "12", "REFERENCE-TROP-LONGUE"})).lignes().get(0);
		assertThat(longue.erreurs()).extracting(e -> e.code()).containsExactly("VALEUR_TROP_LONGUE");
		assertThat(longue.erreurs().get(0).colonne()).isEqualTo("Reference de decision");
		LigneResultat vide = evaluer(p, c, lignes(new String[] {"COL-WEB", "12", ""})).lignes().get(0);
		assertThat(vide.erreurs()).extracting(e -> e.code()).containsExactly("CHAMP_OBLIGATOIRE");
	}

	@Test
	@DisplayName("T8-3 : ligne devenue « Inchangé » avec des valeurs -> avertissement VALEURS_PERSO_NON_ENREGISTREES")
	void ligneInchangeeAvecValeurs() {
		ParametrageImport p = snapshot(ExigenceChamp.FACULTATIF);
		LigneResultat r = evaluer(p, colonnes(p, "Matricule", "Solde", "Reference de decision"), lignes(new String[] {"COL-WEB", "10", "DG-1"}))
				.lignes().get(0);
		assertThat(r.action()).isEqualTo("INCHANGE");
		assertThat(r.avertissements()).extracting(a -> a.code()).containsExactly("VALEURS_PERSO_NON_ENREGISTREES");
	}

	@Test
	@DisplayName("T8-4 : à l'application, les valeurs sont écrites sur les mouvements créés ; une ligne devenue égale à la confirmation donne un avertissement sans valeur écrite")
	void applicationSurLesMouvements() {
		ParametrageImport p = snapshot(ExigenceChamp.FACULTATIF);
		Evaluation e = evaluer(p, colonnes(p, "Matricule", "Solde", "Reference de decision"),
				lignes(new String[] {"COL-WEB", "12", "DG-1"}, new String[] {"COL-MOB", "13", "DG-2"}));
		UUID mouvement = UUID.randomUUID();
		when(lotService.ajuster(any(), any(), any())).thenAnswer(i -> {
			List<SoldeLotService.Ajustement> demandes = i.getArgument(0);
			List<SoldeLotService.Resultat> r = new ArrayList<>();
			for (SoldeLotService.Ajustement d : demandes) {
				boolean web = d.collaborateurId().equals(colWeb.getId());
				r.add(new SoldeLotService.Resultat(d.collaborateurId(), web ? new BigDecimal("2.0") : null, d.cible(), d.motif(),
						web ? mouvement : null)); // COL-MOB est devenu égal entre l'analyse et la confirmation
			}
			return r;
		});
		when(valeursLot.ecrireMouvements(any(), any())).thenReturn(List.of());
		ImportSoldesService.Application app = service.appliquer(e.lignes(), UUID.randomUUID(), "Reprise des soldes",
				new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi"), ctx, p);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ValeursPersoLot.Demande>> demandes = ArgumentCaptor.forClass(List.class);
		verify(valeursLot).ecrireMouvements(demandes.capture(), any());
		assertThat(demandes.getValue()).singleElement().satisfies(d -> {
			assertThat(d.cible()).isEqualTo(mouvement);
			assertThat(d.canoniques()).isEqualTo(Map.of(REFERENCE, "DG-1"));
		});
		assertThat(app.avertissements()).singleElement().asString().contains("Ligne 3").contains("informations complémentaires non enregistrées");
	}
}
