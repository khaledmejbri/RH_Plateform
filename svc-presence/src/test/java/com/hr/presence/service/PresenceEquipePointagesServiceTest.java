package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.CollaborateurResolu;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.dto.PointagesEquipeDtos.PointagesEquipePage;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.service.FacialIdentiteService.Identite;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceEquipePointagesServiceTest {

	@Mock private FacialIdentiteService facialIdentiteService;
	@Mock private ReferentielClient referentielClient;
	@Mock private PresencePointageRepository pointageRepository;
	@Mock private PresenceSiteRepository siteRepository;

	private PresenceEquipePointagesService service;

	private UUID roFicheId;
	private UUID sarraFicheId;
	private UUID sarraCompteId;
	private UUID ahmedFicheId;
	private UUID ahmedCompteId;
	private UUID mariemFicheId;
	private UUID siteId;

	@BeforeEach
	void setUp() {
		PresenceProperties props = new PresenceProperties();
		TempsTravailService tempsTravail = new TempsTravailService(
				pointageRepository, siteRepository, null, null, referentielClient, props,
				ParametresTestSupport.depuis(props),
				Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneId.of("Africa/Tunis")));
		service = new PresenceEquipePointagesService(
				tempsTravail, facialIdentiteService, referentielClient, pointageRepository, siteRepository,
				ParametresTestSupport.depuis(props));

		roFicheId = UUID.randomUUID();
		sarraFicheId = UUID.randomUUID();
		sarraCompteId = UUID.randomUUID();
		ahmedFicheId = UUID.randomUUID();
		ahmedCompteId = UUID.randomUUID();
		mariemFicheId = UUID.randomUUID();
		siteId = UUID.randomUUID();

		ValideurPointage roValideur = new ValideurPointage(roFicheId, UUID.randomUUID(), "Omar Belhadj", null, null, null);
		lenient().when(facialIdentiteService.resoudre()).thenReturn(new Identite(UUID.randomUUID().toString(), roValideur));
		lenient().when(referentielClient.perimetreValidation(roFicheId)).thenReturn(List.of(
				new CollaborateurResolu(sarraFicheId, sarraCompteId, "COL-WEB", "Sarra Khémiri", true, null, null, roFicheId, null),
				new CollaborateurResolu(ahmedFicheId, ahmedCompteId, "COL-WEB2", "Ahmed Nasri", true, null, null, roFicheId, null)
		));
	}

	private PresencePointage pointage(String collabId, PointageType type, PointageStatut statut, Instant ts) {
		PresencePointage p = new PresencePointage();
		p.setId(UUID.randomUUID());
		p.setCollaborateurId(collabId);
		p.setType(type);
		p.setStatut(statut);
		p.setServerTs(ts);
		p.setSiteId(siteId);
		return p;
	}

	@SuppressWarnings("unchecked")
	@Test
	void periodeDefaut7Jours_etEnrichissementFicheCanoniqueSansNPlus1() {
		PresenceSite site = new PresenceSite();
		site.setId(siteId);
		site.setCode("SIEGE");
		site.setLibelle("Siège Social");

		// Stocké sous sarraCompteId (ancienne ligne) : doit ressortir avec collaborateurId = sarraFicheId
		PresencePointage p1 = pointage(sarraCompteId.toString(), PointageType.ENTREE, PointageStatut.VALIDE,
				Instant.parse("2026-11-24T08:15:12Z"));
		PresencePointage p2 = pointage(ahmedFicheId.toString(), PointageType.SORTIE, PointageStatut.REJETE_HORS_ZONE,
				Instant.parse("2026-11-23T17:00:00Z"));
		p2.setDistanceMetres(120.5);
		site.setRayonMetres(50);

		when(pointageRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(p1, p2)));
		when(siteRepository.findAllById(any())).thenReturn(List.of(site));

		PointagesEquipePage res = service.lister(null, null, null, null, null, 0, 20);

		assertThat(res.du()).isEqualTo(LocalDate.of(2026, 11, 18));
		assertThat(res.au()).isEqualTo(LocalDate.of(2026, 11, 24));
		assertThat(res.effectifEquipe()).isEqualTo(2);
		assertThat(res.elements()).hasSize(2);
		assertThat(res.elements().get(0).collaborateurId()).isEqualTo(sarraFicheId);
		assertThat(res.elements().get(0).collaborateurNom()).isEqualTo("Sarra Khémiri");
		assertThat(res.elements().get(0).collaborateurMatricule()).isEqualTo("COL-WEB");
		assertThat(res.elements().get(0).typeLibelle()).isEqualTo("Entrée");
		assertThat(res.elements().get(0).statutLibelle()).isEqualTo("Pointage validé");
		assertThat(res.elements().get(0).siteLibelle()).isEqualTo("Siège Social");
		assertThat(res.elements().get(1).motifRejet()).contains("Hors zone de pointage");

		verify(siteRepository, times(1)).findAllById(any());
	}

	@SuppressWarnings("unchecked")
	@Test
	void rechercheTexte_sansAccentsCasseEtOrdreInverse_filtreEquipeUniquement() {
		when(pointageRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of()));

		service.lister(null, null, "  khemiri sarra ", null, null, 0, 20);

		ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(pointageRepository).findAll(any(Specification.class), pageCaptor.capture());
		assertThat(pageCaptor.getValue().getSort().getOrderFor("serverTs")).isNotNull();

		// Recherche sur un matricule ou nom hors équipe (Mariem / COL-MOB) -> 200 vide sans requête SQL
		org.mockito.Mockito.reset(pointageRepository);
		PointagesEquipePage horsEquipe = service.lister(null, null, "Mariem", null, null, 0, 20);
		assertThat(horsEquipe.elements()).isEmpty();
		assertThat(horsEquipe.totalElements()).isZero();
		assertThat(horsEquipe.effectifEquipe()).isEqualTo(2);
		verify(pointageRepository, never()).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	void validationPeriodeEtRecherche_400_etAntiIdor_403_etReferentiel_503() {
		assertThatThrownBy(() -> service.lister(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 20), null, null, null, 0, 20))
				.isInstanceOf(PresenceMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 400)
				.hasFieldOrPropertyWithValue("code", "PERIODE_INVALIDE");

		assertThatThrownBy(() -> service.lister(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 24), null, null, null, 0, 20))
				.isInstanceOf(PresenceMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 400)
				.hasFieldOrPropertyWithValue("code", "PERIODE_TROP_LONGUE");

		assertThatThrownBy(() -> service.lister(null, null, "a".repeat(101), null, null, 0, 20))
				.isInstanceOf(PresenceMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 400)
				.hasFieldOrPropertyWithValue("code", "RECHERCHE_TROP_LONGUE");

		assertThatThrownBy(() -> service.lister(null, null, null, mariemFicheId, null, 0, 20))
				.isInstanceOf(PresenceMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 403)
				.hasFieldOrPropertyWithValue("code", "HORS_PERIMETRE");

		// Collaborateur simple sans équipe -> 200 vide, mais 403 si collaborateur_id fourni
		when(referentielClient.perimetreValidation(roFicheId)).thenReturn(List.of());
		PointagesEquipePage vide = service.lister(null, null, null, null, null, 0, 20);
		assertThat(vide.elements()).isEmpty();
		assertThat(vide.effectifEquipe()).isZero();
		assertThatThrownBy(() -> service.lister(null, null, null, sarraFicheId, null, 0, 20))
				.isInstanceOf(PresenceMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 403)
				.hasFieldOrPropertyWithValue("code", "HORS_PERIMETRE");

		// Identité incohérente -> 403
		when(facialIdentiteService.resoudre())
				.thenThrow(new PresenceMetierException("IDENTITE_INCOHERENTE", "Incohérent", 403));
		assertThatThrownBy(() -> service.lister(null, null, null, null, null, 0, 20))
				.isInstanceOf(PresenceMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 403)
				.hasFieldOrPropertyWithValue("code", "IDENTITE_INCOHERENTE");

		// Référentiel indisponible -> 503
		org.mockito.Mockito.doReturn(new Identite(
				UUID.randomUUID().toString(),
				new ValideurPointage(roFicheId, UUID.randomUUID(), "Omar", null, null, null)))
				.when(facialIdentiteService).resoudre();
		when(referentielClient.perimetreValidation(roFicheId))
				.thenThrow(PresenceMetierException.indisponible("REFERENTIEL_INDISPONIBLE", "Down"));
		assertThatThrownBy(() -> service.lister(null, null, null, null, null, 0, 20))
				.isInstanceOf(PresenceMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 503)
				.hasFieldOrPropertyWithValue("code", "REFERENTIEL_INDISPONIBLE");
	}
}
