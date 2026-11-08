package com.hr.referentiel.web;

import com.hr.referentiel.config.SecurityConfig;
import com.hr.referentiel.dto.FicheAnnuaireResponse;
import com.hr.referentiel.dto.HierarchieResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.service.CollaborateurConnecteService;
import com.hr.referentiel.service.DemandeAdministrativeRhService;
import com.hr.referentiel.service.DemandeDocumentAdministratifRhService;
import com.hr.referentiel.service.DemandeFormationRhService;
import com.hr.referentiel.service.HierarchieService;
import com.hr.referentiel.service.PlainteRhService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot 2 UAT, ticket 2A : droits (RH, ADMIN, DIRECTION, collaborateur), 404 / 400, plaintes réservées aux RH et ADMIN
 * (D-N01.a), filtres par collaborateur, fiche annuaire limitée à ses champs (D-N02).
 */
@WebMvcTest(controllers = {HierarchieController.class, PlainteRhController.class,
		DemandeAdministrativeRhController.class, DemandeDocumentAdministratifRhController.class,
		DemandeFormationRhController.class})
@Import(SecurityConfig.class)
class FichesDetailControllerTest {

	private static final String BASE = "/api/referentiel/v1";

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private HierarchieService hierarchieService;
	@MockitoBean
	private CollaborateurConnecteService connecte;
	@MockitoBean
	private PlainteRhService plainteService;
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

	private static RequestPostProcessor role(String... roles) {
		return jwt().authorities(java.util.Arrays.stream(roles)
				.map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList());
	}

	// --- Hiérarchie et fiche d'unité : lecture back-office ---

