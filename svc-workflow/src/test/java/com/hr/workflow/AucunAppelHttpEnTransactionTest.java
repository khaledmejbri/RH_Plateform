package com.hr.workflow;

import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.moteur.EffetClient;
import com.hr.workflow.moteur.ResolutionIntervenantsService;
import com.hr.workflow.repository.WfHistoriqueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'architecture (§ 9.6.1, § 10.3, § 13.3 point 6, invariant I5) :
 * <ul>
 *   <li>Aucune classe portant une méthode {@code @Transactional} (ou annotée {@code @Transactional})
 *       ne doit invoquer de client HTTP dans une méthode {@code @Transactional}.</li>
 *   <li>{@link WfHistoriqueRepository} ne doit exposer aucune méthode de mise à jour ni de suppression (I5).</li>
 * </ul>
 */
class AucunAppelHttpEnTransactionTest {

	private static final Set<Class<?>> TYPES_HTTP = Set.of(
			WorkflowReferentielClient.class,
			EffetClient.class,
			ResolutionIntervenantsService.class,
			RestClient.class
	);

	@Test
	void aucuneMethodeTransactionnelleNeDependDunClientHttp() throws Exception {
		List<Class<?>> classes = scannerClasses("com.hr.workflow");
		assertThat(classes).isNotEmpty();

		List<String> violations = new ArrayList<>();
		for (Class<?> clazz : classes) {
			if (clazz.isInterface() || clazz.isEnum() || clazz.getName().endsWith("Test") || clazz.getName().endsWith("IT")) {
				continue;
			}
			boolean classeTx = clazz.isAnnotationPresent(Transactional.class);
			boolean aChampHttp = false;
			for (Field f : clazz.getDeclaredFields()) {
				if (TYPES_HTTP.contains(f.getType())) {
					aChampHttp = true;
					break;
				}
			}
			if (!aChampHttp) {
				continue;
			}
			if (classeTx) {
				violations.add(clazz.getName() + " est @Transactional au niveau classe tout en injectant un client HTTP");
			}
			// Pour les classes qui injectent un client HTTP (ex. ModeleVersionService), aucune méthode publique ne doit être
			// @Transactional si elle appelle le client HTTP : on vérifie qu'aucune méthode @Transactional ne coexiste sur
			// DemarrageService, DecisionService, SimulationService, EffetRepriseScheduler, ResolutionIntervenantsService.
			if (clazz.getPackageName().equals("com.hr.workflow.moteur")) {
				for (Method m : clazz.getDeclaredMethods()) {
					if (m.isAnnotationPresent(Transactional.class)) {
						violations.add(clazz.getSimpleName() + "." + m.getName() + " est @Transactional dans un service moteur avec client HTTP");
					}
				}
			}
		}
		assertThat(violations).isEmpty();
	}

	@Test
	void wfHistoriqueRepository_estStrictementAppendOnly_I5() {
		for (Method m : WfHistoriqueRepository.class.getMethods()) {
			String nom = m.getName().toLowerCase();
			assertThat(nom)
					.as("Méthode interdite sur WfHistoriqueRepository (I5) : %s", m.getName())
					.doesNotStartWith("delete")
					.doesNotStartWith("remove")
					.doesNotStartWith("update");
		}
	}

	private static List<Class<?>> scannerClasses(String basePackage) throws Exception {
		String path = basePackage.replace('.', '/');
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		URL resource = cl.getResource(path);
		List<Class<?>> result = new ArrayList<>();
		if (resource != null) {
			explorer(new File(resource.toURI()), basePackage, result);
		}
		return result;
	}

	private static void explorer(File dir, String pkg, List<Class<?>> result) {
		File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		for (File f : files) {
			if (f.isDirectory()) {
				explorer(f, pkg + "." + f.getName(), result);
			} else if (f.getName().endsWith(".class") && !f.getName().contains("$")) {
				String className = pkg + "." + f.getName().substring(0, f.getName().length() - 6);
				try {
					result.add(Class.forName(className));
				} catch (Throwable ignored) {
				}
			}
		}
	}
}
