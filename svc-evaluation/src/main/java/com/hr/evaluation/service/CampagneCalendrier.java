package com.hr.evaluation.service;

import com.hr.evaluation.domain.EvaluationCampaignType;
import com.hr.evaluation.web.EvaluationMetierException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Calendrier d'une campagne (D-E02, SCRUM-41) : mois libre, jour de commencement (défaut le 20),
 * date de fin = date de commencement + durée en jours. Heure de référence : Tunis.
 */
public final class CampagneCalendrier {

    public static final ZoneId ZONE = ZoneId.of("Africa/Tunis");
    public static final int JOUR_DEBUT_DEFAUT = 20;
    public static final int DUREE_MIN_JOURS = 1;
    public static final int DUREE_MAX_JOURS = 120;

    private CampagneCalendrier() {
    }

    /**
     * @param annee     année de la campagne
     * @param moisDebut 1 à 12
     * @param jourDebut jour du mois ; null → le 20
     * @param dureeJours durée en jours (1 à 120)
     */
    public static Periode calculer(int annee, Integer moisDebut, Integer jourDebut, int dureeJours) {
        if (moisDebut == null || moisDebut < 1 || moisDebut > 12) {
            throw EvaluationMetierException.unprocessable("CALENDRIER_INVALIDE",
                    "Le mois de lancement doit être compris entre 1 et 12.");
        }
        if (dureeJours < DUREE_MIN_JOURS || dureeJours > DUREE_MAX_JOURS) {
            throw EvaluationMetierException.unprocessable("DUREE_INVALIDE",
                    "La durée d'une campagne doit être comprise entre " + DUREE_MIN_JOURS + " et "
                            + DUREE_MAX_JOURS + " jours.");
        }
        int jour = jourDebut != null ? jourDebut : JOUR_DEBUT_DEFAUT;
        LocalDate debut;
        try {
            debut = LocalDate.of(annee, moisDebut, jour);
        } catch (DateTimeException e) {
            throw EvaluationMetierException.unprocessable("CALENDRIER_INVALIDE",
                    "Le jour " + jour + " n'existe pas pour le mois " + moisDebut + "/" + annee + ".");
        }
        LocalDate dernierJour = debut.plusDays(dureeJours);
        return new Periode(
                debut,
                dernierJour,
                debut.atStartOfDay(ZONE).toInstant(),
                // La campagne reste ouverte toute la journée du dernier jour
                dernierJour.plusDays(1).atStartOfDay(ZONE).toInstant().minusSeconds(1),
                jour);
    }

    /**
     * Période au sens de l'unicité : une campagne du même type par année, semestre ou trimestre.
     */
    public static int indicePeriode(EvaluationCampaignType type, int moisDebut) {
        return switch (type) {
            case ANNUELLE -> 0;
            case SEMESTRIELLE -> (moisDebut - 1) / 6;
            case TRIMESTRIELLE -> (moisDebut - 1) / 3;
        };
    }

    public static String libellePeriode(EvaluationCampaignType type, int moisDebut, int annee) {
        return switch (type) {
            case ANNUELLE -> "l'année " + annee;
            case SEMESTRIELLE -> "le semestre S" + (indicePeriode(type, moisDebut) + 1) + " " + annee;
            case TRIMESTRIELLE -> "le trimestre T" + (indicePeriode(type, moisDebut) + 1) + " " + annee;
        };
    }

    public record Periode(LocalDate debut, LocalDate dernierJour, Instant dateDebut, Instant dateFin, int jourDebut) {
    }
}
