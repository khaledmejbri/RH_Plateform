package com.hr.evaluation.service;

import com.hr.evaluation.domain.StatutAlerteEcart;
import com.hr.evaluation.entity.AlerteEcart;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** D-E08.e : non closes d'abord, URGENTE > ROUGE > ORANGE, détection croissante, identifiant croissant. */
class AlerteEcartComparateurTest {

    private static final AlerteEcartComparateur COMPARATEUR =
            new AlerteEcartComparateur(ParametresTestes.parDefaut()::seuil);

    private static final Instant MAINTENANT = Instant.parse("2026-11-04T10:00:00Z");

    private static AlerteEcart alerte(String nom, String niveau, StatutAlerteEcart statut, int joursPassés, UUID id) {
        AlerteEcart a = new AlerteEcart();
        a.setId(id);
        a.setCollaborateurNom(nom);
        a.setNiveauCourant(niveau);
        a.setStatut(statut);
        a.setDetecteeLe(MAINTENANT.minus(joursPassés, ChronoUnit.DAYS));
        return a;
    }

    @Test
    void ordreDuCahierDesCharges() {
        AlerteEcart urgente = alerte("urgente 1j", "URGENTE", StatutAlerteEcart.OUVERTE, 1, UUID.randomUUID());
        AlerteEcart rouge = alerte("rouge 3j", "ROUGE", StatutAlerteEcart.EN_COURS, 3, UUID.randomUUID());
        AlerteEcart orangeAncienne = alerte("orange 10j", "ORANGE", StatutAlerteEcart.OUVERTE, 10, UUID.randomUUID());
        AlerteEcart orangeRecente = alerte("orange 5j", "ORANGE", StatutAlerteEcart.OUVERTE, 5, UUID.randomUUID());
        AlerteEcart urgenteTraitee = alerte("urgente traitée", "URGENTE", StatutAlerteEcart.TRAITEE, 20, UUID.randomUUID());

        List<AlerteEcart> liste = new ArrayList<>(List.of(orangeRecente, urgenteTraitee, rouge, orangeAncienne, urgente));
        Collections.shuffle(liste);
        liste.sort(COMPARATEUR);

        assertThat(liste).containsExactly(urgente, rouge, orangeAncienne, orangeRecente, urgenteTraitee);
    }

    @Test
    void niveauAligneApresOrange() {
        AlerteEcart aligne = alerte("aligné", "ALIGNE", StatutAlerteEcart.OUVERTE, 30, UUID.randomUUID());
        AlerteEcart orange = alerte("orange", "ORANGE", StatutAlerteEcart.OUVERTE, 1, UUID.randomUUID());

        List<AlerteEcart> liste = new ArrayList<>(List.of(aligne, orange));
        liste.sort(COMPARATEUR);

        assertThat(liste).containsExactly(orange, aligne);
    }

    @Test
    void memeNiveauMemeDate_departageParIdentifiantCroissant() {
        UUID petit = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID grand = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        AlerteEcart a = alerte("a", "ROUGE", StatutAlerteEcart.OUVERTE, 2, grand);
        AlerteEcart b = alerte("b", "ROUGE", StatutAlerteEcart.OUVERTE, 2, petit);

        List<AlerteEcart> liste = new ArrayList<>(List.of(a, b));
        liste.sort(COMPARATEUR);

        assertThat(liste).containsExactly(b, a);
    }

    @Test
    void uneAlerteTraiteeUrgenteResteApresUneNonClosePeuGrave() {
        AlerteEcart traitee = alerte("t", "URGENTE", StatutAlerteEcart.TRAITEE, 50, UUID.randomUUID());
        AlerteEcart ouverte = alerte("o", "ORANGE", StatutAlerteEcart.OUVERTE, 1, UUID.randomUUID());

        assertThat(COMPARATEUR.compare(ouverte, traitee)).isNegative();
    }
}
