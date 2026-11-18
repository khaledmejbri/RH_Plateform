package com.hr.commun.societe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SocieteFiltreInterneTest {

	private static final String JETON = "jeton-interne-test";
	private final SocieteFiltreInterne filtre = new SocieteFiltreInterne(JETON);

	@AfterEach
	void tearDown() {
		SocieteContexte.effacer();
	}

	@Test
	@DisplayName("Route /internal/** sans X-Internal-Token ou avec mauvais jeton → 403")
	void jetonAbsentOuInvalide_renvoie403() throws Exception {
		MockHttpServletRequest reqSans = new MockHttpServletRequest("GET", "/internal/users/by-role");
		reqSans.addHeader("X-Societe-Id", UUID.randomUUID().toString());
		MockHttpServletResponse resSans = new MockHttpServletResponse();
		AtomicReference<Boolean> appele = new AtomicReference<>(false);

		filtre.doFilter(reqSans, resSans, (r, s) -> appele.set(true));

		assertThat(appele.get()).isFalse();
		assertThat(resSans.getStatus()).isEqualTo(403);

		MockHttpServletRequest reqMauvais = new MockHttpServletRequest("GET", "/internal/identite/v1/societes");
		reqMauvais.addHeader("X-Internal-Token", "mauvais");
		MockHttpServletResponse resMauvais = new MockHttpServletResponse();

		filtre.doFilter(reqMauvais, resMauvais, (r, s) -> appele.set(true));

		assertThat(appele.get()).isFalse();
		assertThat(resMauvais.getStatus()).isEqualTo(403);
	}

	@Test
	@DisplayName("Route /internal/** hors liste blanche sans X-Societe-Id (sans repli) ou mal formé → 400 SOCIETE_ABSENTE")
	void societeAbsenteOuMalFormee_renvoie400() throws Exception {
		MockHttpServletRequest reqSans = new MockHttpServletRequest("GET", "/internal/users/by-role");
		reqSans.addHeader("X-Internal-Token", JETON);
		MockHttpServletResponse resSans = new MockHttpServletResponse();
		AtomicReference<Boolean> appele = new AtomicReference<>(false);

		filtre.doFilter(reqSans, resSans, (r, s) -> appele.set(true));

		assertThat(appele.get()).isFalse();
		assertThat(resSans.getStatus()).isEqualTo(400);
		assertThat(resSans.getContentAsString()).contains("SOCIETE_ABSENTE");

		MockHttpServletRequest reqInvalide = new MockHttpServletRequest("GET", "/internal/users/by-role");
		reqInvalide.addHeader("X-Internal-Token", JETON);
		reqInvalide.addHeader("X-Societe-Id", "pas-un-uuid");
		MockHttpServletResponse resInvalide = new MockHttpServletResponse();

		filtre.doFilter(reqInvalide, resInvalide, (r, s) -> appele.set(true));

		assertThat(appele.get()).isFalse();
		assertThat(resInvalide.getStatus()).isEqualTo(400);
		assertThat(resInvalide.getContentAsString()).contains("SOCIETE_ABSENTE");
	}

	@Test
	@DisplayName("D-SOC03.e (B3) : avec fournisseur de repli actif, X-Societe-Id absent utilise la société par défaut ; mal formé reste 400")
	void repliTransition_societeAbsenteUtiliseDefaut_malFormeReste400() throws Exception {
		UUID societeDefautId = UUID.randomUUID();
		SocieteFiltreInterne filtreAvecRepli = new SocieteFiltreInterne(JETON, () -> societeDefautId);

		// 1. Sans X-Societe-Id → pose societeDefautId
		MockHttpServletRequest reqSansEntete = new MockHttpServletRequest("GET", "/internal/users/by-role");
		reqSansEntete.addHeader("X-Internal-Token", JETON);
		MockHttpServletResponse resSansEntete = new MockHttpServletResponse();
		AtomicReference<UUID> observe = new AtomicReference<>();

		filtreAvecRepli.doFilter(reqSansEntete, resSansEntete, (r, s) -> observe.set(SocieteContexte.courante()));

		assertThat(resSansEntete.getStatus()).isEqualTo(200);
		assertThat(observe.get()).isEqualTo(societeDefautId);
		assertThat(SocieteContexte.couranteOuVide()).isEmpty();

		// 2. Avec X-Societe-Id mal formé → 400 SOCIETE_ABSENTE malgré le repli actif
		MockHttpServletRequest reqMalForme = new MockHttpServletRequest("GET", "/internal/users/by-role");
		reqMalForme.addHeader("X-Internal-Token", JETON);
		reqMalForme.addHeader("X-Societe-Id", "uuid-invalide");
		MockHttpServletResponse resMalForme = new MockHttpServletResponse();
		AtomicReference<Boolean> appele = new AtomicReference<>(false);

		filtreAvecRepli.doFilter(reqMalForme, resMalForme, (r, s) -> appele.set(true));

		assertThat(appele.get()).isFalse();
		assertThat(resMalForme.getStatus()).isEqualTo(400);
		assertThat(resMalForme.getContentAsString()).contains("SOCIETE_ABSENTE");

		// 3. Repli désactivé (fournisseur renvoie null) → 400 SOCIETE_ABSENTE
		SocieteFiltreInterne filtreRepliInactif = new SocieteFiltreInterne(JETON, () -> null);
		MockHttpServletRequest reqRepliOff = new MockHttpServletRequest("GET", "/internal/users/by-role");
		reqRepliOff.addHeader("X-Internal-Token", JETON);
		MockHttpServletResponse resRepliOff = new MockHttpServletResponse();

		filtreRepliInactif.doFilter(reqRepliOff, resRepliOff, (r, s) -> appele.set(true));

		assertThat(appele.get()).isFalse();
		assertThat(resRepliOff.getStatus()).isEqualTo(400);
		assertThat(resRepliOff.getContentAsString()).contains("SOCIETE_ABSENTE");
	}

	@Test
	@DisplayName("Liste blanche /internal/identite/v1/societes/** acceptée avec X-Internal-Token sans X-Societe-Id")
	void listeBlancheSocietes_sansEnteteSociete_passe() throws Exception {
		MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/identite/v1/societes/par-code/PRINCIPALE");
		req.addHeader("X-Internal-Token", JETON);
		MockHttpServletResponse res = new MockHttpServletResponse();
		AtomicReference<Boolean> appele = new AtomicReference<>(false);

		filtre.doFilter(req, res, (r, s) -> appele.set(true));

		assertThat(appele.get()).isTrue();
		assertThat(res.getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("Route /internal/** avec jeton et X-Societe-Id valides pose SocieteContexte et nettoie en fin de requête")
	void jetonEtSocieteValides_poseContexte() throws Exception {
		UUID societeId = UUID.randomUUID();
		MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/users/by-role");
		req.addHeader("X-Internal-Token", JETON);
		req.addHeader("X-Societe-Id", societeId.toString());
		MockHttpServletResponse res = new MockHttpServletResponse();
		AtomicReference<UUID> observe = new AtomicReference<>();

		filtre.doFilter(req, res, (r, s) -> observe.set(SocieteContexte.courante()));

		assertThat(res.getStatus()).isEqualTo(200);
		assertThat(observe.get()).isEqualTo(societeId);
		assertThat(SocieteContexte.couranteOuVide()).isEmpty();
	}
}
