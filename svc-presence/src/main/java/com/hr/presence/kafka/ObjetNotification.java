package com.hr.presence.kafka;

import java.util.UUID;

/**
 * Objet porté par une notification (ticket 2F). {@code type} appartient à la liste fermée partagée avec le web et le
 * mobile ({@code POINTAGE_MANUEL}, {@code HEURES_SUP}, ...) ; nul pour une information sans page de détail. Un seul
 * {@code notificationId} par événement : tous ses destinataires le partagent (dédoublonnage en cas de rejeu).
 */
public record ObjetNotification(String type, String evenement, UUID id, String numero, UUID notificationId) {

	public static final String POINTAGE_MANUEL = "POINTAGE_MANUEL";
	public static final String HEURES_SUP = "HEURES_SUP";

	public static ObjetNotification de(String type, String evenement, UUID id, String numero) {
		return new ObjetNotification(type, evenement, id, numero, UUID.randomUUID());
	}

	/** Information sans objet cible (ex. vérification faciale) : l'ouverture mène à la liste. */
	public static ObjetNotification sansObjet(String evenement) {
		return de(null, evenement, null, null);
	}
}
