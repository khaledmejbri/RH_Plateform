package com.hr.notification.web;

import com.hr.notification.dto.ErreurApi;
import com.hr.notification.service.NotificationIntrouvableException;
import com.hr.notification.service.ParametreInvalideException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Traduit les erreurs en réponses {code, message} directement, sans renvoi vers /error : ce renvoi repasse par le
 * filtre de sécurité et transformait toute erreur en 403 sans corps.
 */
@RestControllerAdvice
public class NotificationExceptionHandler {

    @ExceptionHandler(NotificationIntrouvableException.class)
    public ResponseEntity<ErreurApi> introuvable(NotificationIntrouvableException e) {
        return reponse(HttpStatus.NOT_FOUND, "NOTIFICATION_INTROUVABLE", "Notification introuvable");
    }

    @ExceptionHandler({ParametreInvalideException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErreurApi> parametreInvalide(Exception e) {
        String message = e instanceof ParametreInvalideException ? e.getMessage() : "Paramètre invalide";
        return reponse(HttpStatus.BAD_REQUEST, "PARAMETRE_INVALIDE", message);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErreurApi> statut(ResponseStatusException e) {
        HttpStatus statut = HttpStatus.valueOf(e.getStatusCode().value());
        String code = statut == HttpStatus.FORBIDDEN ? "ACCES_REFUSE" : "ERREUR";
        return reponse(statut, code, e.getReason() != null ? e.getReason() : statut.getReasonPhrase());
    }

    private static ResponseEntity<ErreurApi> reponse(HttpStatus statut, String code, String message) {
        return ResponseEntity.status(statut).body(new ErreurApi(code, message));
    }
}
