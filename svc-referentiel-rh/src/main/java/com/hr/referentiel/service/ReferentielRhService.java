package com.hr.referentiel.service;

import com.hr.referentiel.config.CacheConfig;
import com.hr.referentiel.domain.ProfilAccesCollaborateur;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.kafka.CollaborateurCompteDemandeEvent;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.kafka.CollaborateurCompteDemandePublisher;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReferentielRhService {

	private final UniteOrganisationRepository uniteRepository;
	private final CollaborateurRepository collaborateurRepository;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final ObjectProvider<CollaborateurCompteDemandePublisher> collaborateurCompteDemandePublisher;

	public ReferentielRhService(UniteOrganisationRepository uniteRepository,
			CollaborateurRepository collaborateurRepository,
			CollaborateurConnecteService collaborateurConnecteService,
			ObjectProvider<CollaborateurCompteDemandePublisher> collaborateurCompteDemandePublisher) {
		this.uniteRepository = uniteRepository;
		this.collaborateurRepository = collaborateurRepository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.collaborateurCompteDemandePublisher = collaborateurCompteDemandePublisher;
	}

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
		if (req.getParentIdentifiant() != null) {
			UniteOrganisation parent = uniteRepository.findById(req.getParentIdentifiant())
					.orElseThrow(() -> new IllegalArgumentException("Unité parente introuvable."));
			u.setParent(parent);
		}
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
				u.setParent(parent);
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

	@Cacheable(value = CacheConfig.CACHE_COLLABORATEUR_ID, key = "#id", unless = "#result.isEmpty()")
	@Transactional(readOnly = true)
	public Optional<CollaborateurResponse> obtenirCollaborateur(UUID id) {
		return collaborateurRepository.findDetailById(id).map(this::toCollaborateurResponse);
	}

	@Transactional(readOnly = true)
	public CollaborateurResponse obtenirMonCollaborateur(org.springframework.security.oauth2.jwt.Jwt jwt) {
		return toCollaborateurResponse(collaborateurConnecteService.exigerCollaborateur(jwt));
	}

	@Cacheable(value = CacheConfig.CACHE_COLLABORATEUR_MATRICULE, key = "#matricule.toLowerCase()", unless = "#result.isEmpty()")
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
		c.setUnite(unite);
		if (req.getSuperieurIdentifiant() != null) {
			Collaborateur sup = collaborateurRepository.findById(req.getSuperieurIdentifiant())
					.orElseThrow(() -> new IllegalArgumentException("Supérieur hiérarchique introuvable."));
			c.setSuperieur(sup);
		}

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
			ProfilAccesCollaborateur profil = parseProfilAcces(req.getProfilAcces());
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
		if (raw == null || raw.isBlank()) {
			return ProfilAccesCollaborateur.COLLABORATEUR;
		}
		String key = raw.trim().toUpperCase(Locale.ROOT);
		if ("RO".equals(key)) {
			key = "RESPONSABLE";
		}
		try {
			return ProfilAccesCollaborateur.valueOf(key);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("profil_acces invalide : COLLABORATEUR, RESPONSABLE ou RO.");
		}
	}

	@Transactional
	@CacheEvict(value = { CacheConfig.CACHE_COLLABORATEUR_ID, CacheConfig.CACHE_COLLABORATEUR_MATRICULE }, allEntries = true)
	public Optional<CollaborateurResponse> mettreAJourCollaborateur(UUID id, CollaborateurMiseAJourRequest req) {
		return collaborateurRepository.findById(id).map(c -> {
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
			if (req.getUniteIdentifiant() != null) {
				UniteOrganisation unite = uniteRepository.findById(req.getUniteIdentifiant())
						.orElseThrow(() -> new IllegalArgumentException("Unité introuvable."));
				c.setUnite(unite);
			}
			if (req.getSuperieurIdentifiant() != null) {
				if (req.getSuperieurIdentifiant().equals(id)) {
					throw new IllegalArgumentException("Un collaborateur ne peut pas être son propre supérieur.");
				}
				Collaborateur sup = collaborateurRepository.findById(req.getSuperieurIdentifiant())
						.orElseThrow(() -> new IllegalArgumentException("Supérieur hiérarchique introuvable."));
				c.setSuperieur(sup);
			}
			if (req.getCompteUtilisateurId() != null) {
				c.setCompteUtilisateurId(req.getCompteUtilisateurId());
			}
			return toCollaborateurResponse(collaborateurRepository.save(c));
		});
	}

	private UniteResponse toUniteResponse(UniteOrganisation u) {
		return new UniteResponse(
				u.getId(),
				u.getCode(),
				u.getLibelle(),
				u.getParent() != null ? u.getParent().getId() : null,
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
