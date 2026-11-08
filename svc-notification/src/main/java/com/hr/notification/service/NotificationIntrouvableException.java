package com.hr.notification.service;

/** Notification inconnue ou appartenant à un autre compte (même réponse : 404, pour ne pas révéler son existence). */
public class NotificationIntrouvableException extends RuntimeException {

    public NotificationIntrouvableException() {
        super("Notification introuvable");
    }
}
