package com.hr.referentiel.web;

import com.hr.referentiel.config.SecurityConfig;
import com.hr.referentiel.domain.StatutDemandeFormationRh;
import com.hr.referentiel.domain.StatutDocumentAdministratifDemandeRh;
import com.hr.referentiel.dto.DemandeDocumentAdministratifRhResponse;
import com.hr.referentiel.dto.DemandeFormationRhResponse;
import com.hr.referentiel.service.DemandeDocumentAdministratifRhService;
import com.hr.referentiel.service.DemandeFormationRhService;
import com.hr.referentiel.service.IdentiteSocieteClient;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
		DemandeDocumentAdministratifRhController.class,
		DemandeFormationRhController.class
})
@Import({SecurityConfig.class, ReferentielExceptionHandler.class})
class ComplementM02M05ControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DemandeDocumentAdministratifRhService documentService;
	@MockitoBean
	private DemandeFormationRhService formationService;
	@MockitoBean
	private IdentiteSocieteClient identiteSocieteClient;
	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private org.springframework.cache.CacheManager cacheManager;

	private static RequestPostProcessor collabJwt() {
		return jwt().jwt(j -> j.claim("societe_id", "00000000-0000-0000-0000-000000000001"))
				.authorities((GrantedAuthority) new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Test
	void documentComplement_sansJeton401_commentaireTropCourt_avecJeton200() throws Exception {
		UUID id = UUID.randomUUID();

		mockMvc.perform(post("/api/rh/v1/demandes-documents-administratifs/" + id + "/complement")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"commentaire\":\"Précision fournie\"}"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/rh/v1/demandes-documents-administratifs/" + id + "/complement")
						.with(collabJwt())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"commentaire\":\"abc\"}"))
				.andExpect(status().isBadRequest());

		DemandeDocumentAdministratifRhResponse rep = new DemandeDocumentAdministratifRhResponse(
				id, UUID.randomUUID(), "ATTESTATION_TRAVAIL", StatutDocumentAdministratifDemandeRh.EN_COURS,
				null, 48, null, null, null, null, null, null, null, false, false, null, null);
		rep.setWorkflowMode("MOTEUR");
		when(documentService.fournirComplement(eq(id), any(), any())).thenReturn(rep);

		mockMvc.perform(post("/api/rh/v1/demandes-documents-administratifs/" + id + "/complement")
						.with(collabJwt())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"commentaire\":\"Version française souhaitée\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statut").value("EN_COURS"));
	}

	@Test
	void formationComplement_sansJeton401_avecJeton200() throws Exception {
		UUID id = UUID.randomUUID();

		mockMvc.perform(post("/api/rh/v1/demandes-formations/" + id + "/complement")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"commentaire\":\"Devis joint\"}"))
				.andExpect(status().isUnauthorized());

		DemandeFormationRhResponse rep = new DemandeFormationRhResponse();
		rep.setIdentifiant(id);
		rep.setStatut(StatutDemandeFormationRh.EN_COURS);
		rep.setWorkflowMode("MOTEUR");
		when(formationService.fournirComplement(eq(id), any(), any())).thenReturn(rep);

		mockMvc.perform(post("/api/rh/v1/demandes-formations/" + id + "/complement")
						.with(collabJwt())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"commentaire\":\"Devis joint au dossier\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statut").value("EN_COURS"));
	}
}
