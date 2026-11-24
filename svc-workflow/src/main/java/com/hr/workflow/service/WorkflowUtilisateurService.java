package com.hr.workflow.service;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeVariable;
import com.hr.workflow.catalogue.CatalogueTypesObjet.VariableCatalogue;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CollaborateurResume;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EvaluateurCondition;
import com.hr.workflow.domain.ModeActivation;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.StatutEtapeExecution;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.dto.AdminWorkflowDtos.PageResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.CompteurTachesResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.EtapeCouranteDto;
import com.hr.workflow.dto.WorkflowMoteurDtos.EtapeSuiviDto;
import com.hr.workflow.dto.WorkflowMoteurDtos.SuiviInstanceResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.TacheDetailResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.TacheListeItemResponse;
import com.hr.workflow.dto.WorkflowMoteurDtos.TypeOuvertResponse;
import com.hr.workflow.entity.WfActivation;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.repository.WfActivationRepository;
import com.hr.workflow.repository.WfBeneficiaireRepository;
import com.hr.workflow.repository.WfEtapeExecutionRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
import com.hr.workflow.repository.WfTacheCandidatRepository;
import com.hr.workflow.repository.WfTacheRepository;
import com.hr.workflow.web.WorkflowMetierException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lecture des types ouverts, « Mes tâches » (pagination en base), détail de tâche et suivi d'instance (§ 9.7.3).
 * Applique la visibilité § 9.10 (404 si invisible) et le masquage des noms de pool au demandeur (D-W26, CA-08.2).
 */
@Service
public class WorkflowUtilisateurService {

	private final CatalogueTypesObjet catalogue;
	private final WfActivationRepository activationRepository;
	private final WfModeleVersionRepository versionRepository;
	private final WfTacheRepository tacheRepository;
	private final WfTacheCandidatRepository candidatRepository;
	private final WfInstanceRepository instanceRepository;
	private final WfEtapeExecutionRepository etapeExecutionRepository;
	private final WfBeneficiaireRepository beneficiaireRepository;
	private final WorkflowReferentielClient referentielClient;
	private final ModeleVersionService modeleVersionService;
	private final DefinitionCodec codec;
	private final EvaluateurCondition evaluateur = new EvaluateurCondition();
	private final TransactionTemplate txReadOnly;
	private final Clock clock;

	public WorkflowUtilisateurService(
			CatalogueTypesObjet catalogue,
			WfActivationRepository activationRepository,
			WfModeleVersionRepository versionRepository,
			WfTacheRepository tacheRepository,
			WfTacheCandidatRepository candidatRepository,
			WfInstanceRepository instanceRepository,
			WfEtapeExecutionRepository etapeExecutionRepository,
			WfBeneficiaireRepository beneficiaireRepository,
			WorkflowReferentielClient referentielClient,
			ModeleVersionService modeleVersionService,
			DefinitionCodec codec,
			PlatformTransactionManager transactionManager,
			Clock clock) {
		this.catalogue = catalogue;
		this.activationRepository = activationRepository;
		this.versionRepository = versionRepository;
		this.tacheRepository = tacheRepository;
		this.candidatRepository = candidatRepository;
		this.instanceRepository = instanceRepository;
		this.etapeExecutionRepository = etapeExecutionRepository;
		this.beneficiaireRepository = beneficiaireRepository;
		this.referentielClient = referentielClient;
		this.modeleVersionService = modeleVersionService;
		this.codec = codec;
		this.txReadOnly = new TransactionTemplate(transactionManager);
		this.txReadOnly.setReadOnly(true);
		this.clock = clock;
	}

	private DefinitionWorkflow chargerDefinition(WfModeleVersion version) {
		if (modeleVersionService != null) {
			return modeleVersionService.chargerDefinition(version.getId());
		}
		return codec.deserialiser(version.getDefinition());
	}

