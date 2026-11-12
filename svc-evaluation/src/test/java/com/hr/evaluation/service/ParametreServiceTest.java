package com.hr.evaluation.service;

import com.hr.evaluation.domain.QuestionType;
import com.hr.evaluation.domain.ValeursInitialesEvaluation;
import com.hr.evaluation.dto.ParametreDto;
import com.hr.evaluation.entity.ParametreValeur;
import com.hr.evaluation.repository.AlerteEcartRepository;
import com.hr.evaluation.repository.EvaluationQuestionRepository;
import com.hr.evaluation.repository.ParametreValeurRepository;
import com.hr.evaluation.service.ParametreJournalService.Auteur;
import com.hr.evaluation.web.EvaluationMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Critères 3C (ADR lot 3 §§ 5 et 6) sur un dépôt en mémoire : seuils des niveaux d'alerte, 4 niveaux actifs au plus, au
 * moins un, suppression si jamais utilisée, valeurs système, types de question, version, journal.
 */
class ParametreServiceTest {

    private static final String NIVEAU = "NIVEAU_ALERTE";
    private static final Auteur AUTEUR = new Auteur(UUID.randomUUID(), "Amel Trabelsi");

    private final List<ParametreValeur> base = new ArrayList<>();
    private final ParametreValeurRepository repository = mock(ParametreValeurRepository.class);
    private final ParametreJournalService journal = mock(ParametreJournalService.class);
    private final ParametresCache cache = mock(ParametresCache.class);
    private final AlerteEcartRepository alertes = mock(AlerteEcartRepository.class);
    private final EvaluationQuestionRepository questions = mock(EvaluationQuestionRepository.class);
    private ParametreService service;

    @BeforeEach
    void setUp() {
        for (var v : ValeursInitialesEvaluation.toutes()) {
            ParametreValeur p = new ParametreValeur();
            p.setReferentiel(v.referentiel());
            p.setCode(v.code());
            p.setNature(v.nature());
            p.setLibelle(v.libelle());
            p.setLibelleNormalise(CodeParametre.normaliser(v.libelle()));
            p.setOrdre(v.ordre());
            p.setSysteme(v.systeme());
            p.setSeuilEcart(v.seuilEcart());
            if (v.utilisee()) {
                p.setPremiereUtilisationLe(Instant.now());
            }
            base.add(p);
        }
        when(repository.findByReferentielOrderByOrdreAscCodeAsc(anyString())).thenAnswer(i -> base.stream()
                .filter(v -> v.getReferentiel().equals(i.getArgument(0)))
                .sorted(Comparator.comparingInt(ParametreValeur::getOrdre).thenComparing(ParametreValeur::getCode))
                .toList());
        when(repository.findForUpdate(anyString(), anyString())).thenAnswer(i -> trouver(i.getArgument(0), i.getArgument(1)));
        when(repository.existsByReferentielAndCode(anyString(), anyString()))
                .thenAnswer(i -> trouver(i.getArgument(0), i.getArgument(1)).isPresent());
        when(repository.existsByReferentielAndLibelleNormalise(anyString(), anyString())).thenAnswer(i -> base.stream()
                .anyMatch(v -> v.getReferentiel().equals(i.getArgument(0))
                        && v.getLibelleNormalise().equals(i.getArgument(1))));
        when(repository.countByReferentielAndActifTrue(anyString())).thenAnswer(i -> base.stream()
                .filter(v -> v.getReferentiel().equals(i.getArgument(0)) && v.isActif()).count());
        when(repository.saveAndFlush(any())).thenAnswer(i -> {
            ParametreValeur v = i.getArgument(0);
            if (!base.contains(v)) {
                base.add(v);
            }
            return v;
        });
        org.mockito.Mockito.doAnswer(i -> base.remove((ParametreValeur) i.getArgument(0))).when(repository).delete(any());
        service = new ParametreService(repository, journal, cache, alertes, questions);
    }

    private Optional<ParametreValeur> trouver(String ref, String code) {
        return base.stream().filter(v -> v.getReferentiel().equals(ref) && v.getCode().equals(code)).findFirst();
    }

