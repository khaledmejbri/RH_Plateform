package com.hr.identiteacces.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.identiteacces.dto.UserResponse;
import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.repository.UserRepository;
import com.hr.identiteacces.web.IdentiteMetierException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final SocieteDefautResolver societeDefautResolver;

	@Autowired
	public UserService(UserRepository userRepository, SocieteDefautResolver societeDefautResolver) {
		this.userRepository = userRepository;
		this.societeDefautResolver = societeDefautResolver;
	}

	@Transactional(readOnly = true)
	public List<UserResponse> getUsersByRole(String role) {
		UUID societeId = resoudreSocieteId("GET /internal/users/by-role");
		return userRepository.findBySocieteIdAndRolesContaining(societeId, role).stream()
				.map(this::toUserResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<UserResponse> getUsersBySocieteAndRole(UUID societeId, String role) {
		return userRepository.findBySocieteIdAndRolesContaining(societeId, role).stream()
				.map(this::toUserResponse)
				.collect(Collectors.toList());
	}

	/** Identifiants (minuscules) qui ont déjà un compte dans la société, parmi une liste en minuscules (import des collaborateurs). */
	@Transactional(readOnly = true)
	public List<String> identifiantsExistants(List<String> identifiantsMinuscules) {
		if (identifiantsMinuscules.isEmpty()) {
			return List.of();
		}
		UUID societeId = resoudreSocieteId("POST /internal/users/identifiants-existants");
		return userRepository.findUsernamesParMinusculesEtSociete(societeId, identifiantsMinuscules);
	}

	private UUID resoudreSocieteId(String origine) {
		Optional<UUID> courante = SocieteContexte.couranteOuVide()
				.filter(id -> !SocieteContexte.AUCUNE.equals(id));
		if (courante.isPresent()) {
			return courante.get();
		}
		Optional<UUID> repli = societeDefautResolver.resoudrePourTransitionAvecWarn(origine);
		if (repli.isPresent()) {
			return repli.get();
		}
		throw IdentiteMetierException.badRequest("SOCIETE_ABSENTE",
				"L'en-tête X-Societe-Id est obligatoire.");
	}

	private UserResponse toUserResponse(User user) {
		return UserResponse.builder()
				.id(user.getId())
				.username(user.getUsername())
				.email(user.getEmail())
				.roles(user.getRoles())
				.createdAt(user.getCreatedAt())
				.build();
	}
}
