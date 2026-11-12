package com.hr.evaluation.web;

import com.hr.evaluation.config.SecurityConfig;
import com.hr.evaluation.dto.ParametreDto;
import com.hr.evaluation.security.IdentiteAppelant;
import com.hr.evaluation.security.IdentiteAppelantResolver;
import com.hr.evaluation.service.EvaluationParametresService;
import com.hr.evaluation.service.ParametreService;
import com.hr.evaluation.service.ParametresEvaluation;
import com.hr.evaluation.service.ParametresInstantane;
import com.hr.evaluation.service.ParametresTestes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
 * Droits des endpoints de paramétrage (ADR lot 3 § 7) : lecture RH / DIRECTION / ADMIN, écriture RH / ADMIN, 403 pour
 * CS-WEB (RO), COL-MOB (collaborateur) et DG01 (DIRECTION) en écriture, 401 sans jeton ; libellés pour tout
 * authentifié, JSON snake_case, ETag et 304.
 */
@WebMvcTest(controllers = {EvaluationParametresController.class, EvaluationLibellesController.class})
@Import(SecurityConfig.class)
class EvaluationParametresControllerSecurityTest {

    private static final String B = "/api/rh/v1/admin/evaluations/parametres";
    private static final String L = "/api/rh/v1/evaluations/parametres/libelles";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationParametresService parametresService;
    @MockitoBean
    private ParametreService parametreService;
    @MockitoBean
    private ParametresEvaluation parametres;
    @MockitoBean
    private IdentiteAppelantResolver identiteResolver;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final ParametreDto.Valeur VALEUR = new ParametreDto.Valeur("ECART_LEGER", "Écart léger", null,
            "BLEU", 4, true, false, "VALEUR", 0, 0, false, 1);

