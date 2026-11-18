package com.hr.commun.societe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocieteGardeEcritureTest {

	private final SocieteGardeEcriture garde = new SocieteGardeEcriture();

	@AfterEach
	void tearDown() {
		SocieteContexte.effacer();
	}

	@Test
	@DisplayName("Lève IllegalStateException si la société est vide ou AUCUNE, passe si définie")
	void verifierSociete() {
		assertThatThrownBy(() -> garde.verifierSociete(new Object()))
				.isInstanceOf(IllegalStateException.class);

		SocieteContexte.definir(SocieteContexte.AUCUNE);
		assertThatThrownBy(() -> garde.verifierSociete(new Object()))
				.isInstanceOf(IllegalStateException.class);

		SocieteContexte.definir(UUID.randomUUID());
		assertThatCode(() -> garde.verifierSociete(new Object()))
				.doesNotThrowAnyException();
	}
}
