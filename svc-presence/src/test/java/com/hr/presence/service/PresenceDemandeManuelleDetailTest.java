package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.Affectation;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.dto.DemandeManuelleDetail;
import com.hr.presence.dto.DemandeManuelleResponse;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.numero.NumeroDemandeService;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-N01 : filtres, détail par identifiant et par numéro PM-, détail mobile (demandeur, valideur, anti-IDOR). */
class PresenceDemandeManuelleDetailTest {

	private final PresenceDemandeManuelleRepository demandeRepository = mock(PresenceDemandeManuelleRepository.class);
	private final PresencePointageRepository pointageRepository = mock(PresencePointageRepository.class);
	private final ReferentielClient referentiel = mock(ReferentielClient.class);
	private final PresenceAccessService accessService = mock(PresenceAccessService.class);
	private final PresenceSiteRepository siteRepository = mock(PresenceSiteRepository.class);
	private final PresenceProperties properties = new PresenceProperties();
	private PresenceDemandeManuelleService service;

	private final UUID fiche = UUID.randomUUID();
	private final UUID compte = UUID.randomUUID();
	private final UUID valideur = UUID.randomUUID();
	private final UUID siteId = UUID.randomUUID();
	private PresenceDemandeManuelle demande;

	@SuppressWarnings("unchecked")
	private static Specification<PresenceDemandeManuelle> anySpec() {
		return any(Specification.class);
	}

