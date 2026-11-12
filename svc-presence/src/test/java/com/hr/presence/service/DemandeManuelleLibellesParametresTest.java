package com.hr.presence.service;

import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.dto.DemandeManuelleResponse;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresenceParametreValeur;
import com.hr.presence.client.ReferentielClient;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresenceParametreValeurRepository;
import com.hr.presence.repository.PresenceParametresRepository;
import com.hr.presence.repository.PresencePointageRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** D-R02 : changer un libellé de statut dans Paramètres change {@code statut_libelle} et {@code refus_statut_libelle}. */
class DemandeManuelleLibellesParametresTest {

	private static PresenceParametreValeur valeur(String code, String libelle) {
		PresenceParametreValeur v = new PresenceParametreValeur();
		v.setCode(code);
		v.setLibelle(libelle);
		v.setLibelleNormalise(libelle.toLowerCase());
		v.setActif(true);
		return v;
	}

	@Test
	void statutLibelleEtRefusStatutLibelleViennentDesParametres() {
		PresenceParametreValeurRepository valeurs = mock(PresenceParametreValeurRepository.class);
		when(valeurs.findByReferentielOrderByOrdreAscCodeAsc(PresenceParametresService.STATUT_DEMANDE_MANUELLE))
				.thenReturn(List.of(valeur("EXPIREE", "Délai dépassé")));
		when(valeurs.findByReferentielOrderByOrdreAscCodeAsc(PresenceParametresService.STATUT_POINTAGE))
				.thenReturn(List.of(valeur("REJETE_HORS_ZONE", "Trop loin du site")));
		PresenceProperties props = new PresenceProperties();
		PresenceParametresService parametres = new PresenceParametresService(valeurs,
				mock(PresenceParametresRepository.class), props, 30);
		PresenceDemandeManuelleRepository demandes = mock(PresenceDemandeManuelleRepository.class);
		when(demandes.compterValideesSurPeriode(any(), any(), any())).thenReturn(0L);
		DemandeManuelleReponseMapper mapper = new DemandeManuelleReponseMapper(demandes,
				mock(PresencePointageRepository.class), mock(ReferentielClient.class), parametres);

		PresenceDemandeManuelle d = new PresenceDemandeManuelle();
		d.setId(UUID.randomUUID());
		d.setCollaborateurId(UUID.randomUUID().toString());
		d.setStatut(StatutDemandeManuelle.EXPIREE);
		d.setRefusStatut("REJETE_HORS_ZONE");
		d.setHeureDeclaree(Instant.parse("2026-11-04T08:00:00Z"));

		DemandeManuelleResponse r = mapper.versReponse(d, null, DemandeManuelleReponseMapper.Vue.DEMANDEUR, null);

		assertThat(r.statutLibelle()).isEqualTo("Délai dépassé");
		assertThat(r.refusStatutLibelle()).isEqualTo("Trop loin du site");
	}
}
