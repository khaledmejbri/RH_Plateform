package com.hr.presence.web;

import com.hr.presence.config.SecurityConfig;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.dto.PointagesEquipeDtos.PointageEquipeItem;
import com.hr.presence.dto.PointagesEquipeDtos.PointagesEquipePage;
import com.hr.presence.service.PresenceCollaborateurService;
import com.hr.presence.service.PresenceEquipePointagesService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PresenceEquipeMobileController.class)
@Import(SecurityConfig.class)
class PresenceEquipePointagesControllerSecurityTest {

	private static final String URL = "/api/rh/v1/mobile/presence/equipe/pointages";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PresenceCollaborateurService presenceService;
	@MockitoBean
	private PresenceEquipePointagesService equipePointagesService;
	@MockitoBean(name = "presenceAccess")
	private com.hr.presence.security.PresenceAccessService presenceAccess;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	private static RequestPostProcessor roles(String... roles) {
		return jwt().authorities(Arrays.stream(roles)
				.map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
				.toArray(GrantedAuthority[]::new));
	}

	@Test
	void pointagesEquipe_authentifieUser200_sansRoleUser403_sansJeton401() throws Exception {
		UUID collabId = UUID.randomUUID();
		UUID siteId = UUID.randomUUID();
		when(equipePointagesService.lister(any(), any(), any(), any(), any(), anyInt(), anyInt()))
				.thenReturn(new PointagesEquipePage(
						List.of(new PointageEquipeItem(
								UUID.randomUUID(), collabId, "Sarra Khemiri", "COL-WEB",
								PointageType.ENTREE, "Entrée", PointageStatut.VALIDE, "Pointage validé",
								Instant.parse("2026-11-24T08:15:12Z"), LocalDate.of(2026, 11, 24),
								siteId, "SIEGE", "Siège Social", false, null)),
						0, 20, 1, 1, LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 24), 2));

		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(URL).with(roles("RH"))).andExpect(status().isForbidden());

		mockMvc.perform(get(URL + "?du=2026-11-18&au=2026-11-24&q=sarra&page=0&size=20")
						.with(roles("USER", "RO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total_elements").value(1))
				.andExpect(jsonPath("$.effectif_equipe").value(2))
				.andExpect(jsonPath("$.elements[0].collaborateur_nom").value("Sarra Khemiri"))
				.andExpect(jsonPath("$.elements[0].collaborateur_matricule").value("COL-WEB"))
				.andExpect(jsonPath("$.elements[0].type").value("ENTREE"))
				.andExpect(jsonPath("$.elements[0].statut_libelle").value("Pointage validé"))
				.andExpect(jsonPath("$.elements[0].motif_rejet").doesNotExist());

		verify(equipePointagesService).lister(
				LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 24), "sarra", null, null, 0, 20);
	}

	@Test
	void pointagesEquipe_erreursMetier400_403_503() throws Exception {
		UUID horsPerimetre = UUID.randomUUID();
		when(equipePointagesService.lister(any(), any(), any(), eq(horsPerimetre), any(), anyInt(), anyInt()))
				.thenThrow(PresenceMetierException.forbidden("HORS_PERIMETRE", "Hors périmètre"));
		when(equipePointagesService.lister(eq(LocalDate.of(2026, 11, 25)), eq(LocalDate.of(2026, 11, 20)),
				any(), any(), any(), anyInt(), anyInt()))
				.thenThrow(PresenceMetierException.badRequest("PERIODE_INVALIDE", "Invalide"));
		when(equipePointagesService.lister(any(), any(), eq("trop-long"), any(), any(), anyInt(), anyInt()))
				.thenThrow(PresenceMetierException.badRequest("RECHERCHE_TROP_LONGUE", "Trop long"));
		when(equipePointagesService.lister(any(), any(), eq("panne"), any(), any(), anyInt(), anyInt()))
				.thenThrow(PresenceMetierException.indisponible("REFERENTIEL_INDISPONIBLE", "Down"));

		mockMvc.perform(get(URL + "?collaborateur_id=" + horsPerimetre).with(roles("USER")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("HORS_PERIMETRE"));

		mockMvc.perform(get(URL + "?du=2026-11-25&au=2026-11-20").with(roles("USER")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PERIODE_INVALIDE"));

		mockMvc.perform(get(URL + "?q=trop-long").with(roles("USER")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("RECHERCHE_TROP_LONGUE"));

		mockMvc.perform(get(URL + "?q=panne").with(roles("USER")))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("REFERENTIEL_INDISPONIBLE"));

		mockMvc.perform(get(URL + "?du=invalide").with(roles("USER")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
	}
}
