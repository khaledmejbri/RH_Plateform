package com.hr.referentiel.web;

import com.hr.referentiel.config.SecurityConfig;
import com.hr.referentiel.dto.PageReferentielResponse;
import com.hr.referentiel.service.AjustementSoldeService;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.service.AuteurParametreResolver;
import com.hr.referentiel.service.DemandeAdministrativeRhService;
import com.hr.referentiel.service.JourFerieService;
import com.hr.referentiel.service.ReglagesCongeService;
import com.hr.referentiel.service.SoldeCongeLectureService;
import com.hr.referentiel.service.ParametreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matrice des rôles G4 (annexe A.3) pour les routes du solde, des jours fériés, des réglages et de l'annulation après
 * approbation : sans JWT 401, collaborateur / responsable (COL-WEB, CS-WEB, CS-MOB) 403 sur le back-office, RH et ADMIN 200,
 * DIRECTION lecture seule.
 */
@WebMvcTest(controllers = {CongeCollaborateurController.class, SoldeCongeAdminController.class, JourFerieController.class,
		DemandeAdministrativeRhController.class, ParametreController.class})
@Import(SecurityConfig.class)
class SoldesCongeControllerSecurityTest {

	private static final String B6 = "/api/rh/v1/admin/conges/soldes/";
	private static final String FERIES = "/api/referentiel/v1/parametres/jours-feries";
	private static final String PARAM = "/api/referentiel/v1/parametres";

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private SoldeCongeLectureService lecture;
	@MockitoBean
	private AjustementSoldeService ajustement;
	@MockitoBean
	private JourFerieService jourFerieService;
	@MockitoBean
	private DemandeAdministrativeRhService demandeService;
	@MockitoBean
	private ParametreService parametreService;
	@MockitoBean
	private ReglagesCongeService reglages;
	@MockitoBean
	private AuteurParametreResolver auteurResolver;
	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private org.springframework.cache.CacheManager cacheManager;

	private static RequestPostProcessor roles(String... roles) {
		return jwt().jwt(j -> j.claim("identifiant_utilisateur", UUID.randomUUID().toString()))
				.authorities(java.util.Arrays.stream(roles).map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
						.toArray(GrantedAuthority[]::new));
	}

	private static final RequestPostProcessor COL = roles("USER");
	private static final RequestPostProcessor CS = roles("USER", "RO");
	private static final RequestPostProcessor RH = roles("USER", "RH");
	private static final RequestPostProcessor ADMIN = roles("USER", "ADMIN");
	private static final RequestPostProcessor DG = roles("USER", "DIRECTION");

	private final UUID cible = UUID.randomUUID();

