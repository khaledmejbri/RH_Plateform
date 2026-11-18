package com.hr.commun.societe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SocieteTenantResolverTest {

	private final SocieteTenantResolver resolver = new SocieteTenantResolver();

	@AfterEach
	void tearDown() {
		SocieteContexte.effacer();
	}

	@Test
	@DisplayName("Renvoie SocieteContexte.AUCUNE hors contexte et la société courante en contexte")
	void resolveCurrentTenantIdentifier() {
		assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(SocieteContexte.AUCUNE);
		assertThat(resolver.validateExistingCurrentSessions()).isTrue();

		UUID societeId = UUID.randomUUID();
		SocieteContexte.definir(societeId);
		assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(societeId);
	}
}
