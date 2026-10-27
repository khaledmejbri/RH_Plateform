package com.hr.referentiel.service;

import com.hr.referentiel.config.CacheConfig;
import com.hr.referentiel.domain.NiveauSeniorite;
import com.hr.referentiel.domain.ProfilAccesCollaborateur;
import com.hr.referentiel.domain.TypeNoeudOrganisation;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.kafka.CollaborateurCompteDemandeEvent;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.kafka.CollaborateurCompteDemandePublisher;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReferentielRhService {

	private final UniteOrganisationRepository uniteRepository;
	private final CollaborateurRepository collaborateurRepository;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final FamilleMetierService familleMetierService;
	private final ObjectProvider<CollaborateurCompteDemandePublisher> collaborateurCompteDemandePublisher;

	public ReferentielRhService(UniteOrganisationRepository uniteRepository,
			CollaborateurRepository collaborateurRepository,
			CollaborateurConnecteService collaborateurConnecteService,
			FamilleMetierService familleMetierService,
			ObjectProvider<CollaborateurCompteDemandePublisher> collaborateurCompteDemandePublisher) {
		this.uniteRepository = uniteRepository;
		this.collaborateurRepository = collaborateurRepository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.familleMetierService = familleMetierService;
		this.collaborateurCompteDemandePublisher = collaborateurCompteDemandePublisher;
		this.hierarchieTypeRules = new HierarchieTypeRules(uniteRepository);
	}

	private final HierarchieTypeRules hierarchieTypeRules;

	@Cacheable(value = CacheConfig.CACHE_UNITES, key = "'liste_actives'")
	@Transactional(readOnly = true)
	public List<UniteResponse> listerUnitesActives() {
		return uniteRepository.findByActifTrueOrderByCodeAsc().stream()
				.map(this::toUniteResponse)
				.collect(Collectors.toList());
	}

	@Cacheable(value = CacheConfig.CACHE_UNITES, key = "#id")
	@Transactional(readOnly = true)
	public Optional<UniteResponse> obtenirUnite(UUID id) {
		return uniteRepository.findById(id).map(this::toUniteResponse);
	}

	@Transactional
	@CacheEvict(value = CacheConfig.CACHE_UNITES, allEntries = true)
	public UniteResponse creerUnite(UniteCreationRequest req) {
		if (uniteRepository.findByCodeIgnoreCase(req.getCode().trim()).isPresent()) {
			throw new IllegalArgumentException("Ce code d'unité existe déjà : " + req.getCode());
		}
		UniteOrganisation u = new UniteOrganisation();
		u.setCode(req.getCode().trim());
		u.setLibelle(req.getLibelle().trim());
		u.setActif(req.getActif() == null || req.getActif());
		UniteOrganisation parent = null;
		if (req.getParentIdentifiant() != null) {
			parent = uniteRepository.findById(req.getParentIdentifiant())
					.orElseThrow(() -> new IllegalArgumentException("Unité parente introuvable."));
		}
		// D-H04 : sans type → Département à la racine (à rattacher), Service sous un parent
		String saisi = trimToNull(req.getTypeNoeud());
		TypeNoeudOrganisation type = saisi != null ? hierarchieTypeRules.exigerType(saisi)
				: parent == null ? TypeNoeudOrganisation.DEPARTEMENT : TypeNoeudOrganisation.SERVICE;
		u.setParent(hierarchieTypeRules.resoudreParentCreation(type, parent));
		u.setTypeNoeud(type.getLibelle());
		u.setTitrePoste(trimToNull(req.getTitrePoste()));
		return toUniteResponse(uniteRepository.save(u));
	}

	@Transactional
	@CacheEvict(value = CacheConfig.CACHE_UNITES, allEntries = true)
	public Optional<UniteResponse> mettreAJourUnite(UUID id, UniteMiseAJourRequest req) {
		return uniteRepository.findById(id).map(u -> {
			if (req.getLibelle() != null) {
				u.setLibelle(req.getLibelle().trim());
			}
			if (req.getActif() != null) {
				u.setActif(req.getActif());
			}
			if (req.getParentIdentifiant() != null) {
				if (req.getParentIdentifiant().equals(id)) {
					throw new IllegalArgumentException("Une unité ne peut pas être sa propre parente.");
				}
				UniteOrganisation parent = uniteRepository.findById(req.getParentIdentifiant())
						.orElseThrow(() -> new IllegalArgumentException("Unité parente introuvable."));
				hierarchieTypeRules.verifierRattachement(u, parent);
				u.setParent(parent);
			}
			if (req.getTypeNoeud() != null) {
				TypeNoeudOrganisation type = hierarchieTypeRules.exigerType(req.getTypeNoeud());
				hierarchieTypeRules.verifierChangementType(u, type);
				u.setTypeNoeud(type.getLibelle());
			}
			if (req.getTitrePoste() != null) {
				u.setTitrePoste(trimToNull(req.getTitrePoste()));
			}
			return toUniteResponse(uniteRepository.save(u));
		});
	}

	@Transactional(readOnly = true)
	public PageReferentielResponse<CollaborateurResponse> listerCollaborateurs(
			int page, int taille, String statut, UUID uniteId) {
		Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, taille)),
				Sort.by("nom").ascending().and(Sort.by("prenom").ascending()));
		Page<Collaborateur> p;
		boolean hasStatut = statut != null && !statut.isBlank();
		boolean hasUnite = uniteId != null;
		if (hasStatut && hasUnite) {
			p = collaborateurRepository.findByStatutIgnoreCaseAndUniteId(statut.trim(), uniteId, pageable);
		} else if (hasStatut) {
			p = collaborateurRepository.findByStatutIgnoreCase(statut.trim(), pageable);
		} else if (hasUnite) {
			p = collaborateurRepository.findByUniteId(uniteId, pageable);
		} else {
			p = collaborateurRepository.findAll(pageable);
		}
		List<CollaborateurResponse> contenu = p.getContent().stream()
				.map(this::toCollaborateurResponse)
				.collect(Collectors.toList());
		return new PageReferentielResponse<>(contenu, p.getTotalElements(), p.getTotalPages(),
				p.getNumber(), p.getSize());
	}

	// Spring Cache déballe l'Optional : #result est le CollaborateurResponse (ou null si absent),
	// donc « #result.isEmpty() » levait une SpelEvaluationException (403 vide) dès que la fiche existait.
	@Cacheable(value = CacheConfig.CACHE_COLLABORATEUR_ID, key = "#id", unless = "#result == null")
	@Transactional(readOnly = true)
	public Optional<CollaborateurResponse> obtenirCollaborateur(UUID id) {
		return collaborateurRepository.findDetailById(id).map(this::toCollaborateurResponse);
	}

	@Transactional(readOnly = true)
	public CollaborateurResponse obtenirMonCollaborateur(org.springframework.security.oauth2.jwt.Jwt jwt) {
		return toCollaborateurResponse(collaborateurConnecteService.exigerCollaborateur(jwt));
	}

	@Cacheable(value = CacheConfig.CACHE_COLLABORATEUR_MATRICULE, key = "#matricule.toLowerCase()", unless = "#result == null")
	@Transactional(readOnly = true)
	public Optional<CollaborateurResponse> obtenirCollaborateurParMatricule(String matricule) {
		return collaborateurRepository.findDetailByMatricule(matricule.trim())
				.map(this::toCollaborateurResponse);
	}

	@Transactional
	@CacheEvict(value = { CacheConfig.CACHE_COLLABORATEUR_ID, CacheConfig.CACHE_COLLABORATEUR_MATRICULE }, allEntries = true)
	public CollaborateurResponse creerCollaborateur(CollaborateurCreationRequest req) {
		if (collaborateurRepository.existsByMatriculeIgnoreCase(req.getMatricule().trim())) {
			throw new IllegalArgumentException("Matricule déjà utilisé : " + req.getMatricule());
		}
		UniteOrganisation unite = uniteRepository.findById(req.getUniteIdentifiant())
				.orElseThrow(() -> new IllegalArgumentException("Unité introuvable."));
		Collaborateur c = new Collaborateur();
		c.setMatricule(req.getMatricule().trim());
		c.setPrenom(req.getPrenom().trim());
		c.setNom(req.getNom().trim());
		c.setCourrielProfessionnel(req.getCourrielProfessionnel() != null
				? req.getCourrielProfessionnel().trim()
				: null);
		c.setPosteLibelle(trimToNull(req.getPosteLibelle()));
		c.setFonction(trimToNull(req.getFonction()));
		c.setQualificationAffectation(trimToNull(req.getQualificationAffectation()));
		c.setQualite(trimToNull(req.getQualite()));
		c.setAffectation(trimToNull(req.getAffectation()));
		c.setDepartementLibelle(trimToNull(req.getDepartementLibelle()));
		c.setDateRecrutement(req.getDateRecrutement());
		c.setStatut(req.getStatut().trim());
		ProfilAccesCollaborateur profil = parseProfilAcces(req.getProfilAcces());
		c.setProfilAcces(profil.name());
		appliquerProfilMetier(c, req.getFamilleMetierCode(), req.getNiveauSeniorite(), false);
		c.setUnite(unite);
		// H3-T1 / H3-R05 : superieur dérivé du manager du nœud ; saisie manuelle ignorée
		c.setSuperieur(SuperieurHierarchieRules.resoudreSuperieurFiche(c, unite));

		final boolean liaisonManuelleCompte = req.getCompteUtilisateurId() != null;
		if (liaisonManuelleCompte) {
			if (req.getMotDePasseInitial() != null && !req.getMotDePasseInitial().isBlank()) {
				throw new IllegalArgumentException(
						"Ne pas envoyer mot_de_passe_initial lorsque compte_utilisateur_id est renseigné.");
			}
			c.setCompteUtilisateurId(req.getCompteUtilisateurId());
		} else {
			String mdp = req.getMotDePasseInitial();
			if (mdp == null || mdp.length() < 8) {
				throw new IllegalArgumentException(
						"mot_de_passe_initial est obligatoire (minimum 8 caractères) pour la création automatique du compte.");
			}
			c.setCompteUtilisateurId(null);
		}

		Collaborateur saved = collaborateurRepository.save(c);

		if (!liaisonManuelleCompte) {
			CollaborateurCompteDemandeEvent event = new CollaborateurCompteDemandeEvent(
					saved.getId(),
					saved.getMatricule(),
					saved.getCourrielProfessionnel(),
					saved.getPrenom(),
					saved.getNom(),
					profil.name(),
					req.getMotDePasseInitial());
			registerKafkaCompteDemandeAfterCommit(saved.getId().toString(), event);
		}

		return toCollaborateurResponse(saved);
	}

	private void registerKafkaCompteDemandeAfterCommit(String key, CollaborateurCompteDemandeEvent event) {
		CollaborateurCompteDemandePublisher publisher = collaborateurCompteDemandePublisher.getIfAvailable();
		if (publisher == null) {
			throw new IllegalStateException(
					"Kafka n'est pas configuré (spring.kafka.bootstrap-servers) : impossible de demander la création du compte identité.");
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				publisher.publishAsyncAfterCommit(key, event);
			}
		});
	}

	private static ProfilAccesCollaborateur parseProfilAcces(String raw) {
		return ProfilAccesCollaborateur.parse(raw);
	}

	@Transactional
	@CacheEvict(value = { CacheConfig.CACHE_COLLABORATEUR_ID, CacheConfig.CACHE_COLLABORATEUR_MATRICULE }, allEntries = true)
	public Optional<CollaborateurResponse> mettreAJourCollaborateur(UUID id, CollaborateurMiseAJourRequest req) {
		return collaborateurRepository.findById(id).map(c -> {
			String profilAvant = c.getProfilAcces();
			if (req.getPrenom() != null) {
				c.setPrenom(req.getPrenom().trim());
			}
			if (req.getNom() != null) {
				c.setNom(req.getNom().trim());
			}
			if (req.getCourrielProfessionnel() != null) {
				c.setCourrielProfessionnel(req.getCourrielProfessionnel().trim());
			}
			if (req.getPosteLibelle() != null) {
				c.setPosteLibelle(trimToNull(req.getPosteLibelle()));
			}
			if (req.getFonction() != null) {
				c.setFonction(trimToNull(req.getFonction()));
			}
			if (req.getQualificationAffectation() != null) {
				c.setQualificationAffectation(trimToNull(req.getQualificationAffectation()));
			}
			if (req.getQualite() != null) {
				c.setQualite(trimToNull(req.getQualite()));
			}
			if (req.getAffectation() != null) {
				c.setAffectation(trimToNull(req.getAffectation()));
			}
			if (req.getDepartementLibelle() != null) {
				c.setDepartementLibelle(trimToNull(req.getDepartementLibelle()));
			}
			if (req.getDateRecrutement() != null) {
				c.setDateRecrutement(req.getDateRecrutement());
			}
			if (req.getStatut() != null) {
				c.setStatut(req.getStatut().trim());
			}
			if (req.getProfilAcces() != null && !req.getProfilAcces().isBlank()) {
				c.setProfilAcces(parseProfilAcces(req.getProfilAcces()).name());
			}
			if (req.getFamilleMetierCode() != null || req.getNiveauSeniorite() != null) {
				appliquerProfilMetier(c, req.getFamilleMetierCode(), req.getNiveauSeniorite(), true);
			}
			if (req.getUniteIdentifiant() != null) {
				UniteOrganisation unite = uniteRepository.findById(req.getUniteIdentifiant())
						.orElseThrow(() -> new IllegalArgumentException("Unité introuvable."));
				c.setUnite(unite);
			}
			// H3-T2 / H3-R05 : toujours dériver ; ignore superieur_identifiant divergent
			if (c.getUnite() != null) {
				Collaborateur cible = SuperieurHierarchieRules.resoudreSuperieurFiche(c, c.getUnite());
				SuperieurHierarchieRules.appliquerSiChange(c, cible);
			}
			if (req.getCompteUtilisateurId() != null) {
				c.setCompteUtilisateurId(req.getCompteUtilisateurId());
			}
			Collaborateur saved = collaborateurRepository.save(c);
			publierMajRolesSiProfilChange(saved, profilAvant);
			return toCollaborateurResponse(saved);
		});
	}

	/**
	 * Resynchronise les rôles JWT si {@code profil_acces} a réellement changé et qu'un compte
	 * est déjà lié. Fiche sans compte : no-op. Même profil : pas d'événement Kafka.
	 */
	private void publierMajRolesSiProfilChange(Collaborateur saved, String profilAvant) {
		if (saved.getCompteUtilisateurId() == null) {
			return;
		}
		if (Objects.equals(profilAvant, saved.getProfilAcces())) {
			return;
		}
		CollaborateurCompteDemandeEvent event = new CollaborateurCompteDemandeEvent(
				saved.getId(),
				saved.getMatricule(),
				saved.getCourrielProfessionnel(),
				saved.getPrenom(),
				saved.getNom(),
				saved.getProfilAcces(),
				null,
				CollaborateurCompteDemandeEvent.OPERATION_MAJ_ROLES,
				saved.getCompteUtilisateurId());
		registerKafkaCompteDemandeAfterCommit(saved.getId().toString(), event);
	}

	/**
	 * Population ACTIF pour activation campagne M07 (E4) — snapshot matching serveur.
	 */
	@Transactional(readOnly = true)
	public List<CollaborateurEvaluationSnapshotResponse> listerActifsPourEvaluation() {
		return collaborateurRepository.findAllActifsWithUnite().stream()
				.map(c -> new CollaborateurEvaluationSnapshotResponse(
						c.getId(),
						c.getStatut(),
						c.getFamilleMetierCode(),
						c.getNiveauSeniorite(),
						c.getSuperieur() != null ? c.getSuperieur().getId() : null,
						c.getProfilAcces()))
				.toList();
	}

	/**
	 * @param partialUpdate si true, null = ne pas toucher ; blank string = effacer
	 */
	private void appliquerProfilMetier(Collaborateur c, String familleCode, String niveauRaw, boolean partialUpdate) {
		if (familleCode != null) {
			String trimmed = familleCode.trim();
			if (trimmed.isEmpty()) {
				c.setFamilleMetierCode(null);
			} else {
				c.setFamilleMetierCode(familleMetierService.exigerActive(trimmed).getCode());
			}
		} else if (!partialUpdate) {
			c.setFamilleMetierCode(null);
		}

		if (niveauRaw != null) {
			String trimmed = niveauRaw.trim();
			if (trimmed.isEmpty()) {
				c.setNiveauSeniorite(null);
			} else {
				NiveauSeniorite niveau = NiveauSeniorite.parseOptional(trimmed)
						.orElseThrow(() -> ReferentielMetierException.unprocessable(
								NiveauSeniorite.MESSAGE_INVALIDE,
								"Niveau de séniorité invalide : " + trimmed));
				c.setNiveauSeniorite(niveau.name());
			}
		} else if (!partialUpdate) {
			c.setNiveauSeniorite(null);
		}
	}

	private UniteResponse toUniteResponse(UniteOrganisation u) {
		String type = u.getTypeNoeud();
		if (type == null || type.isBlank()) {
			type = u.getParent() == null ? "Département" : "Unité";
		}
		return new UniteResponse(
				u.getId(),
				u.getCode(),
				u.getLibelle(),
				u.getParent() != null ? u.getParent().getId() : null,
				type,
				u.getTitrePoste(),
				u.isActif(),
				u.getCreeLe(),
				u.getModifieLe());
	}

	private CollaborateurResponse toCollaborateurResponse(Collaborateur c) {
		CollaborateurResponse r = new CollaborateurResponse();
		r.setIdentifiant(c.getId());
		r.setMatricule(c.getMatricule());
		r.setPrenom(c.getPrenom());
		r.setNom(c.getNom());
		r.setCourrielProfessionnel(c.getCourrielProfessionnel());
		r.setPosteLibelle(c.getPosteLibelle());
		r.setFonction(c.getFonction());
		r.setQualificationAffectation(c.getQualificationAffectation());
		r.setQualite(c.getQualite());
		r.setAffectation(c.getAffectation());
		r.setDepartementLibelle(c.getDepartementLibelle());
		r.setDateRecrutement(c.getDateRecrutement());
		r.setStatut(c.getStatut());
		r.setUnite(toUniteResponse(c.getUnite()));
		r.setSuperieurIdentifiant(c.getSuperieur() != null ? c.getSuperieur().getId() : null);
		r.setCompteUtilisateurId(c.getCompteUtilisateurId());
		r.setProfilAcces(c.getProfilAcces());
		r.setFamilleMetierCode(c.getFamilleMetierCode());
		r.setFamilleMetierLibelle(familleMetierService.libelleOuNull(c.getFamilleMetierCode()));
		r.setNiveauSeniorite(c.getNiveauSeniorite());
		r.setCreeLe(c.getCreeLe());
		r.setModifieLe(c.getModifieLe());
		return r;
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}
}