	@Test
	@DisplayName("A1 à A3 : tout utilisateur authentifié lit les siens (200) ; sans JWT 401 ; en-tête X-Collaborateur-Id ignoré")
	void soldePersonnel() throws Exception {
		when(lecture.mesMouvements(any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
				.thenReturn(new PageReferentielResponse<>(List.of(), 0, 0, 0, 20));
		for (RequestPostProcessor r : List.of(COL, CS, RH, ADMIN, DG)) {
			mockMvc.perform(get("/api/rh/v1/conges/solde/moi").with(r).header("X-Collaborateur-Id", UUID.randomUUID().toString()))
					.andExpect(status().isOk());
			mockMvc.perform(get("/api/rh/v1/conges/solde/moi/mouvements").with(r)).andExpect(status().isOk());
			mockMvc.perform(get("/api/rh/v1/conges/simulation").param("date_debut", "2026-12-07").param("date_fin", "2026-12-11")
					.with(r)).andExpect(status().isOk());
		}
		mockMvc.perform(get("/api/rh/v1/conges/solde/moi")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/rh/v1/conges/simulation")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("B1 à B5 : RH, ADMIN, DIRECTION 200 ; collaborateur et responsables 403 ; sans JWT 401")
	void lectureBackOffice() throws Exception {
		when(lecture.liste(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.anyInt(), any())).thenReturn(new PageReferentielResponse<>(List.of(), 0, 0, 0, 20));
		when(lecture.exporter(any(), any(), any(), any(), any(), any())).thenReturn(new byte[0]);
		when(lecture.exporterMouvements(any())).thenReturn(new byte[0]);
		String[] routes = {B6.substring(0, B6.length() - 1), B6 + "export", B6 + cible, B6 + cible + "/mouvements",
				B6 + cible + "/mouvements/export"};
		for (String route : routes) {
			for (RequestPostProcessor ok : List.of(RH, ADMIN, DG)) {
				mockMvc.perform(get(route).with(ok)).andExpect(status().isOk());
			}
			for (RequestPostProcessor ko : List.of(COL, CS)) {
				mockMvc.perform(get(route).with(ko)).andExpect(status().isForbidden());
			}
			mockMvc.perform(get(route)).andExpect(status().isUnauthorized());
		}
	}

	@Test
	@DisplayName("B6 : RH et ADMIN 200 ; DIRECTION, responsable, collaborateur 403 ; sans JWT 401")
	void ajustement() throws Exception {
		String corps = "{\"ecart\":2,\"motif\":\"Report 2025\",\"version\":0}";
		for (RequestPostProcessor ok : List.of(RH, ADMIN)) {
			mockMvc.perform(post(B6 + cible + "/ajustements").with(ok).contentType(MediaType.APPLICATION_JSON).content(corps))
					.andExpect(status().isOk());
		}
		for (RequestPostProcessor ko : List.of(DG, COL, CS)) {
			mockMvc.perform(post(B6 + cible + "/ajustements").with(ko).contentType(MediaType.APPLICATION_JSON).content(corps))
					.andExpect(status().isForbidden());
		}
		mockMvc.perform(post(B6 + cible + "/ajustements").contentType(MediaType.APPLICATION_JSON).content(corps))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("D4 : annulation après approbation réservée à RH / ADMIN ; DIRECTION, responsable, collaborateur 403")
	void annulationApresApprobation() throws Exception {
		String route = "/api/rh/v1/demandes-administratives/" + cible + "/annuler-apres-approbation";
		String corps = "{\"motif\":\"Besoin de service\"}";
		for (RequestPostProcessor ok : List.of(RH, ADMIN)) {
			mockMvc.perform(post(route).with(ok).contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isOk());
		}
		for (RequestPostProcessor ko : List.of(DG, COL, CS)) {
			mockMvc.perform(post(route).with(ko).contentType(MediaType.APPLICATION_JSON).content(corps))
					.andExpect(status().isForbidden());
		}
		mockMvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isUnauthorized());
		verify(demandeService, org.mockito.Mockito.times(2)).annulerApresApprobation(any(), any(), any());
	}

	@Test
	@DisplayName("P1, P3, P4, P10, P12 : lecture RH / ADMIN / DIRECTION, 403 pour collaborateur et responsable")
	void lectureParametres() throws Exception {
		when(jourFerieService.lister(any())).thenReturn(List.of());
		when(jourFerieService.journal(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(null);
		for (String route : new String[] {PARAM + "/reglages", PARAM + "/reglages/journal", PARAM + "/conges", FERIES,
				FERIES + "/journal"}) {
			for (RequestPostProcessor ok : List.of(RH, ADMIN, DG)) {
				mockMvc.perform(get(route).with(ok)).andExpect(status().isOk());
			}
			for (RequestPostProcessor ko : List.of(COL, CS)) {
				mockMvc.perform(get(route).with(ko)).andExpect(status().isForbidden());
			}
			mockMvc.perform(get(route)).andExpect(status().isUnauthorized());
		}
	}

	@Test
	@DisplayName("P5 : les dates actives sont lisibles par tout utilisateur authentifié, 401 sans JWT")
	void joursFeriesActifs() throws Exception {
		when(jourFerieService.actifs(any())).thenReturn(List.of());
		for (RequestPostProcessor r : List.of(COL, CS, RH, ADMIN, DG)) {
			mockMvc.perform(get(FERIES + "/actifs").param("annee", "2027").with(r)).andExpect(status().isOk());
		}
		mockMvc.perform(get(FERIES + "/actifs")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("P2, P6 à P9 : écriture RH / ADMIN seulement ; DIRECTION, collaborateur et responsable 403 (jours fériés et réglage)")
	void ecritureParametres() throws Exception {
		when(auteurResolver.depuis(any())).thenReturn(new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi"));
		String ferie = "{\"date\":\"2027-03-20\",\"libelle\":\"Aïd el-Fitr\"}";
		String version = "{\"version\":0}";
		String reglage = "{\"valeur\":2,\"version\":0}";
		for (RequestPostProcessor ok : List.of(RH, ADMIN)) {
			mockMvc.perform(post(FERIES).with(ok).contentType(MediaType.APPLICATION_JSON).content(ferie)).andExpect(status().isCreated());
			mockMvc.perform(put(FERIES + "/" + cible).with(ok).contentType(MediaType.APPLICATION_JSON).content(version))
					.andExpect(status().isOk());
			mockMvc.perform(post(FERIES + "/" + cible + "/desactiver").with(ok).contentType(MediaType.APPLICATION_JSON).content(version))
					.andExpect(status().isOk());
			mockMvc.perform(post(FERIES + "/" + cible + "/reactiver").with(ok).contentType(MediaType.APPLICATION_JSON).content(version))
					.andExpect(status().isOk());
			mockMvc.perform(delete(FERIES + "/" + cible).param("version", "0").with(ok)).andExpect(status().isNoContent());
			mockMvc.perform(put(PARAM + "/reglages/jours_acquis_par_mois").with(ok).contentType(MediaType.APPLICATION_JSON)
					.content(reglage)).andExpect(status().isOk());
		}
		for (RequestPostProcessor ko : List.of(DG, COL, CS)) {
			mockMvc.perform(post(FERIES).with(ko).contentType(MediaType.APPLICATION_JSON).content(ferie)).andExpect(status().isForbidden());
			mockMvc.perform(put(FERIES + "/" + cible).with(ko).contentType(MediaType.APPLICATION_JSON).content(version))
					.andExpect(status().isForbidden());
			mockMvc.perform(post(FERIES + "/" + cible + "/desactiver").with(ko).contentType(MediaType.APPLICATION_JSON).content(version))
					.andExpect(status().isForbidden());
			mockMvc.perform(delete(FERIES + "/" + cible).param("version", "0").with(ko)).andExpect(status().isForbidden());
			mockMvc.perform(put(PARAM + "/reglages/jours_acquis_par_mois").with(ko).contentType(MediaType.APPLICATION_JSON)
					.content(reglage)).andExpect(status().isForbidden());
		}
		mockMvc.perform(post(FERIES).contentType(MediaType.APPLICATION_JSON).content(ferie)).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("un rôle refusé n'atteint jamais le service (aucune écriture)")
	void aucuneEcritureSiRefuse() throws Exception {
		mockMvc.perform(post(B6 + cible + "/ajustements").with(DG).contentType(MediaType.APPLICATION_JSON)
				.content("{\"ecart\":1,\"motif\":\"Motif ok\",\"version\":0}")).andExpect(status().isForbidden());
		verifyNoInteractions(ajustement);
	}
}
