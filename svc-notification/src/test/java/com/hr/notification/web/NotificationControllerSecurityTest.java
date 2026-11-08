package com.hr.notification.web;

import com.hr.notification.config.SecurityConfig;
import com.hr.notification.dto.CompteurNotifications;
import com.hr.notification.dto.PageNotifications;
import com.hr.notification.service.NotificationIntrouvableException;
import com.hr.notification.service.NotificationStockageService;
import com.hr.notification.service.ParametreInvalideException;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RBAC et anti-IDOR de « Mes notifications » : tous les rôles lisent les leurs, sans JWT 401, jamais celles d'autrui. */
@WebMvcTest(controllers = NotificationController.class)
@Import({SecurityConfig.class, NotificationExceptionHandler.class})
class NotificationControllerSecurityTest {

    private static final String BASE = "/api/rh/v1/notifications";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private NotificationStockageService stockage;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID compte = UUID.randomUUID();

    private RequestPostProcessor role(String role) {
        return jwt().jwt(j -> j.claim("identifiant_utilisateur", compte.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void toutRoleLitSesNotificationsEtLeCompteVientDuJwt() throws Exception {
        when(stockage.lister(eq(compte), any(), eq(0), eq(20))).thenReturn(new PageNotifications(List.of(), 0, 20, 0));
        when(stockage.compter(compte)).thenReturn(new CompteurNotifications(3));

        for (String r : new String[]{"RH", "ADMIN", "DIRECTION", "COLLABORATEUR", "RESPONSABLE"}) {
            mockMvc.perform(get(BASE).with(role(r))).andExpect(status().isOk());
            mockMvc.perform(get(BASE + "/compteur").with(role(r)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.non_lues").value(3));
        }
    }

    @Test
    void sansJwt401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/compteur")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/lue")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/lues")).andExpect(status().isUnauthorized());
        verifyNoInteractions(stockage);
    }

    @Test
    void parametreOuEnteteDesignantUnAutreCompteEstIgnore() throws Exception {
        UUID autre = UUID.randomUUID();
        when(stockage.lister(eq(compte), any(), eq(0), eq(20))).thenReturn(new PageNotifications(List.of(), 0, 20, 0));

        mockMvc.perform(get(BASE).param("compte", autre.toString()).header("X-Collaborateur-Id", autre.toString())
                .with(role("RH"))).andExpect(status().isOk());

        verify(stockage).lister(eq(compte), any(), eq(0), eq(20));
    }

    @Test
    void marquerLueNotificationDUnAutreCompte404MemePourRhEtAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new NotificationIntrouvableException()).when(stockage).marquerLue(id, compte);

        for (String r : new String[]{"RH", "ADMIN"}) {
            mockMvc.perform(post(BASE + "/" + id + "/lue").with(role(r)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOTIFICATION_INTROUVABLE"))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
    }

    @Test
    void marquerLueEtToutLireRepondent204MemePourDirection() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post(BASE + "/" + id + "/lue").with(role("DIRECTION"))).andExpect(status().isNoContent());
        mockMvc.perform(post(BASE + "/lues").with(role("DIRECTION"))).andExpect(status().isNoContent());
        verify(stockage).marquerLue(id, compte);
        verify(stockage).marquerToutesLues(compte);
    }

    @Test
    void identifiantMalForme400() throws Exception {
        mockMvc.perform(post(BASE + "/abc/lue").with(role("RH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
    }

    @Test
    void paginationHorsBornes400AvecCorps() throws Exception {
        when(stockage.lister(eq(compte), any(), anyInt(), anyInt()))
                .thenThrow(new ParametreInvalideException("Pagination invalide"));

        mockMvc.perform(get(BASE).param("taille", "0").with(role("RH")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
        mockMvc.perform(get(BASE).param("taille", "1000").with(role("RH")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
        mockMvc.perform(get(BASE).param("page", "-1").with(role("RH")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
        mockMvc.perform(get(BASE).param("taille", "abc").with(role("RH")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
        mockMvc.perform(get(BASE).param("lu", "peut-etre").with(role("RH")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
    }

    @Test
    void jwtSansCompte403() throws Exception {
        mockMvc.perform(get(BASE).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_RH"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCES_REFUSE"));
    }

    @Test
    void ancienEndpointDeTestEstSupprime() throws Exception {
        mockMvc.perform(post("/api/notifications/test/send").with(role("ADMIN")).contentType("application/json")
                .content("{}")).andExpect(status().isForbidden());
    }
}
