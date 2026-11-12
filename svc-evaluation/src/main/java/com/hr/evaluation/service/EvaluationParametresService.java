package com.hr.evaluation.service;

import com.hr.evaluation.domain.ActionJournalParametre;
import com.hr.evaluation.domain.ReferentielEvaluation;
import com.hr.evaluation.dto.ParametreDto;
import com.hr.evaluation.entity.EvaluationParametres;
import com.hr.evaluation.repository.EvaluationParametresRepository;
import com.hr.evaluation.service.ParametreJournalService.Auteur;
import com.hr.evaluation.web.EvaluationMetierException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Réglages société des évaluations (SCRUM-41, D-R06) : durée par défaut d'une campagne, en jours, et seuil
 * d'insuffisance d'une note, en étoiles. Chaque modification porte la version lue (409 sinon), est tracée au journal
 * dans la même transaction et invalide le cache des paramètres après commit.
 */
@Service
public class EvaluationParametresService {

    public static final String CLE_DUREE_CAMPAGNE = "duree_campagne_jours";
    public static final String CLE_SEUIL_INSUFFISANCE = "seuil_insuffisance";

    public static final int SEUIL_INSUFFISANCE_MIN = 1;
    public static final int SEUIL_INSUFFISANCE_MAX = 5;

    public static final String REGLAGE_INCONNU = "REGLAGE_INCONNU";
    public static final String REGLAGE_HORS_BORNES = "REGLAGE_HORS_BORNES";

    private final EvaluationParametresRepository repository;
    private final int dureeDefautJours;
    private final ParametreJournalService journal;
    private final ParametresCache cache;

    public EvaluationParametresService(
            EvaluationParametresRepository repository,
            @Value("${evaluation.campagnes.duree-defaut-jours:20}") int dureeDefautJours,
            ParametreJournalService journal,
            ParametresCache cache) {
        this.repository = repository;
        this.dureeDefautJours = dureeDefautJours;
        this.journal = journal;
        this.cache = cache;
    }

    /** Paramètres enregistrés, ou valeurs par défaut tant que la société ne les a pas réglés. */
    @Transactional(readOnly = true)
    public EvaluationParametres obtenir() {
        return repository.findById(EvaluationParametres.IDENTIFIANT_UNIQUE).orElseGet(this::parDefaut);
    }

    public int dureeCampagneJours() {
        return obtenir().getDureeCampagneJours();
    }

    /** Ancienne voie {@code PUT} racine : durée seule, erreur 422 {@code DUREE_INVALIDE} conservée. */
    @Transactional
    public EvaluationParametres mettreAJour(Integer dureeCampagneJours, Auteur auteur) {
        if (dureeCampagneJours == null
                || dureeCampagneJours < CampagneCalendrier.DUREE_MIN_JOURS
                || dureeCampagneJours > CampagneCalendrier.DUREE_MAX_JOURS) {
            throw EvaluationMetierException.unprocessable("DUREE_INVALIDE",
                    "La durée d'une campagne doit être comprise entre " + CampagneCalendrier.DUREE_MIN_JOURS
                            + " et " + CampagneCalendrier.DUREE_MAX_JOURS + " jours.");
        }
        EvaluationParametres p = repository.findById(EvaluationParametres.IDENTIFIANT_UNIQUE)
                .orElseGet(this::parDefaut);
        int avant = p.getDureeCampagneJours();
        if (avant == dureeCampagneJours) {
            return p; // aucun changement : ni écriture ni journal
        }
        p.setDureeCampagneJours(dureeCampagneJours);
        EvaluationParametres sauvee = enregistrer(p, auteur);
        journal.ecrire(ReferentielEvaluation.REGLAGES, CLE_DUREE_CAMPAGNE, ActionJournalParametre.REGLAGE,
                Map.of("valeur", avant), Map.of("valeur", dureeCampagneJours), auteur);
        cache.invaliderApresCommit();
        return sauvee;
    }

    // ------------------------------------------------------------------------------------------- réglages

    @Transactional(readOnly = true)
    public List<ParametreDto.Reglage> reglages() {
        EvaluationParametres p = obtenir();
        long version = p.getVersion() == null ? 0 : p.getVersion();
        return List.of(
                new ParametreDto.Reglage(CLE_DUREE_CAMPAGNE, "Durée d'une campagne", p.getDureeCampagneJours(),
                        CampagneCalendrier.DUREE_MIN_JOURS, CampagneCalendrier.DUREE_MAX_JOURS, "JOURS",
                        dureeDefautJours, version),
                new ParametreDto.Reglage(CLE_SEUIL_INSUFFISANCE, "Seuil d'insuffisance d'une note",
                        seuilInsuffisance(p), SEUIL_INSUFFISANCE_MIN, SEUIL_INSUFFISANCE_MAX, "ETOILES",
                        ParametresInstantane.SEUIL_INSUFFISANCE_DEFAUT, version));
    }

