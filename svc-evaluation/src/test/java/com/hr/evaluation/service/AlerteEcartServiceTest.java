package com.hr.evaluation.service;

import com.hr.evaluation.client.FicheEvaluation;
import com.hr.evaluation.client.ReferentielFicheClient;
import com.hr.evaluation.domain.EvenementAlerteEcart;
import com.hr.evaluation.domain.SensEcart;
import com.hr.evaluation.domain.StatutAlerteEcart;
import com.hr.evaluation.entity.AlerteEcart;
import com.hr.evaluation.entity.AlerteEcartLigne;
import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationAnswer;
import com.hr.evaluation.entity.EvaluationCampaign;
import com.hr.evaluation.entity.EvaluationQuestion;
import com.hr.evaluation.repository.AlerteEcartRepository;
import com.hr.evaluation.repository.EvaluationAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** D-E04 à D-E07 : détection à la validation du RO, recalcul, une alerte par évaluation, notification dosée. */
class AlerteEcartServiceTest {

    private final AlerteEcartRepository alerteRepository = mock(AlerteEcartRepository.class);
    private final EvaluationAnswerRepository answerRepository = mock(EvaluationAnswerRepository.class);
    private final TemplateTechniqueEvaluation templateTechnique = mock(TemplateTechniqueEvaluation.class);
    private final ReferentielFicheClient ficheClient = mock(ReferentielFicheClient.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private AlerteEcartService service;
    private Evaluation evaluation;
    private final UUID collabFiche = UUID.randomUUID();
    private final UUID collabCompte = UUID.randomUUID();
    private final UUID roFiche = UUID.randomUUID();
    private final UUID roCompte = UUID.randomUUID();
    private final List<EvaluationQuestion> questions = new ArrayList<>();
    private final List<EvaluationAnswer> reponses = new ArrayList<>();
    private AlerteEcart stockee;

    @BeforeEach
    void setUp() {
        service = new AlerteEcartService(alerteRepository, answerRepository, templateTechnique, ficheClient, publisher,
                ParametresTestes.parDefaut());
        EvaluationCampaign campagne = new EvaluationCampaign();
        campagne.setId(UUID.randomUUID());
        campagne.setNom("Campagne S2 2026");
        evaluation = new Evaluation();
        evaluation.setId(UUID.randomUUID());
        evaluation.setCampaign(campagne);
        evaluation.setCollaborateurIdentifiant(collabFiche);
        evaluation.setCollaborateurCompteId(collabCompte);
        evaluation.setCollaborateurNom("Sarra Khemiri");
        evaluation.setSuperieurIdentifiant(roFiche);
        evaluation.setSuperieurCompteId(roCompte);
        evaluation.setSuperieurNom("Omar Belhadj");

        when(templateTechnique.questions(evaluation)).thenReturn(questions);
        when(answerRepository.findWithQuestionByEvaluationId(evaluation.getId())).thenReturn(reponses);
        when(alerteRepository.findByEvaluationId(evaluation.getId())).thenAnswer(inv -> Optional.ofNullable(stockee));
        when(alerteRepository.findByEvaluationIdForUpdate(evaluation.getId())).thenAnswer(inv -> Optional.ofNullable(stockee));
        when(alerteRepository.save(any(AlerteEcart.class))).thenAnswer(inv -> {
            AlerteEcart a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            stockee = a;
            return a;
        });
        when(ficheClient.ficheEvaluation(collabFiche)).thenReturn(Optional.of(new FicheEvaluation(
                collabFiche, collabCompte, "M-042", "Sarra Khemiri", UUID.randomUUID(), "Service web", "ACTIF")));
    }

    private EvaluationQuestion question(String libelle, int ordre) {
        EvaluationQuestion q = new EvaluationQuestion();
        q.setId(UUID.randomUUID());
        q.setLibelle(libelle);
        q.setOrdre(ordre);
        questions.add(q);
        return q;
    }

    private EvaluationAnswer notes(EvaluationQuestion q, Integer collaborateur, Integer ro) {
        EvaluationAnswer a = new EvaluationAnswer();
        a.setQuestion(q);
        a.setNoteCollaborateur(collaborateur);
        a.setNoteAttribuee(collaborateur);
        a.setNoteManager(ro);
        reponses.add(a);
        return a;
    }

    private List<AlerteEcartANotifierEvent> evenementsPublies() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher, org.mockito.Mockito.atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(AlerteEcartANotifierEvent.class::isInstance)
                .map(AlerteEcartANotifierEvent.class::cast)
                .toList();
    }

