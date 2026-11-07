package com.hr.evaluation.service;

import com.hr.evaluation.domain.EvenementAlerteEcart;
import com.hr.evaluation.domain.NiveauEcart;
import com.hr.evaluation.domain.StatutAlerteEcart;
import com.hr.evaluation.dto.AlerteEcartDetail;
import com.hr.evaluation.dto.AlerteEcartResume;
import com.hr.evaluation.entity.AlerteEcart;
import com.hr.evaluation.entity.AlerteEcartHistorique;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Lot 1 UAT, ticket 1B (D-L01.a, D-L01.e) : libellés lisibles, jamais de code ni d'identifiant à la place d'un nom. */
class AlerteEcartMapperLisibiliteTest {

    private static AlerteEcart alerte() {
        AlerteEcart a = new AlerteEcart();
        a.setId(UUID.randomUUID());
        a.setNiveauCourant(NiveauEcart.ORANGE);
        a.setNiveauDetection(NiveauEcart.ORANGE);
        a.setStatut(StatutAlerteEcart.EN_COURS);
        a.setDetecteeLe(Instant.now());
        return a;
    }

    @Test
    void historiqueSystemeEtActionHumaine() {
        AlerteEcart a = alerte();
        a.setCollaborateurNom("Sarra Khemiri");
        a.setCampagneNom("T4 2026");
        a.ajouterHistorique(AlerteEcartHistorique.systeme(EvenementAlerteEcart.CREATION,
                null, StatutAlerteEcart.OUVERTE, null, NiveauEcart.ORANGE));
        a.ajouterHistorique(AlerteEcartHistorique.par(EvenementAlerteEcart.PRISE_EN_CHARGE, UUID.randomUUID(),
                "Amel Trabelsi", StatutAlerteEcart.OUVERTE, StatutAlerteEcart.EN_COURS, NiveauEcart.ORANGE, null, null));

        AlerteEcartDetail d = AlerteEcartMapper.detail(a, true, null);

        assertThat(d.historique()).hasSize(2);
        AlerteEcartDetail.Evenement creation = d.historique().get(0);
        assertThat(creation.auteurLibelle()).isEqualTo("Système (automatique)");
        assertThat(creation.auteurSysteme()).isTrue();
        assertThat(creation.statutAvantLibelle()).isEqualTo("Création");
        assertThat(creation.statutApresLibelle()).isEqualTo("Ouverte");
        AlerteEcartDetail.Evenement prise = d.historique().get(1);
        assertThat(prise.auteurLibelle()).isEqualTo("Amel Trabelsi");
        assertThat(prise.auteurSysteme()).isFalse();
        assertThat(prise.typeEvenementLibelle()).isEqualTo("Prise en charge");
        assertThat(prise.statutAvantLibelle()).isEqualTo("Ouverte");
        assertThat(prise.statutApresLibelle()).isEqualTo("En cours");
    }

    @Test
    void nomAbsentRemplaceParUnLibelleJamaisParUnIdentifiant() {
        AlerteEcart a = alerte();

        AlerteEcartResume r = AlerteEcartMapper.resume(a);

        assertThat(r.collaborateurNom()).isEqualTo("Collaborateur introuvable");
        assertThat(r.campagneNom()).isEqualTo("Campagne introuvable");
        assertThat(AlerteEcartMapper.detail(a, false, "LECTURE_SEULE").collaborateurNom())
                .isEqualTo("Collaborateur introuvable");
    }
}
