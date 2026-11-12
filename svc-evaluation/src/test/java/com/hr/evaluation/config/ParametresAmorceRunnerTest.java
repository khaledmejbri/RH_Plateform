package com.hr.evaluation.config;

import com.hr.evaluation.domain.ValeursInitialesEvaluation;
import com.hr.evaluation.entity.ParametreValeur;
import com.hr.evaluation.repository.ParametreJournalRepository;
import com.hr.evaluation.repository.ParametreValeurRepository;
import com.hr.evaluation.service.ParametresCache;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ADR lot 3 § 8 : amorçage rejouable, une ligne existante n'est jamais réécrite, index partiel, réglages remplis. */
class ParametresAmorceRunnerTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ParametreValeurRepository valeurs = mock(ParametreValeurRepository.class);
    private final ParametreJournalRepository journal = mock(ParametreJournalRepository.class);
    private final ParametresCache cache = mock(ParametresCache.class);
    private final ParametresAmorceRunner runner = new ParametresAmorceRunner(jdbc, valeurs, journal, cache, 3);

    @Test
    void premierDemarrage_insereToutesLesValeursInitiales_niveauxMarquesUtilises() {
        List<ParametreValeur> inserees = new ArrayList<>();
        when(valeurs.existsByReferentielAndCode(anyString(), anyString())).thenReturn(false);
        when(valeurs.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> {
            inserees.add(i.getArgument(0));
            return i.getArgument(0);
        });

        runner.amorcer();

        assertThat(inserees).hasSizeGreaterThanOrEqualTo(ValeursInitialesEvaluation.toutes().size());
        assertThat(inserees).extracting(v -> v.getReferentiel() + "/" + v.getCode())
                .containsAll(ValeursInitialesEvaluation.toutes().stream().map(v -> v.referentiel() + "/" + v.code()).toList());
        ParametreValeur orange = inserees.stream().filter(v -> v.getCode().equals("ORANGE")).findFirst().orElseThrow();
        assertThat(orange.getSeuilEcart()).isEqualTo(2);
        assertThat(orange.getPremiereUtilisationLe()).isNotNull();
        assertThat(inserees.stream().filter(v -> v.getCode().equals("AUTRE")).findFirst().orElseThrow().isSysteme()).isTrue();
        verify(cache).invalider();
    }

    @Test
    void rejoue_neReecritRien() {
        when(valeurs.existsByReferentielAndCode(anyString(), anyString())).thenReturn(true);
        when(valeurs.findByReferentielOrderByOrdreAscCodeAsc(anyString())).thenAnswer(i -> {
            List<ParametreValeur> l = new ArrayList<>();
            ValeursInitialesEvaluation.toutes().stream().filter(v -> v.referentiel().equals(i.getArgument(0)))
                    .forEach(v -> l.add(ParametresAmorceRunner.nouvelle(v.referentiel(), v.code(), v.nature(),
                            v.libelle(), v.ordre(), v.systeme())));
            return l;
        });

        runner.amorcer();
        runner.amorcer();

        verify(valeurs, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void indexPartielEtReglagesEtColonnes() {
        runner.amorcer();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.atLeastOnce()).execute(sql.capture());
        assertThat(sql.getAllValues()).anyMatch(s -> s.contains("CREATE UNIQUE INDEX IF NOT EXISTS")
                && s.contains("(seuil_ecart)") && s.contains("WHERE referentiel = 'NIVEAU_ALERTE' AND actif"));
        verify(jdbc).update("UPDATE evaluation_parametres SET version = 0 WHERE version IS NULL");
        verify(jdbc).update("UPDATE evaluation_parametres SET seuil_insuffisance = ? WHERE seuil_insuffisance IS NULL", 3);
        verify(jdbc, times(ParametresAmorceRunner.COLONNES_A_ELARGIR.size())).query(anyString(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Integer>>any(), anyString(), anyString());
    }

    @Test
    void orphelin_recreeInactifEtMarqueUtilise() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("niveau_detection"), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of("ANCIEN_NIVEAU"));
        when(valeurs.existsByReferentielAndCode(anyString(), anyString())).thenReturn(true);
        when(valeurs.existsByReferentielAndCode("NIVEAU_ALERTE", "ANCIEN_NIVEAU")).thenReturn(false);
        List<ParametreValeur> inserees = new ArrayList<>();
        when(valeurs.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> {
            inserees.add(i.getArgument(0));
            return i.getArgument(0);
        });

        runner.amorcer();

        assertThat(inserees.stream().filter(v -> v.getCode().equals("ANCIEN_NIVEAU"))).singleElement().satisfies(v -> {
            assertThat(v.getCode()).isEqualTo("ANCIEN_NIVEAU");
            assertThat(v.isActif()).isFalse();
            assertThat(v.getPremiereUtilisationLe()).isNotNull();
        });
        verify(journal).save(org.mockito.ArgumentMatchers.any());
    }
}
