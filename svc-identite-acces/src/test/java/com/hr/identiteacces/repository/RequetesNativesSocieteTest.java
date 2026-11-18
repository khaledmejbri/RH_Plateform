package com.hr.identiteacces.repository;

import com.hr.identiteacces.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-SOC03.c (amélioration 3) : vérifie que chaque méthode déclarée de {@link UserRepository}
 * (dérivée, JPQL ou native) filtre par {@code societe_id} / {@code societeId}, sauf les seules requêtes
 * transverses explicitement autorisées (unicité globale du courriel, comptes opérateur {@code PLATEFORME}
 * sans société et requête de rattachement de reprise).
 */
class RequetesNativesSocieteTest {

	private static final Set<String> METHODES_TRANSVERSES_AUTORISEES_USER = Set.of(
			"existsByEmailIgnoreCase",
			"findPlateformeByIdentifiantIgnoreCase",
			"existsByRole",
			"rattacherComptesSansSociete"
	);

	interface DepotFautifSansSociete {
		Optional<User> findByUsername(String username);

		@Query("select u from User u where u.username = :u")
		Optional<User> chercherSansSocieteJpql(String u);

		@Query(value = "select * from users where username = :u", nativeQuery = true)
		Optional<User> chercherSansSocieteNatif(String u);
	}

	@Test
	@DisplayName("Toutes les méthodes de UserRepository filtrent par société ou figurent dans la liste blanche transverse")
	void toutesLesMethodesDeUserRepositoryFiltrentParSociete() {
		List<String> violations = controlerDepotUtilisateur(UserRepository.class);
		assertThat(violations)
				.as("Méthodes de UserRepository sans filtre societe_id / societeId")
				.isEmpty();
	}

	@Test
	@DisplayName("Le vérificateur détecte une méthode dérivée, JPQL ou native sans filtre de société")
	void detecteMethodeSansFiltreSociete() {
		List<String> violations = controlerDepotUtilisateur(DepotFautifSansSociete.class);
		assertThat(violations)
				.hasSize(3)
				.anyMatch(v -> v.contains("findByUsername"))
				.anyMatch(v -> v.contains("chercherSansSocieteJpql"))
				.anyMatch(v -> v.contains("chercherSansSocieteNatif"));
	}

	static List<String> controlerDepotUtilisateur(Class<?> depot) {
		List<String> violations = new ArrayList<>();
		for (Method m : depot.getDeclaredMethods()) {
			if (m.isSynthetic() || m.isDefault()) {
				continue;
			}
			String nom = m.getName();
			Query query = m.getAnnotation(Query.class);

			if (METHODES_TRANSVERSES_AUTORISEES_USER.contains(nom)) {
				// Même dans la liste blanche, une requête native ou de recherche PLATEFORME doit mentionner societe_id
				// (sauf existsByEmailIgnoreCase qui est global sur toute l'installation selon D-SOC03)
				if (!"existsByEmailIgnoreCase".equals(nom) && query != null) {
					String sql = query.value().toLowerCase(Locale.ROOT);
					if (!sql.contains("societe_id") && !sql.contains("societeid")) {
						violations.add(nom + " (transverse sans condition sur societe_id/societeId)");
					}
				}
				continue;
			}

			boolean nomFiltre = nom.toLowerCase(Locale.ROOT).contains("societe");
			if (query == null) {
				if (!nomFiltre) {
					violations.add(nom + " (méthode dérivée sans SocieteId)");
				}
			} else {
				String texteRequete = query.value().toLowerCase(Locale.ROOT);
				if (!texteRequete.contains("societe_id") && !texteRequete.contains("societeid")) {
					violations.add(nom + " (@Query sans societe_id / societeId)");
				}
			}
		}
		return violations;
	}
}
