package com.hr.referentiel.service;

import com.hr.referentiel.config.CacheConfig;
import com.hr.referentiel.domain.TypeNoeudOrganisation;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrganigrammeService {

	private final UniteOrganisationRepository uniteRepository;
	private final CollaborateurRepository collaborateurRepository;

	public OrganigrammeService(UniteOrganisationRepository uniteRepository,
			CollaborateurRepository collaborateurRepository) {
		this.uniteRepository = uniteRepository;
		this.collaborateurRepository = collaborateurRepository;
		this.hierarchieTypeRules = new HierarchieTypeRules(uniteRepository);
	}

	private final HierarchieTypeRules hierarchieTypeRules;

	@Transactional(readOnly = true)
	public OrganigrammeResponse obtenirOrganigramme(boolean inclureInactifs) {
		List<UniteOrganisation> unites = inclureInactifs
				? uniteRepository.findAllWithParentAndManager()
				: uniteRepository.findByActifTrueWithParentAndManager();

		Map<UUID, List<Collaborateur>> collabsParUnite = collaborateurRepository.findAllActifsWithUnite().stream()
				.collect(Collectors.groupingBy(c -> c.getUnite().getId()));

		Map<UUID, OrganigrammeNoeudResponse> noeuds = new HashMap<>();
		for (UniteOrganisation u : unites) {
			noeuds.put(u.getId(), toNoeud(u, collabsParUnite.getOrDefault(u.getId(), List.of())));
		}

		List<OrganigrammeNoeudResponse> racines = new ArrayList<>();
		for (UniteOrganisation u : unites) {
			OrganigrammeNoeudResponse noeud = noeuds.get(u.getId());
			if (u.getParent() == null || !noeuds.containsKey(u.getParent().getId())) {
				racines.add(noeud);
			} else {
				noeuds.get(u.getParent().getId()).getEnfants().add(noeud);
			}
		}

		racines.sort((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()));
		noeuds.values().forEach(n -> n.getEnfants().sort((a, b) -> a.getCode().compareToIgnoreCase(b.getCode())));
		return new OrganigrammeResponse(racines);
	}

	@Transactional
	@CacheEvict(value = CacheConfig.CACHE_UNITES, allEntries = true)
	public OrganigrammeNoeudResponse creerNoeud(OrganigrammeNoeudCreationRequest req) {
		if (uniteRepository.findByCodeIgnoreCase(req.getCode().trim()).isPresent()) {
			throw new IllegalArgumentException("Ce code d'unité existe déjà : " + req.getCode());
		}
		UniteOrganisation u = new UniteOrganisation();
		u.setCode(req.getCode().trim());
		u.setLibelle(req.getLibelle().trim());
		// D-H04 : type du catalogue ; direction rattachée d'office à la DG ; parent de niveau supérieur
		TypeNoeudOrganisation type = hierarchieTypeRules.exigerType(req.getTypeNoeud());
		u.setTypeNoeud(type.getLibelle());
		u.setTitrePoste(trimToNull(req.getTitrePoste()));
		u.setActif(req.getActif() == null || req.getActif());
		UniteOrganisation parent = null;
		if (req.getParentIdentifiant() != null) {
			parent = uniteRepository.findById(req.getParentIdentifiant())
					.orElseThrow(() -> new IllegalArgumentException("Nœud parent introuvable."));
		}
		u.setParent(hierarchieTypeRules.resoudreParentCreation(type, parent));
		UniteOrganisation saved = uniteRepository.save(u);
		return toNoeud(saved, List.of());
	}

	@Transactional
	@CacheEvict(value = { CacheConfig.CACHE_UNITES, CacheConfig.CACHE_COLLABORATEUR_ID,
			CacheConfig.CACHE_COLLABORATEUR_MATRICULE }, allEntries = true)
	public OrganigrammeNoeudResponse mettreAJourNoeud(UUID id, OrganigrammeNoeudMiseAJourRequest req) {
		UniteOrganisation u = uniteRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Nœud introuvable."));
		if (req.getLibelle() != null) {
			u.setLibelle(req.getLibelle().trim());
		}
		if (req.getTypeNoeud() != null) {
			TypeNoeudOrganisation type = hierarchieTypeRules.exigerType(req.getTypeNoeud());
			hierarchieTypeRules.verifierChangementType(u, type);
			u.setTypeNoeud(type.getLibelle());
		}
		if (req.getTitrePoste() != null) {
			u.setTitrePoste(trimToNull(req.getTitrePoste()));
		}
		if (req.getActif() != null) {
			u.setActif(req.getActif());
		}
		if (Boolean.TRUE.equals(req.getDetacherDuParent())) {
			hierarchieTypeRules.verifierRattachement(u, null);
			u.setParent(null);
		} else if (req.getParentIdentifiant() != null) {
			if (req.getParentIdentifiant().equals(id)) {
				throw new IllegalArgumentException("Un nœud ne peut pas être son propre parent.");
			}
			UniteOrganisation parent = uniteRepository.findById(req.getParentIdentifiant())
					.orElseThrow(() -> new IllegalArgumentException("Nœud parent introuvable."));
			verifierPasDeCycle(id, parent);
			hierarchieTypeRules.verifierRattachement(u, parent);
			u.setParent(parent);
			if (u.getManager() != null) {
				Collaborateur cible = SuperieurHierarchieRules.resoudreSuperieurFiche(u.getManager(), u);
				if (SuperieurHierarchieRules.appliquerSiChange(u.getManager(), cible)) {
					collaborateurRepository.save(u.getManager());
				}
			}
		}
		UniteOrganisation saved = uniteRepository.save(u);
		List<Collaborateur> membres = collaborateurRepository.findByUniteId(saved.getId());
		return toNoeud(saved, membres);
	}

	@Transactional
	@CacheEvict(value = { CacheConfig.CACHE_UNITES, CacheConfig.CACHE_COLLABORATEUR_ID,
			CacheConfig.CACHE_COLLABORATEUR_MATRICULE }, allEntries = true)
	public OrganigrammeNoeudResponse assignerManager(UUID noeudId, OrganigrammeAssignerManagerRequest req) {
		UniteOrganisation noeud = uniteRepository.findById(noeudId)
				.orElseThrow(() -> new IllegalArgumentException("Nœud introuvable."));
		Collaborateur manager = collaborateurRepository.findById(req.getCollaborateurIdentifiant())
				.orElseThrow(() -> new IllegalArgumentException("Collaborateur introuvable."));
		// role_workflow : ignoré (rétrocompat JSON). profil_acces n'est plus muté ici.

		if (req.getTitrePoste() != null && !req.getTitrePoste().isBlank()) {
			noeud.setTitrePoste(req.getTitrePoste().trim());
		}

		manager.setUnite(noeud);
		if (noeud.getTitrePoste() != null) {
			manager.setPosteLibelle(noeud.getTitrePoste());
		} else if (req.getTitrePoste() != null) {
			manager.setPosteLibelle(trimToNull(req.getTitrePoste()));
		}

		noeud.setManager(manager);
		// H3-R02 : le manager du nœud n'a jamais soi-même comme supérieur
		Collaborateur superieurManager = SuperieurHierarchieRules.resoudreSuperieurFiche(manager, noeud);
		SuperieurHierarchieRules.appliquerSiChange(manager, superieurManager);
		collaborateurRepository.save(manager);
		UniteOrganisation saved = uniteRepository.save(noeud);

		synchroniserSuperieursMembresActifs(saved);
		propagerSuperieurAuxManagersEnfants(saved, SuperieurHierarchieRules.managerActif(saved));
		List<Collaborateur> membres = collaborateurRepository.findByUniteId(saved.getId());
		return toNoeud(saved, membres);
	}

	@Transactional
	@CacheEvict(value = { CacheConfig.CACHE_UNITES, CacheConfig.CACHE_COLLABORATEUR_ID,
			CacheConfig.CACHE_COLLABORATEUR_MATRICULE }, allEntries = true)
	public OrganigrammeNoeudResponse retirerManager(UUID noeudId) {
		UniteOrganisation noeud = uniteRepository.findById(noeudId)
				.orElseThrow(() -> new IllegalArgumentException("Nœud introuvable."));
		noeud.setManager(null);
		UniteOrganisation saved = uniteRepository.save(noeud);
		// H3-T4 : membres ACTIFS → superieur null (écart actuel corrigé)
		synchroniserSuperieursMembresActifs(saved);
		propagerSuperieurAuxManagersEnfants(saved, null);
		return toNoeud(saved, collaborateurRepository.findByUniteId(saved.getId()));
	}

	/**
	 * H3-T3/T4 Must : aligne {@code superieur} des membres ACTIFS du nœud (idempotent).
	 * Ne mute jamais {@code valideur_attendu} des demandes.
	 */
	private void synchroniserSuperieursMembresActifs(UniteOrganisation noeud) {
		List<Collaborateur> membres = collaborateurRepository.findByUniteId(noeud.getId());
		for (Collaborateur m : membres) {
			if (!SuperieurHierarchieRules.estActif(m)) {
				continue;
			}
			Collaborateur cible = SuperieurHierarchieRules.resoudreSuperieurFiche(m, noeud);
			if (SuperieurHierarchieRules.appliquerSiChange(m, cible)) {
				collaborateurRepository.save(m);
			}
		}
	}

	private void propagerSuperieurAuxManagersEnfants(UniteOrganisation parent, Collaborateur managerParent) {
		uniteRepository.findByParentId(parent.getId()).forEach(enfant -> {
			if (enfant.getManager() == null || !SuperieurHierarchieRules.estActif(enfant.getManager())) {
				return;
			}
			Collaborateur managerEnfant = enfant.getManager();
			// R02 côté enfant : son superieur = manager ACTIF du parent
			Collaborateur cible = SuperieurHierarchieRules.estActif(managerParent) ? managerParent : null;
			if (SuperieurHierarchieRules.appliquerSiChange(managerEnfant, cible)) {
				collaborateurRepository.save(managerEnfant);
			}
		});
	}

	private void verifierPasDeCycle(UUID noeudId, UniteOrganisation nouveauParent) {
		Set<UUID> vus = new HashSet<>();
		UniteOrganisation courant = nouveauParent;
		while (courant != null) {
			if (courant.getId().equals(noeudId)) {
				throw new IllegalArgumentException("Rattachement impossible : cycle détecté dans la hiérarchie.");
			}
			if (!vus.add(courant.getId())) {
				break;
			}
			courant = courant.getParent();
		}
	}

	private OrganigrammeNoeudResponse toNoeud(UniteOrganisation u, List<Collaborateur> collabsUnite) {
		OrganigrammeNoeudResponse n = new OrganigrammeNoeudResponse();
		n.setIdentifiant(u.getId());
		n.setCode(u.getCode());
		n.setLibelle(u.getLibelle());
		n.setTypeNoeud(u.getTypeNoeud() != null ? u.getTypeNoeud()
				: (u.getParent() == null ? "Département" : "Unité"));
		n.setTitrePoste(u.getTitrePoste());
		n.setParentIdentifiant(u.getParent() != null ? u.getParent().getId() : null);
		n.setActif(u.isActif());
		UUID managerId = u.getManager() != null ? u.getManager().getId() : null;
		if (u.getManager() != null) {
			n.setManager(toMembre(u.getManager()));
		} else {
			// Compat Structure RH : manager implicite via profil RESPONSABLE / RO sur l'unité
			collabsUnite.stream()
					.filter(c -> "RESPONSABLE".equalsIgnoreCase(c.getProfilAcces())
							|| "RO".equalsIgnoreCase(c.getProfilAcces()))
					.findFirst()
					.ifPresent(c -> n.setManager(toMembre(c)));
			if (n.getManager() != null) {
				managerId = n.getManager().getIdentifiant();
			}
		}
		UUID mid = managerId;
		List<OrganigrammeMembreResponse> membres = collabsUnite.stream()
				.filter(c -> mid == null || !c.getId().equals(mid))
				.map(this::toMembre)
				.collect(Collectors.toList());
		n.setMembres(membres);
		return n;
	}

	private OrganigrammeMembreResponse toMembre(Collaborateur c) {
		return new OrganigrammeMembreResponse(
				c.getId(),
				c.getMatricule(),
				c.getPrenom(),
				c.getNom(),
				c.getPosteLibelle(),
				c.getProfilAcces());
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}
}
