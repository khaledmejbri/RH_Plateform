package com.hr.referentiel.web;

import com.hr.referentiel.config.SecurityConfig;
import com.hr.referentiel.service.DemandeAdministrativeRhService;
import com.hr.referentiel.service.DemandeDocumentAdministratifRhService;
import com.hr.referentiel.service.DemandeFormationRhService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** D-L02 : recherche {@code q} par numéro sur les listes RH, RBAC inchangé (RH et DIRECTION lisent, USER non). */
@WebMvcTest(controllers = {DemandeAdministrativeRhController.class, DemandeDocumentAdministratifRhController.class,
		DemandeFormationRhController.class})
@Import(SecurityConfig.class)
class DemandeListeRhNumeroControllerTest {

	private static final String[] LISTES = {
			"/api/rh/v1/demandes-administratives/liste",
			"/api/rh/v1/demandes-documents-administratifs/file-attente",
			"/api/rh/v1/demandes-formations/liste"};

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private DemandeAdministrativeRhService demandeService;
	@MockitoBean
	private DemandeDocumentAdministratifRhService documentService;
	@MockitoBean
	private DemandeFormationRhService formationService;
	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private org.springframework.cache.CacheManager cacheManager;

	private static RequestPostProcessor role(String r) {
		return jwt().authorities((GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r));
	}

	@Test
	void rhEtDirectionLisentAvecRecherche() throws Exception {
		when(demandeService.listerToutPourRh(any(), any(), any(), eq("dem-202611-0001"), any(), any())).thenReturn(List.of());
		when(documentService.fileAttentePourRh("doc-202611-0001")).thenReturn(List.of());
		when(formationService.listerPourRh(any(), eq("for-202611-0001"), any())).thenReturn(List.of());
		for (String role : new String[] {"RH", "DIRECTION"}) {
			mockMvc.perform(get(LISTES[0]).param("q", "dem-202611-0001").with(role(role))).andExpect(status().isOk());
			mockMvc.perform(get(LISTES[1]).param("q", "doc-202611-0001").with(role(role))).andExpect(status().isOk());
			mockMvc.perform(get(LISTES[2]).param("q", "for-202611-0001").with(role(role))).andExpect(status().isOk());
		}
		verify(demandeService, org.mockito.Mockito.times(2)).listerToutPourRh(any(), any(), any(), eq("dem-202611-0001"), any(), any());
	}

	@Test
	void collaborateurRecoit403() throws Exception {
		for (String url : LISTES) {
			mockMvc.perform(get(url).param("q", "DEM-202611-0001").with(role("USER"))).andExpect(status().isForbidden());
		}
		verifyNoInteractions(demandeService, documentService, formationService);
	}

	@Test
	void anonymeRecoit401() throws Exception {
		for (String url : LISTES) {
			mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
		}
	}
}
