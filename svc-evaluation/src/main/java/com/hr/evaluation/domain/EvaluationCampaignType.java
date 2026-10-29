package com.hr.evaluation.domain;

/**
 * Types de campagne. TRIMESTRIELLE ajouté par D-E02 (SCRUM-41) : une campagne par trimestre.
 * Ajouter une valeur exige de recréer la contrainte CHECK (voir CampagneTypeConstraintRunner).
 */
public enum EvaluationCampaignType {
    ANNUELLE,
    SEMESTRIELLE,
    TRIMESTRIELLE
}
