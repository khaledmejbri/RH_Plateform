package com.hr.presence.service;

import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.dto.PresenceWorkflowEffetDtos.ActeurEffet;
import com.hr.presence.dto.PresenceWorkflowEffetDtos.RequeteEffet;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceWorkflowEffet;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.repository.PresenceWorkflowEffetRepository;
import com.hr.presence.web.PresenceMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Exécution des effets métier demandés par {@code svc-workflow} sur {@code svc-presence} (W6).
 * <p>Règles critiques :
 * <ul>
 *     <li>Ne jamais lever d'HTTP 400 (toujours 422 pour un rejet métier/validation, 404 ou 409) : {@code EffetClient}
 *     traite 400 comme {@code Incertain} et boucle en réessais.</li>
 *     <li>Idempotence sur {@code presence_workflow_effet} : 200 et 409 mémorisés, 422 jamais mémorisé.</li>
 *     <li>Aucune notification propriétaire publiée en mode {@code MOTEUR}.</li>
 * </ul>
 */
@Service
public class PresenceWorkflowEffetService {

	public static final String MODE_CONTROLER = "CONTROLER";
	public static final String MODE_APPLIQUER = "APPLIQUER";
	public static final String NOM_SYSTEME = "Système";

	private static final int COMMENTAIRE_MIN = 10;
	private static final int TEXTE_MAX = 500;
	private static final Set<String> TYPES_GERES = Set.of(
			PresenceWorkflowVariablesBuilder.TYPE_POINTAGE_MANUEL,
			PresenceWorkflowVariablesBuilder.TYPE_HEURES_SUP);

	private final PresenceDemandeManuelleRepository demandeManuelleRepo;
	private final PresencePointageRepository pointageRepo;
	private final PresenceSiteRepository siteRepo;
	private final FenetreHoraireService fenetreHoraire;
	private final PresenceHeuresSupDemandeRepository heuresSupRepo;
	private final PresenceHeuresSupJourRepository heuresSupJourRepo;
	private final PresenceWorkflowEffetRepository effetRepo;
	private final Clock clock;

	public PresenceWorkflowEffetService(
			PresenceDemandeManuelleRepository demandeManuelleRepo,
			PresencePointageRepository pointageRepo,
			PresenceSiteRepository siteRepo,
			FenetreHoraireService fenetreHoraire,
			PresenceHeuresSupDemandeRepository heuresSupRepo,
			PresenceHeuresSupJourRepository heuresSupJourRepo,
			PresenceWorkflowEffetRepository effetRepo,
			Clock clock) {
		this.demandeManuelleRepo = demandeManuelleRepo;
		this.pointageRepo = pointageRepo;
		this.siteRepo = siteRepo;
		this.fenetreHoraire = fenetreHoraire;
		this.heuresSupRepo = heuresSupRepo;
		this.heuresSupJourRepo = heuresSupJourRepo;
		this.effetRepo = effetRepo;
		this.clock = clock;
	}

	public record ResultatEffet(int statutHttp, Map<String, Object> corps) {
	}

