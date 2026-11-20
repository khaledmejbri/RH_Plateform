package com.hr.referentiel.importation.parametrage;

import com.hr.referentiel.config.ImportChampsAmorceRunner;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.ChampRequest;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.ChampVue;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.EnregistrerRequest;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.LigneJournal;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.Parametrage;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.SynonymeRequest;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.SynonymeVue;
import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.test.AvecSocieteContexteExtension;
import com.hr.referentiel.test.SocieteContexteTestExecutionListener;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import com.hr.referentiel.importation.ReconnaissanceEnTetes;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Règles de T1 (D-I14 à D-I23) sur base H2 réelle : verrous, exigences, en-têtes, synonymes, version, journal. */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:importparametrage;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ImportParametrageServiceTest.Config.class)
@ExtendWith(AvecSocieteContexteExtension.class)
@TestExecutionListeners(listeners = SocieteContexteTestExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class ImportParametrageServiceTest {

	@Configuration
	@EnableJpaRepositories(basePackageClasses = {ImportParametrageRepository.class, com.hr.referentiel.champsperso.ChampPersoRepository.class})
	static class Config {
		@Bean
		PersistenceManagedTypes managedTypes() {
			return PersistenceManagedTypes.of(ImportParametrage.class.getName(), ImportChampRegle.class.getName(),
					ImportSynonyme.class.getName(), ImportChampJournal.class.getName(),
					com.hr.referentiel.champsperso.ChampPerso.class.getName(),
					com.hr.referentiel.champsperso.ChampPersoValeurListe.class.getName(),
					com.hr.referentiel.champsperso.ValeurPersoFiche.class.getName(),
					com.hr.referentiel.champsperso.ValeurPersoMouvement.class.getName());
		}
	}

	private static final AuteurParametre DRH01 = new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi");
	private static final String COL = "collaborateurs";
	private static final String SOL = "soldes";

	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	PlatformTransactionManager tm;
	@Autowired
	ImportParametrageRepository parametrageRepository;
	@Autowired
	ImportChampRegleRepository champRepository;
	@Autowired
	ImportSynonymeRepository synonymeRepository;
	@Autowired
	ImportChampJournalRepository journalRepository;
	@Autowired
	com.hr.referentiel.champsperso.ChampPersoRepository champPersoRepository;
	@Autowired
	com.hr.referentiel.champsperso.ChampPersoValeurListeRepository valeurListeRepository;
	@Autowired
	jakarta.persistence.EntityManager entityManager;

	private ParametrageImportCache cache;
	private ImportParametrageService service;

	@BeforeEach
	void init() {
		cache = mock(ParametrageImportCache.class);
		new ImportChampsAmorceRunner(jdbc, tm, cache, () -> List.of(SocieteContexte.courante())).amorcer();
		ImportChampsJournal journal = new ImportChampsJournal(journalRepository);
		service = new ImportParametrageService(new ParametrageEcriture(parametrageRepository, champRepository,
				synonymeRepository, champPersoRepository, valeurListeRepository, journal, cache,
				new com.hr.referentiel.champsperso.ChampPersoUtilisations(jdbc), entityManager), synonymeRepository, journal, Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC));
	}

	// ─── Outils ────────────────────────────────────────────────────────────────

	private List<ChampRequest> tableau(String type) {
		List<ChampRequest> r = new ArrayList<>();
		for (ChampVue c : service.lire(type).champs()) {
			r.add(new ChampRequest(c.code(), c.enTete(), c.dansModele(), c.exigence()));
		}
		return r;
	}

	private ChampRequest ch(List<ChampRequest> t, String code) {
		return t.stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
	}

	private void regler(List<ChampRequest> t, String code, String enTete, Boolean dansModele, String exigence) {
		ChampRequest c = ch(t, code);
		t.set(t.indexOf(c), new ChampRequest(code, enTete != null ? enTete : c.enTete(),
				dansModele != null ? dansModele : c.dansModele(), exigence != null ? exigence : c.exigence()));
	}

	private Parametrage enregistrer(String type, Consumer<List<ChampRequest>> modification) {
		List<ChampRequest> t = tableau(type);
		modification.accept(t);
		return service.enregistrer(type, new EnregistrerRequest(service.lire(type).version(), t), DRH01);
	}

	private Throwable echec(Runnable r) {
		return catchThrowable(r);
	}

	private static Throwable catchThrowable(Runnable r) {
		try {
			r.run();
			return null;
		} catch (RuntimeException e) {
			return e;
		}
	}

	private void erreur(Throwable t, int statut, String code) {
		assertThat(t).isInstanceOf(ReferentielMetierException.class);
		ReferentielMetierException e = (ReferentielMetierException) t;
		assertThat(e.getCode()).isEqualTo(code);
		assertThat(e.getHttpStatus()).isEqualTo(statut);
	}

	private ChampVue champ(Parametrage p, String code) {
		return p.champs().stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
	}

	private SynonymeVue synonyme(Parametrage p, String champ, String forme) {
		return champ(p, champ).synonymes().stream().filter(s -> s.formeNormalisee().equals(forme)).findFirst().orElseThrow();
	}

	private long version(String type) {
		return service.lire(type).version();
	}

	private List<LigneJournal> journal(String type) {
		return service.lireJournal(type, 0, 100).contenu();
	}

	// ─── Lecture (T1-1, T1-24) ─────────────────────────────────────────────────

	@Test
	@DisplayName("T1-1 : base neuve, collaborateurs -> 14 champs dans l'ordre du code, tous dans le modèle, verrous sur Matricule, Prénom, Nom, Unité, synonymes du code DEFAUT actifs")
	void lectureCollaborateurs() {
		Parametrage p = service.lire(COL);
		assertThat(p.type()).isEqualTo("collaborateurs");
		assertThat(p.typeLibelle()).isEqualTo("Collaborateurs");
		assertThat(p.version()).isZero();
		assertThat(p.champs()).extracting(ChampVue::code).containsExactly("MATRICULE", "PRENOM", "NOM", "COURRIEL", "UNITE",
				"POSTE", "FONCTION", "QUALITE", "AFFECTATION", "DATE_RECRUTEMENT", "STATUT", "PROFIL_ACCES", "FAMILLE_METIER",
				"NIVEAU_SENIORITE");
		assertThat(p.champs()).allMatch(ChampVue::dansModele);
		ChampVue matricule = champ(p, "MATRICULE");
		assertThat(matricule.exigence()).isEqualTo("TOUJOURS");
		assertThat(matricule.retirableDuModele()).isFalse();
		assertThat(matricule.verrouille()).isTrue();
		assertThat(matricule.verrouLibelle()).isEqualTo("Toujours obligatoire : sert à retrouver la fiche");
		assertThat(matricule.exigencesPossibles()).extracting(e -> e.code()).containsExactly("TOUJOURS");
		for (String code : List.of("PRENOM", "NOM", "UNITE")) {
			assertThat(champ(p, code).exigence()).isEqualTo("CREATION");
			assertThat(champ(p, code).retirableDuModele()).isFalse();
			assertThat(champ(p, code).exigencesPossibles()).extracting(e -> e.code()).containsExactly("CREATION", "TOUJOURS");
		}
		assertThat(champ(p, "COURRIEL").exigence()).isEqualTo("FACULTATIF");
		assertThat(champ(p, "COURRIEL").verrouille()).isFalse();
		assertThat(champ(p, "COURRIEL").exigenceLibelle()).isEqualTo("Facultatif");
		assertThat(champ(p, "COURRIEL").enTeteDefaut()).isEqualTo("Courriel");
		assertThat(champ(p, "COURRIEL").libelle()).isEqualTo("Courriel");
		assertThat(champ(p, "COURRIEL").synonymes()).allMatch(s -> s.actif() && "DEFAUT".equals(s.origine()) && !s.supprimable());
		assertThat(champ(p, "COURRIEL").synonymes()).extracting(SynonymeVue::formeNormalisee).contains("courriel", "email", "e mail", "mail");
	}

	@Test
	@DisplayName("T1-1 : soldes -> 3 champs, Matricule et Solde « Toujours obligatoire » non retirables, Motif facultatif")
	void lectureSoldes() {
		Parametrage p = service.lire(SOL);
		assertThat(p.champs()).extracting(ChampVue::code).containsExactly("MATRICULE", "SOLDE", "MOTIF");
		assertThat(champ(p, "SOLDE").exigence()).isEqualTo("TOUJOURS");
		assertThat(champ(p, "SOLDE").retirableDuModele()).isFalse();
		assertThat(champ(p, "SOLDE").verrouLibelle()).isEqualTo("Exigé pour chaque ligne");
		assertThat(champ(p, "MOTIF").exigencesPossibles()).extracting(e -> e.code()).containsExactly("FACULTATIF", "TOUJOURS");
		assertThat(champ(p, "MOTIF").verrouLibelle()).isNull();
	}

	@Test
	@DisplayName("T1-24 : type inconnu -> 404 TYPE_IMPORT_INCONNU sur lecture, écriture et journal")
	void typeInconnu() {
		erreur(echec(() -> service.lire("fournisseurs")), 404, "TYPE_IMPORT_INCONNU");
		erreur(echec(() -> service.lireJournal("fournisseurs", 0, 20)), 404, "TYPE_IMPORT_INCONNU");
		erreur(echec(() -> service.reinitialiser("fournisseurs", 0L, DRH01)), 404, "TYPE_IMPORT_INCONNU");
		erreur(echec(() -> service.ajouterSynonyme("fournisseurs", "NOM", new SynonymeRequest("x y", 0L), DRH01)), 404,
				"TYPE_IMPORT_INCONNU");
	}

	// ─── P2 : enregistrer ──────────────────────────────────────────────────────

	@Test
	@DisplayName("T1-3 : Courriel renommé E-mail, Poste retiré du modèle, Unité 2e -> version +1 ; « courriel » déjà synonyme par défaut : rien d'ajouté")
	void enregistrerTableau() {
		int avant = champ(service.lire(COL), "COURRIEL").synonymes().size();
		Parametrage p = enregistrer(COL, t -> {
			regler(t, "COURRIEL", "E-mail", null, null);
			regler(t, "POSTE", null, false, null);
			ChampRequest unite = ch(t, "UNITE");
			t.remove(unite);
			t.add(1, unite);
		});
		assertThat(p.version()).isEqualTo(1);
		assertThat(p.modifieParNom()).isEqualTo("Amel Trabelsi");
		assertThat(champ(p, "COURRIEL").enTete()).isEqualTo("E-mail");
		assertThat(champ(p, "COURRIEL").synonymes()).hasSize(avant);
		assertThat(champ(p, "POSTE").dansModele()).isFalse();
		assertThat(p.champs().get(1).code()).isEqualTo("UNITE");
		assertThat(p.champs()).extracting(ChampVue::ordre).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
		assertThat(version(SOL)).as("l'autre type n'est pas touché").isZero();
		org.mockito.Mockito.verify(cache).invaliderApresCommit(TypeImportParametrable.COLLABORATEURS);
	}

	@Test
	@DisplayName("T1-3 : Matricule renommé « Mle » -> l'ancien en-tête « Matricule » (déjà synonyme par défaut) n'est pas dupliqué")
	void renommerMatricule() {
		int avant = champ(service.lire(COL), "MATRICULE").synonymes().size();
		Parametrage p = enregistrer(COL, t -> regler(t, "MATRICULE", "Mle", null, null));
		assertThat(champ(p, "MATRICULE").synonymes()).hasSize(avant);
	}

	@Test
	@DisplayName("T1-4 : Fonction -> Emploi (rien d'ajouté), puis Emploi -> Métier : « Emploi » devient synonyme d'origine RH de Fonction")
	void ancienEnTeteDevientSynonyme() {
		int avant = champ(service.lire(COL), "FONCTION").synonymes().size();
		Parametrage p1 = enregistrer(COL, t -> regler(t, "FONCTION", "Emploi", null, null));
		assertThat(champ(p1, "FONCTION").synonymes()).hasSize(avant);
		Parametrage p2 = enregistrer(COL, t -> regler(t, "FONCTION", "Métier", null, null));
		assertThat(champ(p2, "FONCTION").synonymes()).hasSize(avant + 1);
		SynonymeVue s = synonyme(p2, "FONCTION", "emploi");
		assertThat(s.origine()).isEqualTo("RH");
		assertThat(s.texte()).isEqualTo("Emploi");
		assertThat(s.actif()).isTrue();
		assertThat(s.supprimable()).isTrue();
		assertThat(journal(COL)).extracting(LigneJournal::attribut).contains("SYNONYME_AJOUTE");
	}

	@Test
	@DisplayName("T1-4 / ADR § 5 : un synonyme inactif de la forme de l'ancien en-tête est réactivé (journal « Synonyme réactivé »)")
	void ancienEnTeteReactiveUnInactif() {
		Parametrage p = service.lire(COL);
		SynonymeVue fonction = synonyme(p, "FONCTION", "fonction");
		service.desactiver(COL, fonction.id(), p.version(), DRH01);
		assertThat(synonyme(service.lire(COL), "FONCTION", "fonction").actif()).isFalse();

		Parametrage apres = enregistrer(COL, t -> regler(t, "FONCTION", "Métier", null, null));

		assertThat(synonyme(apres, "FONCTION", "fonction").actif()).isTrue();
		assertThat(journal(COL)).extracting(LigneJournal::attribut).contains("SYNONYME_REACTIVE");
	}

	@Test
	@DisplayName("T1-5 : retirer du modèle Matricule, Prénom, Nom ou Unité (collaborateurs) ou Solde (soldes) -> 422 CHAMP_VERROUILLE, rien n'est modifié")
	void retraitDunChampVerrouille() {
		for (String code : List.of("MATRICULE", "PRENOM", "NOM", "UNITE")) {
			long v = version(COL);
			Throwable t = echec(() -> enregistrer(COL, tab -> regler(tab, code, null, false, null)));
			erreur(t, 422, "CHAMP_VERROUILLE");
			assertThat(((ReferentielMetierException) t).getDetails()).containsEntry("champ", code);
			assertThat(((ReferentielMetierException) t).getMessage()).contains(champ(service.lire(COL), code).libelle());
			assertThat(version(COL)).isEqualTo(v);
		}
		erreur(echec(() -> enregistrer(SOL, tab -> regler(tab, "SOLDE", null, false, null))), 422, "CHAMP_VERROUILLE");
		assertThat(journal(COL)).isEmpty();
	}

	@Test
	@DisplayName("T1-6 : Prénom FACULTATIF ou Matricule CREATION -> 422 CHAMP_VERROUILLE ; Prénom TOUJOURS -> 200")
	void exigenceSousLeMinimum() {
		erreur(echec(() -> enregistrer(COL, t -> regler(t, "PRENOM", null, null, "FACULTATIF"))), 422, "CHAMP_VERROUILLE");
		erreur(echec(() -> enregistrer(COL, t -> regler(t, "MATRICULE", null, null, "CREATION"))), 422, "CHAMP_VERROUILLE");
		Parametrage p = enregistrer(COL, t -> regler(t, "PRENOM", null, null, "TOUJOURS"));
		assertThat(champ(p, "PRENOM").exigence()).isEqualTo("TOUJOURS");
		assertThat(journal(COL)).anySatisfy(l -> {
			assertThat(l.attribut()).isEqualTo("EXIGENCE");
			assertThat(l.avant()).isEqualTo("Obligatoire à la création");
			assertThat(l.apres()).isEqualTo("Toujours obligatoire");
		});
	}

	@Test
	@DisplayName("T1-7 : soldes, Motif CREATION -> 400 EXIGENCE_INVALIDE ; Motif TOUJOURS -> 200 ; valeur inconnue -> 400 EXIGENCE_INVALIDE")
	void exigencesDesSoldes() {
		erreur(echec(() -> enregistrer(SOL, t -> regler(t, "MOTIF", null, null, "CREATION"))), 400, "EXIGENCE_INVALIDE");
		erreur(echec(() -> enregistrer(SOL, t -> regler(t, "MOTIF", null, null, "OBLIGATOIRE"))), 400, "EXIGENCE_INVALIDE");
		assertThat(champ(enregistrer(SOL, t -> regler(t, "MOTIF", null, null, "TOUJOURS")), "MOTIF").exigence()).isEqualTo("TOUJOURS");
	}

	@Test
	@DisplayName("T1-8 : Poste hors modèle avec une exigence autre que facultatif -> 422 CHAMP_OBLIGATOIRE_HORS_MODELE")
	void obligatoireHorsModele() {
		Throwable t = echec(() -> enregistrer(COL, tab -> regler(tab, "POSTE", null, false, "CREATION")));
		erreur(t, 422, "CHAMP_OBLIGATOIRE_HORS_MODELE");
		assertThat(((ReferentielMetierException) t).getDetails()).containsEntry("champ", "POSTE");
		assertThat(champ(service.lire(COL), "POSTE").dansModele()).isTrue();
	}

	@Test
	@DisplayName("T1-9 : un champ omis ou répété -> 400 ORDRE_INCOMPLET ; code inconnu -> 400 CORPS_INVALIDE ; version absente -> 400 VERSION_OBLIGATOIRE ; corps absent -> 400 CORPS_INVALIDE")
	void ordreEtCorps() {
		List<ChampRequest> sans = tableau(COL);
		sans.remove(ch(sans, "POSTE"));
		erreur(echec(() -> service.enregistrer(COL, new EnregistrerRequest(0L, sans), DRH01)), 400, "ORDRE_INCOMPLET");
		List<ChampRequest> repete = tableau(COL);
		repete.set(repete.indexOf(ch(repete, "POSTE")), ch(repete, "NOM"));
		erreur(echec(() -> service.enregistrer(COL, new EnregistrerRequest(0L, repete), DRH01)), 400, "ORDRE_INCOMPLET");
		List<ChampRequest> inconnu = tableau(COL);
		inconnu.set(0, new ChampRequest("TELEPHONE", "Téléphone", true, "FACULTATIF"));
		erreur(echec(() -> service.enregistrer(COL, new EnregistrerRequest(0L, inconnu), DRH01)), 400, "CORPS_INVALIDE");
		erreur(echec(() -> service.enregistrer(COL, new EnregistrerRequest(null, tableau(COL)), DRH01)), 400, "VERSION_OBLIGATOIRE");
		erreur(echec(() -> service.enregistrer(COL, null, DRH01)), 400, "CORPS_INVALIDE");
		List<ChampRequest> incomplet = tableau(COL);
		incomplet.set(0, new ChampRequest("MATRICULE", "Matricule", null, "TOUJOURS"));
		erreur(echec(() -> service.enregistrer(COL, new EnregistrerRequest(0L, incomplet), DRH01)), 400, "CORPS_INVALIDE");
		assertThat(version(COL)).isZero();
	}

	@Test
	@DisplayName("T1-10 : en-tête de 1 ou 81 caractères, « *** », « 2026 », « =SOMME(A1) », « -Nom » -> 400 LIBELLE_INVALIDE ; « Courriel pro » accepté")
	void libellesInvalides() {
		for (String mauvais : List.of("E", "x".repeat(81), "***", "2026", "=SOMME(A1)", "-Nom", "+33", "@qui", "  E  ")) {
			Throwable t = echec(() -> enregistrer(COL, tab -> regler(tab, "COURRIEL", mauvais, null, null)));
			erreur(t, 400, "LIBELLE_INVALIDE");
			assertThat(((ReferentielMetierException) t).getDetails()).containsEntry("champ", "COURRIEL");
		}
		assertThat(champ(enregistrer(COL, t -> regler(t, "COURRIEL", "  Courriel pro  ", null, null)), "COURRIEL").enTete())
				.isEqualTo("Courriel pro");
	}

	@Test
	@DisplayName("T1-11 : en-tête de Poste « nom » (en-tête de Nom) ou « Mail » (synonyme actif de Courriel) -> 400 LIBELLE_EN_DOUBLE qui nomme le champ")
	void libellesEnDouble() {
		Throwable t = echec(() -> enregistrer(COL, tab -> regler(tab, "POSTE", "nom", null, null)));
		erreur(t, 400, "LIBELLE_EN_DOUBLE");
		assertThat(t.getMessage()).contains("déjà reconnu pour le champ Nom");
		assertThat(((ReferentielMetierException) t).getDetails()).containsEntry("champ", "POSTE");
		Throwable t2 = echec(() -> enregistrer(COL, tab -> regler(tab, "POSTE", "Mail", null, null)));
		erreur(t2, 400, "LIBELLE_EN_DOUBLE");
		assertThat(t2.getMessage()).contains("déjà reconnu pour le champ Courriel");
		// deux en-têtes de même forme dans le même tableau
		erreur(echec(() -> enregistrer(COL, tab -> {
			regler(tab, "POSTE", "Intitulé", null, null);
			regler(tab, "FONCTION", "INTITULÉ", null, null);
		})), 400, "LIBELLE_EN_DOUBLE");
		// un synonyme inactif d'un autre champ ne bloque pas
		Parametrage p = service.lire(COL);
		service.desactiver(COL, synonyme(p, "COURRIEL", "mail").id(), p.version(), DRH01);
		assertThat(champ(enregistrer(COL, tab -> regler(tab, "POSTE", "Mail", null, null)), "POSTE").enTete()).isEqualTo("Mail");
	}

	@Test
	@DisplayName("ADR § 5 : échange d'en-têtes entre deux champs dans un même enregistrement -> 400 LIBELLE_EN_DOUBLE (synonyme actif de l'autre champ)")
	void echangeDenTetes() {
		erreur(echec(() -> enregistrer(COL, t -> {
			regler(t, "PRENOM", "Nom", null, null);
			regler(t, "NOM", "Prénom", null, null);
		})), 400, "LIBELLE_EN_DOUBLE");
	}

	@Test
	@DisplayName("T1-3 : enregistrement identique -> 200, version et journal inchangés ; version périmée quand même refusée")
	void ecritureSansEffet() {
		Parametrage p = service.lire(COL);
		Parametrage apres = service.enregistrer(COL, new EnregistrerRequest(p.version(), tableau(COL)), DRH01);
		assertThat(apres.version()).isEqualTo(p.version());
		assertThat(journal(COL)).isEmpty();
		erreur(echec(() -> service.enregistrer(COL, new EnregistrerRequest(99L, tableau(COL)), DRH01)), 409,
				"PARAMETRE_MODIFIE_ENTRE_TEMPS");
	}

	@Test
	@DisplayName("T1-22 : le changement d'ordre ne produit qu'une ligne « Ordre » (sans champ) avec la liste des en-têtes ; l'auteur vient de l'appelant, la date de l'horloge")
	void journalDeLOrdre() {
		enregistrer(COL, t -> {
			ChampRequest unite = ch(t, "UNITE");
			t.remove(unite);
			t.add(1, unite);
		});
		List<LigneJournal> j = journal(COL);
		assertThat(j).hasSize(1);
		assertThat(j.get(0).attribut()).isEqualTo("ORDRE");
		assertThat(j.get(0).attributLibelle()).isEqualTo("Ordre");
		assertThat(j.get(0).champ()).isNull();
		assertThat(j.get(0).champLibelle()).isEqualTo("Tous les champs");
		assertThat(j.get(0).avant()).startsWith("Matricule, Prénom, Nom, Courriel, Unité");
		assertThat(j.get(0).apres()).startsWith("Matricule, Unité, Prénom, Nom, Courriel");
		assertThat(j.get(0).auteurNom()).isEqualTo("Amel Trabelsi");
		assertThat(j.get(0).horodatage()).isEqualTo(Instant.parse("2026-11-13T10:00:00Z"));
	}

	// ─── P3 : ajouter un synonyme ──────────────────────────────────────────────

	@Test
	@DisplayName("T1-12 : « Mle » sur Matricule -> 201 forme « mle » origine RH ; « MLE » -> 400 SYNONYME_EN_DOUBLE ; « M.L.E. » (forme « m l e ») -> accepté")
	void ajouterSynonyme() {
		Parametrage p = service.ajouterSynonyme(COL, "MATRICULE", new SynonymeRequest("Mle", 0L), DRH01);
		SynonymeVue s = synonyme(p, "MATRICULE", "mle");
		assertThat(s.origine()).isEqualTo("RH");
		assertThat(s.origineLibelle()).isEqualTo("Ajouté par la RH");
		assertThat(s.texte()).isEqualTo("Mle");
		assertThat(p.version()).isEqualTo(1);
		Throwable t = echec(() -> service.ajouterSynonyme(COL, "MATRICULE", new SynonymeRequest("MLE", 1L), DRH01));
		erreur(t, 400, "SYNONYME_EN_DOUBLE");
		assertThat(version(COL)).isEqualTo(1);
		Parametrage p2 = service.ajouterSynonyme(COL, "MATRICULE", new SynonymeRequest("M.L.E.", 1L), DRH01);
		assertThat(synonyme(p2, "MATRICULE", "m l e")).isNotNull();
		assertThat(journal(COL)).filteredOn(l -> "SYNONYME_AJOUTE".equals(l.attribut())).hasSize(2)
				.anySatisfy(l -> {
					assertThat(l.apres()).isEqualTo("Mle");
					assertThat(l.champLibelle()).isEqualTo("Matricule");
				});
	}

	@Test
	@DisplayName("T1-13 : « E-mail » sur Prénom -> 400 déjà reconnu pour Courriel ; « Mle » sur Matricule des soldes alors qu'il existe côté collaborateurs -> 201 (types indépendants)")
	void doublonEntreChampsEtTypes() {
		Throwable t = echec(() -> service.ajouterSynonyme(COL, "PRENOM", new SynonymeRequest("E-mail", 0L), DRH01));
		erreur(t, 400, "SYNONYME_EN_DOUBLE");
		assertThat(t.getMessage()).contains("déjà reconnu pour le champ Courriel");
		service.ajouterSynonyme(COL, "MATRICULE", new SynonymeRequest("Mle", 0L), DRH01);
		Parametrage p = service.ajouterSynonyme(SOL, "MATRICULE", new SynonymeRequest("Mle", 0L), DRH01);
		assertThat(synonyme(p, "MATRICULE", "mle")).isNotNull();
	}

	@Test
	@DisplayName("T1-13 : le synonyme ne peut pas reprendre l'en-tête réglé d'un autre champ")
	void doublonAvecUnEnTeteRegle() {
		enregistrer(COL, t -> regler(t, "POSTE", "Intitulé du métier", null, null));
		erreur(echec(() -> service.ajouterSynonyme(COL, "FONCTION", new SynonymeRequest("intitulé du métier", 1L), DRH01)), 400,
				"SYNONYME_EN_DOUBLE");
	}

	@Test
	@DisplayName("T1-14 : « N° » (forme d'un caractère), « 31/12 » (forme vide) ou 81 caractères -> 400 SYNONYME_INVALIDE ; texte absent -> CORPS_INVALIDE ; version absente -> VERSION_OBLIGATOIRE")
	void synonymeInvalide() {
		for (String mauvais : List.of("N°", "31/12", "x".repeat(81), " a ")) {
			erreur(echec(() -> service.ajouterSynonyme(COL, "MATRICULE", new SynonymeRequest(mauvais, 0L), DRH01)), 400,
					"SYNONYME_INVALIDE");
		}
		erreur(echec(() -> service.ajouterSynonyme(COL, "MATRICULE", new SynonymeRequest(null, 0L), DRH01)), 400, "CORPS_INVALIDE");
		erreur(echec(() -> service.ajouterSynonyme(COL, "MATRICULE", new SynonymeRequest("Mle", null), DRH01)), 400, "VERSION_OBLIGATOIRE");
		erreur(echec(() -> service.ajouterSynonyme(COL, "MATRICULE", null, DRH01)), 400, "CORPS_INVALIDE");
	}

	@Test
	@DisplayName("T1-15 : « Solde au 31/12 » sur Solde -> 400 SYNONYME_EN_DOUBLE (forme « solde au » déjà synonyme par défaut)")
	void synonymeParDefautDejaPresent() {
		erreur(echec(() -> service.ajouterSynonyme(SOL, "SOLDE", new SynonymeRequest("Solde au 31/12", 0L), DRH01)), 400,
				"SYNONYME_EN_DOUBLE");
	}

	@Test
	@DisplayName("T1-16 : 31e synonyme d'un champ (actifs et inactifs comptés) -> 422 SYNONYMES_MAX")
	void trenteSynonymesAuPlus() {
		enregistrer(COL, t -> regler(t, "FONCTION", "Emploi", null, null)); // rien n'est ajouté : « fonction » est déjà synonyme
		int existants = champ(service.lire(COL), "FONCTION").synonymes().size();
		long v = version(COL);
		for (int i = existants; i < 30; i++) {
			String texte = "Zz" + (char) ('a' + i / 26) + (char) ('a' + i % 26);
			service.ajouterSynonyme(COL, "FONCTION", new SynonymeRequest(texte, v++), DRH01);
		}
		assertThat(champ(service.lire(COL), "FONCTION").synonymes()).hasSize(30);
		long courante = v;
		erreur(echec(() -> service.ajouterSynonyme(COL, "FONCTION", new SynonymeRequest("Encore un", courante), DRH01)), 422,
				"SYNONYMES_MAX");
		// le renommage qui devrait conserver l'ancien en-tête « Emploi » comme 31e synonyme est refusé de même
		erreur(echec(() -> enregistrer(COL, t -> regler(t, "FONCTION", "Métier", null, null))), 422, "SYNONYMES_MAX");
		assertThat(version(COL)).isEqualTo(courante);
	}

	@Test
	@DisplayName("T1-24 : champ inconnu sur P3 -> 404 CHAMP_INCONNU (y compris le champ de l'autre type)")
	void champInconnu() {
		erreur(echec(() -> service.ajouterSynonyme(COL, "TELEPHONE", new SynonymeRequest("Tél", 0L), DRH01)), 404, "CHAMP_INCONNU");
		erreur(echec(() -> service.ajouterSynonyme(COL, "SOLDE", new SynonymeRequest("Reste", 0L), DRH01)), 404, "CHAMP_INCONNU");
	}

	// ─── P4, P5 : désactiver, réactiver, supprimer ─────────────────────────────

	@Test
	@DisplayName("T1-17 : désactiver « service » d'Unité -> 200 ; « Service » ajouté à Affectation -> 201 ; réactiver « service » sur Unité -> 400 SYNONYME_EN_DOUBLE")
	void desactiverPuisReactiverEnConflit() {
		Parametrage p = service.lire(COL);
		UUID service = synonyme(p, "UNITE", "service").id();
		Parametrage p1 = this.service.desactiver(COL, service, p.version(), DRH01);
		assertThat(synonyme(p1, "UNITE", "service").actif()).isFalse();
		Parametrage p2 = this.service.ajouterSynonyme(COL, "AFFECTATION", new SynonymeRequest("Service", p1.version()), DRH01);
		assertThat(synonyme(p2, "AFFECTATION", "service").actif()).isTrue();
		long v = p2.version();
		erreur(echec(() -> this.service.reactiver(COL, service, v, DRH01)), 400, "SYNONYME_EN_DOUBLE");
		assertThat(version(COL)).isEqualTo(v);
	}

	@Test
	@DisplayName("T1-17 : réactiver un synonyme libre -> actif ; désactiver puis réactiver déjà dans cet état -> aucune version, aucun journal")
	void reactiverEtSansEffet() {
		Parametrage p = service.lire(COL);
		UUID id = synonyme(p, "COURRIEL", "mail").id();
		Parametrage p1 = service.desactiver(COL, id, p.version(), DRH01);
		Parametrage inchange = service.desactiver(COL, id, p1.version(), DRH01);
		assertThat(inchange.version()).isEqualTo(p1.version());
		Parametrage p2 = service.reactiver(COL, id, p1.version(), DRH01);
		assertThat(synonyme(p2, "COURRIEL", "mail").actif()).isTrue();
		assertThat(service.reactiver(COL, id, p2.version(), DRH01).version()).isEqualTo(p2.version());
		assertThat(journal(COL)).extracting(LigneJournal::attribut).containsExactlyInAnyOrder("SYNONYME_DESACTIVE", "SYNONYME_REACTIVE");
		erreur(echec(() -> service.desactiver(COL, id, 0L, DRH01)), 409, "PARAMETRE_MODIFIE_ENTRE_TEMPS");
	}

	@Test
	@DisplayName("T1-18 : supprimer un synonyme DEFAUT -> 409 PARAMETRE_SYSTEME ; un synonyme RH -> supprimé, version +1")
	void supprimer() {
		Parametrage p = service.lire(COL);
		erreur(echec(() -> service.supprimer(COL, synonyme(p, "COURRIEL", "mail").id(), p.version(), DRH01)), 409, "PARAMETRE_SYSTEME");
		// l'état 409 passe avant la comparaison de version
		erreur(echec(() -> service.supprimer(COL, synonyme(p, "COURRIEL", "mail").id(), 42L, DRH01)), 409, "PARAMETRE_SYSTEME");
		Parametrage p1 = service.ajouterSynonyme(COL, "MATRICULE", new SynonymeRequest("Mle", p.version()), DRH01);
		UUID rh = synonyme(p1, "MATRICULE", "mle").id();
		service.supprimer(COL, rh, p1.version(), DRH01);
		Parametrage p2 = service.lire(COL);
		assertThat(p2.version()).isEqualTo(p1.version() + 1);
		assertThat(champ(p2, "MATRICULE").synonymes()).extracting(SynonymeVue::formeNormalisee).doesNotContain("mle");
		assertThat(journal(COL)).anySatisfy(l -> {
			assertThat(l.attribut()).isEqualTo("SYNONYME_SUPPRIME");
			assertThat(l.avant()).isEqualTo("Mle");
		});
		erreur(echec(() -> service.supprimer(COL, rh, 0L, DRH01)), 404, "SYNONYME_INTROUVABLE");
	}

	@Test
	@DisplayName("T1-24 : synonyme inconnu ou d'un autre type -> 404 SYNONYME_INTROUVABLE (P4 et P5) ; version absente -> 400 VERSION_OBLIGATOIRE")
	void synonymeIntrouvable() {
		erreur(echec(() -> service.desactiver(COL, UUID.randomUUID(), 0L, DRH01)), 404, "SYNONYME_INTROUVABLE");
		erreur(echec(() -> service.supprimer(COL, UUID.randomUUID(), 0L, DRH01)), 404, "SYNONYME_INTROUVABLE");
		UUID autreType = synonyme(service.lire(SOL), "SOLDE", "solde").id();
		erreur(echec(() -> service.desactiver(COL, autreType, 0L, DRH01)), 404, "SYNONYME_INTROUVABLE");
		erreur(echec(() -> service.supprimer(COL, autreType, 0L, DRH01)), 404, "SYNONYME_INTROUVABLE");
		UUID mail = synonyme(service.lire(COL), "COURRIEL", "mail").id();
		erreur(echec(() -> service.desactiver(COL, mail, null, DRH01)), 400, "VERSION_OBLIGATOIRE");
		erreur(echec(() -> service.supprimer(COL, mail, null, DRH01)), 400, "VERSION_OBLIGATOIRE");
		erreur(echec(() -> service.reinitialiser(COL, null, DRH01)), 400, "VERSION_OBLIGATOIRE");
	}

	// ─── Version (T1-20) ───────────────────────────────────────────────────────

	@Test
	@DisplayName("T1-20 : deux RH lisent la version 5 ; l'un enregistre (version 6) ; l'autre, avec la 5, reçoit 409 sur P2, P3, P4, P5 et P6 sans rien modifier")
	void conflitDeVersion() {
		// amener la version à 5 par cinq écritures effectives
		for (int i = 0; i < 5; i++) {
			service.ajouterSynonyme(COL, "MATRICULE", new SynonymeRequest("Mle" + (char) ('a' + i) + "x", (long) i), DRH01);
		}
		assertThat(version(COL)).isEqualTo(5);
		Parametrage lu = service.lire(COL);
		UUID mail = synonyme(lu, "COURRIEL", "mail").id();
		UUID rh = synonyme(lu, "MATRICULE", "mlebx").id();
		service.ajouterSynonyme(COL, "NOM", new SynonymeRequest("Patronyme", 5L), DRH01); // RH02 passe en premier
		assertThat(version(COL)).isEqualTo(6);
		int journalAvant = journal(COL).size();

		List<ChampRequest> t = tableau(COL);
		regler(t, "COURRIEL", "E-mail", null, null);
		String etat = service.lire(COL).champs().toString();
		erreur(echec(() -> service.enregistrer(COL, new EnregistrerRequest(5L, t), DRH01)), 409, "PARAMETRE_MODIFIE_ENTRE_TEMPS");
		erreur(echec(() -> service.ajouterSynonyme(COL, "NOM", new SynonymeRequest("Patro", 5L), DRH01)), 409, "PARAMETRE_MODIFIE_ENTRE_TEMPS");
		erreur(echec(() -> service.desactiver(COL, mail, 5L, DRH01)), 409, "PARAMETRE_MODIFIE_ENTRE_TEMPS");
		erreur(echec(() -> service.reactiver(COL, mail, 5L, DRH01)), 409, "PARAMETRE_MODIFIE_ENTRE_TEMPS");
		erreur(echec(() -> service.supprimer(COL, rh, 5L, DRH01)), 409, "PARAMETRE_MODIFIE_ENTRE_TEMPS");
		erreur(echec(() -> service.reinitialiser(COL, 5L, DRH01)), 409, "PARAMETRE_MODIFIE_ENTRE_TEMPS");

		assertThat(version(COL)).isEqualTo(6);
		assertThat(journal(COL)).hasSize(journalAvant);
		assertThat(service.lire(COL).champs().toString()).isEqualTo(etat);
	}

	@Test
	@DisplayName("P6 : un synonyme par défaut dont la forme est l'en-tête d'un champ personnalisé actif reste inactif (journalisé) ; aucune forme ne désigne deux champs")
	void reinitialiserNeReactivePasUnSynonymeEnConflitAvecUnChampPerso() {
		UUID nomDeFamille = synonyme(service.lire(COL), "NOM", "nom de famille").id();
		service.desactiver(COL, nomDeFamille, version(COL), DRH01);
		// le champ personnalisé prend l'en-tête « Nom de famille » (créé ici directement, hors contrôle de doublon)
		champPersoRepository.save(new com.hr.referentiel.champsperso.ChampPerso("COLLABORATEURS", "PERSO_NOM_DE_FAMILLE",
				"Nom de famille", "Nom d'usage", com.hr.referentiel.champsperso.TypeValeurPerso.TEXTE, false,
				Instant.parse("2026-11-13T09:00:00Z"), "Amel Trabelsi"));
		champRepository.save(new ImportChampRegle("COLLABORATEURS", "PERSO_NOM_DE_FAMILLE", "Nom de famille", true,
				ExigenceChamp.FACULTATIF, 50));

		Parametrage p = service.reinitialiser(COL, version(COL), DRH01);

		assertThat(synonyme(p, "NOM", "nom de famille").actif()).isFalse();
		assertThat(synonyme(p, "NOM", "nom").actif()).isTrue();
		Map<String, Long> proprietaires = new java.util.HashMap<>();
		p.champs().forEach(c -> {
			if (c.dansModele()) {
				proprietaires.merge(ReconnaissanceEnTetes.normaliser(c.enTete()), 1L, Long::sum);
			}
			c.synonymes().stream().filter(SynonymeVue::actif)
					.forEach(sy -> proprietaires.merge(sy.formeNormalisee(), 1L, Long::sum));
		});
		assertThat(proprietaires.get("nom de famille")).as("une seule appartenance").isEqualTo(1L);
	}

	@Test
	@DisplayName("P6 : champ personnalisé renommé (ancien en-tête devenu synonyme RH) -> la réinitialisation remet son en-tête puis laisse inactif le synonyme par défaut en conflit")
	void reinitialiserApresRenommageDuChampPerso() {
		service.desactiver(COL, synonyme(service.lire(COL), "NOM", "nom de famille").id(), version(COL), DRH01);
		champPersoRepository.save(new com.hr.referentiel.champsperso.ChampPerso("COLLABORATEURS", "PERSO_NOM_DE_FAMILLE",
				"Nom de famille", "Nom d'usage", com.hr.referentiel.champsperso.TypeValeurPerso.TEXTE, false,
				Instant.parse("2026-11-13T09:00:00Z"), "Amel Trabelsi"));
		champRepository.save(new ImportChampRegle("COLLABORATEURS", "PERSO_NOM_DE_FAMILLE", "NDF", true,
				ExigenceChamp.FACULTATIF, 50));

		Parametrage p = service.reinitialiser(COL, version(COL), DRH01);

		assertThat(champ(p, "PERSO_NOM_DE_FAMILLE").enTete()).isEqualTo("Nom de famille");
		assertThat(synonyme(p, "NOM", "nom de famille").actif()).isFalse();
	}

	@Test
	@DisplayName("P6 : synonyme par défaut actif en conflit avec un champ personnalisé -> désactivé et journalisé « Synonyme désactivé »")
	void reinitialiserDesactiveEtJournaliseLeSynonymeEnConflit() {
		// état dégradé : le champ perso porte déjà l'en-tête, le synonyme par défaut de NOM est resté actif
		champPersoRepository.save(new com.hr.referentiel.champsperso.ChampPerso("COLLABORATEURS", "PERSO_NOM_DE_FAMILLE",
				"Nom de famille", "Nom d'usage", com.hr.referentiel.champsperso.TypeValeurPerso.TEXTE, false,
				Instant.parse("2026-11-13T09:00:00Z"), "Amel Trabelsi"));
		champRepository.save(new ImportChampRegle("COLLABORATEURS", "PERSO_NOM_DE_FAMILLE", "Nom de famille", true,
				ExigenceChamp.FACULTATIF, 50));
		assertThat(synonyme(service.lire(COL), "NOM", "nom de famille").actif()).isTrue();

		Parametrage p = service.reinitialiser(COL, version(COL), DRH01);

		assertThat(synonyme(p, "NOM", "nom de famille").actif()).isFalse();
		assertThat(journal(COL)).extracting(LigneJournal::attribut).contains("SYNONYME_DESACTIVE");
	}

	@Test
	@DisplayName("ADR § 5 : toute écriture effective incrémente la version d'exactement 1 (P2, P3, P4, P5, P6)")
	void versionPlusUn() {
		long v = version(COL);
		Parametrage p = enregistrer(COL, t -> regler(t, "POSTE", "Intitulé", null, null));
		assertThat(p.version()).isEqualTo(v + 1);
		Parametrage p2 = service.ajouterSynonyme(COL, "NOM", new SynonymeRequest("Patronyme", p.version()), DRH01);
		assertThat(p2.version()).isEqualTo(v + 2);
		Parametrage p3 = service.desactiver(COL, synonyme(p2, "COURRIEL", "mail").id(), p2.version(), DRH01);
		assertThat(p3.version()).isEqualTo(v + 3);
		service.supprimer(COL, synonyme(p3, "NOM", "patronyme").id(), p3.version(), DRH01);
		assertThat(version(COL)).isEqualTo(v + 4);
		assertThat(service.reinitialiser(COL, v + 4, DRH01).version()).isEqualTo(v + 5);
	}

	// ─── P6 ────────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("T1-21 : réinitialiser collaborateurs -> valeurs par défaut, synonymes DEFAUT tous actifs, synonymes RH supprimés ; soldes inchangé ; une seule ligne de journal « Réinitialisation » avec le nombre supprimé")
	void reinitialiser() {
		Parametrage p = service.lire(COL);
		service.desactiver(COL, synonyme(p, "UNITE", "service").id(), 0L, DRH01);
		service.ajouterSynonyme(COL, "MATRICULE", new SynonymeRequest("Mle", 1L), DRH01);
		service.ajouterSynonyme(COL, "NOM", new SynonymeRequest("Patronyme", 2L), DRH01);
		enregistrer(COL, t -> {
			regler(t, "COURRIEL", "E-mail", null, "TOUJOURS");
			regler(t, "POSTE", null, false, null);
			ChampRequest unite = ch(t, "UNITE");
			t.remove(unite);
			t.add(1, unite);
		});
		service.ajouterSynonyme(SOL, "MOTIF", new SynonymeRequest("Justification", 0L), DRH01);
		long vSoldes = version(SOL);
		int journalAvant = journal(COL).size();
		long v = version(COL);

		Parametrage r = service.reinitialiser(COL, v, DRH01);

		assertThat(r.version()).isEqualTo(v + 1);
		assertThat(r.champs()).extracting(ChampVue::code).containsExactly("MATRICULE", "PRENOM", "NOM", "COURRIEL", "UNITE",
				"POSTE", "FONCTION", "QUALITE", "AFFECTATION", "DATE_RECRUTEMENT", "STATUT", "PROFIL_ACCES", "FAMILLE_METIER",
				"NIVEAU_SENIORITE");
		assertThat(r.champs()).allMatch(ChampVue::dansModele);
		assertThat(champ(r, "COURRIEL").enTete()).isEqualTo("Courriel");
		assertThat(champ(r, "COURRIEL").exigence()).isEqualTo("FACULTATIF");
		assertThat(r.champs().stream().flatMap(c -> c.synonymes().stream())).allMatch(s -> s.actif() && "DEFAUT".equals(s.origine()));
		assertThat(synonyme(r, "UNITE", "service").actif()).isTrue();
		assertThat(version(SOL)).isEqualTo(vSoldes);
		assertThat(champ(service.lire(SOL), "MOTIF").synonymes()).extracting(SynonymeVue::formeNormalisee).contains("justification");
		List<LigneJournal> apres = journal(COL);
		assertThat(apres).hasSize(journalAvant + 1);
		assertThat(apres.stream().filter(l -> "REINITIALISATION".equals(l.attribut()))).singleElement().satisfies(l -> {
			assertThat(l.avant()).isEqualTo("2 synonymes ajoutés par la RH supprimés");
			assertThat(l.apres()).isEqualTo("Valeurs par défaut");
			assertThat(l.champ()).isNull();
			assertThat(l.champLibelle()).isEqualTo("Tous les champs");
		});
	}

	// ─── Journal (T1-22) ───────────────────────────────────────────────────────

	@Test
	@DisplayName("T1-22 : le journal liste chaque modification avec auteur, champ, attribut et avant -> après en clair (jamais CREATION), du plus récent au plus ancien, paginé")
	void journalEnClair() {
		enregistrer(COL, t -> {
			regler(t, "COURRIEL", "E-mail", null, "CREATION");
			regler(t, "POSTE", null, false, null);
		});
		List<LigneJournal> j = journal(COL);
		assertThat(j).extracting(LigneJournal::attribut).containsExactlyInAnyOrder("EN_TETE", "EXIGENCE", "DANS_MODELE");
		LigneJournal enTete = j.stream().filter(l -> "EN_TETE".equals(l.attribut())).findFirst().orElseThrow();
		assertThat(enTete.attributLibelle()).isEqualTo("En-tête");
		assertThat(enTete.champ()).isEqualTo("COURRIEL");
		assertThat(enTete.champLibelle()).isEqualTo("Courriel");
		assertThat(enTete.avant()).isEqualTo("Courriel");
		assertThat(enTete.apres()).isEqualTo("E-mail");
		LigneJournal exigence = j.stream().filter(l -> "EXIGENCE".equals(l.attribut())).findFirst().orElseThrow();
		assertThat(exigence.avant()).isEqualTo("Facultatif");
		assertThat(exigence.apres()).isEqualTo("Obligatoire à la création");
		LigneJournal modele = j.stream().filter(l -> "DANS_MODELE".equals(l.attribut())).findFirst().orElseThrow();
		assertThat(modele.champLibelle()).isEqualTo("Poste");
		assertThat(modele.avant()).isEqualTo("Oui");
		assertThat(modele.apres()).isEqualTo("Non");
		assertThat(j).allSatisfy(l -> assertThat(l.auteurNom()).isEqualTo("Amel Trabelsi"));
		assertThat(journalRepository.findAll()).allSatisfy(l -> {
			assertThat(l.getVersion()).isEqualTo(1);
			assertThat(l.getAuteurCompteId()).isEqualTo(DRH01.compteId());
		});
		assertThat(service.lireJournal(COL, 0, 2).contenu()).hasSize(2);
		assertThat(service.lireJournal(COL, 0, 2).total()).isEqualTo(3);
		erreur(echec(() -> service.lireJournal(COL, 0, 0)), 400, "CORPS_INVALIDE");
		erreur(echec(() -> service.lireJournal(COL, 0, 101)), 400, "CORPS_INVALIDE");
		assertThat(service.lireJournal(SOL, 0, 20).contenu()).isEmpty();
	}
}
