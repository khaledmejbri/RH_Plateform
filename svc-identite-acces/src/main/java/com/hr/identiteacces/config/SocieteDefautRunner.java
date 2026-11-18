package com.hr.identiteacces.config;

import com.hr.identiteacces.domain.StatutSociete;
import com.hr.identiteacces.entity.Societe;
import com.hr.identiteacces.repository.SocieteRepository;
import com.hr.identiteacces.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/**
 * Crée la société par défaut au démarrage si elle est absente et lui rattache les comptes existants sans société
 * (hors comptes {@code PLATEFORME}). Idempotent (§ 9.2.3 étape 1).
 */
@Component
@Order(1)
public class SocieteDefautRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SocieteDefautRunner.class);

	private final SocieteRepository societeRepository;
	private final UserRepository userRepository;
	private final String codeDefaut;
	private final String raisonSocialeDefaut;

	@Autowired
	public SocieteDefautRunner(
			SocieteRepository societeRepository,
			UserRepository userRepository,
			@Value("${app.societe.defaut.code:${SOCIETE_DEFAUT_CODE:PRINCIPALE}}") String codeDefaut,
			@Value("${app.societe.defaut.raison-sociale:${SOCIETE_DEFAUT_RAISON_SOCIALE:Société principale}}") String raisonSocialeDefaut) {
		this.societeRepository = societeRepository;
		this.userRepository = userRepository;
		this.codeDefaut = (codeDefaut == null || codeDefaut.isBlank())
				? "PRINCIPALE"
				: codeDefaut.trim().toUpperCase(Locale.ROOT);
		this.raisonSocialeDefaut = (raisonSocialeDefaut == null || raisonSocialeDefaut.isBlank())
				? "Société principale"
				: raisonSocialeDefaut.trim();
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		executer();
	}

	@Transactional
	public Societe executer() {
		Societe societe = societeRepository.findByCodeIgnoreCase(codeDefaut)
				.orElseGet(() -> {
					Instant now = Instant.now();
					Societe nouvelle = Societe.builder()
							.code(codeDefaut)
							.raisonSociale(raisonSocialeDefaut)
							.statut(StatutSociete.ACTIVE)
							.fuseau("Africa/Tunis")
							.creeLe(now)
							.modifieLe(now)
							.build();
					nouvelle = societeRepository.saveAndFlush(nouvelle);
					log.info("Société par défaut créée : code={} id={}", nouvelle.getCode(), nouvelle.getId());
					return nouvelle;
				});

		int lignesReprises = userRepository.rattacherComptesSansSociete(societe.getId());
		log.info("Reprise société par défaut {} ({}) : {} compte(s) mis à jour",
				societe.getCode(), societe.getId(), lignesReprises);
		return societe;
	}
}
