package com.hr.referentiel.importation;

import com.hr.referentiel.domain.NatureParametre;
import com.hr.referentiel.dto.PageReferentielResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.importation.ImportDtos.Analyse;
import com.hr.referentiel.importation.ImportDtos.ColonneChoix;
import com.hr.referentiel.importation.ImportDtos.CompteRendu;
import com.hr.referentiel.importation.ImportDtos.ConfirmationRequest;
import com.hr.referentiel.importation.ImportDtos.CorrespondanceRequest;
import com.hr.referentiel.importation.ImportDtos.LigneAnalyse;
import com.hr.referentiel.importation.lecture.Fichier;
import com.hr.referentiel.importation.lecture.LectureFichier;
import com.hr.referentiel.kafka.CollaborateurCompteDemandePublisher;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.service.AuteurParametreResolver;
import com.hr.referentiel.service.CollaborateurConnecteService;
import com.hr.referentiel.service.ParametreService;
import com.hr.referentiel.service.ParametresCache;
import com.hr.referentiel.service.ParametresCache.ParametreVue;
import com.hr.referentiel.service.SoldeCongeService;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Parcours complet d'un import sur base H2 (tables d'import réelles) : analyse sans écriture, correspondance corrigée sans
 * renvoi du fichier, confirmation revalidée, expiration, verrou unique, atomicité, visibilité par auteur, purge,
 * historique, fichier des erreurs.
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:imports;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ImportServiceTest.Config.class)
class ImportServiceTest {

