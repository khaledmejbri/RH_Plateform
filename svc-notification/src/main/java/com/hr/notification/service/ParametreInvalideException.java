package com.hr.notification.service;

/** Paramètre de requête hors des bornes autorisées (400). */
public class ParametreInvalideException extends RuntimeException {

    public ParametreInvalideException(String message) {
        super(message);
    }
}
