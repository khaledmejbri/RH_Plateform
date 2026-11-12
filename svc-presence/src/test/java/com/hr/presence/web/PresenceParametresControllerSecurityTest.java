package com.hr.presence.web;

import com.hr.presence.config.SecurityConfig;
import com.hr.presence.dto.ParametresDtos.Libelles;
import com.hr.presence.dto.ParametresDtos.PageJournal;
import com.hr.presence.dto.ParametresDtos.Reglage;
import com.hr.presence.dto.ParametresDtos.ValeurLibelle;
import com.hr.presence.service.PresenceParametresAdminService;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 3D : RBAC et contrat des paramètres de présence. Écriture RH / ADMIN (DG01, CS-WEB, COL-WEB : 403),
 * lecture RH / DIRECTION / ADMIN, libellés pour tout utilisateur authentifié, 405 sur un référentiel de comportement.
 */
@WebMvcTest(controllers = PresenceParametresController.class)
@Import(SecurityConfig.class)
class PresenceParametresControllerSecurityTest {

	private static final String B = "/api/rh/v1/admin/presence/parametres";
	private static final String L = "/api/rh/v1/presence/parametres/libelles";
	private static final String BODY_REGLAGE = "{\"valeur\":5,\"version\":0}";
	private static final String BODY_VALEUR = "{\"libelle\":\"QR non reconnu\",\"version\":0}";

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private PresenceParametresAdminService service;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	private static RequestPostProcessor roles(String... roles) {
		return jwt().authorities(Arrays.stream(roles)
				.map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toArray(GrantedAuthority[]::new));
	}