	@Transactional(readOnly = true)
	public List<TypeOuvertResponse> typesOuverts() {
		Map<String, ModeActivation> modes = activationRepository.findAll().stream()
				.collect(Collectors.toMap(WfActivation::getObjetType, WfActivation::getMode, (a, b) -> a));
		List<WfModeleVersion> publiees = versionRepository.findAllPublieesActives();
		Map<String, List<WfModele>> modelesParType = publiees.stream()
				.map(WfModeleVersion::getModele)
				.collect(Collectors.groupingBy(WfModele::getObjetType));

		List<TypeOuvertResponse> out = new ArrayList<>();
		for (TypeObjetCatalogue type : catalogue.listerTypes()) {
			if (type.estEvenementiel()) {
				continue;
			}
			ModeActivation mode = modes.getOrDefault(type.objetType(), ModeActivation.HERITE);
			if (mode == ModeActivation.HERITE) {
				out.add(new TypeOuvertResponse(type.objetType(), type.libelle(), "TOUS"));
				continue;
			}
			List<WfModele> actifs = modelesParType.getOrDefault(type.objetType(), List.of());
			if (actifs.isEmpty()) {
				// D-W03 : type MOTEUR sans modèle actif publié -> non proposé à la saisie
				continue;
			}
			boolean aGenerique = actifs.stream().anyMatch(m -> m.getSousType() == null || m.getSousType().isBlank());
			if (aGenerique) {
				out.add(new TypeOuvertResponse(type.objetType(), type.libelle(), "TOUS"));
			} else {
				List<String> sousTypes = actifs.stream()
						.map(WfModele::getSousType)
						.filter(s -> s != null && !s.isBlank())
						.distinct()
						.sorted()
						.toList();
				out.add(new TypeOuvertResponse(type.objetType(), type.libelle(), sousTypes));
			}
		}
		return out;
	}

	@Transactional(readOnly = true)
	public PageResponse<TacheListeItemResponse> mesTaches(UUID compteId, String statut, int page, int taille) {
		int p = Math.max(page, 0);
		int t = Math.min(Math.max(taille, 1), 100);
		Instant maintenant = clock.instant();

		Page<WfTache> resultat;
		if ("TRAITEE".equalsIgnoreCase(statut)) {
			Instant depuis = maintenant.minus(90, ChronoUnit.DAYS);
			resultat = tacheRepository.findTraiteesPourCompte(compteId, depuis, PageRequest.of(p, t));
		} else {
			resultat = tacheRepository.findATraiterPourCompte(compteId, PageRequest.of(p, t));
		}

		List<UUID> tacheIds = resultat.getContent().stream().map(WfTache::getId).toList();
		Map<UUID, WfTacheCandidat> candParTache = tacheIds.isEmpty() ? Map.of()
				: candidatRepository.findByIdTacheIdIn(tacheIds).stream()
						.filter(c -> compteId.equals(c.getCompteId()) && c.getRetireLe() == null)
						.collect(Collectors.toMap(c -> c.getId().getTacheId(), c -> c, (a, b) -> a));

		List<TacheListeItemResponse> items = resultat.getContent().stream().map(tache -> {
			WfInstance inst = tache.getInstance();
			WfTacheCandidat cand = candParTache.get(tache.getId());
			String objetLibelle = catalogue.findByObjetType(inst.getObjetType())
					.map(TypeObjetCatalogue::libelle).orElse(inst.getObjetType());
			boolean enRetard = tache.getStatut() == StatutTache.A_TRAITER
					&& tache.getEcheanceLe() != null
					&& tache.getEcheanceLe().isBefore(maintenant);
			List<String> actions = actionsPermises(tache, inst);
			return new TacheListeItemResponse(
					tache.getId(), inst.getId(), tache.getType(), tache.getLibelle(),
					inst.getObjetType(), objetLibelle, inst.getObjetNumero(), inst.getObjetId(),
					inst.getDemandeurNom(), tache.getEcheanceLe(), enRetard,
					cand != null ? cand.getOrigine() : OrigineCandidat.TITULAIRE,
					cand != null && cand.getPourNom() != null ? cand.getPourNom() : tache.getPourNom(),
					actions, tache.getVersion(), extraireResume(inst.getVariables()));
		}).toList();

		return new PageResponse<>(items, resultat.getNumber(), resultat.getSize(), resultat.getTotalElements());
	}

	@Transactional(readOnly = true)
	public CompteurTachesResponse compteurMesTaches(UUID compteId) {
		long aTraiter = tacheRepository.countATraiterPourCompte(compteId);
		long enRetard = tacheRepository.countEnRetardPourCompte(compteId, clock.instant());
		return new CompteurTachesResponse(aTraiter, enRetard);
	}

