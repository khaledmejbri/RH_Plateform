package com.hr.evaluation.config;

import com.hr.evaluation.domain.ConclusionAlerteEcart;
import com.hr.evaluation.domain.EvenementAlerteEcart;
import com.hr.evaluation.domain.NiveauEcart;
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

    static final List<Cible> CIBLES = List.of(
            new Cible(ALERTE, "niveau_detection", NiveauEcart.class),
            new Cible(ALERTE, "niveau_courant", NiveauEcart.class),
            new Cible(ALERTE, "niveau_max_notifie", NiveauEcart.class),
            new Cible(ALERTE, "statut", StatutAlerteEcart.class),
            new Cible(ALERTE, "conclusion", ConclusionAlerteEcart.class),
            new Cible(LIGNE, "niveau_detection", NiveauEcart.class),
            new Cible(LIGNE, "niveau_actuel", NiveauEcart.class),
            new Cible(LIGNE, "sens_detection", SensEcart.class),
            new Cible(LIGNE, "sens_actuel", SensEcart.class),
            new Cible(HISTORIQUE, "type_evenement", EvenementAlerteEcart.class),
            new Cible(HISTORIQUE, "statut_avant", StatutAlerteEcart.class),
            new Cible(HISTORIQUE, "statut_apres", StatutAlerteEcart.class),
            new Cible(HISTORIQUE, "niveau_avant", NiveauEcart.class),
            new Cible(HISTORIQUE, "niveau_apres", NiveauEcart.class),
            new Cible(HISTORIQUE, "conclusion", ConclusionAlerteEcart.class));

    private final JdbcTemplate jdbcTemplate;

    public AlerteEcartConstraintRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
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
        log.info("Contraintes CHECK des alertes d'écart alignées sur les enums ({} colonnes)", CIBLES.size());
    }
}
