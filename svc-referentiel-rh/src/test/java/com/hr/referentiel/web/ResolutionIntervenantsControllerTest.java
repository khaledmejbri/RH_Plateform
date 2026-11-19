package com.hr.referentiel.web;

import com.hr.referentiel.config.SecurityConfig;
import com.hr.referentiel.domain.BaseDecompteConge;
import com.hr.referentiel.dto.SocieteParametresDtos.SocieteParametresResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.Fonction;
import com.hr.referentiel.entity.FonctionAffectation;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.FonctionAffectationRepository;
import com.hr.referentiel.repository.FonctionRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.service.FonctionService;
import com.hr.referentiel.service.IdentiteSocieteClient;
import com.hr.referentiel.service.IdentiteSocieteClient.SocieteResume;
import com.hr.referentiel.service.JourFerieService;
import com.hr.referentiel.service.SocieteParametresService;
import com.hr.referentiel.service.WorkflowResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration MockMvc des endpoints internes de résolution pour {@code svc-workflow}
 * (§ 9.5.3, D-H03, D-W14, D-SOC21, B2, B3, #3, #4, #5).
 */
@WebMvcTest(
		controllers = WorkflowReferentielInterneController.class,
		properties = "app.internal-api.token=test-internal-token")
@Import({SecurityConfig.class, WorkflowResolutionService.class, ReferentielExceptionHandler.class,
		ResolutionIntervenantsControllerTest.HorlogeConfig.class})
class ResolutionIntervenantsControllerTest {

	@TestConfiguration
	static class HorlogeConfig {
		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-11-19T09:00:00Z"), ZoneOffset.UTC);
		}
	}

	private static final String TOKEN = "test-internal-token";
	private static final UUID SOCIETE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final String URL_RES = "/internal/referentiel/v1/workflow/resolutions";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CollaborateurRepository collaborateurRepo;
	@MockitoBean
	private UniteOrganisationRepository uniteRepo;
	@MockitoBean
	private FonctionRepository fonctionRepo;
	@MockitoBean
	private FonctionAffectationRepository affectationRepo;
	@MockitoBean
	private DemandeAdministrativeRhRepository demandeRepo;
	@MockitoBean
	private SocieteParametresService paramsService;
	@MockitoBean
	private JourFerieService jourFerieService;
	@MockitoBean
	private IdentiteSocieteClient identiteSocieteClient;
	@MockitoBean
	private FonctionService fonctionService;
	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private org.springframework.cache.CacheManager cacheManager;

	private UniteOrganisation uDg;
	private UniteOrganisation uDirRh;
	private UniteOrganisation uDeptSw;
	private UniteOrganisation uServiceWeb;
	private UniteOrganisation uSansManager;

	private Collaborateur dg01;
	private Collaborateur drh01;
	private Collaborateur cdSw;
	private Collaborateur csWeb;
	private Collaborateur colWeb;
	private Collaborateur colSansManager;
	private Collaborateur colInactif;

	@BeforeEach
	void setUp() {
		uDg = creerUnite("DG", "DIRECTION_GENERALE", null);
		uDirRh = creerUnite("DIR_RH", "DIRECTION", uDg);
		uDeptSw = creerUnite("DEPT_SW", "DEPARTEMENT", uDg);
		uServiceWeb = creerUnite("SVC_WEB", "SERVICE", uDeptSw);
		uSansManager = creerUnite("SVC_VIDE", "SERVICE", null);

		dg01 = creerCollab("DG01", "Sami", "Ben Ali", "ACTIF", "DIRECTION", uDg, "Directeur Général", LocalDate.of(2020, 1, 1));
		drh01 = creerCollab("DRH01", "Amel", "Trabelsi", "ACTIF", "RH", uDirRh, "DRH", LocalDate.of(2021, 5, 1));
		cdSw = creerCollab("CD01", "Karim", "Mansour", "ACTIF", "RESPONSABLE", uDeptSw, "Chef de département", LocalDate.of(2022, 3, 1));
		csWeb = creerCollab("CS01", "Omar", "Belhadj", "ACTIF", "RO", uServiceWeb, "Chef de service", LocalDate.of(2023, 2, 1));
		colWeb = creerCollab("COL01", "Leila", "Gharbi", "ACTIF", "COLLABORATEUR", uServiceWeb, "Développeuse", LocalDate.of(2024, 9, 1));
		colSansManager = creerCollab("COL02", "Youssef", "Khelifi", "ACTIF", "COLLABORATEUR", uSansManager, "Analyste", LocalDate.of(2025, 1, 1));
		colInactif = creerCollab("COL03", "Nadia", "Zitouni", "SORTI", "COLLABORATEUR", uDeptSw, "Ancienne salariée", LocalDate.of(2019, 1, 1));

		uDg.setManager(dg01);
		uDirRh.setManager(drh01);
		uDeptSw.setManager(cdSw);
		uServiceWeb.setManager(csWeb);

		when(uniteRepo.findAll()).thenReturn(List.of(uDg, uDirRh, uDeptSw, uServiceWeb, uSansManager));
		when(collaborateurRepo.findAllWithUnite()).thenReturn(
				List.of(dg01, drh01, cdSw, csWeb, colWeb, colSansManager, colInactif));
	}

	@Test
	@DisplayName("Sécurité filtre interne : 403/401 sans X-Internal-Token et 400 sans X-Societe-Id")
	void securiteFiltreInterne() throws Exception {
		String body = "{\"regles\":[{\"cle\":\"e1\",\"regle\":\"DEMANDEUR\"}]}";
		mockMvc.perform(post(URL_RES)
						.header("X-Societe-Id", SOCIETE_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().is4xxClientError());

		mockMvc.perform(post(URL_RES)
						.header("X-Internal-Token", TOKEN)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Contrat § 9.5.3 complet : resultats indexés par clé, contexte_demandeur, D-H03, ROLE RH, FONCTION et exclure")
	void contratCompletEtReglesNominales() throws Exception {
		Fonction fPaie = new Fonction("GEST_PAIE", "Gestionnaire paie", null, "Test", Instant.now());
		fPaie.setActif(true);
		FonctionAffectation affDrh = new FonctionAffectation(
				fPaie, drh01, null, LocalDate.of(2026, 1, 1), null, Instant.now());
		when(fonctionRepo.findAll()).thenReturn(List.of(fPaie));
		when(affectationRepo.findActivesParCodesEtDate(any(), any())).thenReturn(List.of(affDrh));

		String payload = """
				{
				  "date": "2026-11-19",
				  "demandeur_id": "%s",
				  "beneficiaires": ["%s"],
				  "exclure": ["%s"],
				  "regles": [
				    { "cle": "e_noeud", "regle": "MANAGER_NOEUD", "params": {} },
				    { "cle": "e_niv2", "regle": "MANAGER_NIVEAU", "params": { "niveau": 2 } },
				    { "cle": "e_dept", "regle": "RESPONSABLE_TYPE", "params": { "type_noeud": "DEPARTEMENT" } },
				    { "cle": "e_rh", "regle": "ROLE", "params": { "roles": ["RH"] } },
				    { "cle": "e_fn", "regle": "FONCTION", "params": { "fonction_code": "GEST_PAIE", "portee": "TOUTE_SOCIETE" } },
				    { "cle": "e_exclu", "regle": "COLLABORATEUR", "params": { "collaborateur_id": "%s" } },
				    { "cle": "e_inactif", "regle": "COLLABORATEUR", "params": { "collaborateur_id": "%s" } },
				    { "cle": "e_fn_inconnue", "regle": "FONCTION", "params": { "fonction_code": "INCONNUE" } }
				  ]
				}
				""".formatted(colWeb.getId(), cdSw.getId(), dg01.getId(), dg01.getId(), colInactif.getId());

		mockMvc.perform(post(URL_RES)
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contexte_demandeur.profil_acces").value("COLLABORATEUR"))
				.andExpect(jsonPath("$.contexte_demandeur.type_noeud").value("SERVICE"))
				.andExpect(jsonPath("$.contexte_demandeur.anciennete_mois").value(26))
				.andExpect(jsonPath("$.contexte_demandeur.est_manager").value(false))
				.andExpect(jsonPath("$.resultats.e_noeud.candidats[0].collaborateur_id").value(csWeb.getId().toString()))
				.andExpect(jsonPath("$.resultats.e_noeud.candidats[0].compte_id").value(csWeb.getCompteUtilisateurId().toString()))
				.andExpect(jsonPath("$.resultats.e_noeud.candidats[0].nom").value("Omar Belhadj"))
				.andExpect(jsonPath("$.resultats.e_noeud.candidats[0].intitule").value("Chef de service"))
				.andExpect(jsonPath("$.resultats.e_noeud.raison_vide").isEmpty())
				.andExpect(jsonPath("$.resultats.e_niv2.candidats").isEmpty())
				.andExpect(jsonPath("$.resultats.e_niv2.raison_vide").value("TOUS_EXCLUS"))
				.andExpect(jsonPath("$.resultats.e_dept.candidats").isEmpty())
				.andExpect(jsonPath("$.resultats.e_dept.raison_vide").value("TOUS_EXCLUS"))
				.andExpect(jsonPath("$.resultats.e_rh.candidats[0].collaborateur_id").value(drh01.getId().toString()))
				.andExpect(jsonPath("$.resultats.e_fn.candidats[0].collaborateur_id").value(drh01.getId().toString()))
				.andExpect(jsonPath("$.resultats.e_exclu.raison_vide").value("TOUS_EXCLUS"))
				.andExpect(jsonPath("$.resultats.e_inactif.raison_vide").value("INACTIF"))
				.andExpect(jsonPath("$.resultats.e_fn_inconnue.raison_vide").value("FONCTION_INCONNUE"));
	}

	@Test
	@DisplayName("D-H03 et raisons vides : AUCUN_MANAGER, CHAINE_TERMINEE, AUCUN_TITULAIRE et manager inactif en RESPONSABLE_TYPE")
	void dH03EtRaisonsVides() throws Exception {
		// Manager de département inactif : RESPONSABLE_TYPE doit renvoyer AUCUN_MANAGER (pas le manager inactif)
		cdSw.setStatut("SORTI");

		String payload = """
				{
				  "date": "2026-11-19",
				  "demandeur_id": "%s",
				  "regles": [
				    { "cle": "r1", "regle": "MANAGER_NOEUD", "params": {} },
				    { "cle": "r2", "regle": "RESPONSABLE_TYPE", "params": { "type_noeud": "DEPARTEMENT" } },
				    { "cle": "r3", "regle": "ROLE", "params": { "roles": ["ADMIN"] } }
				  ]
				}
				""".formatted(colSansManager.getId());

		mockMvc.perform(post(URL_RES)
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resultats.r1.raison_vide").value("AUCUN_MANAGER"))
				.andExpect(jsonPath("$.resultats.r2.raison_vide").value("AUCUN_MANAGER"))
				.andExpect(jsonPath("$.resultats.r3.raison_vide").value("AUCUN_TITULAIRE"));

		// Demandeur est le DG au sommet : MANAGER_NOEUD -> CHAINE_TERMINEE et est_manager=true
		String payloadDg = """
				{
				  "date": "2026-11-19",
				  "demandeur_id": "%s",
				  "regles": [
				    { "cle": "r_top", "regle": "MANAGER_NOEUD", "params": {} }
				  ]
				}
				""".formatted(dg01.getId());

		mockMvc.perform(post(URL_RES)
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(payloadDg))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contexte_demandeur.est_manager").value(true))
				.andExpect(jsonPath("$.resultats.r_top.raison_vide").value("CHAINE_TERMINEE"));
	}

	@Test
	@DisplayName("Validation B3 / D-SOC21 / #4 : >50 règles -> 400, ROLE DIRECTION -> 400 DEFINITION_INVALIDE, MANAGER_NIVEAU hors 1..4 -> 400, RESPONSABLE_TYPE SERVICE -> 400, demandeur inconnu -> 404")
	void validationsEtErreursMetier() throws Exception {
		// > 50 règles -> 400
		StringBuilder sb = new StringBuilder("{\"regles\":[");
		for (int i = 0; i < 51; i++) {
			if (i > 0) sb.append(',');
			sb.append("{\"cle\":\"k").append(i).append("\",\"regle\":\"DEMANDEUR\"}");
		}
		sb.append("]}");
		mockMvc.perform(post(URL_RES)
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(sb.toString()))
				.andExpect(status().isBadRequest());

		// ROLE DIRECTION -> 400 DEFINITION_INVALIDE (D-SOC21)
		mockMvc.perform(post(URL_RES)
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"regles\":[{\"cle\":\"e1\",\"regle\":\"ROLE\",\"params\":{\"roles\":[\"DIRECTION\"]}}]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DEFINITION_INVALIDE"));

		// MANAGER_NIVEAU 5 -> 400 DEFINITION_INVALIDE
		mockMvc.perform(post(URL_RES)
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"regles\":[{\"cle\":\"e1\",\"regle\":\"MANAGER_NIVEAU\",\"params\":{\"niveau\":5}}]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DEFINITION_INVALIDE"));

		// RESPONSABLE_TYPE SERVICE -> 400 DEFINITION_INVALIDE
		mockMvc.perform(post(URL_RES)
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"regles\":[{\"cle\":\"e1\",\"regle\":\"RESPONSABLE_TYPE\",\"params\":{\"type_noeud\":\"SERVICE\"}}]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DEFINITION_INVALIDE"));

		// demandeur_id inconnu -> 404 COLLABORATEUR_INTROUVABLE (#4)
		mockMvc.perform(post(URL_RES)
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"demandeur_id\":\"" + UUID.randomUUID() + "\",\"regles\":[{\"cle\":\"e1\",\"regle\":\"DEMANDEUR\"}]}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COLLABORATEUR_INTROUVABLE"));
	}

	@Test
	@DisplayName("Calendrier (sans @Transactional autour de l'appel HTTP #3), absences et résumés")
	void calendrierAbsencesEtResumes() throws Exception {
		Method m = WorkflowResolutionService.class.getMethod("calendrier", LocalDate.class, LocalDate.class);
		assertThat(m.getAnnotation(Transactional.class)).isNull();

		when(paramsService.obtenir()).thenReturn(new SocieteParametresResponse(
				UUID.randomUUID(),
				SOCIETE_ID,
				List.of("LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI"),
				2400,
				Map.of("LUNDI", 480),
				"08:30",
				"17:30",
				BaseDecompteConge.JOURS_OUVRABLES,
				BigDecimal.ONE,
				120,
				false,
				false,
				1L,
				Instant.now(),
				"Test"));
		when(jourFerieService.datesActives()).thenReturn(Set.of(
				LocalDate.of(2026, 3, 20),
				LocalDate.of(2026, 5, 1)));
		when(identiteSocieteClient.trouverParId(SOCIETE_ID)).thenReturn(Optional.of(
				new SocieteResume(SOCIETE_ID, "PRINCIPALE", "Société Principale", "ACTIVE", "Africa/Tunis")));

		mockMvc.perform(get("/internal/referentiel/v1/societe/calendrier")
						.param("du", "2026-01-01")
						.param("au", "2026-04-30")
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fuseau").value("Africa/Tunis"))
				.andExpect(jsonPath("$.jours_feries[0]").value("2026-03-20"))
				.andExpect(jsonPath("$.jours_feries.length()").value(1));

		DemandeAdministrativeRh conge = new DemandeAdministrativeRh();
		conge.setDemandeur(colWeb);
		conge.setPeriodeDebut(LocalDate.of(2026, 11, 19));
		conge.setPeriodeFin(LocalDate.of(2026, 11, 20));
		conge.setContenu(Map.of("type_conge", "ANNUEL"));
		when(demandeRepo.findPourRhCouvrantJour(any(), any(), any())).thenReturn(List.of(conge));

		mockMvc.perform(get("/internal/referentiel/v1/absences")
						.param("date", "2026-11-19")
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].collaborateur_id").value(colWeb.getId().toString()))
				.andExpect(jsonPath("$[0].type_conge").value("ANNUEL"));

		when(collaborateurRepo.findDetailById(colWeb.getId())).thenReturn(Optional.of(colWeb));
		mockMvc.perform(get("/internal/referentiel/v1/collaborateurs/" + colWeb.getId() + "/resume")
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.matricule").value("COL01"));

		when(collaborateurRepo.findByCompteUtilisateurId(colWeb.getCompteUtilisateurId())).thenReturn(Optional.of(colWeb));
		mockMvc.perform(get("/internal/referentiel/v1/collaborateurs/par-compte/" + colWeb.getCompteUtilisateurId())
						.header("X-Internal-Token", TOKEN)
						.header("X-Societe-Id", SOCIETE_ID.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(colWeb.getId().toString()));
	}

	private static UniteOrganisation creerUnite(String code, String typeNoeud, UniteOrganisation parent) {
		UniteOrganisation u = new UniteOrganisation();
		u.setId(UUID.randomUUID());
		u.setCode(code);
		u.setLibelle(code);
		u.setTypeNoeud(typeNoeud);
		u.setParent(parent);
		u.setActif(true);
		return u;
	}

	private static Collaborateur creerCollab(
			String matricule, String prenom, String nom, String statut,
			String profilAcces, UniteOrganisation unite, String poste, LocalDate dateRecrutement) {
		Collaborateur c = new Collaborateur();
		c.setId(UUID.randomUUID());
		c.setCompteUtilisateurId(UUID.randomUUID());
		c.setMatricule(matricule);
		c.setPrenom(prenom);
		c.setNom(nom);
		c.setStatut(statut);
		c.setProfilAcces(profilAcces);
		c.setUnite(unite);
		c.setPosteLibelle(poste);
		c.setDateRecrutement(dateRecrutement);
		return c;
	}
}
