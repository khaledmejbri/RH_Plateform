package com.hr.evaluation.service;

import com.hr.evaluation.entity.EvaluationParametres;
import com.hr.evaluation.repository.EvaluationParametresRepository;
import com.hr.evaluation.web.EvaluationMetierException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Paramètres société des évaluations (SCRUM-41) : durée par défaut d'une campagne, en jours.
 */
@Service
public class EvaluationParametresService {

    private final EvaluationParametresRepository repository;
    private final int dureeDefautJours;

    public EvaluationParametresService(
            EvaluationParametresRepository repository,
            @Value("${evaluation.campagnes.duree-defaut-jours:20}") int dureeDefautJours) {
        this.repository = repository;
        this.dureeDefautJours = dureeDefautJours;
    }

    /** Paramètres enregistrés, ou valeurs par défaut tant que la société ne les a pas réglés. */
    @Transactional(readOnly = true)
    public EvaluationParametres obtenir() {
        return repository.findById(EvaluationParametres.IDENTIFIANT_UNIQUE).orElseGet(() -> {
            EvaluationParametres p = new EvaluationParametres();
            p.setDureeCampagneJours(dureeDefautJours);
            return p;
        });
    }

    public int dureeCampagneJours() {
        return obtenir().getDureeCampagneJours();
    }

    @Transactional
    public EvaluationParametres mettreAJour(Integer dureeCampagneJours, UUID auteur) {
        if (dureeCampagneJours == null
                || dureeCampagneJours < CampagneCalendrier.DUREE_MIN_JOURS
                || dureeCampagneJours > CampagneCalendrier.DUREE_MAX_JOURS) {
            throw EvaluationMetierException.unprocessable("DUREE_INVALIDE",
                    "La durée d'une campagne doit être comprise entre " + CampagneCalendrier.DUREE_MIN_JOURS
                            + " et " + CampagneCalendrier.DUREE_MAX_JOURS + " jours.");
        }
        EvaluationParametres p = repository.findById(EvaluationParametres.IDENTIFIANT_UNIQUE)
                .orElseGet(EvaluationParametres::new);
        p.setDureeCampagneJours(dureeCampagneJours);
        p.setModifieLe(Instant.now());
        p.setModifiePar(auteur);
        return repository.save(p);
    }
}
