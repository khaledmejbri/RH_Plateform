package com.hr.presence.service;

import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.ActionAuditFacial;
import com.hr.presence.domain.StatutReglageFacial;
import com.hr.presence.dto.FacialAdminDtos.ReglageRequest;
import com.hr.presence.dto.FacialAdminDtos.ReglageResponse;
import com.hr.presence.entity.PresenceConsentementVersion;
import com.hr.presence.entity.PresenceReglageFacial;
import com.hr.presence.repository.ConsentementVersionRepository;
import com.hr.presence.repository.ReglageFacialRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-P06 : réglage société (INACTIVE par défaut, seuil 80-99, garde-fous d'activation, dates de mise en service). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReglageFacialServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-10T09:00:00Z");

	@Mock
	private ReglageFacialRepository reglageRepository;
	@Mock
	private ConsentementVersionRepository consentementRepository;
	@Mock
	private FacialAuditService auditService;
	@Mock
	private PresenceAccessService accessService;

	private ReglageFacialService service;
	private PresenceReglageFacial stocke;

	@BeforeEach
	void setUp() {
		service = new ReglageFacialService(reglageRepository, consentementRepository, auditService, accessService,
				new PresenceProperties(), Clock.fixed(MAINTENANT, ZoneId.of("Africa/Tunis")));
		when(accessService.currentCompteId()).thenReturn("compte-rh");
		stocke = null;
		when(reglageRepository.findById(PresenceReglageFacial.ID_SOCIETE)).thenAnswer(inv -> Optional.ofNullable(stocke));
		when(reglageRepository.save(any(PresenceReglageFacial.class))).thenAnswer(inv -> {
			stocke = inv.getArgument(0);
			return stocke;
		});
		when(consentementRepository.findFirstByOrderByPublieLeDescVersionDesc()).thenReturn(
				Optional.of(new PresenceConsentementVersion("v1", "texte", "rh", MAINTENANT)));
	}

	private static ReglageRequest requete(StatutReglageFacial statut, LocalDate date, Integer seuil, Boolean cadre) {
		return new ReglageRequest(statut, date, seuil, cadre);
	}

	private void assertErreur(ReglageRequest r, String code, int statut) {
		assertThatThrownBy(() -> service.modifier(r)).isInstanceOfSatisfying(PresenceMetierException.class, e -> {
			assertThat(e.getCode()).isEqualTo(code);
			assertThat(e.getHttpStatus()).isEqualTo(statut);
		});
	}

	@Test
	void parDefaut_inactif_seuil92_sansLigne() {
		assertThat(service.etat().actif()).isFalse();
		assertThat(service.etat().seuilScore()).isEqualTo(92);
		assertThat(service.etat().controleExige()).isFalse();
		ReglageResponse r = service.lire();
		assertThat(r.statut()).isEqualTo(StatutReglageFacial.INACTIVE);
		assertThat(r.versionConsentementCourante()).isEqualTo("v1");
	}

	@Test
	void seuilEn80Et99_accepte() {
		assertThat(service.modifier(requete(StatutReglageFacial.INACTIVE, null, 80, null)).seuilScore()).isEqualTo(80);
		assertThat(service.modifier(requete(StatutReglageFacial.INACTIVE, null, 99, null)).seuilScore()).isEqualTo(99);
	}

	@Test
	void seuilHorsPlage_400() {
		assertErreur(requete(StatutReglageFacial.INACTIVE, null, 79, null), "SEUIL_HORS_PLAGE", 400);
		assertErreur(requete(StatutReglageFacial.INACTIVE, null, 100, null), "SEUIL_HORS_PLAGE", 400);
		verify(reglageRepository, never()).save(any());
	}

	@Test
	void activation_sansVersionPubliee_422() {
		when(consentementRepository.findFirstByOrderByPublieLeDescVersionDesc()).thenReturn(Optional.empty());
		assertErreur(requete(StatutReglageFacial.ACTIVE, LocalDate.of(2026, 12, 1), 92, true), "VERSION_CONSENTEMENT_NON_PUBLIEE", 422);
	}

	@Test
	void activation_sansCadreLegalValide_422() {
		assertErreur(requete(StatutReglageFacial.ACTIVE, LocalDate.of(2026, 12, 1), 92, false), "CONFIRMATION_CADRE_LEGAL_REQUISE", 422);
		assertErreur(requete(StatutReglageFacial.ACTIVE, LocalDate.of(2026, 12, 1), 92, null), "CONFIRMATION_CADRE_LEGAL_REQUISE", 422);
	}

	@Test
	void activation_sansDate_422() {
		assertErreur(requete(StatutReglageFacial.ACTIVE, null, 92, true), "DATE_MISE_EN_SERVICE_REQUISE", 422);
	}

	@Test
	void activation_valide_traceLeCadreLegalEtLeReglage() {
		ReglageResponse r = service.modifier(requete(StatutReglageFacial.ACTIVE, LocalDate.of(2026, 12, 1), 90, true));

		assertThat(r.statut()).isEqualTo(StatutReglageFacial.ACTIVE);
		assertThat(r.seuilScore()).isEqualTo(90);
		assertThat(r.cadreLegalConfirmePar()).isEqualTo("compte-rh");
		assertThat(r.cadreLegalConfirmeLe()).isEqualTo(MAINTENANT);
		assertThat(r.modifiePar()).isEqualTo("compte-rh");
		verify(auditService).tracer(eq(ActionAuditFacial.REGLAGE_MODIFIE), any(), any(), any(), any(), any(), anyString());
		verify(auditService).tracer(eq(ActionAuditFacial.CADRE_LEGAL_CONFIRME), any(), any(), any(), any(), any(), anyString());
	}

	@Test
	void activeAvantLaDate_controleNonExige_puisExigeLeJourJ() {
		service.modifier(requete(StatutReglageFacial.ACTIVE, LocalDate.of(2026, 11, 11), 92, true));
		assertThat(service.etat().actif()).isTrue();
		assertThat(service.etat().controleExige()).isFalse();

		service.modifier(requete(StatutReglageFacial.ACTIVE, LocalDate.of(2026, 11, 10), 92, null));
		assertThat(service.etat().controleExige()).isTrue();
	}

	@Test
	void dejaActif_changerLeSeuil_necessitePasUneNouvelleConfirmation_etNeTracePasLeCadre() {
		service.modifier(requete(StatutReglageFacial.ACTIVE, LocalDate.of(2026, 12, 1), 92, true));

		ReglageResponse r = service.modifier(requete(StatutReglageFacial.ACTIVE, null, 85, null));

		assertThat(r.seuilScore()).isEqualTo(85);
		assertThat(r.dateMiseEnService()).isEqualTo(LocalDate.of(2026, 12, 1));
		verify(auditService, org.mockito.Mockito.times(1))
				.tracer(eq(ActionAuditFacial.CADRE_LEGAL_CONFIRME), any(), any(), any(), any(), any(), anyString());
	}

	@Test
	void desactivation_conserveDateEtSeuil() {
		service.modifier(requete(StatutReglageFacial.ACTIVE, LocalDate.of(2026, 12, 1), 88, true));

		ReglageResponse r = service.modifier(requete(StatutReglageFacial.INACTIVE, null, null, null));

		assertThat(r.statut()).isEqualTo(StatutReglageFacial.INACTIVE);
		assertThat(r.seuilScore()).isEqualTo(88);
		assertThat(r.controleExige()).isFalse();
	}

	@Test
	void compteIllisible_refuse() {
		when(accessService.currentCompteId()).thenReturn(null);
		assertThatThrownBy(() -> service.modifier(requete(StatutReglageFacial.INACTIVE, null, null, null)))
				.isInstanceOf(SecurityException.class);
	}
}
