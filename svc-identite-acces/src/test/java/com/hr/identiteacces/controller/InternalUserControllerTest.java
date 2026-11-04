package com.hr.identiteacces.controller;

import com.hr.identiteacces.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SCRUM-53 : /internal/users/by-role durci : jeton à temps constant, rôle validé (ADR-M07-ALERTES-ECART § 10).
 */
class InternalUserControllerTest {

	private final UserService userService = mock(UserService.class);
	private final InternalUserController controller = new InternalUserController(userService, "secret-partage");

	private static int statut(Throwable t) {
		return ((ResponseStatusException) t).getStatusCode().value();
	}

	@Test
	void sansJeton_403_serviceNonAppele() {
		assertThatThrownBy(() -> controller.getUsersByRole("RH", null))
				.isInstanceOf(ResponseStatusException.class)
				.satisfies(t -> assertThat(statut(t)).isEqualTo(403));
		verifyNoInteractions(userService);
	}

	@Test
	void mauvaisJeton_403() {
		assertThatThrownBy(() -> controller.getUsersByRole("RH", "autre"))
				.satisfies(t -> assertThat(statut(t)).isEqualTo(403));
		verifyNoInteractions(userService);
	}

	@Test
	void jetonServeurNul_toutRefuse() {
		InternalUserController sansJeton = new InternalUserController(userService, null);

		assertThatThrownBy(() -> sansJeton.getUsersByRole("RH", "x"))
				.satisfies(t -> assertThat(statut(t)).isEqualTo(403));
		assertThatThrownBy(() -> sansJeton.getUsersByRole("RH", null))
				.satisfies(t -> assertThat(statut(t)).isEqualTo(403));
		verifyNoInteractions(userService);
	}

	@Test
	void roleInvalide_400_apresLeJeton() {
		for (String role : new String[] {"rh", "R", "RH;DROP", "A".repeat(31), "RH ", "", "R1"}) {
			assertThatThrownBy(() -> controller.getUsersByRole(role, "secret-partage"))
					.satisfies(t -> assertThat(statut(t)).as(role).isEqualTo(400));
		}
		verifyNoInteractions(userService);
	}

	@Test
	void bonJetonEtRoleValide_delegueAuService() {
		when(userService.getUsersByRole("RH")).thenReturn(List.of());

		assertThat(controller.getUsersByRole("RH", "secret-partage")).isEmpty();
		verify(userService).getUsersByRole("RH");
	}
}
