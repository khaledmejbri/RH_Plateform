package com.hr.notification.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StompAuthInterceptorTest {

    private final UUID compte = UUID.randomUUID();
    private StompAuthInterceptor interceptor;

    /** Décodeur de test : seul « valide » passe ; « expire » lève l'erreur d'expiration ; le reste est forgé. */
    @BeforeEach
    void init() {
        JwtDecoder decodeur = token -> {
            if ("valide".equals(token)) {
                return jwt(compte.toString());
            }
            if ("sans-compte".equals(token)) {
                return jwt(null);
            }
            if ("expire".equals(token)) {
                throw new JwtValidationException("expiré", List.of(new OAuth2Error("invalid_token")));
            }
            throw new BadJwtException("signature invalide");
        };
        interceptor = new StompAuthInterceptor(decodeur);
    }

    private static Jwt jwt(String compte) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "utilisateur");
        if (compte != null) {
            claims.put("identifiant_utilisateur", compte);
        }
        return new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "RS256"), claims);
    }

    private Message<?> connect(String authorization) {
        StompHeaderAccessor a = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            a.setNativeHeader("Authorization", authorization);
        }
        a.setSessionAttributes(new HashMap<>());
        a.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], a.getMessageHeaders());
    }

    @Test
    void jetonValideDefinitLeComptePourPrincipal() {
        Message<?> sortie = interceptor.preSend(connect("Bearer valide"), null);

        StompHeaderAccessor a = StompHeaderAccessor.wrap(sortie);
        assertThat(a.getUser()).isNotNull();
        assertThat(a.getUser().getName()).isEqualTo(compte.toString());
    }

    @Test
    void jetonASignatureFausseRefuse() {
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer forge"), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void jetonExpireRefuse() {
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer expire"), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void jetonAbsentOuSansSchemaBearerRefuse() {
        assertThatThrownBy(() -> interceptor.preSend(connect(null), null)).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(connect("valide"), null)).isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void jetonSansCompteRefuse() {
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer sans-compte"), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    private Message<?> trame(StompCommand commande, String destination, Map<String, Object> session) {
        StompHeaderAccessor a = StompHeaderAccessor.create(commande);
        a.setDestination(destination);
        a.setSessionAttributes(session);
        a.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], a.getMessageHeaders());
    }

    private Map<String, Object> sessionAuthentifiee() {
        return new HashMap<>(Map.of(StompAuthInterceptor.ATTRIBUT_COMPTE, compte.toString()));
    }

    @Test
    void abonnementSansSessionAuthentifieeRefuse() {
        Message<?> m = trame(StompCommand.SUBSCRIBE, "/user/queue/notifications", new HashMap<>());
        assertThatThrownBy(() -> interceptor.preSend(m, null)).isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void abonnementASaFileAutorise() {
        Message<?> m = trame(StompCommand.SUBSCRIBE, "/user/queue/notifications", sessionAuthentifiee());
        assertThat(interceptor.preSend(m, null)).isSameAs(m);
    }

    @Test
    void abonnementAUnTopicOuEnvoiClientRefuses() {
        Message<?> topic = trame(StompCommand.SUBSCRIBE, "/topic/rh", sessionAuthentifiee());
        Message<?> envoi = trame(StompCommand.SEND, "/app/x", sessionAuthentifiee());
        assertThatThrownBy(() -> interceptor.preSend(topic, null)).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(envoi, null)).isInstanceOf(MessageDeliveryException.class);
    }
}
