package com.hr.identiteacces.service;

import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.kafka.CollaborateurCompteCreeEvent;
import com.hr.identiteacces.kafka.CollaborateurCompteDemandeEvent;
import com.hr.identiteacces.kafka.RhKafkaTopics;
import com.hr.identiteacces.repository.UserRepository;
import com.hr.identiteacces.security.ApplicationRoleMatrix;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Optional;
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
	private final GenerateurMotDePasse generateurMotDePasse;

	@Transactional
	public void provisionCollaborateurCompte(CollaborateurCompteDemandeEvent event) {
		Optional<User> existing = trouverCompteExistant(event);
		if (existing.isPresent()) {
			synchroniserRoles(existing.get(), event);
			if (!estMajRoles(event)) {
				publishCree(event.collaborateurIdentifiant(), existing.get().getId());
			}
			return;
		}

		if (estMajRoles(event) || (motDePasseInitialAbsent(event) && !event.genereLeMotDePasse())) {
			if (event.compteUtilisateurId() != null) {
				log.warn(
						"Compte identité introuvable id={} matricule={} collaborateur={} : MAJ rôles ignorée",
						event.compteUtilisateurId(), event.matricule(), event.collaborateurIdentifiant());
			} else if (estMajRoles(event)) {
				log.warn(
						"Compte identité introuvable matricule={} collaborateur={} : MAJ rôles ignorée",
						event.matricule(), event.collaborateurIdentifiant());
			} else {
				log.error("mot_de_passe_initial manquant, création de compte ignorée pour collaborateur {}",
						event.collaborateurIdentifiant());
			}
			return;
		}

		String matricule = event.matricule().trim();
		if (matricule.length() > 100) {
			log.error("Matricule trop long pour username : {}", matricule);
			return;
		}

		if (event.courriel() == null || event.courriel().isBlank()) {
			// import : le mot de passe généré n'a nulle part où aller, la fiche reste sans compte (D-I13.g)
			log.error("Courriel manquant, création de compte ignorée pour collaborateur {}", event.collaborateurIdentifiant());
			return;
		}
		if (userRepository.existsByEmail(event.courriel().trim())) {
			log.error("Courriel déjà utilisé pour un autre compte : {}", event.courriel());
			return;
		}

		Set<String> roles = resolveRoles(event.profilAcces());
		// D-I09 : mot de passe généré ici pour un import, transmis au seul courriel de bienvenue (jamais journalisé)
		String motDePasse = event.genereLeMotDePasse() ? generateurMotDePasse.generer() : event.motDePasseInitial();
		User user = User.builder()
				.username(matricule)
				.email(event.courriel().trim())
				.password(passwordEncoder.encode(motDePasse))
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
				motDePasse);
	}

	private Optional<User> trouverCompteExistant(CollaborateurCompteDemandeEvent event) {
		if (event.compteUtilisateurId() != null) {
			Optional<User> byId = userRepository.findById(event.compteUtilisateurId());
			if (byId.isPresent()) {
				return byId;
			}
		}
		if (event.matricule() == null || event.matricule().isBlank()) {
			return Optional.empty();
		}
		String matricule = event.matricule().trim();
		if (matricule.length() > 100) {
			return Optional.empty();
		}
		return userRepository.findByUsername(matricule);
	}

	private void synchroniserRoles(User user, CollaborateurCompteDemandeEvent event) {
		Set<String> cibles = new LinkedHashSet<>(resolveRoles(event.profilAcces()));
		if (user.getRoles() == null) {
			user.setRoles(cibles);
		} else {
			user.getRoles().clear();
			user.getRoles().addAll(cibles);
		}
		userRepository.save(user);
		log.info("Rôles remplacés pour utilisateur {} selon profil {}", user.getId(), event.profilAcces());
	}

	private static boolean estMajRoles(CollaborateurCompteDemandeEvent event) {
		return CollaborateurCompteDemandeEvent.OPERATION_MAJ_ROLES.equalsIgnoreCase(event.operation());
	}

	private static boolean motDePasseInitialAbsent(CollaborateurCompteDemandeEvent event) {
		return event.motDePasseInitial() == null || event.motDePasseInitial().isBlank();
	}

	private void publishCree(UUID collaborateurId, UUID userId) {
		collaborateurCompteCreeKafkaTemplate.send(RhKafkaTopics.COLLABORATEUR_COMPTE_CREE,
				collaborateurId.toString(),
				new CollaborateurCompteCreeEvent(collaborateurId, userId));
	}

	private static Set<String> resolveRoles(String profilAcces) {
		return ApplicationRoleMatrix.rolesPourProfil(profilAcces);
	}
}
