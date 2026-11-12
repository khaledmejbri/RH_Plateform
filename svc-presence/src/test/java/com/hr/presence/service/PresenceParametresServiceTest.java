package com.hr.presence.service;

import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.TypeLieu;
import com.hr.presence.dto.CreateSiteRequest;
import com.hr.presence.dto.PatchSiteRequest;
import com.hr.presence.entity.PresenceParametreValeur;
import com.hr.presence.entity.PresenceParametres;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceParametreValeurRepository;
import com.hr.presence.repository.PresenceParametresRepository;
import com.hr.presence.repository.PresenceQrCredentialRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.PresenceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ticket 3D : les réglages et libellés de présence sont lus dans l'instantané (délai de grâce, rayons par défaut,
 * seuil des pointages manuels, libellé du motif de refus) et retombent sur les propriétés si la table est absente.
 */
class PresenceParametresServiceTest {

	private final PresenceParametreValeurRepository valeurs = mock(PresenceParametreValeurRepository.class);
	private final PresenceParametresRepository reglages = mock(PresenceParametresRepository.class);
	private PresenceProperties props;
	private PresenceParametres ligne;

	@BeforeEach
	void setUp() {
		props = new PresenceProperties();
		ligne = new PresenceParametres();
		when(reglages.findById(PresenceParametres.ID_UNIQUE)).thenReturn(Optional.of(ligne));
	}

	private PresenceParametresService service() {
		return new PresenceParametresService(valeurs, reglages, props, 30);
	}

	private static PresencePointage entree(Instant debutHoraire, Instant pointage) {
		PresencePointage p = new PresencePointage();
		p.setType(PointageType.ENTREE);
		p.setServerTs(pointage);
		p.setHoraireDebutTs(debutHoraire);
		return p;
	}

	@Test
	void graceCinqMinutes_0804SansRetard_0806UneMinute() {
		ligne.setDelaiGraceRetardMinutes(5);
		Duration grace = service().delaiGraceRetard();
		Instant debut = Instant.parse("2026-11-09T08:00:00Z");

		assertThat(RetardCalcul.calculer(List.of(entree(debut, Instant.parse("2026-11-09T08:04:00Z"))), grace)).isEqualTo(0);
		assertThat(RetardCalcul.calculer(List.of(entree(debut, Instant.parse("2026-11-09T08:06:00Z"))), grace)).isEqualTo(1);
	}

	@Test
	void sansLigneDeReglages_replisSurLesProprietes() {
		when(reglages.findById(PresenceParametres.ID_UNIQUE)).thenReturn(Optional.empty());
		props.getRetard().setDelaiGraceMinutes(7);
		props.getManuel().setSeuilAlerteMois(4);

		PresenceParametresService s = service();

		assertThat(s.delaiGraceRetard()).isEqualTo(Duration.ofMinutes(7));
		assertThat(s.seuilPointagesManuelsMois()).isEqualTo(4);
		assertThat(s.rayonDefaut(TypeLieu.EXTERIEUR)).isEqualTo(20);
		assertThat(s.rayonDefaut(TypeLieu.INTERIEUR)).isEqualTo(50);
	}

	@Test
	void seuilPointagesManuelsDeux_estLuParLAccesseur() {
		ligne.setSeuilPointagesManuelsMois(2);
		assertThat(service().seuilPointagesManuelsMois()).isEqualTo(2);
	}

	@Test
	void reglagesDuPointageManuel_luParLesAccesseurs() {
		ligne.setPlafondDistanceManuelMetres(150);
		ligne.setDelaiDemandeManuelMinutes(60);
		ligne.setDelaiDecisionManuelMinutes(480);

		PresenceParametresService s = service();

		assertThat(s.plafondDistanceManuelMetres()).isEqualTo(150);
		assertThat(s.delaiDemandeManuel()).isEqualTo(Duration.ofMinutes(60));
		assertThat(s.delaiDecisionManuel()).isEqualTo(Duration.ofMinutes(480));
	}

	@Test
	void colonnesManuelAbsentes_replisSurLesProprietes() {
		props.getManuel().setPlafondDistanceMetres(250);
		props.getManuel().setDelaiDemandeMinutes(90);
		props.getManuel().setDelaiDecisionMinutes(600);

		PresenceParametresService s = service();

		assertThat(s.plafondDistanceManuelMetres()).isEqualTo(250);
		assertThat(s.delaiDemandeManuel()).isEqualTo(Duration.ofMinutes(90));
		assertThat(s.delaiDecisionManuel()).isEqualTo(Duration.ofMinutes(600));
	}

	@Test
	void baseCoupee_defautsDuProduitPourLePointageManuel() {
		when(reglages.findById(PresenceParametres.ID_UNIQUE)).thenThrow(new IllegalStateException("base coupée"));

		PresenceParametresService s = service();

		assertThat(s.plafondDistanceManuelMetres()).isEqualTo(300);
		assertThat(s.delaiDemandeManuel()).isEqualTo(Duration.ofMinutes(120));
		assertThat(s.delaiDecisionManuel()).isEqualTo(Duration.ofMinutes(1440));
	}

