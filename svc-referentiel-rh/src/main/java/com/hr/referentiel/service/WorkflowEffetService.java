package com.hr.referentiel.service;

import com.hr.referentiel.domain.ActionWorkflowAdministratif;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.WorkflowEffetDtos.ActeurEffet;
import com.hr.referentiel.dto.WorkflowEffetDtos.RequeteEffet;
import com.hr.referentiel.entity.DemandeAdminWorkflowHistory;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.RhWorkflowEffet;
import com.hr.referentiel.repository.DemandeAdminWorkflowHistoryRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.RhWorkflowEffetRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Exécution des effets métier demandés par {@code svc-workflow} pour M01 (§ 9.6.5, D-W5-07).
 * Ordre des verrous respecté : demande ({@code findByIdForUpdate}) puis solde ({@code CongeDecompteService}).
 * Aucune notification métier n'est publiée par le référentiel pour une demande {@code MOTEUR}.
 */
@Service
public class WorkflowEffetService {

	public static final String MODE_CONTROLER = "CONTROLER";
	public static final String MODE_APPLIQUER = "APPLIQUER";
	public static final String MOTIF_EXPIRATION = "Demande expirée sans décision dans le délai";

	private static final Set<String> TYPES_GERES = Set.of(
			TypeDemandeAdministrativeRh.CONGE.name(),
			TypeDemandeAdministrativeRh.AUTORISATION_SORTIE.name(),
			TypeDemandeAdministrativeRh.ORDRE_MISSION.name());

	private final DemandeAdministrativeRhRepository demandeRepo;
	private final DemandeAdminWorkflowHistoryRepository historyRepo;
	private final RhWorkflowEffetRepository effetRepo;
	private final CongeDecompteService congeDecompteService;

	public WorkflowEffetService(
			DemandeAdministrativeRhRepository demandeRepo,
			DemandeAdminWorkflowHistoryRepository historyRepo,
			RhWorkflowEffetRepository effetRepo,
			CongeDecompteService congeDecompteService) {
		this.demandeRepo = demandeRepo;
		this.historyRepo = historyRepo;
		this.effetRepo = effetRepo;
		this.congeDecompteService = congeDecompteService;
	}

	public record ResultatEffet(int statutHttp, Map<String, Object> corps) {
	}

