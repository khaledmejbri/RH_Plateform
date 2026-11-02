package com.hr.presence.web;

import com.hr.presence.config.SecurityConfig;
import com.hr.presence.dto.HoraireDtos.HoraireSiteResponse;
import com.hr.presence.service.FenetreMobileService;
import com.hr.presence.service.HeuresSupCreationService;
import com.hr.presence.service.HeuresSupCsvService;
import com.hr.presence.service.HeuresSupService;
import com.hr.presence.service.HoraireSiteService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC des horaires de site et des heures supplémentaires (SCRUM-48, 50, 51) : RH / ADMIN écrivent, DIRECTION lit,
 * RO et chef de département demandent, un simple collaborateur ne fait que consulter ses propres heures.
 */
@WebMvcTest(controllers = {HoraireSiteController.class, HeuresSupAdminController.class, HeuresSupMobileController.class})
@Import(SecurityConfig.class)
class HeuresSupControllersSecurityTest {

	private static final String ADMIN = "/api/rh/v1/admin/presence";
	private static final String MOBILE = "/api/rh/v1/mobile/presence";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private HoraireSiteService horaireService;
	@MockitoBean
	private HeuresSupService heuresSupService;
	@MockitoBean
	private HeuresSupCreationService creationService;
	@MockitoBean
	private HeuresSupCsvService csvService;
	@MockitoBean
	private FenetreMobileService fenetreMobileService;
	@MockitoBean(name = "presenceAccess")
	private com.hr.presence.security.PresenceAccessService presenceAccess;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	private final UUID id = UUID.randomUUID();

