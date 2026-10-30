package com.hr.presence.config;

import com.hr.presence.domain.ActionAuditFacial;
import com.hr.presence.domain.PlateformeMobile;
import com.hr.presence.domain.StatutEnrolement;
import com.hr.presence.domain.StatutReglageFacial;
import com.hr.presence.domain.VerificationFaciale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * {@code ddl-auto=update} ne régénère pas les CHECK existants : une valeur ajoutée plus tard à l'un des enums stockés
 * de la vague faciale violerait l'ancienne contrainte. Recrée chaque contrainte à partir de l'enum à chaque démarrage
 * (idempotent), sur le modèle de {@link PointageStatutConstraintRunner} (qui, lui, suit {@code PointageStatut} et couvre
 * donc les trois nouveaux rejets faciaux).
 */
@Component
public class FacialConstraintRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(FacialConstraintRunner.class);

	private final JdbcTemplate jdbcTemplate;

	public FacialConstraintRunner(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		aligner("presence_enrolement_facial", "statut", StatutEnrolement.values());
		aligner("presence_audit_facial", "action", ActionAuditFacial.values());
		aligner("presence_audit_facial", "statut_avant", StatutEnrolement.values());
		aligner("presence_audit_facial", "statut_apres", StatutEnrolement.values());
		aligner("presence_reglage_facial", "statut", StatutReglageFacial.values());
		aligner("presence_pointage", "verification_faciale", VerificationFaciale.values());
		aligner("presence_pointage", "plateforme", PlateformeMobile.values());
	}

	private void aligner(String table, String colonne, Enum<?>[] valeurs) {
		String contrainte = table + "_" + colonne + "_check";
		String liste = Arrays.stream(valeurs).map(v -> "'" + v.name() + "'").collect(Collectors.joining(", "));
		try {
			jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + contrainte);
			jdbcTemplate.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + contrainte
					+ " CHECK (" + colonne + " IN (" + liste + "))");
			log.info("Contrainte {} alignée sur l'enum ({})", contrainte, liste);
		} catch (Exception e) {
			log.warn("Impossible d'aligner la contrainte {} : {}", contrainte, e.getMessage());
		}
	}
}
