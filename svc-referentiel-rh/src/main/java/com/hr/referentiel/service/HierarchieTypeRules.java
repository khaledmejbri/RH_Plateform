package com.hr.referentiel.service;

import com.hr.referentiel.domain.TypeNoeudOrganisation;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.UniteOrganisationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * D-H04 — règles de structure : Direction Générale (unique, racine) &gt; Direction &gt; Département &gt; Service.
 * <ul>
 *   <li>Une direction est toujours rattachée à la Direction Générale (rattachement automatique à la création).</li>
 *   <li>Un département ou un service peut rester « à rattacher » (sans parent) puis être placé sous un nœud de niveau
 *   supérieur.</li>
 * </ul>
 * Toute violation → {@link IllegalArgumentException} (400).
 */
public class HierarchieTypeRules {

	private final UniteOrganisationRepository uniteRepository;

	public HierarchieTypeRules(UniteOrganisationRepository uniteRepository) {
		this.uniteRepository = uniteRepository;
	}

	public TypeNoeudOrganisation exigerType(String brut) {
		return TypeNoeudOrganisation.depuisLibelle(brut).orElseThrow(() -> new IllegalArgumentException(
				"Type de nœud invalide : « " + brut + " ». Types autorisés : "
						+ TypeNoeudOrganisation.libellesAutorises() + "."));
	}

	/** Parent effectif d'un nouveau nœud (la direction est rattachée d'office à la DG). */
	public UniteOrganisation resoudreParentCreation(TypeNoeudOrganisation type, UniteOrganisation parentDemande) {
		if (type == TypeNoeudOrganisation.DIRECTION_GENERALE && directionGenerale(null).isPresent()) {
			throw new IllegalArgumentException("Une Direction Générale existe déjà : il ne peut y en avoir qu'une.");
		}
		UniteOrganisation parent = parentDemande;
		if (type == TypeNoeudOrganisation.DIRECTION && parent == null) {
			parent = directionGenerale(null).orElseThrow(() -> new IllegalArgumentException(
					"Créez d'abord la Direction Générale : une direction y est toujours rattachée."));
		}
		verifierParent(type, parent);
		return parent;
	}

	/** Contrôle d'un rattachement (ou détachement si {@code nouveauParent} est null) d'un nœud existant. */
	public void verifierRattachement(UniteOrganisation noeud, UniteOrganisation nouveauParent) {
		verifierParent(typeExistant(noeud), nouveauParent);
	}

	/** Contrôle d'un changement de type : parent actuel, enfants et unicité de la DG. */
	public void verifierChangementType(UniteOrganisation noeud, TypeNoeudOrganisation nouveau) {
		if (nouveau == TypeNoeudOrganisation.DIRECTION_GENERALE
				&& directionGenerale(noeud.getId()).isPresent()) {
			throw new IllegalArgumentException("Une Direction Générale existe déjà : il ne peut y en avoir qu'une.");
		}
		verifierParent(nouveau, noeud.getParent());
		if (noeud.getId() == null) {
			return;
		}
		for (UniteOrganisation enfant : uniteRepository.findByParentId(noeud.getId())) {
			TypeNoeudOrganisation.depuisLibelle(enfant.getTypeNoeud()).ifPresent(te -> {
				if (!nouveau.peutContenir(te)) {
					throw new IllegalArgumentException("Impossible de passer « " + noeud.getLibelle() + " » en "
							+ nouveau.getLibelle() + " : il contient « " + enfant.getLibelle() + " » ("
							+ te.getLibelle() + ").");
				}
			});
		}
	}

	private void verifierParent(TypeNoeudOrganisation type, UniteOrganisation parent) {
		if (parent == null) {
			if (type == TypeNoeudOrganisation.DIRECTION) {
				throw new IllegalArgumentException("Une direction doit rester rattachée à la Direction Générale.");
			}
			return;
		}
		if (type == TypeNoeudOrganisation.DIRECTION_GENERALE) {
			throw new IllegalArgumentException("La Direction Générale est au sommet : elle n'a pas de parent.");
		}
		TypeNoeudOrganisation typeParent = typeExistant(parent);
		if (type == TypeNoeudOrganisation.DIRECTION && typeParent != TypeNoeudOrganisation.DIRECTION_GENERALE) {
			throw new IllegalArgumentException("Une direction se rattache uniquement à la Direction Générale.");
		}
		if (!typeParent.peutContenir(type)) {
			throw new IllegalArgumentException("Un nœud de type " + type.getLibelle() + " ne peut pas être placé sous "
					+ typeParent.getLibelle().toLowerCase() + " « " + parent.getLibelle() + " ».");
		}
	}

	private TypeNoeudOrganisation typeExistant(UniteOrganisation u) {
		return TypeNoeudOrganisation.depuisLibelle(u.getTypeNoeud()).orElseThrow(() -> new IllegalArgumentException(
				"« " + u.getLibelle() + " » a un type non reconnu (« " + u.getTypeNoeud() + " ») : corrigez son type ("
						+ TypeNoeudOrganisation.libellesAutorises() + ") avant de le déplacer."));
	}

	private Optional<UniteOrganisation> directionGenerale(UUID sauf) {
		List<UniteOrganisation> toutes = uniteRepository.findAll();
		return toutes.stream()
				.filter(u -> sauf == null || !sauf.equals(u.getId()))
				.filter(u -> TypeNoeudOrganisation.depuisLibelle(u.getTypeNoeud())
						.filter(t -> t == TypeNoeudOrganisation.DIRECTION_GENERALE).isPresent())
				.findFirst();
	}
}
