package com.hr.referentiel.domain;

public enum TypeDemandeAdministrativeRh {
	CONGE("de congé"),
	AUTORISATION_SORTIE("d'autorisation de sortie"),
	ORDRE_MISSION("d'ordre de mission");

	private final String libelleDemande;

	TypeDemandeAdministrativeRh(String libelleDemande) {
		this.libelleDemande = libelleDemande;
	}

	/** Complément de « Demande » sans code : « de congé », « d'autorisation de sortie », « d'ordre de mission ». */
	public String getLibelleDemande() {
		return libelleDemande;
	}
}
