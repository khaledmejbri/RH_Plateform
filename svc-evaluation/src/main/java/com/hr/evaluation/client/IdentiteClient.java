package com.hr.evaluation.client;

import java.util.List;
import java.util.UUID;

/** Annuaire des comptes (svc-identite-acces) : destinataires des alertes d'écart. */
public interface IdentiteClient {

	/** Identifiants de compte portant le rôle ; lève une exception si le service est injoignable. */
	List<UUID> comptesParRole(String role);
}
