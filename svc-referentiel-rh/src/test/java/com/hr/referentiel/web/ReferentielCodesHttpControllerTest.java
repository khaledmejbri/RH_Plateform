package com.hr.referentiel.web;

import com.hr.referentiel.config.SecurityConfig;
import com.hr.referentiel.service.DemandeAdministrativeRhService;
import com.hr.referentiel.service.DemandeDocumentAdministratifRhService;
import com.hr.referentiel.service.DemandeFormationRhService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Codes HTTP : demande inexistante 404, accès à un document d'autrui 403, demande déjà décidée 409, route inconnue 404. */
@WebMvcTest(controllers = {DemandeAdministrativeRhController.class, DemandeDocumentAdministratifRhController.class,
		DemandeFormationRhController.class})
@Import(SecurityConfig.class)
class ReferentielCodesHttpControllerTest {

	private static final String DEM = "/api/rh/v1/demandes-administratives/";
	private static final String DOC = "/api/rh/v1/demandes-documents-administratifs/";
	private static final String FOR = "/api/rh/v1/demandes-formations/";

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
	void demandeInexistante404() throws Exception {
		UUID id = UUID.randomUUID();
		when(demandeService.obtenir(any(), any(), anyBoolean()))
				.thenThrow(ReferentielMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable."));
		when(formationService.obtenir(any(), any(), anyBoolean()))
				.thenThrow(ReferentielMetierException.notFound("DEMANDE_INTROUVABLE", "Demande de formation introuvable."));
		when(documentService.obtenir(any(), any(), anyBoolean()))
				.thenThrow(ReferentielMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable."));
		for (String base : new String[] {DEM, FOR, DOC}) {
			mockMvc.perform(get(base + id).with(role("RH")))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("DEMANDE_INTROUVABLE"));
		}
	}

	@Test
	void documentDAutrui403() throws Exception {
		when(documentService.obtenir(any(), any(), anyBoolean())).thenThrow(new AccessDeniedException("Accès refusé."));
		when(formationService.obtenir(any(), any(), anyBoolean())).thenThrow(new AccessDeniedException("Accès refusé."));
		mockMvc.perform(get(DOC + UUID.randomUUID()).with(role("USER"))).andExpect(status().isForbidden());
		mockMvc.perform(get(FOR + UUID.randomUUID()).with(role("USER"))).andExpect(status().isForbidden());
	}

	@Test
	void demandeDejaDecidee409() throws Exception {
		when(demandeService.validerRrh(any(), any())).thenThrow(ReferentielMetierException.conflict(
				"DEMANDE_DEJA_DECIDEE", "Cette demande est déjà décidée. Statut actuel : « Approuvée »."));
		mockMvc.perform(post(DEM + UUID.randomUUID() + "/valider-rrh").with(role("RH")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DEMANDE_DEJA_DECIDEE"))
				.andExpect(jsonPath("$.erreur").value(org.hamcrest.Matchers.containsString("Approuvée")));
	}

	@Test
	void routeInconnue404() throws Exception {
		mockMvc.perform(get("/api/rh/v1/route-qui-nexiste-pas").with(role("RH"))).andExpect(status().isNotFound());
	}
}
