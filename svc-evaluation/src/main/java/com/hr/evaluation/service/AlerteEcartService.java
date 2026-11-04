package com.hr.evaluation.service;

import com.hr.evaluation.client.FicheEvaluation;
import com.hr.evaluation.client.ReferentielFicheClient;
import com.hr.evaluation.domain.EvenementAlerteEcart;
import com.hr.evaluation.domain.NiveauEcart;
import com.hr.evaluation.domain.SensEcart;
import com.hr.evaluation.domain.StatutAlerteEcart;
import com.hr.evaluation.entity.AlerteEcart;
import com.hr.evaluation.entity.AlerteEcartHistorique;
import com.hr.evaluation.entity.AlerteEcartLigne;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationAnswer;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.repository.AlerteEcartRepository;
import com.hr.evaluation.repository.EvaluationAnswerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Détection et recalcul des alertes d'écart d'étoiles (D-E04 à D-E07, ADR M07 alertes d'écart § 5).
 * <ul>
 *   <li>Source : questions du template TECHNICAL de l'évaluation ; collaborateur = {@code COALESCE(note_collaborateur,
 *       note_attribuee)}, RO = {@code note_manager}.</li>
 *   <li>Détection à la validation du RO (même transaction) ; recalcul à chaque note technique enregistrée ensuite.</li>
 *   <li>La notification n'est jamais envoyée ici : un {@link AlerteEcartANotifierEvent} est publié, traité après
 *       commit.</li>
 * </ul>
 * Les appelants doivent avoir verrouillé l'évaluation ({@code findByIdForUpdate}) : l'ordre des verrous est toujours
 * évaluation puis alerte.
 */
@Service
public class AlerteEcartService {

    private static final Logger log = LoggerFactory.getLogger(AlerteEcartService.class);

    private final AlerteEcartRepository alerteRepository;
    private final EvaluationAnswerRepository answerRepository;
    private final TemplateTechniqueEvaluation templateTechnique;
    private final ReferentielFicheClient ficheClient;
    private final ApplicationEventPublisher eventPublisher;

    public AlerteEcartService(AlerteEcartRepository alerteRepository,
                              EvaluationAnswerRepository answerRepository,
                              TemplateTechniqueEvaluation templateTechnique,
                              ReferentielFicheClient ficheClient,
                              ApplicationEventPublisher eventPublisher) {
        this.alerteRepository = alerteRepository;
        this.answerRepository = answerRepository;
        this.templateTechnique = templateTechnique;
        this.ficheClient = ficheClient;
        this.eventPublisher = eventPublisher;
    }

    /** Notes valides d'une compétence : collaborateur et RO en étoiles 1 à 5. */
    private record Notes(int collaborateur, int ro) {
    }

    // ----------------------------------------------------------------------------------------------------------
    // Détection (D-E06.a)
    // ----------------------------------------------------------------------------------------------------------

