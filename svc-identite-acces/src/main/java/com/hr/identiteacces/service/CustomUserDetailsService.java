package com.hr.identiteacces.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		UUID societeId = SocieteContexte.couranteOuVide()
				.filter(id -> !SocieteContexte.AUCUNE.equals(id))
				.orElseThrow(() -> new UsernameNotFoundException("Société absente du contexte pour : " + username));

		User user = userRepository.findBySocieteIdAndIdentifiantIgnoreCase(societeId, username)
				.orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + username));

		var authorities = user.getAuthoritiesAsStrings().stream()
				.map(SimpleGrantedAuthority::new)
				.collect(Collectors.toList());

		return org.springframework.security.core.userdetails.User.builder()
				.username(user.getUsername())
				.password(user.getPassword())
				.authorities(authorities)
				.accountExpired(false)
				.accountLocked(false)
				.credentialsExpired(false)
				.disabled(false)
				.build();
	}
}
