package com.hr.referentiel.web;

import com.hr.referentiel.champsperso.ChampPersoDefinitionService;
import com.hr.referentiel.champsperso.ChampPersoListeService;
import com.hr.referentiel.champsperso.ValeursPersoDtos.ChampMoi;
import com.hr.referentiel.champsperso.ValeursPersoDtos.MesChamps;
import com.hr.referentiel.champsperso.ValeursPersoDtos.PageJournalValeurs;
import com.hr.referentiel.champsperso.ValeursPersoDtos.ValeursFiche;
import com.hr.referentiel.champsperso.ValeursPersoFicheService;
import com.hr.referentiel.config.SecurityConfig;
import com.hr.referentiel.dto.CollaborateurResponse;
import com.hr.referentiel.dto.PageReferentielResponse;
import com.hr.referentiel.importation.parametrage.ImportParametrageDtos.Parametrage;
import com.hr.referentiel.service.AuteurParametreResolver;
import com.hr.referentiel.service.CollaborateurConnecteService;
import com.hr.referentiel.service.CreationCollaborateurService;
import com.hr.referentiel.service.ExportCollaborateursService;
import com.hr.referentiel.service.RechercheCollaborateursService;
import com.hr.referentiel.service.ReferentielRhService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
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
 * Matrice des rôles de l'annexe A bis.3 (C1 à C6, V1 à V6, création) : sans JWT 401, COLLABORATEUR et RESPONSABLE 403,
 * RH et ADMIN autorisés, DIRECTION en lecture seule ; V4 ouvert à tout appelant authentifié, sans paramètre lu.
 */
@WebMvcTest(controllers = {ChampPersoController.class, ValeursPersoController.class, ReferentielController.class})
@Import(SecurityConfig.class)
class ChampsPersoControllersSecurityTest {

	private static final String P = "/api/referentiel/v1/parametres/imports/collaborateurs/champs-personnalises";
	private static final String F = "/api/referentiel/v1/collaborateurs";

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private ChampPersoDefinitionService definitions;
	@MockitoBean
	private ChampPersoListeService listes;
	@MockitoBean
	private ValeursPersoFicheService valeurs;
	@MockitoBean
	private ReferentielRhService referentielRhService;
	@MockitoBean
	private CollaborateurConnecteService collaborateurConnecteService;
	@MockitoBean
	private CreationCollaborateurService creation;
	@MockitoBean
	private RechercheCollaborateursService recherche;
	@MockitoBean
	private ExportCollaborateursService export;
	@MockitoBean
	private AuteurParametreResolver auteurResolver;
	@MockitoBean
	private JwtDecoder jwtDecoder;
	@MockitoBean
	private org.springframework.cache.CacheManager cacheManager;

	private static RequestPostProcessor roles(String... roles) {
		return jwt().jwt(j -> j.claim("identifiant_utilisateur", UUID.randomUUID().toString())
						.claim("societe_id", com.hr.referentiel.test.AvecSocieteContexteExtension.SOCIETE_TEST.toString()))
				.authorities(java.util.Arrays.stream(roles).map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
						.toArray(GrantedAuthority[]::new));
	}

	private static final RequestPostProcessor COL = roles("USER");
	private static final RequestPostProcessor CS = roles("USER", "RO");
	private static final RequestPostProcessor RESP = roles("USER", "RESPONSABLE");
	private static final RequestPostProcessor RH = roles("USER", "RH");
	private static final RequestPostProcessor ADMIN = roles("USER", "ADMIN");
	private static final RequestPostProcessor DG = roles("USER", "DIRECTION");

	private final UUID id = UUID.randomUUID();

