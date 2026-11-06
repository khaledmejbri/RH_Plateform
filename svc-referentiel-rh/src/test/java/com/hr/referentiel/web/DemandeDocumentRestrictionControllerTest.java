package com.hr.referentiel.web;

import com.hr.referentiel.config.SecurityConfig;
import com.hr.referentiel.dto.CheckRestrictionDocumentResponse;
import com.hr.referentiel.service.DemandeDocumentAdministratifRhService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** D-F06.c / C-M02-03 : pré-contrôle des demandes de document en double, RBAC et erreurs. */
@WebMvcTest(controllers = DemandeDocumentAdministratifRhController.class)
@Import(SecurityConfig.class)
class DemandeDocumentRestrictionControllerTest {

	private static final String URL = "/api/rh/v1/demandes-documents-administratifs/check-restriction";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DemandeDocumentAdministratifRhService service;
	@MockitoBean
	private JwtDecoder jwtDecoder;
	/** L'application active @EnableCaching : la tranche WebMvc n'a pas de CacheManager. */
	@MockitoBean
	private org.springframework.cache.CacheManager cacheManager;

	private static RequestPostProcessor role(String r) {
		return jwt().authorities((GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r));
	}

	@Test
	void collaborateur_demandeOuverte_200_canRequestFalseEtMessage() throws Exception {
		when(service.verifierRestriction(eq("ATTESTATION_TRAVAIL"), any())).thenReturn(
				new CheckRestrictionDocumentResponse(false, "Vous avez déjà une demande en cours."));

		mockMvc.perform(get(URL).param("type_document", "ATTESTATION_TRAVAIL").with(role("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.can_request").value(false))
				.andExpect(jsonPath("$.message").isNotEmpty());
	}

	@Test
	void collaborateur_aucuneDemandeOuverte_200_canRequestTrueMessageNul() throws Exception {
		when(service.verifierRestriction(eq("AUTRE"), any())).thenReturn(new CheckRestrictionDocumentResponse(true, null));

		mockMvc.perform(get(URL).param("type_document", "AUTRE").with(role("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.can_request").value(true))
				.andExpect(jsonPath("$.message").doesNotExist());
	}

	@Test
	void typeInconnu_400() throws Exception {
		when(service.verifierRestriction(eq("INCONNU"), any()))
				.thenThrow(new IllegalArgumentException("Type de document inconnu."));

		mockMvc.perform(get(URL).param("type_document", "INCONNU").with(role("USER")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void sansJeton_401_serviceNonAppele() throws Exception {
		mockMvc.perform(get(URL).param("type_document", "AUTRE")).andExpect(status().isUnauthorized());
		verifyNoInteractions(service);
	}

	@Test
	void sansRoleUser_403_serviceNonAppele() throws Exception {
		mockMvc.perform(get(URL).param("type_document", "AUTRE").with(role("RH"))).andExpect(status().isForbidden());
		verifyNoInteractions(service);
	}
}
