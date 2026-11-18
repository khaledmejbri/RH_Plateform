package com.hr.identiteacces.config;

import com.hr.identiteacces.domain.StatutSociete;
import com.hr.identiteacces.entity.Societe;
import com.hr.identiteacces.repository.SocieteRepository;
import com.hr.identiteacces.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocieteDefautRunnerTest {

	@Mock
	private SocieteRepository societeRepository;

	@Mock
	private UserRepository userRepository;

	@Test
	@DisplayName("Premier démarrage : crée la société par défaut PRINCIPALE et rattache les comptes sans société ; second démarrage idempotent")
	void idempotenceCreationEtReprise() {
		UUID idDefaut = UUID.randomUUID();
		Societe creee = Societe.builder()
				.id(idDefaut)
				.code("PRINCIPALE")
				.raisonSociale("Société principale")
				.statut(StatutSociete.ACTIVE)
				.fuseau("Africa/Tunis")
				.build();

		when(societeRepository.findByCodeIgnoreCase("PRINCIPALE"))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(creee));
		when(societeRepository.saveAndFlush(any(Societe.class))).thenReturn(creee);
		when(userRepository.rattacherComptesSansSociete(idDefaut))
				.thenReturn(12)
				.thenReturn(0);

		SocieteDefautRunner runner = new SocieteDefautRunner(
				societeRepository, userRepository, "PRINCIPALE", "Société principale");

		// 1er démarrage
		Societe s1 = runner.executer();
		assertThat(s1.getId()).isEqualTo(idDefaut);
		ArgumentCaptor<Societe> captor = ArgumentCaptor.forClass(Societe.class);
		verify(societeRepository, times(1)).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getCode()).isEqualTo("PRINCIPALE");
		assertThat(captor.getValue().getRaisonSociale()).isEqualTo("Société principale");

		// 2e démarrage (idempotent : pas de seconde insertion)
		Societe s2 = runner.executer();
		assertThat(s2.getId()).isEqualTo(idDefaut);
		verify(societeRepository, times(1)).saveAndFlush(any(Societe.class));
		verify(userRepository, times(2)).rattacherComptesSansSociete(idDefaut);
	}
}
