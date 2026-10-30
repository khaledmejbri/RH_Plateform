package com.hr.presence.web;

import com.hr.presence.config.SecurityConfig;
import com.hr.presence.domain.StatutEnrolement;
import com.hr.presence.dto.CreatePointageRequest;
import com.hr.presence.dto.FacialAdminDtos.EnrolementDetail;
import com.hr.presence.dto.FacialAdminDtos.ReglageResponse;
import com.hr.presence.dto.FacialMobileDtos.EtatFacialMoi;
import com.hr.presence.dto.PageResponse;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.service.ConsentementFacialService;
import com.hr.presence.service.EnrolementFacialAdminService;
import com.hr.presence.service.EnrolementFacialService;
import com.hr.presence.service.EnrolementFacialService.ResultatEnrolement;
import com.hr.presence.service.PresencePointageService;
import com.hr.presence.service.ReglageFacialService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RBAC et codes HTTP de la vérification faciale (mobile USER, admin lecture RH/DIRECTION/ADMIN, écriture RH/ADMIN). */
@WebMvcTest(controllers = {FacialMobileController.class, FacialAdminController.class, PresenceMobileController.class})
@Import(SecurityConfig.class)
class FacialControllersSecurityTest {

	private static final String MOBILE = "/api/rh/v1/mobile/presence/verification-faciale";
	private static final String ADMIN = "/api/rh/v1/admin/presence/verification-faciale";
	private static final String UUID_OK = "11111111-1111-4111-8111-111111111111";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EnrolementFacialService enrolementService;
	@MockitoBean
	private ReglageFacialService reglageService;
	@MockitoBean
	private ConsentementFacialService consentementService;
	@MockitoBean
	private EnrolementFacialAdminService adminService;
	@MockitoBean
	private PresencePointageService pointageService;
	@MockitoBean(name = "presenceAccess")
	private PresenceAccessService presenceAccess;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	private static org.springframework.test.web.servlet.request.RequestPostProcessor role(String... roles) {
		return jwt().authorities(java.util.Arrays.stream(roles)
				.map(r -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
				.toArray(org.springframework.security.core.GrantedAuthority[]::new));
	}

	private static EtatFacialMoi etat() {
		return new EtatFacialMoi(true, false, null, "v1", StatutEnrolement.NON_ENROLE, null, false, true, null, false,
				true, 0, null, null);
	}

	private static ReglageResponse reglage() {
		return new ReglageResponse(com.hr.presence.domain.StatutReglageFacial.INACTIVE, null, 92, null, false, null, null, null, null);
	}

	// --- Mobile ---

	@Test
	void mobile_utilisateurLitSonEtat_200_sansSeuil() throws Exception {
		when(enrolementService.moi(any())).thenReturn(etat());

		mockMvc.perform(get(MOBILE + "/moi").param("device_id", UUID_OK).with(role("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statut").value("NON_ENROLE"))
				.andExpect(jsonPath("$.seuil_score").doesNotExist())
				.andExpect(jsonPath("$.seuil").doesNotExist());
	}

	@Test
	void mobile_sansRoleUser_403() throws Exception {
		mockMvc.perform(get(MOBILE + "/moi").with(role("RH"))).andExpect(status().isForbidden());
		mockMvc.perform(post(MOBILE + "/consentement/retrait").with(role("DIRECTION"))).andExpect(status().isForbidden());
	}

	@Test
	void mobile_sansAuthentification_401() throws Exception {
		mockMvc.perform(get(MOBILE + "/moi")).andExpect(status().isUnauthorized());
	}

	@Test
	void mobile_enrolement_201_puisRejeuIdempotent200() throws Exception {
		String corps = "{\"device_id\":\"" + UUID_OK + "\",\"version_consentement\":\"v1\",\"vivacite_reussie\":true,"
				+ "\"modele_version\":\"mfn-1\",\"idempotency_key\":\"" + UUID_OK + "\"}";
		when(enrolementService.enroler(any())).thenReturn(new ResultatEnrolement(etat(), true));
		mockMvc.perform(post(MOBILE + "/enrolement").contentType("application/json").content(corps).with(role("USER")))
				.andExpect(status().isCreated());

		when(enrolementService.enroler(any())).thenReturn(new ResultatEnrolement(etat(), false));
		mockMvc.perform(post(MOBILE + "/enrolement").contentType("application/json").content(corps).with(role("USER")))
				.andExpect(status().isOk());
	}

	@Test
	void mobile_enrolement_deviceIdNonUuid_422() throws Exception {
		String corps = "{\"device_id\":\"pas-un-uuid\",\"version_consentement\":\"v1\",\"vivacite_reussie\":true,"
				+ "\"modele_version\":\"mfn-1\",\"idempotency_key\":\"" + UUID_OK + "\"}";
		mockMvc.perform(post(MOBILE + "/enrolement").contentType("application/json").content(corps).with(role("USER")))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		verify(enrolementService, never()).enroler(any());
	}

	@Test
	void mobile_identiteIncoherente_403_codeNomme() throws Exception {
		when(enrolementService.moi(any())).thenThrow(new PresenceMetierException("IDENTITE_INCOHERENTE", "x", 403));

		mockMvc.perform(get(MOBILE + "/moi").with(role("USER")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("IDENTITE_INCOHERENTE"));
	}

	@Test
	void mobile_conflitMetier_409_codeNomme() throws Exception {
		when(enrolementService.retirerConsentement())
				.thenThrow(PresenceMetierException.conflict("AUCUN_CONSENTEMENT_ACTIF", "x"));

		mockMvc.perform(post(MOBILE + "/consentement/retrait").with(role("USER")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("AUCUN_CONSENTEMENT_ACTIF"))
				.andExpect(jsonPath("$.http_status").value(409));
	}

	@Test
	void mobile_raisonInconnue_400() throws Exception {
		mockMvc.perform(post(MOBILE + "/appareil-incompatible").contentType("application/json")
						.content("{\"device_id\":\"" + UUID_OK + "\",\"raison\":\"AUTRE\"}").with(role("USER")))
				.andExpect(status().isBadRequest());
	}

	// --- Admin ---

	@Test
	void admin_lecture_rhDirectionAdmin_200() throws Exception {
		when(reglageService.lire()).thenReturn(reglage());
		for (String role : new String[] {"RH", "DIRECTION", "ADMIN"}) {
			mockMvc.perform(get(ADMIN + "/reglage").with(role("USER", role))).andExpect(status().isOk());
		}
	}

	@Test
	void admin_lecture_utilisateurSimple_403() throws Exception {
		mockMvc.perform(get(ADMIN + "/reglage").with(role("USER"))).andExpect(status().isForbidden());
		mockMvc.perform(get(ADMIN + "/enrolements").with(role("USER"))).andExpect(status().isForbidden());
		mockMvc.perform(get(ADMIN + "/consentements").with(role("USER"))).andExpect(status().isForbidden());
	}

	@Test
	void admin_ecriture_direction_403_accessDenied() throws Exception {
		mockMvc.perform(put(ADMIN + "/reglage").contentType("application/json").content("{\"statut\":\"INACTIVE\"}")
						.with(role("USER", "DIRECTION")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(post(ADMIN + "/consentements").contentType("application/json")
						.content("{\"version\":\"v1\",\"texte\":\"" + "a".repeat(60) + "\"}").with(role("DIRECTION")))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(ADMIN + "/enrolements/" + UUID.randomUUID() + "/deblocage").contentType("application/json")
						.content("{\"motif\":\"motif valable\"}").with(role("DIRECTION")))
				.andExpect(status().isForbidden());
		verify(reglageService, never()).modifier(any());
	}

	@Test
	void admin_ecriture_rhEtAdmin_200() throws Exception {
		when(reglageService.modifier(any())).thenReturn(reglage());
		for (String role : new String[] {"RH", "ADMIN"}) {
			mockMvc.perform(put(ADMIN + "/reglage").contentType("application/json").content("{\"statut\":\"INACTIVE\"}")
							.with(role("USER", role)))
					.andExpect(status().isOk());
		}
	}

	@Test
	void admin_seuilHorsPlage_400() throws Exception {
		when(reglageService.modifier(any())).thenThrow(new PresenceMetierException("SEUIL_HORS_PLAGE", "x", 400));

		mockMvc.perform(put(ADMIN + "/reglage").contentType("application/json")
						.content("{\"statut\":\"INACTIVE\",\"seuil_score\":50}").with(role("RH")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SEUIL_HORS_PLAGE"));
	}

	@Test
	void admin_publication_texteTropCourt_422_puis201() throws Exception {
		mockMvc.perform(post(ADMIN + "/consentements").contentType("application/json")
						.content("{\"version\":\"v1\",\"texte\":\"court\"}").with(role("RH")))
				.andExpect(status().isUnprocessableEntity());

		when(consentementService.publier(any())).thenReturn(
				new com.hr.presence.dto.FacialAdminDtos.ConsentementVersionResume("v1", Instant.now(), "rh", true));
		mockMvc.perform(post(ADMIN + "/consentements").contentType("application/json")
						.content("{\"version\":\"v1\",\"texte\":\"" + "a".repeat(60) + "\"}").with(role("RH")))
				.andExpect(status().isCreated());
	}

	@Test
	void admin_deblocage_motifTropCourt_422() throws Exception {
		mockMvc.perform(post(ADMIN + "/enrolements/" + UUID.randomUUID() + "/deblocage").contentType("application/json")
						.content("{\"motif\":\"ok\"}").with(role("RH")))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void admin_autoDeblocage_403_codeNomme() throws Exception {
		when(adminService.debloquer(any(), any())).thenThrow(new PresenceMetierException("AUTO_DEBLOCAGE_INTERDIT", "x", 403));

		mockMvc.perform(post(ADMIN + "/enrolements/" + UUID.randomUUID() + "/deblocage").contentType("application/json")
						.content("{\"motif\":\"motif valable\"}").with(role("RH")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTO_DEBLOCAGE_INTERDIT"));
	}

	@Test
	void admin_listeEnrolements_directionEnLecture_200_statutInconnu400() throws Exception {
		when(adminService.lister(any(), any(), any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

		mockMvc.perform(get(ADMIN + "/enrolements").param("statut", "A_REENROLER").with(role("DIRECTION")))
				.andExpect(status().isOk());
		mockMvc.perform(get(ADMIN + "/enrolements").param("statut", "BIDON").with(role("DIRECTION")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void admin_detailIntrouvable_404() throws Exception {
		UUID id = UUID.randomUUID();
		when(adminService.detail(id)).thenThrow(PresenceMetierException.notFound("ENROLEMENT_INTROUVABLE", "x"));

		mockMvc.perform(get(ADMIN + "/enrolements/" + id).with(role("RH")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ENROLEMENT_INTROUVABLE"));
	}

	@Test
	void admin_detail_200() throws Exception {
		UUID id = UUID.randomUUID();
		when(adminService.detail(id)).thenReturn(new EnrolementDetail(id, "c", "f", "Nom", StatutEnrolement.ENROLE, "v1",
				null, null, null, "123456", null, false, 0, null, List.of()));

		mockMvc.perform(get(ADMIN + "/enrolements/" + id).with(role("DIRECTION")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.device_id_masque").value("123456"));
	}

	// --- Pointage : 409 POINTAGE_BLOQUE_VISAGE et 400 PREUVE_FACIALE_INVALIDE ---

	private static final String POINTAGE = "{\"qr_token\":\"x\",\"type\":\"ENTREE\",\"latitude\":36.8,\"longitude\":10.1,"
			+ "\"accuracy_metres\":5,\"device_id\":\"" + UUID_OK + "\",\"score_facial\":95,\"vivacite_reussie\":true,"
			+ "\"plateforme\":\"ANDROID\"}";

	@Test
	void pointage_blocageFacial_409_avecBloqueJusquA() throws Exception {
		Instant fin = Instant.parse("2026-11-10T09:15:00Z");
		when(presenceAccess.requireCurrentCollaborateurId()).thenReturn(UUID_OK);
		when(pointageService.pointer(any(), any(CreatePointageRequest.class)))
				.thenThrow(new PointageBloqueException("Pointage bloqué", fin));

		mockMvc.perform(post("/api/rh/v1/mobile/presence/pointages").contentType("application/json").content(POINTAGE)
						.with(role("USER")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("POINTAGE_BLOQUE_VISAGE"))
				.andExpect(jsonPath("$.http_status").value(409))
				.andExpect(jsonPath("$.bloque_jusqu_a").value("2026-11-10T09:15:00Z"));
	}

	@Test
	void pointage_preuveInvalide_400() throws Exception {
		when(presenceAccess.requireCurrentCollaborateurId()).thenReturn(UUID_OK);
		when(pointageService.pointer(any(), any(CreatePointageRequest.class)))
				.thenThrow(new PresenceMetierException("PREUVE_FACIALE_INVALIDE", "x", 400));

		mockMvc.perform(post("/api/rh/v1/mobile/presence/pointages").contentType("application/json").content(POINTAGE)
						.with(role("USER")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PREUVE_FACIALE_INVALIDE"));
	}

	@Test
	void pointage_plateformeInconnue_400() throws Exception {
		when(presenceAccess.requireCurrentCollaborateurId()).thenReturn(UUID_OK);

		mockMvc.perform(post("/api/rh/v1/mobile/presence/pointages").contentType("application/json")
						.content(POINTAGE.replace("ANDROID", "WINDOWS")).with(role("USER")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void mobile_demandeReenrolement_deviceIdNonUuidOuAbsent_422_sansAppel() throws Exception {
		for (String corps : new String[] {"{\"device_id\":\"x\"}", "{}"}) {
			mockMvc.perform(post(MOBILE + "/demande-reenrolement").contentType("application/json").content(corps).with(role("USER")))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		verify(enrolementService, never()).demanderReenrolement(any());
	}

	@Test
	void mobile_appareilIncompatible_deviceIdNonUuid_422_sansAppel() throws Exception {
		mockMvc.perform(post(MOBILE + "/appareil-incompatible").contentType("application/json")
						.content("{\"device_id\":\"x\",\"raison\":\"SANS_CAMERA_FRONTALE\"}").with(role("USER")))
				.andExpect(status().isUnprocessableEntity());
		verify(enrolementService, never()).signalerAppareilIncompatible(any());
	}
}
