package com.hr.referentiel.web;

import com.hr.referentiel.config.SecurityConfig;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.PageJournalImport;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.Parametrage;
import com.hr.referentiel.importation.parametrage.ImportParametrageService;
import com.hr.referentiel.service.AuteurParametreResolver;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Matrice des rôles A.3 pour P1 à P7 et les 405 (T1-23), corps d'erreur et 404 (T1-24). */
@WebMvcTest(controllers = {ImportParametrageController.class})
@Import(SecurityConfig.class)
class ImportParametrageControllerSecurityTest {

	private static final String B = "/api/referentiel/v1/parametres/imports/collaborateurs";

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private ImportParametrageService service;
	@MockitoBean
	private AuteurParametreResolver auteurResolver;
	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private org.springframework.cache.CacheManager cacheManager;

	private static RequestPostProcessor roles(String... roles) {
		return jwt().jwt(j -> j.claim("identifiant_utilisateur", UUID.randomUUID().toString())
						.claim("societe_id", com.hr.referentiel.test.AvecSocieteContexteExtension.SOCIETE_TEST.toString()))
				.authorities(java.util.Arrays.stream(roles).map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
						.toArray(GrantedAuthority[]::new));
	}

	private static final RequestPostProcessor COL = roles("USER");
	private static final RequestPostProcessor CS = roles("USER", "RO");
	private static final RequestPostProcessor RESP = roles("USER", "RESPONSABLE");
	private static final RequestPostProcessor RH = roles("USER", "RH");
	private static final RequestPostProcessor ADMIN = roles("USER", "ADMIN");
	private static final RequestPostProcessor DG = roles("USER", "DIRECTION");

	private final UUID id = UUID.randomUUID();

	@BeforeEach
	void stubs() {
		Parametrage vide = new Parametrage("collaborateurs", "Collaborateurs", 6, null, null, List.of());
		when(service.lire(any())).thenReturn(vide);
		when(service.enregistrer(any(), any(), any())).thenReturn(vide);
		when(service.ajouterSynonyme(any(), any(), any(), any())).thenReturn(vide);
		when(service.desactiver(any(), any(), any(), any())).thenReturn(vide);
		when(service.reactiver(any(), any(), any(), any())).thenReturn(vide);
		when(service.reinitialiser(any(), any(), any())).thenReturn(vide);
		when(service.lireJournal(any(), anyInt(), anyInt())).thenReturn(new PageJournalImport(List.of(), 0, 20, 0));
	}

