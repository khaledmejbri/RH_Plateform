package com.hr.referentiel.web;

import com.hr.referentiel.config.SecurityConfig;
import com.hr.referentiel.dto.ParametreDto.Libelles;
import com.hr.referentiel.dto.ParametreDto.LibelleVue;
import com.hr.referentiel.dto.ParametreDto.ValeurResponse;
import com.hr.referentiel.service.AuteurParametre;
import com.hr.referentiel.service.AuteurParametreResolver;
import com.hr.referentiel.service.ParametreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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

/** Ticket 3B : droits par rôle (RH, ADMIN, DIRECTION, collaborateur, RO), libellés et ETag, erreurs HTTP. */
@WebMvcTest(controllers = {ParametreController.class})
@Import(SecurityConfig.class)
class ParametreControllerTest {

	private static final String B = "/api/referentiel/v1/parametres";
	private static final String VALEURS = B + "/referentiels/TYPE_DOCUMENT/valeurs";

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private ParametreService service;
	@MockitoBean
	private AuteurParametreResolver auteurResolver;
	@MockitoBean
	private JwtDecoder jwtDecoder;
	/** L'application active @EnableCaching : la tranche WebMvc n'a pas de CacheManager. */
	@MockitoBean
	private org.springframework.cache.CacheManager cacheManager;

