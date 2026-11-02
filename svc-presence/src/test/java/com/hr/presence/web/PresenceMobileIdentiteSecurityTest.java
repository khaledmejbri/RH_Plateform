package com.hr.presence.web;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.SecurityConfig;
import com.hr.presence.dto.CreatePointageRequest;
import com.hr.presence.dto.PageResponse;
import com.hr.presence.dto.PointageResponse;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.service.FacialIdentiteService;
import com.hr.presence.service.PresencePointageService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pointage mobile : l'identité est la fiche du JWT (référentiel). Un {@code X-Collaborateur-Id} différent est refusé
 * (403 IDENTITE_INCOHERENTE) avant tout traitement, facial inactif ou non ; l'en-tête seul ne désigne jamais personne.
 * {@link FacialIdentiteService} est réel ; seuls le référentiel, l'accès et le service de pointage sont simulés.
 */
@WebMvcTest(controllers = PresenceMobileController.class)
@Import({SecurityConfig.class, FacialIdentiteService.class})
class PresenceMobileIdentiteSecurityTest {

	private static final String POINTAGES = "/api/rh/v1/mobile/presence/pointages";
	private static final String CORPS = "{\"qr_token\":\"x\",\"type\":\"ENTREE\",\"latitude\":36.8,\"longitude\":10.1,"
			+ "\"accuracy_metres\":5,\"device_id\":\"11111111-1111-4111-8111-111111111111\",\"plateforme\":\"ANDROID\"}";

	private final UUID fiche = UUID.randomUUID();
	private final UUID compte = UUID.randomUUID();

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PresencePointageService pointageService;
	@MockitoBean
	private ReferentielClient referentielClient;
	@MockitoBean(name = "presenceAccess")
	private PresenceAccessService presenceAccess;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	@BeforeEach
	void setUp() {
		when(referentielClient.monValideurPointage())
				.thenReturn(new ValideurPointage(fiche, compte, "Sarra", null, null, null));
	}

	private static RequestPostProcessor user() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Test
	void pointage_enteteFalsifie_403IdentiteIncoherente_rienNEstTraite() throws Exception {
		when(presenceAccess.collaborateurHeaderOuNull()).thenReturn(UUID.randomUUID().toString());

		mockMvc.perform(post(POINTAGES).contentType("application/json").content(CORPS).with(user()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("IDENTITE_INCOHERENTE"));
		verifyNoInteractions(pointageService);
	}

	@Test
	void pointage_enteteEgalALaFiche_ok() throws Exception {
		when(presenceAccess.collaborateurHeaderOuNull()).thenReturn(fiche.toString());

		mockMvc.perform(post(POINTAGES).contentType("application/json").content(CORPS).with(user()))
				.andExpect(status().isOk());
		verify(pointageService).pointer(eq(fiche.toString()), any(CreatePointageRequest.class));
	}

	@Test
	void pointage_sansEntete_ficheDuJwt_pasLeCompte() throws Exception {
		when(presenceAccess.collaborateurHeaderOuNull()).thenReturn(null);

		mockMvc.perform(post(POINTAGES).contentType("application/json").content(CORPS).with(user()))
				.andExpect(status().isOk());
		verify(pointageService).pointer(eq(fiche.toString()), any(CreatePointageRequest.class));
		verify(pointageService, never()).pointer(eq(compte.toString()), any(CreatePointageRequest.class));
	}

	@Test
	void pointage_enteteMalForme_403() throws Exception {
		when(presenceAccess.collaborateurHeaderOuNull()).thenThrow(new SecurityException("X-Collaborateur-Id invalide"));

		mockMvc.perform(post(POINTAGES).contentType("application/json").content(CORPS).with(user()))
				.andExpect(status().isForbidden());
		verifyNoInteractions(pointageService);
	}

	@Test
	void historique_enteteFalsifie_403_sansLecture() throws Exception {
		when(presenceAccess.collaborateurHeaderOuNull()).thenReturn(UUID.randomUUID().toString());

		mockMvc.perform(get(POINTAGES + "/me").with(user()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("IDENTITE_INCOHERENTE"));
		verifyNoInteractions(pointageService);
	}

	@Test
	void historique_sansEntete_ficheDuJwt() throws Exception {
		when(presenceAccess.collaborateurHeaderOuNull()).thenReturn(null);
		when(pointageService.historiqueMoi(fiche.toString(), 0, 20))
				.thenReturn(new PageResponse<PointageResponse>(List.of(), 0, 20, 0, 0));

		mockMvc.perform(get(POINTAGES + "/me").with(user())).andExpect(status().isOk());
		verify(pointageService).historiqueMoi(fiche.toString(), 0, 20);
	}
}