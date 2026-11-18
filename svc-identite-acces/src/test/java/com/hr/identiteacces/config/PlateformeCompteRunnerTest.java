package com.hr.identiteacces.config;

import com.hr.identiteacces.entity.User;
import com.hr.identiteacces.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlateformeCompteRunnerTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Test
	@DisplayName("D-SOC03.b : crée le compte PLATEFORME initial si les variables sont définies et qu'aucun compte PLATEFORME n'existe")
	void creeComptePlateformeInitialSiVariablesDefinies() {
		when(userRepository.existsByRole("PLATEFORME")).thenReturn(false);
		when(userRepository.existsByEmailIgnoreCase("plat-op@plateforme.local")).thenReturn(false);
		when(passwordEncoder.encode("SecretFixtureTest!")).thenReturn("hash-fixture");

		PlateformeCompteRunner runner = new PlateformeCompteRunner(
				userRepository, passwordEncoder, "plat-op", "SecretFixtureTest!");
		runner.executer();

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(captor.capture());
		User cree = captor.getValue();
		assertThat(cree.getSocieteId()).isNull();
		assertThat(cree.getUsername()).isEqualTo("plat-op");
		assertThat(cree.getRoles()).containsExactly("PLATEFORME");
		assertThat(cree.getPassword()).isEqualTo("hash-fixture");
	}

	@Test
	@DisplayName("D-SOC03.b : ne crée rien si les variables sont vides ou si un compte PLATEFORME existe déjà")
	void neCreeRienSiVariablesVidesOuCompteExistant() {
		PlateformeCompteRunner runnerVide = new PlateformeCompteRunner(
				userRepository, passwordEncoder, "", "");
		runnerVide.executer();
		verify(userRepository, never()).saveAndFlush(any());

		when(userRepository.existsByRole("PLATEFORME")).thenReturn(true);
		PlateformeCompteRunner runnerDejaPresent = new PlateformeCompteRunner(
				userRepository, passwordEncoder, "plat-op", "SecretFixtureTest!");
		runnerDejaPresent.executer();
		verify(userRepository, never()).saveAndFlush(any());
	}
}