	private static RequestPostProcessor role(String... roles) {
		return jwt().jwt(j -> j.claim("identifiant_utilisateur", UUID.randomUUID().toString()))
				.authorities(java.util.Arrays.stream(roles)
						.map(r -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
						.toArray(org.springframework.security.core.GrantedAuthority[]::new));
	}

	private static ValeurResponse valeur() {
		return new ValeurResponse("ATTESTATION_STAGE", "Attestation de stage", null, null, 8, true, false, "VALEUR",
				0, 0, false, 72, null, null);
	}

	private void auteur() {
		when(auteurResolver.depuis(any())).thenReturn(new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi"));
	}

	@Test
	@DisplayName("corps JSON absent ou illisible : 400 CORPS_INVALIDE (et non 403 via /error) sur POST, PUT, désactiver, réglages")
	void corpsAbsentOuIllisible400() throws Exception {
		auteur();
		var rh = role("USER", "RH");
		mockMvc.perform(post(VALEURS).with(rh).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CORPS_INVALIDE"));
		mockMvc.perform(put(VALEURS + "/X").with(rh).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CORPS_INVALIDE"));
		mockMvc.perform(post(VALEURS + "/X/desactiver").with(rh).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CORPS_INVALIDE"));
		mockMvc.perform(put(B + "/reglages/CLE").with(rh).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CORPS_INVALIDE"));
		mockMvc.perform(post(VALEURS).with(rh).contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CORPS_INVALIDE"));
	}

	@Test
	@DisplayName("DIRECTION : lectures 200 (liste, valeurs, journal), toute écriture 403")
	void direction() throws Exception {
		when(service.valeurs("TYPE_DOCUMENT")).thenReturn(List.of(valeur()));
		mockMvc.perform(get(VALEURS).with(role("USER", "DIRECTION"))).andExpect(status().isOk());
		mockMvc.perform(get(B + "/referentiels").with(role("USER", "DIRECTION"))).andExpect(status().isOk());
		mockMvc.perform(get(B + "/referentiels/TYPE_DOCUMENT/journal").with(role("USER", "DIRECTION")))
				.andExpect(status().isOk());
		mockMvc.perform(get(B + "/reglages").with(role("USER", "DIRECTION"))).andExpect(status().isOk());

		String corps = "{\"libelle\":\"Test\",\"sla_heures\":24}";
		mockMvc.perform(post(VALEURS).with(role("USER", "DIRECTION")).contentType(MediaType.APPLICATION_JSON)
				.content(corps)).andExpect(status().isForbidden());
		mockMvc.perform(put(VALEURS + "/X").with(role("USER", "DIRECTION")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"libelle\":\"T\",\"version\":0}")).andExpect(status().isForbidden());
		mockMvc.perform(post(VALEURS + "/X/desactiver").with(role("USER", "DIRECTION"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isForbidden());
		mockMvc.perform(delete(VALEURS + "/X").with(role("USER", "DIRECTION"))).andExpect(status().isForbidden());
		mockMvc.perform(put(B + "/referentiels/TYPE_DOCUMENT/ordre").with(role("USER", "DIRECTION"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"codes\":[]}")).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("collaborateur et RO : lecture admin 403, écriture 403, aucun appel au service")
	void collaborateurEtRo() throws Exception {
		for (String r : List.of("COLLABORATEUR", "RO", "RESPONSABLE")) {
			mockMvc.perform(get(VALEURS).with(role("USER", r))).andExpect(status().isForbidden());
			mockMvc.perform(post(VALEURS).with(role("USER", r)).contentType(MediaType.APPLICATION_JSON)
					.content("{\"libelle\":\"Test\",\"sla_heures\":24}")).andExpect(status().isForbidden());
			mockMvc.perform(delete(VALEURS + "/X").with(role("USER", r))).andExpect(status().isForbidden());
		}
		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("RH et ADMIN : mêmes droits d'écriture (201 / 200 / 204), l'auteur vient du JWT")
	void rhEtAdmin() throws Exception {
		auteur();
		when(service.creer(eq("TYPE_DOCUMENT"), any(), any())).thenReturn(valeur());
		when(service.modifier(eq("TYPE_DOCUMENT"), eq("ATTESTATION_STAGE"), any(), eq(0L), any())).thenReturn(valeur());
		when(service.desactiver(eq("TYPE_DOCUMENT"), eq("ATTESTATION_STAGE"), eq(0L), any())).thenReturn(valeur());
		for (String r : List.of("RH", "ADMIN")) {
			mockMvc.perform(post(VALEURS).with(role("USER", r)).contentType(MediaType.APPLICATION_JSON)
							.content("{\"libelle\":\"Attestation de stage\",\"sla_heures\":72}"))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.code").value("ATTESTATION_STAGE"))
					.andExpect(jsonPath("$.sla_heures").value(72))
					.andExpect(jsonPath("$.deja_utilisee").value(false));
			mockMvc.perform(put(VALEURS + "/ATTESTATION_STAGE").with(role("USER", r))
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"code\":\"AUTRE_CODE\",\"libelle\":\"Attestation de stage\",\"version\":0}"))
					.andExpect(status().isOk());
			mockMvc.perform(post(VALEURS + "/ATTESTATION_STAGE/desactiver").with(role("USER", r))
					.contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isOk());
			mockMvc.perform(delete(VALEURS + "/ATTESTATION_STAGE?version=0").with(role("USER", r)))
					.andExpect(status().isNoContent());
		}
	}

	@Test
	@DisplayName("PUT sans version : 400 VERSION_OBLIGATOIRE ; désactiver sans version : 400")
	void versionObligatoire() throws Exception {
		auteur();
		mockMvc.perform(put(VALEURS + "/X").with(role("USER", "RH")).contentType(MediaType.APPLICATION_JSON)
						.content("{\"libelle\":\"Test\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VERSION_OBLIGATOIRE"));
		mockMvc.perform(post(VALEURS + "/X/desactiver").with(role("USER", "RH"))
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("erreurs métier rendues avec code, erreur et message ; 409 PARAMETRE_UTILISE porte utilisations")
	void erreursMetier() throws Exception {
		auteur();
		org.mockito.Mockito.doThrow(
				new ReferentielMetierException("PARAMETRE_UTILISE", "Utilisée 3 fois : désactivez-la.", 409,
						Map.of("utilisations", 3L, "deja_utilisee", true)))
				.when(service).supprimer(eq("TYPE_DOCUMENT"), eq("X"), any(), any());
		doThrow405();
		mockMvc.perform(delete(VALEURS + "/X").with(role("USER", "RH")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PARAMETRE_UTILISE"))
				.andExpect(jsonPath("$.message").value("Utilisée 3 fois : désactivez-la."))
				.andExpect(jsonPath("$.utilisations").value(3));
		mockMvc.perform(post(B + "/referentiels/STATUT_DEMANDE/valeurs").with(role("USER", "RH"))
						.contentType(MediaType.APPLICATION_JSON).content("{\"libelle\":\"Nouveau statut\"}"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("PARAMETRE_COMPORTEMENT"));
	}

	private void doThrow405() {
		when(service.creer(eq("STATUT_DEMANDE"), any(), any())).thenThrow(
				ReferentielMetierException.methodNotAllowed("PARAMETRE_COMPORTEMENT", "Liste pilotée par le serveur."));
	}

	@Test
	@DisplayName("libellés : tout utilisateur authentifié, ETag + no-cache, 304 si inchangé, sans jeton 401")
	void libelles() throws Exception {
		Libelles l = new Libelles("W/\"abc123\"", Map.of("TYPE_CONGE", List.of(
				new LibelleVue("ANNUEL", "Congé annuel", null, "NEUTRE", 1, true, null, false))));
		when(service.libelles(anyBoolean(), anyBoolean())).thenReturn(l);

		mockMvc.perform(get(B + "/libelles").with(role("USER")))
				.andExpect(status().isOk())
				.andExpect(header().string("ETag", "W/\"abc123\""))
				.andExpect(header().string("Cache-Control", "no-cache"))
				.andExpect(jsonPath("$.referentiels.TYPE_CONGE[0].libelle").value("Congé annuel"));
		mockMvc.perform(get(B + "/libelles").with(role("USER")).header("If-None-Match", "W/\"abc123\""))
				.andExpect(status().isNotModified());
		mockMvc.perform(get(B + "/libelles")).andExpect(status().isUnauthorized());
		// un collaborateur n'est pas back-office : le service reçoit backOffice = false
		verify(service, org.mockito.Mockito.atLeastOnce()).libelles(false, false);
	}

	@Test
	@DisplayName("réglage inexistant du service référentiel : 404 ; DIRECTION ne peut pas écrire un réglage")
	void reglages() throws Exception {
		when(service.modifierReglage("inconnu")).thenThrow(
				ReferentielMetierException.notFound("REGLAGE_INCONNU", "Réglage inconnu."));
		mockMvc.perform(put(B + "/reglages/inconnu").with(role("USER", "RH")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"valeur\":1,\"version\":0}")).andExpect(status().isNotFound());
		mockMvc.perform(put(B + "/reglages/inconnu").with(role("USER", "DIRECTION"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"valeur\":1,\"version\":0}"))
				.andExpect(status().isForbidden());
	}
}
