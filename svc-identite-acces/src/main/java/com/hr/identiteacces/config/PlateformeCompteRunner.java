package com.hr.identiteacces.config;

import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Crée le compte initial de rôle {@code PLATEFORME} (sans société) si {@code PLATEFORME_ADMIN_IDENTIFIANT}
 * et {@code PLATEFORME_ADMIN_MOT_DE_PASSE} sont définis et qu'aucun compte {@code PLATEFORME} n'existe (D-SOC03.b).
 */
@Component
@Order(3)
public class PlateformeCompteRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(PlateformeCompteRunner.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final String identifiant;
	private final String motDePasse;

	@Autowired
	public PlateformeCompteRunner(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			@Value("${app.plateforme.admin.identifiant:${PLATEFORME_ADMIN_IDENTIFIANT:}}") String identifiant,
			@Value("${app.plateforme.admin.mot-de-passe:${PLATEFORME_ADMIN_MOT_DE_PASSE:}}") String motDePasse) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.identifiant = identifiant != null ? identifiant.trim() : "";
		this.motDePasse = motDePasse != null ? motDePasse : "";
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		executer();
	}

	@Transactional
	public void executer() {
		if (identifiant.isEmpty() || motDePasse.isBlank()) {
			return;
		}
		if (userRepository.existsByRole("PLATEFORME")) {
			return;
		}
		String courriel = identifiant.contains("@")
				? identifiant
				: identifiant.toLowerCase(Locale.ROOT) + "@plateforme.local";
		if (userRepository.existsByEmailIgnoreCase(courriel)) {
			log.warn("Courriel {} déjà utilisé : création du compte PLATEFORME initial ignorée", courriel);
			return;
		}
		User user = User.builder()
				.societeId(null)
				.username(identifiant)
				.email(courriel)
				.password(passwordEncoder.encode(motDePasse))
				.roles(new HashSet<>(Set.of("PLATEFORME")))
				.build();
		userRepository.saveAndFlush(user);
		log.info("Compte opérateur PLATEFORME initial créé : username={}", identifiant);
	}
}
