package com.hr.identiteacces.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-SOC03.c : vérifie que toute requête native des dépôts de {@code svc-identite-acces}
 * mentionne {@code societe_id}, hors liste blanche des requêtes transverses assumées.
 */
class RequetesNativesSocieteTest {

	private static final Set<String> METHODES_TRANSVERSES_AUTORISEES = Set.of(
			"SocieteRepository.findByCodeIgnoreCase",
			"SocieteRepository.existsByCodeIgnoreCase",
			"UserRepository.existsByEmailIgnoreCase",
			"UserRepository.findPlateformeByIdentifiantIgnoreCase",
			"UserRepository.existsByRole"
	);

	@Test
	@DisplayName("Aucune requête native sans societe_id dans les dépôts de svc-identite-acces")
	void aucuneRequeteNativeSansSocieteId() {
		List<Class<?>> repos = List.of(UserRepository.class, SocieteRepository.class);
		for (Class<?> repo : repos) {
			for (Method m : repo.getDeclaredMethods()) {
				Query q = m.getAnnotation(Query.class);
				if (q != null && q.nativeQuery()) {
					String cle = repo.getSimpleName() + "." + m.getName();
					if (!METHODES_TRANSVERSES_AUTORISEES.contains(cle)) {
						assertThat(q.value().toLowerCase(Locale.ROOT))
								.as("Requête native de %s doit mentionner societe_id", cle)
								.contains("societe_id");
					}
				}
			}
		}
	}
}
