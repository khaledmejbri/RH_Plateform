package com.hr.referentiel.service;

import com.hr.referentiel.champsperso.DefinitionsPerso;
import com.hr.referentiel.champsperso.DefinitionsPerso.Def;
import com.hr.referentiel.champsperso.ValeursPersoFicheService;
import com.hr.referentiel.dto.CollaborateurResponse;
import com.hr.referentiel.dto.PageReferentielResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Liste filtrée des collaborateurs (V5, ADR partie 2, P2-7) : tous les filtres de la page passent côté serveur, avec le
 * total et la pagination exacts. Tri nom, prénom, puis identifiant (tri stable entre pages). La spécification est aussi
 * celle de l'export ({@link ExportCollaborateursService}).
 */
@Service
public class RechercheCollaborateursService {

	public static final int TAILLE_MAX = 200;

	private final CollaborateurRepository collaborateurRepository;
	private final UniteOrganisationRepository uniteRepository;
	private final DefinitionsPerso definitions;
	private final ReferentielRhService referentielRhService;

	public RechercheCollaborateursService(CollaborateurRepository collaborateurRepository,
			UniteOrganisationRepository uniteRepository, DefinitionsPerso definitions, ReferentielRhService referentielRhService) {
		this.collaborateurRepository = collaborateurRepository;
		this.uniteRepository = uniteRepository;
		this.definitions = definitions;
		this.referentielRhService = referentielRhService;
	}

	@Transactional(readOnly = true)
	public PageReferentielResponse<CollaborateurResponse> lister(FiltreCollaborateurs filtre, int page, int taille) {
		Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(TAILLE_MAX, Math.max(1, taille)), TRI);
		Page<Collaborateur> p = collaborateurRepository.findAll(specification(filtre), pageable);
		return new PageReferentielResponse<>(p.getContent().stream().map(referentielRhService::versReponse).toList(),
				p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize());
	}

	static final Sort TRI = Sort.by("nom").ascending().and(Sort.by("prenom").ascending()).and(Sort.by("id").ascending());

	/** Spécification du filtre (validée avant toute requête : 400 {@code FILTRE_INVALIDE}). */
	Specification<Collaborateur> specification(FiltreCollaborateurs f) {
		Map<String, Def> defs = f.criteres().isEmpty() ? Map.of() : definitions.lire(ValeursPersoFicheService.TYPE);
		return CollaborateurSpecifications.construire(f, unites(f), defs);
	}

	/** Intersection des sous-arbres de la direction, du département et du service choisis ; null si aucun n'est choisi. */
	private Set<UUID> unites(FiltreCollaborateurs f) {
		if (f.direction() == null && f.departement() == null && f.service() == null) {
			return null;
		}
		Map<UUID, Set<UUID>> enfants = new HashMap<>();
		for (UniteOrganisation u : uniteRepository.findAll()) {
			if (u.getParent() != null) {
				enfants.computeIfAbsent(u.getParent().getId(), k -> new HashSet<>()).add(u.getId());
			}
		}
		Set<UUID> r = null;
		for (UUID racine : new UUID[] {f.direction(), f.departement(), f.service()}) {
			if (racine == null) {
				continue;
			}
			Set<UUID> arbre = new HashSet<>();
			ajouter(racine, enfants, arbre);
			if (r == null) {
				r = arbre;
			} else {
				r.retainAll(arbre);
			}
		}
		return r;
	}

	private static void ajouter(UUID id, Map<UUID, Set<UUID>> enfants, Set<UUID> arbre) {
		if (arbre.add(id)) {
			enfants.getOrDefault(id, Set.of()).forEach(e -> ajouter(e, enfants, arbre));
		}
	}
}
