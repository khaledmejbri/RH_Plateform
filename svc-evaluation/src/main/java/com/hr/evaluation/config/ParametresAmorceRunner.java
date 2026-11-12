package com.hr.evaluation.config;

import com.hr.evaluation.domain.ActionJournalParametre;
import com.hr.evaluation.domain.AppreciationEvaluationRh;
import com.hr.evaluation.domain.EvaluationCampaignStatus;
import com.hr.evaluation.domain.NatureParametre;
import com.hr.evaluation.domain.QuestionType;
import com.hr.evaluation.domain.ReferentielEvaluation;
import com.hr.evaluation.domain.StatutAlerteEcart;
import com.hr.evaluation.domain.StatutEvaluationRh;
import com.hr.evaluation.domain.ValeursInitialesEvaluation;
import com.hr.evaluation.domain.ValeursInitialesEvaluation.ValeurInitiale;
import com.hr.evaluation.entity.ParametreJournal;
import com.hr.evaluation.entity.ParametreValeur;
import com.hr.evaluation.repository.ParametreJournalRepository;
import com.hr.evaluation.repository.ParametreValeurRepository;
import com.hr.evaluation.service.CodeParametre;
import com.hr.evaluation.service.ParametresCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Amorçage et migration des paramètres sans Flyway (ADR lot 3 § 8). Rejouable à chaque démarrage, jamais bloquant
 * (une étape en échec est journalisée en ERROR et les suivantes sont tentées). Tourne avant l'ouverture du serveur
 * web ({@link SmartInitializingSingleton}), comme dans le référentiel : aucune requête n'arrive sur un paramétrage vide.
 * <ol>
 *   <li>élargit à 40 caractères les colonnes qui portent un code paramétré ({@code ddl-auto=update} ne le fait pas) ;</li>
 *   <li>crée l'index unique partiel « un seul niveau actif par seuil » (D-R04.a) ;</li>
 *   <li>insère les valeurs initiales manquantes (une ligne déjà présente n'est <b>jamais</b> réécrite) et une ligne
 *       par valeur d'enum de comportement manquante ;</li>
 *   <li>recrée en <b>inactifs</b> les codes stockés sans ligne de paramétrage (orphelins), journalisés ;</li>
 *   <li>remplit {@code version} et {@code seuil_insuffisance} des réglages existants (colonnes nullables).</li>
 * </ol>
 */
@Component
public class ParametresAmorceRunner implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ParametresAmorceRunner.class);

    static final int LARGEUR_CODE = 40;

    /** Colonnes qui portent un code paramétré : table, colonne. */
    static final List<String[]> COLONNES_A_ELARGIR = List.of(
            new String[] {"evaluation_alerte_ecart", "niveau_detection"},
            new String[] {"evaluation_alerte_ecart", "niveau_courant"},
            new String[] {"evaluation_alerte_ecart", "niveau_max_notifie"},
            new String[] {"evaluation_alerte_ecart_ligne", "niveau_detection"},
            new String[] {"evaluation_alerte_ecart_ligne", "niveau_actuel"},
            new String[] {"evaluation_alerte_ecart_historique", "niveau_avant"},
            new String[] {"evaluation_alerte_ecart_historique", "niveau_apres"},
            new String[] {"evaluation", "niveau_seniorite"});

    static final String INDEX_SEUIL_ACTIF = "uk_eval_niveau_alerte_seuil_actif";

    private final JdbcTemplate jdbc;
    private final ParametreValeurRepository valeurs;
    private final ParametreJournalRepository journal;
    private final ParametresCache cache;
    private final int seuilInsuffisanceInitial;

    public ParametresAmorceRunner(JdbcTemplate jdbc, ParametreValeurRepository valeurs,
                                  ParametreJournalRepository journal, ParametresCache cache,
                                  @Value("${evaluation.alert.seuil-insuffisant:3}") int seuilInsuffisanceInitial) {
        this.jdbc = jdbc;
        this.valeurs = valeurs;
        this.journal = journal;
        this.cache = cache;
        this.seuilInsuffisanceInitial = seuilInsuffisanceInitial;
    }

    @Override
    public void afterSingletonsInstantiated() {
        amorcer();
    }

    /** Point d'entrée rejouable (aussi appelé par les tests). */
    public void amorcer() {
        etape("élargissement des colonnes", this::elargirColonnes);
        etape("index unique partiel des seuils", this::creerIndexSeuil);
        etape("valeurs initiales", this::inserer);
        etape("codes de comportement manquants", this::completerComportements);
        etape("rapprochement des orphelins", this::rapprocherOrphelins);
        etape("réglages existants", this::completerReglages);
        cache.invalider();
        log.info("Paramètres d'évaluation amorcés");
    }

    private void etape(String nom, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            // Jamais avalé en silence : l'étape est à reprendre (le démarrage, lui, continue)
            log.error("Amorçage des paramètres, étape « {} » non terminée : {}", nom, e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------------------------------ 1. colonnes

    void elargirColonnes() {
        for (String[] c : COLONNES_A_ELARGIR) {
            List<Integer> largeurs = jdbc.query(
                    "SELECT character_maximum_length FROM information_schema.columns "
                            + "WHERE table_name = ? AND column_name = ? AND character_maximum_length IS NOT NULL",
                    (rs, i) -> rs.getInt(1), c[0], c[1]);
            if (!largeurs.isEmpty() && largeurs.get(0) < LARGEUR_CODE) {
                jdbc.execute("ALTER TABLE " + c[0] + " ALTER COLUMN " + c[1] + " TYPE varchar(" + LARGEUR_CODE + ")");
                log.info("Colonne {}.{} élargie à {}", c[0], c[1], LARGEUR_CODE);
            }
        }
    }

    // --------------------------------------------------------------------------------------------- 2. index

    void creerIndexSeuil() {
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + INDEX_SEUIL_ACTIF
                + " ON evaluation_parametre_valeur (seuil_ecart) WHERE referentiel = '"
                + ReferentielEvaluation.NIVEAU_ALERTE + "' AND actif");
    }

    // ------------------------------------------------------------------------------------ 3. valeurs initiales

    void inserer() {
        Instant maintenant = Instant.now();
        int ajoutees = 0;
        for (ValeurInitiale v : ValeursInitialesEvaluation.toutes()) {
            if (valeurs.existsByReferentielAndCode(v.referentiel(), v.code())) {
                continue;
            }
            ParametreValeur p = nouvelle(v.referentiel(), v.code(), v.nature(), v.libelle(), v.ordre(), v.systeme());
            p.setDescription(v.description());
            p.setCouleur(v.couleur());
            p.setSeuilEcart(v.seuilEcart());
            if (v.utilisee()) {
                p.setPremiereUtilisationLe(maintenant);
            }
            try {
                valeurs.saveAndFlush(p);
                ajoutees++;
            } catch (RuntimeException e) {
                // Course entre deux instances, ou libellé déjà pris par une valeur renommée : on garde l'existant
                log.warn("Valeur initiale {}/{} non insérée : {}", v.referentiel(), v.code(), e.getMessage());
            }
        }
        log.info("{} valeur(s) initiale(s) de paramétrage insérée(s)", ajoutees);
    }

    // ------------------------------------------------------------------------ 4. comportements manquants

    void completerComportements() {
        completer(ReferentielEvaluation.STATUT_EVALUATION, noms(StatutEvaluationRh.values()));
        completer(ReferentielEvaluation.STATUT_CAMPAGNE, noms(EvaluationCampaignStatus.values()));
        completer(ReferentielEvaluation.STATUT_ALERTE, noms(StatutAlerteEcart.values()));
        completer(ReferentielEvaluation.TYPE_QUESTION, noms(QuestionType.values()));
        completer(ReferentielEvaluation.APPRECIATION, noms(AppreciationEvaluationRh.values()));
    }

    private static List<String> noms(Enum<?>[] constantes) {
        List<String> noms = new ArrayList<>();
        for (Enum<?> c : constantes) {
            noms.add(c.name());
        }
        return noms;
    }

    private void completer(String referentiel, List<String> codesEnum) {
        List<ParametreValeur> existantes = valeurs.findByReferentielOrderByOrdreAscCodeAsc(referentiel);
        Set<String> presents = new HashSet<>();
        int ordreMax = 0;
        for (ParametreValeur v : existantes) {
            presents.add(v.getCode());
            ordreMax = Math.max(ordreMax, v.getOrdre());
            if (!codesEnum.contains(v.getCode()) && v.getNature() == NatureParametre.COMPORTEMENT) {
                log.warn("Paramètre {}/{} : le code n'existe plus dans l'enum, ligne laissée", referentiel, v.getCode());
            }
        }
        for (String code : codesEnum) {
            if (presents.contains(code)) {
                continue;
            }
            ParametreValeur p = nouvelle(referentiel, code, NatureParametre.COMPORTEMENT,
                    CodeParametre.libelleDepuisCode(code), ++ordreMax, true);
            p.setCouleur("NEUTRE");
            try {
                valeurs.saveAndFlush(p);
                log.info("Nouveau code de comportement {}/{} ajouté au paramétrage", referentiel, code);
            } catch (RuntimeException e) {
                log.warn("Code de comportement {}/{} non ajouté : {}", referentiel, code, e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------------------------- 5. orphelins

    void rapprocherOrphelins() {
        List<String> niveaux = jdbc.queryForList(
                "SELECT niveau_detection FROM evaluation_alerte_ecart UNION SELECT niveau_courant FROM evaluation_alerte_ecart "
                        + "UNION SELECT niveau_max_notifie FROM evaluation_alerte_ecart "
                        + "UNION SELECT niveau_detection FROM evaluation_alerte_ecart_ligne "
                        + "UNION SELECT niveau_actuel FROM evaluation_alerte_ecart_ligne "
                        + "UNION SELECT niveau_avant FROM evaluation_alerte_ecart_historique "
                        + "UNION SELECT niveau_apres FROM evaluation_alerte_ecart_historique", String.class);
        for (String code : niveaux) {
            recreerOrphelin(ReferentielEvaluation.NIVEAU_ALERTE, code, true);
        }
        List<String> conclusions = jdbc.queryForList(
                "SELECT conclusion FROM evaluation_alerte_ecart UNION SELECT conclusion FROM evaluation_alerte_ecart_historique",
                String.class);
        for (String code : conclusions) {
            recreerOrphelin(ReferentielEvaluation.CONCLUSION_ALERTE, code, false);
        }
    }

    private void recreerOrphelin(String referentiel, String code, boolean avecSeuil) {
        if (code == null || code.isBlank() || "ALIGNE".equals(code)
                || valeurs.existsByReferentielAndCode(referentiel, code)) {
            return;
        }
        ParametreValeur p = nouvelle(referentiel, code, NatureParametre.VALEUR, CodeParametre.libelleDepuisCode(code),
                999, false);
        p.setActif(false);
        p.setPremiereUtilisationLe(Instant.now());
        if (avecSeuil) {
            p.setSeuilEcart(ReferentielEvaluation.SEUIL_ECART_MIN);
        }
        try {
            valeurs.saveAndFlush(p);
            journal.save(new ParametreJournal(referentiel, code, ActionJournalParametre.CREATION, null,
                    "{\"code\":\"" + code + "\",\"actif\":false,\"origine\":\"orphelin\"}", null,
                    ParametreJournal.AUTEUR_SYSTEME));
            log.warn("Code {}/{} stocké sans paramétrage : recréé inactif", referentiel, code);
        } catch (RuntimeException e) {
            log.warn("Orphelin {}/{} non recréé : {}", referentiel, code, e.getMessage());
        }
    }

    // ---------------------------------------------------------------------------------------- 6. réglages

    void completerReglages() {
        jdbc.update("UPDATE evaluation_parametres SET version = 0 WHERE version IS NULL");
        jdbc.update("UPDATE evaluation_parametres SET seuil_insuffisance = ? WHERE seuil_insuffisance IS NULL",
                seuilInsuffisanceInitial);
    }

    // ------------------------------------------------------------------------------------------- outils

    static ParametreValeur nouvelle(String referentiel, String code, NatureParametre nature, String libelle,
                                    int ordre, boolean systeme) {
        ParametreValeur p = new ParametreValeur();
        p.setReferentiel(referentiel);
        p.setCode(code);
        p.setNature(nature);
        p.setLibelle(libelle);
        p.setLibelleNormalise(CodeParametre.normaliser(libelle));
        p.setOrdre(ordre);
        p.setSysteme(systeme);
        p.setActif(true);
        return p;
    }
}
