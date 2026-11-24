package com.hr.workflow.service;

import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.NotificationsDynamiquesDefinition;
import com.hr.workflow.domain.ModeActivation;
import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.dto.WorkflowMoteurDtos.RegleActivePointageResponse;
import com.hr.workflow.entity.WfActivation;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.repository.WfActivationRepository;
import com.hr.workflow.repository.WfModeleRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lecture directe (sans cache TTL obsolète) de la règle active {@code POINTAGE} pour {@code svc-presence}.
 */
@Service
public class PointageRegleInterneService {

	private static final Set<String> TYPES_AUTORISES = Set.of("ENTREE", "SORTIE");
	private static final Set<String> CANAUX_AUTORISES = Set.of("WEBSOCKET", "BOTH");

	private final WfActivationRepository activationRepository;
	private final WfModeleRepository modeleRepository;
	private final WfModeleVersionRepository versionRepository;
	private final ModeleVersionService modeleVersionService;

	public PointageRegleInterneService(
			WfActivationRepository activationRepository,
			WfModeleRepository modeleRepository,
			WfModeleVersionRepository versionRepository,
			ModeleVersionService modeleVersionService) {
		this.activationRepository = activationRepository;
		this.modeleRepository = modeleRepository;
		this.versionRepository = versionRepository;
		this.modeleVersionService = modeleVersionService;
	}

	@Transactional(readOnly = true)
	public RegleActivePointageResponse lireRegleActive() {
		ModeActivation mode = activationRepository.findByObjetType("POINTAGE")
				.map(WfActivation::getMode)
				.orElse(ModeActivation.HERITE);
		if (mode != ModeActivation.MOTEUR) {
			return RegleActivePointageResponse.inactive(mode);
		}

		Optional<WfModele> modeleOpt = modeleRepository.findActifParObjetTypeEtSousType("POINTAGE", null);
		if (modeleOpt.isEmpty()) {
			return RegleActivePointageResponse.inactive(mode);
		}
		WfModele modele = modeleOpt.get();

		Optional<WfModeleVersion> versionOpt = versionRepository.findByModeleIdAndStatut(
				modele.getId(), StatutVersion.PUBLIEE);
		if (versionOpt.isEmpty()) {
			return RegleActivePointageResponse.inactive(mode);
		}
		WfModeleVersion version = versionOpt.get();

		DefinitionWorkflow def = modeleVersionService.chargerDefinition(version.getId());
		NotificationsDynamiquesDefinition nd = def != null ? def.notificationsDynamiques() : null;
		if (nd == null) {
			return RegleActivePointageResponse.inactive(mode);
		}

		boolean notifierRo = Boolean.TRUE.equals(nd.notifierRo());
		String porteeSites = nd.porteeSites() != null ? nd.porteeSites() : "TOUTE_LA_SOCIETE";
		List<UUID> siteIds = "SITES_SELECTIONNES".equals(porteeSites)
				? normaliserSiteIds(nd.siteIds())
				: List.of();
		List<String> typesPointage = normaliserTypesPointage(nd.typesPointage());
		String canal = nd.canal() != null && CANAUX_AUTORISES.contains(nd.canal())
				? nd.canal()
				: "WEBSOCKET";

		return new RegleActivePointageResponse(
				notifierRo,
				mode,
				modele.getCode(),
				version.getNumero(),
				notifierRo,
				porteeSites,
				siteIds,
				typesPointage,
				canal
		);
	}

	private static List<UUID> normaliserSiteIds(List<String> bruts) {
		if (bruts == null || bruts.isEmpty()) {
			return List.of();
		}
		Set<UUID> uniques = new LinkedHashSet<>();
		for (String raw : bruts) {
			if (raw == null || raw.isBlank()) {
				continue;
			}
			try {
				uniques.add(UUID.fromString(raw.trim()));
			} catch (IllegalArgumentException ignored) {
			}
		}
		return new ArrayList<>(uniques);
	}

	private static List<String> normaliserTypesPointage(List<String> bruts) {
		if (bruts == null || bruts.isEmpty()) {
			return List.of();
		}
		Set<String> uniques = new LinkedHashSet<>();
		for (String t : bruts) {
			if (t != null && TYPES_AUTORISES.contains(t)) {
				uniques.add(t);
			}
		}
		return new ArrayList<>(uniques);
	}
}