    /**
     * Modifie un réglage. 404 clé inconnue ; 400 {@code REGLAGE_HORS_BORNES} ; 409 si la version lue n'est plus la
     * version courante. La version est celle de la ligne de réglages (partagée par les deux réglages).
     */
    @Transactional
    public ParametreDto.Reglage modifierReglage(String cle, Integer valeur, Long version, Auteur auteur) {
        if (!CLE_DUREE_CAMPAGNE.equals(cle) && !CLE_SEUIL_INSUFFISANCE.equals(cle)) {
            throw EvaluationMetierException.notFound(REGLAGE_INCONNU, "Réglage inconnu : " + cle);
        }
        if (version == null) {
            throw EvaluationMetierException.badRequest(ParametreService.VERSION_OBLIGATOIRE,
                    "La version lue est obligatoire pour modifier un réglage.");
        }
        int min = CLE_DUREE_CAMPAGNE.equals(cle) ? CampagneCalendrier.DUREE_MIN_JOURS : SEUIL_INSUFFISANCE_MIN;
        int max = CLE_DUREE_CAMPAGNE.equals(cle) ? CampagneCalendrier.DUREE_MAX_JOURS : SEUIL_INSUFFISANCE_MAX;
        if (valeur == null || valeur < min || valeur > max) {
            throw EvaluationMetierException.badRequest(REGLAGE_HORS_BORNES,
                    "La valeur doit être comprise entre " + min + " et " + max + ".");
        }
        EvaluationParametres p = repository.findById(EvaluationParametres.IDENTIFIANT_UNIQUE).orElseGet(this::parDefaut);
        long courante = p.getVersion() == null ? 0 : p.getVersion();
        if (version != courante) {
            throw ParametreService.modifieEntreTemps();
        }
        int courant = CLE_DUREE_CAMPAGNE.equals(cle) ? p.getDureeCampagneJours() : seuilInsuffisance(p);
        if (courant == valeur) {
            // Aucun changement : ni écriture, ni entrée de journal, version inchangée
            return reglageDto(cle, valeur, min, max, courante);
        }
        int avant = courant;
        if (CLE_DUREE_CAMPAGNE.equals(cle)) {
            p.setDureeCampagneJours(valeur);
        } else {
            p.setSeuilInsuffisance(valeur);
        }
        EvaluationParametres sauvee = enregistrer(p, auteur);
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("valeur", valeur);
        journal.ecrire(ReferentielEvaluation.REGLAGES, cle, ActionJournalParametre.REGLAGE,
                Map.of("valeur", avant), apres, auteur);
        cache.invaliderApresCommit();
        long nouvelleVersion = sauvee.getVersion() == null ? 0 : sauvee.getVersion();
        return reglageDto(cle, valeur, min, max, nouvelleVersion);
    }

    private ParametreDto.Reglage reglageDto(String cle, int valeur, int min, int max, long version) {
        return new ParametreDto.Reglage(cle,
                CLE_DUREE_CAMPAGNE.equals(cle) ? "Durée d'une campagne" : "Seuil d'insuffisance d'une note",
                valeur, min, max, CLE_DUREE_CAMPAGNE.equals(cle) ? "JOURS" : "ETOILES",
                CLE_DUREE_CAMPAGNE.equals(cle) ? dureeDefautJours : ParametresInstantane.SEUIL_INSUFFISANCE_DEFAUT,
                version);
    }

    @Transactional(readOnly = true)
    public ParametreDto.PageJournal journalReglages(int page, int taille) {
        return journal.lire(ReferentielEvaluation.REGLAGES, page, taille);
    }

    // ------------------------------------------------------------------------------------------- outils

    private int seuilInsuffisance(EvaluationParametres p) {
        return p.getSeuilInsuffisance() != null ? p.getSeuilInsuffisance() : ParametresInstantane.SEUIL_INSUFFISANCE_DEFAUT;
    }

    private EvaluationParametres parDefaut() {
        EvaluationParametres p = new EvaluationParametres();
        p.setDureeCampagneJours(dureeDefautJours);
        return p;
    }

    private EvaluationParametres enregistrer(EvaluationParametres p, Auteur auteur) {
        p.setModifieLe(Instant.now());
        p.setModifiePar(auteur != null ? auteur.compteId() : null);
        try {
            return repository.saveAndFlush(p);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            throw ParametreService.modifieEntreTemps();
        }
    }
}