    @BeforeEach
    void setUp() {
        when(identiteResolver.courant()).thenReturn(new IdentiteAppelant(UUID.randomUUID(), "amel", true, Optional::empty));
        when(parametreService.referentiels()).thenReturn(List.of());
        when(parametreService.valeurs("NIVEAU_ALERTE")).thenReturn(List.of(VALEUR));
        when(parametreService.creer(eq("NIVEAU_ALERTE"), any(), any())).thenReturn(VALEUR);
        when(parametreService.modifier(eq("NIVEAU_ALERTE"), eq("ECART_LEGER"), any(), any())).thenReturn(VALEUR);
        when(parametreService.desactiver(eq("NIVEAU_ALERTE"), eq("ECART_LEGER"), any(), any())).thenReturn(VALEUR);
        when(parametreService.reactiver(eq("NIVEAU_ALERTE"), eq("ECART_LEGER"), any(), any())).thenReturn(VALEUR);
        when(parametreService.reordonner(eq("NIVEAU_ALERTE"), any(), any())).thenReturn(List.of(VALEUR));
        when(parametreService.journal(eq("NIVEAU_ALERTE"), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(new ParametreDto.PageJournal(List.of(), 0, 20, 0));
        when(parametresService.mettreAJour(any(), any())).thenReturn(new com.hr.evaluation.entity.EvaluationParametres());
        when(parametresService.reglages()).thenReturn(List.of());
        when(parametresService.journalReglages(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(new ParametreDto.PageJournal(List.of(), 0, 20, 0));
        when(parametresService.modifierReglage(eq("seuil_insuffisance"), any(), any(), any())).thenReturn(
                new ParametreDto.Reglage("seuil_insuffisance", "Seuil", 2, 1, 5, "ETOILES", 3, 1));
    }

    @Test
    void corpsAbsentOuIllisible_400_pasDe403() throws Exception {
        String valeurs = B + "/referentiels/NIVEAU_ALERTE/valeurs";
        mockMvc.perform(post(valeurs).with(avec("RH")).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(valeurs + "/ECART_LEGER").with(avec("RH")).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(valeurs + "/ECART_LEGER/desactiver").with(avec("RH")).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(B + "/reglages/seuil_insuffisance").with(avec("RH")).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(valeurs).with(avec("RH")).contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest());
    }

    private static RequestPostProcessor avec(String... roles) {
        SimpleGrantedAuthority[] a = new SimpleGrantedAuthority[roles.length + 1];
        a[0] = new SimpleGrantedAuthority("ROLE_USER");
        for (int i = 0; i < roles.length; i++) {
            a[i + 1] = new SimpleGrantedAuthority("ROLE_" + roles[i]);
        }
        return jwt().authorities(a);
    }

    private void lectures(RequestPostProcessor qui, int attendu) throws Exception {
        mockMvc.perform(get(B + "/referentiels").with(qui)).andExpect(status().is(attendu));
        mockMvc.perform(get(B + "/referentiels/NIVEAU_ALERTE/valeurs").with(qui)).andExpect(status().is(attendu));
        mockMvc.perform(get(B + "/referentiels/NIVEAU_ALERTE/journal").with(qui)).andExpect(status().is(attendu));
        mockMvc.perform(get(B + "/reglages").with(qui)).andExpect(status().is(attendu));
        mockMvc.perform(get(B + "/reglages/journal").with(qui)).andExpect(status().is(attendu));
    }

    private void ecritures(RequestPostProcessor qui, int creation, int autres) throws Exception {
        String corps = "{\"libelle\":\"Écart léger\",\"seuil_ecart\":1,\"version\":0}";
        mockMvc.perform(post(B + "/referentiels/NIVEAU_ALERTE/valeurs").with(qui)
                .contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().is(creation));
        mockMvc.perform(put(B + "/referentiels/NIVEAU_ALERTE/valeurs/ECART_LEGER").with(qui)
                .contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().is(autres));
        mockMvc.perform(post(B + "/referentiels/NIVEAU_ALERTE/valeurs/ECART_LEGER/desactiver").with(qui)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().is(autres));
        mockMvc.perform(post(B + "/referentiels/NIVEAU_ALERTE/valeurs/ECART_LEGER/reactiver").with(qui)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().is(autres));
        mockMvc.perform(put(B + "/referentiels/NIVEAU_ALERTE/ordre").with(qui)
                .contentType(MediaType.APPLICATION_JSON).content("{\"codes\":[\"ECART_LEGER\"]}")).andExpect(status().is(autres));
        mockMvc.perform(delete(B + "/referentiels/NIVEAU_ALERTE/valeurs/ECART_LEGER").param("version", "0").with(qui))
                .andExpect(status().is(autres == 200 ? 204 : autres));
        mockMvc.perform(put(B + "/reglages/seuil_insuffisance").with(qui)
                .contentType(MediaType.APPLICATION_JSON).content("{\"valeur\":2,\"version\":1}")).andExpect(status().is(autres));
        mockMvc.perform(put(B).with(qui).contentType(MediaType.APPLICATION_JSON).content("{\"dureeCampagneJours\":10}"))
                .andExpect(status().is(autres == 200 ? 200 : autres));
    }

    @Test
    void rhEtAdmin_lisentEtEcrivent() throws Exception {
        for (String role : List.of("RH", "ADMIN")) {
            lectures(avec(role), 200);
            ecritures(avec(role), 201, 200);
        }
    }

    @Test
    void direction_litToutJournalCompris_maisNEcritJamais403() throws Exception {
        lectures(avec("DIRECTION"), 200);
        ecritures(avec("DIRECTION"), 403, 403);
    }

    @Test
    void roEtCollaborateur_403_enLectureEtEnEcriture() throws Exception {
        lectures(avec(), 403);
        ecritures(avec(), 403, 403);
        lectures(avec("RO"), 403);
        ecritures(avec("RO"), 403, 403);
    }

    @Test
    void sansJeton_401() throws Exception {
        mockMvc.perform(get(B + "/referentiels")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(L)).andExpect(status().isUnauthorized());
    }

    @Test
    void creation_renvoie201_etJsonSnakeCase() throws Exception {
        mockMvc.perform(post(B + "/referentiels/NIVEAU_ALERTE/valeurs").with(avec("RH"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"libelle\":\"Écart léger\",\"seuil_ecart\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ECART_LEGER"))
                .andExpect(jsonPath("$.seuil_ecart").value(1))
                .andExpect(jsonPath("$.deja_utilisee").value(false));
        verify(parametreService).creer(eq("NIVEAU_ALERTE"),
                eq(new ParametreDto.CorpsValeur("Écart léger", null, null, null, 1, null)), any());
    }

    @Test
    void libelles_toutUtilisateurAuthentifie_etagEt304() throws Exception {
        var instantane = ParametresInstantane.de(ParametresTestes.initiales(), null);
        when(parametres.instantane()).thenReturn(instantane);
        when(parametreService.libelles(eq(false), eq(false), any())).thenReturn(
                new ParametreDto.Libelles("W/\"abc\"", java.util.Map.of()));

        mockMvc.perform(get(L).with(avec())).andExpect(status().isOk())
                .andExpect(header().string("ETag", "W/\"abc\""))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(jsonPath("$.version").value("W/\"abc\""));
        mockMvc.perform(get(L).with(avec()).header("If-None-Match", "W/\"abc\"")).andExpect(status().isNotModified());
    }
}
