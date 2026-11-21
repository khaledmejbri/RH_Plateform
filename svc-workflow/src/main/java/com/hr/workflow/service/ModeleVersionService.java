package com.hr.workflow.service;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.config.CacheConfig;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionValidateur;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EcheanceDefinition;
import com.hr.workflow.definition.ErreurDefinition;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.domain.ActionJournalModele;
import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.dto.AdminWorkflowDtos.ActionVersionRequest;
import com.hr.workflow.dto.AdminWorkflowDtos.ComparaisonVersionsResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.CreerModeleRequest;
import com.hr.workflow.dto.AdminWorkflowDtos.DiffEtape;
import com.hr.workflow.dto.AdminWorkflowDtos.JournalModeleResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.ModeleDetailResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.ModeleResumeResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.ModifierModeleRequest;
import com.hr.workflow.dto.AdminWorkflowDtos.ModifierVersionRequest;
import com.hr.workflow.dto.AdminWorkflowDtos.PageResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.ValidationResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.VersionDetailResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.VersionResumeResponse;
import com.hr.workflow.entity.WfJournalModele;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.repository.WfJournalModeleRepository;
import com.hr.workflow.repository.WfModeleRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
import com.hr.workflow.web.WorkflowMetierException;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Gestion des modèles, versions et journaux du moteur de workflow (D-W01 à D-W05, D-W3-05, D-W3-06).
 * Note : {@link #publier} n'est PAS {@code @Transactional} afin que les appels HTTP au référentiel
 * (calendrier quatre yeux + marquage des fonctions) s'exécutent strictement hors transaction.
 */
@Service
public class ModeleVersionService {

	private final WfModeleRepository modeleRepository;
	private final WfModeleVersionRepository versionRepository;
	private final WfJournalModeleRepository journalRepository;
	private final CatalogueTypesObjet catalogue;
	private final DefinitionCodec codec;
	private final DefinitionValidateur validateur;
	private final WorkflowReferentielClient referentielClient;
	private final CacheManager cacheManager;
	private final TransactionTemplate txTemplate;
	private final Clock clock;

	public ModeleVersionService(
			WfModeleRepository modeleRepository,
			WfModeleVersionRepository versionRepository,
			WfJournalModeleRepository journalRepository,
			CatalogueTypesObjet catalogue,
			DefinitionCodec codec,
			DefinitionValidateur validateur,
			WorkflowReferentielClient referentielClient,
			CacheManager cacheManager,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.modeleRepository = modeleRepository;
		this.versionRepository = versionRepository;
		this.journalRepository = journalRepository;
		this.catalogue = catalogue;
		this.codec = codec;
		this.validateur = validateur;
		this.referentielClient = referentielClient;
		this.cacheManager = cacheManager;
		this.txTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<ModeleResumeResponse> listerModeles(String objetType, Boolean actif, int page, int taille) {
		int t = Math.min(Math.max(taille, 1), 100);
		Page<WfModele> p = modeleRepository.rechercher(
				objetType != null && !objetType.isBlank() ? objetType.trim() : null,
				actif,
				PageRequest.of(Math.max(page, 0), t));
		List<ModeleResumeResponse> elements = p.getContent().stream().map(this::versResume).toList();
		return new PageResponse<>(elements, p.getNumber(), p.getSize(), p.getTotalElements());
	}

	@Transactional
	public ModeleDetailResponse creerModele(CreerModeleRequest req, UUID auteurCompteId, String auteurNom) {
		if (!catalogue.existe(req.objetType())) {
			throw WorkflowMetierException.badRequest("TYPE_OBJET_INCONNU", "Type d'objet inconnu : " + req.objetType());
		}
		String code = req.code().trim();
		if (modeleRepository.existsByCode(code)) {
			throw WorkflowMetierException.conflict("MODELE_CODE_EXISTANT",
					"Un modèle avec le code '" + code + "' existe déjà.");
		}
		String sousType = req.sousType() != null && !req.sousType().isBlank() ? req.sousType().trim() : null;
		if (modeleRepository.findActifParObjetTypeEtSousType(req.objetType(), sousType).isPresent()) {
			throw WorkflowMetierException.conflict("MODELE_ACTIF_EXISTANT",
					"Un modèle actif existe déjà pour ce type et sous-type.");
		}

		Instant maintenant = clock.instant();
		WfModele modele = new WfModele();
		modele.setCode(code);
		modele.setLibelle(req.libelle().trim());
		modele.setDescription(req.description() != null ? req.description().trim() : null);
		modele.setObjetType(req.objetType());
		modele.setSousType(sousType);
		modele.setActif(true);
		modele.setCreeLe(maintenant);
		modele.setCreeParCompteId(auteurCompteId);
		modele.setModifieLe(maintenant);
		modele.setModifieParCompteId(auteurCompteId);
		modele = modeleRepository.saveAndFlush(modele);

		// Brouillon v1 avec une étape VALIDATION par défaut (D-W16, D-W20)
		DefinitionWorkflow defParDefaut = definitionParDefaut(req.objetType());
		WfModeleVersion v1 = new WfModeleVersion();
		v1.setModele(modele);
		v1.setNumero(1);
		v1.setStatut(StatutVersion.BROUILLON);
		v1.setDefinition(codec.serialiser(defParDefaut));
		v1.setFormatDefinition(1);
		v1.setEmpreinte(codec.calculerEmpreinte(defParDefaut));
		v1.setModifieLe(maintenant);
		v1.setModifieParCompteId(auteurCompteId);
		v1.setModifieParNom(auteurNom);
		v1 = versionRepository.saveAndFlush(v1);

		enregistrerJournal(modele.getId(), v1.getId(), ActionJournalModele.CREATION, null,
				Map.of("code", modele.getCode(), "objet_type", modele.getObjetType(), "version_numero", 1),
				auteurCompteId, auteurNom, maintenant);

		return versDetail(modele, v1.getId());
	}

	@Transactional(readOnly = true)
	public ModeleDetailResponse detailModele(UUID modeleId) {
		WfModele m = modeleRepository.findById(modeleId)
				.orElseThrow(() -> WorkflowMetierException.notFound("MODELE_INTROUVABLE", "Modèle introuvable."));
		UUID brouillonId = versionRepository.findByModeleIdAndStatut(modeleId, StatutVersion.BROUILLON)
				.map(WfModeleVersion::getId).orElse(null);
		return versDetail(m, brouillonId);
	}

	@Transactional
	public ModeleDetailResponse modifierModele(UUID modeleId, ModifierModeleRequest req, UUID auteurCompteId, String auteurNom) {
		WfModele m = modeleRepository.findById(modeleId)
				.orElseThrow(() -> WorkflowMetierException.notFound("MODELE_INTROUVABLE", "Modèle introuvable."));
		if (!m.getVersion().equals(req.version())) {
			throw WorkflowMetierException.conflict("MODIFIE_ENTRE_TEMPS", "Le modèle a été modifié par un autre utilisateur.");
		}
		Map<String, Object> avant = Map.of("libelle", m.getLibelle(),
				"description", m.getDescription() != null ? m.getDescription() : "");
		Instant maintenant = clock.instant();
		m.setLibelle(req.libelle().trim());
		m.setDescription(req.description() != null ? req.description().trim() : null);
		m.setModifieLe(maintenant);
		m.setModifieParCompteId(auteurCompteId);
		m = modeleRepository.saveAndFlush(m);

		enregistrerJournal(m.getId(), null, ActionJournalModele.MODIFICATION, avant,
				Map.of("libelle", m.getLibelle(), "description", m.getDescription() != null ? m.getDescription() : ""),
				auteurCompteId, auteurNom, maintenant);

		UUID brouillonId = versionRepository.findByModeleIdAndStatut(modeleId, StatutVersion.BROUILLON)
				.map(WfModeleVersion::getId).orElse(null);
		return versDetail(m, brouillonId);
	}

	@Transactional
	public ModeleDetailResponse activerModele(UUID modeleId, ActionVersionRequest req, UUID auteurCompteId, String auteurNom) {
		WfModele m = modeleRepository.findById(modeleId)
				.orElseThrow(() -> WorkflowMetierException.notFound("MODELE_INTROUVABLE", "Modèle introuvable."));
		if (!m.getVersion().equals(req.version())) {
			throw WorkflowMetierException.conflict("MODIFIE_ENTRE_TEMPS", "Le modèle a été modifié par un autre utilisateur.");
		}
		Optional<WfModele> existant = modeleRepository.findActifParObjetTypeEtSousType(m.getObjetType(), m.getSousType());
		if (existant.isPresent() && !existant.get().getId().equals(m.getId())) {
			throw WorkflowMetierException.conflict("MODELE_ACTIF_EXISTANT",
					"Un autre modèle actif existe déjà pour ce type et sous-type.");
		}
		Instant maintenant = clock.instant();
		m.setActif(true);
		m.setModifieLe(maintenant);
		m.setModifieParCompteId(auteurCompteId);
		m = modeleRepository.saveAndFlush(m);

		enregistrerJournal(m.getId(), null, ActionJournalModele.ACTIVATION, Map.of("actif", false), Map.of("actif", true),
				auteurCompteId, auteurNom, maintenant);
		return detailModele(m.getId());
	}

	@Transactional
	public ModeleDetailResponse desactiverModele(UUID modeleId, ActionVersionRequest req, UUID auteurCompteId, String auteurNom) {
		WfModele m = modeleRepository.findById(modeleId)
				.orElseThrow(() -> WorkflowMetierException.notFound("MODELE_INTROUVABLE", "Modèle introuvable."));
		if (!m.getVersion().equals(req.version())) {
			throw WorkflowMetierException.conflict("MODIFIE_ENTRE_TEMPS", "Le modèle a été modifié par un autre utilisateur.");
		}
		Instant maintenant = clock.instant();
		m.setActif(false);
		m.setModifieLe(maintenant);
		m.setModifieParCompteId(auteurCompteId);
		m = modeleRepository.saveAndFlush(m);

		enregistrerJournal(m.getId(), null, ActionJournalModele.DESACTIVATION, Map.of("actif", true), Map.of("actif", false),
				auteurCompteId, auteurNom, maintenant);
		return detailModele(m.getId());
	}

	@Transactional
	public void supprimerModele(UUID modeleId) {
		WfModele m = modeleRepository.findById(modeleId)
				.orElseThrow(() -> WorkflowMetierException.notFound("MODELE_INTROUVABLE", "Modèle introuvable."));
		if (versionRepository.existsByModeleIdAndStatutIn(modeleId, List.of(StatutVersion.PUBLIEE, StatutVersion.RETIREE))) {
			throw WorkflowMetierException.conflict("MODELE_DEJA_PUBLIE",
					"Un modèle déjà publié ne peut pas être supprimé ; désactivez-le.");
		}
		List<WfModeleVersion> versions = versionRepository.findByModeleIdOrderByNumeroDesc(modeleId);
		versionRepository.deleteAll(versions);
		modeleRepository.delete(m);
	}

	@Transactional(readOnly = true)
	public List<VersionResumeResponse> listerVersions(UUID modeleId) {
		if (!modeleRepository.existsById(modeleId)) {
			throw WorkflowMetierException.notFound("MODELE_INTROUVABLE", "Modèle introuvable.");
		}
		return versionRepository.findByModeleIdOrderByNumeroDesc(modeleId).stream()
				.map(v -> new VersionResumeResponse(
						v.getId(), v.getNumero(), v.getStatut(), v.getEmpreinte(),
						v.getModifieLe(), v.getModifieParNom(), v.getPublieLe(), v.getRetireLe(), v.getVersion()))
				.toList();
	}

	@Transactional
	public VersionDetailResponse creerBrouillon(UUID modeleId, UUID auteurCompteId, String auteurNom) {
		WfModele m = modeleRepository.findById(modeleId)
				.orElseThrow(() -> WorkflowMetierException.notFound("MODELE_INTROUVABLE", "Modèle introuvable."));
		if (versionRepository.existsByModeleIdAndStatut(modeleId, StatutVersion.BROUILLON)) {
			throw WorkflowMetierException.conflict("BROUILLON_EXISTANT",
					"Un brouillon existe déjà pour ce modèle.");
		}
		WfModeleVersion derniere = versionRepository.findTopByModeleIdOrderByNumeroDesc(modeleId).orElse(null);
		int numero = derniere != null ? derniere.getNumero() + 1 : 1;
		String defJson = derniere != null ? derniere.getDefinition() : codec.serialiser(definitionParDefaut(m.getObjetType()));
		DefinitionWorkflow def = codec.deserialiser(defJson);

		Instant maintenant = clock.instant();
		WfModeleVersion brouillon = new WfModeleVersion();
		brouillon.setModele(m);
		brouillon.setNumero(numero);
		brouillon.setStatut(StatutVersion.BROUILLON);
		brouillon.setDefinition(defJson);
		brouillon.setFormatDefinition(1);
		brouillon.setEmpreinte(codec.calculerEmpreinte(def));
		brouillon.setModifieLe(maintenant);
		brouillon.setModifieParCompteId(auteurCompteId);
		brouillon.setModifieParNom(auteurNom);
		brouillon = versionRepository.saveAndFlush(brouillon);

		return versDetailVersion(brouillon, def, validateur.valider(def, m.getObjetType()));
	}

	@Transactional(readOnly = true)
	public VersionDetailResponse detailVersion(UUID versionId) {
		WfModeleVersion v = versionRepository.findByIdAvecModele(versionId)
				.orElseThrow(() -> WorkflowMetierException.notFound("VERSION_INTROUVABLE", "Version introuvable."));
		DefinitionWorkflow def = codec.deserialiser(v.getDefinition());
		return versDetailVersion(v, def, null);
	}

	/** Charge la définition d'une version publiée depuis le cache Caffeine (clé = {@code versionId}, D-W3-01). */
	@Transactional(readOnly = true)
	public DefinitionWorkflow chargerDefinition(UUID versionId) {
		Cache cache = cacheManager != null ? cacheManager.getCache(CacheConfig.CACHE_VERSIONS_PUBLIEES) : null;
		if (cache != null) {
			DefinitionWorkflow cached = cache.get(versionId, DefinitionWorkflow.class);
			if (cached != null) {
				return cached;
			}
		}
		WfModeleVersion v = versionRepository.findById(versionId)
				.orElseThrow(() -> WorkflowMetierException.notFound("VERSION_INTROUVABLE", "Version introuvable."));
		DefinitionWorkflow def = codec.deserialiser(v.getDefinition());
		if (cache != null && v.getStatut() == StatutVersion.PUBLIEE) {
			cache.put(versionId, def);
		}
		return def;
	}

	@Transactional
	public VersionDetailResponse modifierBrouillon(
			UUID versionId,
			ModifierVersionRequest req,
			UUID auteurCompteId,
			String auteurNom) {
		WfModeleVersion v = versionRepository.findByIdAvecModele(versionId)
				.orElseThrow(() -> WorkflowMetierException.notFound("VERSION_INTROUVABLE", "Version introuvable."));
		if (v.getStatut() != StatutVersion.BROUILLON) {
			throw WorkflowMetierException.conflict("VERSION_NON_BROUILLON",
					"Seul un brouillon peut être modifié.");
		}
		if (!v.getVersion().equals(req.version())) {
			throw WorkflowMetierException.conflict("MODIFIE_ENTRE_TEMPS",
					"Ce brouillon a été modifié par un autre utilisateur.");
		}

		String rawJson = req.definition().toString();
		DefinitionWorkflow def = codec.deserialiser(rawJson);
		List<ErreurDefinition> erreurs = validateur.valider(def, v.getModele().getObjetType());

		Instant maintenant = clock.instant();
		String empreinteAvant = v.getEmpreinte();
		String canonique = codec.serialiser(def);
		String empreinteApres = codec.calculerEmpreinte(def);

		v.setDefinition(canonique);
		v.setEmpreinte(empreinteApres);
		v.setModifieLe(maintenant);
		v.setModifieParCompteId(auteurCompteId);
		v.setModifieParNom(auteurNom);
		v = versionRepository.saveAndFlush(v);

		enregistrerJournal(v.getModele().getId(), v.getId(), ActionJournalModele.MODIFICATION,
				Map.of("empreinte", empreinteAvant), Map.of("empreinte", empreinteApres),
				auteurCompteId, auteurNom, maintenant);

		// Un brouillon invalide s'enregistre avec ses erreurs (§ 9.7.2)
		return versDetailVersion(v, def, erreurs);
	}

	@Transactional(readOnly = true)
	public ValidationResponse validerVersion(UUID versionId) {
		WfModeleVersion v = versionRepository.findByIdAvecModele(versionId)
				.orElseThrow(() -> WorkflowMetierException.notFound("VERSION_INTROUVABLE", "Version introuvable."));
		DefinitionWorkflow def = codec.deserialiser(v.getDefinition());
		List<ErreurDefinition> erreurs = validateur.valider(def, v.getModele().getObjetType());
		return new ValidationResponse(validateur.estValide(erreurs), erreurs);
	}

	/**
	 * Publication d'un brouillon valide (D-W04, D-W3-05, D-W3-06).
	 * <strong>Hors transaction</strong> pour les appels HTTP au référentiel (calendrier quatre yeux et fonctions utilisées),
	 * puis transaction courte pour la publication atomique.
	 */
	public VersionDetailResponse publier(UUID versionId, ActionVersionRequest req, UUID auteurCompteId, String auteurNom) {
		// 1. Lecture initiale + validation statique (hors appel HTTP)
		record PreparationPublication(UUID modeleId, String objetType, DefinitionWorkflow def,
				UUID modifieParCompteId, Long versionOptimiste) {
		}
		PreparationPublication prep = txTemplate.execute(status -> {
			WfModeleVersion v = versionRepository.findByIdAvecModele(versionId)
					.orElseThrow(() -> WorkflowMetierException.notFound("VERSION_INTROUVABLE", "Version introuvable."));
			if (v.getStatut() != StatutVersion.BROUILLON) {
				throw WorkflowMetierException.conflict("VERSION_NON_BROUILLON",
						"Seul un brouillon peut être publié.");
			}
			if (!v.getVersion().equals(req.version())) {
				throw WorkflowMetierException.conflict("MODIFIE_ENTRE_TEMPS",
						"Ce brouillon a été modifié par un autre utilisateur.");
			}
			DefinitionWorkflow def = codec.deserialiser(v.getDefinition());
			List<ErreurDefinition> erreurs = validateur.valider(def, v.getModele().getObjetType());
			if (!validateur.estValide(erreurs)) {
				throw WorkflowMetierException.definitionInvalide(erreurs);
			}
			return new PreparationPublication(
					v.getModele().getId(), v.getModele().getObjetType(), def, v.getModifieParCompteId(), v.getVersion());
		});

		// 2. Hors transaction : contrôle quatre yeux (D-W3-06) via le calendrier société
		LocalDate aujourdhui = LocalDate.now(clock);
		CalendrierSocieteResponse cal = referentielClient.calendrierSociete(aujourdhui, aujourdhui.plusDays(1));
		if (cal.publicationQuatreYeux()
				&& prep.modifieParCompteId() != null
				&& prep.modifieParCompteId().equals(auteurCompteId)) {
			throw WorkflowMetierException.forbidden("PUBLICATION_QUATRE_YEUX",
					"Le principe des quatre yeux impose qu'un autre utilisateur RH ou ADMIN publie ce brouillon.");
		}

		// 3. Hors transaction : marquer chaque fonction utilisée auprès du référentiel (D-W3-05)
		Set<String> fonctions = validateur.extraireCodesFonctions(prep.def());
		for (String codeFonction : fonctions) {
			referentielClient.marquerFonctionUtilisee(codeFonction);
		}

		// 4. Transaction courte : retrait de l'ancienne publiée, publication de la nouvelle, journal
		VersionDetailResponse resultat = txTemplate.execute(status -> {
			WfModeleVersion v = versionRepository.findByIdAvecModele(versionId)
					.orElseThrow(() -> WorkflowMetierException.notFound("VERSION_INTROUVABLE", "Version introuvable."));
			if (v.getStatut() != StatutVersion.BROUILLON) {
				throw WorkflowMetierException.conflict("VERSION_NON_BROUILLON", "Seul un brouillon peut être publié.");
			}
			if (!v.getVersion().equals(req.version())) {
				throw WorkflowMetierException.conflict("MODIFIE_ENTRE_TEMPS",
						"Ce brouillon a été modifié par un autre utilisateur.");
			}
			Instant maintenant = clock.instant();
			UUID modeleId = v.getModele().getId();
			versionRepository.findByModeleIdAndStatut(modeleId, StatutVersion.PUBLIEE)
					.ifPresent(ancienne -> {
						ancienne.setStatut(StatutVersion.RETIREE);
						ancienne.setRetireLe(maintenant);
						versionRepository.saveAndFlush(ancienne);
						enregistrerJournal(modeleId, ancienne.getId(), ActionJournalModele.RETRAIT,
								Map.of("numero", ancienne.getNumero(), "statut", "PUBLIEE"),
								Map.of("numero", ancienne.getNumero(), "statut", "RETIREE"),
								auteurCompteId, auteurNom, maintenant);
					});

			v.setStatut(StatutVersion.PUBLIEE);
			v.setPublieLe(maintenant);
			v.setPublieParCompteId(auteurCompteId);
			v = versionRepository.saveAndFlush(v);

			enregistrerJournal(v.getModele().getId(), v.getId(), ActionJournalModele.PUBLICATION,
					Map.of("numero", v.getNumero(), "statut", "BROUILLON"),
					Map.of("numero", v.getNumero(), "statut", "PUBLIEE", "empreinte", v.getEmpreinte()),
					auteurCompteId, auteurNom, maintenant);

			return versDetailVersion(v, prep.def(), List.of());
		});

		return resultat;
	}

	@Transactional
	public void supprimerBrouillon(UUID versionId, UUID auteurCompteId, String auteurNom) {
		WfModeleVersion v = versionRepository.findByIdAvecModele(versionId)
				.orElseThrow(() -> WorkflowMetierException.notFound("VERSION_INTROUVABLE", "Version introuvable."));
		if (v.getStatut() != StatutVersion.BROUILLON) {
			throw WorkflowMetierException.conflict("VERSION_NON_BROUILLON",
					"Seul un brouillon peut être supprimé.");
		}
		UUID modeleId = v.getModele().getId();
		int numero = v.getNumero();
		versionRepository.delete(v);
		enregistrerJournal(modeleId, versionId, ActionJournalModele.SUPPRESSION_BROUILLON,
				Map.of("numero", numero, "statut", "BROUILLON"), null, auteurCompteId, auteurNom, clock.instant());
	}

	@Transactional(readOnly = true)
	public ComparaisonVersionsResponse comparerVersions(UUID vid, UUID autreVid) {
		WfModeleVersion v1 = versionRepository.findById(vid)
				.orElseThrow(() -> WorkflowMetierException.notFound("VERSION_INTROUVABLE", "Version source introuvable."));
		WfModeleVersion v2 = versionRepository.findById(autreVid)
				.orElseThrow(() -> WorkflowMetierException.notFound("VERSION_INTROUVABLE", "Version cible introuvable."));

		DefinitionWorkflow d1 = codec.deserialiser(v1.getDefinition());
		DefinitionWorkflow d2 = codec.deserialiser(v2.getDefinition());

		Map<String, EtapeDefinition> m1 = new LinkedHashMap<>();
		if (d1.etapes() != null) {
			d1.etapes().forEach(e -> m1.put(e.code(), e));
		}
		Map<String, EtapeDefinition> m2 = new LinkedHashMap<>();
		if (d2.etapes() != null) {
			d2.etapes().forEach(e -> m2.put(e.code(), e));
		}

		List<DiffEtape> diffs = new ArrayList<>();
		for (Map.Entry<String, EtapeDefinition> entry : m2.entrySet()) {
			String code = entry.getKey();
			EtapeDefinition e2 = entry.getValue();
			EtapeDefinition e1 = m1.get(code);
			if (e1 == null) {
				diffs.add(new DiffEtape(code, "AJOUTEE", List.of()));
			} else if (!e1.equals(e2)) {
				diffs.add(new DiffEtape(code, "MODIFIEE", champsModifies(e1, e2)));
			}
		}
		for (String code : m1.keySet()) {
			if (!m2.containsKey(code)) {
				diffs.add(new DiffEtape(code, "RETIREE", List.of()));
			}
		}
		return new ComparaisonVersionsResponse(vid, autreVid, diffs);
	}

	@Transactional(readOnly = true)
	public PageResponse<JournalModeleResponse> listerJournal(UUID modeleId, int page, int taille) {
		if (!modeleRepository.existsById(modeleId)) {
			throw WorkflowMetierException.notFound("MODELE_INTROUVABLE", "Modèle introuvable.");
		}
		int t = Math.min(Math.max(taille, 1), 100);
		Page<WfJournalModele> p = journalRepository.findByModeleIdOrderByHorodatageDesc(
				modeleId, PageRequest.of(Math.max(page, 0), t));
		List<JournalModeleResponse> elements = p.getContent().stream()
				.map(j -> new JournalModeleResponse(j.getId(), j.getModeleId(), j.getVersionId(),
						j.getAction(), j.getAvant(), j.getApres(), j.getAuteurNom(), j.getHorodatage()))
				.toList();
		return new PageResponse<>(elements, p.getNumber(), p.getSize(), p.getTotalElements());
	}

	private static List<String> champsModifies(EtapeDefinition e1, EtapeDefinition e2) {
		List<String> champs = new ArrayList<>();
		if (!Objects.equals(e1.libelle(), e2.libelle())) champs.add("libelle");
		if (!Objects.equals(e1.type(), e2.type())) champs.add("type");
		if (!Objects.equals(e1.condition(), e2.condition())) champs.add("condition");
		if (!Objects.equals(e1.intervenants(), e2.intervenants())) champs.add("intervenants");
		if (!Objects.equals(e1.repliSiVide(), e2.repliSiVide())) champs.add("repli_si_vide");
		if (!Objects.equals(e1.taches(), e2.taches())) champs.add("taches");
		if (!Objects.equals(e1.publicDestinataires(), e2.publicDestinataires())) champs.add("public");
		if (!Objects.equals(e1.actions(), e2.actions())) champs.add("actions");
		if (!Objects.equals(e1.controleAvantApprobation(), e2.controleAvantApprobation())) champs.add("controle_avant_approbation");
		if (!Objects.equals(e1.commentaireObligatoireSi(), e2.commentaireObligatoireSi())) champs.add("commentaire_obligatoire_si");
		if (!Objects.equals(e1.delai(), e2.delai())) champs.add("delai");
		if (!Objects.equals(e1.relances(), e2.relances())) champs.add("relances");
		if (!Objects.equals(e1.aEcheance(), e2.aEcheance())) champs.add("a_echeance");
		if (!Objects.equals(e1.siApprouve(), e2.siApprouve())) champs.add("si_approuve");
		if (!Objects.equals(e1.siRefuse(), e2.siRefuse())) champs.add("si_refuse");
		if (!Objects.equals(e1.notifications(), e2.notifications())) champs.add("notifications");
		return champs;
	}

	private DefinitionWorkflow definitionParDefaut(String objetType) {
		EtapeDefinition etapeDefaut = new EtapeDefinition(
				"VALIDATION_RH",
				"Validation RH",
				"VALIDATION",
				null,
				new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH", "ADMIN")), "UN_SEUL"),
				new EtapeDefinition.RepliSiVideDefinition("ROLE", List.of("RH", "ADMIN")),
				null,
				null,
				List.of("APPROUVER", "REFUSER"),
				false,
				null,
				new DelaiDefinition(2, "JOURS_OUVRES", null),
				new EtapeDefinition.RelancesDefinition(1, null, null),
				new EcheanceDefinition("ESCALADER",
						new EcheanceDefinition.ApresEcheance(1, "JOURS_OUVRES"),
						new IntervenantsDefinition("ROLE", Map.of("roles", List.of("RH", "ADMIN")), null)),
				null,
				new EtapeDefinition.TransitionDefinition("FIN_REFUS"),
				null
		);
		return new DefinitionWorkflow(1, objetType, new DefinitionWorkflow.OptionsWorkflow(false), List.of(etapeDefaut));
	}

	private ModeleResumeResponse versResume(WfModele m) {
		Integer pubNum = versionRepository.findByModeleIdAndStatut(m.getId(), StatutVersion.PUBLIEE)
				.map(WfModeleVersion::getNumero).orElse(null);
		Integer brouillonNum = versionRepository.findByModeleIdAndStatut(m.getId(), StatutVersion.BROUILLON)
				.map(WfModeleVersion::getNumero).orElse(null);
		return new ModeleResumeResponse(
				m.getId(), m.getCode(), m.getLibelle(), m.getDescription(), m.getObjetType(),
				m.getSousType(), m.isActif(), pubNum, brouillonNum, m.getModifieLe(), m.getVersion());
	}

	private ModeleDetailResponse versDetail(WfModele m, UUID brouillonId) {
		Integer pubNum = versionRepository.findByModeleIdAndStatut(m.getId(), StatutVersion.PUBLIEE)
				.map(WfModeleVersion::getNumero).orElse(null);
		Integer brouillonNum = versionRepository.findByModeleIdAndStatut(m.getId(), StatutVersion.BROUILLON)
				.map(WfModeleVersion::getNumero).orElse(null);
		return new ModeleDetailResponse(
				m.getId(), m.getCode(), m.getLibelle(), m.getDescription(), m.getObjetType(),
				m.getSousType(), m.isActif(), pubNum, brouillonNum, m.getCreeLe(), m.getModifieLe(),
				m.getVersion(), brouillonId);
	}

	private VersionDetailResponse versDetailVersion(WfModeleVersion v, DefinitionWorkflow def, List<ErreurDefinition> erreurs) {
		return new VersionDetailResponse(
				v.getId(), v.getModele().getId(), v.getNumero(), v.getStatut(), def,
				v.getEmpreinte(), v.getVersion(), v.getModifieLe(), v.getModifieParNom(), v.getPublieLe(), erreurs);
	}

	private void enregistrerJournal(
			UUID modeleId,
			UUID versionId,
			ActionJournalModele action,
			Map<String, Object> avant,
			Map<String, Object> apres,
			UUID auteurCompteId,
			String auteurNom,
			Instant horodatage) {
		WfJournalModele j = new WfJournalModele();
		j.setModeleId(modeleId);
		j.setVersionId(versionId);
		j.setAction(action);
		j.setAvant(avant);
		j.setApres(apres);
		j.setAuteurCompteId(auteurCompteId);
		j.setAuteurNom(auteurNom);
		j.setHorodatage(horodatage);
		journalRepository.save(j);
	}
}
