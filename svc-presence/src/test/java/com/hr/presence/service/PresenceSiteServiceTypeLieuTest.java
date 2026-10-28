package com.hr.presence.service;

import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.TypeLieu;
import com.hr.presence.dto.CreateSiteRequest;
import com.hr.presence.dto.EmplacementRequest;
import com.hr.presence.dto.PatchSiteRequest;
import com.hr.presence.dto.SiteResponse;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceQrCredentialRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * D-P01 (SCRUM-35) : type de lieu et rayon par site.
 */
@ExtendWith(MockitoExtension.class)
class PresenceSiteServiceTypeLieuTest {

	@Mock
	private PresenceSiteRepository siteRepository;
	@Mock
	private PresenceQrCredentialRepository qrRepository;
	@Mock
	private PresenceAccessService accessService;

	private PresenceSiteService service;

	@BeforeEach
	void setUp() {
		service = new PresenceSiteService(siteRepository, qrRepository, new PresenceProperties(), accessService);
		lenient().when(siteRepository.save(any(PresenceSite.class))).thenAnswer(inv -> {
			PresenceSite s = inv.getArgument(0);
			if (s.getId() == null) {
				s.setId(UUID.randomUUID());
			}
			return s;
		});
		lenient().when(qrRepository.findBySiteIdAndStatut(any(), any())).thenReturn(Optional.empty());
	}

	@Test
	void creationExterieurParDefaut_rayon20() {
		SiteResponse r = service.create(new CreateSiteRequest("CH1", "Chantier 1", null, null, null, null));

		assertThat(r.typeLieu()).isEqualTo(TypeLieu.EXTERIEUR);
		assertThat(r.rayonMetres()).isEqualTo(20);
	}

	@Test
	void creationInterieur_rayon50() {
		SiteResponse r = service.create(new CreateSiteRequest("HQ", "Siège", null, null, "interieur", null));

		assertThat(r.typeLieu()).isEqualTo(TypeLieu.INTERIEUR);
		assertThat(r.rayonMetres()).isEqualTo(50);
	}

	@Test
	void rayonHorsBornes_refuse() {
		assertThatThrownBy(() -> service.create(new CreateSiteRequest("X", "X", null, null, "EXTERIEUR", 5)))
				.isInstanceOf(PresenceMetierException.class);
		assertThatThrownBy(() -> service.create(new CreateSiteRequest("Y", "Y", null, null, "EXTERIEUR", 150)))
				.isInstanceOf(PresenceMetierException.class);
	}

	@Test
	void typeLieuInconnu_refuse() {
		assertThatThrownBy(() -> service.create(new CreateSiteRequest("Z", "Z", null, null, "SOUS_SOL", null)))
				.isInstanceOf(PresenceMetierException.class);
	}

	@Test
	void changementDeTypeSansRayon_appliqueLeDefautDuNouveauType() {
		PresenceSite site = siteExistant(20, TypeLieu.EXTERIEUR);

		SiteResponse r = service.patch(site.getId(), new PatchSiteRequest(null, null, "INTERIEUR", null, null));

		assertThat(r.typeLieu()).isEqualTo(TypeLieu.INTERIEUR);
		assertThat(r.rayonMetres()).isEqualTo(50);
	}

	@Test
	void rayonExplicite_conserve() {
		PresenceSite site = siteExistant(20, TypeLieu.EXTERIEUR);

		SiteResponse r = service.patch(site.getId(), new PatchSiteRequest(null, null, "INTERIEUR", 35, null));

		assertThat(r.rayonMetres()).isEqualTo(35);
	}

	@Test
	void siteAnterieur_luCommeExterieur() {
		PresenceSite site = siteExistant(50, null);

		assertThat(service.get(site.getId()).typeLieu()).isEqualTo(TypeLieu.EXTERIEUR);
		assertThat(service.get(site.getId()).rayonMetres()).isEqualTo(50);
	}

	// --- D-P03 (SCRUM-37) ---

	@Test
	void ancrageExterieurPrecis_enregistreAvecPrecision() {
		PresenceSite site = siteExistant(20, TypeLieu.EXTERIEUR);

		SiteResponse r = service.updateEmplacement(site.getId(),
				new EmplacementRequest(36.8, 10.18, 6.5, 12, false));

		assertThat(r.precisionAncrageMetres()).isEqualTo(6.5);
		assertThat(r.nbMesuresAncrage()).isEqualTo(12);
		assertThat(r.latitude()).isEqualTo(36.8);
	}

	@Test
	void ancrageExterieurImprecis_refuse() {
		PresenceSite site = siteExistant(20, TypeLieu.EXTERIEUR);

		assertThatThrownBy(() -> service.updateEmplacement(site.getId(),
				new EmplacementRequest(36.8, 10.18, 15.0, 10, false)))
				.isInstanceOf(PresenceMetierException.class)
				.hasMessageContaining("Signal GPS trop faible");
	}

	@Test
	void ancrageInterieur_tolere25m() {
		PresenceSite site = siteExistant(50, TypeLieu.INTERIEUR);

		SiteResponse r = service.updateEmplacement(site.getId(),
				new EmplacementRequest(36.8, 10.18, 22.0, 10, false));

		assertThat(r.precisionAncrageMetres()).isEqualTo(22.0);
	}

	@Test
	void ancragePositionSimulee_refuse() {
		PresenceSite site = siteExistant(20, TypeLieu.EXTERIEUR);

		assertThatThrownBy(() -> service.updateEmplacement(site.getId(),
				new EmplacementRequest(36.8, 10.18, 3.0, 10, true)))
				.isInstanceOf(PresenceMetierException.class);
	}

	private PresenceSite siteExistant(int rayon, TypeLieu type) {
		PresenceSite site = new PresenceSite();
		site.setId(UUID.randomUUID());
		site.setCode("S");
		site.setLibelle("S");
		site.setRayonMetres(rayon);
		site.setTypeLieu(type);
		when(siteRepository.findById(site.getId())).thenReturn(Optional.of(site));
		return site;
	}
}
