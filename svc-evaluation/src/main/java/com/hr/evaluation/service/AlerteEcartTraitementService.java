package com.hr.evaluation.service;

import com.hr.evaluation.domain.ConclusionAlerteEcart;
import com.hr.evaluation.domain.EvenementAlerteEcart;
import com.hr.evaluation.domain.NiveauEcart;
import com.hr.evaluation.domain.StatutAlerteEcart;
import com.hr.evaluation.dto.AlerteEcartCompteur;
import com.hr.evaluation.dto.AlerteEcartDetail;
import com.hr.evaluation.dto.AlerteEcartPage;
import com.hr.evaluation.dto.AlerteEcartResume;
import com.hr.evaluation.entity.AlerteEcart;
import com.hr.evaluation.entity.AlerteEcartHistorique;
import com.hr.evaluation.repository.AlerteEcartRepository;
import com.hr.evaluation.security.IdentiteAppelant;
import com.hr.evaluation.web.EvaluationMetierException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Liste, détail et traitement RH des alertes d'écart (D-E08, D-E09). Les actions ne touchent jamais l'évaluation
 * (étoiles, score, couleur, statut). Précédence des erreurs (Q-H) : 404, 403 conflit d'intérêts, 400 validation,
 * 409 état (le 401 et le 403 de rôle sont traités avant, par la sécurité).
 */
@Service
public class AlerteEcartTraitementService {

    public static final String ALERTE_INTROUVABLE = "ALERTE_INTROUVABLE";
    public static final String CONFLIT_INTERETS = "CONFLIT_INTERETS";
    public static final String ALERTE_ETAT_INVALIDE = "ALERTE_ETAT_INVALIDE";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String PARAMETRE_INVALIDE = "PARAMETRE_INVALIDE";

    public static final int LONGUEUR_COMMENTAIRE_MAX = 1000;
    public static final int LONGUEUR_COMMENTAIRE_MIN_CLOTURE = 10;
    public static final int TAILLE_PAR_DEFAUT = 20;
    public static final int TAILLE_MAX = 100;

    private final AlerteEcartRepository alerteRepository;

    public AlerteEcartTraitementService(AlerteEcartRepository alerteRepository) {
        this.alerteRepository = alerteRepository;
    }

    // ---------------------------------------------------------------------------------------------- lecture

    @Transactional(readOnly = true)
    public AlerteEcartPage lister(StatutAlerteEcart statut, NiveauEcart niveau, UUID campagneId, UUID uniteId,
                                  int page, int taille) {
        if (page < 0) {
            throw EvaluationMetierException.badRequest(PARAMETRE_INVALIDE, "Le paramètre page doit être positif ou nul.");
        }
        if (taille < 1 || taille > TAILLE_MAX) {
            throw EvaluationMetierException.badRequest(PARAMETRE_INVALIDE,
                    "Le paramètre taille doit être compris entre 1 et " + TAILLE_MAX + ".");
        }
        Specification<AlerteEcart> filtre = (racine, requete, cb) -> cb.conjunction();
        if (statut != null) {
            filtre = filtre.and((r, q, cb) -> cb.equal(r.get("statut"), statut));
        }
        if (niveau != null) {
            filtre = filtre.and((r, q, cb) -> cb.equal(r.get("niveauCourant"), niveau));
        }
        if (campagneId != null) {
            filtre = filtre.and((r, q, cb) -> cb.equal(r.get("campagneIdentifiant"), campagneId));
        }
        if (uniteId != null) {
            filtre = filtre.and((r, q, cb) -> cb.equal(r.get("collaborateurUniteId"), uniteId));
        }
        // Filtres en base ; tri par le comparateur testé seul, pagination en mémoire (volume : quelques centaines par an)
        List<AlerteEcart> triees = alerteRepository.findAll(filtre).stream()
                .sorted(AlerteEcartComparateur.INSTANCE)
                .toList();
        long debut = (long) page * taille;
        List<AlerteEcartResume> elements = triees.stream()
                .skip(debut)
                .limit(taille)
                .map(AlerteEcartMapper::resume)
                .toList();
        return new AlerteEcartPage(elements, triees.size(), page, taille);
    }

    @Transactional(readOnly = true)
    public AlerteEcartCompteur compteur() {
        return new AlerteEcartCompteur(
                alerteRepository.countByStatutNot(StatutAlerteEcart.TRAITEE),
                alerteRepository.countByStatutNotAndNiveauCourant(StatutAlerteEcart.TRAITEE, NiveauEcart.URGENTE));
    }

    @Transactional(readOnly = true)
    public AlerteEcartDetail detail(UUID alerteId, IdentiteAppelant appelant) {
        AlerteEcart alerte = alerteRepository.findById(alerteId).orElseThrow(() -> introuvable(alerteId));
        if (!appelant.peutEcrire()) {
            return AlerteEcartMapper.detail(alerte, false, AlerteEcartDetail.MOTIF_LECTURE_SEULE);
        }
        boolean concerne = estConcerne(alerte, appelant);
        return AlerteEcartMapper.detail(alerte, !concerne, concerne ? AlerteEcartDetail.MOTIF_CONCERNE : null);
    }

    // --------------------------------------------------------------------------------------------- actions

