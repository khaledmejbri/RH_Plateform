package com.hr.referentiel.service;

import com.hr.referentiel.dto.PageReferentielResponse;
import com.hr.referentiel.dto.SoldeDtos.SoldeLigne;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.repository.AcquisitionMoisRepository;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.MouvementSoldeCongeRepository;
import com.hr.referentiel.repository.ReglagesCongesRepository;
import com.hr.referentiel.entity.ParametresConges;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Corrections de la recette sur 8090 : Eureka, tri en collation française, statut inconnu, accord D-S14, réglage non numérique. */
class RecetteCorrectionsTest {

	@Test
	@DisplayName("Eureka : aucun bean RestClient.Builder, RestClient ni RestTemplate n'est exposé (un builder équilibré était capté par le client Eureka)")
	void aucunBeanClientHttp() throws Exception {
		ClassPathScanningCandidateComponentProvider scan = new ClassPathScanningCandidateComponentProvider(false);
		scan.addIncludeFilter(new AnnotationTypeFilter(org.springframework.context.annotation.Configuration.class));
		scan.addIncludeFilter(new AnnotationTypeFilter(org.springframework.stereotype.Component.class));
		int methodesBean = 0;
		for (var definition : scan.findCandidateComponents("com.hr.referentiel")) {
			Class<?> classe = Class.forName(definition.getBeanClassName());
			for (var m : classe.getDeclaredMethods()) {
				if (m.isAnnotationPresent(Bean.class)) {
					methodesBean++;
					Class<?> type = m.getReturnType();
					assertThat(type).as(classe.getSimpleName() + "." + m.getName()).isNotIn(RestClient.Builder.class, RestClient.class,
							RestTemplate.class);
					assertThat(m.isAnnotationPresent(org.springframework.cloud.client.loadbalancer.LoadBalanced.class))
							.as(classe.getSimpleName() + "." + m.getName() + " @LoadBalanced").isFalse();
				}
			}
		}
		assertThat(methodesBean).isPositive();
		// le client d'identité prend l'adresse directe (propriété), comme les autres services
		var constructeur = com.hr.referentiel.importation.IdentiteInterneClient.class.getConstructors()[0];
		assertThat(constructeur.getParameterTypes()).doesNotContain(RestClient.Builder.class);
		assertThat(constructeur.getParameters()[0].getAnnotation(Value.class).value()).contains("referentiel.identite.base-url");
	}

	// ─── Liste des soldes ──────────────────────────────────────────────────────

	private SoldeCongeLectureService lecture(List<Collaborateur> fiches, ParametreService parametres) {
		CollaborateurRepository repo = mock(CollaborateurRepository.class);
		when(repo.findAllWithUnite()).thenReturn(fiches);
		SoldeCongeService soldes = mock(SoldeCongeService.class);
		when(soldes.soldes(any())).thenReturn(List.of());
		when(soldes.reservesGroupes(any())).thenReturn(java.util.Map.of());
		CollaborateurConnecteService connecte = mock(CollaborateurConnecteService.class);
		when(connecte.resoudre(any())).thenReturn(Optional.empty());
		return new SoldeCongeLectureService(soldes, mock(CongeDecompteService.class), connecte, repo,
				mock(MouvementSoldeCongeRepository.class), parametres);
	}

	private static Collaborateur fiche(String matricule, String prenom, String nom) {
		Collaborateur c = new Collaborateur();
		c.setId(UUID.randomUUID());
		c.setMatricule(matricule);
		c.setPrenom(prenom);
		c.setNom(nom);
		c.setStatut("ACTIF");
		return c;
	}

	private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("x").build();

	@Test
	@DisplayName("tri par nom en collation française : « Éloïse » avant « Eric » avant « Fatma » (jamais après « Zoé » à cause du É) ; sens décroissant inversé")
	void triCollationFrancaise() {
		ParametreService parametres = mock(ParametreService.class);
		when(parametres.libelle(any(), any())).thenReturn("Actif");
		when(parametres.libelleOuInconnu(any(), any())).thenReturn("Actif");
		SoldeCongeLectureService service = lecture(new ArrayList<>(List.of(fiche("M4", "Zoé", "Zed"), fiche("M1", "Fatma", "Alpha"),
				fiche("M2", "Eric", "Beta"), fiche("M3", "Éloïse", "Zed"))), parametres);
		PageReferentielResponse<SoldeLigne> asc = service.liste(null, null, "ACTIF", "nom", "asc", 0, 20, jwt);
		assertThat(asc.getContenu()).extracting(SoldeLigne::nomComplet)
				.containsExactly("Éloïse Zed", "Eric Beta", "Fatma Alpha", "Zoé Zed");
		PageReferentielResponse<SoldeLigne> desc = service.liste(null, null, "ACTIF", "nom", "desc", 0, 20, jwt);
		assertThat(desc.getContenu().get(0).nomComplet()).isEqualTo("Zoé Zed");
		assertThat(service.liste(null, null, "ACTIF", "unite", "asc", 0, 20, jwt).getContenu()).hasSize(4);
	}

