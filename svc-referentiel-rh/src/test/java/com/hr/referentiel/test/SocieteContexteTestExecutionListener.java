package com.hr.referentiel.test;

import com.hr.commun.societe.SocieteContexte;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Définit {@link SocieteContexte} avant l'ouverture de la transaction Spring Test
 * ({@code TransactionalTestExecutionListener}) afin que Hibernate {@code @TenantId}
 * capture l'identifiant de société de test dès l'ouverture de la session.
 */
public class SocieteContexteTestExecutionListener implements TestExecutionListener, Ordered {

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	@Override
	public void beforeTestMethod(TestContext testContext) {
		SocieteContexte.definir(AvecSocieteContexteExtension.SOCIETE_TEST);
	}

	@Override
	public void afterTestMethod(TestContext testContext) {
		SocieteContexte.effacer();
	}
}