	/** Horloge réglable. */
	static final class HorlogeMutable extends Clock {
		Instant maintenant = Instant.parse("2026-12-10T10:00:00Z");

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return maintenant;
		}
	}

	@Configuration
	@EnableJpaRepositories(basePackageClasses = ImportAnalyseRepository.class, excludeFilters =
	@ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.hr\\.referentiel\\.(?!importation\\.Import).*"))
	static class Config {
		@Bean
		PersistenceManagedTypes managedTypes() {
			return PersistenceManagedTypes.of(ImportAnalyse.class.getName(), ImportContenu.class.getName(), ImportLigne.class.getName());
		}
	}

	@Autowired
	ImportAnalyseRepository analyseRepository;
	@Autowired
	ImportContenuRepository contenuRepository;
	@Autowired
	ImportLigneRepository ligneRepository;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	PlatformTransactionManager tm;

	private HorlogeMutable horloge;
	private CollaborateurRepository collaborateurRepository;
	private CollaborateurCompteDemandePublisher publisher;
	private IdentiteInterneClient identite;
	private AuteurParametreResolver auteurResolver;
	private CollaborateurConnecteService connecte;
	private SoldeCongeService soldeService;
	private com.hr.referentiel.service.SoldeLotService lotService;
	private ImportService service;
	private ImportCollaborateursService collaborateursService;
	private ImportSoldesService soldesService;
	private List<Collaborateur> fiches;
	private Jwt jwtDrh;
	private Jwt jwtRh02;
	private final UUID compteDrh = UUID.randomUUID();
	private final UUID compteRh02 = UUID.randomUUID();
	private UniteOrganisation software;
	private Collaborateur colWeb;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void init() {
		ligneRepository.deleteAll();
		contenuRepository.deleteAll();
		analyseRepository.deleteAll();
		horloge = new HorlogeMutable();
		collaborateurRepository = mock(CollaborateurRepository.class);
		publisher = mock(CollaborateurCompteDemandePublisher.class);
		identite = mock(IdentiteInterneClient.class);
		auteurResolver = mock(AuteurParametreResolver.class);
		connecte = mock(CollaborateurConnecteService.class);
		soldeService = mock(SoldeCongeService.class);
		lotService = mock(com.hr.referentiel.service.SoldeLotService.class);
		UniteOrganisationRepository uniteRepository = mock(UniteOrganisationRepository.class);
		software = new UniteOrganisation();
		software.setId(UUID.randomUUID());
		software.setCode("DTECH-SW");
		software.setLibelle("Département Software");
		software.setActif(true);
		when(uniteRepository.findAllWithParentAndManager()).thenAnswer(i -> new ArrayList<>(List.of(software)));
		colWeb = new Collaborateur();
		colWeb.setId(UUID.randomUUID());
		colWeb.setMatricule("COL-WEB");
		colWeb.setPrenom("Sarra");
		colWeb.setNom("Khemiri");
		colWeb.setUnite(software);
		colWeb.setStatut("ACTIF");
		colWeb.setProfilAcces("COLLABORATEUR");
		colWeb.setPosteLibelle("Développeur");
		colWeb.setModifieLe(Instant.parse("2026-11-01T09:00:00Z"));
		fiches = new ArrayList<>(List.of(colWeb));
		when(collaborateurRepository.findAllWithUnite()).thenAnswer(i -> new ArrayList<>(fiches));
		when(collaborateurRepository.saveAll(any())).thenAnswer(i -> {
			List<Collaborateur> liste = i.getArgument(0);
			liste.forEach(c -> {
				if (c.getId() == null) {
					c.setId(UUID.randomUUID());
				}
				if (!fiches.contains(c)) {
					fiches.add(c);
				}
			});
			return liste;
		});
		ParametresCache cache = mock(ParametresCache.class);
		ParametresCache.Instantane ref = mock(ParametresCache.Instantane.class);
		when(ref.liste(any())).thenReturn(List.of());
		when(ref.liste("STATUT_COLLABORATEUR")).thenReturn(List.of(new ParametreVue("STATUT_COLLABORATEUR", "ACTIF",
				NatureParametre.COMPORTEMENT, "Actif", null, null, 1, true, true, null, null, null)));
		when(cache.instantane()).thenReturn(ref);
		ObjectProvider<CollaborateurCompteDemandePublisher> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(publisher);
		collaborateursService = new ImportCollaborateursService(collaborateurRepository, uniteRepository,
				cache, mock(ParametreService.class), provider, horloge);
		soldesService = new ImportSoldesService(collaborateurRepository, soldeService,
				mock(RhNotificationPublisher.class), lotService, horloge);
		jwtDrh = Jwt.withTokenValue("drh").header("alg", "none").subject("DRH01").claim("k", "drh").build();
		jwtRh02 = Jwt.withTokenValue("rh02").header("alg", "none").subject("RH02").claim("k", "rh02").build();
		when(auteurResolver.depuis(jwtDrh)).thenReturn(new AuteurParametre(compteDrh, "Amel Trabelsi"));
		when(auteurResolver.depuis(jwtRh02)).thenReturn(new AuteurParametre(compteRh02, "Rima Aloui"));
		when(connecte.resoudre(any())).thenReturn(Optional.empty());
		service = new ImportService(analyseRepository, contenuRepository, ligneRepository, collaborateursService,
				soldesService, identite, new ImportVerrou(jdbc), auteurResolver, connecte, JsonMapper.builder().build(), horloge, tm, 30);
	}

	private static byte[] csv(String... lignes) {
		return String.join("\n", lignes).getBytes(StandardCharsets.UTF_8);
	}

	private Analyse analyser(byte[] contenu, boolean creerComptes) {
		return service.analyserCollaborateurs(new MockMultipartFile("fichier", "agents.csv", "text/csv", contenu), creerComptes, jwtDrh);
	}

	private CompteRendu confirmer(UUID id, Jwt jwt) {
		return new TransactionTemplate(tm).execute(s -> service.confirmer(id, null, jwt));
	}

	private static void code(Throwable t, String code, int statut) {
		assertThat(t).isInstanceOf(ReferentielMetierException.class);
		assertThat(((ReferentielMetierException) t).getCode()).isEqualTo(code);
		assertThat(((ReferentielMetierException) t).getHttpStatus()).isEqualTo(statut);
	}

	private static final String ENTETE = "Mat.;Prénom;NOM;E-mail;Code unité";
	private static final String BONNE_LIGNE_1 = "NEW-1;Ada;Lovelace;ada@rh-evenement.test;DTECH-SW";
	private static final String BONNE_LIGNE_2 = "NEW-2;Alan;Turing;alan@rh-evenement.test;DTECH-SW";

	// ─── Analyse ───────────────────────────────────────────────────────────────

	@Test
	@DisplayName("critère 1 / C2-02 : analyse 3 lignes, 5 colonnes reconnues dans le désordre, échéance à 30 min, aucune écriture métier")
	void analyseSansEcriture() {
		Analyse a = analyser(csv("E-mail;Code unité;NOM;Prénom;Mat.", "ada@rh-evenement.test;DTECH-SW;Lovelace;Ada;NEW-1",
				"alan@rh-evenement.test;DTECH-SW;Turing;Alan;NEW-2", "grace@rh-evenement.test;DTECH-SW;Hopper;Grace;NEW-3"), false);
		assertThat(a.colonnes()).extracting(ImportDtos.ColonneVue::etat).containsOnly("RECONNUE");
		assertThat(a.colonnes()).extracting(ImportDtos.ColonneVue::champ)
				.containsExactly("COURRIEL", "UNITE", "NOM", "PRENOM", "MATRICULE");
		assertThat(a.totaux().lignes()).isEqualTo(3);
		assertThat(a.totaux().creations()).isEqualTo(3);
		assertThat(a.expireLe()).isEqualTo(horloge.maintenant.plus(Duration.ofMinutes(30)));
		assertThat(a.confirmable()).isTrue();
		assertThat(a.statut()).isEqualTo("ANALYSEE");
		assertThat(a.champsDisponibles()).hasSize(14);
		assertThat(a.lignes().getContenu()).hasSize(3);
		verify(collaborateurRepository, never()).saveAll(any());
		verify(publisher, never()).publishAsyncAfterCommit(any(), any());
		assertThat(ligneRepository.count()).isZero();
		assertThat(contenuRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("critère 3 / C2-04 : colonne « Téléphone » signalée « colonne ignorée », analyse poursuivie, hors du total des avertissements")
	void colonneIgnoree() {
		Analyse a = analyser(csv(ENTETE + ";Téléphone", BONNE_LIGNE_1 + ";55 123 456"), false);
		assertThat(a.colonnesIgnorees()).containsExactly("Téléphone");
		assertThat(a.totaux().avertissements()).isZero();
		assertThat(a.colonnes().get(5).etat()).isEqualTo("IGNOREE");
	}

	@Test
	@DisplayName("critère 4 / C2-05 : deux colonnes Email et Courriel -> COLONNE_EN_DOUBLE, non confirmable ; correspondance corrigée (Ignorer) sans renvoyer le fichier")
	void colonneEnDouble() {
		Analyse a = analyser(csv("Mat.;Prénom;NOM;Email;Courriel;Code unité", "NEW-1;Ada;Lovelace;a@x.test;b@x.test;DTECH-SW"), false);
		assertThat(a.erreursFichier()).extracting(ImportDtos.ProblemeVue::code).contains("COLONNE_EN_DOUBLE");
		assertThat(a.erreursFichier().get(0).message()).contains("« Email »").contains("« Courriel »");
		assertThat(a.confirmable()).isFalse();
		assertThat(a.colonnes()).filteredOn(c -> "EN_DOUBLE".equals(c.etat())).hasSize(2);
		assertThatThrownBy(() -> confirmer(a.analyseId(), jwtDrh)).satisfies(e -> code(e, "ERREUR_DE_FICHIER", 422));

		Analyse corrigee = service.corriger(a.analyseId(), new CorrespondanceRequest(null, List.of(new ColonneChoix(0, "MATRICULE"),
				new ColonneChoix(1, "PRENOM"), new ColonneChoix(2, "NOM"), new ColonneChoix(3, "COURRIEL"), new ColonneChoix(5, "UNITE")),
				null, null), jwtDrh);
		assertThat(corrigee.analyseId()).isEqualTo(a.analyseId());
		assertThat(corrigee.erreursFichier()).isEmpty();
		assertThat(corrigee.confirmable()).isTrue();
		assertThat(corrigee.colonnes().get(4).champ()).isNull();
		assertThat(corrigee.totaux().creations()).isEqualTo(1);
	}

	@Test
	@DisplayName("C2-05 : correspondance qui retire la colonne matricule -> COLONNE_MATRICULE_ABSENTE, confirmation -> 422 ERREUR_DE_FICHIER")
	void matriculeAbsent() {
		Analyse a = analyser(csv(ENTETE, BONNE_LIGNE_1), false);
		Analyse sans = service.corriger(a.analyseId(), new CorrespondanceRequest(null, List.of(new ColonneChoix(1, "PRENOM"),
				new ColonneChoix(2, "NOM"), new ColonneChoix(4, "UNITE")), null, null), jwtDrh);
		assertThat(sans.erreursFichier()).extracting(ImportDtos.ProblemeVue::code).contains("COLONNE_MATRICULE_ABSENTE");
		assertThatThrownBy(() -> confirmer(a.analyseId(), jwtDrh)).satisfies(e -> code(e, "ERREUR_DE_FICHIER", 422));
	}

	@Test
	@DisplayName("critère 5 / C2-06 : aucune ligne d'en-tête reconnue (ou une seule colonne reconnue) -> 422 EN_TETES_NON_RECONNUS ; en-tête à la ligne 3 et 10 OK, ligne 11 KO")
	void entetes() {
		assertThatThrownBy(() -> analyser(csv("a;b;c", "1;2;3"), false)).satisfies(e -> code(e, "EN_TETES_NON_RECONNUS", 422));
		assertThatThrownBy(() -> analyser(csv("Matricule;b;c", "1;2;3"), false)).satisfies(e -> code(e, "EN_TETES_NON_RECONNUS", 422));
		String[] trois = {"Titre du rapport", "Édité le 12/11/2026", ENTETE, BONNE_LIGNE_1};
		Analyse a3 = analyser(csv(trois), false);
		assertThat(a3.ligneEnTete()).isEqualTo(3);
		assertThat(a3.lignes().getContenu().get(0).numero()).isEqualTo(4);
		List<String> dix = new ArrayList<>(java.util.Collections.nCopies(9, "bruit;x"));
		dix.add(ENTETE);
		dix.add(BONNE_LIGNE_1);
		assertThat(analyser(csv(dix.toArray(new String[0])), false).ligneEnTete()).isEqualTo(10);
		List<String> onze = new ArrayList<>(java.util.Collections.nCopies(10, "bruit;x"));
		onze.add(ENTETE);
		onze.add(BONNE_LIGNE_1);
		assertThatThrownBy(() -> analyser(csv(onze.toArray(new String[0])), false))
				.satisfies(e -> code(e, "EN_TETES_NON_RECONNUS", 422));
	}

	@Test
	@DisplayName("critère 6 / C2-07 : 5 001 lignes de données -> 422 TROP_DE_LIGNES ; 5 000 acceptées ; fichier de 11 Mo -> 413 ; fichier vide -> 400")
	void limites() {
		List<String> lignes = new ArrayList<>();
		lignes.add(ENTETE);
		for (int i = 0; i < 5_000; i++) {
			lignes.add("N" + i + ";A;B;n" + i + "@x.test;DTECH-SW");
		}
		assertThat(analyser(csv(lignes.toArray(new String[0])), false).totaux().lignes()).isEqualTo(5_000);
		lignes.add("N5000;A;B;n5000@x.test;DTECH-SW");
		assertThatThrownBy(() -> analyser(csv(lignes.toArray(new String[0])), false)).satisfies(e -> code(e, "TROP_DE_LIGNES", 422));
		assertThatThrownBy(() -> service.analyserCollaborateurs(new MockMultipartFile("fichier", "g.csv", "text/csv",
				new byte[10_485_761]), false, jwtDrh)).satisfies(e -> code(e, "FICHIER_TROP_VOLUMINEUX", 413));
		assertThatThrownBy(() -> service.analyserCollaborateurs(new MockMultipartFile("fichier", "g.csv", "text/csv", new byte[0]),
				false, jwtDrh)).satisfies(e -> code(e, "FORMAT_NON_SUPPORTE", 400));
	}

	@Test
	@DisplayName("C2-18 / D-I13.i : l'analyse n'est visible que de son auteur (404 ANALYSE_INTROUVABLE pour un autre RH), y compris lignes, correspondance, fichier des erreurs et confirmation")
	void visibiliteParAuteur() {
		Analyse a = analyser(csv(ENTETE, BONNE_LIGNE_1), false);
		assertThatThrownBy(() -> service.obtenir(a.analyseId(), jwtRh02)).satisfies(e -> code(e, "ANALYSE_INTROUVABLE", 404));
		assertThatThrownBy(() -> service.lignes(a.analyseId(), 0, 50, false, jwtRh02)).satisfies(e -> code(e, "ANALYSE_INTROUVABLE", 404));
		assertThatThrownBy(() -> service.fichierErreurs(a.analyseId(), jwtRh02)).satisfies(e -> code(e, "ANALYSE_INTROUVABLE", 404));
		assertThatThrownBy(() -> service.corriger(a.analyseId(), new CorrespondanceRequest(null, List.of(), null, null), jwtRh02))
				.satisfies(e -> code(e, "ANALYSE_INTROUVABLE", 404));
		assertThatThrownBy(() -> confirmer(a.analyseId(), jwtRh02)).satisfies(e -> code(e, "ANALYSE_INTROUVABLE", 404));
		assertThatThrownBy(() -> service.obtenir(UUID.randomUUID(), jwtDrh)).satisfies(e -> code(e, "ANALYSE_INTROUVABLE", 404));
		assertThat(service.obtenir(a.analyseId(), jwtDrh).lignes()).isNull();
	}

	// ─── Confirmation ──────────────────────────────────────────────────────────

	@Test
	@DisplayName("critère 13 / C2-16 : 3 lignes dont 1 en erreur -> 2 créées, 1 rejetée, historique par ligne sans valeur de cellule, statut CONFIRMEE")
	void confirmation() {
		Analyse a = analyser(csv(ENTETE, BONNE_LIGNE_1, BONNE_LIGNE_2, "ERR-1;Zoé;Test;z@x.test;XXX"), true);
		assertThat(a.totaux().erreurs()).isEqualTo(1);

		CompteRendu r = confirmer(a.analyseId(), jwtDrh);

		assertThat(r.creees()).isEqualTo(2);
		assertThat(r.rejetees()).isEqualTo(1);
		assertThat(r.comptesDemandes()).isEqualTo(2);
		verify(publisher, Mockito.times(2)).publishAsyncAfterCommit(any(), any());
		assertThat(analyseRepository.findById(a.analyseId()).orElseThrow().getStatut()).isEqualTo("CONFIRMEE");
		assertThat(analyseRepository.findById(a.analyseId()).orElseThrow().getConfirmeLe()).isEqualTo(horloge.maintenant);
		List<ImportLigne> historique = ligneRepository.findAll();
		assertThat(historique).hasSize(3);
		assertThat(historique).filteredOn(l -> "REJETEE".equals(l.getAction())).singleElement().satisfies(l -> {
			assertThat(l.getErreurs()).contains("UNITE_INCONNUE").doesNotContain("XXX");
			assertThat(l.getMatricule()).isEqualTo("ERR-1");
		});
		assertThat(historique).filteredOn(l -> "CREATION".equals(l.getAction())).hasSize(2).allSatisfy(l -> assertThat(l.getCollaborateurId()).isNotNull());
		// l'historique est lisible (I10 à I12) et ne porte aucun mot de passe
		PageReferentielResponse<ImportDtos.ImportResume> page = service.historique(null, 0, 20);
		assertThat(page.getContenu()).hasSize(1);
		assertThat(page.getContenu().get(0).auteurNom()).isEqualTo("Amel Trabelsi");
		assertThat(page.getContenu().get(0).nomFichier()).isEqualTo("agents.csv");
		assertThat(page.getContenu().get(0).totaux().creees()).isEqualTo(2);
		assertThat(service.lignesHistorique(a.analyseId(), 0, 50).getContenu()).hasSize(3);
		assertThat(service.historique("SOLDES", 0, 20).getContenu()).isEmpty();
		assertThatThrownBy(() -> service.historique("AUTRE", 0, 20)).satisfies(e -> code(e, "CORPS_INVALIDE", 400));
		assertThatThrownBy(() -> service.resume(UUID.randomUUID())).satisfies(e -> code(e, "IMPORT_INTROUVABLE", 404));
	}

	@Test
	@DisplayName("critère 15 / C2-18 : 1 min 59 s après la dernière action -> accepté, 2 min 01 s avec délai réglé à 2 min -> 409 IMPORT_EXPIRE ; confirmée puis rejouée après expiration -> 409 IMPORT_DEJA_CONFIRME")
	void expiration() {
		ImportService court = new ImportService(analyseRepository, contenuRepository, ligneRepository, collaborateursService,
				soldesService, identite, new ImportVerrou(jdbc), auteurResolver, connecte, JsonMapper.builder().build(), horloge, tm, 2);
		Analyse a = analyser(csv(ENTETE, BONNE_LIGNE_1), false);
		Instant debut = horloge.maintenant;
		horloge.maintenant = debut.plusSeconds(31 * 60);
		assertThatThrownBy(() -> confirmer(a.analyseId(), jwtDrh)).satisfies(e -> code(e, "IMPORT_EXPIRE", 409));
		assertThatThrownBy(() -> service.obtenir(a.analyseId(), jwtDrh)).satisfies(e -> code(e, "IMPORT_EXPIRE", 409));
		assertThatThrownBy(() -> service.lignes(a.analyseId(), 0, 50, false, jwtDrh)).satisfies(e -> code(e, "IMPORT_EXPIRE", 409));
		// délai réglable (2 min) : à 1 min 59 s la confirmation passe, à 2 min 01 s elle est expirée
		horloge.maintenant = debut;
		Analyse b = analyser(csv(ENTETE, BONNE_LIGNE_2), false);
		horloge.maintenant = debut.plusSeconds(121);
		assertThatThrownBy(() -> new TransactionTemplate(tm).execute(s -> court.confirmer(b.analyseId(), null, jwtDrh)))
				.satisfies(e -> code(e, "IMPORT_EXPIRE", 409));
		// confirmée, puis rejouée bien après l'expiration
		horloge.maintenant = debut;
		Analyse c = analyser(csv(ENTETE, "NEW-9;Ada;Lovelace;ada9@x.test;DTECH-SW"), false);
		horloge.maintenant = debut.plusSeconds(119);
		assertThat(new TransactionTemplate(tm).execute(s -> court.confirmer(c.analyseId(), null, jwtDrh)).creees()).isEqualTo(1);
		horloge.maintenant = debut.plus(Duration.ofHours(10));
		assertThatThrownBy(() -> confirmer(c.analyseId(), jwtDrh)).satisfies(e -> code(e, "IMPORT_DEJA_CONFIRME", 409));
	}

	@Test
	@DisplayName("critère 16 : analyse sans aucune ligne valide ou seulement inchangée -> 422 AUCUNE_LIGNE_IMPORTABLE")
	void aucuneLigneImportable() {
		Analyse a = analyser(csv(ENTETE, "ERR-1;A;B;e@x.test;XXX"), false);
		assertThatThrownBy(() -> confirmer(a.analyseId(), jwtDrh)).satisfies(e -> code(e, "AUCUNE_LIGNE_IMPORTABLE", 422));
		Analyse inchangee = analyser(csv("Mat.;Poste", "COL-WEB;Développeur"), false);
		assertThat(inchangee.totaux().inchangees()).isEqualTo(1);
		assertThatThrownBy(() -> confirmer(inchangee.analyseId(), jwtDrh)).satisfies(e -> code(e, "AUCUNE_LIGNE_IMPORTABLE", 422));
		assertThat(analyseRepository.findById(a.analyseId()).orElseThrow().getStatut()).isEqualTo("ANALYSEE");
	}

	@Test
	@DisplayName("critère 14 / C2-17 : matricule créé à la main ou fiche modifiée entre l'analyse et la confirmation -> ligne rejetée LIGNE_MODIFIEE_DEPUIS_ANALYSE, comptée et présente dans le fichier des erreurs")
	void ligneModifieeDepuisAnalyse() {
		Analyse a = analyser(csv(ENTETE, BONNE_LIGNE_1, BONNE_LIGNE_2), false);
		Analyse b = analyser(csv("Mat.;Poste", "COL-WEB;Développeur senior"), false);
		Collaborateur apparu = new Collaborateur();
		apparu.setId(UUID.randomUUID());
		apparu.setMatricule("NEW-1");
		apparu.setPrenom("Ada");
		apparu.setNom("Lovelace");
		apparu.setUnite(software);
		fiches.add(apparu);
		colWeb.setModifieLe(Instant.parse("2026-12-10T10:00:30Z"));

		CompteRendu r = confirmer(a.analyseId(), jwtDrh);
		assertThat(r.creees()).isEqualTo(1);
		assertThat(r.rejetees()).isEqualTo(1);
		assertThatThrownBy(() -> confirmer(b.analyseId(), jwtDrh)).satisfies(e -> code(e, "AUCUNE_LIGNE_IMPORTABLE", 422));
		Fichier erreurs = LectureFichier.lire("e.xlsx", service.fichierErreurs(a.analyseId(), jwtDrh));
		assertThat(erreurs.lignes()).hasSize(2);
		assertThat(erreurs.lignes().get(1).cellules()).contains("NEW-1").anyMatch(c -> c.contains("a changé depuis l'analyse"));
	}

	@Test
	@DisplayName("critère 15 / C2-18 : un autre import en cours -> 409 IMPORT_EN_COURS, relancée après la fin -> 200")
	void verrouUnique() throws Exception {
		Analyse a = analyser(csv(ENTETE, BONNE_LIGNE_1), false);
		CountDownLatch pris = new CountDownLatch(1);
		CountDownLatch relache = new CountDownLatch(1);
		var pool = Executors.newSingleThreadExecutor();
		try {
			Future<?> autre = pool.submit(() -> new TransactionTemplate(tm).executeWithoutResult(s -> {
				if (!new ImportVerrou(jdbc).essayer()) {
					throw new IllegalStateException("verrou non obtenu");
				}
				pris.countDown();
				try {
					relache.await(20, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}));
			assertThat(pris.await(10, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> confirmer(a.analyseId(), jwtDrh)).satisfies(e -> code(e, "IMPORT_EN_COURS", 409));
			relache.countDown();
			autre.get(10, TimeUnit.SECONDS);
		} finally {
			relache.countDown();
			pool.shutdownNow();
		}
		assertThat(confirmer(a.analyseId(), jwtDrh).creees()).isEqualTo(1);
	}

	@Test
	@DisplayName("critère 17 / C2-19 : échec forcé à l'écriture -> aucune ligne d'historique, aucun événement Kafka, statut inchangé, l'analyse reste confirmable")
	void atomicite() {
		Analyse a = analyser(csv(ENTETE, BONNE_LIGNE_1, BONNE_LIGNE_2), true);
		Mockito.doThrow(new IllegalStateException("panne de base")).when(collaborateurRepository).saveAll(any());
		assertThatThrownBy(() -> confirmer(a.analyseId(), jwtDrh)).isInstanceOf(IllegalStateException.class);
		verify(publisher, never()).publishAsyncAfterCommit(any(), any());
		assertThat(ligneRepository.count()).isZero();
		ImportAnalyse apres = analyseRepository.findById(a.analyseId()).orElseThrow();
		assertThat(apres.getStatut()).isEqualTo("ANALYSEE");
		assertThat(apres.getConfirmeLe()).isNull();
		Mockito.doAnswer(i -> {
			List<Collaborateur> l = i.getArgument(0);
			l.forEach(c -> c.setId(UUID.randomUUID()));
			return l;
		}).when(collaborateurRepository).saveAll(any());
		assertThat(confirmer(a.analyseId(), jwtDrh).creees()).isEqualTo(2);
	}

	// ─── Fichier des erreurs, modèle, purge ────────────────────────────────────

	@Test
	@DisplayName("critère 20 / C2-22 : fichier des erreurs .xlsx = colonnes d'origine (même ignorées) + « Erreurs » en français, lignes en erreur seulement, « =1+1 » ressort « '=1+1 », « -5 » aussi")
	void fichierDesErreurs() {
		Analyse a = analyser(csv(ENTETE + ";Téléphone", BONNE_LIGNE_1 + ";55", "=1+1;-5;@x;e@x.test;XXX;77"), false);
		Fichier f = LectureFichier.lire("e.xlsx", service.fichierErreurs(a.analyseId(), jwtDrh));
		assertThat(f.lignes()).hasSize(2);
		assertThat(f.lignes().get(0).cellules()).containsExactly("Mat.", "Prénom", "NOM", "E-mail", "Code unité", "Téléphone", "Erreurs");
		List<String> ligne = f.lignes().get(1).cellules();
		assertThat(ligne.get(0)).isEqualTo("'=1+1");
		assertThat(ligne.get(1)).isEqualTo("'-5");
		assertThat(ligne.get(2)).isEqualTo("'@x");
		assertThat(ligne.get(5)).isEqualTo("77");
		assertThat(ligne.get(6)).contains("Unité inconnue");
	}

	@Test
	@DisplayName("critère 6 / C4-09 : le modèle est un .xlsx à 14 en-têtes, renvoyé tel quel -> 14 colonnes reconnues ; modèle des soldes : 3 colonnes")
	void modele() {
		Fichier modele = LectureFichier.lire("m.xlsx", service.modeleCollaborateurs());
		assertThat(modele.lignes()).hasSize(1);
		assertThat(modele.lignes().get(0).cellules()).hasSize(14);
		Analyse a = service.analyserCollaborateurs(new MockMultipartFile("fichier", "modele.xlsx", "application/octet-stream",
				service.modeleCollaborateurs()), false, jwtDrh);
		assertThat(a.colonnes()).hasSize(14).allMatch(c -> "RECONNUE".equals(c.etat()));
		assertThat(a.totaux().lignes()).isZero();
		assertThat(LectureFichier.lire("s.xlsx", service.modeleSoldes()).lignes().get(0).cellules()).containsExactly("Matricule", "Solde", "Motif");
	}

	@Test
	@DisplayName("ADR § 6 : la purge supprime le contenu extrait 30 min après la dernière action (confirmé comprises) et les analyses non confirmées 24 h plus tard ; l'historique reste")
	void purge() {
		Analyse a = analyser(csv(ENTETE, BONNE_LIGNE_1), false);
		Analyse confirmee = analyser(csv(ENTETE, BONNE_LIGNE_2), false);
		confirmer(confirmee.analyseId(), jwtDrh);
		ImportPurgeScheduler purge = new ImportPurgeScheduler(jdbc, service, horloge);
		horloge.maintenant = horloge.maintenant.plus(Duration.ofMinutes(31));
		assertThat(purge.purger()).isEqualTo(2);
		assertThat(contenuRepository.count()).isZero();
		assertThat(analyseRepository.findById(a.analyseId())).isPresent();
		horloge.maintenant = horloge.maintenant.plus(Duration.ofHours(25));
		purge.purger();
		assertThat(analyseRepository.findById(a.analyseId())).isEmpty();
		assertThat(analyseRepository.findById(confirmee.analyseId())).isPresent();
		assertThat(ligneRepository.count()).isEqualTo(1);
		assertThatThrownBy(() -> service.obtenir(a.analyseId(), jwtDrh)).satisfies(e -> code(e, "ANALYSE_INTROUVABLE", 404));
	}

	// ─── Soldes ────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("critère 9 à 12 (T3) : en-têtes « N° matricule », « Solde au 30/11 », « Observation » reconnus ; confirmation sans motif global -> 400 MOTIF_OBLIGATOIRE ; avec motif -> ajustements écrits, historique")
	void soldes() {
		when(soldeService.acquisGroupes(any())).thenReturn(java.util.Map.of(colWeb.getId(), new BigDecimal("10.0")));
		when(soldeService.reservesGroupes(any())).thenReturn(java.util.Map.of());
		when(lotService.ajuster(any(), any(), any())).thenAnswer(i -> {
			List<com.hr.referentiel.service.SoldeLotService.Ajustement> demandes = i.getArgument(0);
			return demandes.stream().map(d -> new com.hr.referentiel.service.SoldeLotService.Resultat(d.collaborateurId(),
					new BigDecimal("2.5"), d.cible(), d.motif())).toList();
		});
		Analyse a = service.analyserSoldes(new MockMultipartFile("fichier", "soldes.csv", "text/csv",
				csv("N° matricule;Solde au 30/11;Observation", "COL-WEB;12,5;", "INCONNU;3;").clone()), null, jwtDrh);
		assertThat(a.colonnes()).extracting(ImportDtos.ColonneVue::champ).containsExactly("MATRICULE", "SOLDE", "MOTIF");
		assertThat(a.totaux().ajustements()).isEqualTo(1);
		assertThat(a.totaux().creations()).isNull();
		LigneAnalyse l0 = a.lignes().getContenu().get(0);
		assertThat(l0.soldeActuel()).isEqualByComparingTo("10");
		assertThat(l0.soldeCible()).isEqualByComparingTo("12.5");
		assertThat(l0.ecart()).isEqualByComparingTo("2.5");
		assertThatThrownBy(() -> new TransactionTemplate(tm).execute(st -> service.confirmer(a.analyseId(), null, jwtDrh)))
				.satisfies(e -> code(e, "MOTIF_OBLIGATOIRE", 400));
		assertThatThrownBy(() -> new TransactionTemplate(tm).execute(st -> service.confirmer(a.analyseId(),
				new ConfirmationRequest("abcd"), jwtDrh))).satisfies(e -> code(e, "MOTIF_OBLIGATOIRE", 400));

		CompteRendu r = new TransactionTemplate(tm).execute(st -> service.confirmer(a.analyseId(),
				new ConfirmationRequest("Reprise des soldes au 30/11/2026"), jwtDrh));

		assertThat(r.ajustements()).isEqualTo(1);
		assertThat(r.rejetees()).isEqualTo(1);
		assertThat(ligneRepository.findAll()).extracting(ImportLigne::getAction).containsExactlyInAnyOrder("AJUSTEMENT", "REJETEE");
		assertThat(analyseRepository.findById(a.analyseId()).orElseThrow().getMotifGlobal()).isEqualTo("Reprise des soldes au 30/11/2026");
	}

	@Test
	@DisplayName("D-I07 : service d'identité en panne -> l'analyse continue avec l'avertissement VERIFICATION_COMPTES_INDISPONIBLE ; matricules interrogés = ceux à créer")
	void identiteEnPanne() {
		Mockito.doThrow(new IdentiteInterneClient.IdentiteIndisponibleException("panne", null)).when(identite).identifiantsExistants(any());
		Analyse a = analyser(csv(ENTETE, BONNE_LIGNE_1), true);
		assertThat(a.avertissementsFichier()).extracting(ImportDtos.ProblemeVue::code).contains("VERIFICATION_COMPTES_INDISPONIBLE");
		assertThat(a.totaux().creations()).isEqualTo(1);
		Mockito.doReturn(Set.of("new-1")).when(identite).identifiantsExistants(any());
		Analyse b = analyser(csv(ENTETE, BONNE_LIGNE_1, "COL-WEB;Sarra;Khemiri;s@x.test;DTECH-SW"), true);
		verify(identite, Mockito.times(2)).identifiantsExistants(Set.of("new-1"));
		assertThat(b.lignes().getContenu().get(0).avertissements()).extracting(ImportDtos.ProblemeVue::code).contains("COMPTE_DEJA_EXISTANT");
	}

	@Test
	@DisplayName("D-I09 : case décochée -> aucun appel au service d'identité ; la case se change par la correspondance sans renvoyer le fichier")
	void caseComptes() {
		Analyse a = analyser(csv(ENTETE, BONNE_LIGNE_1), false);
		verify(identite, never()).identifiantsExistants(any());
		Analyse coche = service.corriger(a.analyseId(), new CorrespondanceRequest(null, List.of(new ColonneChoix(0, "MATRICULE"),
				new ColonneChoix(1, "PRENOM"), new ColonneChoix(2, "NOM"), new ColonneChoix(3, "COURRIEL"), new ColonneChoix(4, "UNITE")),
				true, null), jwtDrh);
		assertThat(coche.creerComptes()).isTrue();
		assertThat(coche.expireLe()).isEqualTo(horloge.maintenant.plus(Duration.ofMinutes(30)));
	}

	@Test
	@DisplayName("lignes paginées par 50 côté serveur, filtre « erreurs seulement », taille 101 -> 400")
	void pagination() {
		List<String> lignes = new ArrayList<>();
		lignes.add(ENTETE);
		for (int i = 0; i < 120; i++) {
			lignes.add((i % 10 == 0 ? "ERR" : "N") + i + ";A;B;n" + i + "@x.test;" + (i % 10 == 0 ? "XXX" : "DTECH-SW"));
		}
		Analyse a = analyser(csv(lignes.toArray(new String[0])), false);
		assertThat(a.lignes().getContenu()).hasSize(50);
		assertThat(a.lignes().getTotalPages()).isEqualTo(3);
		assertThat(service.lignes(a.analyseId(), 2, 50, false, jwtDrh).getContenu()).hasSize(20);
		PageReferentielResponse<LigneAnalyse> erreurs = service.lignes(a.analyseId(), 0, 50, true, jwtDrh);
		assertThat(erreurs.getTotalElements()).isEqualTo(12);
		assertThat(erreurs.getContenu()).allMatch(l -> "ERREUR".equals(l.action()));
		assertThatThrownBy(() -> service.lignes(a.analyseId(), 0, 101, false, jwtDrh)).satisfies(e -> code(e, "CORPS_INVALIDE", 400));
	}

	@Test
	@DisplayName("m1 : le second confirm d'une analyse déjà confirmée -> 409 IMPORT_DEJA_CONFIRME (état lu sous verrou de ligne)")
	void secondConfirmation() {
		Analyse a = analyser(csv(ENTETE, BONNE_LIGNE_1), false);
		assertThat(confirmer(a.analyseId(), jwtDrh).creees()).isEqualTo(1);
		assertThatThrownBy(() -> confirmer(a.analyseId(), jwtDrh)).satisfies(e -> code(e, "IMPORT_DEJA_CONFIRME", 409));
		assertThat(ligneRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("m2 : le compte rendu ne compte que les lignes IMPORTÉES avec avertissement (une ligne en erreur avec avertissement n'est pas comptée)")
	void avertissementsDuCompteRendu() {
		// NEW-1 : créée sans courriel (avertissement, importée) ; ERR-1 : sans courriel ET unité inconnue (avertissement + erreur)
		Analyse a = analyser(csv("Mat.;Prénom;NOM;Code unité", "NEW-1;Ada;Lovelace;DTECH-SW", "ERR-1;Zoé;Test;XXX"), false);
		assertThat(a.totaux().avertissements()).isEqualTo(2);
		CompteRendu r = confirmer(a.analyseId(), jwtDrh);
		assertThat(r.avertissements()).contains("1 ligne importée avec avertissement.");
	}

	@Test
	@DisplayName("ADR § 6 : analyse non confirmée expirée depuis plus de 24 h -> 404 ANALYSE_INTROUVABLE à la lecture, même si la purge n'a pas tourné ; entre 30 min et 24 h -> 409 IMPORT_EXPIRE")
	void analyseExpireePlusDe24Heures() {
		Analyse a = analyser(csv(ENTETE, BONNE_LIGNE_1), false);
		Instant debut = horloge.maintenant;
		horloge.maintenant = debut.plus(Duration.ofMinutes(30)).plus(Duration.ofHours(23));
		assertThatThrownBy(() -> service.obtenir(a.analyseId(), jwtDrh)).satisfies(e -> code(e, "IMPORT_EXPIRE", 409));
		horloge.maintenant = debut.plus(Duration.ofMinutes(30)).plus(Duration.ofHours(24)).plusSeconds(1);
		assertThat(analyseRepository.findById(a.analyseId())).isPresent();
		assertThatThrownBy(() -> service.obtenir(a.analyseId(), jwtDrh)).satisfies(e -> code(e, "ANALYSE_INTROUVABLE", 404));
		assertThatThrownBy(() -> confirmer(a.analyseId(), jwtDrh)).satisfies(e -> code(e, "ANALYSE_INTROUVABLE", 404));
		assertThatThrownBy(() -> service.fichierErreurs(a.analyseId(), jwtDrh)).satisfies(e -> code(e, "ANALYSE_INTROUVABLE", 404));
	}
}