	@Test
	@DisplayName("statut inconnu (FOO) -> 400 CORPS_INVALIDE ; TOUS et les statuts connus acceptés")
	void statutInconnu() {
		ParametreService parametres = mock(ParametreService.class);
		when(parametres.libelle("STATUT_COLLABORATEUR", "ACTIF")).thenReturn("Actif");
		when(parametres.libelle("STATUT_COLLABORATEUR", "FOO")).thenReturn(null);
		when(parametres.libelleOuInconnu(any(), any())).thenReturn("Actif");
		SoldeCongeLectureService service = lecture(List.of(fiche("M1", "A", "B")), parametres);
		assertThatThrownBy(() -> service.liste(null, null, "FOO", null, null, 0, 20, jwt))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(e -> {
					assertThat(((ReferentielMetierException) e).getCode()).isEqualTo("CORPS_INVALIDE");
					assertThat(((ReferentielMetierException) e).getHttpStatus()).isEqualTo(400);
				});
		assertThatThrownBy(() -> service.exporter(null, null, "foo", null, null, jwt)).isInstanceOf(ReferentielMetierException.class);
		assertThat(service.liste(null, null, "TOUS", null, null, 0, 20, jwt).getContenu()).hasSize(1);
		assertThat(service.liste(null, null, "actif", null, null, 0, 20, jwt).getContenu()).hasSize(1);
	}

	@Test
	@DisplayName("D-S14 : « 1 jour demandé », « 2 jours demandés », « 1,5 jour disponible », « 0 jour disponible »")
	void accordDuMessage() {
		assertThat(CongeDecompteService.messageSolde(new BigDecimal("1.0"), new BigDecimal("0.5")))
				.isEqualTo("Solde insuffisant : 1 jour demandé, 0,5 jour disponible");
		assertThat(CongeDecompteService.messageSolde(new BigDecimal("2.0"), new BigDecimal("1.5")))
				.isEqualTo("Solde insuffisant : 2 jours demandés, 1,5 jour disponible");
		assertThat(CongeDecompteService.messageSolde(new BigDecimal("3.5"), new BigDecimal("3.0")))
				.isEqualTo("Solde insuffisant : 3,5 jours demandés, 3 jours disponibles");
		assertThat(CongeDecompteService.messageSolde(new BigDecimal("1.0"), new BigDecimal("-2.0")))
				.isEqualTo("Solde insuffisant : 1 jour demandé, 0 jour disponible");
	}

	@Test
	@DisplayName("T8 C8-05 : réglage « abc » (texte) ou null -> 400 REGLAGE_HORS_BORNES (fiche), « 2 » et « 1,5 » en texte acceptés")
	void reglageNonNumerique() {
		ReglagesCongesRepository repo = mock(ReglagesCongesRepository.class);
		ParametresConges ligne = new ParametresConges();
		ligne.setJoursAcquisParMois(new BigDecimal("1.5"));
		when(repo.findById(ParametresConges.ID_UNIQUE)).thenReturn(Optional.of(ligne));
		when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
		ReglagesCongeService service = new ReglagesCongeService(repo, mock(AcquisitionMoisRepository.class),
				mock(JournalParametres.class), Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC));
		for (Object ko : new Object[] {"abc", "", "1.2.3", true, null}) {
			assertThatThrownBy(() -> service.modifier(ReglagesCongeService.CLE_JOURS_PAR_MOIS, ko, 0L, AuteurParametre.systeme()))
					.satisfies(e -> {
						assertThat(((ReferentielMetierException) e).getCode()).isEqualTo("REGLAGE_HORS_BORNES");
						assertThat(((ReferentielMetierException) e).getHttpStatus()).isEqualTo(400);
					});
		}
		assertThat(service.modifier(ReglagesCongeService.CLE_JOURS_PAR_MOIS, "2", 0L, AuteurParametre.systeme()).valeur())
				.isEqualByComparingTo("2");
		assertThat(service.modifier(ReglagesCongeService.CLE_JOURS_PAR_MOIS, "1,5", 0L, AuteurParametre.systeme()).valeur())
				.isEqualByComparingTo("1.5");
		assertThat(service.modifier(ReglagesCongeService.CLE_JOURS_PAR_MOIS, 3, 0L, AuteurParametre.systeme()).valeur())
				.isEqualByComparingTo("3");
	}
}
