package com.hr.presence.web;

import com.hr.presence.config.SecurityConfig;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.dto.DemandeManuelleDetail;
import com.hr.presence.dto.DemandeManuelleResponse;
import com.hr.presence.dto.PresenceCollaborateurDtos.PersonneResume;
import com.hr.presence.dto.PresenceCollaborateurDtos.PresenceCollaborateur;
import com.hr.presence.dto.PresenceCollaborateurDtos.PresenceEquipe;
import com.hr.presence.dto.PresenceCollaborateurDtos.SynthesePresence;
import com.hr.presence.service.HeuresSupCreationService;
import com.hr.presence.service.HeuresSupCsvService;
import com.hr.presence.service.HeuresSupService;
import com.hr.presence.service.PresenceCollaborateurService;
import com.hr.presence.service.PresenceDemandeManuelleService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot 2 UAT, ticket 2C : RBAC (200 / 403 / 404 / 400) des endpoints de présence par collaborateur, de détail des
 * pointages manuels et des heures sup par numéro, et de la fiche d'équipe mobile. DIRECTION : lecture seule.
 */
@WebMvcTest(controllers = {PresenceCollaborateurAdminController.class, PresenceEquipeMobileController.class,
		PresenceDemandeManuelleController.class, HeuresSupAdminController.class})
@Import(SecurityConfig.class)
class PresenceLot2ControllersSecurityTest {

	private static final String ADMIN = "/api/rh/v1/admin/presence";
	private static final String MOBILE = "/api/rh/v1/mobile/presence";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PresenceCollaborateurService presenceService;
	@MockitoBean
	private PresenceDemandeManuelleService demandeService;
	@MockitoBean
	private HeuresSupService heuresSupService;
	@MockitoBean
	private HeuresSupCreationService creationService;
	@MockitoBean
	private HeuresSupCsvService csvService;
	@MockitoBean(name = "presenceAccess")
	private com.hr.presence.security.PresenceAccessService presenceAccess;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	private final UUID id = UUID.randomUUID();

	private static RequestPostProcessor roles(String... roles) {
		return jwt().authorities(Arrays.stream(roles)
				.map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toArray(GrantedAuthority[]::new));
	}

	private static SynthesePresence synthese() {
		return new SynthesePresence("f", "Sarra Khemiri", "COL-WEB", LocalDate.of(2026, 10, 6), LocalDate.of(2026, 11, 5),
				18, 9012, 3, 41, 1, 0, 1, 0, 120);
	}

	// ---- Présence d'un collaborateur (C2) ---------------------------------------------------------------------

