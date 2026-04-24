package com.hr.referentiel.kafka;

public final class RhKafkaTopics {

	private RhKafkaTopics() {
	}

	/** Référentiel → Identité : demande de création de compte pour un collaborateur. */
	public static final String COLLABORATEUR_COMPTE_DEMANDE = "rh.identite.collaborateur-compte.demande";

	/** Identité → Référentiel : compte créé, à lier au collaborateur. */
	public static final String COLLABORATEUR_COMPTE_CREE = "rh.referentiel.collaborateur-compte.cree";
}