	private void ecritures(RequestPostProcessor qui, int ok, int okCreation, int okSuppression) throws Exception {
		String json = "{\"version\":6,\"champs\":[]}";
		mockMvc.perform(put(B).with(qui).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().is(ok));
		mockMvc.perform(post(B + "/champs/MATRICULE/synonymes").with(qui).contentType(MediaType.APPLICATION_JSON)
				.content("{\"texte\":\"Mle\",\"version\":6}")).andExpect(status().is(okCreation));
		mockMvc.perform(post(B + "/synonymes/" + id + "/desactiver").with(qui).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":6}")).andExpect(status().is(ok));
		mockMvc.perform(post(B + "/synonymes/" + id + "/reactiver").with(qui).contentType(MediaType.APPLICATION_JSON)
				.content("{\"version\":6}")).andExpect(status().is(ok));
		mockMvc.perform(delete(B + "/synonymes/" + id).param("version", "6").with(qui)).andExpect(status().is(okSuppression));
		mockMvc.perform(post(B + "/reinitialiser").with(qui).contentType(MediaType.APPLICATION_JSON).content("{\"version\":6}"))
				.andExpect(status().is(ok));
	}

	@Test
	@DisplayName("T1-23 : P1 et P7 (lecture) -> RH, ADMIN, DIRECTION 200 ; collaborateur et responsable 403 ; sans JWT 401")
	void lecture() throws Exception {
		for (RequestPostProcessor ok : List.of(RH, ADMIN, DG)) {
			mockMvc.perform(get(B).with(ok)).andExpect(status().isOk()).andExpect(header().string("ETag", "\"6\""))
					.andExpect(header().string("Cache-Control", "no-cache"));
			mockMvc.perform(get(B + "/journal").with(ok)).andExpect(status().isOk());
		}
		for (RequestPostProcessor ko : List.of(COL, CS, RESP)) {
			mockMvc.perform(get(B).with(ko)).andExpect(status().isForbidden());
			mockMvc.perform(get(B + "/journal").with(ko)).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(B)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(B + "/journal")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("T1-23 : P2 à P6 (écriture) -> RH et ADMIN 200 / 201 / 204 ; DIRECTION, responsable, collaborateur 403 sans appel du service ; sans JWT 401")
	void ecriture() throws Exception {
		ecritures(RH, 200, 201, 204);
		ecritures(ADMIN, 200, 201, 204);
		org.mockito.Mockito.clearInvocations(service);
		for (RequestPostProcessor ko : List.of(DG, CS, RESP, COL)) {
			ecritures(ko, 403, 403, 403);
		}
		verifyNoInteractions(service);
		String json = "{\"version\":6}";
		mockMvc.perform(put(B).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(B + "/champs/MATRICULE/synonymes").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post(B + "/reinitialiser").contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(B + "/synonymes/" + id).param("version", "6")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("T1-19 : créer ou supprimer un champ -> 405 PARAMETRE_COMPORTEMENT pour RH et ADMIN ; 403 pour les autres ; 401 sans JWT")
	void listeDeChampsFixe() throws Exception {
		for (RequestPostProcessor ok : List.of(RH, ADMIN)) {
			mockMvc.perform(post(B + "/champs").with(ok).contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("PARAMETRE_COMPORTEMENT"))
					.andExpect(jsonPath("$.message").value("La liste des champs d'import est fixe."));
			mockMvc.perform(delete(B + "/champs/POSTE").with(ok)).andExpect(status().isMethodNotAllowed())
					.andExpect(jsonPath("$.code").value("PARAMETRE_COMPORTEMENT"));
		}
		for (RequestPostProcessor ko : List.of(DG, CS, COL)) {
			mockMvc.perform(post(B + "/champs").with(ko).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
			mockMvc.perform(delete(B + "/champs/POSTE").with(ko)).andExpect(status().isForbidden());
		}
		mockMvc.perform(post(B + "/champs").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(B + "/champs/POSTE")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("T1-24 : type inconnu -> 404 TYPE_IMPORT_INCONNU avec code, erreur et message ; erreur sur un champ -> « champ » dans le corps")
	void corpsDErreur() throws Exception {
		when(service.lire("fournisseurs")).thenThrow(new ReferentielMetierException("TYPE_IMPORT_INCONNU", "Type d'import inconnu.", 404));
		mockMvc.perform(get("/api/referentiel/v1/parametres/imports/fournisseurs").with(RH)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TYPE_IMPORT_INCONNU")).andExpect(jsonPath("$.erreur").value("Type d'import inconnu."))
				.andExpect(jsonPath("$.message").value("Type d'import inconnu."));
		when(service.enregistrer(eq("collaborateurs"), any(), any())).thenThrow(new ReferentielMetierException("CHAMP_VERROUILLE",
				"Le champ « Matricule » est obligatoire.", 422, java.util.Map.of("champ", "MATRICULE")));
		mockMvc.perform(put(B).with(RH).contentType(MediaType.APPLICATION_JSON).content("{\"version\":6,\"champs\":[]}"))
				.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CHAMP_VERROUILLE"))
				.andExpect(jsonPath("$.champ").value("MATRICULE")).andExpect(jsonPath("$.message").exists());
	}

	@Test
	@DisplayName("P1 : If-None-Match égal à la version courante -> 304 sans corps")
	void notModified() throws Exception {
		mockMvc.perform(get(B).with(RH).header("If-None-Match", "\"6\"")).andExpect(status().isNotModified());
	}
}
