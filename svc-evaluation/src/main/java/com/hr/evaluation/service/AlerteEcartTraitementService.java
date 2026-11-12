package com.hr.evaluation.service;

import com.hr.evaluation.domain.EvenementAlerteEcart;
import com.hr.evaluation.domain.NiveauAlerte;
import com.hr.evaluation.domain.ReferentielEvaluation;
import com.hr.evaluation.domain.StatutAlerteEcart;
import com.hr.evaluation.dto.AlerteEcartCompteur;
import com.hr.evaluation.dto.AlerteEcartDetail;
import com.hr.evaluation.dto.AlerteEcartOuvertesParNiveau;
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
import java.util.HashMap;
import java.util.ArrayList;
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
    public static final String CONCLUSION_INACTIVE = "CONCLUSION_INACTIVE";

    public static final int LONGUEUR_COMMENTAIRE_MAX = 1000;
    public static final int LONGUEUR_COMMENTAIRE_MIN_CLOTURE = 10;
    public static final int TAILLE_PAR_DEFAUT = 20;
    public static final int TAILLE_MAX = 100;

    private final AlerteEcartRepository alerteRepository;
    private final ParametresEvaluation parametres;

    public AlerteEcartTraitementService(AlerteEcartRepository alerteRepository, ParametresEvaluation parametres) {
        this.alerteRepository = alerteRepository;
        this.parametres = parametres;
    }

    // ---------------------------------------------------------------------------------------------- lecture

    @Transactional(readOnly = true)
    public AlerteEcartPage lister(StatutAlerteEcart statut, String niveau, UUID campagneId, UUID uniteId,
                                  UUID collaborateurId, int page, int taille) {
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
        if (niveau != null && !niveau.isBlank()) {
            String code = niveau.strip();
            if (!NiveauAlerte.ALIGNE.equals(code) && parametres.niveaux().stream().noneMatch(n -> n.code().equals(code))) {
                throw EvaluationMetierException.badRequest(PARAMETRE_INVALIDE, "Niveau inconnu : " + code);
            }
            filtre = filtre.and((r, q, cb) -> cb.equal(r.get("niveauCourant"), code));
        }
        if (campagneId != null) {
            filtre = filtre.and((r, q, cb) -> cb.equal(r.get("campagneIdentifiant"), campagneId));
        }
        if (uniteId != null) {
            filtre = filtre.and((r, q, cb) -> cb.equal(r.get("collaborateurUniteId"), uniteId));
        }
        if (collaborateurId != null) {
            // D-N01 : alertes d'un collaborateur, désigné par sa fiche ou par son compte
            filtre = filtre.and((r, q, cb) -> cb.or(
                    cb.equal(r.get("collaborateurFicheId"), collaborateurId),
                    cb.equal(r.get("collaborateurCompteId"), collaborateurId)));
        }
        // Filtres en base ; tri par le comparateur testé seul, pagination en mémoire (volume : quelques centaines par an)
        List<AlerteEcart> triees = alerteRepository.findAll(filtre).stream()
                .sorted(new AlerteEcartComparateur(parametres::seuil))
                .toList();
        long debut = (long) page * taille;
        List<AlerteEcartResume> elements = triees.stream()
                .skip(debut)
                .limit(taille)
                .map(a -> AlerteEcartMapper.resume(parametres, a))
                .toList();
        return new AlerteEcartPage(elements, triees.size(), page, taille);
    }

    @Transactional(readOnly = true)
    public AlerteEcartCompteur compteur() {
        // « Urgentes » = alertes au niveau actif le plus haut (ou au-dessus si un niveau désactivé garde un seuil supérieur)
        List<NiveauAlerte> actifs = parametres.niveauxActifs();
        long urgentes = 0;
        if (!actifs.isEmpty()) {
            int seuilMax = actifs.get(actifs.size() - 1).seuil();
            List<String> codes = parametres.niveaux().stream()
                    .filter(n -> n.seuil() >= seuilMax).map(NiveauAlerte::code).toList();
            urgentes = alerteRepository.countByStatutNotAndNiveauCourantIn(StatutAlerteEcart.TRAITEE, codes);
        }
        return new AlerteEcartCompteur(alerteRepository.countByStatutNot(StatutAlerteEcart.TRAITEE), urgentes);
    }

    /**
     * D-E12.c : alertes d'écart ouvertes (OUVERTE + EN_COURS, jamais TRAITEE) par niveau, pour une campagne ou, si
     * {@code campagneId} est nul, toutes campagnes. Tous les niveaux actifs sont présents (0 si aucune alerte) ; un
     * niveau désactivé n'apparaît que s'il porte encore des alertes ouvertes.
     */
    @Transactional(readOnly = true)
    public AlerteEcartOuvertesParNiveau ouvertesParNiveau(UUID campagneId) {
        List<StatutAlerteEcart> statuts = List.of(StatutAlerteEcart.OUVERTE, StatutAlerteEcart.EN_COURS);
        List<Object[]> lignes = campagneId == null
                ? alerteRepository.compterParNiveau(statuts)
                : alerteRepository.compterParNiveauEtCampagne(statuts, campagneId);
        Map<String, Long> parNiveau = new HashMap<>();
        for (Object[] ligne : lignes) {
            parNiveau.merge((String) ligne[0], ((Number) ligne[1]).longValue(), Long::sum);
        }
        List<AlerteEcartOuvertesParNiveau.Niveau> resultat = new ArrayList<>();
        for (NiveauAlerte n : parametres.niveaux()) {
            long nombre = parNiveau.getOrDefault(n.code(), 0L);
            if (n.actif() || nombre > 0) {
                resultat.add(new AlerteEcartOuvertesParNiveau.Niveau(n.code(), n.libelle(), n.couleur(), n.seuil(), nombre));
            }
        }
        return AlerteEcartOuvertesParNiveau.de(resultat);
    }

    @Transactional(readOnly = true)
    public AlerteEcartDetail detail(UUID alerteId, IdentiteAppelant appelant) {
        AlerteEcart alerte = alerteRepository.findById(alerteId).orElseThrow(() -> introuvable(alerteId));
        if (!appelant.peutEcrire()) {
            return AlerteEcartMapper.detail(parametres, alerte, false, AlerteEcartDetail.MOTIF_LECTURE_SEULE);
        }
        boolean concerne = estConcerne(alerte, appelant);
        return AlerteEcartMapper.detail(parametres, alerte, !concerne, concerne ? AlerteEcartDetail.MOTIF_CONCERNE : null);
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
        return AlerteEcartMapper.detail(parametres, alerte, true, null);
    }

    @Transactional
    public AlerteEcartDetail cloturer(UUID alerteId, String conclusionBrute, String commentaire,
                                      IdentiteAppelant appelant) {
        AlerteEcart alerte = chargerPourAction(alerteId, appelant);

        String conclusion = conclusion(conclusionBrute);
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
        parametres.marquerUtilise(ReferentielEvaluation.CONCLUSION_ALERTE, conclusion);
        alerte.setModifieeLe(Instant.now());
        alerteRepository.save(alerte);
        return AlerteEcartMapper.detail(parametres, alerte, true, null);
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

    /** Conclusion saisie : 400 si absente ou inconnue, 422 {@code CONCLUSION_INACTIVE} si désactivée (D-R02). */
    private String conclusion(String brute) {
        if (brute == null || brute.isBlank()) {
            throw validation("La conclusion est obligatoire.");
        }
        String code = brute.strip();
        // Validation d'écriture : lue en base, pas dans le cache de 30 s (une conclusion désactivée est refusée aussitôt)
        var valeur = parametres.lireEnBase(ReferentielEvaluation.CONCLUSION_ALERTE, code)
                .orElseThrow(() -> validation("Conclusion inconnue : " + brute));
        if (!valeur.actif()) {
            throw EvaluationMetierException.unprocessable(CONCLUSION_INACTIVE,
                    "La conclusion « " + valeur.libelle() + " » est désactivée : choisissez-en une autre.");
        }
        return code;
    }

    private static String nettoyer(String texte) {
        if (texte == null) {
            return null;
        }
        String s = texte.strip();
        return s.isEmpty() ? null : s;
    }

    private static EvaluationMetierException introuvable(UUID alerteId) {
        return EvaluationMetierException.notFound(ALERTE_INTROUVABLE, "Alerte introuvable");
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
