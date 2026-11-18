package com.hr.commun.societe;

/**
 * Exception levée lorsque la société courante est requise mais absente du contexte.
 */
public class SocieteAbsenteException extends RuntimeException {

	public SocieteAbsenteException() {
		super("Aucune société présente dans le contexte courant");
	}

	public SocieteAbsenteException(String message) {
		super(message);
	}
}
