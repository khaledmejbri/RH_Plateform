package com.hr.referentiel.service;

import com.hr.referentiel.domain.TypeNoeudOrganisation;
import com.hr.referentiel.dto.CollaborateurPointageResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Périmètre de validation D-H03 pour les heures supplémentaires de svc-presence (D-P13.a) : résolution de
 * collaborateurs avec leur valideur, et liste des collaborateurs dont une fiche est le valideur. Lecture seule ;
 * exposé uniquement par les endpoints {@code /internal} (jeton de service).
 */
@Service
public class PerimetreValidationService {

	/** Nombre maximal d'identifiants par appel de résolution (une demande en porte au plus 50). */
	public static final int RESOLUTION_MAX = 100;

	private final CollaborateurRepository collaborateurRepository;

	public PerimetreValidationService(CollaborateurRepository collaborateurRepository) {
		this.collaborateurRepository = collaborateurRepository;
	}

	/** Un identifiant inconnu est absent du résultat. */
	@Transactional(readOnly = true)
	public List<CollaborateurPointageResponse> resoudre(Collection<UUID> identifiants) {
		return collaborateurRepository.findAllById(identifiants).stream().map(this::versReponse).toList();
	}

	/** Collaborateurs actifs dont le valideur D-H03 est {@code ficheId}. */
	@Transactional(readOnly = true)
	public List<CollaborateurPointageResponse> perimetreDe(UUID ficheId) {
		return collaborateurRepository.findAllActifsWithUnite().stream()
				.filter(c -> {
					Collaborateur valideur = SuperieurHierarchieRules.resoudreValideurM01(c);
					return valideur != null && valideur.getId().equals(ficheId);
				})
				.map(this::versReponse)
				.sorted(Comparator.comparing(r -> r.nom() != null ? r.nom() : ""))
				.toList();
	}

	private CollaborateurPointageResponse versReponse(Collaborateur c) {
		Collaborateur valideur = SuperieurHierarchieRules.resoudreValideurM01(c);
		UniteOrganisation direction = direction(c.getUnite());
		return new CollaborateurPointageResponse(
				c.getId(),
				c.getCompteUtilisateurId(),
				c.getMatricule(),
				nomComplet(c),
				SuperieurHierarchieRules.estActif(c),
				direction != null ? direction.getId() : null,
				direction != null ? direction.getLibelle() : null,
				valideur != null ? valideur.getId() : null,
				valideur != null ? valideur.getCompteUtilisateurId() : null);
	}

	/** Direction (D-H04) : premier nœud de type « Direction » en remontant depuis l'unité ; aucune sous la DG. */
	static UniteOrganisation direction(UniteOrganisation unite) {
		for (UniteOrganisation u = unite; u != null; u = u.getParent()) {
			if (TypeNoeudOrganisation.depuisLibelle(u.getTypeNoeud()).orElse(null) == TypeNoeudOrganisation.DIRECTION) {
				return u;
			}
		}
		return null;
	}

	private static String nomComplet(Collaborateur c) {
		String complet = ((c.getPrenom() != null ? c.getPrenom() : "") + " " + (c.getNom() != null ? c.getNom() : "")).trim();
		return complet.isEmpty() ? null : complet;
	}
}
