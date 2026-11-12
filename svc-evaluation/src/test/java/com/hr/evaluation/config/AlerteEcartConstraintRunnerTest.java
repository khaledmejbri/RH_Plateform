package com.hr.evaluation.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** D-E06.f : contraintes CHECK des enums des alertes recréées à chaque démarrage, idempotent, jamais bloquant. */
class AlerteEcartConstraintRunnerTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AlerteEcartConstraintRunner runner = new AlerteEcartConstraintRunner(jdbc);

    @Test
    void recreeLesCheckDeComportementEtSupprimeceuxDesCodesParametres() {
        runner.run(null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(AlerteEcartConstraintRunner.A_SUPPRIMER.size() + AlerteEcartConstraintRunner.CIBLES.size() * 2))
                .execute(sql.capture());
        List<String> requetes = sql.getAllValues();
        assertThat(requetes).anyMatch(s -> s.contains("ADD CONSTRAINT evaluation_alerte_ecart_statut_check")
                && s.contains("'OUVERTE', 'EN_COURS', 'TRAITEE'"));
        assertThat(requetes).anyMatch(s -> s.contains("evaluation_alerte_ecart_ligne_sens_actuel_check")
                && s.contains("'RO_INFERIEUR', 'RO_SUPERIEUR'"));
        assertThat(requetes).anyMatch(s -> s.contains("evaluation_alerte_ecart_historique_type_evenement_check")
                && s.contains("'REOUVERTURE_AUTOMATIQUE'"));
        // niveaux (7 colonnes) et conclusions (2 colonnes) : DROP seulement, jamais recréés
        assertThat(AlerteEcartConstraintRunner.A_SUPPRIMER).hasSize(9);
        for (var c : AlerteEcartConstraintRunner.A_SUPPRIMER) {
            assertThat(requetes).anyMatch(s -> s.contains("DROP CONSTRAINT IF EXISTS " + c.nomContrainte()));
            assertThat(requetes).noneMatch(s -> s.contains("ADD CONSTRAINT " + c.nomContrainte()));
        }
    }

    @Test
    void uneContrainteEnEchecNeBloquePasLesAutresNiLeDemarrage() {
        doThrow(new IllegalStateException("table absente")).when(jdbc).execute(contains("evaluation_alerte_ecart_statut_check"));

        runner.run(null);

        verify(jdbc, atLeastOnce()).execute(contains("evaluation_alerte_ecart_niveau_courant_check"));
        verify(jdbc, atLeastOnce()).execute(contains("evaluation_alerte_ecart_ligne_sens_actuel_check"));
        verify(jdbc, atLeastOnce()).execute(contains("evaluation_alerte_ecart_historique_statut_apres_check"));
        verify(jdbc, atLeastOnce()).execute(anyString());
    }
}