	@Test
	void synthese_rhDirectionAdmin_200_collaborateurEtRoSeul_403() throws Exception {
		when(presenceService.syntheseAdmin(eq(id), any(), any())).thenReturn(synthese());

		for (String role : new String[] {"RH", "DIRECTION", "ADMIN"}) {
			mockMvc.perform(get(ADMIN + "/collaborateurs/" + id + "/synthese").with(roles("USER", role)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.retards_nombre").value(3))
					.andExpect(jsonPath("$.retards_cumul_minutes").value(41))
					.andExpect(jsonPath("$.collaborateur_nom").value("Sarra Khemiri"));
		}
		for (String role : new String[] {"USER", "RO", "RESPONSABLE"}) {
			mockMvc.perform(get(ADMIN + "/collaborateurs/" + id + "/synthese").with(roles(role)))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void synthese_identifiantMalForme_400_etPeriodeMalFormee_400() throws Exception {
		mockMvc.perform(get(ADMIN + "/collaborateurs/pas-un-uuid/synthese").with(roles("RH")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get(ADMIN + "/collaborateurs/" + id + "/synthese?du=hier").with(roles("RH")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void synthese_periodeTropLongue_400_collaborateurInconnu_404() throws Exception {
		when(presenceService.syntheseAdmin(eq(id), any(), any()))
				.thenThrow(PresenceMetierException.badRequest("PERIODE_TROP_LONGUE", "trop longue"));
		UUID inconnu = UUID.randomUUID();
		when(presenceService.syntheseAdmin(eq(inconnu), any(), any()))
				.thenThrow(PresenceMetierException.notFound("COLLABORATEUR_INTROUVABLE", "Collaborateur introuvable"));

		mockMvc.perform(get(ADMIN + "/collaborateurs/" + id + "/synthese").with(roles("RH")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PERIODE_TROP_LONGUE"));
		mockMvc.perform(get(ADMIN + "/collaborateurs/" + inconnu + "/synthese").with(roles("DIRECTION")))
				.andExpect(status().isNotFound());
	}

	@Test
	void presenceDuCollaborateur_directionLit_collaborateurRefuse() throws Exception {
		when(presenceService.presenceAdmin(eq(id), any(), any())).thenReturn(new PresenceCollaborateur(
				new PersonneResume(id.toString(), "Sarra Khemiri", "COL-WEB"), synthese(), List.of(), List.of()));

		mockMvc.perform(get(ADMIN + "/collaborateurs/" + id + "/presence").with(roles("DIRECTION")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.collaborateur.nom_complet").value("Sarra Khemiri"));
		mockMvc.perform(get(ADMIN + "/collaborateurs/" + id + "/presence").with(roles("USER")))
				.andExpect(status().isForbidden());
		verifyNoInteractions(creationService);
	}

	@Test
	void syntheseSite_directionLit_collaborateurRefuse_siteInconnu404() throws Exception {
		when(presenceService.syntheseSite(eq(id), any(), any())).thenThrow(
				PresenceMetierException.notFound("SITE_INTROUVABLE", "Site introuvable"));

		mockMvc.perform(get(ADMIN + "/sites/" + id + "/synthese").with(roles("DIRECTION"))).andExpect(status().isNotFound());
		mockMvc.perform(get(ADMIN + "/sites/" + id + "/synthese").with(roles("USER"))).andExpect(status().isForbidden());
	}

	// ---- Pointages manuels (C3, C4, C5) -----------------------------------------------------------------------

	@Test
	void listeManuels_filtreCollaborateurSiteEtPeriode_transmisAuService_directionLit() throws Exception {
		UUID site = UUID.randomUUID();
		when(demandeService.listerAdmin(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

		mockMvc.perform(get(ADMIN + "/demandes-manuelles?collaborateurId=" + id + "&siteId=" + site
						+ "&du=2026-11-01&au=2026-11-05&statut=EN_ATTENTE&avecSignal=true").with(roles("DIRECTION")))
				.andExpect(status().isOk());

		verify(demandeService).listerAdmin(StatutDemandeManuelle.EN_ATTENTE, id, site, LocalDate.of(2026, 11, 1),
				LocalDate.of(2026, 11, 5), true);
	}

	@Test
	void listeManuels_collaborateurMalForme_400_collaborateurRefuse_403() throws Exception {
		mockMvc.perform(get(ADMIN + "/demandes-manuelles?collaborateurId=abc").with(roles("RH")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get(ADMIN + "/demandes-manuelles?collaborateurId=" + id).with(roles("USER")))
				.andExpect(status().isForbidden());
	}

	@Test
	void detailManuel_aPlat_avecLeNumero_directionLit_inconnu404_collaborateur403() throws Exception {
		DemandeManuelleResponse base = new DemandeManuelleResponse(id, "PM-202611-0001", StatutDemandeManuelle.EN_ATTENTE,
				PointageType.ENTREE, Instant.parse("2026-11-04T08:00:00Z"), "GPS", "f", "Sarra Khemiri", "COL-WEB", null,
				null, null, "Omar Belhadj", false, null, null, null, null, null, 2L, false);
		DemandeManuelleDetail detail = new DemandeManuelleDetail(base, "Siège", "Web",
				new PersonneResume("v", "Omar Belhadj", null), null);
		when(demandeService.detailAdmin(id)).thenReturn(detail);
		UUID inconnu = UUID.randomUUID();
		when(demandeService.detailAdmin(inconnu)).thenThrow(PresenceMetierException.notFound("DEMANDE_INTROUVABLE", "x"));
		when(demandeService.detailAdminParNumero("PM-202611-0001")).thenReturn(detail);

		mockMvc.perform(get(ADMIN + "/demandes-manuelles/" + id).with(roles("DIRECTION")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.numero").value("PM-202611-0001"))
				.andExpect(jsonPath("$.collaborateur_nom").value("Sarra Khemiri"))
				.andExpect(jsonPath("$.site_libelle").value("Siège"))
				.andExpect(jsonPath("$.valideur.nom_complet").value("Omar Belhadj"))
				.andExpect(jsonPath("$.manuels_du_mois").value(2));
		mockMvc.perform(get(ADMIN + "/demandes-manuelles/numero/PM-202611-0001").with(roles("RH")))
				.andExpect(status().isOk());
		mockMvc.perform(get(ADMIN + "/demandes-manuelles/" + inconnu).with(roles("RH"))).andExpect(status().isNotFound());
		mockMvc.perform(get(ADMIN + "/demandes-manuelles/" + id).with(roles("USER"))).andExpect(status().isForbidden());
		mockMvc.perform(get(ADMIN + "/demandes-manuelles/numero/PM-202611-0001").with(roles("USER")))
				.andExpect(status().isForbidden());
	}

	@Test
	void detailManuelMobile_horsPerimetre403_okPourUser() throws Exception {
		UUID autre = UUID.randomUUID();
		when(demandeService.detailMobile(autre)).thenThrow(PresenceMetierException.forbidden("HORS_PERIMETRE", "x"));
		when(demandeService.detailMobile(id)).thenReturn(new DemandeManuelleDetail(
				new DemandeManuelleResponse(id, "PM-202611-0001", StatutDemandeManuelle.EN_ATTENTE, PointageType.ENTREE,
						null, "GPS", "f", "S", null, null, null, null, null, true, null, null, null, null, null, null, null),
				null, null, null, null));

		mockMvc.perform(get(MOBILE + "/demandes-manuelles/" + id).with(roles("USER"))).andExpect(status().isOk());
		mockMvc.perform(get(MOBILE + "/demandes-manuelles/" + autre).with(roles("USER")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("HORS_PERIMETRE"));
	}

	// ---- Heures supplémentaires (C8) --------------------------------------------------------------------------

	@Test
	void heuresSup_detailParNumero_directionLit_collaborateur403_inconnu404() throws Exception {
		when(heuresSupService.obtenirParNumero("HS-202611-0001")).thenReturn(null);
		when(heuresSupService.obtenirParNumero("HS-202611-9999"))
				.thenThrow(PresenceMetierException.notFound("DEMANDE_INTROUVABLE", "x"));

		mockMvc.perform(get(ADMIN + "/heures-sup/demandes/numero/HS-202611-0001").with(roles("DIRECTION")))
				.andExpect(status().isOk());
		mockMvc.perform(get(ADMIN + "/heures-sup/demandes/numero/HS-202611-9999").with(roles("RH")))
				.andExpect(status().isNotFound());
		mockMvc.perform(get(ADMIN + "/heures-sup/demandes/numero/HS-202611-0001").with(roles("USER", "RO")))
				.andExpect(status().isForbidden());
	}

	@Test
	void heuresSup_compteesDeLaDemande_directionLit_collaborateur403() throws Exception {
		when(heuresSupService.compteesDeDemande(id)).thenReturn(List.of());

		mockMvc.perform(get(ADMIN + "/heures-sup/demandes/" + id + "/comptees").with(roles("DIRECTION")))
				.andExpect(status().isOk());
		mockMvc.perform(get(ADMIN + "/heures-sup/demandes/" + id + "/comptees").with(roles("USER")))
				.andExpect(status().isForbidden());
	}

	@Test
	void heuresSup_filtreParCollaborateurEtSite_transmis() throws Exception {
		UUID site = UUID.randomUUID();
		mockMvc.perform(get(ADMIN + "/heures-sup/demandes?collaborateur_id=" + id + "&site_id=" + site)
				.with(roles("DIRECTION"))).andExpect(status().isOk());

		verify(heuresSupService).lister(any(), eq(site), eq(id), any(), any(), any(), eq(0), eq(20));
	}

	// ---- Mobile : mes pointages et fiche d'équipe (C6, C7) ------------------------------------------------------

	@Test
	void mesJournees_utilisateur200_sansRoleUser403_aucunIdentifiantDansLUrl() throws Exception {
		when(presenceService.mesJournees(any(), any())).thenReturn(List.of());

		mockMvc.perform(get(MOBILE + "/pointages/me/journees").with(roles("USER"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get(MOBILE + "/pointages/me/journees").with(roles("RH"))).andExpect(status().isForbidden());
	}

	@Test
	void mesJournees_identiteIncoherente403_periodeTropLongue400() throws Exception {
		when(presenceService.mesJournees(eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 11, 1))))
				.thenThrow(PresenceMetierException.badRequest("PERIODE_TROP_LONGUE", "x"));
		when(presenceService.mesJournees(eq(null), eq(null)))
				.thenThrow(new PresenceMetierException("IDENTITE_INCOHERENTE", "x", 403));

		mockMvc.perform(get(MOBILE + "/pointages/me/journees?du=2026-10-01&au=2026-11-01").with(roles("USER")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get(MOBILE + "/pointages/me/journees").with(roles("USER")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("IDENTITE_INCOHERENTE"));
	}

	@Test
	void equipe_valideur200_horsPerimetre403_referentielCoupe503_malForme400() throws Exception {
		UUID membre = UUID.randomUUID();
		UUID horsPerimetre = UUID.randomUUID();
		UUID coupe = UUID.randomUUID();
		when(presenceService.equipe(eq(membre), any(), any())).thenReturn(new PresenceEquipe(
				new PersonneResume(membre.toString(), "Ahmed Nasri", "COL-WEB2"), synthese(), List.of(), List.of()));
		when(presenceService.equipe(eq(horsPerimetre), any(), any()))
				.thenThrow(PresenceMetierException.forbidden("HORS_PERIMETRE", "x"));
		when(presenceService.equipe(eq(coupe), any(), any()))
				.thenThrow(PresenceMetierException.indisponible("REFERENTIEL_INDISPONIBLE", "x"));

		mockMvc.perform(get(MOBILE + "/equipe/" + membre).with(roles("USER", "RO")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.collaborateur.nom_complet").value("Ahmed Nasri"));
		mockMvc.perform(get(MOBILE + "/equipe/" + horsPerimetre).with(roles("USER")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("HORS_PERIMETRE"));
		mockMvc.perform(get(MOBILE + "/equipe/" + coupe).with(roles("USER")))
				.andExpect(status().isServiceUnavailable());
		mockMvc.perform(get(MOBILE + "/equipe/pas-un-uuid").with(roles("USER"))).andExpect(status().isBadRequest());
		mockMvc.perform(get(MOBILE + "/equipe/" + membre).with(roles("RH"))).andExpect(status().isForbidden());
	}
}