	public TacheDetailResponse detailTache(UUID tacheId, UUID appelantCompteId, boolean estBackofficeLecture, boolean voirNomsPool) {
		record PreCheck(UUID instanceId, boolean visible, boolean verifierBeneficiaire) {}
		PreCheck check = txReadOnly.execute(status -> {
			WfTache t = tacheRepository.findByIdAvecDetails(tacheId)
					.orElseThrow(() -> WorkflowMetierException.notFound("TACHE_INTROUVABLE", "Tâche introuvable."));
			WfInstance inst = t.getInstance();
			if (estVisibleDirect(inst, appelantCompteId, estBackofficeLecture)) {
				return new PreCheck(inst.getId(), true, false);
			}
			boolean aBenefs = !beneficiaireRepository.findByIdInstanceId(inst.getId()).isEmpty();
			return new PreCheck(inst.getId(), false, aBenefs);
		});

		if (!check.visible()) {
			UUID collabId = check.verifierBeneficiaire() ? resoudreCollabHorsTransaction(appelantCompteId) : null;
			boolean visibleBenef = collabId != null && Boolean.TRUE.equals(txReadOnly.execute(status ->
					beneficiaireRepository.existsByIdInstanceIdAndIdCollaborateurId(check.instanceId(), collabId)));
			if (!visibleBenef) {
				throw WorkflowMetierException.notFound("TACHE_INTROUVABLE", "Tâche introuvable.");
			}
		}

		return txReadOnly.execute(status -> {
			WfTache t = tacheRepository.findByIdAvecDetails(tacheId)
					.orElseThrow(() -> WorkflowMetierException.notFound("TACHE_INTROUVABLE", "Tâche introuvable."));
			WfInstance inst = t.getInstance();

			DefinitionWorkflow def = chargerDefinition(inst.getModeleVersion());
			List<EtapeDefinition> etapesDef = def != null && def.etapes() != null ? def.etapes() : List.of();
			EtapeDefinition etapeDef = etapesDef.stream()
					.filter(e -> e.code().equals(t.getEtapeExecution().getEtapeCode()))
					.findFirst().orElse(null);

			List<String> actions = (t.getType() == TypeTache.VALIDATION && etapeDef != null && etapeDef.actions() != null)
					? etapeDef.actions()
					: actionsPermisesSimple(t.getType());

			boolean commentaireOblig = false;
			if (etapeDef != null && etapeDef.commentaireObligatoireSi() != null) {
				Map<String, TypeVariable> typesVars = new HashMap<>();
				for (VariableCatalogue vc : catalogue.variablesPour(inst.getObjetType()).values()) {
					typesVars.put(vc.nom(), vc.type());
				}
				commentaireOblig = evaluateur.evaluer(etapeDef.commentaireObligatoireSi(), inst.getVariables(), typesVars);
			}

			String objetLibelle = catalogue.findByObjetType(inst.getObjetType())
					.map(TypeObjetCatalogue::libelle).orElse(inst.getObjetType());
			boolean enRetard = t.getStatut() == StatutTache.A_TRAITER
					&& t.getEcheanceLe() != null
					&& t.getEcheanceLe().isBefore(clock.instant());

			SuiviInstanceResponse suivi = construireSuivi(inst, def, voirNomsPool);

			return new TacheDetailResponse(
					t.getId(), inst.getId(), t.getType(), t.getStatut(), t.getLibelle(),
					inst.getObjetType(), objetLibelle, inst.getObjetNumero(), inst.getObjetId(),
					inst.getDemandeurNom(), t.getEcheanceLe(), enRetard,
					actions, commentaireOblig, t.getVersion(), extraireResume(inst.getVariables()), suivi);
		});
	}

