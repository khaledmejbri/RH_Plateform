package com.hr.evaluation.service;

import com.hr.evaluation.domain.EvenementAlerteEcart;
import com.hr.evaluation.domain.SensEcart;
import com.hr.evaluation.domain.StatutAlerteEcart;
import com.hr.evaluation.dto.AlerteEcartDetail;
import com.hr.evaluation.dto.AlerteEcartPage;
import com.hr.evaluation.entity.AlerteEcart;
import com.hr.evaluation.entity.AlerteEcartHistorique;
import com.hr.evaluation.entity.AlerteEcartLigne;
import com.hr.evaluation.repository.AlerteEcartRepository;
import com.hr.evaluation.security.IdentiteAppelant;
import com.hr.evaluation.web.EvaluationMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-E08 / D-E09 : traitement RH, conflit d'intérêts, précédence des erreurs, liste triée et paginée. */
class AlerteEcartTraitementServiceTest {

    private final AlerteEcartRepository repository = mock(AlerteEcartRepository.class);
    private AlerteEcartTraitementService service;

    private final UUID rhCompte = UUID.randomUUID();
    private final UUID collabCompte = UUID.randomUUID();
    private final UUID roCompte = UUID.randomUUID();
    private AlerteEcart alerte;

    private IdentiteAppelant rh() {
        return new IdentiteAppelant(rhCompte, "drh01", true, Optional::empty);
    }

    private IdentiteAppelant direction() {
        return new IdentiteAppelant(UUID.randomUUID(), "dg01", false, Optional::empty);
    }

    private IdentiteAppelant concerne(UUID compte) {
        return new IdentiteAppelant(compte, "concerne", true, Optional::empty);
    }