    // ------------------------------------------------------------------------------------------ détection

    @Test
    void ecartDeDeux_alerteOrange_uneLigneRoInferieur_snapshotEtNotification() {
        EvaluationQuestion java = question("Maîtrise de Java et Spring Boot", 1);
        notes(java, 4, 2);

        Optional<AlerteEcart> alerte = service.detecter(evaluation);

        assertThat(alerte).isPresent();
        AlerteEcart a = alerte.get();
        assertThat(a.getNiveauCourant()).isEqualTo("ORANGE");
        assertThat(a.getNiveauDetection()).isEqualTo("ORANGE");
        assertThat(a.getNiveauMaxNotifie()).isEqualTo("ORANGE");
        assertThat(a.getStatut()).isEqualTo(StatutAlerteEcart.OUVERTE);
        assertThat(a.getCollaborateurMatricule()).isEqualTo("M-042");
        assertThat(a.getCollaborateurUniteLibelle()).isEqualTo("Service web");
        assertThat(a.getCampagneNom()).isEqualTo("Campagne S2 2026");
        assertThat(a.getLignes()).hasSize(1);
        AlerteEcartLigne ligne = a.getLignes().get(0);
        assertThat(ligne.getCompetenceLibelle()).isEqualTo("Maîtrise de Java et Spring Boot");
        assertThat(ligne.getEtoilesCollaborateurDetection()).isEqualTo(4);
        assertThat(ligne.getEtoilesRoDetection()).isEqualTo(2);
        assertThat(ligne.getEcartDetection()).isEqualTo(2);
        assertThat(ligne.getSensDetection()).isEqualTo(SensEcart.RO_INFERIEUR);
        assertThat(a.getHistorique()).singleElement().satisfies(h -> {
            assertThat(h.getTypeEvenement()).isEqualTo(EvenementAlerteEcart.CREATION);
            assertThat(h.getAuteurCompteId()).isNull();
            assertThat(h.getAuteurLibelle()).isEqualTo("SYSTEME");
            assertThat(h.getStatutApres()).isEqualTo(StatutAlerteEcart.OUVERTE);
            assertThat(h.getNiveauApres()).isEqualTo("ORANGE");
        });
        assertThat(evenementsPublies()).singleElement().satisfies(e -> {
            assertThat(e.alerteId()).isEqualTo(a.getId());
            assertThat(e.niveau()).isEqualTo("ORANGE");
            assertThat(e.nbCompetencesEnEcart()).isEqualTo(1);
            assertThat(e.ecartMax()).isEqualTo(2);
            assertThat(e.comptesExclus()).containsExactlyInAnyOrder(collabCompte, roCompte);
        });
    }

    @Test
    void ecartDeTrois_roSuperieur_rouge() {
        notes(question("Java", 1), 2, 5);

        AlerteEcart a = service.detecter(evaluation).orElseThrow();

        assertThat(a.getNiveauCourant()).isEqualTo("ROUGE");
        assertThat(a.getLignes().get(0).getSensDetection()).isEqualTo(SensEcart.RO_SUPERIEUR);
    }

    private AlerteEcartService serviceAvecNiveaux(ParametresTestes.Banc banc) {
        return new AlerteEcartService(alerteRepository, answerRepository, templateTechnique, ficheClient, publisher,
                banc.parametres());
    }

    private static ParametresInstantane avecNiveau(String code, String libelle, int seuil) {
        return ParametresInstantane.de(ParametresTestes.initiales().stream()
                .map(v -> v.referentiel().equals("NIVEAU_ALERTE") && v.code().equals(code)
                        ? new ParametresInstantane.Valeur(v.referentiel(), v.code(), v.nature(), libelle,
                                v.description(), v.couleur(), v.ordre(), true, v.systeme(), seuil)
                        : v).toList(), null);
    }