    /**
     * Crée l'alerte si au moins une compétence technique a un écart d'au moins 2 étoiles ; sinon ne fait rien.
     * Ne recrée jamais une alerte existante (unicité par évaluation).
     */
    @Transactional
    public Optional<AlerteEcart> detecter(Evaluation evaluation) {
        if (alerteRepository.findByEvaluationId(evaluation.getId()).isPresent()) {
            return Optional.empty();
        }
        List<EvaluationQuestion> questions = templateTechnique.questions(evaluation);
        Map<EvaluationQuestion, Notes> notes = notesParQuestion(evaluation, questions);

        List<Map.Entry<EvaluationQuestion, Notes>> enEcart = notes.entrySet().stream()
                .filter(e -> EcartEtoiles.ecart(e.getValue().collaborateur(), e.getValue().ro()) >= EcartEtoiles.SEUIL_ORANGE)
                .toList();
        if (enEcart.isEmpty()) {
            return Optional.empty();
        }

        Instant maintenant = Instant.now();
        AlerteEcart alerte = new AlerteEcart();
        alerte.setEvaluation(evaluation);
        alerte.setCampagneIdentifiant(evaluation.getCampaign().getId());
        alerte.setCampagneNom(evaluation.getCampaign().getNom());
        alerte.setCollaborateurFicheId(evaluation.getCollaborateurIdentifiant());
        alerte.setCollaborateurCompteId(evaluation.getCollaborateurCompteId());
        alerte.setCollaborateurNom(evaluation.getCollaborateurNom());
        alerte.setSuperieurFicheId(evaluation.getSuperieurIdentifiant());
        alerte.setSuperieurCompteId(evaluation.getSuperieurCompteId());
        alerte.setSuperieurNom(evaluation.getSuperieurNom());
        completerSnapshot(alerte);

        NiveauEcart niveauMax = NiveauEcart.ALIGNE;
        for (Map.Entry<EvaluationQuestion, Notes> entree : enEcart) {
            AlerteEcartLigne ligne = nouvelleLigne(entree.getKey(), entree.getValue(), maintenant);
            alerte.ajouterLigne(ligne);
            niveauMax = NiveauEcart.max(niveauMax, ligne.getNiveauActuel());
        }
        alerte.setNiveauDetection(niveauMax);
        alerte.setNiveauCourant(niveauMax);
        alerte.setNiveauMaxNotifie(niveauMax);
        alerte.setStatut(StatutAlerteEcart.OUVERTE);
        alerte.setDetecteeLe(maintenant);
        alerte.ajouterHistorique(AlerteEcartHistorique.systeme(EvenementAlerteEcart.CREATION,
                null, StatutAlerteEcart.OUVERTE, null, niveauMax));

        // La contrainte d'unicité reste le garde-fou ; le verrou de l'évaluation neutralise la course.
        AlerteEcart sauvee = alerteRepository.save(alerte);
        publierNotification(sauvee);
        return Optional.of(sauvee);
    }

    // ----------------------------------------------------------------------------------------------------------
    // Recalcul (D-E06.c)
    // ----------------------------------------------------------------------------------------------------------

    /**
     * Appelé après l'enregistrement d'une note (collaborateur ou RO). Sans effet avant la validation du RO
     * ({@code validation_superieur_par} nul) et pour les questions hors template technique.
     */
    @Transactional
    public void recalculerSiNecessaire(Evaluation evaluation, EvaluationQuestion question) {
        if (evaluation.getValidationSuperieurPar() == null) {
            return;
        }
        List<EvaluationQuestion> questions = templateTechnique.questions(evaluation);
        boolean technique = questions.stream().anyMatch(q -> q.getId().equals(question.getId()));
        if (!technique) {
            return;
        }
        Optional<AlerteEcart> existante = alerteRepository.findByEvaluationIdForUpdate(evaluation.getId());
        if (existante.isEmpty()) {
            detecter(evaluation); // Q-C : un écart apparu après la validation crée l'alerte
            return;
        }
        recalculer(existante.get(), notesParQuestion(evaluation, questions));
    }