	/** Point d'entrée appelé par le contrôleur interne {@code POST /internal/referentiel/v1/workflow/effets}. */
	@Transactional(readOnly = true)
	public ResultatEffet controler(RequeteEffet req) {
		verifierTypeGere(req.objetType());
		DemandeAdministrativeRh d = demandeRepo.findById(req.objetId())
				.orElseThrow(() -> ReferentielMetierException.notFound(
						"DEMANDE_INTROUVABLE", "Demande administrative introuvable."));
		if (!WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())) {
			return conflitDejaDecide();
		}
		if (d.getStatut() != StatutDemandeAdministrativeRh.EN_COURS
				&& d.getStatut() != StatutDemandeAdministrativeRh.A_COMPLETER) {
			return conflitDejaDecide();
		}
		if ("APPROUVER".equals(req.effet()) && CongeDecompteService.estAnnuel(d)) {
			congeDecompteService.controlerValidationLectureSeule(d);
		}
		return new ResultatEffet(200, Map.of("ok", true));
	}

	/** Application idempotente d'un effet métier dans une seule transaction (D-W5-07). */
	@Transactional
	public ResultatEffet appliquer(RequeteEffet req) {
		verifierTypeGere(req.objetType());
		String cle = req.cleIdempotence();
		if (cle == null || cle.isBlank()) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE", "La clé d'idempotence est obligatoire.");
		}
		Optional<RhWorkflowEffet> existant = effetRepo.findById(cle);
		if (existant.isPresent()) {
			RhWorkflowEffet e = existant.get();
			return new ResultatEffet(e.getStatutHttp(), e.getReponse());
		}

		DemandeAdministrativeRh d = demandeRepo.findByIdForUpdate(req.objetId())
				.orElseThrow(() -> ReferentielMetierException.notFound(
						"DEMANDE_INTROUVABLE", "Demande administrative introuvable."));

		// Deux appels concurrents avec la même clé : le second attend le verrou de la demande, puis doit
		// renvoyer la réponse mémorisée par le premier, et non un 409 « déjà décidée ».
		Optional<RhWorkflowEffet> memorise = effetRepo.findById(cle);
		if (memorise.isPresent()) {
			RhWorkflowEffet e = memorise.get();
			return new ResultatEffet(e.getStatutHttp(), e.getReponse());
		}

		if (!WorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				|| (d.getStatut() != StatutDemandeAdministrativeRh.EN_COURS
				&& d.getStatut() != StatutDemandeAdministrativeRh.A_COMPLETER)) {
			ResultatEffet conflit = conflitDejaDecide();
			effetRepo.save(new RhWorkflowEffet(
					cle, d.getId(), req.effet(), "OBJET_DEJA_DECIDE", conflit.statutHttp(), conflit.corps()));
			return conflit;
		}

		ActeurEffet acteur = req.acteur();
		String nomActeur = formaterNomActeur(acteur);
		UUID acteurId = acteur != null
				? (acteur.collaborateurId() != null ? acteur.collaborateurId() : acteur.compteId())
				: null;
		String commentaire = req.commentaire() != null ? req.commentaire().trim() : null;

		ActionWorkflowAdministratif action;
		String commentaireHist;
		switch (req.effet()) {
			case "APPROUVER" -> {
				AuteurParametre auteur = new AuteurParametre(
						acteur != null ? acteur.compteId() : null, nomActeur);
				boolean anterieure = DemandeApprobationSupport.approuverSousVerrou(
						d, auteur, congeDecompteService, demandeRepo);
				action = ActionWorkflowAdministratif.APPROBATION_RRH;
				commentaireHist = anterieure
						? "Approuvée. " + CongeDecompteService.MENTION_ANTERIEURE
						: (commentaire != null && !commentaire.isBlank() ? commentaire : "Approuvée");
			}
			case "REFUSER" -> {
				d.setStatut(StatutDemandeAdministrativeRh.REFUSEE);
				d.setMotifRefus(commentaire);
				demandeRepo.save(d);
				action = ActionWorkflowAdministratif.REFUS_RRH;
				commentaireHist = commentaire != null && !commentaire.isBlank()
						? "Refusée : " + commentaire
						: "Refusée";
			}
			case "METTRE_A_COMPLETER" -> {
				d.setStatut(StatutDemandeAdministrativeRh.A_COMPLETER);
				demandeRepo.save(d);
				action = ActionWorkflowAdministratif.DEMANDE_COMPLEMENT;
				commentaireHist = commentaire != null && !commentaire.isBlank()
						? commentaire
						: "Complément demandé";
			}
			case "ANNULER" -> {
				d.setStatut(StatutDemandeAdministrativeRh.ANNULEE);
				if (commentaire != null && !commentaire.isBlank()) {
					d.setMotifAnnulation(commentaire);
				}
				demandeRepo.save(d);
				action = ActionWorkflowAdministratif.ANNULATION_DEMANDEUR;
				commentaireHist = commentaire != null && !commentaire.isBlank() ? commentaire : "Annulée";
			}
			case "EXPIRER" -> {
				d.setStatut(StatutDemandeAdministrativeRh.ANNULEE);
				d.setMotifAnnulation(MOTIF_EXPIRATION);
				demandeRepo.save(d);
				action = ActionWorkflowAdministratif.CLOTUREE;
				commentaireHist = MOTIF_EXPIRATION;
			}
			default -> throw ReferentielMetierException.unprocessable(
					"EFFET_INCONNU", "Effet de workflow inconnu : " + req.effet());
		}

		DemandeAdminWorkflowHistory h = new DemandeAdminWorkflowHistory();
		h.setDemandeAdministrative(d);
		h.setAction(action);
		h.setActeurIdentifiant(acteurId);
		h.setActeurNom(nomActeur);
		h.setCommentaire(commentaireHist);
		historyRepo.save(h);

		Map<String, Object> corps = Map.of("statut", d.getStatut().name());
		effetRepo.save(new RhWorkflowEffet(cle, d.getId(), req.effet(), d.getStatut().name(), 200, corps));
		return new ResultatEffet(200, corps);
	}

	private static void verifierTypeGere(String objetType) {
		if (objetType == null || !TYPES_GERES.contains(objetType)) {
			throw ReferentielMetierException.unprocessable(
					"TYPE_OBJET_NON_GERE", "Type d'objet non géré par les effets M01 : " + objetType);
		}
	}

	private static ResultatEffet conflitDejaDecide() {
		String msg = "Cette demande est déjà décidée ou ne relève pas du moteur de workflow.";
		Map<String, Object> corps = new LinkedHashMap<>();
		corps.put("code", "OBJET_DEJA_DECIDE");
		corps.put("message", msg);
		corps.put("erreur", msg);
		return new ResultatEffet(409, corps);
	}

	static String formaterNomActeur(ActeurEffet acteur) {
		if (acteur == null || acteur.nom() == null || acteur.nom().isBlank()) {
			return AuteurParametre.NOM_SYSTEME;
		}
		String nom = acteur.nom().trim();
		if (acteur.pourNom() != null && !acteur.pourNom().isBlank()) {
			return nom + " (pour le compte de " + acteur.pourNom().trim() + ")";
		}
		return nom;
	}
}