    @Test
    void seuilModifie_estAppliqueAuxDetectionsPosterieures_etLeTitreUtiliseLeLibelleCourant() {
        // ORANGE passe au seuil 1 et devient « Vigilance » : un écart d'une étoile déclenche désormais l'alerte
        var banc = ParametresTestes.avec(avecNiveau("ORANGE", "Vigilance", 1));
        // l'ancien seuil 2 de ORANGE n'est plus occupé : l'instantané ne contient plus de seuil 2 actif
        AlerteEcartService modifie = serviceAvecNiveaux(banc);
        notes(question("Java", 1), 3, 2);

        AlerteEcart a = modifie.detecter(evaluation).orElseThrow();

        assertThat(a.getNiveauCourant()).isEqualTo("ORANGE");
        assertThat(evenementsPublies()).singleElement().satisfies(e -> {
            assertThat(e.niveau()).isEqualTo("ORANGE");
            assertThat(e.niveauLibelle()).isEqualTo("Vigilance");
        });
        verify(banc.repository()).marquerUtilisee(org.mockito.ArgumentMatchers.eq("NIVEAU_ALERTE"),
                org.mockito.ArgumentMatchers.eq("ORANGE"), any());
    }

    @Test
    void seuilModifie_neRecalculePasLesAlertesExistantes_etSansChangementDeNotesLeNiveauResteLe() {
        notes(question("Java", 1), 4, 2);
        AlerteEcart a = service.detecter(evaluation).orElseThrow();
        assertThat(a.getNiveauCourant()).isEqualTo("ORANGE");

        // le seuil de ORANGE descend à 1 après la détection
        var banc = ParametresTestes.avec(avecNiveau("ORANGE", "Alerte", 1));
        AlerteEcartService modifie = serviceAvecNiveaux(banc);
        evaluation.setValidationSuperieurPar(UUID.randomUUID());
        modifie.recalculerSiNecessaire(evaluation, questions.get(0));

        assertThat(a.getNiveauCourant()).isEqualTo("ORANGE"); // notes inchangées : la ligne n'est pas recalculée
        assertThat(a.getLignes().get(0).getNiveauActuel()).isEqualTo("ORANGE");
    }

    @Test
    void cinqContreUnDansLesDeuxSens_urgente() {
        notes(question("Java", 1), 5, 1);
        assertThat(service.detecter(evaluation).orElseThrow().getNiveauCourant()).isEqualTo("URGENTE");

        stockee = null;
        reponses.clear();
        notes(questions.get(0), 1, 5);
        assertThat(service.detecter(evaluation).orElseThrow().getNiveauCourant()).isEqualTo("URGENTE");
    }

    @Test
    void troisCompetencesEcarts1_2_3_uneAlerteRouge_deuxLignes_texteDeLaNotification() {
        notes(question("A", 1), 3, 4); // écart 1 : ignoré
        notes(question("B", 2), 4, 2); // écart 2
        notes(question("C", 3), 1, 4); // écart 3

        AlerteEcart a = service.detecter(evaluation).orElseThrow();

        assertThat(a.getNiveauCourant()).isEqualTo("ROUGE");
        assertThat(a.getLignes()).hasSize(2);
        assertThat(evenementsPublies()).singleElement().satisfies(e -> {
            assertThat(e.nbCompetencesEnEcart()).isEqualTo(2);
            assertThat(e.ecartMax()).isEqualTo(3);
        });
    }

