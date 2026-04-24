package com.hr.referentiel.service;

import com.hr.referentiel.dto.*;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DemandeAdministrativeRhService {

	private final DemandeAdministrativeRhRepository demandeAdministrativeRhRepository;
	private final CollaborateurRepository collaborateurRepository;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final DemandeAdministrativeValidationService validationService;

	public DemandeAdministrativeRhService(DemandeAdministrativeRhRepository demandeAdministrativeRhRepository,
			CollaborateurRepository collaborateurRepository,
			CollaborateurConnecteService collaborateurConnecteService,
			DemandeAdministrativeValidationService validationService) {
		this.demandeAdministrativeRhRepository = demandeAdministrativeRhRepository;
		this.collaborateurRepository = collaborateurRepository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.validationService = validationService;
	}

	@Transactional
	public DemandeAdministrativeRhResponse creer(DemandeAdministrativeRhCreationRequest req, Jwt jwt) {
		Collaborateur demandeur = collaborateurConnecteService.exigerCollaborateur(jwt);
		demandeur = collaborateurRepository.findDetailById(demandeur.getId())
				.orElseThrow(() -> new IllegalStateException("Collaborateur introuvable."));
		validationService.validerContenu(req.getTypeDemande(),
				req.getContenu() != null ? req.getContenu() : new HashMap<>());

		DemandeAdministrativeRh d = new DemandeAdministrativeRh();
		d.setTypeDemande(req.getTypeDemande());
		d.setDemandeur(demandeur);
		d.setContenu(new HashMap<>(req.getContenu()));
		DemandeAdministrativePeriodeHelper.appliquerPeriodeIndexee(d);
		if (demandeur.getSuperieur() != null) {
			d.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR);
		} else {
			d.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		}
		return toResponse(demandeAdministrativeRhRepository.save(d));
	}

	@Transactional(readOnly = true)
	public List<DemandeAdministrativeRhResponse> mesDemandes(Jwt jwt, TypeDemandeAdministrativeRh typeDemande,
			LocalDate couvreJour, StatutDemandeAdministrativeRh statutFiltre) {
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		List<DemandeAdministrativeRh> lignes;
		if (couvreJour != null) {
			lignes = demandeAdministrativeRhRepository.findByDemandeurCouvrantJour(c.getId(), couvreJour,
					typeDemande, statutFiltre);
		} else if (typeDemande == null) {
			lignes = demandeAdministrativeRhRepository.findByDemandeurIdOrderByCreeLeDesc(c.getId());
			lignes = filtrerParStatutSiBesoin(lignes, statutFiltre);
		} else {
			lignes = demandeAdministrativeRhRepository.findByDemandeurIdAndTypeDemandeOrderByCreeLeDesc(c.getId(),
					typeDemande);
			lignes = filtrerParStatutSiBesoin(lignes, statutFiltre);
		}
		return lignes.stream().map(this::toResponse).collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<DemandeAdministrativeRhResponse> listerToutPourRh(TypeDemandeAdministrativeRh typeDemande,
			LocalDate couvreJour, StatutDemandeAdministrativeRh statutFiltre) {
		List<DemandeAdministrativeRh> lignes;
		if (couvreJour != null) {
			lignes = demandeAdministrativeRhRepository.findPourRhCouvrantJour(couvreJour, typeDemande,
					statutFiltre);
		} else if (typeDemande == null) {
			lignes = demandeAdministrativeRhRepository.findAllPourRhOrderByCreeLeDesc();
			lignes = filtrerParStatutSiBesoin(lignes, statutFiltre);
		} else {
			lignes = demandeAdministrativeRhRepository.findAllPourRhByTypeDemandeOrderByCreeLeDesc(typeDemande);
			lignes = filtrerParStatutSiBesoin(lignes, statutFiltre);
		}
		return lignes.stream().map(this::toResponse).collect(Collectors.toList());
	}

	private static List<DemandeAdministrativeRh> filtrerParStatutSiBesoin(List<DemandeAdministrativeRh> lignes,
			StatutDemandeAdministrativeRh statutFiltre) {
		if (statutFiltre == null) {
			return lignes;
		}
		return lignes.stream().filter(d -> d.getStatut() == statutFiltre).collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public DemandeAdministrativeRhResponse obtenir(UUID id, Jwt jwt, boolean rh) {
		DemandeAdministrativeRh d = demandeAdministrativeRhRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Demande introuvable."));
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!d.getDemandeur().getId().equals(c.getId())) {
				throw new IllegalArgumentException("Accès refusé.");
			}
		}
		return toResponse(d);
	}

	@Transactional(readOnly = true)
	public DemandeAdministrativeSuiviResponse suivi(UUID id, Jwt jwt, boolean rh) {
		DemandeAdministrativeRh d = demandeAdministrativeRhRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Demande introuvable."));
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!d.getDemandeur().getId().equals(c.getId())) {
				throw new IllegalArgumentException("Accès refusé.");
			}
		}
		Collaborateur demandeur = collaborateurRepository.findDetailById(d.getDemandeur().getId()).orElseThrow();
		boolean etapeSuperieurRequise = demandeur.getSuperieur() != null;
		return new DemandeAdministrativeSuiviResponse(d.getId(), d.getTypeDemande(), d.getStatut(),
				etapeSuperieurRequise, etapesAdministratif(d.getStatut(), etapeSuperieurRequise));
	}

	@Transactional
	public DemandeAdministrativeRhResponse validerSuperieur(UUID id, Jwt jwt) {
		DemandeAdministrativeRh d = chargerAvecDemandeur(id);
		if (d.getStatut() != StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR) {
			throw new IllegalArgumentException("Cette demande n'est pas en attente du supérieur.");
		}
		Collaborateur sup = collaborateurConnecteService.exigerCollaborateur(jwt);
		Collaborateur demandeur = collaborateurRepository.findDetailById(d.getDemandeur().getId()).orElseThrow();
		if (demandeur.getSuperieur() == null || !demandeur.getSuperieur().getId().equals(sup.getId())) {
			throw new IllegalArgumentException("Seul le supérieur hiérarchique peut valider cette étape.");
		}
		d.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		return toResponse(demandeAdministrativeRhRepository.save(d));
	}

	@Transactional
	public DemandeAdministrativeRhResponse validerRrh(UUID id) {
		DemandeAdministrativeRh d = chargerAvecDemandeur(id);
		if (d.getStatut() != StatutDemandeAdministrativeRh.EN_VALIDATION_RRH) {
			throw new IllegalArgumentException("Cette demande n'est pas en attente du RRH.");
		}
		d.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		return toResponse(demandeAdministrativeRhRepository.save(d));
	}

	@Transactional
	public DemandeAdministrativeRhResponse refuserSuperieur(UUID id, Jwt jwt, DemandeRefusRequest req) {
		DemandeAdministrativeRh d = chargerAvecDemandeur(id);
		if (d.getStatut() != StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR) {
			throw new IllegalArgumentException("Cette demande n'est pas en attente du supérieur.");
		}
		Collaborateur sup = collaborateurConnecteService.exigerCollaborateur(jwt);
		Collaborateur demandeur = collaborateurRepository.findDetailById(d.getDemandeur().getId()).orElseThrow();
		if (demandeur.getSuperieur() == null || !demandeur.getSuperieur().getId().equals(sup.getId())) {
			throw new IllegalArgumentException("Seul le supérieur hiérarchique peut refuser à cette étape.");
		}
		d.setStatut(StatutDemandeAdministrativeRh.REFUSEE);
		d.setMotifRefus(req.getMotifRefus().trim());
		return toResponse(demandeAdministrativeRhRepository.save(d));
	}

	@Transactional
	public DemandeAdministrativeRhResponse refuserRrh(UUID id, DemandeRefusRequest req) {
		DemandeAdministrativeRh d = chargerAvecDemandeur(id);
		if (d.getStatut() != StatutDemandeAdministrativeRh.EN_VALIDATION_RRH) {
			throw new IllegalArgumentException("Cette demande n'est pas en attente du RRH.");
		}
		d.setStatut(StatutDemandeAdministrativeRh.REFUSEE);
		d.setMotifRefus(req.getMotifRefus().trim());
		return toResponse(demandeAdministrativeRhRepository.save(d));
	}

	private DemandeAdministrativeRh chargerAvecDemandeur(UUID id) {
		DemandeAdministrativeRh d = demandeAdministrativeRhRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Demande introuvable."));
		d.getDemandeur().getId();
		return d;
	}

	private static List<WorkflowEtapeResponse> etapesAdministratif(StatutDemandeAdministrativeRh s,
			boolean avecSuperieur) {
		List<WorkflowEtapeResponse> etapes = new ArrayList<>();
		etapes.add(new WorkflowEtapeResponse("DEPOT", "Demande enregistrée", true, false));

		if (avecSuperieur) {
			boolean supEnCours = s == StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR;
			boolean supTerminee = s != StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR
					&& s != StatutDemandeAdministrativeRh.SOUMISE;
			etapes.add(new WorkflowEtapeResponse("SUPERIEUR", "Validation du responsable hiérarchique", supTerminee,
					supEnCours));
		}

		boolean rrhEnCours = s == StatutDemandeAdministrativeRh.EN_VALIDATION_RRH;
		boolean rrhTerminee = s == StatutDemandeAdministrativeRh.APPROUVEE
				|| s == StatutDemandeAdministrativeRh.REFUSEE || s == StatutDemandeAdministrativeRh.ANNULEE;
		String libelleRrh = avecSuperieur && s == StatutDemandeAdministrativeRh.REFUSEE
				? "Validation RRH (non requise si refus en amont — voir motif)"
				: "Validation RRH";
		boolean rrhMarqueTermine = rrhTerminee || (avecSuperieur && s == StatutDemandeAdministrativeRh.REFUSEE);
		etapes.add(new WorkflowEtapeResponse("RRH", libelleRrh, rrhMarqueTermine, rrhEnCours));

		String libelleFin = switch (s) {
			case APPROUVEE -> "Demande approuvée";
			case REFUSEE -> "Demande refusée";
			case ANNULEE -> "Demande annulée";
			default -> "Clôture";
		};
		boolean cloture = s == StatutDemandeAdministrativeRh.APPROUVEE || s == StatutDemandeAdministrativeRh.REFUSEE
				|| s == StatutDemandeAdministrativeRh.ANNULEE;
		etapes.add(new WorkflowEtapeResponse("CLOTURE", libelleFin, cloture, false));

		return etapes;
	}

	private DemandeAdministrativeRhResponse toResponse(DemandeAdministrativeRh d) {
		return new DemandeAdministrativeRhResponse(
				d.getId(),
				d.getTypeDemande(),
				d.getDemandeur().getId(),
				d.getStatut(),
				d.getContenu(),
				d.getPeriodeDebut(),
				d.getPeriodeFin(),
				d.getMotifRefus(),
				d.getCreeLe(),
				d.getModifieLe());
	}
}
