package com.hr.workflow.service;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeObjetCatalogue;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.dto.AdminWorkflowDtos.PageResponse;
import com.hr.workflow.dto.SupervisionDtos.CandidatAdminDto;
import com.hr.workflow.dto.SupervisionDtos.EtapeExecutionAdminDto;
import com.hr.workflow.dto.SupervisionDtos.HistoriqueAdminDto;
import com.hr.workflow.dto.SupervisionDtos.IndicateurEtapeDto;
import com.hr.workflow.dto.SupervisionDtos.IndicateursWorkflowResponse;
import com.hr.workflow.dto.SupervisionDtos.InstanceDetailAdminResponse;
import com.hr.workflow.dto.SupervisionDtos.InstanceResumeResponse;
import com.hr.workflow.dto.SupervisionDtos.TacheAdminDto;
import com.hr.workflow.entity.WfEtapeExecution;
import com.hr.workflow.entity.WfHistorique;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.moteur.PlanificationEcheanceService;
import com.hr.workflow.repository.WfEtapeExecutionRepository;
import com.hr.workflow.repository.WfHistoriqueRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfModeleRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
import com.hr.workflow.repository.WfTacheCandidatRepository;
import com.hr.workflow.repository.WfTacheRepository;
import com.hr.workflow.web.WorkflowMetierException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Supervision RH / Direction / Admin : recherche paginée, détail complet et indicateurs agrégés en base (US-15, D-W4-09).
 */
@Service
public class SupervisionService {

	private final WfInstanceRepository instanceRepository;
	private final WfEtapeExecutionRepository etapeExecutionRepository;
	private final WfTacheRepository tacheRepository;
	private final WfTacheCandidatRepository candidatRepository;
	private final WfHistoriqueRepository historiqueRepository;
	private final WfModeleRepository modeleRepository;
	private final WfModeleVersionRepository versionRepository;
	private final ModeleVersionService modeleVersionService;
	private final CatalogueTypesObjet catalogue;
	private final DefinitionCodec codec;
	private final Clock clock;