	@Transactional(readOnly = true)
	public ResultatEffet controler(RequeteEffet req) {
		verifierRequeteDeBase(req, false);
		if (PresenceWorkflowVariablesBuilder.TYPE_POINTAGE_MANUEL.equals(req.objetType())) {
			PresenceDemandeManuelle d = demandeManuelleRepo.findById(req.objetId())
					.orElseThrow(() -> PresenceMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable"));
			return controlerPointageManuel(d, req);
		}
		PresenceHeuresSupDemande d = heuresSupRepo.findById(req.objetId())
				.orElseThrow(() -> PresenceMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable"));
		return controlerHeuresSup(d, req);
	}

	@Transactional(noRollbackFor = PresenceMetierException.class)
	public ResultatEffet appliquer(RequeteEffet req) {
		verifierRequeteDeBase(req, true);
		String cle = req.cleIdempotence().trim();
		Optional<PresenceWorkflowEffet> existant = effetRepo.findByCleIdempotence(cle);
		if (existant.isPresent()) {
			PresenceWorkflowEffet e = existant.get();
			return new ResultatEffet(e.getStatutHttp(), e.getReponse());
		}

		if (PresenceWorkflowVariablesBuilder.TYPE_POINTAGE_MANUEL.equals(req.objetType())) {
			return appliquerPointageManuel(req, cle);
		}
		return appliquerHeuresSup(req, cle);
	}

	// ---- Pointage manuel -----------------------------------------------------------------------------------------

	private ResultatEffet controlerPointageManuel(PresenceDemandeManuelle d, RequeteEffet req) {
		if (!estPointageManuelActif(d)) {
			return conflitDejaDecide();
		}
		switch (req.effet()) {
			case "APPROUVER" -> {
				validerCommentairePointageManuel(d, req.commentaire());
				PointageManuelSupport.controlerFenetre(
						siteRepo, fenetreHoraire, d.getSiteId(), d.getCollaborateurId(), d.getHeureDeclaree(), d.getType());
			}
			case "REFUSER" -> validerMotifObligatoire(req.commentaire());
			case "EXPIRER" -> {
				// Aucun contrôle complémentaire requis
			}
			default -> throw PresenceMetierException.unprocessable(
					"EFFET_NON_SUPPORTE", "Effet non supporté pour POINTAGE_MANUEL : " + req.effet());
		}
		return new ResultatEffet(200, Map.of("ok", true));
	}

	private ResultatEffet appliquerPointageManuel(RequeteEffet req, String cle) {
		PresenceDemandeManuelle d = demandeManuelleRepo.findByIdPourDecision(req.objetId())
				.orElseThrow(() -> PresenceMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable"));
		Optional<PresenceWorkflowEffet> memorise = effetRepo.findByCleIdempotence(cle);
		if (memorise.isPresent()) {
			PresenceWorkflowEffet e = memorise.get();
			return new ResultatEffet(e.getStatutHttp(), e.getReponse());
		}
		if (!estPointageManuelActif(d)) {
			ResultatEffet conflit = conflitDejaDecide();
			memoriserEffet(cle, req, "OBJET_DEJA_DECIDE", conflit);
			return conflit;
		}

		Instant maintenant = clock.instant();
		String nomActeur = formaterNomActeur(req.acteur());
		switch (req.effet()) {
			case "APPROUVER" -> {
				String note = validerCommentairePointageManuel(d, req.commentaire());
				FenetreHoraireService.DansFenetre dansFenetre = PointageManuelSupport.controlerFenetre(
						siteRepo, fenetreHoraire, d.getSiteId(), d.getCollaborateurId(), d.getHeureDeclaree(), d.getType());
				PresencePointage pointage = PointageManuelSupport.creerPointageValide(d, dansFenetre, pointageRepo);
				d.setStatut(StatutDemandeManuelle.VALIDEE);
				d.setDecidePar(nomActeur);
				d.setDecideLe(maintenant);
				d.setPointageId(pointage.getId());
				d.setCommentaireValideur(note);
				d.setDelaiDecisionMinutes(minutesDepuisTentative(d, maintenant));
			}
			case "REFUSER" -> {
				String motif = validerMotifObligatoire(req.commentaire());
				d.setStatut(StatutDemandeManuelle.REFUSEE);
				d.setDecidePar(nomActeur);
				d.setDecideLe(maintenant);
				d.setMotifRefus(motif);
				d.setDelaiDecisionMinutes(minutesDepuisTentative(d, maintenant));
			}
			case "EXPIRER" -> d.setStatut(StatutDemandeManuelle.EXPIREE);
			default -> throw PresenceMetierException.unprocessable(
					"EFFET_NON_SUPPORTE", "Effet non supporté pour POINTAGE_MANUEL : " + req.effet());
		}
		d.setEtapeCouranteLibelle(null);
		d.setEcheanceLe(null);
		PresenceDemandeManuelle saved = demandeManuelleRepo.save(d);

		Map<String, Object> corps = new LinkedHashMap<>();
		corps.put("statut", saved.getStatut().name());
		if (saved.getPointageId() != null) {
			corps.put("pointage_id", saved.getPointageId());
		}
		ResultatEffet res = new ResultatEffet(200, corps);
		memoriserEffet(cle, req, saved.getStatut().name(), res);
		return res;
	}

	private static boolean estPointageManuelActif(PresenceDemandeManuelle d) {
		return PresenceWorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())
				&& d.getStatut() == StatutDemandeManuelle.EN_COURS;
	}

	private static String validerCommentairePointageManuel(PresenceDemandeManuelle d, String commentaire) {
		String c = commentaire == null ? "" : commentaire.trim();
		if (c.isEmpty()) {
			if (!d.getSignaux().isEmpty()) {
				throw PresenceMetierException.unprocessable("COMMENTAIRE_OBLIGATOIRE",
						"Un commentaire est obligatoire pour valider une demande signalée.");
			}
			return null;
		}
		if (c.length() < COMMENTAIRE_MIN) {
			throw PresenceMetierException.unprocessable("COMMENTAIRE_TROP_COURT",
					"Le commentaire doit faire au moins " + COMMENTAIRE_MIN + " caractères.");
		}
		if (c.length() > TEXTE_MAX) {
			throw PresenceMetierException.unprocessable("COMMENTAIRE_TROP_LONG",
					"Le commentaire ne peut pas dépasser " + TEXTE_MAX + " caractères.");
		}
		return c;
	}

	private static long minutesDepuisTentative(PresenceDemandeManuelle d, Instant maintenant) {
		if (d.getHeureDeclaree() == null) {
			return 0;
		}
		return Math.max(0, Duration.between(d.getHeureDeclaree(), maintenant).toMinutes());
	}

	// ---- Heures supplémentaires ------------------------------------------------------------------------------------

	private ResultatEffet controlerHeuresSup(PresenceHeuresSupDemande d, RequeteEffet req) {
		if (!PresenceWorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())) {
			return conflitDejaDecide();
		}
		Instant maintenant = clock.instant();
		switch (req.effet()) {
			case "APPROUVER" -> {
				if (d.getStatut() != StatutHeuresSup.EN_COURS) {
					return conflitDejaDecide();
				}
				validerCommentaireOptionnel(req.commentaire(), "COMMENTAIRE_TROP_LONG");
			}
			case "REFUSER" -> {
				if (d.getStatut() != StatutHeuresSup.EN_COURS) {
					return conflitDejaDecide();
				}
				validerMotifObligatoire(req.commentaire());
			}
			case "EXPIRER" -> {
				if (d.getStatut() != StatutHeuresSup.EN_COURS) {
					return conflitDejaDecide();
				}
			}
			case "ANNULER" -> {
				if (!estAnnulableHeuresSup(d, maintenant)) {
					if (d.getStatut() == StatutHeuresSup.VALIDEE) {
						throw PresenceMetierException.unprocessable("PLAGE_DEJA_COMMENCEE",
								"La plage a déjà commencé : la demande ne peut plus être annulée.");
					}
					return conflitDejaDecide();
				}
				validerCommentaireOptionnel(req.commentaire(), "MOTIF_TROP_LONG");
			}
			default -> throw PresenceMetierException.unprocessable(
					"EFFET_NON_SUPPORTE", "Effet non supporté pour HEURES_SUP : " + req.effet());
		}
		return new ResultatEffet(200, Map.of("ok", true));
	}

	private ResultatEffet appliquerHeuresSup(RequeteEffet req, String cle) {
		PresenceHeuresSupDemande d = heuresSupRepo.findByIdPourDecision(req.objetId())
				.orElseThrow(() -> PresenceMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable"));
		Optional<PresenceWorkflowEffet> memorise = effetRepo.findByCleIdempotence(cle);
		if (memorise.isPresent()) {
			PresenceWorkflowEffet e = memorise.get();
			return new ResultatEffet(e.getStatutHttp(), e.getReponse());
		}
		if (!PresenceWorkflowActivationService.MODE_MOTEUR.equals(d.getWorkflowMode())) {
			ResultatEffet conflit = conflitDejaDecide();
			memoriserEffet(cle, req, "OBJET_DEJA_DECIDE", conflit);
			return conflit;
		}

		Instant maintenant = clock.instant();
		ActeurEffet acteur = req.acteur();
		String nomActeur = formaterNomActeur(acteur);
		String compteActeur = acteur != null && acteur.compteId() != null ? acteur.compteId().toString() : null;

		switch (req.effet()) {
			case "APPROUVER" -> {
				if (d.getStatut() != StatutHeuresSup.EN_COURS) {
					ResultatEffet conflit = conflitDejaDecide();
					memoriserEffet(cle, req, "OBJET_DEJA_DECIDE", conflit);
					return conflit;
				}
				validerCommentaireOptionnel(req.commentaire(), "COMMENTAIRE_TROP_LONG");
				d.setStatut(StatutHeuresSup.VALIDEE);
				if (d.isCreeeValidee() && compteActeur == null) {
					d.setDecideParCompteId(d.getDemandeurCompteId());
					d.setDecideParNom(d.getDemandeurNom());
				} else {
					d.setDecideParCompteId(compteActeur);
					d.setDecideParNom(nomActeur);
				}
				d.setDecideLe(maintenant);
			}
			case "REFUSER" -> {
				if (d.getStatut() != StatutHeuresSup.EN_COURS) {
					ResultatEffet conflit = conflitDejaDecide();
					memoriserEffet(cle, req, "OBJET_DEJA_DECIDE", conflit);
					return conflit;
				}
				String motif = validerMotifObligatoire(req.commentaire());
				d.setStatut(StatutHeuresSup.REFUSEE);
				d.setMotifRefus(motif);
				d.setDecideParCompteId(compteActeur);
				d.setDecideParNom(nomActeur);
				d.setDecideLe(maintenant);
			}
			case "EXPIRER" -> {
				if (d.getStatut() != StatutHeuresSup.EN_COURS) {
					ResultatEffet conflit = conflitDejaDecide();
					memoriserEffet(cle, req, "OBJET_DEJA_DECIDE", conflit);
					return conflit;
				}
				d.setStatut(StatutHeuresSup.EXPIREE);
			}
			case "ANNULER" -> {
				if (!estAnnulableHeuresSup(d, maintenant)) {
					if (d.getStatut() == StatutHeuresSup.VALIDEE) {
						throw PresenceMetierException.unprocessable("PLAGE_DEJA_COMMENCEE",
								"La plage a déjà commencé : la demande ne peut plus être annulée.");
					}
					ResultatEffet conflit = conflitDejaDecide();
					memoriserEffet(cle, req, "OBJET_DEJA_DECIDE", conflit);
					return conflit;
				}
				String motif = validerCommentaireOptionnel(req.commentaire(), "MOTIF_TROP_LONG");
				d.setStatut(StatutHeuresSup.ANNULEE);
				d.setAnnuleParCompteId(compteActeur);
				d.setAnnuleParNom(nomActeur);
				d.setAnnuleLe(maintenant);
				d.setMotifAnnulation(motif);
			}
			default -> throw PresenceMetierException.unprocessable(
					"EFFET_NON_SUPPORTE", "Effet non supporté pour HEURES_SUP : " + req.effet());
		}

		d.setEtapeCouranteLibelle(null);
		d.setEcheanceLe(null);
		PresenceHeuresSupDemande saved = heuresSupRepo.save(d);
		if (saved.getStatut() == StatutHeuresSup.REFUSEE
				|| saved.getStatut() == StatutHeuresSup.EXPIREE
				|| saved.getStatut() == StatutHeuresSup.ANNULEE) {
			heuresSupJourRepo.deleteByDemandeId(saved.getId());
		}

		Map<String, Object> corps = Map.of("statut", saved.getStatut().name());
		ResultatEffet res = new ResultatEffet(200, corps);
		memoriserEffet(cle, req, saved.getStatut().name(), res);
		return res;
	}

	private static boolean estAnnulableHeuresSup(PresenceHeuresSupDemande d, Instant maintenant) {
		if (d.getStatut() == StatutHeuresSup.EN_COURS) {
			return true;
		}
		return d.getStatut() == StatutHeuresSup.VALIDEE
				&& (d.getDebutPlageTs() == null || d.getDebutPlageTs().isAfter(maintenant));
	}

	// ---- Validation commune & outils ------------------------------------------------------------------------------

	private static void verifierRequeteDeBase(RequeteEffet req, boolean exigerCle) {
		if (req == null || req.objetId() == null || req.objetType() == null || req.objetType().isBlank()
				|| req.effet() == null || req.effet().isBlank()) {
			throw PresenceMetierException.unprocessable(
					"CORPS_INVALIDE", "Les champs objet_type, objet_id et effet sont obligatoires.");
		}
		if (!TYPES_GERES.contains(req.objetType())) {
			throw PresenceMetierException.unprocessable(
					"TYPE_OBJET_NON_GERE", "Type d'objet non géré par svc-presence : " + req.objetType());
		}
		if (exigerCle && (req.cleIdempotence() == null || req.cleIdempotence().isBlank())) {
			throw PresenceMetierException.unprocessable(
					"CORPS_INVALIDE", "La clé d'idempotence est obligatoire.");
		}
	}

	private static String validerMotifObligatoire(String motif) {
		String m = motif == null ? "" : motif.trim();
		if (m.isEmpty()) {
			throw PresenceMetierException.unprocessable("MOTIF_OBLIGATOIRE", "Le motif du refus est obligatoire.");
		}
		if (m.length() > TEXTE_MAX) {
			throw PresenceMetierException.unprocessable("MOTIF_TROP_LONG",
					"Le motif ne peut pas dépasser " + TEXTE_MAX + " caractères.");
		}
		return m;
	}

	private static String validerCommentaireOptionnel(String texte, String codeDepassement) {
		if (texte == null || texte.isBlank()) {
			return null;
		}
		String t = texte.trim();
		if (t.length() > TEXTE_MAX) {
			throw PresenceMetierException.unprocessable(codeDepassement,
					"Le texte ne peut pas dépasser " + TEXTE_MAX + " caractères.");
		}
		return t;
	}

	private void memoriserEffet(String cle, RequeteEffet req, String codeResultat, ResultatEffet res) {
		effetRepo.save(new PresenceWorkflowEffet(
				cle, req.objetType(), req.objetId(), req.effet(), codeResultat, res.statutHttp(), res.corps()));
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
			return NOM_SYSTEME;
		}
		String nom = acteur.nom().trim();
		if (acteur.pourNom() != null && !acteur.pourNom().isBlank()) {
			return nom + " (pour le compte de " + acteur.pourNom().trim() + ")";
		}
		return nom;
	}
}
