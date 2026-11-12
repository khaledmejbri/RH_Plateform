package com.hr.referentiel.service;

import com.hr.referentiel.config.CacheConfig;
import com.hr.referentiel.config.ParametresAmorceRunner;
import com.hr.referentiel.config.ReferentielEvenementsProperties;
import com.hr.referentiel.dto.ParametreDto.UtiliserResponse;
import com.hr.referentiel.dto.ParametreDto.ValeurRequest;
import com.hr.referentiel.dto.ParametreDto.ValeurResponse;
import com.hr.referentiel.dto.ParametreDto.Reference;
import com.hr.referentiel.entity.ParametreJournal;
import com.hr.referentiel.entity.ParametreValeur;
import com.hr.referentiel.repository.ParametreJournalRepository;
import com.hr.referentiel.repository.ParametreValeurRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Ticket 3B : règles communes du paramétrage (D-R02) sur une vraie couche JPA (H2). Les transactions sont réelles
 * (commit), donc l'invalidation du cache après commit est exercée.
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:param3b;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ParametreServiceTest.Config.class, ParametreService.class, ParametresCache.class})
class ParametreServiceTest {

	@Configuration
	@EnableJpaRepositories(basePackageClasses = ParametreValeurRepository.class, excludeFilters =
	@org.springframework.context.annotation.ComponentScan.Filter(type = FilterType.REGEX,
			pattern = "com\\.hr\\.referentiel\\.repository\\.(?!Parametre).*"))
	static class Config {
		@Bean
		PersistenceManagedTypes managedTypes() {
			return PersistenceManagedTypes.of(ParametreValeur.class.getName(), ParametreJournal.class.getName());
		}

		@Bean
		CacheManager cacheManager() {
			CaffeineCacheManager m = new CaffeineCacheManager();
			m.registerCustomCache(CacheConfig.CACHE_PARAMETRES,
					com.github.benmanes.caffeine.cache.Caffeine.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).build());
			return m;
		}