	@BeforeEach
	void setUp() {
		service = new PresenceDemandeManuelleService(demandeRepository, pointageRepository, referentiel, accessService,
				new FacialIdentiteService(accessService, referentiel), mock(PresenceEventPublisher.class), properties,
				siteRepository, mock(FenetreHoraireService.class), new NumeroDemandeService(cle -> 1L));

		PresencePointage rejet = new PresencePointage();
		rejet.setId(UUID.randomUUID());
		rejet.setCollaborateurId(fiche.toString());
		rejet.setType(PointageType.ENTREE);
		rejet.setStatut(PointageStatut.REJETE_HORS_ZONE);
		rejet.setDistanceMetres(179.8);
		rejet.setServerTs(Instant.parse("2026-11-04T07:50:00Z"));
		when(pointageRepository.findById(rejet.getId())).thenReturn(Optional.of(rejet));

		PresenceSite site = new PresenceSite();
		site.setId(siteId);
		site.setLibelle("Siège");
		site.setRayonMetres(50);
		when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));

		demande = new PresenceDemandeManuelle();
		demande.setId(UUID.randomUUID());
		demande.setNumero("PM-202611-0001");
		demande.setPointageRejeteId(rejet.getId());
		demande.setCollaborateurId(fiche.toString());
		demande.setCollaborateurNom("Sarra Khemiri");
		demande.setSiteId(siteId);
		demande.setType(PointageType.ENTREE);
		demande.setHeureDeclaree(Instant.parse("2026-11-04T08:00:00Z"));
		demande.setJustification("GPS");
		demande.setStatut(StatutDemandeManuelle.EN_ATTENTE);
		demande.setValideurId(valideur.toString());
		demande.setValideurNom("Omar Belhadj");
		demande.setCreeLe(Instant.parse("2026-11-04T08:05:00Z"));
		when(demandeRepository.findById(demande.getId())).thenReturn(Optional.of(demande));
		when(demandeRepository.findByNumero("PM-202611-0001")).thenReturn(Optional.of(demande));
		when(demandeRepository.compterValideesSurPeriode(any(), any(), any())).thenReturn(2L);
		when(referentiel.affectations(any())).thenReturn(List.of(new Affectation(fiche, compte, "COL-WEB",
				"Sarra Khemiri", "Direction Technique", "Logiciel", "Web")));
	}

	private void appelant(UUID ficheAppelant, UUID compteAppelant) {
		when(referentiel.monValideurPointage())
				.thenReturn(new ValideurPointage(ficheAppelant, compteAppelant, "X", null, null, null));
	}

	@Test
	void detailAdmin_porteSiteUniteValideurRefusEtCompteurDuMois() {
		DemandeManuelleDetail d = service.detailAdmin(demande.getId());

		assertThat(d.demande().numero()).isEqualTo("PM-202611-0001");
		assertThat(d.demande().collaborateurMatricule()).isEqualTo("COL-WEB");
		assertThat(d.siteLibelle()).isEqualTo("Siège");
		assertThat(d.uniteLibelle()).isEqualTo("Web");
		assertThat(d.valideur().nomComplet()).isEqualTo("Omar Belhadj");
		assertThat(d.pointageRejete().statut()).isEqualTo(PointageStatut.REJETE_HORS_ZONE);
		assertThat(d.pointageRejete().motifRejet()).isEqualTo("Hors zone de pointage (179,8 m pour une zone de 50 m)");
		assertThat(d.demande().manuelsDuMois()).isEqualTo(2L);
		assertThat(d.seuilMois()).isEqualTo(3);
	}

	@Test
	void detailAdmin_inconnu_404() {
		assertThatThrownBy(() -> service.detailAdmin(UUID.randomUUID()))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(404);
					assertThat(e.getCode()).isEqualTo("DEMANDE_INTROUVABLE");
				});
	}

	@Test
	void detailParNumero_trouve_inconnu404_malForme400() {
		assertThat(service.detailAdminParNumero("PM-202611-0001").demande().id()).isEqualTo(demande.getId());
		assertThatThrownBy(() -> service.detailAdminParNumero("PM-202611-9999"))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(404));
		assertThatThrownBy(() -> service.detailAdminParNumero("DEM-1"))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(400);
					assertThat(e.getCode()).isEqualTo("NUMERO_INVALIDE");
				});
	}

	@Test
	void demandeSansValideur_valideurAbsent_validationParRh() {
		demande.setValideurId(null);
		assertThat(service.detailAdmin(demande.getId()).valideur()).isNull();
	}

	@Test
	void detailMobile_demandeur_200_sansCompteurDuMois() {
		appelant(fiche, compte);

		DemandeManuelleDetail d = service.detailMobile(demande.getId());

		assertThat(d.demande().manuelsDuMois()).isNull();
		assertThat(d.seuilMois()).isNull();
		assertThat(d.siteLibelle()).isEqualTo("Siège");
	}

	@Test
	void detailMobile_valideurAttendu_200_avecCompteur() {
		appelant(valideur, UUID.randomUUID());

		assertThat(service.detailMobile(demande.getId()).demande().manuelsDuMois()).isEqualTo(2L);
	}

	@Test
	void detailMobile_autreCollaborateur_403HorsPerimetre() {
		appelant(UUID.randomUUID(), UUID.randomUUID());

		assertThatThrownBy(() -> service.detailMobile(demande.getId()))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(403);
					assertThat(e.getCode()).isEqualTo("HORS_PERIMETRE");
				});
	}

	@Test
	void detailMobile_demandeInconnue_403MemeCodeQueHorsPerimetre() {
		appelant(fiche, compte);

		assertThatThrownBy(() -> service.detailMobile(UUID.randomUUID()))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("HORS_PERIMETRE"));
	}

	@Test
	void detailMobile_rhSurDemandeSansValideur_200() {
		demande.setValideurId(null);
		appelant(UUID.randomUUID(), UUID.randomUUID());
		when(accessService.isBackofficeEcriture()).thenReturn(true);

		assertThat(service.detailMobile(demande.getId()).valideur()).isNull();
	}

	@Test
	void detailMobile_enteteDUnAutre_403IdentiteIncoherente() {
		appelant(fiche, compte);
		when(accessService.collaborateurHeaderOuNull()).thenReturn(UUID.randomUUID().toString());

		assertThatThrownBy(() -> service.detailMobile(demande.getId()))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("IDENTITE_INCOHERENTE"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void liste_filtreParCollaborateurElargiAuCompte_etPeriodeInversee400() {
		when(demandeRepository.findAll(anySpec(), any(Sort.class))).thenReturn(List.of(demande));

		List<DemandeManuelleResponse> liste = service.listerAdmin(null, fiche, null, null, null);

		assertThat(liste).hasSize(1);
		verify(referentiel, org.mockito.Mockito.atLeastOnce()).affectations(List.of(fiche));
		assertThatThrownBy(() -> service.listerAdmin(null, null, null, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1)))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("PERIODE_INVALIDE"));
	}

	@Test
	void liste_referentielCoupe_serviceSansNomsMaisAvecLeNomFige() {
		when(demandeRepository.findAll(anySpec(), any(Sort.class))).thenReturn(List.of(demande));
		when(referentiel.affectations(any())).thenThrow(PresenceMetierException.indisponible("REFERENTIEL_INDISPONIBLE", "x"));

		List<DemandeManuelleResponse> liste = service.listerAdmin(null, fiche, null, null, null);

		assertThat(liste.get(0).collaborateurNom()).isEqualTo("Sarra Khemiri");
		assertThat(liste.get(0).collaborateurMatricule()).isNull();
	}

	@Test
	void numeroHsEtPmDeCompteursDifferentsNeSontPasConfondus() {
		assertThatThrownBy(() -> service.detailAdminParNumero("HS-202611-0001"))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("NUMERO_INVALIDE"));
	}
}
