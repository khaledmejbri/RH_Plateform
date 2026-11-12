package com.hr.evaluation.service;

import com.hr.evaluation.domain.NiveauAlerte;
import com.hr.evaluation.domain.QuestionType;
import com.hr.evaluation.domain.ReferentielEvaluation;
import com.hr.evaluation.repository.ParametreValeurRepository;
import com.hr.evaluation.web.EvaluationMetierException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Porte d'entrée de la logique métier vers les paramètres (ADR lot 3 §§ 3, 4, 6) : niveaux d'alerte, libellés,
 * seuil d'insuffisance, échelle des étoiles, types de question. Toute lecture passe par l'instantané en cache ; aucune
 * règle n'est écrite en dur sur un code de niveau.
 */
@Service
public class ParametresEvaluation {

    /** Libellé d'un code absent du paramétrage (convention du lot 1). */
    public static final String VALEUR_NON_RECONNUE = "Valeur non reconnue";

    public static final String TYPE_QUESTION_INACTIF = "TYPE_QUESTION_INACTIF";

    private final ParametresCache cache;
    private final ParametreValeurRepository valeurs;

    public ParametresEvaluation(ParametresCache cache, ParametreValeurRepository valeurs) {
        this.cache = cache;
        this.valeurs = valeurs;
    }

    public ParametresInstantane instantane() {
        return cache.obtenir();
    }

    /**
     * Lecture d'une valeur <b>en base</b> (jamais l'instantané en cache de 30 s) : à utiliser pour valider une écriture
     * (ex. conclusion de clôture), afin qu'une valeur désactivée à l'instant ne soit plus acceptée.
     */
    public Optional<ParametresInstantane.Valeur> lireEnBase(String referentiel, String code) {
        return valeurs.findByReferentielAndCode(referentiel, code).map(ParametresCache::copie);
    }

    // -------------------------------------------------------------------------------------- niveaux d'alerte

    /** Niveaux actifs, du seuil le plus bas au plus haut. */
    public List<NiveauAlerte> niveauxActifs() {
        return instantane().niveauxActifs();
    }

    /** Tous les niveaux, actifs et inactifs (comparaison des codes déjà stockés). */
    public List<NiveauAlerte> niveaux() {
        return instantane().niveaux();
    }

    /**
     * Code du niveau d'un écart, selon les seuils actifs courants ; {@code ALIGNE} sinon. Sans aucun niveau actif
     * lu, les seuils initiaux 2 / 3 / 4 s'appliquent.
     */
    public String niveau(int ecart) {
        List<NiveauAlerte> actifs = niveauxActifs();
        // Aucun niveau actif lu : repli sur les seuils initiaux 2 / 3 / 4 plutôt que de masquer l'écart
        return NiveauxAlerte.niveau(ecart, actifs.isEmpty() ? NiveauxAlerte.PAR_DEFAUT : actifs);
    }

    public int comparer(String a, String b) {
        return NiveauxAlerte.comparer(a, b, niveaux());
    }

    public String max(String a, String b) {
        return NiveauxAlerte.max(a, b, niveaux());
    }

    public int seuil(String code) {
        return NiveauxAlerte.seuil(code, niveaux());
    }

    public static boolean estAlerte(String code) {
        return NiveauAlerte.estAlerte(code);
    }

    /** Libellé courant d'un niveau (le code ALIGNE n'est pas dans la table). */
    public String libelleNiveau(String code) {
        if (code == null) {
            return null;
        }
        if (NiveauAlerte.ALIGNE.equals(code)) {
            return NiveauAlerte.LIBELLE_ALIGNE;
        }
        return libelle(ReferentielEvaluation.NIVEAU_ALERTE, code);
    }

    public String libelleConclusion(String code) {
        return code == null ? null : libelle(ReferentielEvaluation.CONCLUSION_ALERTE, code);
    }

    public String libelle(String referentiel, String code) {
        String lu = instantane().libelle(referentiel, code);
        return lu != null ? lu : VALEUR_NON_RECONNUE;
    }

    // ------------------------------------------------------------------------------------------- réglages

    public int seuilInsuffisance() {
        return instantane().seuilInsuffisance();
    }

    /**
     * Libellés de l'échelle des étoiles, de 1 à 5, servis au mobile (D-R02.c). Triés par code numérique et non par
     * {@code ordre} : la note est l'indice de l'étoile, un ordre d'affichage modifié ne doit jamais la décaler.
     */
    public List<String> libellesEchelle() {
        return instantane().valeurs(ReferentielEvaluation.ECHELLE_ETOILES).stream()
                .sorted(java.util.Comparator.comparingInt(v -> Integer.parseInt(v.code())))
                .map(ParametresInstantane.Valeur::libelle)
                .toList();
    }

    // -------------------------------------------------------------------------------- types de question

    /** 422 {@code TYPE_QUESTION_INACTIF} si le type est désactivé (RATING ne l'est jamais). */
    public void verifierTypeQuestionActif(QuestionType type) {
        boolean actif = instantane().valeur(ReferentielEvaluation.TYPE_QUESTION, type.name())
                .map(ParametresInstantane.Valeur::actif)
                .orElse(true);
        if (!actif) {
            throw EvaluationMetierException.unprocessable(TYPE_QUESTION_INACTIF,
                    "Le type de question « " + libelle(ReferentielEvaluation.TYPE_QUESTION, type.name())
                            + " » est désactivé : choisissez un autre type.");
        }
    }

    // --------------------------------------------------------------------------------- marque collante

    /**
     * Pose la marque « déjà utilisée » (ADR § 6) dans la transaction qui utilise la valeur : un seul UPDATE, sans
     * effet si la marque existe déjà. Les valeurs de comportement ne sont pas marquées (jamais supprimables).
     */
    public void marquerUtilise(String referentiel, String code) {
        if (code == null || NiveauAlerte.ALIGNE.equals(code)) {
            return;
        }
        valeurs.marquerUtilisee(referentiel, code, Instant.now());
    }
}
