package com.hr.evaluation.config;

import com.hr.evaluation.domain.EvenementAlerteEcart;
import com.hr.evaluation.domain.SensEcart;
import com.hr.evaluation.domain.StatutAlerteEcart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code ddl-auto=update} ne régénère pas les CHECK existants : toute valeur ajoutée plus tard à un enum des alertes
 * d'écart serait refusée. Recrée à chaque démarrage (idempotent) une contrainte
 * {@code <table>_<colonne>_check} depuis chaque enum. NULL reste accepté par SQL. Modèle :
 * {@link CampagneTypeConstraintRunner}.
 * <p>
 * Depuis le lot 3 (ADR § 3), les niveaux d'alerte et les conclusions sont des <b>codes paramétrés</b> : leurs CHECK
 * ({@link #A_SUPPRIMER}) sont supprimés à chaque démarrage au lieu d'être recréés, sinon la création d'un niveau par la
 * RH serait refusée par la base.
 */
@Component
public class AlerteEcartConstraintRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AlerteEcartConstraintRunner.class);

    static final String ALERTE = "evaluation_alerte_ecart";
    static final String LIGNE = "evaluation_alerte_ecart_ligne";
    static final String HISTORIQUE = "evaluation_alerte_ecart_historique";

    /** Une contrainte à aligner : table, colonne, valeurs de l'enum. */
    record Cible(String table, String colonne, Class<? extends Enum<?>> enumeration) {
        String nomContrainte() {
            return table + "_" + colonne + "_check";
        }
    }

    /** Une colonne dont le CHECK d'enum est supprimé (code paramétré). */
    record ASupprimer(String table, String colonne) {
        String nomContrainte() {
            return table + "_" + colonne + "_check";
        }
    }

    /** Codes de comportement : les CHECK d'enum restent recréés. */
    static final List<Cible> CIBLES = List.of(
            new Cible(ALERTE, "statut", StatutAlerteEcart.class),
            new Cible(LIGNE, "sens_detection", SensEcart.class),
            new Cible(LIGNE, "sens_actuel", SensEcart.class),
            new Cible(HISTORIQUE, "type_evenement", EvenementAlerteEcart.class),
            new Cible(HISTORIQUE, "statut_avant", StatutAlerteEcart.class),
            new Cible(HISTORIQUE, "statut_apres", StatutAlerteEcart.class));

    /** Codes de niveau (7 colonnes) et de conclusion (2 colonnes) : valeurs paramétrables, aucun CHECK. */
    static final List<ASupprimer> A_SUPPRIMER = List.of(
            new ASupprimer(ALERTE, "niveau_detection"),
            new ASupprimer(ALERTE, "niveau_courant"),
            new ASupprimer(ALERTE, "niveau_max_notifie"),
            new ASupprimer(ALERTE, "conclusion"),
            new ASupprimer(LIGNE, "niveau_detection"),
            new ASupprimer(LIGNE, "niveau_actuel"),
            new ASupprimer(HISTORIQUE, "niveau_avant"),
            new ASupprimer(HISTORIQUE, "niveau_apres"),
            new ASupprimer(HISTORIQUE, "conclusion"));

    private final JdbcTemplate jdbcTemplate;

    public AlerteEcartConstraintRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (ASupprimer cible : A_SUPPRIMER) {
            try {
                jdbcTemplate.execute("ALTER TABLE " + cible.table() + " DROP CONSTRAINT IF EXISTS " + cible.nomContrainte());
            } catch (Exception e) {
                log.warn("Impossible de supprimer la contrainte {} : {}", cible.nomContrainte(), e.getMessage());
            }
        }
        for (Cible cible : CIBLES) {
            String valeurs = Arrays.stream(cible.enumeration().getEnumConstants())
                    .map(v -> "'" + v.name() + "'")
                    .collect(Collectors.joining(", "));
            try {
                jdbcTemplate.execute("ALTER TABLE " + cible.table() + " DROP CONSTRAINT IF EXISTS " + cible.nomContrainte());
                jdbcTemplate.execute("ALTER TABLE " + cible.table() + " ADD CONSTRAINT " + cible.nomContrainte()
                        + " CHECK (" + cible.colonne() + " IN (" + valeurs + "))");
            } catch (Exception e) {
                log.warn("Impossible d'aligner la contrainte {} : {}", cible.nomContrainte(), e.getMessage());
            }
        }
        log.info("Contraintes CHECK des alertes d'écart : {} alignées sur les enums, {} supprimées (codes paramétrés)",
                CIBLES.size(), A_SUPPRIMER.size());
    }
}
