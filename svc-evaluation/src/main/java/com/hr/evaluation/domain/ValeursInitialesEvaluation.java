package com.hr.evaluation.domain;

import java.util.ArrayList;
import java.util.List;

import static com.hr.evaluation.domain.ReferentielEvaluation.*;

/**
 * Valeurs initiales des référentiels de svc-evaluation (ADR lot 3 § 8) : reprise du catalogue historique (anciens enums
 * {@code NiveauEcart} et {@code ConclusionAlerteEcart}) et libellés français des codes de comportement. Lues une
 * seule fois, à l'amorçage : une ligne déjà présente n'est jamais réécrite.
 */
public final class ValeursInitialesEvaluation {

    /**
     * @param utilisee   marquée « déjà utilisée » d'office (catalogue historique : on ne sait pas ce qui existe en base)
     * @param seuilEcart seuil des niveaux d'alerte, null ailleurs
     */
    public record ValeurInitiale(String referentiel, String code, NatureParametre nature, String libelle,
                                 String description, String couleur, int ordre, boolean systeme, boolean utilisee,
                                 Integer seuilEcart) {
    }

    /** Attribut propre à un référentiel (nom JSON, type), servi par {@code GET B/referentiels}. */
    public record Attribut(String nom, String type) {
    }

    /** Descripteur d'un référentiel, servi par {@code GET B/referentiels}. */
    public record Descripteur(String referentiel, String libelle, String domaine, NatureParametre nature,
                              boolean creer, boolean supprimer, boolean desactiver, List<Attribut> attributs) {
    }

    public static final List<Descripteur> DESCRIPTEURS = List.of(
            new Descripteur(NIVEAU_ALERTE, "Niveaux d'alerte d'écart", "EVALUATIONS", NatureParametre.VALEUR,
                    true, true, true, List.of(new Attribut("seuil_ecart", "ENTIER"))),
            new Descripteur(CONCLUSION_ALERTE, "Conclusions d'alerte d'écart", "EVALUATIONS", NatureParametre.VALEUR,
                    true, true, true, List.of()),
            new Descripteur(STATUT_EVALUATION, "Statuts d'évaluation", "EVALUATIONS", NatureParametre.COMPORTEMENT,
                    false, false, false, List.of()),
            new Descripteur(STATUT_CAMPAGNE, "Statuts de campagne", "EVALUATIONS", NatureParametre.COMPORTEMENT,
                    false, false, false, List.of()),
            new Descripteur(STATUT_ALERTE, "Statuts d'alerte d'écart", "EVALUATIONS", NatureParametre.COMPORTEMENT,
                    false, false, false, List.of()),
            new Descripteur(TYPE_QUESTION, "Types de question", "EVALUATIONS", NatureParametre.COMPORTEMENT,
                    false, false, true, List.of()),
            new Descripteur(ECHELLE_ETOILES, "Échelle des étoiles", "EVALUATIONS", NatureParametre.COMPORTEMENT,
                    false, false, false, List.of()),
            new Descripteur(APPRECIATION, "Appréciations", "EVALUATIONS", NatureParametre.COMPORTEMENT,
                    false, false, false, List.of()),
            new Descripteur(ETAT_ECART, "État d'un écart", "EVALUATIONS", NatureParametre.COMPORTEMENT,
                    false, false, false, List.of()));

    private ValeursInitialesEvaluation() {
    }