	private static RequestPostProcessor roles(String... roles) {
		return jwt().authorities(java.util.Arrays.stream(roles)
				.map(r -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
				.toArray(org.springframework.security.core.GrantedAuthority[]::new));
	}

	private static final String CORPS_DEMANDE = "{\"collaborateur_ids\":[\"" + UUID.randomUUID() + "\"],\"site_id\":\""
			+ UUID.randomUUID() + "\",\"date_debut\":\"2026-11-03\",\"ouverture_avant_minutes\":120,\"motif\":\"Cloture\"}";

	private static final String CORPS_HORAIRE = "{\"tolerance_avant_minutes\":60,\"tolerance_apres_minutes\":90,"
			+ "\"jours\":[{\"jour\":\"LUNDI\",\"debut\":\"06:00\",\"fin\":\"15:00\"}]}";

	// --- Horaire du site (SCRUM-48) ---

	@Test
	void horaire_lecture_rhDirectionAdmin_200_collaborateurEtRo_403() throws Exception {
		when(horaireService.lire(id)).thenReturn(new HoraireSiteResponse(id, 60, 60, List.of(), false, null, null));
		for (String role : List.of("RH", "DIRECTION", "ADMIN")) {
			mockMvc.perform(get(ADMIN + "/sites/" + id + "/horaire").with(roles(role)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.tolerance_avant_minutes").value(60));
		}
		for (String role : List.of("USER", "RO", "RESPONSABLE")) {
			mockMvc.perform(get(ADMIN + "/sites/" + id + "/horaire").with(roles(role))).andExpect(status().isForbidden());
		}
	}

	@Test
	void horaire_ecriture_rhEtAdmin_200_directionCsWebColWeb_403() throws Exception {
		when(horaireService.remplacer(any(), any())).thenReturn(new HoraireSiteResponse(id, 60, 90, List.of(), true, "DRH01", null));
		for (String role : List.of("RH", "ADMIN")) {
			mockMvc.perform(put(ADMIN + "/sites/" + id + "/horaire").contentType("application/json").content(CORPS_HORAIRE)
					.with(roles(role))).andExpect(status().isOk());
		}
		for (String role : List.of("DIRECTION", "RO", "USER")) {
			mockMvc.perform(put(ADMIN + "/sites/" + id + "/horaire").contentType("application/json").content(CORPS_HORAIRE)
					.with(roles(role))).andExpect(status().isForbidden());
		}
		verify(horaireService, org.mockito.Mockito.times(2)).remplacer(any(), any());
	}

	@Test
	void horaire_suppression_rhEtAdmin_204_directionEtUser_403() throws Exception {
		mockMvc.perform(delete(ADMIN + "/sites/" + id + "/horaire").with(roles("RH"))).andExpect(status().isNoContent());
		mockMvc.perform(delete(ADMIN + "/sites/" + id + "/horaire").with(roles("ADMIN"))).andExpect(status().isNoContent());
		mockMvc.perform(delete(ADMIN + "/sites/" + id + "/horaire").with(roles("DIRECTION"))).andExpect(status().isForbidden());
		mockMvc.perform(delete(ADMIN + "/sites/" + id + "/horaire").with(roles("USER"))).andExpect(status().isForbidden());
	}

	@Test
	void horaire_historique_directionLit_userNon() throws Exception {
		mockMvc.perform(get(ADMIN + "/sites/" + id + "/horaire/historique").with(roles("DIRECTION"))).andExpect(status().isOk());
		mockMvc.perform(get(ADMIN + "/sites/" + id + "/horaire/historique").with(roles("USER"))).andExpect(status().isForbidden());
	}

	@Test
	void horaire_erreurMetier400_toleranceInvalide_codeRenvoye() throws Exception {
		when(horaireService.remplacer(any(), any())).thenThrow(
				PresenceMetierException.badRequest("TOLERANCE_INVALIDE", "La tolérance doit être..."));

		mockMvc.perform(put(ADMIN + "/sites/" + id + "/horaire").contentType("application/json").content(CORPS_HORAIRE)
						.with(roles("RH")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOLERANCE_INVALIDE"));
	}

	@Test
	void horaire_sansAuthentification_401() throws Exception {
		mockMvc.perform(get(ADMIN + "/sites/" + id + "/horaire")).andExpect(status().isUnauthorized());
	}

	// --- Heures sup côté Plateforme RH (SCRUM-50, 51) ---

	@Test
	void adminDemandes_lecture_directionOk_userEtRo_403() throws Exception {
		when(heuresSupService.lister(any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.anyInt())).thenReturn(null);
		mockMvc.perform(get(ADMIN + "/heures-sup/demandes").with(roles("DIRECTION"))).andExpect(status().isOk());
		mockMvc.perform(get(ADMIN + "/heures-sup/demandes/" + id).with(roles("DIRECTION"))).andExpect(status().isOk());
		for (String role : List.of("USER", "RO", "RESPONSABLE")) {
			mockMvc.perform(get(ADMIN + "/heures-sup/demandes").with(roles(role))).andExpect(status().isForbidden());
			mockMvc.perform(get(ADMIN + "/heures-sup/demandes/" + id).with(roles(role))).andExpect(status().isForbidden());
		}
	}

	@Test
	void adminDecisions_rhEtAdmin_200_directionRoEtCollaborateur_403() throws Exception {
		for (String role : List.of("RH", "ADMIN")) {
			mockMvc.perform(post(ADMIN + "/heures-sup/demandes/" + id + "/valider").with(roles(role))).andExpect(status().isOk());
			mockMvc.perform(post(ADMIN + "/heures-sup/demandes/" + id + "/refuser").contentType("application/json")
					.content("{\"motif\":\"non\"}").with(roles(role))).andExpect(status().isOk());
			mockMvc.perform(post(ADMIN + "/heures-sup/demandes/" + id + "/annuler").with(roles(role))).andExpect(status().isOk());
		}
		for (String role : List.of("DIRECTION", "RO", "RESPONSABLE", "USER")) {
			mockMvc.perform(post(ADMIN + "/heures-sup/demandes/" + id + "/valider").with(roles(role))).andExpect(status().isForbidden());
			mockMvc.perform(post(ADMIN + "/heures-sup/demandes/" + id + "/refuser").contentType("application/json")
					.content("{\"motif\":\"non\"}").with(roles(role))).andExpect(status().isForbidden());
			mockMvc.perform(post(ADMIN + "/heures-sup/demandes/" + id + "/annuler").with(roles(role))).andExpect(status().isForbidden());
		}
	}

	@Test
	void adminDecision_autoValidation403_etStatutIncompatible409_traduitsParLeHandler() throws Exception {
		when(heuresSupService.valider(id)).thenThrow(
				PresenceMetierException.forbidden("AUTO_VALIDATION_INTERDITE", "Vous ne pouvez pas..."));
		mockMvc.perform(post(ADMIN + "/heures-sup/demandes/" + id + "/valider").with(roles("RH")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTO_VALIDATION_INTERDITE"));

		when(heuresSupService.refuser(any(), any())).thenThrow(
				PresenceMetierException.conflict("STATUT_INCOMPATIBLE", "déjà décidée"));
		mockMvc.perform(post(ADMIN + "/heures-sup/demandes/" + id + "/refuser").contentType("application/json")
						.content("{\"motif\":\"non\"}").with(roles("RH")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STATUT_INCOMPATIBLE"));
	}

	@Test
	void adminCreationDirecte_rhEtAdmin_201_directionEtRo_403() throws Exception {
		for (String role : List.of("RH", "ADMIN")) {
			mockMvc.perform(post(ADMIN + "/heures-sup/demandes").contentType("application/json").content(CORPS_DEMANDE)
					.with(roles(role))).andExpect(status().isCreated());
		}
		for (String role : List.of("DIRECTION", "RO", "USER")) {
			mockMvc.perform(post(ADMIN + "/heures-sup/demandes").contentType("application/json").content(CORPS_DEMANDE)
					.with(roles(role))).andExpect(status().isForbidden());
		}
		verify(creationService, org.mockito.Mockito.times(2)).creerDirecte(any());
	}

	@Test
	void adminCreationDirecte_listeVide_422() throws Exception {
		mockMvc.perform(post(ADMIN + "/heures-sup/demandes").contentType("application/json")
						.content("{\"collaborateur_ids\":[],\"site_id\":\"" + UUID.randomUUID() + "\",\"date_debut\":\"2026-11-03\"}")
						.with(roles("RH")))
				.andExpect(status().isUnprocessableEntity());
		verify(creationService, never()).creerDirecte(any());
	}

	@Test
	void adminComptees_etExportCsv_directionEnLecture_userRefuse() throws Exception {
		when(csvService.exporter(any(), any(), any(), any(), any())).thenReturn("﻿\"Matricule\"".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		mockMvc.perform(get(ADMIN + "/heures-sup/comptees").with(roles("DIRECTION"))).andExpect(status().isOk());
		mockMvc.perform(get(ADMIN + "/heures-sup/comptees/export.csv").with(roles("DIRECTION")))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
				.andExpect(content().contentTypeCompatibleWith("text/csv"));
		mockMvc.perform(get(ADMIN + "/heures-sup/comptees").with(roles("USER"))).andExpect(status().isForbidden());
		mockMvc.perform(get(ADMIN + "/heures-sup/comptees/export.csv").with(roles("RO"))).andExpect(status().isForbidden());
	}

	@Test
	void adminComptees_periodeTropLongue400_traduite() throws Exception {
		when(heuresSupService.comptees(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
				org.mockito.ArgumentMatchers.anyInt())).thenThrow(
				PresenceMetierException.badRequest("PERIODE_TROP_LONGUE", "93 jours"));

		mockMvc.perform(get(ADMIN + "/heures-sup/comptees").with(roles("RH")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PERIODE_TROP_LONGUE"));
	}

	// --- Heures sup côté RH Connect ---

	@Test
	void mobileDemandeur_roEtChefDeDepartement_200_collaborateurSeul_403() throws Exception {
		for (String role : List.of("RO", "RESPONSABLE", "RH", "ADMIN")) {
			mockMvc.perform(get(MOBILE + "/heures-sup/beneficiaires-possibles").with(roles("USER", role))).andExpect(status().isOk());
			mockMvc.perform(get(MOBILE + "/heures-sup/demandes/moi").with(roles("USER", role))).andExpect(status().isOk());
			mockMvc.perform(get(MOBILE + "/heures-sup/demandes/moi/comptees").with(roles("USER", role))).andExpect(status().isOk());
			mockMvc.perform(post(MOBILE + "/heures-sup/demandes/" + id + "/annuler").with(roles("USER", role))).andExpect(status().isOk());
		}
		mockMvc.perform(get(MOBILE + "/heures-sup/beneficiaires-possibles").with(roles("USER"))).andExpect(status().isForbidden());
		mockMvc.perform(get(MOBILE + "/heures-sup/demandes/moi").with(roles("USER"))).andExpect(status().isForbidden());
		mockMvc.perform(get(MOBILE + "/heures-sup/demandes/moi/comptees").with(roles("USER"))).andExpect(status().isForbidden());
		mockMvc.perform(post(MOBILE + "/heures-sup/demandes/" + id + "/annuler").with(roles("USER"))).andExpect(status().isForbidden());
	}

	@Test
	void mobileCreation_roEtChef_201_collaborateurEtDirection_403() throws Exception {
		mockMvc.perform(post(MOBILE + "/heures-sup/demandes").contentType("application/json").content(CORPS_DEMANDE)
				.with(roles("USER", "RO"))).andExpect(status().isCreated());
		mockMvc.perform(post(MOBILE + "/heures-sup/demandes").contentType("application/json").content(CORPS_DEMANDE)
				.with(roles("RESPONSABLE"))).andExpect(status().isCreated());
		mockMvc.perform(post(MOBILE + "/heures-sup/demandes").contentType("application/json").content(CORPS_DEMANDE)
				.with(roles("USER"))).andExpect(status().isForbidden());
		mockMvc.perform(post(MOBILE + "/heures-sup/demandes").contentType("application/json").content(CORPS_DEMANDE)
				.with(roles("DIRECTION"))).andExpect(status().isForbidden());
		verify(creationService, org.mockito.Mockito.times(2)).creerParDemandeur(any());
	}

	@Test
	void mobileCreation_horsPerimetre403_rienNEstCree() throws Exception {
		when(creationService.creerParDemandeur(any())).thenThrow(
				PresenceMetierException.forbidden("HORS_PERIMETRE", "périmètre"));

		mockMvc.perform(post(MOBILE + "/heures-sup/demandes").contentType("application/json").content(CORPS_DEMANDE)
						.with(roles("USER", "RO")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("HORS_PERIMETRE"));
	}

	@Test
	void mobileAnnulation_dUnAutreDemandeur_403_antiIdor() throws Exception {
		when(heuresSupService.annulerParDemandeur(id)).thenThrow(new SecurityException("Cette demande ne vous appartient pas"));

		mockMvc.perform(post(MOBILE + "/heures-sup/demandes/" + id + "/annuler").with(roles("USER", "RO")))
				.andExpect(status().isForbidden());
	}

	@Test
	void mobileVueDuRo_demandeDUnAutre_403() throws Exception {
		when(heuresSupService.mesDemandesComptees(any(), any(), any())).thenThrow(new SecurityException("autre demandeur"));

		mockMvc.perform(get(MOBILE + "/heures-sup/demandes/moi/comptees?demande_id=" + id).with(roles("USER", "RO")))
				.andExpect(status().isForbidden());
	}

	@Test
	void mobileMesHeures_collaborateurOk_etAucunParametreDIdentiteNEstLu() throws Exception {
		mockMvc.perform(get(MOBILE + "/heures-sup/moi").header("X-Collaborateur-Id", UUID.randomUUID().toString())
						.with(roles("USER")))
				.andExpect(status().isOk());

		verifyNoInteractions(presenceAccess);
		verify(heuresSupService).mesHeures(null, null);
	}

	@Test
	void mobileCreation_enteteFalsifie_n_estJamaisLu() throws Exception {
		mockMvc.perform(post(MOBILE + "/heures-sup/demandes").contentType("application/json").content(CORPS_DEMANDE)
				.header("X-Collaborateur-Id", UUID.randomUUID().toString()).with(roles("USER", "RO"))).andExpect(status().isCreated());

		verifyNoInteractions(presenceAccess);
	}

	@Test
	void mobileFenetreDuJour_collaborateurOk_corpsSansQr422_sansRole403() throws Exception {
		mockMvc.perform(post(MOBILE + "/fenetre-du-jour").contentType("application/json").content("{\"qr_token\":\"p1.x.y\"}")
				.with(roles("USER"))).andExpect(status().isOk());
		mockMvc.perform(post(MOBILE + "/fenetre-du-jour").contentType("application/json").content("{}")
				.with(roles("USER"))).andExpect(status().isUnprocessableEntity());
		mockMvc.perform(post(MOBILE + "/fenetre-du-jour").contentType("application/json").content("{\"qr_token\":\"p1.x.y\"}")
				.with(roles("DIRECTION"))).andExpect(status().isForbidden());
	}

	@Test
	void mobileFenetreDuJour_qrInvalide422_neFuitePasLeQrDansLUrl() throws Exception {
		when(fenetreMobileService.fenetreDuJour(any())).thenThrow(PresenceMetierException.unprocessable("QR_INVALIDE", "x"));

		mockMvc.perform(post(MOBILE + "/fenetre-du-jour").contentType("application/json").content("{\"qr_token\":\"p1.x.y\"}")
						.with(roles("USER")))
				.andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("QR_INVALIDE"));
		mockMvc.perform(get(MOBILE + "/fenetre-du-jour?qr_token=p1.x.y").with(roles("USER")))
				.andExpect(status().is4xxClientError());
	}

	@Test
	void mobileSites_collaborateurOk_directionNon() throws Exception {
		mockMvc.perform(get(MOBILE + "/sites").with(roles("USER"))).andExpect(status().isOk());
		mockMvc.perform(get(MOBILE + "/sites").with(roles("DIRECTION"))).andExpect(status().isForbidden());
	}

	@Test
	void referentielIndisponible_503() throws Exception {
		when(heuresSupService.beneficiairesPossibles()).thenThrow(
				PresenceMetierException.indisponible("REFERENTIEL_INDISPONIBLE", "indisponible"));

		mockMvc.perform(get(MOBILE + "/heures-sup/beneficiaires-possibles").with(roles("USER", "RO")))
				.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("REFERENTIEL_INDISPONIBLE"));
	}

	@Test
	void conflitDeVersion_409_STATUT_INCOMPATIBLE() throws Exception {
		when(heuresSupService.valider(id)).thenThrow(
				new org.springframework.orm.ObjectOptimisticLockingFailureException(
						com.hr.presence.entity.PresenceHeuresSupDemande.class, id));

		mockMvc.perform(post(ADMIN + "/heures-sup/demandes/" + id + "/valider").with(roles("RH")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STATUT_INCOMPATIBLE"));
	}
}
