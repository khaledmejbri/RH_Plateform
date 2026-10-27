package com.hr.referentiel.service;

import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;

import java.util.Objects;
import java.util.UUID;

/**
 * H3 — dérivation du champ fiche {@code superieur} depuis le manager ACTIF du nœud.
 * Ne touche jamais {@code valideur_attendu} des demandes M01.
 */
final class SuperieurHierarchieRules {

	private SuperieurHierarchieRules() {
	}

	/**
	 * H3-R01 / H3-R02 : supérieur = manager ACTIF du nœud ; si le collab est ce manager,
	 * remonter au manager ACTIF du parent (jamais soi-même).
	 */
	static Collaborateur resoudreSuperieurFiche(Collaborateur collab, UniteOrganisation unite) {
		if (collab == null || unite == null) {
			return null;
		}
		Collaborateur manager = managerActif(unite);
		if (manager == null) {
			return null;
		}
		if (manager.getId().equals(collab.getId())) {
			UniteOrganisation parent = unite.getParent();
			return parent != null ? managerActif(parent) : null;
		}
		return manager;
	}

	/**
	 * D-H03 (SCRUM-23) : valideur M01 1er niveau. Personne ne valide sa propre demande.
	 * <ul>
	 *   <li>Membre d'un nœud : manager ACTIF de ce nœud ; nœud sans manager → {@code null} (passage RRH, D-H01).</li>
	 *   <li>Manager de son propre nœud : manager ACTIF du premier nœud ancêtre qui en a un (chef de service →
	 *   chef de département → directeur → DG) ; aucun ancêtre → {@code null} (passage RRH).</li>
	 * </ul>
	 */
	static Collaborateur resoudreValideurM01(Collaborateur demandeur) {
		if (demandeur == null || demandeur.getUnite() == null) {
			return null;
		}
		UniteOrganisation unite = demandeur.getUnite();
		Collaborateur manager = managerActif(unite);
		if (manager == null || !manager.getId().equals(demandeur.getId())) {
			return manager;
		}
		for (UniteOrganisation u = unite.getParent(); u != null; u = u.getParent()) {
			Collaborateur m = managerActif(u);
			if (m != null && !m.getId().equals(demandeur.getId())) {
				return m;
			}
		}
		return null;
	}

	static Collaborateur managerActif(UniteOrganisation unite) {
		if (unite == null || unite.getManager() == null) {
			return null;
		}
		Collaborateur manager = unite.getManager();
		return estActif(manager) ? manager : null;
	}

	static boolean estActif(Collaborateur c) {
		return c != null && "ACTIF".equalsIgnoreCase(c.getStatut());
	}

	/**
	 * Applique le supérieur si l'UUID change (idempotence C-S-05).
	 *
	 * @return true si une mutation a été faite
	 */
	static boolean appliquerSiChange(Collaborateur collab, Collaborateur nouveauSuperieur) {
		UUID actuel = collab.getSuperieur() != null ? collab.getSuperieur().getId() : null;
		UUID cible = nouveauSuperieur != null ? nouveauSuperieur.getId() : null;
		if (Objects.equals(actuel, cible)) {
			return false;
		}
		collab.setSuperieur(nouveauSuperieur);
		return true;
	}
}
