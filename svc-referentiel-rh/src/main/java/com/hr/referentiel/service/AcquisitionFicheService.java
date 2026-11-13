package com.hr.referentiel.service;

import com.hr.referentiel.domain.OrigineMouvementSolde;
import com.hr.referentiel.domain.TypeMouvementSolde;
import com.hr.referentiel.entity.SoldeConge;
import com.hr.referentiel.repository.AcquisitionMoisRepository;
import com.hr.referentiel.repository.MouvementSoldeCongeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Étapes transactionnelles de l'acquisition mensuelle (une transaction par fiche, ADR § 5) : chaque méthode est
 * {@code REQUIRES_NEW} pour qu'une collision d'unicité sur une fiche n'interrompe pas le lot.
 */
@Service
public class AcquisitionFicheService {

	private final SoldeCongeService soldeService;
	private final MouvementSoldeCongeRepository mouvementRepository;
	private final AcquisitionMoisRepository acquisitionMoisRepository;
	private final ReglagesCongeService reglages;

	public AcquisitionFicheService(SoldeCongeService soldeService, MouvementSoldeCongeRepository mouvementRepository,
			AcquisitionMoisRepository acquisitionMoisRepository, ReglagesCongeService reglages) {
		this.soldeService = soldeService;
		this.mouvementRepository = mouvementRepository;
		this.acquisitionMoisRepository = acquisitionMoisRepository;
		this.reglages = reglages;
	}

	/** Fige N au premier passage du mois (D-S07.c) puis le relit : la valeur figée vaut pour tout le mois. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public BigDecimal figerJoursDuMois(String mois) {
		acquisitionMoisRepository.figerSiAbsent(mois, reglages.joursParMois());
		return acquisitionMoisRepository.findById(mois).orElseThrow().getJoursParMois();
	}

	/** +N pour une fiche si le mois n'est pas déjà acquis ; {@code true} si un mouvement a été écrit. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean acquerir(UUID collaborateurId, String mois, BigDecimal jours) {
		SoldeConge solde = soldeService.verrouiller(collaborateurId);
		if (mouvementRepository.existsByCollaborateurIdAndMoisAcquisition(collaborateurId, mois)) {
			return false;
		}
		soldeService.enregistrer(solde, TypeMouvementSolde.ACQUISITION, jours, OrigineMouvementSolde.SYSTEME, null,
				null, null, mois, null, AuteurParametre.systeme());
		return true;
	}
}
