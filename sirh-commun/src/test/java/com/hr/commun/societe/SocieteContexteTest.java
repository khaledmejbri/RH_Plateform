package com.hr.commun.societe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocieteContexteTest {

	@AfterEach
	void tearDown() {
		SocieteContexte.effacer();
	}

	@Test
	@DisplayName("courante() lève SocieteAbsenteException si le contexte est vide ou AUCUNE")
	void courante_videOuAucune_leveException() {
		assertThat(SocieteContexte.couranteOuVide()).isEmpty();
		assertThatThrownBy(SocieteContexte::courante)
				.isInstanceOf(SocieteAbsenteException.class);

		SocieteContexte.definir(SocieteContexte.AUCUNE);
		assertThatThrownBy(SocieteContexte::courante)
				.isInstanceOf(SocieteAbsenteException.class);
	}

	@Test
	@DisplayName("executer(UUID, Runnable) et executer(UUID, Supplier) gèrent l'imbrication et restaurent la valeur précédente")
	void executer_imbricationEtRestauration() {
		UUID s1 = UUID.randomUUID();
		UUID s2 = UUID.randomUUID();
		AtomicReference<UUID> observeInterne = new AtomicReference<>();
		AtomicReference<UUID> observeApresInterne = new AtomicReference<>();

		String resultat = SocieteContexte.executer(s1, () -> {
			assertThat(SocieteContexte.courante()).isEqualTo(s1);
			SocieteContexte.executer(s2, () -> observeInterne.set(SocieteContexte.courante()));
			observeApresInterne.set(SocieteContexte.courante());
			return "ok";
		});

		assertThat(resultat).isEqualTo("ok");
		assertThat(observeInterne.get()).isEqualTo(s2);
		assertThat(observeApresInterne.get()).isEqualTo(s1);
		assertThat(SocieteContexte.couranteOuVide()).isEmpty();
	}

	@Test
	@DisplayName("executer restaure la valeur précédente même en cas d'exception")
	void executer_restaureEnCasDException() {
		UUID s1 = UUID.randomUUID();
		UUID s2 = UUID.randomUUID();

		SocieteContexte.definir(s1);
		assertThatThrownBy(() -> SocieteContexte.executer(s2, (Runnable) () -> {
			assertThat(SocieteContexte.courante()).isEqualTo(s2);
			throw new IllegalStateException("boum");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(SocieteContexte.courante()).isEqualTo(s1);
	}
}
