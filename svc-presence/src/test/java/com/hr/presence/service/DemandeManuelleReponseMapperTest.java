package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.SignalVigilance;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.dto.DemandeManuelleDetail;
import com.hr.presence.dto.DemandeManuelleResponse;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.SignauxVigilanceConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static com.hr.presence.service.ManuelTestSupport.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Contrat JSON de la ressource demande (ADR annexe A) : champs figés, signaux en clair, masquage pour le demandeur
 * (D-P24.d), aucune coordonnée (C-L-03).
 */
class DemandeManuelleReponseMapperTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private ManuelTestSupport t;
	private PresenceDemandeManuelleService service;

	@BeforeEach
	void setUp() {
		t = new ManuelTestSupport();
		service = t.service;
		t.siteSansHoraire();
	}

	private PresenceDemandeManuelle demandeSignalee() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setRefusStatut("REJETE_HORS_ZONE");
		d.setDistanceMetres(280.0);
		d.setPrecisionMetres(35.0);
		d.setRayonMetres(50);
		d.setModeQr("PAPIER");
		d.setPlafondMetres(300);
		d.setDelaiDemandeMinutes(100L);
		d.setCommentaireValideur("Vu avec le chef de site");
		d.setSignaux(List.of(SignalVigilance.DISTANCE_SUP_2X_RAYON, SignalVigilance.PRECISION_SUP_RAYON,
				SignalVigilance.SITE_QR_PAPIER, SignalVigilance.MANUELS_MOIS_SEUIL, SignalVigilance.REFUS_FACIAL,
				SignalVigilance.DEMANDE_TARDIVE));
		return d;
	}

	@Test
	void vueDecideur_porteSignauxEnClair_compteursPlafondEtCommentaire() {
		PresenceDemandeManuelle d = demandeSignalee();
		when(t.pointageRepository.countByCollaborateurIdAndStatutInAndServerTsGreaterThanEqualAndServerTsLessThan(
				eq(t.ficheCollab.toString()), any(), any(), any())).thenReturn(5L);
		when(t.demandeRepository.compterValideesSurPeriode(eq(t.ficheCollab.toString()), any(), any())).thenReturn(4L);
		when(t.demandeRepository.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<PresenceDemandeManuelle>>any(),
				org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(d));

		DemandeManuelleResponse r = service.listerAdmin(null).get(0);

		assertThat(r.statutLibelle()).isEqualTo("En attente");
		assertThat(r.refusStatut()).isEqualTo("REJETE_HORS_ZONE");
		assertThat(r.refusStatutLibelle()).isEqualTo("Hors zone de pointage");
		assertThat(r.modeQr()).isEqualTo("PAPIER");
		assertThat(r.modeQrLibelle()).isEqualTo("Papier");
		assertThat(r.plafondMetres()).isEqualTo(300);
		assertThat(r.reprise()).isFalse();
		assertThat(r.avecSignal()).isTrue();
		assertThat(r.signaux()).extracting(DemandeManuelleResponse.Signal::code).containsExactly(
				"DISTANCE_SUP_2X_RAYON", "PRECISION_SUP_RAYON", "SITE_QR_PAPIER", "MANUELS_MOIS_SEUIL", "REFUS_FACIAL",
				"DEMANDE_TARDIVE");
		assertThat(r.signaux().get(0).libelle()).isEqualTo("Position à 280,0 m du site (zone de 50 m)");
		assertThat(r.signaux().get(2).libelle()).isEqualTo("Site à QR papier : le code peut avoir été photographié");
		assertThat(r.signaux().get(5).libelle()).isEqualTo("Demande faite 1 h 40 après la tentative");
		assertThat(r.manuelsDuMois()).isEqualTo(4L);
		assertThat(r.seuilMois()).isEqualTo(3);
		assertThat(r.alerteSeuil()).isTrue();
		assertThat(r.refusDuJour()).isEqualTo(5L);
		assertThat(r.commentaireValideur()).isEqualTo("Vu avec le chef de site");
	}

	@Test
	void vueDemandeur_masqueSignauxCompteursPlafondEtCommentaire_maisGardeLesMesures() {
		PresenceDemandeManuelle d = demandeSignalee();
		when(t.referentielClient.monValideurPointage()).thenReturn(t.collab(t.ficheRo, t.compteRo));
		when(t.demandeRepository.findByCollaborateurIdOrderByCreeLeDesc(t.ficheCollab.toString())).thenReturn(List.of(d));

		DemandeManuelleResponse r = service.mesDemandes().get(0);

		assertThat(r.signaux()).isNull();
		assertThat(r.avecSignal()).isNull();
		assertThat(r.plafondMetres()).isNull();
		assertThat(r.reprise()).isNull();
		assertThat(r.manuelsDuMois()).isNull();
		assertThat(r.seuilMois()).isNull();
		assertThat(r.alerteSeuil()).isNull();
		assertThat(r.refusDuJour()).isNull();
		assertThat(r.commentaireValideur()).isNull();
		assertThat(r.delaiDemandeMinutes()).isNull();
		assertThat(r.delaiDecisionMinutes()).isNull();
		assertThat(r.numero()).isEqualTo("PM-202611-0007");
		assertThat(r.echeance()).isNotNull();
		assertThat(r.distanceMetres()).isEqualTo(280.0);
		JsonNode json = JSON.valueToTree(r);
		for (String cle : List.of("signaux", "avec_signal", "plafond_metres", "reprise", "manuels_du_mois", "seuil_mois",
				"alerte_seuil", "refus_du_jour", "commentaire_valideur", "delai_demande_minutes", "delai_decision_minutes")) {
			assertThat(json.has(cle)).as(cle).isFalse();
		}
		assertThat(json.has("echeance")).isTrue();
		assertThat(json.has("refus_statut")).isTrue();
	}

	@Test
	void aucuneCleLatitudeOuLongitude_dansLeJsonDesListesEtDesDetails() {
		PresenceDemandeManuelle d = demandeSignalee();
		t.rejet.setLatitude(36.80651);
		t.rejet.setLongitude(10.18152);
		when(t.demandeRepository.findById(d.getId())).thenReturn(java.util.Optional.of(d));
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());
		when(t.demandeRepository.findByValideurIdAndStatutOrderByCreeLeAsc(t.ficheRo.toString(), StatutDemandeManuelle.EN_ATTENTE))
				.thenReturn(List.of(d));

		DemandeManuelleDetail detail = service.detailAdmin(d.getId());
		List<DemandeManuelleResponse> file = service.aValider();

		String json = JSON.writeValueAsString(List.of(detail, file));
		assertThat(json.toLowerCase()).doesNotContain("latitude").doesNotContain("longitude").doesNotContain("36.8065")
				.doesNotContain("10.1815");
		assertThat(JSON.valueToTree(detail).has("seuil_mois")).isTrue();
	}

	@Test
	void demandeAncienne_champsFigesNuls_sontAbsentsDuJson() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setEcheance(null);
		when(t.referentielClient.monValideurPointage()).thenReturn(t.collab(t.ficheRo, t.compteRo));
		when(t.demandeRepository.findByCollaborateurIdOrderByCreeLeDesc(t.ficheCollab.toString())).thenReturn(List.of(d));

		JsonNode json = JSON.valueToTree(service.mesDemandes().get(0));

		assertThat(json.has("distance_metres")).isFalse();
		assertThat(json.has("echeance")).isFalse();
		assertThat(json.has("refus_statut")).isFalse();
	}

	@Test
	void detailMobile_demandeurSansSignaux_valideurAvecSignaux() {
		PresenceDemandeManuelle d = demandeSignalee();
		when(t.demandeRepository.findById(d.getId())).thenReturn(java.util.Optional.of(d));
		when(t.referentielClient.monValideurPointage()).thenReturn(t.collab(t.ficheRo, t.compteRo));
		assertThat(service.detailMobile(d.getId()).demande().signaux()).isNull();

		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());
		assertThat(service.detailMobile(d.getId()).demande().signaux()).hasSize(6);
	}

	@Test
	void expiree_libelleExpiree() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setStatut(StatutDemandeManuelle.EXPIREE);
		when(t.referentielClient.monValideurPointage()).thenReturn(t.collab(t.ficheRo, t.compteRo));
		when(t.demandeRepository.findByCollaborateurIdOrderByCreeLeDesc(t.ficheCollab.toString())).thenReturn(List.of(d));

		assertThat(service.mesDemandes().get(0).statutLibelle()).isEqualTo("Expirée");
	}

	@Test
	void convertisseurDeSignaux_aller_retour_vide_etCodeInconnu() {
		SignauxVigilanceConverter c = new SignauxVigilanceConverter();

		assertThat(c.convertToDatabaseColumn(List.of(SignalVigilance.SITE_QR_PAPIER, SignalVigilance.REFUS_FACIAL)))
				.isEqualTo("SITE_QR_PAPIER,REFUS_FACIAL");
		assertThat(c.convertToDatabaseColumn(List.of())).isNull();
		assertThat(c.convertToEntityAttribute(null)).isEmpty();
		assertThat(c.convertToEntityAttribute("SITE_QR_PAPIER,CODE_FUTUR")).containsExactly(SignalVigilance.SITE_QR_PAPIER);
	}

	@Test
	void listeAdmin_avecSignal_filtreTransmis_etUneDemandeSansSignalNeCompteComme() {
		PresenceDemandeManuelle sans = t.demandeEnAttente();
		when(t.demandeRepository.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<PresenceDemandeManuelle>>any(),
				org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(sans));

		DemandeManuelleResponse r = service.listerAdmin(null, null, null, null, null, true).get(0);

		assertThat(r.avecSignal()).isFalse();
		assertThat(r.signaux()).isEmpty();
	}

	@Test
	void validerParUnValideur_retourneLaVueDecideur() {
		PresenceDemandeManuelle d = demandeSignalee();
		when(t.referentielClient.monValideurPointage()).thenReturn(new ValideurPointage(t.ficheRo, t.compteRo, "Omar Belhadj",
				UUID.randomUUID(), UUID.randomUUID(), "Chef"));

		DemandeManuelleResponse r = service.valider(d.getId(), "Vérifié avec le site");

		assertThat(r.statut()).isEqualTo(StatutDemandeManuelle.VALIDEE);
		assertThat(r.signaux()).hasSize(6);
		assertThat(r.commentaireValideur()).isEqualTo("Vérifié avec le site");
		assertThat(r.delaiDecisionMinutes()).isEqualTo(10L);
		assertThat(PointageStatut.valueOf(r.refusStatut())).isEqualTo(PointageStatut.REJETE_HORS_ZONE);
		assertThat(MAINTENANT).isNotNull();
	}
}
