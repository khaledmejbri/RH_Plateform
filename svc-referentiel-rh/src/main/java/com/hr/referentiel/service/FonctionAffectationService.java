package com.hr.referentiel.service;

import com.hr.referentiel.dto.FonctionDtos.AffectationRequest;
import com.hr.referentiel.dto.FonctionDtos.AffectationResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.Fonction;
import com.hr.referentiel.entity.FonctionAffectation;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.FonctionAffectationRepository;
import com.hr.referentiel.repository.FonctionRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class FonctionAffectationService {

	private final FonctionRepository fonctionRepo;
	private final FonctionAffectationRepository affectationRepo;
	private final CollaborateurRepository collaborateurRepo;
	private final UniteOrganisationRepository uniteRepo;
	private final Clock clock;

	public FonctionAffectationService(
			FonctionRepository fonctionRepo,
			FonctionAffectationRepository affectationRepo,
			CollaborateurRepository collaborateurRepo,
			UniteOrganisationRepository uniteRepo,
			Clock clock) {
		this.fonctionRepo = fonctionRepo;
		this.affectationRepo = affectationRepo;
		this.collaborateurRepo = collaborateurRepo;
		this.uniteRepo = uniteRepo;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<AffectationResponse> lister(UUID fonctionId) {
		if (!fonctionRepo.existsById(fonctionId)) {
			throw ReferentielMetierException.notFound("FONCTION_INTROUVABLE", "Fonction absente.");
		}
		return affectationRepo.findAllByFonctionIdAvecDetails(fonctionId).stream()
				.map(a -> new AffectationResponse(
						a.getId(), a.getFonction().getId(), a.getCollaborateur().getId(),
						a.getCollaborateur().getPrenom() + " " + a.getCollaborateur().getNom(),
						a.getCollaborateur().getMatricule(),
						a.getUnitePerimetre() != null ? a.getUnitePerimetre().getId() : null,
						a.getUnitePerimetre() != null ? a.getUnitePerimetre().getLibelle() : null,
						a.getDu(), a.getAu(), a.getCreeLe()))
				.toList();
	}

	@Transactional
	public AffectationResponse affecter(UUID fonctionId, AffectationRequest req) {
		Fonction fonction = fonctionRepo.findById(fonctionId)
				.orElseThrow(() -> ReferentielMetierException.notFound("FONCTION_INTROUVABLE", "Fonction absente."));
		if (!fonction.isActif()) {
			throw ReferentielMetierException.unprocessable("FONCTION_INACTIVE", "Fonction inactive.");
		}
		if (req.au() != null && req.au().isBefore(req.du())) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "Date de fin antérieure au début.");
		}
		Collaborateur collaborateur = collaborateurRepo.findById(req.collaborateurId())
				.orElseThrow(() -> ReferentielMetierException.notFound("COLLABORATEUR_INTROUVABLE", "Collaborateur absent."));
		UniteOrganisation perimetre = req.unitePerimetreId() == null ? null
				: uniteRepo.findById(req.unitePerimetreId())
						.orElseThrow(() -> ReferentielMetierException.notFound("UNITE_INTROUVABLE", "Unité absente."));
		boolean doublon = affectationRepo.findAllByFonctionIdAvecDetails(fonctionId).stream().anyMatch(ex -> {
			UUID uEx = ex.getUnitePerimetre() == null ? null : ex.getUnitePerimetre().getId();
			boolean memeCible = ex.getCollaborateur().getId().equals(collaborateur.getId())
					&& Objects.equals(uEx, req.unitePerimetreId());
			boolean chevauche = (ex.getAu() == null || !ex.getAu().isBefore(req.du()))
					&& (req.au() == null || !req.au().isBefore(ex.getDu()));
			return memeCible && chevauche;
		});
		if (doublon) {
			throw ReferentielMetierException.conflict("AFFECTATION_CHEVAUCHEMENT", "Affectation déjà active sur cette période.");
		}
		Instant maintenant = Instant.now(clock);
		if (!fonction.isUtilisee()) {
			fonction.setUtilisee(true);
			fonction.setModifieLe(maintenant);
			fonctionRepo.save(fonction);
		}
		FonctionAffectation creee = affectationRepo.saveAndFlush(
				new FonctionAffectation(fonction, collaborateur, perimetre, req.du(), req.au(), maintenant));
		return new AffectationResponse(
				creee.getId(), fonctionId, collaborateur.getId(),
				collaborateur.getPrenom() + " " + collaborateur.getNom(), collaborateur.getMatricule(),
				perimetre != null ? perimetre.getId() : null, perimetre != null ? perimetre.getLibelle() : null,
				creee.getDu(), creee.getAu(), creee.getCreeLe());
	}

	@Transactional
	public void retirer(UUID fonctionId, UUID affectationId) {
		if (!fonctionRepo.existsById(fonctionId)) {
			throw ReferentielMetierException.notFound("FONCTION_INTROUVABLE", "Fonction absente.");
		}
		FonctionAffectation aff = affectationRepo.findById(affectationId)
				.filter(a -> a.getFonction().getId().equals(fonctionId))
				.orElseThrow(() -> ReferentielMetierException.notFound("AFFECTATION_INTROUVABLE", "Affectation absente."));
		affectationRepo.delete(aff);
	}
}
