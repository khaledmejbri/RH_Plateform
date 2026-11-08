package com.hr.notification.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

/**
 * Authentifie la trame STOMP CONNECT : le jeton doit être présent, de signature valide (JWKS de
 * svc-identite-acces), non expiré et porter un compte ({@code identifiant_utilisateur}). Sinon la connexion est
 * refusée. Le compte devient le nom du principal : {@code /user/queue/notifications} ne sert que ce compte.
 * Les autres trames exigent une session authentifiée ; seul l'abonnement à sa propre file est permis.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthInterceptor implements ChannelInterceptor {

    static final String CLAIM_COMPTE = "identifiant_utilisateur";
    static final String ATTRIBUT_COMPTE = "compte_authentifie";
    static final String FILE_UTILISATEUR = "/user/queue/";

    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand commande = accessor.getCommand();

        if (StompCommand.CONNECT.equals(commande) || StompCommand.STOMP.equals(commande)) {
            Principal principal = authentifier(accessor);
            accessor.setUser(principal);
            if (accessor.getSessionAttributes() != null) {
                accessor.getSessionAttributes().put(ATTRIBUT_COMPTE, principal.getName());
            }
        } else if (StompCommand.SUBSCRIBE.equals(commande) || StompCommand.SEND.equals(commande)) {
            if (accessor.getUser() == null && !sessionAuthentifiee(accessor)) {
                throw new MessageDeliveryException("Connexion non authentifiée");
            }
            if (StompCommand.SEND.equals(commande)) {
                throw new MessageDeliveryException("Envoi non autorisé");
            }
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith(FILE_UTILISATEUR)) {
                throw new MessageDeliveryException("Abonnement non autorisé");
            }
        }
        return message;
    }

    private static boolean sessionAuthentifiee(StompHeaderAccessor accessor) {
        return accessor.getSessionAttributes() != null && accessor.getSessionAttributes().get(ATTRIBUT_COMPTE) != null;
    }

    private Principal authentifier(StompHeaderAccessor accessor) {
        String entete = accessor.getFirstNativeHeader("Authorization");
        if (entete == null) {
            entete = accessor.getFirstNativeHeader("authorization");
        }
        if (entete == null || !entete.regionMatches(true, 0, "Bearer ", 0, 7)) {
            log.warn("STOMP CONNECT refusé : jeton absent");
            throw new MessageDeliveryException("Jeton absent");
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(entete.substring(7).trim());
        } catch (JwtException e) {
            log.warn("STOMP CONNECT refusé : jeton invalide ou expiré");
            throw new MessageDeliveryException("Jeton invalide");
        }
        String compte = jwt.getClaimAsString(CLAIM_COMPTE);
        try {
            compte = UUID.fromString(compte).toString();
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("STOMP CONNECT refusé : jeton sans compte");
            throw new MessageDeliveryException("Jeton sans compte");
        }
        final String nom = compte;
        return () -> nom;
    }
}
