package com.hr.presence.domain;

/** Indicateur porté par un pointage VALIDE (D-P10.4) ; nul quand aucun contrôle facial n'a eu lieu. */
public enum VerificationFaciale {
	VERIFIEE,
	NON_CONSENTIE,
	NON_DISPONIBLE
}