	public SupervisionService(
			WfInstanceRepository instanceRepository,
			WfEtapeExecutionRepository etapeExecutionRepository,
			WfTacheRepository tacheRepository,
			WfTacheCandidatRepository candidatRepository,
			WfHistoriqueRepository historiqueRepository,
			WfModeleRepository modeleRepository,
			WfModeleVersionRepository versionRepository,
			ModeleVersionService modeleVersionService,
			CatalogueTypesObjet catalogue,
			DefinitionCodec codec,
			Clock clock) {
		this.instanceRepository = instanceRepository;
		this.etapeExecutionRepository = etapeExecutionRepository;
		this.tacheRepository = tacheRepository;
		this.candidatRepository = candidatRepository;
		this.historiqueRepository = historiqueRepository;
		this.modeleRepository = modeleRepository;
		this.versionRepository = versionRepository;
		this.modeleVersionService = modeleVersionService;
		this.catalogue = catalogue;
		this.codec = codec;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<InstanceResumeResponse> listerInstances(
			String objetType,
			StatutInstance statut,
			String etape,
			Boolean enRetard,
			UUID demandeurId,
			UUID intervenantId,
			LocalDate du,
			LocalDate au,
			int page,
			int taille) {
		int p = Math.max(page, 0);
		int t = Math.min(Math.max(taille, 1), 100);
		Instant maintenant = clock.instant();
		Instant debut = du != null ? du.atStartOfDay(PlanificationEcheanceService.FUSEAU_DEFAUT).toInstant() : null;
		Instant finExclue = au != null ? au.plusDays(1).atStartOfDay(PlanificationEcheanceService.FUSEAU_DEFAUT).toInstant() : null;

		Specification<WfInstance> spec = (root, query, cb) -> {
			List<Predicate> preds = new ArrayList<>();
			if (objetType != null && !objetType.isBlank()) {
				preds.add(cb.equal(root.get("objetType"), objetType.trim()));
			}
			if (statut != null) {
				preds.add(cb.equal(root.get("statut"), statut));
			}
			if (etape != null && !etape.isBlank()) {
				preds.add(cb.equal(root.get("etapeCouranteCode"), etape.trim()));
			}
			if (demandeurId != null) {
				preds.add(cb.equal(root.get("demandeurCollaborateurId"), demandeurId));
			}
			if (debut != null) {
				preds.add(cb.greaterThanOrEqualTo(root.get("demarreLe"), debut));
			}
			if (finExclue != null) {
				preds.add(cb.lessThan(root.get("demarreLe"), finExclue));
			}
			if (enRetard != null) {
				Subquery<UUID> sqRetard = query.subquery(UUID.class);
				Root<WfTache> rt = sqRetard.from(WfTache.class);
				sqRetard.select(rt.get("id"));
				sqRetard.where(
						cb.equal(rt.get("instance").get("id"), root.get("id")),
						cb.equal(rt.get("statut"), StatutTache.A_TRAITER),
						cb.isNotNull(rt.get("echeanceLe")),
						cb.lessThan(rt.get("echeanceLe"), maintenant));
				Predicate instanceEnRetard = cb.and(
						root.get("statut").in(StatutInstance.EN_COURS, StatutInstance.BLOQUEE),
						cb.exists(sqRetard));
				preds.add(Boolean.TRUE.equals(enRetard) ? instanceEnRetard : cb.not(instanceEnRetard));
			}
			if (intervenantId != null) {
				Subquery<UUID> sqCand = query.subquery(UUID.class);
				Root<WfTacheCandidat> rc = sqCand.from(WfTacheCandidat.class);
				Root<WfTache> rt = sqCand.from(WfTache.class);
				sqCand.select(rt.get("id"));
				sqCand.where(
						cb.equal(rc.get("id").get("tacheId"), rt.get("id")),
						cb.equal(rt.get("instance").get("id"), root.get("id")),
						rt.get("statut").in(StatutTache.A_TRAITER, StatutTache.RESERVEE),
						cb.equal(rc.get("id").get("collaborateurId"), intervenantId),
						cb.isNull(rc.get("retireLe")));
				preds.add(cb.exists(sqCand));
			}
			return cb.and(preds.toArray(Predicate[]::new));
		};

		Sort tri = Sort.by(Sort.Order.desc("demarreLe"), Sort.Order.desc("id"));
		Page<WfInstance> resultat = instanceRepository.findAll(spec, PageRequest.of(p, t, tri));

		List<UUID> instanceIds = resultat.getContent().stream().map(WfInstance::getId).toList();
		Map<UUID, List<WfTache>> tachesOuvertesParInstance = instanceIds.isEmpty() ? Map.of()
				: tacheRepository.findByInstanceIdInAndStatutIn(
								instanceIds, List.of(StatutTache.A_TRAITER, StatutTache.RESERVEE))
						.stream()
						.collect(Collectors.groupingBy(ta -> ta.getInstance().getId()));

		Map<UUID, Map<String, String>> cacheLibelles = new HashMap<>();
		List<InstanceResumeResponse> items = resultat.getContent().stream().map(inst -> {
			List<WfTache> ouvertes = tachesOuvertesParInstance.getOrDefault(inst.getId(), List.of());
			Instant echeanceLe = ouvertes.stream()
					.map(WfTache::getEcheanceLe)
					.filter(java.util.Objects::nonNull)
					.min(Instant::compareTo)
					.orElse(null);
			boolean retard = !inst.getStatut().estTerminale()
					&& ouvertes.stream().anyMatch(ta ->
							ta.getStatut() == StatutTache.A_TRAITER
									&& ta.getEcheanceLe() != null
									&& ta.getEcheanceLe().isBefore(maintenant));

			String etapeCode = inst.getEtapeCouranteCode();
			String etapeLibelle = null;
			if (etapeCode != null && inst.getModeleVersion() != null) {
				Map<String, String> libs = cacheLibelles.computeIfAbsent(
						inst.getModeleVersion().getId(),
						vid -> libellesParCode(chargerDefinition(inst.getModeleVersion())));
				etapeLibelle = libs.getOrDefault(etapeCode, etapeCode);
			}
			String objetLibelle = catalogue.findByObjetType(inst.getObjetType())
					.map(TypeObjetCatalogue::libelle).orElse(inst.getObjetType());

			return new InstanceResumeResponse(
					inst.getId(),
					inst.getObjetType(),
					objetLibelle,
					inst.getObjetNumero(),
					inst.getObjetId(),
					inst.getModeleCode(),
					inst.getDemandeurCollaborateurId(),
					inst.getDemandeurNom(),
					inst.getStatut(),
					etapeCode,
					etapeLibelle,
					echeanceLe,
					retard,
					inst.getDemarreLe(),
					inst.getTermineLe());
		}).toList();

		return new PageResponse<>(items, resultat.getNumber(), resultat.getSize(), resultat.getTotalElements());
	}

	@Transactional(readOnly = true)
	public InstanceDetailAdminResponse detailInstance(UUID instanceId) {
		WfInstance inst = instanceRepository.findByIdAvecVersion(instanceId)
				.orElseThrow(() -> WorkflowMetierException.notFound("INSTANCE_INTROUVABLE", "Instance introuvable."));

		DefinitionWorkflow def = chargerDefinition(inst.getModeleVersion());
		Map<String, String> libelles = libellesParCode(def);

		List<WfEtapeExecution> executions = etapeExecutionRepository.findByInstanceIdOrderByOrdreAsc(instanceId);
		List<WfTache> taches = tacheRepository.findByInstanceId(instanceId);
		List<UUID> tacheIds = taches.stream().map(WfTache::getId).toList();
		Map<UUID, List<WfTacheCandidat>> candsParTache = tacheIds.isEmpty() ? Map.of()
				: candidatRepository.findByIdTacheIdIn(tacheIds).stream()
						.collect(Collectors.groupingBy(c -> c.getId().getTacheId()));
		List<WfHistorique> hist = historiqueRepository.findByInstanceIdOrderByHorodatageAsc(instanceId);

		Instant maintenant = clock.instant();
		String objetLibelle = catalogue.findByObjetType(inst.getObjetType())
				.map(TypeObjetCatalogue::libelle).orElse(inst.getObjetType());
		String etapeCouranteLibelle = inst.getEtapeCouranteCode() != null
				? libelles.getOrDefault(inst.getEtapeCouranteCode(), inst.getEtapeCouranteCode())
				: null;

		List<EtapeExecutionAdminDto> etapesDto = executions.stream()
				.map(ee -> new EtapeExecutionAdminDto(
						ee.getId(),
						ee.getEtapeCode(),
						libelles.getOrDefault(ee.getEtapeCode(), ee.getEtapeCode()),
						ee.getOrdre(),
						ee.getStatut(),
						ee.getRaisonSaut(),
						ee.getResultat(),
						ee.getNbRenvois(),
						ee.getActiveLe(),
						ee.getTermineeLe()))
				.toList();

		List<TacheAdminDto> tachesDto = taches.stream().map(t -> {
			boolean retard = t.getStatut() == StatutTache.A_TRAITER
					&& t.getEcheanceLe() != null
					&& t.getEcheanceLe().isBefore(maintenant);
			List<CandidatAdminDto> candsDto = candsParTache.getOrDefault(t.getId(), List.of()).stream()
					.map(c -> new CandidatAdminDto(
							c.getId().getCollaborateurId(),
							c.getCompteId(),
							c.getNom(),
							c.getOrigine(),
							c.getPourCollaborateurId(),
							c.getPourNom(),
							c.getAjouteLe(),
							c.getRetireLe()))
					.toList();
			return new TacheAdminDto(
					t.getId(),
					t.getEtapeExecution().getId(),
					t.getCodeTache(),
					t.getType(),
					t.getStatut(),
					t.getLibelle(),
					t.isObligatoire(),
					t.getEcheanceLe(),
					t.getProchaineRelanceLe(),
					retard,
					t.getNbRelances(),
					t.getEscaladeAFaireLe(),
					t.getEscaladeLe(),
					t.getVersion(),
					t.getDecision(),
					t.getDecideParNom(),
					t.getDecideLe(),
					t.getPourNom(),
					t.getCommentaire(),
					candsDto);
		}).toList();

		List<HistoriqueAdminDto> histDto = hist.stream()
				.map(h -> new HistoriqueAdminDto(
						h.getId(), h.getTacheId(), h.getTypeEvenement(),
						h.getActeurCompteId(), h.getActeurNom(), h.getPourNom(),
						h.getDetails(), h.getHorodatage()))
				.toList();

		return new InstanceDetailAdminResponse(
				inst.getId(),
				inst.getObjetType(),
				objetLibelle,
				inst.getObjetNumero(),
				inst.getObjetId(),
				inst.getModeleCode(),
				inst.getModeleVersionNumero(),
				inst.getDemandeurCollaborateurId(),
				inst.getDemandeurNom(),
				inst.getStatut(),
				inst.getEtapeCouranteCode(),
				etapeCouranteLibelle,
				inst.getDemarreLe(),
				inst.getTermineLe(),
				inst.getResultatMotif(),
				inst.getVariables(),
				etapesDto,
				tachesDto,
				histDto);
	}

	@Transactional(readOnly = true)
	public IndicateursWorkflowResponse indicateurs(LocalDate du, LocalDate au) {
		Instant maintenant = clock.instant();
		Instant debut = du != null
				? du.atStartOfDay(PlanificationEcheanceService.FUSEAU_DEFAUT).toInstant()
				: maintenant.minus(30, ChronoUnit.DAYS);
		Instant fin = au != null
				? au.plusDays(1).atStartOfDay(PlanificationEcheanceService.FUSEAU_DEFAUT).toInstant().minusNanos(1)
				: maintenant;

		long enCours = instanceRepository.countByStatut(StatutInstance.EN_COURS);
		long bloquees = instanceRepository.countByStatut(StatutInstance.BLOQUEE);
		long enRetard = tacheRepository.countInstancesEnRetard(maintenant);

		List<Object[]> rows = tacheRepository.calculerIndicateursParEtape(debut, fin);
		Map<String, Map<String, String>> libellesParModele = new HashMap<>();
		List<IndicateurEtapeDto> parEtape = new ArrayList<>(rows.size());

		for (Object[] row : rows) {
			String modeleCode = String.valueOf(row[0]);
			String etapeCode = String.valueOf(row[1]);
			BigDecimal delaiMoyen = versBigDecimal(row[2]);
			long nb = row[3] instanceof Number n ? n.longValue() : 0L;

			Map<String, String> libs = libellesParModele.computeIfAbsent(modeleCode, this::chargerLibellesModele);
			String libelle = libs.getOrDefault(etapeCode, etapeCode);
			parEtape.add(new IndicateurEtapeDto(modeleCode, etapeCode, libelle, delaiMoyen, nb));
		}

		return new IndicateursWorkflowResponse(enCours, enRetard, bloquees, parEtape);
	}

	private DefinitionWorkflow chargerDefinition(WfModeleVersion version) {
		if (modeleVersionService != null) {
			return modeleVersionService.chargerDefinition(version.getId());
		}
		return codec.deserialiser(version.getDefinition());
	}

	private Map<String, String> chargerLibellesModele(String modeleCode) {
		Optional<WfModele> mOpt = modeleRepository.findByCode(modeleCode);
		if (mOpt.isEmpty()) {
			return Map.of();
		}
		WfModele m = mOpt.get();
		Optional<WfModeleVersion> vOpt = versionRepository.findByModeleIdAndStatut(m.getId(), StatutVersion.PUBLIEE)
				.or(() -> versionRepository.findTopByModeleIdOrderByNumeroDesc(m.getId()));
		return vOpt.map(v -> libellesParCode(chargerDefinition(v))).orElse(Map.of());
	}

	private static Map<String, String> libellesParCode(DefinitionWorkflow def) {
		Map<String, String> map = new HashMap<>();
		if (def != null && def.etapes() != null) {
			for (EtapeDefinition e : def.etapes()) {
				if (e.code() != null && e.libelle() != null) {
					map.put(e.code(), e.libelle());
				}
			}
		}
		return map;
	}

	private static BigDecimal versBigDecimal(Object val) {
		if (val == null) {
			return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}
		if (val instanceof BigDecimal bd) {
			return bd.setScale(2, RoundingMode.HALF_UP);
		}
		if (val instanceof Number n) {
			return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
		}
		return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
	}
}
