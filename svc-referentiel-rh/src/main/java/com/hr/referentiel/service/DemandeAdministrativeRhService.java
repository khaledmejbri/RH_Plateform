package com.hr.referentiel.service;

import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.*;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CDC v2 §M01 :
 * - Workflow : collaborateur → RO (Responsable Opérationnel de son unité) → RRH.
 *   Si l'unité n'a pas de RO → passage direct en EN_VALIDATION_RRH.
 * - Le RO validant/refusant est celui dont profil_acces = RO dans l'unité du demandeur.
 * - Annulation possible par le demandeur uniquement si statut = SOUMISE ou EN_VALIDATION_SUPERIEUR.
 * - Refus avec motif obligatoire à chaque étape.
 */
@Service
public class DemandeAdministrativeRhService {

	private final DemandeAdministrativeRhRepository demandeRepo;
	private final CollaborateurRepository collaborateurRepository;
	private final UniteOrganisationRepository uniteRepo;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final DemandeAdministrativeValidationService validationService;

	public DemandeAdministrativeRhService(
			DemandeAdministrativeRhRepository demandeRepo,
			CollaborateurRepository collaborateurRepository,
			UniteOrganisationRepository uniteRepo,
			CollaborateurConnecteService collaborateurConnecteService,
			DemandeAdministrativeValidationService validationService) {
		this.demandeRepo = demandeRepo;
		this.collaborateurRepository = collaborateurRepository;
		this.uniteRepo = uniteRepo;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.validationService = validationService;
	}

	// ─── Création ─────────────────────────────────────────────────────────────

	@Transactional
	public DemandeAdministrativeRhResponse creer(DemandeAdministrativeRhCreationRequest req, Jwt jwt) {
		Collaborateur demandeur = exigerCollaborateurDetail(jwt);
		validationService.validerContenu(req.getTypeDemande(),
				req.getContenu() != null ? req.getContenu() : new HashMap<>());

		DemandeAdministrativeRh d = new DemandeAdministrativeRh();
		d.setTypeDemande(req.getTypeDemande());
		d.setDemandeur(demandeur);
		d.setContenu(new HashMap<>(req.getContenu()));
		DemandeAdministrativePeriodeHelper.appliquerPeriodeIndexee(d);

		// CDC §M01 : RO de l'unité du demandeur est le valideur intermédiaire
		boolean aUnRo = roDeUnite(demandeur.getUnite()) != null;
		d.setStatut(aUnRo
				? StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR
				: StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);

		return toResponse(demandeRepo.save(d));
	}