	@BeforeEach
	void stubs() {
		Parametrage vide = new Parametrage("collaborateurs", "Collaborateurs", 6, null, null, List.of());
		when(definitions.creer(any(), any(), any())).thenReturn(vide);
		when(definitions.modifier(any(), any(), any(), any())).thenReturn(vide);
		when(definitions.desactiver(any(), any(), any(), any())).thenReturn(vide);
		when(definitions.reactiver(any(), any(), any(), any())).thenReturn(vide);
		when(listes.ajouter(any(), any(), any(), any())).thenReturn(vide);
		when(listes.modifier(any(), any(), any(), any(), any())).thenReturn(vide);
		when(listes.basculer(any(), any(), any(), any(), anyBoolean(), any())).thenReturn(vide);
		when(valeurs.lire(any(), any(), anyBoolean())).thenReturn(new ValeursFiche(id, 1, true, null, List.of(), List.of()));
		when(valeurs.ecrire(any(), any(), any(), anyBoolean())).thenReturn(new ValeursFiche(id, 2, true, null, List.of(), List.of()));
		when(valeurs.journal(any(), anyInt(), anyInt())).thenReturn(new PageJournalValeurs(List.of(), 0, 20, 0));
		when(valeurs.moi(any())).thenReturn(new MesChamps(List.of(new ChampMoi("PERSO_TAILLE", "Taille de tenue", "LISTE", "M"))));
		when(recherche.lister(any(), anyInt(), anyInt())).thenReturn(new PageReferentielResponse<CollaborateurResponse>(List.of(), 0, 0, 0, 20));
		when(export.exporter(any(), any())).thenReturn("﻿Matricule\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		when(export.nomFichier()).thenReturn("collaborateurs_20261113.csv");
		when(creation.creer(any(), any())).thenReturn(new CollaborateurResponse());
	}

	private void definitionsAvec(RequestPostProcessor qui, int creer, int ok, int supprime) throws Exception {
		String json = "{\"version\":6}";
		mockMvc.perform(post(P).with(qui).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().is(creer));
		mockMvc.perform(put(P + "/PERSO_X").with(qui).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().is(ok));
		mockMvc.perform(post(P + "/PERSO_X/desactiver").with(qui).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().is(ok));
		mockMvc.perform(post(P + "/PERSO_X/reactiver").with(qui).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().is(ok));
		mockMvc.perform(delete(P + "/PERSO_X?version=6").with(qui)).andExpect(status().is(supprime));
		mockMvc.perform(post(P + "/PERSO_X/valeurs").with(qui).contentType(MediaType.APPLICATION_JSON).content("{\"libelle\":\"XXL\",\"version\":6}"))
				.andExpect(status().is(creer));
		mockMvc.perform(put(P + "/PERSO_X/valeurs/S").with(qui).contentType(MediaType.APPLICATION_JSON).content("{\"libelle\":\"S\",\"version\":6}"))
				.andExpect(status().is(ok));
		mockMvc.perform(post(P + "/PERSO_X/valeurs/S/desactiver").with(qui).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().is(ok));
		mockMvc.perform(post(P + "/PERSO_X/valeurs/S/reactiver").with(qui).contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().is(ok));
		mockMvc.perform(delete(P + "/PERSO_X/valeurs/S?version=6").with(qui)).andExpect(status().is(supprime));
	}

	@Test
	@DisplayName("C1 à C6 : RH et ADMIN autorisés (201 / 200 / 204) ; DIRECTION, COLLABORATEUR et RESPONSABLE 403 ; sans JWT 401")
	void matriceDesDefinitions() throws Exception {
		definitionsAvec(RH, 201, 200, 204);
		definitionsAvec(ADMIN, 201, 200, 204);
		definitionsAvec(DG, 403, 403, 403);
		definitionsAvec(COL, 403, 403, 403);
		definitionsAvec(CS, 403, 403, 403);
		definitionsAvec(RESP, 403, 403, 403);
		mockMvc.perform(post(P).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(P + "/PERSO_X?version=1")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("V1, V3, V5, V6 : RH, ADMIN et DIRECTION 200 ; COLLABORATEUR et RESPONSABLE 403 (D-I36) ; sans JWT 401")
	void matriceDesLectures() throws Exception {
		for (RequestPostProcessor ok : List.of(RH, ADMIN, DG)) {
			mockMvc.perform(get(F + "/" + id + "/champs-personnalises").with(ok)).andExpect(status().isOk());
			mockMvc.perform(get(F + "/" + id + "/champs-personnalises/journal").with(ok)).andExpect(status().isOk());
			mockMvc.perform(get(F).with(ok)).andExpect(status().isOk());
			mockMvc.perform(get(F + "/export").with(ok)).andExpect(status().isOk());
		}
		for (RequestPostProcessor ko : List.of(COL, CS, RESP)) {
			mockMvc.perform(get(F + "/" + id + "/champs-personnalises").with(ko)).andExpect(status().isForbidden());
			mockMvc.perform(get(F + "/" + id + "/champs-personnalises/journal").with(ko)).andExpect(status().isForbidden());
			mockMvc.perform(get(F).with(ko)).andExpect(status().isForbidden());
			mockMvc.perform(get(F + "/export").with(ko)).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(F + "/" + id + "/champs-personnalises")).andExpect(status().isUnauthorized());
		mockMvc.perform(get(F + "/export")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("V2 et création avec valeurs : RH et ADMIN 200 / 201 ; DIRECTION, COLLABORATEUR, RESPONSABLE 403 ; sans JWT 401")
	void matriceDesEcritures() throws Exception {
		String corps = "{\"version\":1,\"valeurs\":{\"PERSO_TAILLE\":\"M\"}}";
		String creer = "{\"matricule\":\"N-1\",\"prenom\":\"A\",\"nom\":\"B\",\"courriel_professionnel\":\"a@x.test\",\"statut\":\"ACTIF\","
				+ "\"unite_identifiant\":\"" + UUID.randomUUID() + "\",\"champs_personnalises\":{\"PERSO_TAILLE\":\"M\"}}";
		for (RequestPostProcessor ok : List.of(RH, ADMIN)) {
			mockMvc.perform(put(F + "/" + id + "/champs-personnalises").with(ok).contentType(MediaType.APPLICATION_JSON).content(corps))
					.andExpect(status().isOk());
			mockMvc.perform(post(F).with(ok).contentType(MediaType.APPLICATION_JSON).content(creer)).andExpect(status().isCreated());
		}
		for (RequestPostProcessor ko : List.of(DG, COL, CS, RESP)) {
			mockMvc.perform(put(F + "/" + id + "/champs-personnalises").with(ko).contentType(MediaType.APPLICATION_JSON).content(corps))
					.andExpect(status().isForbidden());
			mockMvc.perform(post(F).with(ko).contentType(MediaType.APPLICATION_JSON).content(creer)).andExpect(status().isForbidden());
		}
		mockMvc.perform(put(F + "/" + id + "/champs-personnalises").contentType(MediaType.APPLICATION_JSON).content(corps))
				.andExpect(status().isUnauthorized());
		verify(valeurs, org.mockito.Mockito.times(2)).ecrire(any(), any(), any(), anyBoolean());
	}

	@Test
	@DisplayName("V4 : tout appelant authentifié reçoit les siennes ; sans JWT 401 ; un collaborateurId éventuel est ignoré ; seuls code, libellé, type et valeur affichée sont exposés")
	void mesChampsSansParametre() throws Exception {
		for (RequestPostProcessor qui : List.of(COL, CS, RESP, RH, ADMIN, DG)) {
			mockMvc.perform(get(F + "/moi/champs-personnalises").with(qui)).andExpect(status().isOk());
		}
		mockMvc.perform(get(F + "/moi/champs-personnalises")).andExpect(status().isUnauthorized());
		mockMvc.perform(get(F + "/moi/champs-personnalises").param("collaborateurId", UUID.randomUUID().toString())
				.param("collaborateur_id", UUID.randomUUID().toString()).with(COL)).andExpect(status().isOk())
				.andExpect(jsonPath("$.champs[0].libelle").value("Taille de tenue"))
				.andExpect(jsonPath("$.champs[0].valeur_affichee").value("M"))
				.andExpect(jsonPath("$.champs[0].regles").doesNotExist())
				.andExpect(jsonPath("$.champs[0].finalite").doesNotExist())
				.andExpect(jsonPath("$.champs_desactives").doesNotExist());
		verify(valeurs, org.mockito.Mockito.atLeastOnce()).moi(any()); // la seule entrée du service est le JWT
	}

	@Test
	@DisplayName("V6 : en-têtes de téléchargement (CSV UTF-8, nom collaborateurs_AAAAMMJJ.csv) ; V5 : filtre mal formé -> 400 FILTRE_INVALIDE")
	void exportEtFiltreInvalide() throws Exception {
		mockMvc.perform(get(F + "/export").param("q", "sarra").with(RH)).andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"collaborateurs_20261113.csv\""))
				.andExpect(content().contentTypeCompatibleWith("text/csv"));
		mockMvc.perform(get(F).param("direction", "pas-un-uuid").with(RH)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("FILTRE_INVALIDE"));
		mockMvc.perform(get(F).param("metier", "AUTRE").with(DG)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("FILTRE_INVALIDE"));
	}
}
