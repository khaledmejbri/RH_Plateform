package com.hr.presence.web;

import com.hr.presence.config.SecurityConfig;
import com.hr.presence.dto.PageResponse;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.service.PresencePointageService;
import com.hr.presence.service.PresenceQrService;
import com.hr.presence.service.PresenceSiteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PresenceAdminController.class)
@Import(SecurityConfig.class)
class PresenceAdminControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PresenceSiteService siteService;
	@MockitoBean
	private PresenceQrService qrService;
	@MockitoBean
	private PresencePointageService pointageService;
	@MockitoBean
	private com.hr.presence.service.TempsTravailService tempsTravailService;
	@MockitoBean
	private PresenceAccessService presenceAccess;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void directionPeutLireSites() throws Exception {
		when(siteService.lister(anyInt(), anyInt()))
				.thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

		mockMvc.perform(get("/api/rh/v1/admin/presence/sites")
						.with(jwt().authorities(
								new SimpleGrantedAuthority("ROLE_USER"),
								new SimpleGrantedAuthority("ROLE_DIRECTION"))))
				.andExpect(status().isOk());
	}

	@Test
	void directionNePeutPasCreerSite() throws Exception {
		mockMvc.perform(post("/api/rh/v1/admin/presence/sites")
						.contentType("application/json")
						.content("""
								{"code":"S1","libelle":"Site 1"}
								""")
						.with(jwt().authorities(
								new SimpleGrantedAuthority("ROLE_USER"),
								new SimpleGrantedAuthority("ROLE_DIRECTION"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void userSansBackofficeRefuseLectureAdmin() throws Exception {
		mockMvc.perform(get("/api/rh/v1/admin/presence/sites")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void rhPeutListerPointagesFiltresParCollaborateur() throws Exception {
		java.util.UUID collab = java.util.UUID.randomUUID();
		var vide = new PageResponse<com.hr.presence.dto.PointageResponse>(List.of(), 0, 20, 0, 0);
		when(pointageService.listerAdmin(any(), any(), any(), any(), any(), any(), eq(collab), anyInt(), anyInt()))
				.thenReturn(vide);
		when(tempsTravailService.annoterSequences(vide)).thenReturn(vide);

		mockMvc.perform(get("/api/rh/v1/admin/presence/pointages")
						.param("collaborateurId", collab.toString())
						.with(jwt().authorities(
								new SimpleGrantedAuthority("ROLE_USER"),
								new SimpleGrantedAuthority("ROLE_RH"))))
				.andExpect(status().isOk());
		verify(pointageService).listerAdmin(any(), any(), any(), any(), any(), any(), eq(collab), anyInt(), anyInt());
	}

	@Test
	void collaborateurIdInvalideRenvoie400() throws Exception {
		mockMvc.perform(get("/api/rh/v1/admin/presence/pointages")
						.param("collaborateurId", "pas-un-uuid")
						.with(jwt().authorities(
								new SimpleGrantedAuthority("ROLE_USER"),
								new SimpleGrantedAuthority("ROLE_RH"))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void userSansBackofficeRefuseListeDesPointages() throws Exception {
		mockMvc.perform(get("/api/rh/v1/admin/presence/pointages")
						.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void listeDesPointagesExposeEtatSequenceEnSnakeCase() throws Exception {
		var p = new com.hr.presence.dto.PointageResponse(java.util.UUID.randomUUID(),
				com.hr.presence.domain.PointageStatut.VALIDE, com.hr.presence.domain.PointageType.ENTREE,
				java.time.Instant.parse("2026-11-03T07:00:00Z"), java.util.UUID.randomUUID(), null, null, null, "c", null,
				null, null, null, null, null)
				.avecEtatSequence(com.hr.presence.domain.EtatSequencePointage.ENTREE_SANS_SORTIE);
		var page = new PageResponse<>(List.of(p), 0, 20, 1, 1);
		when(pointageService.listerAdmin(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(page);
		when(tempsTravailService.annoterSequences(page)).thenReturn(page);

		mockMvc.perform(get("/api/rh/v1/admin/presence/pointages")
						.with(jwt().authorities(
								new SimpleGrantedAuthority("ROLE_USER"),
								new SimpleGrantedAuthority("ROLE_DIRECTION"))))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.content[0].etat_sequence").value("ENTREE_SANS_SORTIE"));
	}
}
