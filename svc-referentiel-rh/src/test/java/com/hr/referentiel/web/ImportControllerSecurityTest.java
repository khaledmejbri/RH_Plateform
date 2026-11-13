package com.hr.referentiel.web;

import com.hr.referentiel.config.SecurityConfig;
import com.hr.referentiel.dto.PageReferentielResponse;
import com.hr.referentiel.importation.ImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Matrice des rôles G4 pour I1 à I12 : RH et ADMIN écrivent, DIRECTION lit l'historique seulement, les autres 403, sans JWT 401. */
@WebMvcTest(controllers = {ImportController.class})
@Import(SecurityConfig.class)
class ImportControllerSecurityTest {

	private static final String B = "/api/rh/v1/admin/imports";

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private ImportService service;
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

	private final UUID analyse = UUID.randomUUID();

	@Test
	@DisplayName("I1, I2 : analyse réservée à RH et ADMIN (200) ; DIRECTION, responsable, collaborateur 403 ; sans JWT 401")
	void analyses() throws Exception {
		MockMultipartFile fichier = new MockMultipartFile("fichier", "a.csv", "text/csv", "Matricule;Nom".getBytes());
		for (String route : new String[] {B + "/collaborateurs/analyses", B + "/soldes/analyses"}) {
			for (RequestPostProcessor ok : List.of(RH, ADMIN)) {
				mockMvc.perform(multipart(route).file(fichier).with(ok)).andExpect(status().isOk());
			}
			for (RequestPostProcessor ko : List.of(DG, CS, COL)) {
				mockMvc.perform(multipart(route).file(fichier).with(ko)).andExpect(status().isForbidden());
			}
			mockMvc.perform(multipart(route).file(fichier)).andExpect(status().isUnauthorized());
		}
	}

	@Test
	@DisplayName("I3 à I9 : écriture RH / ADMIN seulement, DIRECTION 403 y compris sur l'analyse d'un autre")
	void analysesEtFichiers() throws Exception {
		String corps = "{\"colonnes\":[]}";
		for (RequestPostProcessor ok : List.of(RH, ADMIN)) {
			mockMvc.perform(get(B + "/analyses/" + analyse).with(ok)).andExpect(status().isOk());
			mockMvc.perform(get(B + "/analyses/" + analyse + "/lignes").with(ok)).andExpect(status().isOk());
			mockMvc.perform(put(B + "/analyses/" + analyse + "/correspondance").with(ok).contentType(MediaType.APPLICATION_JSON).content(corps))
					.andExpect(status().isOk());
			mockMvc.perform(post(B + "/analyses/" + analyse + "/confirmation").with(ok).contentType(MediaType.APPLICATION_JSON)
					.content("{\"motif_global\":\"Reprise des soldes\"}")).andExpect(status().isOk());
			mockMvc.perform(get(B + "/analyses/" + analyse + "/lignes-en-erreur").with(ok)).andExpect(status().isOk());
			mockMvc.perform(get(B + "/collaborateurs/modele").with(ok)).andExpect(status().isOk());
			mockMvc.perform(get(B + "/soldes/modele").with(ok)).andExpect(status().isOk());
		}
		for (RequestPostProcessor ko : List.of(DG, CS, COL)) {
			mockMvc.perform(get(B + "/analyses/" + analyse).with(ko)).andExpect(status().isForbidden());
			mockMvc.perform(get(B + "/analyses/" + analyse + "/lignes").with(ko)).andExpect(status().isForbidden());
			mockMvc.perform(put(B + "/analyses/" + analyse + "/correspondance").with(ko).contentType(MediaType.APPLICATION_JSON).content(corps))
					.andExpect(status().isForbidden());
			mockMvc.perform(post(B + "/analyses/" + analyse + "/confirmation").with(ko).contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isForbidden());
			mockMvc.perform(get(B + "/analyses/" + analyse + "/lignes-en-erreur").with(ko)).andExpect(status().isForbidden());
			mockMvc.perform(get(B + "/collaborateurs/modele").with(ko)).andExpect(status().isForbidden());
			mockMvc.perform(get(B + "/soldes/modele").with(ko)).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(B + "/analyses/" + analyse)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(B + "/analyses/" + analyse + "/confirmation").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("I10 à I12 : historique lisible par RH, ADMIN et DIRECTION ; collaborateur et responsable 403")
	void historique() throws Exception {
		when(service.historique(null, 0, 20)).thenReturn(new PageReferentielResponse<>(List.of(), 0, 0, 0, 20));
		for (String route : new String[] {B + "/historique", B + "/historique/" + analyse, B + "/historique/" + analyse + "/lignes"}) {
			for (RequestPostProcessor ok : List.of(RH, ADMIN, DG)) {
				mockMvc.perform(get(route).with(ok)).andExpect(status().isOk());
			}
			for (RequestPostProcessor ko : List.of(CS, COL)) {
				mockMvc.perform(get(route).with(ko)).andExpect(status().isForbidden());
			}
			mockMvc.perform(get(route)).andExpect(status().isUnauthorized());
		}
	}

	@Test
	@DisplayName("un rôle refusé n'atteint jamais le service d'import")
	void rienPourLesRolesRefuses() throws Exception {
		mockMvc.perform(post(B + "/analyses/" + analyse + "/confirmation").with(DG).contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isForbidden());
		verifyNoInteractions(service);
	}
}