    @BeforeEach
    void setUp() {
        service = new AlerteEcartTraitementService(repository, ParametresTestes.parDefaut());
        alerte = nouvelleAlerte("ROUGE", StatutAlerteEcart.OUVERTE, 3);
        when(repository.findByIdForUpdate(alerte.getId())).thenReturn(Optional.of(alerte));
        when(repository.findById(alerte.getId())).thenReturn(Optional.of(alerte));
        when(repository.save(any(AlerteEcart.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AlerteEcart nouvelleAlerte(String niveau, StatutAlerteEcart statut, int joursPasses) {
        AlerteEcart a = new AlerteEcart();
        a.setId(UUID.randomUUID());
        a.setCollaborateurCompteId(collabCompte);
        a.setCollaborateurFicheId(UUID.randomUUID());
        a.setSuperieurCompteId(roCompte);
        a.setSuperieurFicheId(UUID.randomUUID());
        a.setCollaborateurNom("Sarra Khemiri");
        a.setNiveauCourant(niveau);
        a.setNiveauDetection(niveau);
        a.setNiveauMaxNotifie(niveau);
        a.setStatut(statut);
        a.setDetecteeLe(Instant.now().minus(joursPasses, ChronoUnit.DAYS));
        AlerteEcartLigne l = new AlerteEcartLigne();
        l.setQuestionIdentifiant(UUID.randomUUID());
        l.setCompetenceLibelle("Java");
        l.setOrdre(1);
        l.setEtoilesCollaborateurDetection(5);
        l.setEtoilesRoDetection(2);
        l.setEcartDetection(3);
        l.setNiveauDetection("ROUGE");
        l.setSensDetection(SensEcart.RO_INFERIEUR);
        l.setEtoilesCollaborateurActuelles(5);
        l.setEtoilesRoActuelles(2);
        l.setEcartActuel(3);
        l.setNiveauActuel("ROUGE");
        l.setSensActuel(SensEcart.RO_INFERIEUR);
        a.ajouterLigne(l);
        return a;
    }

    private static void assertErreur(Throwable t, int statut, String code) {
        assertThat(t).isInstanceOfSatisfying(EvaluationMetierException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(statut);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // --------------------------------------------------------------------------------------- prise en charge

    @Test
    void priseEnChargeSansCommentaire_enCoursEtHistoriqueAuteurDuJwt() {
        AlerteEcartDetail detail = service.prendreEnCharge(alerte.getId(), null, rh());

        assertThat(detail.statut()).isEqualTo(StatutAlerteEcart.EN_COURS);
        assertThat(alerte.getHistorique()).singleElement().satisfies(h -> {
            assertThat(h.getTypeEvenement()).isEqualTo(EvenementAlerteEcart.PRISE_EN_CHARGE);
            assertThat(h.getAuteurCompteId()).isEqualTo(rhCompte);
            assertThat(h.getAuteurLibelle()).isEqualTo("drh01");
            assertThat(h.getStatutAvant()).isEqualTo(StatutAlerteEcart.OUVERTE);
            assertThat(h.getStatutApres()).isEqualTo(StatutAlerteEcart.EN_COURS);
            assertThat(h.getCommentaire()).isNull();
        });
    }

    @Test
    void commentaireDePriseEnCharge_1000Accepte_1001Refuse() {
        service.prendreEnCharge(alerte.getId(), "x".repeat(1000), rh());
        assertThat(alerte.getStatut()).isEqualTo(StatutAlerteEcart.EN_COURS);

        AlerteEcart autre = nouvelleAlerte("ORANGE", StatutAlerteEcart.OUVERTE, 1);
        when(repository.findByIdForUpdate(autre.getId())).thenReturn(Optional.of(autre));
        assertThatThrownBy(() -> service.prendreEnCharge(autre.getId(), "x".repeat(1001), rh()))
                .satisfies(t -> assertErreur(t, 400, "VALIDATION_ERROR"));
        assertThat(autre.getStatut()).isEqualTo(StatutAlerteEcart.OUVERTE);
        assertThat(autre.getHistorique()).isEmpty();
    }

    @Test
    void priseEnChargeHorsOuverte_409AvecStatutActuel() {
        alerte.setStatut(StatutAlerteEcart.EN_COURS);
        assertThatThrownBy(() -> service.prendreEnCharge(alerte.getId(), null, rh())).satisfies(t -> {
            assertErreur(t, 409, "ALERTE_ETAT_INVALIDE");
            assertThat(((EvaluationMetierException) t).getDonnees()).containsEntry("statutActuel", "EN_COURS");
        });

        alerte.setStatut(StatutAlerteEcart.TRAITEE);
        assertThatThrownBy(() -> service.prendreEnCharge(alerte.getId(), null, rh()))
                .satisfies(t -> assertErreur(t, 409, "ALERTE_ETAT_INVALIDE"));
        assertThat(alerte.getHistorique()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------- clôture

    private static final String COMMENTAIRE_OK = "Échange avec les deux parties, écart expliqué.";

    @Test
    void clotureDirecteDepuisOuverte_traiteeAvecConclusionEtHistorique() {
        AlerteEcartDetail detail = service.cloturer(alerte.getId(), "MANQUE_EFFORT", COMMENTAIRE_OK, rh());

        assertThat(detail.statut()).isEqualTo(StatutAlerteEcart.TRAITEE);
        assertThat(detail.conclusion()).isEqualTo("MANQUE_EFFORT");
        assertThat(detail.conclusionLibelle()).isEqualTo("Manque d'effort confirmé");
        assertThat(alerte.getHistorique()).singleElement().satisfies(h -> {
            assertThat(h.getTypeEvenement()).isEqualTo(EvenementAlerteEcart.CLOTURE);
            assertThat(h.getStatutAvant()).isEqualTo(StatutAlerteEcart.OUVERTE);
            assertThat(h.getConclusion()).isEqualTo("MANQUE_EFFORT");
            assertThat(h.getCommentaire()).isEqualTo(COMMENTAIRE_OK);
            assertThat(h.getAuteurCompteId()).isEqualTo(rhCompte);
        });
    }

    @Test
    void clotureDepuisEnCours_acceptee() {
        alerte.setStatut(StatutAlerteEcart.EN_COURS);
        service.cloturer(alerte.getId(), "AUTRE", "x".repeat(30), rh());
        assertThat(alerte.getStatut()).isEqualTo(StatutAlerteEcart.TRAITEE);
    }

    @Test
    void lesSeptConclusionsSontAcceptees() {
        String[] codes = {"MANQUE_EFFORT", "SURESTIMATION_COLLABORATEUR", "SUIVI_INSUFFISANT_RO",
                "SOUS_ESTIMATION_COLLABORATEUR", "NOTE_GONFLEE_RO", "ECART_JUSTIFIE", "AUTRE"};
        for (String c : codes) {
            alerte.setStatut(StatutAlerteEcart.OUVERTE);
            service.cloturer(alerte.getId(), c, COMMENTAIRE_OK, rh());
            assertThat(alerte.getConclusion()).isEqualTo(c);
        }
        assertThat(codes).hasSize(7);
    }

    @Test
    void conclusionDesactiveeEnBase_refuseMemeSiLeCacheDe30sLaCroitActive() {
        // le cache (instantané) voit la conclusion active ; la base vient de la désactiver
        var banc = ParametresTestes.avec(ParametresInstantane.de(ParametresTestes.initiales(), null));
        var desactivee = ParametresTestes.initiales().stream()
                .filter(v -> v.referentiel().equals("CONCLUSION_ALERTE") && v.code().equals("MANQUE_EFFORT"))
                .findFirst().orElseThrow();
        var entite = ParametresTestes.enEntite(desactivee);
        entite.setActif(false);
        when(banc.repository().findByReferentielAndCode("CONCLUSION_ALERTE", "MANQUE_EFFORT"))
                .thenReturn(Optional.of(entite));
        service = new AlerteEcartTraitementService(repository, banc.parametres());

        assertThatThrownBy(() -> service.cloturer(alerte.getId(), "MANQUE_EFFORT", COMMENTAIRE_OK, rh()))
                .satisfies(t -> assertErreur(t, 422, "CONCLUSION_INACTIVE"));
        assertThat(alerte.getStatut()).isEqualTo(StatutAlerteEcart.OUVERTE);
    }

    @Test
    void clotureInvalide_400_rienNeChange() {
        String[][] cas = {
                {null, COMMENTAIRE_OK},
                {"INCONNUE", COMMENTAIRE_OK},
                {"", COMMENTAIRE_OK},
                {"MANQUE_EFFORT", null},
                {"MANQUE_EFFORT", "123456789"},
                {"MANQUE_EFFORT", " ".repeat(10)},
                {"MANQUE_EFFORT", "x".repeat(1001)},
        };
        for (String[] c : cas) {
            assertThatThrownBy(() -> service.cloturer(alerte.getId(), c[0], c[1], rh()))
                    .satisfies(t -> assertErreur(t, 400, "VALIDATION_ERROR"));
        }
        assertThat(alerte.getStatut()).isEqualTo(StatutAlerteEcart.OUVERTE);
        assertThat(alerte.getConclusion()).isNull();
        assertThat(alerte.getHistorique()).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void commentaireDeCloture_bornes10Et1000Acceptees() {
        service.cloturer(alerte.getId(), "AUTRE", "x".repeat(10), rh());
        alerte.setStatut(StatutAlerteEcart.OUVERTE);
        service.cloturer(alerte.getId(), "AUTRE", "x".repeat(1000), rh());
        assertThat(alerte.getStatut()).isEqualTo(StatutAlerteEcart.TRAITEE);
    }

    @Test
    void commentaireDeCloture_maximumMesureSurTexteNettoye() {
        String commentaire = "x".repeat(995) + " ".repeat(10);
        AlerteEcartDetail detail = service.cloturer(alerte.getId(), "AUTRE", commentaire, rh());
        assertThat(detail.statut()).isEqualTo(StatutAlerteEcart.TRAITEE);
    }

    @Test
    void clotureSurTraitee_409() {
        alerte.setStatut(StatutAlerteEcart.TRAITEE);
        assertThatThrownBy(() -> service.cloturer(alerte.getId(), "AUTRE", COMMENTAIRE_OK, rh()))
                .satisfies(t -> {
                    assertErreur(t, 409, "ALERTE_ETAT_INVALIDE");
                    assertThat(((EvaluationMetierException) t).getDonnees()).containsEntry("statutActuel", "TRAITEE");
                });
        assertThat(alerte.getHistorique()).isEmpty();
    }

    // ------------------------------------------------------------------------ précédence et conflit d'intérêts

    @Test
    void alerteInexistante_404AvantTout() {
        UUID inconnue = UUID.randomUUID();
        when(repository.findByIdForUpdate(inconnue)).thenReturn(Optional.empty());
        when(repository.findById(inconnue)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prendreEnCharge(inconnue, null, rh()))
                .satisfies(t -> assertErreur(t, 404, "ALERTE_INTROUVABLE"));
        assertThatThrownBy(() -> service.cloturer(inconnue, null, null, rh()))
                .satisfies(t -> assertErreur(t, 404, "ALERTE_INTROUVABLE"));
        assertThatThrownBy(() -> service.detail(inconnue, rh()))
                .satisfies(t -> assertErreur(t, 404, "ALERTE_INTROUVABLE"));
    }

    @Test
    void collaborateurOuRoConcerne_403ConflitInterets_memeSurAlerteTraiteeEtAvantValidation() {
        alerte.setStatut(StatutAlerteEcart.TRAITEE);
        for (UUID compte : List.of(collabCompte, roCompte)) {
            assertThatThrownBy(() -> service.cloturer(alerte.getId(), null, null, concerne(compte)))
                    .satisfies(t -> assertErreur(t, 403, "CONFLIT_INTERETS"));
            assertThatThrownBy(() -> service.prendreEnCharge(alerte.getId(), "x".repeat(2000), concerne(compte)))
                    .satisfies(t -> assertErreur(t, 403, "CONFLIT_INTERETS"));
        }
        assertThat(alerte.getHistorique()).isEmpty();
    }

    @Test
    void conflitDInteretsParRepliFiche_quandLeCompteDuSnapshotManque() {
        UUID ficheRh = UUID.randomUUID();
        alerte.setCollaborateurCompteId(null);
        alerte.setCollaborateurFicheId(ficheRh);
        IdentiteAppelant rhEvalue = new IdentiteAppelant(rhCompte, "rh02", true,
                () -> Optional.of(new com.hr.evaluation.client.MoiReferentiel(ficheRh, "Rh", "Deux")));

        assertThatThrownBy(() -> service.cloturer(alerte.getId(), "AUTRE", COMMENTAIRE_OK, rhEvalue))
                .satisfies(t -> assertErreur(t, 403, "CONFLIT_INTERETS"));
    }

    @Test
    void validationAvantEtat_400AvantLe409() {
        alerte.setStatut(StatutAlerteEcart.TRAITEE);
        assertThatThrownBy(() -> service.cloturer(alerte.getId(), "AUTRE", "court", rh()))
                .satisfies(t -> assertErreur(t, 400, "VALIDATION_ERROR"));
    }

    @Test
    void directionNePeutPasAgir() {
        assertThatThrownBy(() -> service.prendreEnCharge(alerte.getId(), null, direction()))
                .satisfies(t -> assertErreur(t, 403, "ACCESS_DENIED"));
        assertThat(alerte.getStatut()).isEqualTo(StatutAlerteEcart.OUVERTE);
    }

    // ------------------------------------------------------------------------------------------------ détail

    @Test
    void detailExposeLibellesSensLignesHistoriqueEtPeutTraiter() {
        alerte.ajouterHistorique(AlerteEcartHistorique.systeme(EvenementAlerteEcart.CREATION,
                null, StatutAlerteEcart.OUVERTE, null, "ROUGE"));

        AlerteEcartDetail d = service.detail(alerte.getId(), rh());

        assertThat(d.peutTraiter()).isTrue();
        assertThat(d.motifNonTraitable()).isNull();
        assertThat(d.sensGlobal()).isEqualTo("RO_INFERIEUR");
        assertThat(d.libelleSensGlobal()).startsWith("Note du responsable plus basse");
        assertThat(d.aideSensGlobal()).contains("Vérifier");
        assertThat(d.nbCompetencesEnEcart()).isEqualTo(1);
        assertThat(d.ecartMax()).isEqualTo(3);
        assertThat(d.lignes()).singleElement().satisfies(l -> {
            assertThat(l.etoilesCollaborateurDetection()).isEqualTo(5);
            assertThat(l.etoilesRoActuelles()).isEqualTo(2);
            assertThat(l.libelleSens()).startsWith("Note du responsable plus basse");
        });
        assertThat(d.historique()).singleElement().satisfies(h -> {
            assertThat(h.auteurSysteme()).isTrue();
            // D-L01.e : libellé lisible exposé, le code SYSTEME reste stocké
            assertThat(h.auteurLibelle()).isEqualTo("Système (automatique)");
            assertThat(h.typeEvenementLibelle()).isEqualTo("Création");
            // première ligne : statut avant vide présenté comme « Création »
            assertThat(h.statutAvant()).isNull();
            assertThat(h.statutAvantLibelle()).isEqualTo("Création");
            assertThat(h.statutApresLibelle()).isEqualTo("Ouverte");
        });
    }

    @Test
    void detailDansLesDeuxSens_libelleDedie() {
        AlerteEcartLigne l2 = new AlerteEcartLigne();
        l2.setQuestionIdentifiant(UUID.randomUUID());
        l2.setEtoilesCollaborateurActuelles(1);
        l2.setEtoilesRoActuelles(4);
        l2.setEcartActuel(3);
        l2.setNiveauActuel("ROUGE");
        l2.setSensActuel(SensEcart.RO_SUPERIEUR);
        alerte.ajouterLigne(l2);

        AlerteEcartDetail d = service.detail(alerte.getId(), rh());

        assertThat(d.sensGlobal()).isEqualTo("DEUX_SENS");
        assertThat(d.libelleSensGlobal()).isEqualTo("Écarts dans les deux sens");
    }

    @Test
    void detail_directionLectureSeule_concerneNonTraitable_autreRhTraitable() {
        AlerteEcartDetail dir = service.detail(alerte.getId(), direction());
        assertThat(dir.peutTraiter()).isFalse();
        assertThat(dir.motifNonTraitable()).isEqualTo("LECTURE_SEULE");

        AlerteEcartDetail concerneRo = service.detail(alerte.getId(), concerne(roCompte));
        assertThat(concerneRo.peutTraiter()).isFalse();
        assertThat(concerneRo.motifNonTraitable()).isEqualTo("CONCERNE");

        assertThat(service.detail(alerte.getId(), rh()).peutTraiter()).isTrue();
    }

    // ----------------------------------------------------------------------------------------- liste, compteur

    @SuppressWarnings("unchecked")
    private void alertesEnBase(List<AlerteEcart> liste) {
        when(repository.findAll(any(Specification.class))).thenReturn(liste);
    }

    @Test
    void listeTrieeAvantPagination_20Puis5() {
        List<AlerteEcart> liste = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            liste.add(nouvelleAlerte("ORANGE", StatutAlerteEcart.OUVERTE, i + 1));
        }
        AlerteEcart urgente = nouvelleAlerte("URGENTE", StatutAlerteEcart.OUVERTE, 0);
        liste.set(24, urgente);
        alertesEnBase(liste);

        AlerteEcartPage page0 = service.lister(null, null, null, null, null, 0, 20);
        AlerteEcartPage page1 = service.lister(null, null, null, null, null, 1, 20);

        assertThat(page0.elements()).hasSize(20);
        assertThat(page0.total()).isEqualTo(25);
        assertThat(page0.elements().get(0).identifiant()).isEqualTo(urgente.getId());
        assertThat(page1.elements()).hasSize(5);
        assertThat(page1.page()).isEqualTo(1);
    }

    @Test
    void listeVide_elementsVidesTotalZero() {
        alertesEnBase(List.of());
        AlerteEcartPage page = service.lister(null, null, null, null, null, 0, 20);
        assertThat(page.elements()).isEmpty();
        assertThat(page.total()).isZero();
    }

    @Test
    void parametresDePaginationInvalides_400() {
        assertThatThrownBy(() -> service.lister(null, null, null, null, null, -1, 20))
                .satisfies(t -> assertErreur(t, 400, "PARAMETRE_INVALIDE"));
        assertThatThrownBy(() -> service.lister(null, null, null, null, null, 0, 101))
                .satisfies(t -> assertErreur(t, 400, "PARAMETRE_INVALIDE"));
        assertThatThrownBy(() -> service.lister(null, null, null, null, null, 0, 0))
                .satisfies(t -> assertErreur(t, 400, "PARAMETRE_INVALIDE"));
        service.lister(null, null, null, null, null, 0, 100);
    }

    private static long nombre(com.hr.evaluation.dto.AlerteEcartOuvertesParNiveau r, String code) {
        return r.niveaux().stream().filter(n -> n.code().equals(code)).findFirst().orElseThrow().nombre();
    }

    @Test
    void compteur_nonClosesDontUrgentes() {
        when(repository.countByStatutNot(StatutAlerteEcart.TRAITEE)).thenReturn(4L);
        when(repository.countByStatutNotAndNiveauCourantIn(StatutAlerteEcart.TRAITEE, List.of("URGENTE"))).thenReturn(1L);

        var c = service.compteur();

        assertThat(c.nonCloses()).isEqualTo(4);
        assertThat(c.urgentesNonCloses()).isEqualTo(1);
    }

    @Test
    void lAlerteNeTouchePasLEvaluation() {
        // Le service de traitement n'a aucune dépendance sur l'évaluation, le score ou la couleur (D-E08.d) :
        // son seul collaborateur est le dépôt des alertes.
        assertThat(AlerteEcartTraitementService.class.getDeclaredConstructors()[0].getParameterTypes())
                .containsExactly(AlerteEcartRepository.class, ParametresEvaluation.class);
    }

    // --- Lot 0 UAT : D-E12.c (EVA-9) ---

    @Test
    void ouvertesParNiveauCompteUneUrgenteOuverteEtInterrogeSeulementOuverteEtEnCours() {
        when(repository.compterParNiveau(any())).thenReturn(java.util.Collections.singletonList(
                new Object[] {"URGENTE", 1L}));

        var r = service.ouvertesParNiveau(null);

        assertThat(nombre(r, "URGENTE")).isEqualTo(1);
        assertThat(nombre(r, "ORANGE")).isZero();
        assertThat(nombre(r, "ROUGE")).isZero();
        assertThat(r.niveaux()).extracting(n -> n.code()).containsExactly("ORANGE", "ROUGE", "URGENTE");
        assertThat(r.total()).isEqualTo(1);
        // une alerte TRAITEE n'est jamais comptée : seuls OUVERTE et EN_COURS sont demandés au dépôt
        verify(repository).compterParNiveau(org.mockito.ArgumentMatchers.argThat(statuts ->
                statuts.size() == 2 && statuts.contains(StatutAlerteEcart.OUVERTE)
                        && statuts.contains(StatutAlerteEcart.EN_COURS)));
    }

    @Test
    void ouvertesParNiveauAvecCampagneUtiliseLaRequeteParCampagne() {
        UUID campagne = UUID.randomUUID();
        when(repository.compterParNiveauEtCampagne(any(), eq(campagne))).thenReturn(List.of(
                new Object[] {"ORANGE", 2L}, new Object[] {"ROUGE", 3L}));

        var r = service.ouvertesParNiveau(campagne);

        assertThat(nombre(r, "ORANGE")).isEqualTo(2);
        assertThat(nombre(r, "ROUGE")).isEqualTo(3);
        assertThat(nombre(r, "URGENTE")).isZero();
        assertThat(r.total()).isEqualTo(5);
        verify(repository, never()).compterParNiveau(any());
    }
}