	@Test
	void corpsAbsentOuIllisible_400_pasDe403() throws Exception {
		// POST valeurs / désactiver / réactiver ne lisent aucun corps dans ce service (405 de comportement)
		String valeurs = B + "/referentiels/STATUT_POINTAGE/valeurs";
		mockMvc.perform(put(valeurs + "/VALIDE").with(roles("RH")).contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put(valeurs + "/VALIDE").with(roles("RH")).contentType(MediaType.APPLICATION_JSON)
				.content("{pas du json")).andExpect(status().isBadRequest());
		mockMvc.perform(put(B + "/referentiels/STATUT_POINTAGE/ordre").with(roles("RH"))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
		mockMvc.perform(put(B + "/reglages/delai_grace_retard_minutes").with(roles("RH"))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
	}

	@Test
	void ecritureReglage_rhEtAdmin200_autres403() throws Exception {
		when(service.modifierReglage(eq("delai_grace_retard_minutes"), anyInt(), anyLong()))
				.thenReturn(new Reglage("delai_grace_retard_minutes", "Délai", 5, 0, 60, "MINUTES", 0, 1));
		for (String role : List.of("RH", "ADMIN")) {
			mockMvc.perform(put(B + "/reglages/delai_grace_retard_minutes").with(roles(role))
					.contentType(MediaType.APPLICATION_JSON).content(BODY_REGLAGE))
					.andExpect(status().isOk()).andExpect(jsonPath("$.valeur").value(5))
					.andExpect(jsonPath("$.cle").value("delai_grace_retard_minutes"));
		}
		for (String role : List.of("DIRECTION", "RO", "RESPONSABLE", "USER")) {
			mockMvc.perform(put(B + "/reglages/delai_grace_retard_minutes").with(roles(role))
					.contentType(MediaType.APPLICATION_JSON).content(BODY_REGLAGE))
					.andExpect(status().isForbidden());
		}
		mockMvc.perform(put(B + "/reglages/delai_grace_retard_minutes").contentType(MediaType.APPLICATION_JSON).content(BODY_REGLAGE))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void reglageSansValeur_422ValidationDuService() throws Exception {
		mockMvc.perform(put(B + "/reglages/delai_grace_retard_minutes").with(roles("RH"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
				.andExpect(status().isUnprocessableEntity());
		verifyNoInteractions(service);
	}

	@Test
	void ecritureValeur_directionEtCollaborateur403() throws Exception {
		for (String role : List.of("DIRECTION", "RO", "USER")) {
			mockMvc.perform(put(B + "/referentiels/STATUT_POINTAGE/valeurs/REJETE_QR_INVALIDE").with(roles(role))
					.contentType(MediaType.APPLICATION_JSON).content(BODY_VALEUR)).andExpect(status().isForbidden());
			mockMvc.perform(put(B + "/referentiels/STATUT_POINTAGE/ordre").with(roles(role))
					.contentType(MediaType.APPLICATION_JSON).content("{\"codes\":[]}")).andExpect(status().isForbidden());
			mockMvc.perform(post(B + "/referentiels/STATUT_POINTAGE/valeurs").with(roles(role))).andExpect(status().isForbidden());
			mockMvc.perform(delete(B + "/referentiels/STATUT_POINTAGE/valeurs/X").with(roles(role))).andExpect(status().isForbidden());
		}
		verifyNoInteractions(service);
	}

	@Test
	void postEtDeleteSurUnStatut_405() throws Exception {
		when(service.creer("STATUT_POINTAGE")).thenThrow(new PresenceMetierException("PARAMETRE_COMPORTEMENT", "interdit", 405));
		org.mockito.Mockito.doThrow(new PresenceMetierException("PARAMETRE_COMPORTEMENT", "interdit", 405)).when(service).supprimer("STATUT_POINTAGE");
		when(service.desactiver("STATUT_POINTAGE")).thenThrow(new PresenceMetierException("PARAMETRE_COMPORTEMENT", "interdit", 405));

		mockMvc.perform(post(B + "/referentiels/STATUT_POINTAGE/valeurs").with(roles("ADMIN")))
				.andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("PARAMETRE_COMPORTEMENT"));
		mockMvc.perform(delete(B + "/referentiels/STATUT_POINTAGE/valeurs/REJETE_QR_INVALIDE?version=0").with(roles("RH")))
				.andExpect(status().isMethodNotAllowed());
		mockMvc.perform(post(B + "/referentiels/STATUT_POINTAGE/valeurs/REJETE_QR_INVALIDE/desactiver").with(roles("RH"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isMethodNotAllowed());
	}

	@Test
	void versionPerimee_409() throws Exception {
		when(service.modifierReglage(any(), anyInt(), anyLong()))
				.thenThrow(PresenceMetierException.conflict("PARAMETRE_MODIFIE_ENTRE_TEMPS", "x"));
		mockMvc.perform(put(B + "/reglages/delai_grace_retard_minutes").with(roles("RH"))
				.contentType(MediaType.APPLICATION_JSON).content(BODY_REGLAGE))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PARAMETRE_MODIFIE_ENTRE_TEMPS"));
	}

	@Test
	void lectureBackoffice_rhDirectionAdmin200_collaborateur403() throws Exception {
		when(service.reglages()).thenReturn(List.of());
		when(service.referentiels()).thenReturn(List.of());
		when(service.valeurs("STATUT_POINTAGE")).thenReturn(List.of());
		when(service.journal(eq("STATUT_POINTAGE"), anyInt(), anyInt())).thenReturn(new PageJournal(List.of(), 0, 20, 0));
		when(service.journalReglages(anyInt(), anyInt())).thenReturn(new PageJournal(List.of(), 0, 20, 0));
		for (String role : List.of("RH", "DIRECTION", "ADMIN")) {
			for (String chemin : List.of("/reglages", "/referentiels", "/referentiels/STATUT_POINTAGE/valeurs",
					"/referentiels/STATUT_POINTAGE/journal", "/reglages/journal")) {
				mockMvc.perform(get(B + chemin).with(roles(role))).andExpect(status().isOk());
			}
		}
		for (String role : List.of("RO", "RESPONSABLE", "USER")) {
			mockMvc.perform(get(B + "/reglages").with(roles(role))).andExpect(status().isForbidden());
			mockMvc.perform(get(B + "/referentiels").with(roles(role))).andExpect(status().isForbidden());
		}
	}

	@Test
	void libelles_toutUtilisateurAuthentifie_contratSnakeCase() throws Exception {
		when(service.libelles(anyBoolean())).thenReturn(new Libelles("W/\"x\"", Map.of("STATUT_POINTAGE",
				List.of(new ValeurLibelle("REJETE_QR_INVALIDE", "QR non reconnu", null, "ROUGE", 3, true)))));
		for (String role : List.of("USER", "RO", "RH", "DIRECTION")) {
			mockMvc.perform(get(L + "?actifs=true").with(roles(role)))
					.andExpect(status().isOk())
					.andExpect(header().exists("ETag"))
					.andExpect(jsonPath("$.referentiels.STATUT_POINTAGE[0].code").value("REJETE_QR_INVALIDE"))
					.andExpect(jsonPath("$.referentiels.STATUT_POINTAGE[0].libelle").value("QR non reconnu"));
		}
		mockMvc.perform(get(L)).andExpect(status().isUnauthorized());
	}
}
