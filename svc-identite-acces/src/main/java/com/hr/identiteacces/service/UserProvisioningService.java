package com.hr.identiteacces.service;

import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.kafka.CollaborateurCompteCreeEvent;
import com.hr.identiteacces.kafka.CollaborateurCompteDemandeEvent;
import com.hr.identiteacces.kafka.RhKafkaTopics;
import com.hr.identiteacces.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class UserProvisioningService {

	private static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final KafkaTemplate<String, CollaborateurCompteCreeEvent> collaborateurCompteCreeKafkaTemplate;
	private final CollaborateurWelcomeMailService collaborateurWelcomeMailService;

	@Transactional
	public void provisionCollaborateurCompte(CollaborateurCompteDemandeEvent event) {
		String matricule = event.matricule().trim();
		if (matricule.length() > 100) {
			log.error("Matricule trop long pour username : {}", matricule);
			return;
		}

		if (userRepository.existsByUsername(matricule)) {
			UUID userId = userRepository.findByUsername(matricule)
					.map(User::getId)
					.orElseThrow();
			log.info("Compte déjà existant pour matricule {}, liaison collaborateur {}", matricule,
					event.collaborateurIdentifiant());
			publishCree(event.collaborateurIdentifiant(), userId);
			return;
		}

		if (userRepository.existsByEmail(event.courriel().trim())) {
			log.error("Courriel déjà utilisé pour un autre compte : {}", event.courriel());
			return;
		}

		Set<String> roles = resolveRoles(event.profilAcces());
		User user = User.builder()
				.username(matricule)
				.email(event.courriel().trim())
				.password(passwordEncoder.encode(event.motDePasseInitial()))
				.roles(roles)
				.build();
		user = userRepository.save(user);
		log.info("Compte créé pour collaborateur {} utilisateur {}", event.collaborateurIdentifiant(), user.getId());
		publishCree(event.collaborateurIdentifiant(), user.getId());
		collaborateurWelcomeMailService.scheduleWelcomeEmail(
				event.courriel().trim(),
				event.prenom(),
				event.nom(),
				matricule,
				event.motDePasseInitial());
	}

	private void publishCree(UUID collaborateurId, UUID userId) {
		collaborateurCompteCreeKafkaTemplate.send(RhKafkaTopics.COLLABORATEUR_COMPTE_CREE,
				collaborateurId.toString(),
				new CollaborateurCompteCreeEvent(collaborateurId, userId));
	}

	private static Set<String> resolveRoles(String profilAcces) {
		if (profilAcces == null || profilAcces.isBlank()) {
			return Set.of("USER");
		}
		return switch (profilAcces.trim().toUpperCase(Locale.ROOT)) {
			case "RESPONSABLE", "RO" -> new LinkedHashSet<>(Set.of("USER", "RESPONSABLE"));
			case "RH" -> new LinkedHashSet<>(Set.of("USER", "RH"));
			default -> Set.of("USER");
		};
	}
}