	public SuiviInstanceResponse suiviParInstance(UUID instanceId, UUID appelantCompteId, boolean estBackofficeLecture, boolean voirNomsPool) {
		boolean verifierBenef = Boolean.TRUE.equals(txReadOnly.execute(status -> {
			WfInstance inst = instanceRepository.findByIdAvecVersion(instanceId)
					.orElseThrow(() -> WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable."));
			if (estVisibleDirect(inst, appelantCompteId, estBackofficeLecture)) {
				return false;
			}
			if (beneficiaireRepository.findByIdInstanceId(inst.getId()).isEmpty()) {
				throw WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable.");
			}
			return true;
		}));

		if (verifierBenef) {
			UUID collabId = resoudreCollabHorsTransaction(appelantCompteId);
			boolean visibleBenef = collabId != null && Boolean.TRUE.equals(txReadOnly.execute(status ->
					beneficiaireRepository.existsByIdInstanceIdAndIdCollaborateurId(instanceId, collabId)));
			if (!visibleBenef) {
				throw WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable.");
			}
		}

		return txReadOnly.execute(status -> {
			WfInstance inst = instanceRepository.findByIdAvecVersion(instanceId)
					.orElseThrow(() -> WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable."));
			DefinitionWorkflow def = chargerDefinition(inst.getModeleVersion());
			return construireSuivi(inst, def, voirNomsPool);
		});
	}

	public SuiviInstanceResponse suiviParObjet(String objetType, UUID objetId, UUID appelantCompteId, boolean estBackofficeLecture, boolean voirNomsPool) {
		record PreCheckObjet(UUID instanceId, boolean verifierBenef) {}
		PreCheckObjet check = txReadOnly.execute(status -> {
			WfInstance inst = instanceRepository.findByObjetTypeAndObjetIdAvecVersion(objetType, objetId)
					.orElseThrow(() -> WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable."));
			if (estVisibleDirect(inst, appelantCompteId, estBackofficeLecture)) {
				return new PreCheckObjet(inst.getId(), false);
			}
			if (beneficiaireRepository.findByIdInstanceId(inst.getId()).isEmpty()) {
				throw WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable.");
			}
			return new PreCheckObjet(inst.getId(), true);
		});

		if (check.verifierBenef()) {
			UUID collabId = resoudreCollabHorsTransaction(appelantCompteId);
			boolean visibleBenef = collabId != null && Boolean.TRUE.equals(txReadOnly.execute(status ->
					beneficiaireRepository.existsByIdInstanceIdAndIdCollaborateurId(check.instanceId(), collabId)));
			if (!visibleBenef) {
				throw WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable.");
			}
		}

		return txReadOnly.execute(status -> {
			WfInstance inst = instanceRepository.findByObjetTypeAndObjetIdAvecVersion(objetType, objetId)
					.orElseThrow(() -> WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable."));
			DefinitionWorkflow def = chargerDefinition(inst.getModeleVersion());
			return construireSuivi(inst, def, voirNomsPool);
		});
	}

	@Transactional(readOnly = true)
	public SuiviInstanceResponse suiviParObjetInterne(String objetType, UUID objetId) {
		WfInstance inst = instanceRepository.findByObjetTypeAndObjetIdAvecVersion(objetType, objetId)
				.orElseThrow(() -> WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable."));
		DefinitionWorkflow def = codec.deserialiser(inst.getModeleVersion().getDefinition());
		return construireSuivi(inst, def, true);
	}

	private UUID resoudreCollabHorsTransaction(UUID compteId) {
		try {
			return referentielClient.collaborateurParCompte(compteId)
					.map(CollaborateurResume::id)
					.orElse(null);
		} catch (WorkflowMetierException ex) {
			return null;
		}
	}

	/** Visibilité § 9.10 (hors bénéficiaire vérifié hors transaction) : demandeur, candidat actuel ou passé, ou BACKOFFICE_LECTURE. */
	private boolean estVisibleDirect(WfInstance inst, UUID appelantCompteId, boolean estBackofficeLecture) {
		if (estBackofficeLecture) {
			return true;
		}
		if (appelantCompteId.equals(inst.getDemandeurCompteId())) {
			return true;
		}
		return candidatRepository.existsCandidatPourInstanceEtCompte(inst.getId(), appelantCompteId);
	}

	private SuiviInstanceResponse construireSuivi(WfInstance inst, DefinitionWorkflow def, boolean voirNomsPool) {
		List<WfEtapeExecution> executions = etapeExecutionRepository.findByInstanceIdOrderByOrdreAsc(inst.getId());
		List<WfTache> taches = tacheRepository.findByInstanceId(inst.getId());
		Map<UUID, List<WfTache>> tachesParEtape = taches.stream()
				.collect(Collectors.groupingBy(t -> t.getEtapeExecution().getId()));
		List<EtapeDefinition> etapesDef = def != null && def.etapes() != null ? def.etapes() : List.of();
		Map<String, EtapeDefinition> defsParCode = etapesDef.stream()
				.collect(Collectors.toMap(EtapeDefinition::code, e -> e, (a, b) -> a));

		List<EtapeSuiviDto> etapesDto = new ArrayList<>();
		Map<String, Boolean> codesExecutes = new HashMap<>();
		EtapeCouranteDto couranteDto = null;

		for (WfEtapeExecution ee : executions) {
			codesExecutes.put(ee.getEtapeCode(), true);
			EtapeDefinition ed = defsParCode.get(ee.getEtapeCode());
			String libelle = ed != null ? ed.libelle() : ee.getEtapeCode();
			String intitule = (ed != null && ed.intervenants() != null)
					? CatalogueTypesObjet.libelleRegle(ed.intervenants().regle(), ed.intervenants().params())
					: libelle;

			List<WfTache> ts = tachesParEtape.getOrDefault(ee.getId(), List.of());
			Comparator<WfTache> parPlusRecente = Comparator
					.comparing((WfTache t) -> t.getStatut() == StatutTache.ANNULEE ? 0 : 1)
					.thenComparing(WfTache::getDecideLe, Comparator.nullsLast(Comparator.naturalOrder()))
					.thenComparing(WfTache::getEcheanceLe, Comparator.nullsLast(Comparator.naturalOrder()))
					.thenComparing(WfTache::getId)
					.reversed();
			WfTache tPrincipale = ts.stream()
					.min(parPlusRecente)
					.orElse(null);

			List<String> nomsCandidats = null;
			if (voirNomsPool && tPrincipale != null && ee.getStatut() == StatutEtapeExecution.ACTIVE) {
				// D-W26 / CA-08.2 : noms des candidats d'un pool exposés uniquement aux RH et ADMIN, jamais au demandeur
				nomsCandidats = candidatRepository.findByIdTacheIdAndRetireLeIsNull(tPrincipale.getId()).stream()
						.map(WfTacheCandidat::getNom)
						.filter(n -> n != null && !n.isBlank())
						.toList();
			}

			String raison = ee.getRaisonSaut() != null ? ee.getRaisonSaut().name() : null;
			String motif = (tPrincipale != null && tPrincipale.getCommentaire() != null && !tPrincipale.getCommentaire().isBlank())
					? tPrincipale.getCommentaire() : null;

			etapesDto.add(new EtapeSuiviDto(
					ee.getEtapeCode(),
					libelle,
					ee.getStatut().name(),
					raison,
					intitule,
					nomsCandidats,
					tPrincipale != null ? tPrincipale.getDecideParNom() : null,
					tPrincipale != null ? tPrincipale.getPourNom() : null,
					ee.getResultat() != null ? ee.getResultat().name() : null,
					motif,
					tPrincipale != null ? tPrincipale.getEcheanceLe() : null,
					tPrincipale != null ? tPrincipale.getDecideLe() : null
			));

			if (ee.getStatut() == StatutEtapeExecution.ACTIVE) {
				couranteDto = new EtapeCouranteDto(ee.getEtapeCode(), libelle,
						tPrincipale != null ? tPrincipale.getEcheanceLe() : null, intitule);
			}
		}

		// Étapes à venir connues (libellés seulement) si l'instance n'est pas terminée (CA-08.1)
		if (!inst.getStatut().estTerminale()) {
			for (EtapeDefinition ed : etapesDef) {
				if (!codesExecutes.containsKey(ed.code())) {
					String intitule = ed.intervenants() != null
							? CatalogueTypesObjet.libelleRegle(ed.intervenants().regle(), ed.intervenants().params())
							: ed.libelle();
					etapesDto.add(new EtapeSuiviDto(
							ed.code(), ed.libelle(), "A_VENIR", null, intitule,
							null, null, null, null, null, null, null));
				}
			}
		}

		return new SuiviInstanceResponse(
				inst.getId(), inst.getObjetType(), inst.getObjetId(), inst.getObjetNumero(),
				inst.getModeleCode(), inst.getModeleVersionNumero(), inst.getStatut(),
				couranteDto, inst.getDemarreLe(), inst.getTermineLe(), inst.getResultatMotif(), etapesDto);
	}

	private List<String> actionsPermises(WfTache tache, WfInstance inst) {
		if (tache.getType() != TypeTache.VALIDATION) {
			return actionsPermisesSimple(tache.getType());
		}
		DefinitionWorkflow def = chargerDefinition(inst.getModeleVersion());
		String etapeCode = tache.getEtapeExecution() != null ? tache.getEtapeExecution().getEtapeCode() : null;
		EtapeDefinition etapeDef = (def != null && def.etapes() != null && etapeCode != null)
				? def.etapes().stream().filter(e -> etapeCode.equals(e.code())).findFirst().orElse(null)
				: null;
		return (etapeDef != null && etapeDef.actions() != null)
				? etapeDef.actions()
				: actionsPermisesSimple(tache.getType());
	}

	private static List<String> actionsPermisesSimple(TypeTache type) {
		return switch (type) {
			case VALIDATION -> List.of("APPROUVER", "REFUSER");
			case TACHE, COMPLEMENT -> List.of("FAIT");
		};
	}

	private static Map<String, Object> extraireResume(Map<String, Object> variables) {
		Map<String, Object> res = new LinkedHashMap<>();
		if (variables == null) {
			return res;
		}
		for (Map.Entry<String, Object> e : variables.entrySet()) {
			if (e.getKey() != null && e.getKey().startsWith("affichage_")) {
				res.put(e.getKey(), e.getValue());
			}
		}
		return res;
	}
}