    public static List<ValeurInitiale> toutes() {
        List<ValeurInitiale> l = new ArrayList<>();
        // Niveaux d'alerte : seuils historiques 2 / 3 / 4 (D-E04.b)
        niveau(l, "ORANGE", "Alerte", "Écart d'au moins 2 étoiles", "ORANGE", 1, 2);
        niveau(l, "ROUGE", "Critique", "Écart d'au moins 3 étoiles", "ROUGE", 2, 3);
        niveau(l, "URGENTE", "Urgente", "Écart d'au moins 4 étoiles", "VIOLET", 3, 4);

        conclusion(l, "MANQUE_EFFORT", "Manque d'effort confirmé", 1, false);
        conclusion(l, "SURESTIMATION_COLLABORATEUR", "Le collaborateur a surestimé son niveau", 2, false);
        conclusion(l, "SUIVI_INSUFFISANT_RO", "Le responsable ne suivait pas assez le travail", 3, false);
        conclusion(l, "SOUS_ESTIMATION_COLLABORATEUR", "Le collaborateur a sous-estimé son travail", 4, false);
        conclusion(l, "NOTE_GONFLEE_RO", "Le responsable a surévalué la note", 5, false);
        conclusion(l, "ECART_JUSTIFIE", "Écart expliqué, aucune action", 6, false);
        conclusion(l, CONCLUSION_AUTRE, "Autre (préciser dans le commentaire)", 7, true);

        comportement(l, STATUT_EVALUATION, "EN_ATTENTE_VALIDATION_CROISEE", "En cours", "AMBRE", 1);
        comportement(l, STATUT_EVALUATION, "VALIDEE_COLLABORATEUR", "Validée par le collaborateur", "BLEU", 2);
        comportement(l, STATUT_EVALUATION, "VALIDEE_SUPERIEUR", "Validée par le responsable", "CYAN", 3);
        comportement(l, STATUT_EVALUATION, "VALIDEE", "Validée", "VERT", 4);
        comportement(l, STATUT_EVALUATION, "ARCHIVEE", "Archivée", "NEUTRE", 5);

        comportement(l, STATUT_CAMPAGNE, "PLANIFIEE", "Planifiée", "BLEU", 1);
        comportement(l, STATUT_CAMPAGNE, "ACTIVE", "Active", "VERT", 2);
        comportement(l, STATUT_CAMPAGNE, "TERMINEE", "Terminée", "NEUTRE", 3);
        comportement(l, STATUT_CAMPAGNE, "ANNULEE", "Annulée", "ROUGE", 4);

        for (StatutAlerteEcart s : StatutAlerteEcart.values()) {
            String couleur = switch (s) {
                case OUVERTE -> "ROUGE";
                case EN_COURS -> "AMBRE";
                case TRAITEE -> "VERT";
            };
            comportement(l, STATUT_ALERTE, s.name(), s.libelle(), couleur, s.ordinal() + 1);
        }

        comportement(l, TYPE_QUESTION, "TEXT", "Texte court", "NEUTRE", 1);
        comportement(l, TYPE_QUESTION, "PARAGRAPH", "Texte long", "NEUTRE", 2);
        comportement(l, TYPE_QUESTION, "MULTIPLE_CHOICE", "Choix unique", "NEUTRE", 3);
        comportement(l, TYPE_QUESTION, "CHECKBOX", "Choix multiples", "NEUTRE", 4);
        comportement(l, TYPE_QUESTION, TYPE_QUESTION_RATING, "Étoiles", "NEUTRE", 5);
        comportement(l, TYPE_QUESTION, "SCALE", "Échelle numérique", "NEUTRE", 6);
        comportement(l, TYPE_QUESTION, "DATE", "Date", "NEUTRE", 7);
        comportement(l, TYPE_QUESTION, "NUMBER", "Nombre", "NEUTRE", 8);

        comportement(l, ECHELLE_ETOILES, "1", "Débutant", "ROUGE", 1);
        comportement(l, ECHELLE_ETOILES, "2", "Supervisé", "ORANGE", 2);
        comportement(l, ECHELLE_ETOILES, "3", "Autonome", "AMBRE", 3);
        comportement(l, ECHELLE_ETOILES, "4", "Avancé", "CYAN", 4);
        comportement(l, ECHELLE_ETOILES, "5", "Expert", "VERT", 5);

        for (AppreciationEvaluationRh a : AppreciationEvaluationRh.values()) {
            String couleur = switch (a) {
                case INSUFFISANT -> "ROUGE";
                case A_AMELIORER -> "ORANGE";
                case SATISFAISANT -> "AMBRE";
                case POSITIF -> "CYAN";
                case EXCELLENT -> "VERT";
            };
            comportement(l, APPRECIATION, a.name(), a.libelle(), couleur, a.ordinal() + 1);
        }

        comportement(l, ETAT_ECART, NiveauAlerte.ALIGNE, NiveauAlerte.LIBELLE_ALIGNE, "VERT", 1);
        return List.copyOf(l);
    }

    private static void niveau(List<ValeurInitiale> l, String code, String libelle, String description,
                               String couleur, int ordre, int seuil) {
        l.add(new ValeurInitiale(NIVEAU_ALERTE, code, NatureParametre.VALEUR, libelle, description, couleur, ordre,
                false, true, seuil));
    }

    private static void conclusion(List<ValeurInitiale> l, String code, String libelle, int ordre, boolean systeme) {
        l.add(new ValeurInitiale(CONCLUSION_ALERTE, code, NatureParametre.VALEUR, libelle, null, null, ordre,
                systeme, true, null));
    }

    private static void comportement(List<ValeurInitiale> l, String ref, String code, String libelle, String couleur,
                                     int ordre) {
        l.add(new ValeurInitiale(ref, code, NatureParametre.COMPORTEMENT, libelle, null, couleur, ordre, true,
                false, null));
    }
}