	@Test
	void hierarchieEtFicheUnite_rhAdminDirectionLisent_collaborateurNon() throws Exception {
		UUID id = UUID.randomUUID();
		when(hierarchieService.hierarchieDe(id)).thenReturn(
				new HierarchieResponse(null, List.of(), null, null, true, List.of(), List.of()));
		for (String r : new String[] {"RH", "ADMIN", "DIRECTION"}) {
			mockMvc.perform(get(BASE + "/collaborateurs/" + id + "/hierarchie").with(role(r)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.valideur_est_rh").value(true));
			mockMvc.perform(get(BASE + "/unites/" + id + "/fiche").with(role(r))).andExpect(status().isOk());
		}
		mockMvc.perform(get(BASE + "/collaborateurs/" + id + "/hierarchie").with(role("USER")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(BASE + "/unites/" + id + "/fiche").with(role("USER", "RO")))
				.andExpect(status().isForbidden());
	}

	@Test
	void hierarchieInconnue404_identifiantMalForme400_anonyme401() throws Exception {
		UUID id = UUID.randomUUID();
		when(hierarchieService.hierarchieDe(id))
				.thenThrow(ReferentielMetierException.notFound("COLLABORATEUR_INTROUVABLE", "Collaborateur introuvable."));
		when(hierarchieService.ficheUnite(id))
				.thenThrow(ReferentielMetierException.notFound("UNITE_INTROUVABLE", "Unité introuvable."));

		mockMvc.perform(get(BASE + "/collaborateurs/" + id + "/hierarchie").with(role("RH")))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COLLABORATEUR_INTROUVABLE"));
		mockMvc.perform(get(BASE + "/unites/" + id + "/fiche").with(role("RH")))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("UNITE_INTROUVABLE"));
		mockMvc.perform(get(BASE + "/collaborateurs/pas-un-uuid/hierarchie").with(role("RH")))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get(BASE + "/collaborateurs/" + id + "/hierarchie")).andExpect(status().isUnauthorized());
	}

	// --- Mobile : identité depuis le JWT, en-tête cohérent ---

	@Test
	void monDossier_enTeteDUnAutre_403IdentiteIncoherente_serviceNonAppele() throws Exception {
		Collaborateur moi = new Collaborateur();
		moi.setId(UUID.randomUUID());
		when(connecte.exigerCollaborateur(any())).thenReturn(moi);

		mockMvc.perform(get(BASE + "/collaborateurs/moi/hierarchie")
						.header("X-Collaborateur-Id", UUID.randomUUID().toString()).with(role("USER")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("IDENTITE_INCOHERENTE"));
		mockMvc.perform(get(BASE + "/collaborateurs/moi/equipe")
						.header("X-Collaborateur-Id", "n-importe-quoi").with(role("USER")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(BASE + "/collaborateurs/moi/equipe/" + UUID.randomUUID())
						.header("X-Collaborateur-Id", UUID.randomUUID().toString()).with(role("USER")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("IDENTITE_INCOHERENTE"));
		verifyNoInteractions(hierarchieService);
	}

	@Test
	void monDossier_enTeteCoherentOuAbsent_200() throws Exception {
		Collaborateur moi = new Collaborateur();
		moi.setId(UUID.randomUUID());
		when(connecte.exigerCollaborateur(any())).thenReturn(moi);
		when(hierarchieService.maHierarchie(any())).thenReturn(
				new HierarchieResponse(null, List.of(), null, null, true, List.of(), List.of()));
		when(hierarchieService.monEquipe(any())).thenReturn(List.of());

		mockMvc.perform(get(BASE + "/collaborateurs/moi/hierarchie")
						.header("X-Collaborateur-Id", moi.getId().toString()).with(role("USER")))
				.andExpect(status().isOk());
		mockMvc.perform(get(BASE + "/collaborateurs/moi/hierarchie").with(role("USER"))).andExpect(status().isOk());
		mockMvc.perform(get(BASE + "/collaborateurs/moi/equipe").with(role("USER")))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void ficheEquipeHorsPerimetre_403HorsPerimetre() throws Exception {
		UUID id = UUID.randomUUID();
		when(hierarchieService.ficheEquipe(eq(id), any())).thenThrow(
				new ReferentielMetierException("HORS_PERIMETRE", "Hors périmètre.", 403));

		mockMvc.perform(get(BASE + "/collaborateurs/moi/equipe/" + id).with(role("USER")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("HORS_PERIMETRE"));
	}

	@Test
	void annuaire_exposeExactementLesChampsAnnuaire() throws Exception {
		UUID id = UUID.randomUUID();
		when(hierarchieService.annuaire(id)).thenReturn(new FicheAnnuaireResponse(
				id, "Amel Trabelsi", "DRH", "Directrice", UUID.randomUUID(), "DRH",
				new FicheAnnuaireResponse.Responsable(UUID.randomUUID(), "Karim Ben Salah"), "drh01@rh-evenement.test"));

		String json = mockMvc.perform(get(BASE + "/annuaire/" + id).with(role("USER")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		var cles = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
		java.util.Set<String> noms = new java.util.TreeSet<>();
		cles.fieldNames().forEachRemaining(noms::add);
		org.assertj.core.api.Assertions.assertThat(noms).containsExactlyInAnyOrder(
				"collaborateur_id", "nom_complet", "poste_libelle", "intitule_role", "unite_id", "unite_libelle",
				"responsable", "courriel_professionnel");
	}

	@Test
	void annuaire_inactifOuInconnu404_sansRole403() throws Exception {
		UUID id = UUID.randomUUID();
		when(hierarchieService.annuaire(id))
				.thenThrow(ReferentielMetierException.notFound("COLLABORATEUR_INTROUVABLE", "Collaborateur introuvable."));

		mockMvc.perform(get(BASE + "/annuaire/" + id).with(role("USER"))).andExpect(status().isNotFound());
		mockMvc.perform(get(BASE + "/annuaire/" + id).with(role("AUTRE"))).andExpect(status().isForbidden());
	}

	// --- Plaintes : RH et ADMIN seulement ---

	@Test
	void plaintesListe_directionEtCollaborateur403_rhEtAdmin200() throws Exception {
		when(plainteService.listerToutPourRh(any(), any(), any(), any())).thenReturn(List.of());
		UUID col = UUID.randomUUID();

		mockMvc.perform(get("/api/rh/v1/plaintes/liste").with(role("DIRECTION"))).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/rh/v1/plaintes/liste").param("collaborateur_id", col.toString())
				.with(role("DIRECTION"))).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/rh/v1/plaintes/liste").with(role("USER"))).andExpect(status().isForbidden());
		for (String r : new String[] {"RH", "ADMIN"}) {
			mockMvc.perform(get("/api/rh/v1/plaintes/liste").param("collaborateur_id", col.toString()).with(role(r)))
					.andExpect(status().isOk());
		}
		verify(plainteService, org.mockito.Mockito.times(2)).listerToutPourRh(any(), any(), eq(col), any());
	}

	@Test
	void plainteDetailEtSuivi_directionNonRhPasseAvecRhFalse_rhAdminAvecRhTrue() throws Exception {
		UUID id = UUID.randomUUID();
		when(plainteService.obtenir(any(), any(), anyBoolean()))
				.thenThrow(new org.springframework.security.access.AccessDeniedException("Accès refusé."));
		when(plainteService.suivi(any(), any(), anyBoolean()))
				.thenThrow(new org.springframework.security.access.AccessDeniedException("Accès refusé."));

		mockMvc.perform(get("/api/rh/v1/plaintes/" + id).with(role("DIRECTION"))).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/rh/v1/plaintes/" + id + "/suivi").with(role("DIRECTION")))
				.andExpect(status().isForbidden());
		// DIRECTION n'est jamais traitée comme RH pour les plaintes
		verify(plainteService).obtenir(eq(id), any(), eq(false));
		verify(plainteService).suivi(eq(id), any(), eq(false));
	}

	@Test
	void plainteDetail_rhEtAdminTransmettentRhVrai_inconnue404() throws Exception {
		UUID id = UUID.randomUUID();
		when(plainteService.obtenir(any(), any(), eq(true)))
				.thenThrow(ReferentielMetierException.notFound("PLAINTE_INTROUVABLE", "Plainte introuvable."));

		mockMvc.perform(get("/api/rh/v1/plaintes/" + id).with(role("RH")))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PLAINTE_INTROUVABLE"));
		mockMvc.perform(get("/api/rh/v1/plaintes/" + id).with(role("ADMIN"))).andExpect(status().isNotFound());
		verify(plainteService, org.mockito.Mockito.times(2)).obtenir(eq(id), any(), eq(true));
	}

	// --- Listes filtrées par collaborateur ---

	@Test
	void listesFiltrees_directionLit_collaborateurNon_filtresTransmis() throws Exception {
		UUID col = UUID.randomUUID();
		UUID unite = UUID.randomUUID();
		when(demandeService.listerToutPourRh(any(), any(), any(), any(), eq(col), eq(unite))).thenReturn(List.of());
		when(documentService.listerPourRh(eq(col), any(), any())).thenReturn(List.of());
		when(formationService.listerPourRh(any(), any(), eq(col))).thenReturn(List.of());

		for (String r : new String[] {"RH", "ADMIN", "DIRECTION"}) {
			mockMvc.perform(get("/api/rh/v1/demandes-administratives/liste")
					.param("collaborateur_id", col.toString()).param("unite_id", unite.toString()).with(role(r)))
					.andExpect(status().isOk());
			mockMvc.perform(get("/api/rh/v1/demandes-documents-administratifs/liste")
					.param("collaborateur_id", col.toString()).with(role(r))).andExpect(status().isOk());
			mockMvc.perform(get("/api/rh/v1/demandes-formations/liste")
					.param("collaborateur_id", col.toString()).with(role(r))).andExpect(status().isOk());
		}
		for (String url : new String[] {"/api/rh/v1/demandes-administratives/liste",
				"/api/rh/v1/demandes-documents-administratifs/liste", "/api/rh/v1/demandes-formations/liste"}) {
			mockMvc.perform(get(url).param("collaborateur_id", col.toString()).with(role("USER")))
					.andExpect(status().isForbidden());
			mockMvc.perform(get(url).param("collaborateur_id", "mal-forme").with(role("RH")))
					.andExpect(status().isBadRequest());
		}
	}
}