    private void recalculer(AlerteEcart alerte, Map<EvaluationQuestion, Notes> notes) {
        Instant maintenant = Instant.now();
        Map<UUID, AlerteEcartLigne> lignes = new HashMap<>();
        for (AlerteEcartLigne ligne : alerte.getLignes()) {
            lignes.put(ligne.getQuestionIdentifiant(), ligne);
        }

        boolean modifie = false;
        for (Map.Entry<EvaluationQuestion, Notes> entree : notes.entrySet()) {
            EvaluationQuestion question = entree.getKey();
            Notes n = entree.getValue();
            int ecart = EcartEtoiles.ecart(n.collaborateur(), n.ro());
            AlerteEcartLigne ligne = lignes.get(question.getId());
            if (ligne != null) {
                if (ligne.getEtoilesCollaborateurActuelles() != n.collaborateur()
                        || ligne.getEtoilesRoActuelles() != n.ro()) {
                    ligne.setEtoilesCollaborateurActuelles(n.collaborateur());
                    ligne.setEtoilesRoActuelles(n.ro());
                    ligne.setEcartActuel(ecart);
                    ligne.setNiveauActuel(EcartEtoiles.niveau(ecart));
                    ligne.setSensActuel(EcartEtoiles.sens(n.collaborateur(), n.ro()));
                    ligne.setModifieeLe(maintenant);
                    modifie = true;
                }
                if (ligne.getEtoilesCollaborateurActuelles() != ligne.getEtoilesCollaborateurDetection()
                        || ligne.getEtoilesRoActuelles() != ligne.getEtoilesRoDetection()) {
                    alerte.setNotesModifieesApresDetection(true); // indicateur collant
                }
            } else if (ecart >= EcartEtoiles.SEUIL_ORANGE) {
                alerte.ajouterLigne(nouvelleLigne(question, n, maintenant));
                alerte.setNotesModifieesApresDetection(true);
                modifie = true;
            }
        }

        NiveauEcart ancien = alerte.getNiveauCourant();
        NiveauEcart nouveau = alerte.getLignes().stream()
                .map(AlerteEcartLigne::getNiveauActuel)
                .reduce(NiveauEcart.ALIGNE, NiveauEcart::max);
        boolean hausse = nouveau.compareTo(ancien) > 0;
        boolean reouverture = false;

        if (hausse && alerte.getStatut() == StatutAlerteEcart.TRAITEE) {
            alerte.ajouterHistorique(AlerteEcartHistorique.systeme(EvenementAlerteEcart.REOUVERTURE_AUTOMATIQUE,
                    StatutAlerteEcart.TRAITEE, StatutAlerteEcart.OUVERTE, ancien, nouveau));
            alerte.setStatut(StatutAlerteEcart.OUVERTE);
            alerte.setConclusion(null); // l'ancienne reste dans l'historique
            reouverture = true;
        } else if (nouveau != ancien) {
            alerte.ajouterHistorique(AlerteEcartHistorique.systeme(EvenementAlerteEcart.RECALCUL,
                    alerte.getStatut(), alerte.getStatut(), ancien, nouveau));
        }
        alerte.setNiveauCourant(nouveau);

        boolean notifier = nouveau.compareTo(alerte.getNiveauMaxNotifie()) > 0 || reouverture;
        if (notifier) {
            alerte.setNiveauMaxNotifie(NiveauEcart.max(alerte.getNiveauMaxNotifie(), nouveau));
        }

        if (alerte.getCollaborateurMatricule() == null) {
            completerSnapshot(alerte); // nouvel essai tant que le référentiel n'a pas répondu
        }
        if (modifie || nouveau != ancien || reouverture) {
            alerte.setModifieeLe(maintenant);
        }
        alerteRepository.save(alerte);
        if (notifier) {
            publierNotification(alerte);
        }
    }

    // ----------------------------------------------------------------------------------------------------------
    // Outils
    // ----------------------------------------------------------------------------------------------------------

    private Map<EvaluationQuestion, Notes> notesParQuestion(Evaluation evaluation, List<EvaluationQuestion> questions) {
        Map<EvaluationQuestion, Notes> resultat = new LinkedHashMap<>();
        if (questions.isEmpty()) {
            return resultat;
        }
        Map<UUID, EvaluationAnswer> reponses = new HashMap<>();
        for (EvaluationAnswer reponse : answerRepository.findWithQuestionByEvaluationId(evaluation.getId())) {
            reponses.put(reponse.getQuestion().getId(), reponse);
        }
        for (EvaluationQuestion question : questions) {
            EvaluationAnswer reponse = reponses.get(question.getId());
            if (reponse == null) {
                continue;
            }
            Integer collaborateur = reponse.getNoteCollaborateur() != null
                    ? reponse.getNoteCollaborateur() : reponse.getNoteAttribuee();
            Integer ro = reponse.getNoteManager();
            if (collaborateur == null || ro == null) {
                continue; // une des deux notes manque : compétence ignorée
            }
            if (!EcartEtoiles.estEtoilesValide(collaborateur) || !EcartEtoiles.estEtoilesValide(ro)) {
                log.warn("Évaluation {} : note hors 1-5 ignorée sur la question {} (collaborateur={}, ro={})",
                        evaluation.getId(), question.getId(), collaborateur, ro);
                continue;
            }
            resultat.put(question, new Notes(collaborateur, ro));
        }
        return resultat;
    }

