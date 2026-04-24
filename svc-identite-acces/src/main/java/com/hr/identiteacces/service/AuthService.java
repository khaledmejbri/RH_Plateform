package com.hr.identiteacces.service;

import com.hr.identiteacces.dto.AuthResponse;
import com.hr.identiteacces.dto.SigninRequest;
import com.hr.identiteacces.dto.UserResponse;
import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import java.time.Instant;
import com.hr.identiteacces.security.ApplicationRoleMatrix;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;

    @Value("${app.jwt.access-ttl-seconds:3600}")
    private long accessTtlSeconds;

    @Value("${spring.security.oauth2.authorizationserver.issuer:http://localhost:8080}")
    private String issuer;

    public AuthResponse signin(SigninRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        String principalName = authentication.getName();
        User user = userRepository.findByUsername(principalName)
                .or(() -> userRepository.findByEmail(principalName))
                .orElseThrow(() -> new IllegalStateException("Utilisateur introuvable après authentification : " + principalName));

        String scope = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.joining(" "));

        Set<String> roleSet = ApplicationRoleMatrix.expandWithImplicitUser(
                user.getRoles() != null && !user.getRoles().isEmpty()
                        ? user.getRoles()
                        : Set.of("USER"));

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getUsername())
                .claim("scope", scope)
                .claim("roles", new ArrayList<>(roleSet))
                .claim("email", user.getEmail())
                .claim("identifiant_utilisateur", user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTtlSeconds))
                .build();

        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(null)
                .tokenType("Bearer")
                .expiresIn(accessTtlSeconds)
                .user(toUserResponse(user))
                .build();
    }

    private static UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