	@Test
	void baseIndisponible_replisSansErreur() {
		when(reglages.findById(PresenceParametres.ID_UNIQUE)).thenThrow(new IllegalStateException("base coupée"));
		props.getRetard().setDelaiGraceMinutes(3);

		PresenceParametresService s = service();

		assertThat(s.delaiGraceRetard()).isEqualTo(Duration.ofMinutes(3));
		assertThat(s.libelleStatutPointage(PointageStatut.REJETE_QR_INVALIDE)).isEqualTo("QR code invalide");
	}

	@Test
	void libelleParametre_remplaceLibelleDOrigine() {
		PresenceParametreValeur v = new PresenceParametreValeur();
		v.setReferentiel(PresenceParametresService.STATUT_POINTAGE);
		v.setCode("REJETE_QR_INVALIDE");
		v.setLibelle("QR non reconnu");
		v.setLibelleNormalise("qr non reconnu");
		when(valeurs.findByReferentielOrderByOrdreAscCodeAsc(PresenceParametresService.STATUT_POINTAGE)).thenReturn(List.of(v));

		PresenceParametresService s = service();

		assertThat(s.libelleStatutPointage(PointageStatut.REJETE_QR_INVALIDE)).isEqualTo("QR non reconnu");
		assertThat(s.libelleStatutPointage(PointageStatut.REJETE_QR_EXPIRE)).isEqualTo("QR code expiré");
		assertThat(s.libellesStatutsPointage().get(PointageStatut.REJETE_QR_INVALIDE)).isEqualTo("QR non reconnu");
	}

	@Test
	void invalidation_rendLaNouvelleValeur() {
		ligne.setSeuilPointagesManuelsMois(3);
		PresenceParametresService s = service();
		assertThat(s.seuilPointagesManuelsMois()).isEqualTo(3);

		ligne.setSeuilPointagesManuelsMois(2);
		assertThat(s.seuilPointagesManuelsMois()).isEqualTo(3); // encore l'instantané (30 s)
		s.invaliderApresCommit();
		assertThat(s.seuilPointagesManuelsMois()).isEqualTo(2);
	}

	@Test
	void rayonInterieurTrente_nouveauSiteSeulement() {
		ligne.setRayonDefautInterieurMetres(30);
		PresenceSiteRepository siteRepository = mock(PresenceSiteRepository.class);
		PresenceQrCredentialRepository qrRepository = mock(PresenceQrCredentialRepository.class);
		PresenceAccessService acces = mock(PresenceAccessService.class);
		when(siteRepository.save(any(PresenceSite.class))).thenAnswer(inv -> {
			PresenceSite s = inv.getArgument(0);
			if (s.getId() == null) {
				s.setId(UUID.randomUUID());
			}
			return s;
		});
		when(qrRepository.findBySiteIdAndStatut(any(), any())).thenReturn(Optional.empty());
		PresenceSiteService sites = new PresenceSiteService(siteRepository, qrRepository, props, acces, service());

		// nouveau site intérieur sans rayon : 30 m ; extérieur : inchangé (20 m)
		assertThat(sites.create(new CreateSiteRequest("INT", "Bureau", null, null, "INTERIEUR", null)).rayonMetres()).isEqualTo(30);
		assertThat(sites.create(new CreateSiteRequest("EXT", "Chantier", null, null, null, null)).rayonMetres()).isEqualTo(20);
		// rayon fourni : respecté
		assertThat(sites.create(new CreateSiteRequest("INT2", "Bureau 2", null, null, "INTERIEUR", 80)).rayonMetres()).isEqualTo(80);

		// site existant : un simple renommage ne touche pas à son rayon
		PresenceSite existant = new PresenceSite();
		existant.setId(UUID.randomUUID());
		existant.setCode("OLD");
		existant.setLibelle("Ancien");
		existant.setTypeLieu(TypeLieu.INTERIEUR);
		existant.setRayonMetres(50);
		when(siteRepository.findById(existant.getId())).thenReturn(Optional.of(existant));
		var r = sites.patch(existant.getId(), new PatchSiteRequest("Ancien renommé", null, null, null, null));
		assertThat(r.rayonMetres()).isEqualTo(50);
	}

	@Test
	void libelleStatutDemande_suitLeParametreMemeInactif_etRetombeSurLOrigine() {
		PresenceParametreValeur v = new PresenceParametreValeur();
		v.setCode("EXPIREE");
		v.setLibelle("Délai dépassé");
		v.setLibelleNormalise("delai depasse");
		v.setActif(false);
		when(valeurs.findByReferentielOrderByOrdreAscCodeAsc(PresenceParametresService.STATUT_DEMANDE_MANUELLE))
				.thenReturn(List.of(v));
		PresenceParametresService service = service();

		assertThat(service.libelleStatutDemande(com.hr.presence.domain.StatutDemandeManuelle.EXPIREE)).isEqualTo("Délai dépassé");
		assertThat(service.libelleStatutDemande(com.hr.presence.domain.StatutDemandeManuelle.VALIDEE)).isEqualTo("Validée");
	}

	@Test
	void libelleStatutPointageParCode_codeInconnuDonneValeurNonReconnue() {
		PresenceParametresService service = service();

		assertThat(service.libelleStatutPointage("REJETE_HORS_ZONE")).isEqualTo("Hors zone de pointage");
		assertThat(service.libelleStatutPointage("CODE_INCONNU")).isEqualTo("Valeur non reconnue");
		assertThat(service.libelleStatutPointage((String) null)).isNull();
	}
}