	// ─── Lecture ──────────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<DemandeAdministrativeRhResponse> mesDemandes(Jwt jwt,
			TypeDemandeAdministrativeRh type, LocalDate couvreJour,
			StatutDemandeAdministrativeRh statut) {
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		List<DemandeAdministrativeRh> lignes;
		if (couvreJour != null) {
			lignes = demandeRepo.findByDemandeurCouvrantJour(c.getId(), couvreJour, type, statut);
		} else if (type == null) {
			lignes = demandeRepo.findByDemandeurIdOrderByCreeLeDesc(c.getId());
			lignes = filtrerStatut(lignes, statut);
		} else {
			lignes = demandeRepo.findByDemandeurIdAndTypeDemandeOrderByCreeLeDesc(c.getId(), type);
			lignes = filtrerStatut(lignes, statut);
		}
		return lignes.stream().map(this::toResponse).collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<DemandeAdministrativeRhResponse> demandesEnAttenteRo(Jwt jwt) {
		Collaborateur ro = collaborateurConnecteService.exigerCollaborateur(jwt);
		// Trouver l'unité dont ce collaborateur est RO
		List<Collaborateur> membres = collaborateurRepository.findByUniteId(ro.getUnite().getId());
		// Retourner les demandes EN_VALIDATION_SUPERIEUR de ses membres
		return membres.stream()
				.filter(m -> !m.getId().equals(ro.getId()))
				.flatMap(m -> demandeRepo
						.findByDemandeurIdOrderByCreeLeDesc(m.getId()).stream()
						.filter(d -> d.getStatut() == StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR))
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<DemandeAdministrativeRhResponse> listerToutPourRh(
			TypeDemandeAdministrativeRh type, LocalDate couvreJour,
			StatutDemandeAdministrativeRh statut) {
		List<DemandeAdministrativeRh> lignes;
		if (couvreJour != null) {
			lignes = demandeRepo.findPourRhCouvrantJour(couvreJour, type, statut);
		} else if (type == null) {
			lignes = demandeRepo.findAllPourRhOrderByCreeLeDesc();
			lignes = filtrerStatut(lignes, statut);
		} else {
			lignes = demandeRepo.findAllPourRhByTypeDemandeOrderByCreeLeDesc(type);
			lignes = filtrerStatut(lignes, statut);
		}
		return lignes.stream().map(this::toResponse).collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public DemandeAdministrativeRhResponse obtenir(UUID id, Jwt jwt, boolean rh) {
		DemandeAdministrativeRh d = charger(id);
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
		DemandeAdministrativeRh d = charger(id);
		if (!rh) {
			Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
			if (!d.getDemandeur().getId().equals(c.getId())) {
				throw new IllegalArgumentException("Accès refusé.");
			}
		}
		Collaborateur demandeur = collaborateurRepository.findDetailById(d.getDemandeur().getId()).orElseThrow();
		boolean avecRo = roDeUnite(demandeur.getUnite()) != null;
		return new DemandeAdministrativeSuiviResponse(d.getId(), d.getTypeDemande(), d.getStatut(),
				avecRo, etapesAdministratif(d.getStatut(), avecRo));
	}

	// ─── Validation RO ────────────────────────────────────────────────────────

	@Transactional
	public DemandeAdministrativeRhResponse validerSuperieur(UUID id, Jwt jwt) {
		DemandeAdministrativeRh d = charger(id);
		verifierStatut(d, StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR,
				"Cette demande n'est pas en attente de validation RO.");
		verifierEstRoDuDemandeur(jwt, d);
		d.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		return toResponse(demandeRepo.save(d));
	}

	@Transactional
	public DemandeAdministrativeRhResponse refuserSuperieur(UUID id, Jwt jwt, DemandeRefusRequest req) {
		DemandeAdministrativeRh d = charger(id);
		verifierStatut(d, StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR,
				"Cette demande n'est pas en attente de validation RO.");
		verifierEstRoDuDemandeur(jwt, d);
		d.setStatut(StatutDemandeAdministrativeRh.REFUSEE);
		d.setMotifRefus(req.getMotifRefus().trim());
		return toResponse(demandeRepo.save(d));
	}

	// ─── Validation RRH ───────────────────────────────────────────────────────

	@Transactional
	public DemandeAdministrativeRhResponse validerRrh(UUID id) {
		DemandeAdministrativeRh d = charger(id);
		verifierStatut(d, StatutDemandeAdministrativeRh.EN_VALIDATION_RRH,
				"Cette demande n'est pas en attente de validation RRH.");
		d.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		return toResponse(demandeRepo.save(d));
	}

	@Transactional
	public DemandeAdministrativeRhResponse refuserRrh(UUID id, DemandeRefusRequest req) {
		DemandeAdministrativeRh d = charger(id);
		verifierStatut(d, StatutDemandeAdministrativeRh.EN_VALIDATION_RRH,
				"Cette demande n'est pas en attente de validation RRH.");
		d.setStatut(StatutDemandeAdministrativeRh.REFUSEE);
		d.setMotifRefus(req.getMotifRefus().trim());
		return toResponse(demandeRepo.save(d));
	}

	// ─── Annulation par le demandeur ──────────────────────────────────────────

	/**
	 * CDC §M01 : un collaborateur peut annuler sa propre demande si elle est encore
	 * SOUMISE ou EN_VALIDATION_SUPERIEUR (pas encore traitée par le RRH).
	 */
	@Transactional
	public DemandeAdministrativeRhResponse annuler(UUID id, Jwt jwt) {
		DemandeAdministrativeRh d = charger(id);
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		if (!d.getDemandeur().getId().equals(c.getId())) {
			throw new IllegalArgumentException("Seul le demandeur peut annuler sa demande.");
		}
		if (d.getStatut() != StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR
				&& d.getStatut() != StatutDemandeAdministrativeRh.EN_VALIDATION_RRH) {
			throw new IllegalArgumentException(
					"Impossible d'annuler une demande au statut " + d.getStatut()
					+ ". Annulation possible uniquement si la demande n'a pas encore été traitée par le RRH.");
		}
		d.setStatut(StatutDemandeAdministrativeRh.ANNULEE);
		return toResponse(demandeRepo.save(d));
	}

	// ─── Helpers privés ───────────────────────────────────────────────────────

	private Collaborateur exigerCollaborateurDetail(Jwt jwt) {
		Collaborateur c = collaborateurConnecteService.exigerCollaborateur(jwt);
		return collaborateurRepository.findDetailById(c.getId())
				.orElseThrow(() -> new IllegalStateException("Collaborateur introuvable."));
	}

	private DemandeAdministrativeRh charger(UUID id) {
		return demandeRepo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + id));
	}

	private static void verifierStatut(DemandeAdministrativeRh d,
			StatutDemandeAdministrativeRh attendu, String msg) {
		if (d.getStatut() != attendu) {
			throw new IllegalArgumentException(msg + " Statut actuel : " + d.getStatut());
		}
	}

	/**
	 * Vérifie que le collaborateur connecté est bien le RO de l'unité du demandeur.
	 * CDC §M01 : la validation intermédiaire est faite par le RO (profil_acces=RO) de l'unité.
	 */
	private void verifierEstRoDuDemandeur(Jwt jwt, DemandeAdministrativeRh d) {
		Collaborateur connecte = collaborateurConnecteService.exigerCollaborateur(jwt);
		Collaborateur demandeur = collaborateurRepository.findDetailById(d.getDemandeur().getId()).orElseThrow();
		Collaborateur ro = roDeUnite(demandeur.getUnite());
		if (ro == null || !ro.getId().equals(connecte.getId())) {
			throw new IllegalArgumentException(
					"Seul le Responsable Opérationnel (RO) de l'unité du demandeur peut valider cette étape.");
		}
	}

	/**
	 * Retourne le RO (profil_acces = RO) de l'unité, ou null si aucun.
	 */
	private Collaborateur roDeUnite(UniteOrganisation unite) {
		if (unite == null) return null;
		return collaborateurRepository.findByUniteId(unite.getId()).stream()
				.filter(c -> "RO".equals(c.getProfilAcces()) && "ACTIF".equalsIgnoreCase(c.getStatut()))
				.findFirst()
				.orElse(null);
	}

	private static List<DemandeAdministrativeRh> filtrerStatut(
			List<DemandeAdministrativeRh> lignes, StatutDemandeAdministrativeRh s) {
		return s == null ? lignes
				: lignes.stream().filter(d -> d.getStatut() == s).collect(Collectors.toList());
	}

	private static List<WorkflowEtapeResponse> etapesAdministratif(
			StatutDemandeAdministrativeRh s, boolean avecRo) {
		List<WorkflowEtapeResponse> etapes = new ArrayList<>();
		etapes.add(new WorkflowEtapeResponse("DEPOT", "Demande enregistrée", true, false));

		if (avecRo) {
			boolean enCours  = s == StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR;
			boolean terminee = s != StatutDemandeAdministrativeRh.SOUMISE && s != StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR;
			etapes.add(new WorkflowEtapeResponse("RO", "Validation du Responsable Opérationnel", terminee, enCours));
		}

		boolean rrhEnCours  = s == StatutDemandeAdministrativeRh.EN_VALIDATION_RRH;
		boolean rrhTerminee = s == StatutDemandeAdministrativeRh.APPROUVEE
				|| s == StatutDemandeAdministrativeRh.REFUSEE
				|| s == StatutDemandeAdministrativeRh.ANNULEE;
		etapes.add(new WorkflowEtapeResponse("RRH", "Validation RRH", rrhTerminee, rrhEnCours));

		String libelleFin = switch (s) {
			case APPROUVEE -> "Demande approuvée ✓";
			case REFUSEE   -> "Demande refusée";
			case ANNULEE   -> "Demande annulée par le demandeur";
			default        -> "Clôture";
		};
		boolean cloture = s == StatutDemandeAdministrativeRh.APPROUVEE
				|| s == StatutDemandeAdministrativeRh.REFUSEE
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