		@Bean
		JsonMapper jsonMapper() {
			return JsonMapper.builder().build();
		}
	}

	@Autowired
	ParametreService service;
	@Autowired
	ParametreValeurRepository repository;
	@Autowired
	ParametreJournalRepository journalRepository;
	@Autowired
	ParametresCache cache;
	@Autowired
	org.springframework.jdbc.core.JdbcTemplate jdbc;
	@Autowired
	org.springframework.transaction.PlatformTransactionManager tm;
	@MockitoBean
	ParametreUtilisationCompteur compteur;

	private final AuteurParametre drh = new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi");

	@BeforeEach
	void init() {
		journalRepository.deleteAll();
		repository.deleteAll();
		cache.invalider();
		new ParametresAmorceRunner(jdbc, tm, new ReferentielEvenementsProperties(), JsonMapper.builder().build())
				.amorcer();
		cache.invalider();
	}

	private static ValeurRequest doc(String libelle, Integer sla) {
		return new ValeurRequest(libelle, null, null, null, sla, null, null);
	}

	private static ReferentielMetierException erreur(Throwable t) {
		assertThat(t).isInstanceOf(ReferentielMetierException.class);
		return (ReferentielMetierException) t;
	}

	@Test
	@DisplayName("création d'un type de document : 201 logique, code généré, SLA conservé, visible dans l'instantané")
	void creerTypeDocument() {
		ValeurResponse v = service.creer("TYPE_DOCUMENT", doc("Attestation de stage", 72), drh);

		assertThat(v.code()).isEqualTo("ATTESTATION_DE_STAGE");
		assertThat(v.slaHeures()).isEqualTo(72);
		assertThat(v.actif()).isTrue();
		assertThat(v.systeme()).isFalse();
		assertThat(service.libelle("TYPE_DOCUMENT", "ATTESTATION_DE_STAGE")).isEqualTo("Attestation de stage");
	}

	@Test
	@DisplayName("modifier le SLA : la valeur change (les demandes déjà déposées gardent leur copie)")
	void modifierSla() {
		ValeurResponse v = service.creer("TYPE_DOCUMENT", doc("Attestation de stage", 72), drh);

		ValeurResponse maj = service.modifier("TYPE_DOCUMENT", v.code(), doc(null, 24), v.version(), drh);

		assertThat(maj.slaHeures()).isEqualTo(24);
		assertThat(service.instantane().vue("TYPE_DOCUMENT", v.code()).orElseThrow().slaHeures()).isEqualTo(24);
	}

	@Test
	@DisplayName("lot 3 : libelleOuInconnu = libellé courant (même désactivé), « Valeur non reconnue » pour un code inconnu")
	void libelleOuInconnu() {
		ValeurResponse v = service.creer("TYPE_CONGE", doc("Congé sabbatique", null), drh);
		service.desactiver("TYPE_CONGE", v.code(), v.version(), drh);

		assertThat(service.libelleOuInconnu("TYPE_CONGE", v.code())).isEqualTo("Congé sabbatique");
		assertThat(service.libelleOuInconnu("TYPE_CONGE", "N_EXISTE_PAS")).isEqualTo("Valeur non reconnue");
		assertThat(service.libelleOuInconnu("TYPE_CONGE", null)).isNull();
	}

	@Test
	@DisplayName("lot 3 : un PUT sans changement ne crée pas d'entrée de journal et ne change pas la version")
	void putSansChangementNeJournalisePas() {
		ValeurResponse v = service.creer("TYPE_DOCUMENT", doc("Attestation de stage", 72), drh);
		long avant = journalRepository.count();

		ValeurResponse maj = service.modifier("TYPE_DOCUMENT", v.code(), doc("Attestation de stage", 72),
				v.version(), drh);

		assertThat(journalRepository.count()).isEqualTo(avant);
		assertThat(maj.version()).isEqualTo(v.version());
	}

	@Test
	@DisplayName("code de la valeur immuable : un PUT ne le change jamais, renommer garde le code")
	void renommerGardeLeCode() {
		ValeurResponse v = service.creer("TYPE_DOCUMENT", doc("Attestation de stage", 72), drh);

		ValeurResponse maj = service.modifier("TYPE_DOCUMENT", v.code(), doc("Attestation de stage 2026", 72),
				v.version(), drh);

		assertThat(maj.code()).isEqualTo(v.code());
		assertThat(maj.libelle()).isEqualTo("Attestation de stage 2026");
	}

	@Test
	@DisplayName("renommage d'un statut visible immédiatement dans l'instantané (invalidation après commit)")
	void renommerStatut() {
		ValeurResponse avant = service.valeurs("STATUT_DEMANDE").stream()
				.filter(x -> x.code().equals("EN_VALIDATION_RRH")).findFirst().orElseThrow();
		assertThat(service.libelle("STATUT_DEMANDE", "EN_VALIDATION_RRH")).isEqualTo("En attente de validation RH");

		service.modifier("STATUT_DEMANDE", "EN_VALIDATION_RRH", doc("En attente de la RH", null), avant.version(), drh);

		assertThat(service.libelle("STATUT_DEMANDE", "EN_VALIDATION_RRH")).isEqualTo("En attente de la RH");
	}

	@Test
	@DisplayName("POST sur un statut : 405 PARAMETRE_COMPORTEMENT ; DELETE et désactivation aussi")
	void comportementNonCreableNiSupprimable() {
		assertThat(erreur(catchThrowable(() -> service.creer("STATUT_DEMANDE", doc("Nouveau statut", null), drh)))
				.getHttpStatus()).isEqualTo(405);
		assertThat(erreur(catchThrowable(() -> service.supprimer("STATUT_DEMANDE", "SOUMISE", null, drh)))
				.getHttpStatus()).isEqualTo(405);
		assertThat(erreur(catchThrowable(() -> service.desactiver("STATUT_DEMANDE", "SOUMISE", null, drh)))
				.getCode()).isEqualTo("PARAMETRE_COMPORTEMENT");
	}

	@Test
	@DisplayName("référentiel inconnu : 404 REFERENTIEL_INCONNU ; valeur inconnue : 404")
	void inconnus() {
		ReferentielMetierException e = erreur(catchThrowable(() -> service.valeurs("INEXISTANT")));
		assertThat(e.getHttpStatus()).isEqualTo(404);
		assertThat(e.getCode()).isEqualTo("REFERENTIEL_INCONNU");
		assertThat(erreur(catchThrowable(() -> service.modifier("TYPE_DOCUMENT", "NOPE", doc("Truc", 5), 0L, drh)))
				.getHttpStatus()).isEqualTo(404);
	}

	@Test
	@DisplayName("libellé en double (casse, accents, espaces) : 400 LIBELLE_EN_DOUBLE, même contre une valeur inactive")
	void doublon() {
		ValeurResponse v = service.creer("TYPE_DOCUMENT", doc("Attestation de stage", 72), drh);
		assertThat(erreur(catchThrowable(() -> service.creer("TYPE_DOCUMENT", doc("attestation  de STAGE", 72), drh)))
				.getCode()).isEqualTo("LIBELLE_EN_DOUBLE");
		service.desactiver("TYPE_DOCUMENT", v.code(), v.version(), drh);
		ReferentielMetierException e = erreur(catchThrowable(
				() -> service.creer("TYPE_DOCUMENT", doc("Attestation de stage", 72), drh)));
		assertThat(e.getHttpStatus()).isEqualTo(400);
		assertThat(e.getCode()).isEqualTo("LIBELLE_EN_DOUBLE");
	}

	@Test
	@DisplayName("bornes du libellé : 1 caractère et 81 caractères refusés, 80 accepté, espaces seuls refusés")
	void bornesLibelle() {
		assertThat(erreur(catchThrowable(() -> service.creer("TYPE_DOCUMENT", doc("A", 5), drh))).getCode())
				.isEqualTo("LIBELLE_INVALIDE");
		assertThat(erreur(catchThrowable(() -> service.creer("TYPE_DOCUMENT", doc("   ", 5), drh))).getHttpStatus())
				.isEqualTo(400);
		assertThat(erreur(catchThrowable(() -> service.creer("TYPE_DOCUMENT", doc("x".repeat(81), 5), drh)))
				.getHttpStatus()).isEqualTo(400);
		ValeurResponse ok = service.creer("TYPE_DOCUMENT", doc("y".repeat(80), 5), drh);
		assertThat(ok.libelle()).hasSize(80);
		assertThat(ok.code()).hasSizeLessThanOrEqualTo(40);
	}

	@Test
	@DisplayName("bornes du SLA : 0 et 721 refusés, 1 et 720 acceptés, absent refusé à la création")
	void bornesSla() {
		assertThat(erreur(catchThrowable(() -> service.creer("TYPE_DOCUMENT", doc("Type zero", 0), drh))).getCode())
				.isEqualTo("SLA_INVALIDE");
		assertThat(erreur(catchThrowable(() -> service.creer("TYPE_DOCUMENT", doc("Type max", 721), drh))).getCode())
				.isEqualTo("SLA_INVALIDE");
		assertThat(erreur(catchThrowable(() -> service.creer("TYPE_DOCUMENT", doc("Type sans sla", null), drh)))
				.getCode()).isEqualTo("SLA_INVALIDE");
		assertThat(service.creer("TYPE_DOCUMENT", doc("Type un", 1), drh).slaHeures()).isEqualTo(1);
		assertThat(service.creer("TYPE_DOCUMENT", doc("Type sept", 720), drh).slaHeures()).isEqualTo(720);
	}

	@Test
	@DisplayName("couleur hors palette : 400 COULEUR_INVALIDE ; description de 301 caractères : 400")
	void couleurEtDescription() {
		assertThat(erreur(catchThrowable(() -> service.creer("TYPE_DOCUMENT",
				new ValeurRequest("Type couleur", null, "FUCHSIA", null, 5, null, null), drh))).getCode())
				.isEqualTo("COULEUR_INVALIDE");
		assertThat(erreur(catchThrowable(() -> service.creer("TYPE_DOCUMENT",
				new ValeurRequest("Type desc", "d".repeat(301), null, null, 5, null, null), drh))).getCode())
				.isEqualTo("DESCRIPTION_TROP_LONGUE");
		assertThat(service.creer("TYPE_DOCUMENT",
				new ValeurRequest("Type bleu", "aide", "bleu", null, 5, null, null), drh).couleur()).isEqualTo("BLEU");
	}

	@Test
	@DisplayName("deux modifications avec la même version : la seconde reçoit 409 PARAMETRE_MODIFIE_ENTRE_TEMPS")
	void concurrence() {
		ValeurResponse v = service.creer("TYPE_DOCUMENT", doc("Attestation de stage", 72), drh);
		service.modifier("TYPE_DOCUMENT", v.code(), doc("Attestation stage A", 72), v.version(), drh);

		ReferentielMetierException e = erreur(catchThrowable(() -> service.modifier("TYPE_DOCUMENT", v.code(),
				doc("Attestation stage B", 72), v.version(), drh)));

		assertThat(e.getHttpStatus()).isEqualTo(409);
		assertThat(e.getCode()).isEqualTo("PARAMETRE_MODIFIE_ENTRE_TEMPS");
		assertThat(service.libelle("TYPE_DOCUMENT", v.code())).isEqualTo("Attestation stage A");
	}

	@Test
	@DisplayName("suppression d'une valeur jamais utilisée : OK ; après marque « utilisée » : 409 PARAMETRE_UTILISE")
	void suppression() {
		ValeurResponse v = service.creer("TYPE_DOCUMENT", doc("Attestation de stage", 72), drh);
		ValeurResponse w = service.creer("TYPE_DOCUMENT", doc("Attestation jetable", 72), drh);
		service.supprimer("TYPE_DOCUMENT", w.code(), w.version(), drh);
		assertThat(repository.findByReferentielAndCode("TYPE_DOCUMENT", w.code())).isEmpty();

		// une demande utilise le type : marque collante, même sans compteur local
		service.marquerUtilisee("TYPE_DOCUMENT", v.code());
		when(compteur.compter("TYPE_DOCUMENT", v.code())).thenReturn(1L);

		ReferentielMetierException e = erreur(catchThrowable(
				() -> service.supprimer("TYPE_DOCUMENT", v.code(), null, drh)));
		assertThat(e.getHttpStatus()).isEqualTo(409);
		assertThat(e.getCode()).isEqualTo("PARAMETRE_UTILISE");
		assertThat(e.getDetails()).containsEntry("utilisations", 1L);
		assertThat(e.getMessage()).contains("désactiver");
		assertThat(repository.findByReferentielAndCode("TYPE_DOCUMENT", v.code())).isPresent();
	}

	@Test
	@DisplayName("marque collante : encore 409 même si le compteur local retombe à 0 (demande supprimée)")
	void margueCollante() {
		ValeurResponse v = service.creer("TYPE_DOCUMENT", doc("Attestation de stage", 72), drh);
		service.marquerUtilisee("TYPE_DOCUMENT", v.code());
		when(compteur.compter(eq("TYPE_DOCUMENT"), anyString())).thenReturn(0L);

		assertThat(erreur(catchThrowable(() -> service.supprimer("TYPE_DOCUMENT", v.code(), null, drh))).getCode())
				.isEqualTo("PARAMETRE_UTILISE");
	}

	@Test
	@DisplayName("désactivation : 200, valeur absente des libellés actifs, réactivation possible")
	void desactiverReactiver() {
		ValeurResponse v = service.creer("TYPE_DOCUMENT", doc("Attestation de stage", 72), drh);

		ValeurResponse off = service.desactiver("TYPE_DOCUMENT", v.code(), v.version(), drh);
		assertThat(off.actif()).isFalse();
		assertThat(service.libelles(false, true).referentiels().get("TYPE_DOCUMENT"))
				.noneMatch(x -> x.code().equals(v.code()));
		assertThat(service.libelles(true, false).referentiels().get("TYPE_DOCUMENT"))
				.anyMatch(x -> x.code().equals(v.code()) && !x.actif());

		ValeurResponse on = service.reactiver("TYPE_DOCUMENT", v.code(), off.version(), drh);
		assertThat(on.actif()).isTrue();
	}

	@Test
	@DisplayName("congé de mariage sans pièce jointe créé ; MALADIE : pièce jointe verrouillée → 422 ; ANNUEL → 409")
	void typesDeConge() {
		ValeurResponse mariage = service.creer("TYPE_CONGE",
				new ValeurRequest("Congé de mariage", null, null, null, null, false, null), drh);
		assertThat(mariage.pieceJointeObligatoire()).isFalse();
		assertThat(mariage.pieceJointeVerrouillee()).isFalse();

		ValeurResponse maladie = service.valeurs("TYPE_CONGE").stream()
				.filter(x -> x.code().equals("MALADIE")).findFirst().orElseThrow();
		ReferentielMetierException e = erreur(catchThrowable(() -> service.modifier("TYPE_CONGE", "MALADIE",
				new ValeurRequest(null, null, null, null, null, false, null), maladie.version(), drh)));
		assertThat(e.getHttpStatus()).isEqualTo(422);
		assertThat(e.getCode()).isEqualTo("PIECE_JOINTE_VERROUILLEE");

		ValeurResponse annuel = service.valeurs("TYPE_CONGE").stream()
				.filter(x -> x.code().equals("ANNUEL")).findFirst().orElseThrow();
		ReferentielMetierException s = erreur(catchThrowable(
				() -> service.desactiver("TYPE_CONGE", "ANNUEL", annuel.version(), drh)));
		assertThat(s.getHttpStatus()).isEqualTo(409);
		assertThat(s.getCode()).isEqualTo("PARAMETRE_SYSTEME");
		assertThat(erreur(catchThrowable(() -> service.supprimer("TYPE_CONGE", "ANNUEL", null, drh))).getCode())
				.isEqualTo("PARAMETRE_SYSTEME");
		// rendre la PJ obligatoire sur un type libre est autorisé
		assertThat(service.modifier("TYPE_CONGE", mariage.code(),
				new ValeurRequest(null, null, null, null, null, true, null), mariage.version(), drh)
				.pieceJointeObligatoire()).isTrue();
	}

	@Test
	@DisplayName("journal : création, modification, désactivation tracées avec auteur et avant / après")
	void journal() {
		ValeurResponse v = service.creer("TYPE_DOCUMENT", doc("Attestation de stage", 72), drh);
		ValeurResponse m = service.modifier("TYPE_DOCUMENT", v.code(), doc(null, 24), v.version(), drh);
		service.desactiver("TYPE_DOCUMENT", v.code(), m.version(), drh);

		var page = service.journal("TYPE_DOCUMENT", 0, 20);

		assertThat(page.total()).isGreaterThanOrEqualTo(3);
		var entrees = page.contenu().stream().filter(x -> v.code().equals(x.code())).toList();
		assertThat(entrees).extracting(x -> x.action()).containsExactlyInAnyOrder("CREATION", "MODIFICATION",
				"DESACTIVATION");
		assertThat(entrees).allMatch(x -> "Amel Trabelsi".equals(x.auteurNom()));
		var modif = entrees.stream().filter(x -> x.action().equals("MODIFICATION")).findFirst().orElseThrow();
		assertThat(modif.avant().get("sla_heures").asInt()).isEqualTo(72);
		assertThat(modif.apres().get("sla_heures").asInt()).isEqualTo(24);
	}

	@Test
	@DisplayName("ordre : liste complète requise (400 ORDRE_INCOMPLET), puis nouvel ordre appliqué et journalisé")
	void ordre() {
		List<String> codes = service.valeurs("NIVEAU_SENIORITE").stream().map(ValeurResponse::code).toList();
		assertThat(erreur(catchThrowable(() -> service.reordonner("NIVEAU_SENIORITE", codes.subList(0, 2), drh)))
				.getCode()).isEqualTo("ORDRE_INCOMPLET");

		List<String> inverse = new java.util.ArrayList<>(codes);
		java.util.Collections.reverse(inverse);
		List<ValeurResponse> res = service.reordonner("NIVEAU_SENIORITE", inverse, drh);

		assertThat(res.stream().map(ValeurResponse::code).toList()).isEqualTo(inverse);
		assertThat(service.journal("NIVEAU_SENIORITE", 0, 50).contenu()).anyMatch(x -> x.action().equals("ORDRE"));
	}

	@Test
	@DisplayName("code généré : accents retirés, suffixe numérique si pris")
	void codeGenere() {
		assertThat(CodeParametre.base("Chef d'équipe sénior")).isEqualTo("CHEF_D_EQUIPE_SENIOR");
		ValeurResponse a = service.creer("FAMILLE_METIER", doc("Qualité", null), drh);
		ValeurResponse b = service.creer("FAMILLE_METIER", doc("QUALITE!", null), drh);
		assertThat(a.code()).isEqualTo("QUALITE");
		assertThat(b.code()).isEqualTo("QUALITE_2");
	}

	@Test
	@DisplayName("lecture non back-office : valeurs actives des référentiels de la liste blanche seulement")
	void libellesNonBackOffice() {
		var l = service.libelles(false, false);
		assertThat(l.referentiels()).containsKeys("TYPE_CONGE", "TYPE_DOCUMENT", "STATUT_DEMANDE");
		assertThat(l.referentiels()).doesNotContainKeys("PROFIL_ACCES", "STATUT_COLLABORATEUR");
		assertThat(l.version()).startsWith("W/\"");
		assertThat(service.libelles(true, false).version()).isNotEqualTo(l.version());
	}

	@Test
	@DisplayName("utiliser (appel interne) : valide et marque les actifs, signale inactifs et inconnus")
	void utiliser() {
		ValeurResponse inactive = service.creer("FAMILLE_METIER", doc("Famille morte", null), drh);
		service.desactiver("FAMILLE_METIER", inactive.code(), inactive.version(), drh);
		ValeurResponse libre = service.creer("FAMILLE_METIER", doc("Famille libre", null), drh);

		UtiliserResponse r = service.utiliser(List.of(
				new Reference("FAMILLE_METIER", libre.code()),
				new Reference("FAMILLE_METIER", inactive.code()),
				new Reference("NIVEAU_SENIORITE", "inexistant"),
				new Reference("TYPE_DOCUMENT", "AUTRE")));

		assertThat(r.valides()).containsExactly(new Reference("FAMILLE_METIER", libre.code()));
		assertThat(r.inactifs()).hasSize(1);
		assertThat(r.inconnus()).hasSize(2);
		assertThat(repository.findByReferentielAndCode("FAMILLE_METIER", libre.code()).orElseThrow()
				.getPremiereUtilisationLe()).isNotNull();
		assertThat(erreur(catchThrowable(() -> service.supprimer("FAMILLE_METIER", libre.code(), null, drh)))
				.getCode()).isEqualTo("PARAMETRE_UTILISE");
	}

	private static Throwable catchThrowable(org.assertj.core.api.ThrowableAssert.ThrowingCallable c) {
		return org.assertj.core.api.Assertions.catchThrowable(c);
	}
}