    @Test
    void tousLesEcartsInferieursOuEgauxAUn_aucuneAlerteAucuneNotification() {
        notes(question("A", 1), 3, 4);
        notes(question("B", 2), 5, 5);

        assertThat(service.detecter(evaluation)).isEmpty();

        verify(alerteRepository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void competenceDontUneNoteManque_ignoree() {
        notes(question("A", 1), 5, null);
        notes(question("B", 2), null, 1);

        assertThat(service.detecter(evaluation)).isEmpty();
    }

    @Test
    void noteHorsEchelle_ignoreeSansException() {
        notes(question("A", 1), 9, 1);
        notes(question("B", 2), 0, 5);

        assertThat(service.detecter(evaluation)).isEmpty();
    }

    @Test
    void questionHorsTemplateTechnique_neCompteJamais() {
        EvaluationQuestion generale = new EvaluationQuestion();
        generale.setId(UUID.randomUUID());
        generale.setLibelle("Question générale");
        notes(generale, 5, 1); // réponse existante mais question absente des questions techniques
        notes(question("Java", 1), 4, 3);

        assertThat(service.detecter(evaluation)).isEmpty();
    }

    @Test
    void sansTemplateTechnique_pasDAlerte() {
        assertThat(service.detecter(evaluation)).isEmpty();
        verifyNoInteractions(publisher);
    }

    @Test
    void noteCollaborateurAbsente_repliSurNoteAttribuee() {
        EvaluationQuestion q = question("Java", 1);
        EvaluationAnswer a = notes(q, null, 1);
        a.setNoteAttribuee(4);

        assertThat(service.detecter(evaluation).orElseThrow().getNiveauCourant()).isEqualTo("ROUGE");
    }

    @Test
    void uneAlerteExistanteNEstJamaisRecreee() {
        notes(question("Java", 1), 4, 2);
        service.detecter(evaluation);

        assertThat(service.detecter(evaluation)).isEmpty();
        verify(alerteRepository, times(1)).save(any());
        assertThat(evenementsPublies()).hasSize(1);
    }

    @Test
    void referentielIndisponible_alerteCreeeQuandMemeChampsNuls() {
        when(ficheClient.ficheEvaluation(any())).thenThrow(new IllegalStateException("panne"));
        notes(question("Java", 1), 5, 1);

        AlerteEcart a = service.detecter(evaluation).orElseThrow();

        assertThat(a.getCollaborateurMatricule()).isNull();
        assertThat(a.getCollaborateurUniteLibelle()).isNull();
        assertThat(a.getCollaborateurNom()).isEqualTo("Sarra Khemiri");
        assertThat(evenementsPublies()).hasSize(1);
    }

    @Test
    void comptesManquantsCompletesParLeReferentiel() {
        evaluation.setCollaborateurCompteId(null);
        evaluation.setSuperieurCompteId(null);
        UUID compteRoFiche = UUID.randomUUID();
        when(ficheClient.ficheEvaluation(roFiche)).thenReturn(Optional.of(new FicheEvaluation(
                roFiche, compteRoFiche, "M-001", "Omar Belhadj", null, null, "ACTIF")));
        notes(question("Java", 1), 5, 1);

        AlerteEcart a = service.detecter(evaluation).orElseThrow();

        assertThat(a.getCollaborateurCompteId()).isEqualTo(collabCompte);
        assertThat(a.getSuperieurCompteId()).isEqualTo(compteRoFiche);
        assertThat(evenementsPublies().get(0).comptesExclus()).containsExactlyInAnyOrder(collabCompte, compteRoFiche);
    }

    // -------------------------------------------------------------------------------------------- recalcul

    private AlerteEcart alerteOrangeExistante(EvaluationQuestion q) {
        evaluation.setValidationSuperieurPar(roCompte);
        notes(q, 4, 2);
        service.detecter(evaluation);
        org.mockito.Mockito.clearInvocations(publisher);
        return stockee;
    }

    @Test
    void avantLaValidationDuRo_aucunRecalcul() {
        EvaluationQuestion q = question("Java", 1);
        notes(q, 4, 2);

        service.recalculerSiNecessaire(evaluation, q); // validation_superieur_par nul

        verifyNoInteractions(alerteRepository);
        verifyNoInteractions(publisher);
    }

    @Test
    void questionHorsTemplateTechnique_aucunRecalcul() {
        EvaluationQuestion q = question("Java", 1);
        alerteOrangeExistante(q);
        EvaluationQuestion autre = new EvaluationQuestion();
        autre.setId(UUID.randomUUID());
        org.mockito.Mockito.clearInvocations(alerteRepository);

        service.recalculerSiNecessaire(evaluation, autre);

        verify(alerteRepository, never()).save(any());
    }

    @Test
    void roAbaisseSaNote_orangeVersRouge_valeursDeDetectionConserveesIndicateurPoseNouvelleNotification() {
        EvaluationQuestion q = question("Java", 1);
        AlerteEcart a = alerteOrangeExistante(q);
        reponses.get(0).setNoteManager(1); // collab 4, RO 2 -> 1

        service.recalculerSiNecessaire(evaluation, q);

        AlerteEcartLigne ligne = a.getLignes().get(0);
        assertThat(ligne.getEtoilesRoDetection()).isEqualTo(2);
        assertThat(ligne.getEtoilesRoActuelles()).isEqualTo(1);
        assertThat(ligne.getEcartActuel()).isEqualTo(3);
        assertThat(a.getNiveauCourant()).isEqualTo("ROUGE");
        assertThat(a.getNiveauDetection()).isEqualTo("ORANGE");
        assertThat(a.isNotesModifieesApresDetection()).isTrue();
        assertThat(a.getHistorique()).extracting(h -> h.getTypeEvenement())
                .containsExactly(EvenementAlerteEcart.CREATION, EvenementAlerteEcart.RECALCUL);
        assertThat(evenementsPublies()).singleElement().satisfies(e -> assertThat(e.niveau()).isEqualTo("ROUGE"));
    }

    @Test
    void roAlignesaNote_ecartZero_alerteConserveeNiveauAligneAucuneNotification() {
        EvaluationQuestion q = question("Java", 1);
        AlerteEcart a = alerteOrangeExistante(q);
        reponses.get(0).setNoteManager(4);

        service.recalculerSiNecessaire(evaluation, q);

        assertThat(a.getLignes()).hasSize(1);
        assertThat(a.getLignes().get(0).getNiveauActuel()).isEqualTo("ALIGNE");
        assertThat(a.getNiveauCourant()).isEqualTo("ALIGNE");
        assertThat(a.isNotesModifieesApresDetection()).isTrue();
        assertThat(a.getStatut()).isEqualTo(StatutAlerteEcart.OUVERTE);
        verifyNoInteractions(publisher);
    }

    @Test
    void collaborateurModifieSaNote_memeRecalcul() {
        EvaluationQuestion q = question("Java", 1);
        AlerteEcart a = alerteOrangeExistante(q);
        reponses.get(0).setNoteCollaborateur(5); // collab 4 -> 5, RO 2 : écart 3
        reponses.get(0).setNoteAttribuee(5);

        service.recalculerSiNecessaire(evaluation, q);

        assertThat(a.getNiveauCourant()).isEqualTo("ROUGE");
        assertThat(a.getLignes().get(0).getEtoilesCollaborateurActuelles()).isEqualTo(5);
        assertThat(a.getLignes().get(0).getEtoilesCollaborateurDetection()).isEqualTo(4);
        assertThat(evenementsPublies()).hasSize(1);
    }

    @Test
    void alerteTraitee_hausse_rouverteEtRenotifieeConclusionRemiseANull() {
        EvaluationQuestion q = question("Java", 1);
        AlerteEcart a = alerteOrangeExistante(q);
        a.setStatut(StatutAlerteEcart.TRAITEE);
        a.setConclusion("MANQUE_EFFORT");
        reponses.get(0).setNoteManager(1);

        service.recalculerSiNecessaire(evaluation, q);

        assertThat(a.getStatut()).isEqualTo(StatutAlerteEcart.OUVERTE);
        assertThat(a.getConclusion()).isNull();
        assertThat(a.getHistorique()).extracting(h -> h.getTypeEvenement())
                .containsExactly(EvenementAlerteEcart.CREATION, EvenementAlerteEcart.REOUVERTURE_AUTOMATIQUE);
        assertThat(a.getHistorique().get(1).getAuteurLibelle()).isEqualTo("SYSTEME");
        assertThat(evenementsPublies()).hasSize(1);
    }

    @Test
    void alerteTraitee_sansHausse_resteTraiteeRienNEstEnvoye() {
        EvaluationQuestion q = question("Java", 1);
        AlerteEcart a = alerteOrangeExistante(q);
        a.setStatut(StatutAlerteEcart.TRAITEE);
        reponses.get(0).setNoteManager(3); // écart 1 : baisse

        service.recalculerSiNecessaire(evaluation, q);

        assertThat(a.getStatut()).isEqualTo(StatutAlerteEcart.TRAITEE);
        verifyNoInteractions(publisher);
    }

    @Test
    void alerteTraiteeRouverteMemeSansDepasserLeMaxNotifie_estQuandMemeRenotifiee() {
        EvaluationQuestion q = question("Java", 1);
        AlerteEcart a = alerteOrangeExistante(q);
        // Hausse déjà notifiée jusqu'à ROUGE, puis baisse à ORANGE, alerte clôturée, nouvelle hausse à ROUGE.
        reponses.get(0).setNoteManager(1);
        service.recalculerSiNecessaire(evaluation, q);
        reponses.get(0).setNoteManager(2);
        service.recalculerSiNecessaire(evaluation, q);
        a.setStatut(StatutAlerteEcart.TRAITEE);
        org.mockito.Mockito.clearInvocations(publisher);
        reponses.get(0).setNoteManager(1);

        service.recalculerSiNecessaire(evaluation, q);

        assertThat(a.getStatut()).isEqualTo(StatutAlerteEcart.OUVERTE);
        assertThat(evenementsPublies()).hasSize(1);
    }

    @Test
    void alerteEnCours_hausse_resteEnCoursEtNotifie() {
        EvaluationQuestion q = question("Java", 1);
        AlerteEcart a = alerteOrangeExistante(q);
        a.setStatut(StatutAlerteEcart.EN_COURS);
        reponses.get(0).setNoteManager(1);

        service.recalculerSiNecessaire(evaluation, q);

        assertThat(a.getStatut()).isEqualTo(StatutAlerteEcart.EN_COURS);
        assertThat(evenementsPublies()).hasSize(1);
    }

    @Test
    void orangeRougeOrangeRouge_uneSeuleNotificationRouge_niveauMaxNotifie() {
        EvaluationQuestion q = question("Java", 1);
        AlerteEcart a = alerteOrangeExistante(q);

        reponses.get(0).setNoteManager(1); // ROUGE : notifié
        service.recalculerSiNecessaire(evaluation, q);
        reponses.get(0).setNoteManager(2); // ORANGE : baisse
        service.recalculerSiNecessaire(evaluation, q);
        reponses.get(0).setNoteManager(1); // ROUGE de nouveau : déjà notifié

        service.recalculerSiNecessaire(evaluation, q);

        List<AlerteEcartANotifierEvent> envoyes = evenementsPublies();
        assertThat(envoyes).hasSize(1);
        assertThat(envoyes.get(0).niveau()).isEqualTo("ROUGE");
        assertThat(a.getNiveauMaxNotifie()).isEqualTo("ROUGE");
    }

    @Test
    void ecartApparuApresValidationSansAlerte_alerteCreeeEtNotifiee() {
        EvaluationQuestion q = question("Java", 1);
        evaluation.setValidationSuperieurPar(roCompte);
        EvaluationAnswer a = notes(q, 3, 3); // validée sans alerte

        service.recalculerSiNecessaire(evaluation, q);
        verifyNoInteractions(publisher);

        a.setNoteManager(1); // écart 2 puis...
        a.setNoteManager(1);
        a.setNoteCollaborateur(3);
        service.recalculerSiNecessaire(evaluation, q);

        assertThat(stockee).isNotNull();
        assertThat(stockee.getNiveauCourant()).isEqualTo("ORANGE");
        assertThat(evenementsPublies()).hasSize(1);
    }

    @Test
    void nouvelleCompetenceEnEcartApresDetection_ligneAjouteeIndicateurPose() {
        EvaluationQuestion java = question("Java", 1);
        EvaluationQuestion sql = question("SQL", 2);
        AlerteEcart a = alerteOrangeExistante(java);
        EvaluationAnswer reponseSql = notes(sql, 5, 2); // écart 3 apparu après la détection

        service.recalculerSiNecessaire(evaluation, sql);

        assertThat(reponseSql).isNotNull();
        assertThat(a.getLignes()).hasSize(2);
        assertThat(a.isNotesModifieesApresDetection()).isTrue();
        assertThat(a.getNiveauCourant()).isEqualTo("ROUGE");
    }

    @Test
    void recalculSansChangement_aucunHistoriqueNiNotification() {
        EvaluationQuestion q = question("Java", 1);
        AlerteEcart a = alerteOrangeExistante(q);

        service.recalculerSiNecessaire(evaluation, q);

        assertThat(a.getHistorique()).hasSize(1);
        assertThat(a.isNotesModifieesApresDetection()).isFalse();
        verifyNoInteractions(publisher);
    }

    @Test
    void recalculRetenteLeSnapshotTantQueLeMatriculeEstNul() {
        org.mockito.Mockito.doThrow(new IllegalStateException("panne")).when(ficheClient).ficheEvaluation(collabFiche);
        EvaluationQuestion q = question("Java", 1);
        AlerteEcart a = alerteOrangeExistante(q);
        assertThat(a.getCollaborateurMatricule()).isNull();
        org.mockito.Mockito.doReturn(Optional.of(new FicheEvaluation(
                collabFiche, collabCompte, "M-042", "Sarra Khemiri", UUID.randomUUID(), "Service web", "ACTIF")))
                .when(ficheClient).ficheEvaluation(collabFiche);

        service.recalculerSiNecessaire(evaluation, q);

        assertThat(a.getCollaborateurMatricule()).isEqualTo("M-042");
    }
}