    private static ParametreDto.CorpsValeur niveau(String libelle, Integer seuil) {
        return new ParametreDto.CorpsValeur(libelle, null, "BLEU", null, seuil, null);
    }

    private static void erreur(Throwable t, int statut, String code) {
        assertThat(t).isInstanceOfSatisfying(EvaluationMetierException.class, e -> {
            assertThat(e.getHttpStatus()).as(e.getMessage()).isEqualTo(statut);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // ------------------------------------------------------------------------------- niveaux d'alerte

    @Test
    void creerNiveauSeuil1_201_codeGenere_journalEtCacheInvalide() {
        ParametreDto.Valeur v = service.creer(NIVEAU, niveau("Écart léger", 1), AUTEUR);

        assertThat(v.code()).isEqualTo("ECART_LEGER");
        assertThat(v.seuilEcart()).isEqualTo(1);
        assertThat(v.actif()).isTrue();
        assertThat(v.systeme()).isFalse();
        verify(journal).ecrire(org.mockito.ArgumentMatchers.eq(NIVEAU), org.mockito.ArgumentMatchers.eq("ECART_LEGER"),
                any(), any(), any(), org.mockito.ArgumentMatchers.eq(AUTEUR));
        verify(cache).invaliderApresCommit();
    }

    @Test
    void seuilEnDouble_400() {
        assertThatThrownBy(() -> service.creer(NIVEAU, niveau("Nouveau", 3), AUTEUR))
                .satisfies(t -> erreur(t, 400, "SEUIL_EN_DOUBLE"));
    }

    @Test
    void seuil0Ou5_400() {
        assertThatThrownBy(() -> service.creer(NIVEAU, niveau("Zéro", 0), AUTEUR))
                .satisfies(t -> erreur(t, 400, "SEUIL_INVALIDE"));
        assertThatThrownBy(() -> service.creer(NIVEAU, niveau("Cinq", 5), AUTEUR))
                .satisfies(t -> erreur(t, 400, "SEUIL_INVALIDE"));
        assertThatThrownBy(() -> service.creer(NIVEAU, niveau("Sans seuil", null), AUTEUR))
                .satisfies(t -> erreur(t, 400, "SEUIL_INVALIDE"));
    }

    @Test
    void cinquiemeNiveauActif_422() {
        service.creer(NIVEAU, niveau("Écart léger", 1), AUTEUR); // 2, 3, 4 + 1 = 4 actifs
        assertThatThrownBy(() -> service.creer(NIVEAU, niveau("Cinquième", 1), AUTEUR))
                .satisfies(t -> erreur(t, 422, "NIVEAUX_ACTIFS_MAX"));
    }

    @Test
    void dernierNiveauActif_nePeutPasEtreDesactive_422() {
        for (String code : List.of("ORANGE", "ROUGE")) {
            service.desactiver(NIVEAU, code, 0L, AUTEUR);
        }
        assertThatThrownBy(() -> service.desactiver(NIVEAU, "URGENTE", 0L, AUTEUR))
                .satisfies(t -> erreur(t, 422, "NIVEAU_ACTIF_MIN"));
    }

    @Test
    void reactiverUnNiveauDontLeSeuilEstPris_422() {
        service.desactiver(NIVEAU, "ORANGE", 0L, AUTEUR);
        service.creer(NIVEAU, niveau("Remplaçant", 2), AUTEUR);
        assertThatThrownBy(() -> service.reactiver(NIVEAU, "ORANGE", 0L, AUTEUR))
                .satisfies(t -> erreur(t, 422, "SEUIL_EN_DOUBLE"));
    }

    @Test
    void niveauJamaisUtilise_supprime_puisUtiliseRefuse409() {
        ParametreDto.Valeur v = service.creer(NIVEAU, niveau("Écart léger", 1), AUTEUR);
        service.supprimer(NIVEAU, v.code(), v.version(), AUTEUR); // 204
        assertThat(trouver(NIVEAU, v.code())).isEmpty();

        ParametreDto.Valeur w = service.creer(NIVEAU, niveau("Écart léger", 1), AUTEUR);
        trouver(NIVEAU, w.code()).orElseThrow().setPremiereUtilisationLe(Instant.now()); // marque collante
        assertThatThrownBy(() -> service.supprimer(NIVEAU, w.code(), w.version(), AUTEUR))
                .satisfies(t -> {
                    erreur(t, 409, "PARAMETRE_UTILISE");
                    assertThat(((EvaluationMetierException) t).getDonnees()).containsEntry("deja_utilisee", true);
                });
    }

    @Test
    void niveauUtiliseParDesAlertes_409_avecLeNombre() {
        ParametreDto.Valeur v = service.creer(NIVEAU, niveau("Écart léger", 1), AUTEUR);
        when(alertes.compterUtilisationsNiveau(v.code())).thenReturn(3L);
        assertThatThrownBy(() -> service.supprimer(NIVEAU, v.code(), v.version(), AUTEUR))
                .satisfies(t -> {
                    erreur(t, 409, "PARAMETRE_UTILISE");
                    assertThat(((EvaluationMetierException) t).getDonnees()).containsEntry("utilisations", 3L);
                    assertThat(t.getMessage()).contains("3 fois");
                });
    }

    @Test
    void modifierLeSeuilSeulementSiLibre() {
        ParametreDto.Valeur orange = service.valeurs(NIVEAU).stream().filter(x -> x.code().equals("ORANGE")).findFirst().orElseThrow();
        assertThatThrownBy(() -> service.modifier(NIVEAU, "ORANGE",
                new ParametreDto.CorpsValeur("Alerte", null, null, null, 3, orange.version()), AUTEUR))
                .satisfies(t -> erreur(t, 400, "SEUIL_EN_DOUBLE"));
        ParametreDto.Valeur ok = service.modifier(NIVEAU, "ORANGE",
                new ParametreDto.CorpsValeur("Alerte", null, null, null, 1, orange.version()), AUTEUR);
        assertThat(ok.seuilEcart()).isEqualTo(1);
    }

    // ----------------------------------------------------------------------------- règles communes

    @Test
    void conclusionAutre_systeme_409_surDesactivationEtSuppression() {
        assertThatThrownBy(() -> service.desactiver("CONCLUSION_ALERTE", "AUTRE", 0L, AUTEUR))
                .satisfies(t -> erreur(t, 409, "PARAMETRE_SYSTEME"));
        assertThatThrownBy(() -> service.supprimer("CONCLUSION_ALERTE", "AUTRE", 0L, AUTEUR))
                .satisfies(t -> erreur(t, 409, "PARAMETRE_SYSTEME"));
    }

    @Test
    void typeQuestionDate_desactivable_ratingJamais() {
        ParametreDto.Valeur date = service.desactiver("TYPE_QUESTION", "DATE", 0L, AUTEUR);
        assertThat(date.actif()).isFalse();
        assertThatThrownBy(() -> service.desactiver("TYPE_QUESTION", "RATING", 0L, AUTEUR))
                .satisfies(t -> erreur(t, 409, "PARAMETRE_SYSTEME"));
        assertThat(service.reactiver("TYPE_QUESTION", "DATE", 0L, AUTEUR).actif()).isTrue();
    }

    @Test
    void codeDeComportement_creation405_suppression405_desactivation405() {
        assertThatThrownBy(() -> service.creer("STATUT_EVALUATION", niveau("Nouveau statut", null), AUTEUR))
                .satisfies(t -> erreur(t, 405, "PARAMETRE_COMPORTEMENT"));
        assertThatThrownBy(() -> service.supprimer("STATUT_EVALUATION", "VALIDEE", 0L, AUTEUR))
                .satisfies(t -> erreur(t, 405, "PARAMETRE_COMPORTEMENT"));
        assertThatThrownBy(() -> service.desactiver("STATUT_EVALUATION", "VALIDEE", 0L, AUTEUR))
                .satisfies(t -> erreur(t, 405, "PARAMETRE_COMPORTEMENT"));
    }

    @Test
    void renommerUnComportement_habillageSeulement_codeInchange() {
        ParametreDto.Valeur v = service.modifier("ECHELLE_ETOILES", "3",
                new ParametreDto.CorpsValeur("Confirmé", "Aide", "VERT", 3, null, 0L), AUTEUR);
        assertThat(v.code()).isEqualTo("3");
        assertThat(v.libelle()).isEqualTo("Confirmé");
    }

    @Test
    void libelleEnDouble_400_valeursInactivesComprises() {
        service.desactiver(NIVEAU, "ORANGE", 0L, AUTEUR);
        assertThatThrownBy(() -> service.creer(NIVEAU, niveau("  alerte ", 1), AUTEUR))
                .satisfies(t -> erreur(t, 400, "LIBELLE_EN_DOUBLE"));
    }

    @Test
    void libelleEtCouleurInvalides_400() {
        assertThatThrownBy(() -> service.creer(NIVEAU, niveau("x", 1), AUTEUR))
                .satisfies(t -> erreur(t, 400, "LIBELLE_INVALIDE"));
        assertThatThrownBy(() -> service.creer(NIVEAU,
                new ParametreDto.CorpsValeur("Nouveau", null, "FUCHSIA", null, 1, null), AUTEUR))
                .satisfies(t -> erreur(t, 400, "COULEUR_INVALIDE"));
        assertThatThrownBy(() -> service.creer(NIVEAU,
                new ParametreDto.CorpsValeur("Nouveau", "a".repeat(301), null, null, 1, null), AUTEUR))
                .satisfies(t -> erreur(t, 400, "DESCRIPTION_TROP_LONGUE"));
    }

    @Test
    void versionPerimee_409_etVersionAbsente_400() {
        assertThatThrownBy(() -> service.desactiver(NIVEAU, "ORANGE", 5L, AUTEUR))
                .satisfies(t -> erreur(t, 409, "PARAMETRE_MODIFIE_ENTRE_TEMPS"));
        assertThatThrownBy(() -> service.desactiver(NIVEAU, "ORANGE", null, AUTEUR))
                .satisfies(t -> erreur(t, 400, "VERSION_OBLIGATOIRE"));
    }

    @Test
    void referentielOuValeurInconnus_404() {
        assertThatThrownBy(() -> service.valeurs("INCONNU")).satisfies(t -> erreur(t, 404, "REFERENTIEL_INCONNU"));
        assertThatThrownBy(() -> service.desactiver(NIVEAU, "XXX", 0L, AUTEUR))
                .satisfies(t -> erreur(t, 404, "PARAMETRE_INTROUVABLE"));
    }

    @Test
    void ordreIncomplet_400_puisOrdreAppliqueEtJournalise() {
        assertThatThrownBy(() -> service.reordonner(NIVEAU, List.of("ORANGE"), AUTEUR))
                .satisfies(t -> erreur(t, 400, "ORDRE_INCOMPLET"));
        service.reordonner(NIVEAU, List.of("URGENTE", "ROUGE", "ORANGE"), AUTEUR);
        assertThat(trouver(NIVEAU, "URGENTE").orElseThrow().getOrdre()).isEqualTo(1);
        assertThat(trouver(NIVEAU, "ORANGE").orElseThrow().getOrdre()).isEqualTo(3);
        verify(journal).ecrire(org.mockito.ArgumentMatchers.eq(NIVEAU), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(com.hr.evaluation.domain.ActionJournalParametre.ORDRE), any(), any(), any());
    }

    @Test
    void ordreDeLEchelleDesEtoiles_405() {
        assertThatThrownBy(() -> service.reordonner("ECHELLE_ETOILES", List.of("5", "4", "3", "2", "1"), AUTEUR))
                .satisfies(t -> erreur(t, 405, "PARAMETRE_COMPORTEMENT"));
    }

    @Test
    void libelleNul_inchange_videOuEspaces_400() {
        ParametreDto.Valeur v = service.modifier("ECHELLE_ETOILES", "3",
                new ParametreDto.CorpsValeur(null, "Aide", null, null, null, 0L), AUTEUR);
        assertThat(v.libelle()).isEqualTo("Autonome");
        assertThat(v.description()).isEqualTo("Aide");
        long version = v.version();
        assertThatThrownBy(() -> service.modifier("ECHELLE_ETOILES", "3",
                new ParametreDto.CorpsValeur("   ", null, null, null, null, version), AUTEUR))
                .satisfies(t -> erreur(t, 400, "LIBELLE_INVALIDE"));
        assertThatThrownBy(() -> service.modifier("ECHELLE_ETOILES", "3",
                new ParametreDto.CorpsValeur("", null, null, null, null, version), AUTEUR))
                .satisfies(t -> erreur(t, 400, "LIBELLE_INVALIDE"));
    }

    @Test
    void typeQuestionDesactive_ajoutRefuse422_apresInvalidationDuCache() {
        var banc = ParametresTestes.avec(ParametresInstantane.de(ParametresTestes.initiales().stream()
                .map(v -> v.referentiel().equals("TYPE_QUESTION") && v.code().equals("DATE")
                        ? new ParametresInstantane.Valeur(v.referentiel(), v.code(), v.nature(), v.libelle(),
                                v.description(), v.couleur(), v.ordre(), false, v.systeme(), null)
                        : v).toList(), null));

        assertThatThrownBy(() -> banc.parametres().verifierTypeQuestionActif(QuestionType.DATE))
                .satisfies(t -> erreur(t, 422, "TYPE_QUESTION_INACTIF"));
        banc.parametres().verifierTypeQuestionActif(QuestionType.TEXT);
        banc.parametres().verifierTypeQuestionActif(QuestionType.RATING);
    }

    @Test
    void echelleRenommee_renvoyeeTellequelle() {
        var renommee = ParametresTestes.initiales().stream()
                .map(v -> v.referentiel().equals("ECHELLE_ETOILES") && v.code().equals("1")
                        ? new ParametresInstantane.Valeur(v.referentiel(), v.code(), v.nature(), "Novice",
                                v.description(), v.couleur(), v.ordre(), true, v.systeme(), null)
                        : v).toList();
        var parametres = ParametresTestes.avec(ParametresInstantane.de(renommee, null)).parametres();

        assertThat(parametres.libellesEchelle()).containsExactly("Novice", "Supervisé", "Autonome", "Avancé", "Expert");
    }

    @Test
    void echelle_trieeParCode_memeSiOrdreModifie() {
        var inverse = ParametresTestes.initiales().stream()
                .map(v -> v.referentiel().equals("ECHELLE_ETOILES")
                        ? new ParametresInstantane.Valeur(v.referentiel(), v.code(), v.nature(), v.libelle(),
                                v.description(), v.couleur(), 10 - Integer.parseInt(v.code()), true, v.systeme(), null)
                        : v).toList();
        var parametres = ParametresTestes.avec(ParametresInstantane.de(inverse, null)).parametres();

        assertThat(parametres.libellesEchelle()).containsExactly("Débutant", "Supervisé", "Autonome", "Avancé", "Expert");
    }

    @Test
    void libellesNonBackoffice_listeBlancheEtActifsSeulement_sansAide() {
        service.desactiver("TYPE_QUESTION", "DATE", 0L, AUTEUR);
        var instantane = ParametresTestes.avec(ParametresInstantane.de(ParametresTestes.initiales(), null)).parametres().instantane();

        ParametreDto.Libelles mobile = service.libelles(false, false, instantane);
        ParametreDto.Libelles rh = service.libelles(true, false, instantane);

        assertThat(mobile.referentiels()).containsOnlyKeys("STATUT_EVALUATION", "ECHELLE_ETOILES", "APPRECIATION");
        assertThat(mobile.referentiels().get("ECHELLE_ETOILES")).allSatisfy(l -> assertThat(l.description()).isNull());
        assertThat(rh.referentiels()).containsKeys("NIVEAU_ALERTE", "CONCLUSION_ALERTE", "TYPE_QUESTION");
        assertThat(mobile.version()).isNotEqualTo(rh.version());
    }
}
