package com.hr.presence.domain;

/**
 * Signalement d'un pointage dans la liste admin (D-P18.b) : orphelin au sens de l'appariement D-P17.c, ou ENTREE
 * encore ouverte dans sa fenêtre (D-P17.e). Un pointage apparié n'a aucun état.
 */
public enum EtatSequencePointage {
	ENTREE_SANS_SORTIE,
	SORTIE_SANS_ENTREE,
	EN_COURS
}
