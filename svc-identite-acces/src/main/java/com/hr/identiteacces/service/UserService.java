package com.hr.identiteacces.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.identiteacces.dto.UserResponse;
import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public List<UserResponse> getUsersByRole(String role) {
		Optional<UUID> societeId = SocieteContexte.couranteOuVide()
				.filter(id -> !SocieteContexte.AUCUNE.equals(id));
		List<User> users = societeId.isPresent()
				? userRepository.findBySocieteIdAndRolesContaining(societeId.get(), role)
				: userRepository.findByRolesContaining(role);
		return users.stream()
				.map(this::toUserResponse)
				.collect(Collectors.toList());
	}

	@Transactional(readOnly = true)
	public List<UserResponse> getUsersBySocieteAndRole(UUID societeId, String role) {
		return userRepository.findBySocieteIdAndRolesContaining(societeId, role).stream()
				.map(this::toUserResponse)
				.collect(Collectors.toList());
	}

	/**
	 * Identifiants (minuscules) qui ont déjà un compte, parmi une liste en
	 * minuscules (import des collaborateurs).
	 */
	@Transactional(readOnly = true)
	public List<String> identifiantsExistants(List<String> identifiantsMinuscules) {
		if (identifiantsMinuscules.isEmpty()) {
			return List.of();
		}
		Optional<UUID> societeId = SocieteContexte.couranteOuVide()
				.filter(id -> !SocieteContexte.AUCUNE.equals(id));
		return societeId.isPresent()
				? userRepository.findUsernamesParMinusculesEtSociete(societeId.get(), identifiantsMinuscules)
				: userRepository.findUsernamesParMinuscules(identifiantsMinuscules);
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
