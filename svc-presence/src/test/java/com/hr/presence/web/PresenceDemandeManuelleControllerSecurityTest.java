package com.hr.presence.web;

import com.hr.presence.config.SecurityConfig;
import com.hr.presence.service.PresenceDemandeManuelleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-P04 (SCRUM-38) : RBAC des demandes de pointage manuel.
 */
@WebMvcTest(controllers = PresenceDemandeManuelleController.class)
@Import(SecurityConfig.class)
class PresenceDemandeManuelleControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PresenceDemandeManuelleService service;
	@MockitoBean(name = "presenceAccess")
	private com.hr.presence.security.PresenceAccessService presenceAccess;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void collaborateurVoitSaFileAValider() throws Exception {
		when(service.aValider()).thenReturn(List.of());
		mockMvc.perform(get("/api/rh/v1/mobile/presence/demandes-manuelles/a-valider")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isOk());
	}

	@Test
	void collaborateurNeVoitPasLaListeRh() throws Exception {
		mockMvc.perform(get("/api/rh/v1/admin/presence/demandes-manuelles")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void directionLitLaListeRh() throws Exception {
		when(service.listerAdmin(any())).thenReturn(List.of());
		mockMvc.perform(get("/api/rh/v1/admin/presence/demandes-manuelles")
						.with(jwt().authorities(
								new SimpleGrantedAuthority("ROLE_USER"),
								new SimpleGrantedAuthority("ROLE_DIRECTION"))))
				.andExpect(status().isOk());
	}

	@Test
	void validationRefuseeParLeServiceDonne403() throws Exception {
		UUID id = UUID.randomUUID();
		when(service.valider(id, null)).thenThrow(new SecurityException("Vous ne pouvez pas valider votre propre pointage manuel"));
		mockMvc.perform(post("/api/rh/v1/mobile/presence/demandes-manuelles/" + id + "/valider")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void validerTransmetLeCommentaire_mobileEtAdmin_etAccepteUnCorpsAbsent() throws Exception {
		UUID id = UUID.randomUUID();
		when(service.valider(any(), any())).thenReturn(null);
		mockMvc.perform(post("/api/rh/v1/mobile/presence/demandes-manuelles/" + id + "/valider")
						.contentType("application/json").content("{\"commentaire\":\"Vu avec le chef de site\"}")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isOk());
		org.mockito.Mockito.verify(service).valider(id, "Vu avec le chef de site");
		mockMvc.perform(post("/api/rh/v1/admin/presence/demandes-manuelles/" + id + "/valider")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_RH"))))
				.andExpect(status().isOk());
		org.mockito.Mockito.verify(service).valider(id, null);
	}

	@Test
	void validerSansRoleConnu403_etCommentaireInvalideRemonteEn400() throws Exception {
		UUID id = UUID.randomUUID();
		mockMvc.perform(post("/api/rh/v1/admin/presence/demandes-manuelles/" + id + "/valider")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DIRECTION"))))
				.andExpect(status().isForbidden());
		when(service.valider(id, "court")).thenThrow(
				PresenceMetierException.badRequest("COMMENTAIRE_TROP_COURT", "Le commentaire doit faire au moins 10 caractères."));
		mockMvc.perform(post("/api/rh/v1/mobile/presence/demandes-manuelles/" + id + "/valider")
						.contentType("application/json").content("{\"commentaire\":\"court\"}")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void creationSansCorpsValide_laValidationEstFaiteParLeService() throws Exception {
		when(service.creer(any())).thenThrow(PresenceMetierException.forbidden("HORS_PERIMETRE", "x"));
		mockMvc.perform(post("/api/rh/v1/mobile/presence/demandes-manuelles")
						.contentType("application/json").content("{\"pointage_rejete_id\":\"" + UUID.randomUUID()
								+ "\",\"justification\":\"GPS faible ici\"}")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void refusSansMotif422() throws Exception {
		mockMvc.perform(post("/api/rh/v1/mobile/presence/demandes-manuelles/" + UUID.randomUUID() + "/refuser")
						.contentType("application/json")
						.content("{\"motif\":\"\"}")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isUnprocessableEntity());
	}
}
