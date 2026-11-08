package com.hr.evaluation.kafka;

import java.util.UUID;

/**
 * Objet porté par une notification (ticket 2F). {@code type} appartient à la liste fermée partagée avec le web et le
 * mobile ({@code EVALUATION}, {@code CAMPAGNE}, {@code ALERTE_ECART}, ...). Un seul {@code notificationId} par
 * événement : tous les destinataires du même événement le partagent (dédoublonnage en cas de rejeu).
 */
public record ObjetNotification(String type, String evenement, UUID id, String numero, UUID notificationId) {

	public static final String EVALUATION = "EVALUATION";
	public static final String CAMPAGNE = "CAMPAGNE";
	public static final String ALERTE_ECART = "ALERTE_ECART";

	public static ObjetNotification de(String type, String evenement, UUID id, String numero) {
		return new ObjetNotification(type, evenement, id, numero, UUID.randomUUID());
	}
}