    private static AlerteEcartLigne nouvelleLigne(EvaluationQuestion question, Notes n, Instant maintenant) {
        int ecart = EcartEtoiles.ecart(n.collaborateur(), n.ro());
        NiveauEcart niveau = EcartEtoiles.niveau(ecart);
        SensEcart sens = EcartEtoiles.sens(n.collaborateur(), n.ro());
        AlerteEcartLigne ligne = new AlerteEcartLigne();
        ligne.setQuestionIdentifiant(question.getId());
        ligne.setCompetenceLibelle(question.getLibelle());
        ligne.setOrdre(question.getOrdre());
        ligne.setEtoilesCollaborateurDetection(n.collaborateur());
        ligne.setEtoilesRoDetection(n.ro());
        ligne.setEcartDetection(ecart);
        ligne.setNiveauDetection(niveau);
        ligne.setSensDetection(sens);
        ligne.setEtoilesCollaborateurActuelles(n.collaborateur());
        ligne.setEtoilesRoActuelles(n.ro());
        ligne.setEcartActuel(ecart);
        ligne.setNiveauActuel(niveau);
        ligne.setSensActuel(sens);
        ligne.setDetecteeLe(maintenant);
        return ligne;
    }

    /**
     * Matricule, unité et comptes manquants lus au référentiel. Référentiel indisponible : l'alerte est créée quand
     * même (champs nuls, avertissement).
     */
    private void completerSnapshot(AlerteEcart alerte) {
        fiche(alerte.getCollaborateurFicheId()).ifPresent(f -> {
            alerte.setCollaborateurMatricule(f.matricule());
            alerte.setCollaborateurUniteId(f.uniteIdentifiant());
            alerte.setCollaborateurUniteLibelle(f.uniteLibelle());
            if (alerte.getCollaborateurCompteId() == null) {
                alerte.setCollaborateurCompteId(f.compteUtilisateurId());
            }
            if (alerte.getCollaborateurNom() == null) {
                alerte.setCollaborateurNom(f.nomComplet());
            }
        });
        if (alerte.getSuperieurCompteId() == null || alerte.getSuperieurNom() == null) {
            fiche(alerte.getSuperieurFicheId()).ifPresent(f -> {
                if (alerte.getSuperieurCompteId() == null) {
                    alerte.setSuperieurCompteId(f.compteUtilisateurId());
                }
                if (alerte.getSuperieurNom() == null) {
                    alerte.setSuperieurNom(f.nomComplet());
                }
            });
        }
    }

    private Optional<FicheEvaluation> fiche(UUID ficheId) {
        try {
            return ficheClient.ficheEvaluation(ficheId);
        } catch (RuntimeException ex) {
            log.warn("Fiche {} illisible au référentiel (snapshot d'alerte incomplet) : {}", ficheId, ex.getMessage());
            return Optional.empty();
        }
    }

    private void publierNotification(AlerteEcart alerte) {
        List<AlerteEcartLigne> enEcart = alerte.getLignes().stream()
                .filter(l -> l.getNiveauActuel().estAlerte())
                .toList();
        int ecartMax = enEcart.stream().mapToInt(AlerteEcartLigne::getEcartActuel).max().orElse(0);
        Set<UUID> exclus = new HashSet<>();
        if (alerte.getCollaborateurCompteId() != null) {
            exclus.add(alerte.getCollaborateurCompteId());
        }
        if (alerte.getSuperieurCompteId() != null) {
            exclus.add(alerte.getSuperieurCompteId());
        }
        eventPublisher.publishEvent(new AlerteEcartANotifierEvent(
                alerte.getId(),
                alerte.getNiveauCourant(),
                alerte.getCollaborateurNom(),
                alerte.getCampagneNom(),
                enEcart.size(),
                ecartMax,
                Set.copyOf(exclus)));
    }
}
