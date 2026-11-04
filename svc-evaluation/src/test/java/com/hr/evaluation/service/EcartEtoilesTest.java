package com.hr.evaluation.service;

import com.hr.evaluation.domain.NiveauEcart;
import com.hr.evaluation.domain.SensEcart;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** D-E04 / D-E05 : seuils 1 / 2 / 3 / 4 dans les deux sens, sens global. */
class EcartEtoilesTest {

    @Test
    void tousLesCouplesSuiventLesSeuilsDansLesDeuxSens() {
        for (int collaborateur = 1; collaborateur <= 5; collaborateur++) {
            for (int ro = 1; ro <= 5; ro++) {
                int ecart = Math.abs(collaborateur - ro);
                NiveauEcart attendu = switch (ecart) {
                    case 0, 1 -> NiveauEcart.ALIGNE;
                    case 2 -> NiveauEcart.ORANGE;
                    case 3 -> NiveauEcart.ROUGE;
                    default -> NiveauEcart.URGENTE;
                };
                assertThat(EcartEtoiles.ecart(collaborateur, ro)).as(collaborateur + "/" + ro).isEqualTo(ecart);
                assertThat(EcartEtoiles.niveau(collaborateur, ro)).as(collaborateur + "/" + ro).isEqualTo(attendu);
            }
        }
    }

    @Test
    void sensSelonLaNoteDuRo() {
        assertThat(EcartEtoiles.sens(4, 2)).isEqualTo(SensEcart.RO_INFERIEUR);
        assertThat(EcartEtoiles.sens(2, 5)).isEqualTo(SensEcart.RO_SUPERIEUR);
        assertThat(EcartEtoiles.sens(3, 3)).isNull();
    }

    @Test
    void niveauxOrdonnesAlignePuisOrangePuisRougePuisUrgente() {
        assertThat(NiveauEcart.ALIGNE).isLessThan(NiveauEcart.ORANGE);
        assertThat(NiveauEcart.ORANGE).isLessThan(NiveauEcart.ROUGE);
        assertThat(NiveauEcart.ROUGE).isLessThan(NiveauEcart.URGENTE);
        assertThat(NiveauEcart.ALIGNE.estAlerte()).isFalse();
        assertThat(NiveauEcart.ORANGE.estAlerte()).isTrue();
    }

    @Test
    void sensGlobal_unSeulSens_lesDeuxSens_ouAucun() {
        assertThat(EcartEtoiles.sensGlobal(List.of(SensEcart.RO_INFERIEUR, SensEcart.RO_INFERIEUR)))
                .isEqualTo("RO_INFERIEUR");
        assertThat(EcartEtoiles.sensGlobal(List.of(SensEcart.RO_SUPERIEUR))).isEqualTo("RO_SUPERIEUR");
        assertThat(EcartEtoiles.sensGlobal(List.of(SensEcart.RO_INFERIEUR, SensEcart.RO_SUPERIEUR)))
                .isEqualTo(SensEcart.DEUX_SENS);
        assertThat(EcartEtoiles.sensGlobal(Arrays.asList((SensEcart) null))).isNull();
        assertThat(EcartEtoiles.sensGlobal(List.of())).isNull();
    }

    @Test
    void etoilesValidesDeUnACinq() {
        assertThat(EcartEtoiles.estEtoilesValide(0)).isFalse();
        assertThat(EcartEtoiles.estEtoilesValide(1)).isTrue();
        assertThat(EcartEtoiles.estEtoilesValide(5)).isTrue();
        assertThat(EcartEtoiles.estEtoilesValide(6)).isFalse();
        assertThat(EcartEtoiles.estEtoilesValide(null)).isFalse();
    }

    @Test
    void libellesDeSensProduitsParLeServeur() {
        assertThat(SensEcart.RO_INFERIEUR.libelleSens()).startsWith("Note du responsable plus basse");
        assertThat(SensEcart.RO_SUPERIEUR.libelleSens()).startsWith("Note du responsable plus haute");
        assertThat(SensEcart.LIBELLE_DEUX_SENS).isEqualTo("Écarts dans les deux sens");
    }
}
