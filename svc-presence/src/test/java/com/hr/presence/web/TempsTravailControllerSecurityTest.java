package com.hr.presence.web;

import com.hr.presence.config.SecurityConfig;
import com.hr.presence.dto.TempsTravailDtos.PageLignes;
import com.hr.presence.dto.TempsTravailDtos.TempsTravailResponse;
import com.hr.presence.service.TempsTravailCsvService;
import com.hr.presence.service.TempsTravailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** D-P17.k : RH, ADMIN et DIRECTION consultent et exportent ; COLLABORATEUR, RO et RESPONSABLE reçoivent 403. */
@WebMvcTest(controllers = TempsTravailAdminController.class)
@Import(SecurityConfig.class)
class TempsTravailControllerSecurityTest {

	private static final String VUE = "/api/rh/v1/admin/presence/temps-travail";
	private static final String EXPORT = VUE + "/export";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TempsTravailService service;
	@MockitoBean
	private TempsTravailCsvService csvService;
	@MockitoBean(name = "presenceAccess")
	private com.hr.presence.security.PresenceAccessService presenceAccess;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	private static RequestPostProcessor roles(String... roles) {
		return jwt().authorities(java.util.Arrays.stream(roles)
				.map(r -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
				.toArray(org.springframework.security.core.GrantedAuthority[]::new));
	}

	@BeforeEach
	void setUp() {
		when(service.consulter(any(), any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(
				new TempsTravailResponse(new PageLignes(List.of(), 0, 0, 0, 50), 0, 0, 0, 0, List.of()));
		when(csvService.exporter(any(), any(), any(), any(), any(), any())).thenReturn("a;b".getBytes());
	}

	@Test
	void vue_rhDirectionAdmin_200_reponseAuContrat() throws Exception {
		for (String role : List.of("RH", "DIRECTION", "ADMIN")) {
			mockMvc.perform(get(VUE).param("du", "2026-11-01").param("au", "2026-11-05").with(roles(role)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.lignes.content").isArray())
					.andExpect(jsonPath("$.lignes.totalElements").value(0))
					.andExpect(jsonPath("$.lignes.totalPages").value(0))
					.andExpect(jsonPath("$.lignes.number").value(0))
					.andExpect(jsonPath("$.lignes.size").value(50))
					.andExpect(jsonPath("$.total_minutes").value(0))
					.andExpect(jsonPath("$.nb_lignes").value(0))
					.andExpect(jsonPath("$.synthese").isArray());
		}
	}

	@Test
	void vue_collaborateurRoResponsable_403_serviceNonAppele() throws Exception {
		for (String role : List.of("USER", "RO", "RESPONSABLE")) {
			mockMvc.perform(get(VUE).with(roles(role))).andExpect(status().isForbidden());
		}
		verifyNoInteractions(service);
	}

	@Test
	void vue_sansAuthentification_401() throws Exception {
		mockMvc.perform(get(VUE)).andExpect(status().isUnauthorized());
	}

	@Test
	void export_rhDirectionAdmin_200_csvAvecEnTeteDeFichier() throws Exception {
		for (String role : List.of("RH", "DIRECTION", "ADMIN")) {
			mockMvc.perform(get(EXPORT).with(roles(role)))
					.andExpect(status().isOk())
					.andExpect(header().string("Content-Disposition", "attachment; filename=\"temps-de-travail.csv\""))
					.andExpect(content().contentTypeCompatibleWith("text/csv"));
		}
	}

	@Test
	void export_collaborateurRoResponsable_403() throws Exception {
		for (String role : List.of("USER", "RO", "RESPONSABLE")) {
			mockMvc.perform(get(EXPORT).with(roles(role))).andExpect(status().isForbidden());
		}
		verifyNoInteractions(csvService);
	}

	@Test
	void vue_periodeTropLongue_400_codeRenvoye() throws Exception {
		when(service.consulter(any(), any(), any(), any(), any(), any(), anyInt(), anyInt())).thenThrow(
				PresenceMetierException.badRequest("PERIODE_TROP_LONGUE", "La période ne peut pas dépasser 31 jours"));

		mockMvc.perform(get(VUE).param("du", "2026-10-01").param("au", "2026-11-01").with(roles("RH")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PERIODE_TROP_LONGUE"));
	}

	@Test
	void export_periodeInvalide_400() throws Exception {
		when(csvService.exporter(any(), any(), any(), any(), any(), any())).thenThrow(
				PresenceMetierException.badRequest("PERIODE_INVALIDE", "La date de fin précède la date de début"));

		mockMvc.perform(get(EXPORT).param("du", "2026-11-03").param("au", "2026-11-02").with(roles("RH")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PERIODE_INVALIDE"));
	}

	@Test
	void identifiantMalForme_400_serviceNonAppele() throws Exception {
		for (String param : List.of("siteId", "collaborateurId", "uniteId")) {
			mockMvc.perform(get(VUE).param(param, "pas-un-uuid").with(roles("RH"))).andExpect(status().isBadRequest());
			mockMvc.perform(get(EXPORT).param(param, "pas-un-uuid").with(roles("RH"))).andExpect(status().isBadRequest());
		}
		mockMvc.perform(get(VUE).param("etat", "INCONNU").with(roles("RH"))).andExpect(status().isBadRequest());
		mockMvc.perform(get(VUE).param("du", "03/11/2026").with(roles("RH"))).andExpect(status().isBadRequest());
		org.mockito.Mockito.verify(service, org.mockito.Mockito.never())
				.consulter(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
	}
}
