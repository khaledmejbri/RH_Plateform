package com.hr.referentiel.service;

import com.hr.referentiel.domain.TypeNoeudOrganisation;
import com.hr.referentiel.dto.AffectationCollaborateurResponse;
import com.hr.referentiel.dto.MembreUniteResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Affectation actuelle des collaborateurs pour la vue « Temps de travail » de svc-presence (D-P17.g) : filtre par
 * unité (sous-unités comprises) et libellés direction / département / service. Lecture seule, tous statuts confondus
 * (l'historique d'un collaborateur inactif reste visible), exposée par les endpoints {@code /internal} uniquement.
 */
@Service
public class AffectationService {

	/** Nombre maximal d'identifiants par appel de résolution. */
	public static final int RESOLUTION_MAX = 100;

	private final CollaborateurRepository collaborateurRepository;

	public AffectationService(CollaborateurRepository collaborateurRepository) {
		this.collaborateurRepository = collaborateurRepository;
	}

	/** Collaborateurs dont l'unité est {@code uniteId} ou en descend. Unité inconnue : liste vide. */
	@Transactional(readOnly = true)
	public List<MembreUniteResponse> membresDeUnite(UUID uniteId) {
		return collaborateurRepository.findAllWithUnite().stream()
				.filter(c -> descendDe(c.getUnite(), uniteId))
				.map(c -> new MembreUniteResponse(c.getId(), c.getCompteUtilisateurId()))
				.toList();
	}

	/**
	 * Affectation de collaborateurs désignés par leur fiche <em>ou</em> leur compte (un pointage ancien porte
	 * l'identifiant du compte). Un identifiant inconnu est absent du résultat ; une fiche n'apparaît qu'une fois.
	 */
	@Transactional(readOnly = true)
	public List<AffectationCollaborateurResponse> affectations(Collection<UUID> identifiants) {
		Map<UUID, Collaborateur> trouves = new LinkedHashMap<>();
		collaborateurRepository.findAllById(identifiants).forEach(c -> trouves.put(c.getId(), c));
		collaborateurRepository.findAllByCompteUtilisateurIdIn(identifiants).forEach(c -> trouves.putIfAbsent(c.getId(), c));
		return trouves.values().stream().map(AffectationService::versReponse).toList();
	}

	private static boolean descendDe(UniteOrganisation unite, UUID uniteId) {
		Set<UUID> vus = new HashSet<>();
		for (UniteOrganisation u = unite; u != null && vus.add(u.getId()); u = u.getParent()) {
			if (uniteId.equals(u.getId())) {
				return true;
			}
		}
		return false;
	}

	private static AffectationCollaborateurResponse versReponse(Collaborateur c) {
		return new AffectationCollaborateurResponse(
				c.getId(),
				c.getCompteUtilisateurId(),
				c.getMatricule(),
				nomComplet(c),
				libelleDuType(c.getUnite(), TypeNoeudOrganisation.DIRECTION),
				libelleDuType(c.getUnite(), TypeNoeudOrganisation.DEPARTEMENT),
				libelleDuType(c.getUnite(), TypeNoeudOrganisation.SERVICE),
				SuperieurHierarchieRules.estActif(c),
				c.getPosteLibelle(),
				c.getUnite() != null ? c.getUnite().getId() : null,
				c.getUnite() != null ? c.getUnite().getLibelle() : null);
	}

	/** Premier nœud du type voulu en remontant depuis l'unité du collaborateur (lui compris). */
	private static String libelleDuType(UniteOrganisation unite, TypeNoeudOrganisation type) {
		Set<UUID> vus = new HashSet<>();
		for (UniteOrganisation u = unite; u != null && vus.add(u.getId()); u = u.getParent()) {
			if (TypeNoeudOrganisation.depuisLibelle(u.getTypeNoeud()).orElse(null) == type) {
				return u.getLibelle();
			}
		}
		return null;
	}

	private static String nomComplet(Collaborateur c) {
		String complet = ((c.getPrenom() != null ? c.getPrenom() : "") + " " + (c.getNom() != null ? c.getNom() : "")).trim();
		return complet.isEmpty() ? null : complet;
	}
}
