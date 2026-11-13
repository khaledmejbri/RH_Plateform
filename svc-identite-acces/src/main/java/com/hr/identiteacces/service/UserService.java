package com.hr.identiteacces.service;

import com.hr.identiteacces.dto.UserResponse;
import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByRole(String role) {
        return userRepository.findByRolesContaining(role).stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    /** Identifiants (minuscules) qui ont déjà un compte, parmi une liste en minuscules (import des collaborateurs). */
    @Transactional(readOnly = true)
    public List<String> identifiantsExistants(List<String> identifiantsMinuscules) {
        return identifiantsMinuscules.isEmpty() ? List.of() : userRepository.findUsernamesParMinuscules(identifiantsMinuscules);
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
