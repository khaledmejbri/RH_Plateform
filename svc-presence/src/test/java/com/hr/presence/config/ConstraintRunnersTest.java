package com.hr.presence.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;

/** Les CHECK stockés suivent les enums : nouveaux statuts de pointage et tables de la vague faciale. */
class ConstraintRunnersTest {

	private static List<String> executer(java.util.function.Consumer<JdbcTemplate> action) {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		action.accept(jdbc);
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbc, atLeastOnce()).execute(sql.capture());
		return sql.getAllValues();
	}

	@Test
	void contrainteStatutPointage_inclutLesTroisRejetsFaciaux() {
		List<String> requetes = executer(j -> new PointageStatutConstraintRunner(j).run(null));

		String ajout = requetes.stream().filter(s -> s.contains("ADD CONSTRAINT")).findFirst().orElseThrow();
		assertThat(ajout).contains("'REJETE_VISAGE_NON_RECONNU'", "'REJETE_VIVACITE'",
				"'REJETE_VERIFICATION_FACIALE_ABSENTE'", "'VALIDE_MANUEL'");
	}

	@Test
	void contraintesFaciales_recreeesDepuisLesEnums() {
		List<String> requetes = executer(j -> new FacialConstraintRunner(j).run(null));
		String tout = String.join("\n", requetes);

		assertThat(tout).contains("presence_enrolement_facial_statut_check", "'A_REENROLER'", "'REVOQUE'");
		assertThat(tout).contains("presence_audit_facial_action_check", "'DEBLOCAGE_RH'", "'BLOCAGE_ECHECS'",
				"'CADRE_LEGAL_CONFIRME'");
		assertThat(tout).contains("presence_reglage_facial_statut_check", "'ACTIVE'", "'INACTIVE'");
		assertThat(tout).contains("presence_pointage_verification_faciale_check", "'NON_CONSENTIE'", "'NON_DISPONIBLE'");
		assertThat(tout).contains("presence_pointage_plateforme_check", "'ANDROID'", "'WEB'");
		assertThat(requetes.stream().filter(s -> s.contains("DROP CONSTRAINT IF EXISTS")).count())
				.isEqualTo(requetes.stream().filter(s -> s.contains("ADD CONSTRAINT")).count());
	}

	@Test
	void runnerFacial_toleraTableAbsente_sansEchouer() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		org.mockito.Mockito.doThrow(new RuntimeException("table absente")).when(jdbc).execute(anyString());

		new FacialConstraintRunner(jdbc).run(null);
	}
}
