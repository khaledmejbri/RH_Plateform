package com.hr.evaluation.service;

import com.hr.evaluation.domain.CouleurAlerteEvaluationRh;
import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.domain.TypeEvaluationRh;
import com.hr.evaluation.dto.EvaluationAnnuelleCreationRequest;
import com.hr.evaluation.dto.EvaluationPdfArchiveResponse;
import com.hr.evaluation.dto.EvaluationRhResponse;
import com.hr.evaluation.dto.EvaluationSemestrielleCreationRequest;
import com.hr.evaluation.entity.EvaluationRh;
import com.hr.evaluation.kafka.EvaluationEventPublisher;
import com.hr.evaluation.repository.EvaluationRhRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EvaluationRhService {

	private final EvaluationRhRepository repository;
	private final EvaluationScoringService scoringService;
	private final EvaluationEventPublisher eventPublisher;
	private final EvaluationPdfGenerator pdfGenerator;
	private final EvaluationArchiveStorage archiveStorage;

	public EvaluationRhService(
			EvaluationRhRepository repository,
			EvaluationScoringService scoringService,
			EvaluationEventPublisher eventPublisher,
			EvaluationPdfGenerator pdfGenerator,
			EvaluationArchiveStorage archiveStorage) {
		this.repository = repository;
		this.scoringService = scoringService;
		this.eventPublisher = eventPublisher;
		this.pdfGenerator = pdfGenerator;
		this.archiveStorage = archiveStorage;
	}

	@Transactional
	public EvaluationRhResponse creerSemestrielle(EvaluationSemestrielleCreationRequest req) {
		EvaluationScore score = scoringService.calculer(
				req.qualiteTravail(), req.rendement(), req.ponctualite(), req.espritEquipe());
		EvaluationRh evaluation = new EvaluationRh();
		evaluation.setType(TypeEvaluationRh.SEMESTRIELLE);
		evaluation.setCollaborateurIdentifiant(req.collaborateurIdentifiant());
		evaluation.setSuperieurIdentifiant(req.superieurIdentifiant());
		evaluation.setAnnee(req.annee());
		evaluation.setSemestre(req.semestre());
		evaluation.setQualiteTravail(req.qualiteTravail());
		evaluation.setRendement(req.rendement());
		evaluation.setPonctualite(req.ponctualite());
		evaluation.setEspritEquipe(req.espritEquipe());
		evaluation.setPointsForts(trim(req.pointsForts()));
		evaluation.setPointsAAmeliorer(trim(req.pointsAAmeliorer()));
		evaluation.setPlanActionRecommande(trim(req.planActionRecommande()));
		appliquerScoreEtRegles(evaluation, score);
		EvaluationRh saved = repository.save(evaluation);
		eventPublisher.publierAlerteSiNecessaire(saved);
		return toResponse(saved);
	}

	@Transactional
	public EvaluationRhResponse creerAnnuelle(EvaluationAnnuelleCreationRequest req) {
		EvaluationScore score = scoringService.calculer(req.savoirTechnique(), req.savoirFaire(), req.savoirEtre());
		EvaluationRh evaluation = new EvaluationRh();
		evaluation.setType(TypeEvaluationRh.ANNUELLE);
		evaluation.setCollaborateurIdentifiant(req.collaborateurIdentifiant());
		evaluation.setSuperieurIdentifiant(req.superieurIdentifiant());
		evaluation.setAnnee(req.annee());
		evaluation.setSavoirTechnique(req.savoirTechnique());
		evaluation.setSavoirFaire(req.savoirFaire());
		evaluation.setSavoirEtre(req.savoirEtre());
		evaluation.setBilanSavoir(trim(req.bilanSavoir()));
		evaluation.setBilanSavoirFaire(trim(req.bilanSavoirFaire()));
		evaluation.setBilanSavoirEtre(trim(req.bilanSavoirEtre()));
		evaluation.setResultatsObjectifsN(trim(req.resultatsObjectifsN()));
		evaluation.setObjectifsNPlus1(trim(req.objectifsNPlus1()));
		evaluation.setPointsForts(trim(req.pointsForts()));
		evaluation.setPointsAAmeliorer(trim(req.pointsAAmeliorer()));
		evaluation.setPlanActionRecommande(trim(req.planActionRecommande()));
		evaluation.setFormationsRecommandees(nettoyerListe(req.formationsRecommandees()));
		appliquerScoreEtRegles(evaluation, score);
		EvaluationRh saved = repository.save(evaluation);
		eventPublisher.publierAlerteSiNecessaire(saved);
		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public List<EvaluationRhResponse> lister(TypeEvaluationRh type, StatutEvaluationRh statut) {
		List<EvaluationRh> evaluations;
		if (type != null && statut != null) {
			evaluations = repository.findByTypeAndStatutOrderByCreeLeDesc(type, statut);
		} else if (type != null) {
			evaluations = repository.findByTypeOrderByCreeLeDesc(type);
		} else if (statut != null) {
			evaluations = repository.findByStatutOrderByCreeLeDesc(statut);
		} else {
			evaluations = repository.findAllByOrderByCreeLeDesc();
		}
		return evaluations.stream().map(this::toResponse).collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<EvaluationRhResponse> listerCollaborateur(UUID collaborateurIdentifiant) {
		return repository.findByCollaborateurIdentifiantOrderByCreeLeDesc(collaborateurIdentifiant).stream()
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<EvaluationRhResponse> listerSuperieur(UUID superieurIdentifiant) {
		return repository.findBySuperieurIdentifiantOrderByCreeLeDesc(superieurIdentifiant).stream()
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public EvaluationRhResponse obtenir(UUID id) {
		return toResponse(charger(id));
	}

	@Transactional
	public EvaluationRhResponse validerCollaborateur(UUID id, UUID acteurIdentifiant) {
		EvaluationRh evaluation = charger(id);
		if (acteurIdentifiant != null && !acteurIdentifiant.equals(evaluation.getCollaborateurIdentifiant())) {
			throw new IllegalArgumentException("Seul le collaborateur evalue peut valider cette evaluation.");
		}
		evaluation.setValidationCollaborateurLe(Instant.now());
		actualiserStatutValidation(evaluation);
		return toResponse(repository.save(evaluation));
	}

	@Transactional
	public EvaluationRhResponse validerSuperieur(UUID id, UUID acteurIdentifiant) {
		EvaluationRh evaluation = charger(id);
		if (acteurIdentifiant != null && !acteurIdentifiant.equals(evaluation.getSuperieurIdentifiant())) {
			throw new IllegalArgumentException("Seul le superieur renseigne peut valider cette evaluation.");
		}
		evaluation.setValidationSuperieurLe(Instant.now());
		actualiserStatutValidation(evaluation);
		if (evaluation.getType() == TypeEvaluationRh.ANNUELLE && !evaluation.isFormationsIntegreesM05()) {
			integrerFormationsM05(evaluation);
		}
		return toResponse(repository.save(evaluation));
	}

	@Transactional
	public EvaluationRhResponse integrerFormationsM05(UUID id) {
		EvaluationRh evaluation = charger(id);
		integrerFormationsM05(evaluation);
		return toResponse(repository.save(evaluation));
	}

	@Transactional
	public EvaluationPdfArchiveResponse exporterPdf(UUID id) {
		EvaluationRh evaluation = charger(id);
		if (evaluation.getStatut() != StatutEvaluationRh.VALIDEE
				&& evaluation.getStatut() != StatutEvaluationRh.ARCHIVEE) {
			throw new IllegalArgumentException("Export PDF possible uniquement apres validation croisee.");
		}
		String objectKey = "evaluations/" + evaluation.getAnnee() + "/" + evaluation.getId() + ".pdf";
		ArchivedPdf archived = archiveStorage.stocker(objectKey, pdfGenerator.generer(evaluation));
		evaluation.setPdfObjectKey(archived.objectKey());
		evaluation.setPdfArchiveUrl(archived.archiveUrl());
		evaluation.setPdfArchiveLe(Instant.now());
		evaluation.setStatut(StatutEvaluationRh.ARCHIVEE);
		repository.save(evaluation);
		return new EvaluationPdfArchiveResponse(
				evaluation.getId(),
				evaluation.getPdfObjectKey(),
				evaluation.getPdfArchiveUrl(),
				evaluation.getPdfArchiveLe());
	}

	private void appliquerScoreEtRegles(EvaluationRh evaluation, EvaluationScore score) {
		if (score.couleurAlerte() == CouleurAlerteEvaluationRh.ROUGE
				&& isBlank(evaluation.getPlanActionRecommande())) {
			throw new IllegalArgumentException("Un plan d'action est obligatoire pour une evaluation en alerte rouge.");
		}
		evaluation.setScoreSur20(score.scoreSur20());
		evaluation.setAppreciation(score.appreciation());
		evaluation.setCouleurAlerte(score.couleurAlerte());
		evaluation.setRecommandationsIa(score.recommandationsIa());
	}

	private void actualiserStatutValidation(EvaluationRh evaluation) {
		boolean collaborateur = evaluation.getValidationCollaborateurLe() != null;
		boolean superieur = evaluation.getValidationSuperieurLe() != null;
		if (collaborateur && superieur) {
			evaluation.setStatut(StatutEvaluationRh.VALIDEE);
		} else if (collaborateur) {
			evaluation.setStatut(StatutEvaluationRh.VALIDEE_COLLABORATEUR);
		} else if (superieur) {
			evaluation.setStatut(StatutEvaluationRh.VALIDEE_SUPERIEUR);
		}
	}

	private void integrerFormationsM05(EvaluationRh evaluation) {
		if (evaluation.getFormationsRecommandees().isEmpty()) {
			evaluation.setFormationsIntegreesM05(true);
			return;
		}
		evaluation.getFormationsRecommandees().forEach(formation ->
				eventPublisher.publierFormationRecommandee(evaluation, formation));
		evaluation.setFormationsIntegreesM05(true);
	}

	private EvaluationRh charger(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Evaluation introuvable : " + id));
	}

	private EvaluationRhResponse toResponse(EvaluationRh e) {
		return new EvaluationRhResponse(
				e.getId(),
				e.getType(),
				e.getStatut(),
				e.getCollaborateurIdentifiant(),
				e.getSuperieurIdentifiant(),
				e.getAnnee(),
				e.getSemestre(),
				e.getQualiteTravail(),
				e.getRendement(),
				e.getPonctualite(),
				e.getEspritEquipe(),
				e.getSavoirTechnique(),
				e.getSavoirFaire(),
				e.getSavoirEtre(),
				e.getScoreSur20(),
				e.getAppreciation(),
				e.getCouleurAlerte(),
				e.getPointsForts(),
				e.getPointsAAmeliorer(),
				e.getPlanActionRecommande(),
				e.getRecommandationsIa(),
				e.getBilanSavoir(),
				e.getBilanSavoirFaire(),
				e.getBilanSavoirEtre(),
				e.getResultatsObjectifsN(),
				e.getObjectifsNPlus1(),
				List.copyOf(e.getFormationsRecommandees()),
				e.isFormationsIntegreesM05(),
				e.getValidationCollaborateurLe(),
				e.getValidationSuperieurLe(),
				e.getPdfObjectKey(),
				e.getPdfArchiveUrl(),
				e.getPdfArchiveLe(),
				e.getCreeLe(),
				e.getModifieLe());
	}

	private static List<String> nettoyerListe(List<String> values) {
		if (values == null) {
			return List.of();
		}
		return values.stream()
				.map(EvaluationRhService::trim)
				.filter(v -> !isBlank(v))
				.distinct()
				.toList();
	}

	private static String trim(String value) {
		return value == null ? null : value.trim();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
