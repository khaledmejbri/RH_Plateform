package com.hr.referentiel.test;

import com.hr.commun.societe.SocieteContexte;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Extension JUnit 5 locale à {@code svc-referentiel-rh} qui exécute les callbacks et méthodes de test
 * dans un {@link SocieteContexte} fixe (sans modifier les assertions des tests existants).
 */
public class AvecSocieteContexteExtension implements InvocationInterceptor {

	public static final UUID SOCIETE_TEST = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Override
	public void interceptBeforeEachMethod(
			Invocation<Void> invocation,
			ReflectiveInvocationContext<Method> invocationContext,
			ExtensionContext extensionContext) throws Throwable {
		executer(invocation);
	}

	@Override
	public void interceptTestMethod(
			Invocation<Void> invocation,
			ReflectiveInvocationContext<Method> invocationContext,
			ExtensionContext extensionContext) throws Throwable {
		executer(invocation);
	}

	@Override
	public void interceptTestTemplateMethod(
			Invocation<Void> invocation,
			ReflectiveInvocationContext<Method> invocationContext,
			ExtensionContext extensionContext) throws Throwable {
		executer(invocation);
	}

	@Override
	public void interceptAfterEachMethod(
			Invocation<Void> invocation,
			ReflectiveInvocationContext<Method> invocationContext,
			ExtensionContext extensionContext) throws Throwable {
		executer(invocation);
	}

	private static void executer(Invocation<Void> invocation) throws Throwable {
		AtomicReference<Throwable> erreur = new AtomicReference<>();
		SocieteContexte.executer(SOCIETE_TEST, () -> {
			try {
				invocation.proceed();
			} catch (Throwable t) {
				erreur.set(t);
			}
		});
		if (erreur.get() != null) {
			throw erreur.get();
		}
	}
}
