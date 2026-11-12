package com.hr.evaluation.domain;

/** Codes des référentiels paramétrables de svc-evaluation (ADR lot 3 § 1). */
public final class ReferentielEvaluation {

    public static final String NIVEAU_ALERTE = "NIVEAU_ALERTE";
    public static final String CONCLUSION_ALERTE = "CONCLUSION_ALERTE";
    public static final String STATUT_EVALUATION = "STATUT_EVALUATION";
    public static final String STATUT_CAMPAGNE = "STATUT_CAMPAGNE";
    public static final String STATUT_ALERTE = "STATUT_ALERTE";
    public static final String TYPE_QUESTION = "TYPE_QUESTION";
    public static final String ECHELLE_ETOILES = "ECHELLE_ETOILES";
    public static final String APPRECIATION = "APPRECIATION";
    public static final String ETAT_ECART = "ETAT_ECART";

    /** Conclusion de repli, système : jamais supprimée ni désactivée. */
    public static final String CONCLUSION_AUTRE = "AUTRE";

    /** Type de question piloté par une règle (étoiles) : toujours actif. */
    public static final String TYPE_QUESTION_RATING = "RATING";

    /** Pseudo-référentiel du journal des réglages. */
    public static final String REGLAGES = "REGLAGES";

    public static final int SEUIL_ECART_MIN = 1;
    public static final int SEUIL_ECART_MAX = 4;
    public static final int NIVEAUX_ACTIFS_MAX = 4;

    private ReferentielEvaluation() {
    }
}