    @Transactional
    public AlerteEcartDetail prendreEnCharge(UUID alerteId, String commentaire, IdentiteAppelant appelant) {
        AlerteEcart alerte = chargerPourAction(alerteId, appelant);
        String note = nettoyer(commentaire);
        if (note != null && note.length() > LONGUEUR_COMMENTAIRE_MAX) {
            throw validation("Le commentaire ne doit pas dépasser " + LONGUEUR_COMMENTAIRE_MAX + " caractères.");
        }
        if (alerte.getStatut() != StatutAlerteEcart.OUVERTE) {
            throw etatInvalide(alerte);
        }
        alerte.ajouterHistorique(AlerteEcartHistorique.par(EvenementAlerteEcart.PRISE_EN_CHARGE,
                appelant.compteJwtObligatoire(), appelant.libelle(),
                StatutAlerteEcart.OUVERTE, StatutAlerteEcart.EN_COURS, alerte.getNiveauCourant(), null, note));
        alerte.setStatut(StatutAlerteEcart.EN_COURS);
        alerte.setModifieeLe(Instant.now());
        alerteRepository.save(alerte);
        return AlerteEcartMapper.detail(alerte, true, null);
    }

    @Transactional
    public AlerteEcartDetail cloturer(UUID alerteId, String conclusionBrute, String commentaire,
                                      IdentiteAppelant appelant) {
        AlerteEcart alerte = chargerPourAction(alerteId, appelant);

        ConclusionAlerteEcart conclusion = conclusion(conclusionBrute);
        String note = nettoyer(commentaire);
        if (note == null || note.length() < LONGUEUR_COMMENTAIRE_MIN_CLOTURE) {
            throw validation("Le commentaire est obligatoire (" + LONGUEUR_COMMENTAIRE_MIN_CLOTURE
                    + " caractères minimum).");
        }
        if (note.length() > LONGUEUR_COMMENTAIRE_MAX) {
            throw validation("Le commentaire ne doit pas dépasser " + LONGUEUR_COMMENTAIRE_MAX + " caractères.");
        }
        if (alerte.getStatut() == StatutAlerteEcart.TRAITEE) {
            throw etatInvalide(alerte);
        }
        StatutAlerteEcart avant = alerte.getStatut();
        alerte.ajouterHistorique(AlerteEcartHistorique.par(EvenementAlerteEcart.CLOTURE,
                appelant.compteJwtObligatoire(), appelant.libelle(),
                avant, StatutAlerteEcart.TRAITEE, alerte.getNiveauCourant(), conclusion, note));
        alerte.setStatut(StatutAlerteEcart.TRAITEE);
        alerte.setConclusion(conclusion);
        alerte.setModifieeLe(Instant.now());
        alerteRepository.save(alerte);
        return AlerteEcartMapper.detail(alerte, true, null);
    }

    // ----------------------------------------------------------------------------------------------- outils

    /** 404, puis 403 conflit d'intérêts (même sur une alerte TRAITEE, avant tout contrôle d'état). */
    private AlerteEcart chargerPourAction(UUID alerteId, IdentiteAppelant appelant) {
        AlerteEcart alerte = alerteRepository.findByIdForUpdate(alerteId).orElseThrow(() -> introuvable(alerteId));
        if (!appelant.peutEcrire()) {
            throw EvaluationMetierException.forbidden("ACCESS_DENIED", "Action réservée à la RH et à l'administration.");
        }
        if (estConcerne(alerte, appelant)) {
            throw EvaluationMetierException.forbidden(CONFLIT_INTERETS,
                    "Vous êtes concerné par cette évaluation : vous ne pouvez pas traiter cette alerte.");
        }
        return alerte;
    }

    /** L'appelant est le collaborateur évalué ou son RO (compte du snapshot, repli fiche). */
    private static boolean estConcerne(AlerteEcart alerte, IdentiteAppelant appelant) {
        return appelant.correspond(alerte.getCollaborateurCompteId(), alerte.getCollaborateurFicheId())
                || appelant.correspond(alerte.getSuperieurCompteId(), alerte.getSuperieurFicheId());
    }

    private static ConclusionAlerteEcart conclusion(String brute) {
        if (brute == null || brute.isBlank()) {
            throw validation("La conclusion est obligatoire.");
        }
        try {
            return ConclusionAlerteEcart.valueOf(brute.strip());
        } catch (IllegalArgumentException e) {
            throw validation("Conclusion inconnue : " + brute);
        }
    }

    private static String nettoyer(String texte) {
        if (texte == null) {
            return null;
        }
        String s = texte.strip();
        return s.isEmpty() ? null : s;
    }

    private static EvaluationMetierException introuvable(UUID alerteId) {
        return EvaluationMetierException.notFound(ALERTE_INTROUVABLE, "Alerte introuvable : " + alerteId);
    }

    private static EvaluationMetierException validation(String message) {
        return EvaluationMetierException.badRequest(VALIDATION_ERROR, message);
    }

    private static EvaluationMetierException etatInvalide(AlerteEcart alerte) {
        return EvaluationMetierException.conflict(ALERTE_ETAT_INVALIDE,
                "Cette action n'est pas possible dans l'état actuel de l'alerte.",
                Map.of("statutActuel", alerte.getStatut().name()));
    }
}
