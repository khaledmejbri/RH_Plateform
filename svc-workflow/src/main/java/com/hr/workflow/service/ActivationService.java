package com.hr.workflow.service;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.config.CacheConfig;
import com.hr.workflow.domain.ActionJournalModele;
import com.hr.workflow.domain.ModeActivation;
import com.hr.workflow.dto.AdminWorkflowDtos.ActivationResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.ModifierActivationRequest;
import com.hr.workflow.entity.WfActivation;
import com.hr.workflow.entity.WfJournalModele;
import com.hr.workflow.repository.WfActivationRepository;
import com.hr.workflow.repository.WfJournalModeleRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
import com.hr.workflow.web.WorkflowMetierException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion des activations {@code HERITE} / {@code MOTEUR} par type d'objet (§ 9.7.2, D8).
 */
@Service
public class ActivationService {

	private final WfActivationRepository activationRepository;
	private final WfModeleVersionRepository versionRepository;
	private final WfJournalModeleRepository journalRepository;
	private final CatalogueTypesObjet catalogue;
	private final Clock clock;

	public ActivationService(
			WfActivationRepository activationRepository,
			WfModeleVersionRepository versionRepository,
			WfJournalModeleRepository journalRepository,
			CatalogueTypesObjet catalogue,
			Clock clock) {
		this.activationRepository = activationRepository;
		this.versionRepository = versionRepository;
		this.journalRepository = journalRepository;
		this.catalogue = catalogue;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<ActivationResponse> lister() {
		Map<String, WfActivation> parType = activationRepository.findAllByOrderByObjetTypeAsc().stream()
				.collect(java.util.stream.Collectors.toMap(WfActivation::getObjetType, a -> a, (a, b) -> a));
		return catalogue.listerTypes().stream()
				.map(t -> {
					WfActivation act = parType.get(t.objetType());
					if (act != null) {
						return new ActivationResponse(t.objetType(), t.libelle(), act.getMode(), act.getModifieLe(), act.getVersion());
					}
					return new ActivationResponse(t.objetType(), t.libelle(), ModeActivation.HERITE, null, 0L);
				})
				.toList();
	}

	@Transactional(readOnly = true)
	@Cacheable(cacheNames = CacheConfig.CACHE_ACTIVATIONS, key = "#objetType")
	public ModeActivation modePour(String objetType) {
		if (!catalogue.existe(objetType)) {
			throw WorkflowMetierException.notFound("TYPE_OBJET_INCONNU", "Type d'objet inconnu : " + objetType);
		}
		return activationRepository.findByObjetType(objetType)
				.map(WfActivation::getMode)
				.orElse(ModeActivation.HERITE);
	}

	@Transactional
	@CacheEvict(cacheNames = CacheConfig.CACHE_ACTIVATIONS, key = "#objetType")
	public ActivationResponse modifier(
			String objetType,
			ModifierActivationRequest req,
			UUID auteurCompteId,
			String auteurNom) {
		TypeObjetCatalogue type = catalogue.findByObjetType(objetType)
				.orElseThrow(() -> WorkflowMetierException.badRequest("TYPE_OBJET_INCONNU",
						"Type d'objet inconnu : " + objetType));

		if (req.mode() == ModeActivation.MOTEUR && !versionRepository.existsVersionPublieePourObjetType(objetType)) {
			throw WorkflowMetierException.unprocessable("WORKFLOW_ABSENT",
					"Impossible d'activer le moteur pour " + type.libelle() + " sans modèle actif publié.");
		}

		Instant maintenant = clock.instant();
		WfActivation act = activationRepository.findByObjetType(objetType).orElseGet(() -> {
			WfActivation nouveau = new WfActivation();
			nouveau.setObjetType(objetType);
			nouveau.setMode(ModeActivation.HERITE);
			nouveau.setModifieLe(maintenant);
			return nouveau;
		});

		if (act.getId() != null && !act.getVersion().equals(req.version())) {
			throw WorkflowMetierException.conflict("MODIFIE_ENTRE_TEMPS",
					"L'activation a été modifiée par un autre utilisateur.");
		}

		ModeActivation avant = act.getMode();
		act.setMode(req.mode());
		act.setModifieLe(maintenant);
		act.setModifieParCompteId(auteurCompteId);
		act = activationRepository.saveAndFlush(act);

		WfJournalModele journal = new WfJournalModele();
		journal.setAction(ActionJournalModele.BASCULE_MODE);
		journal.setAvant(Map.of("objet_type", objetType, "mode", avant.name()));
		journal.setApres(Map.of("objet_type", objetType, "mode", act.getMode().name()));
		journal.setAuteurCompteId(auteurCompteId);
		journal.setAuteurNom(auteurNom);
		journal.setHorodatage(maintenant);
		journalRepository.save(journal);

		return new ActivationResponse(objetType, type.libelle(), act.getMode(), act.getModifieLe(), act.getVersion());
	}
}
