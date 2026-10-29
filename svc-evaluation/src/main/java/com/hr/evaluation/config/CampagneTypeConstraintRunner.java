package com.hr.evaluation.config;

import com.hr.evaluation.domain.EvaluationCampaignType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * {@code ddl-auto=update} ne régénère pas les CHECK existants : TRIMESTRIELLE (SCRUM-41) violait l'ancienne
 * contrainte sur les bases créées avant. Recrée la contrainte depuis l'enum à chaque démarrage (idempotent).
 */
@Component
public class CampagneTypeConstraintRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CampagneTypeConstraintRunner.class);

    static final String TABLE = "evaluation_campaign";
    static final String CONSTRAINT = "evaluation_campaign_type_check";

    private final JdbcTemplate jdbcTemplate;

    public CampagneTypeConstraintRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        String valeurs = Arrays.stream(EvaluationCampaignType.values())
                .map(t -> "'" + t.name() + "'")
                .collect(Collectors.joining(", "));
        try {
            jdbcTemplate.execute("ALTER TABLE " + TABLE + " DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
            jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD CONSTRAINT " + CONSTRAINT
                    + " CHECK (type IN (" + valeurs + "))");
            log.info("Contrainte {} alignée sur l'enum ({})", CONSTRAINT, valeurs);
        } catch (Exception e) {
            log.warn("Impossible d'aligner la contrainte {} : {}", CONSTRAINT, e.getMessage());
        }
    }
}
