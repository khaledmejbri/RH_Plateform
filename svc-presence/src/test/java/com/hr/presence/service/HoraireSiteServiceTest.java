package com.hr.presence.service;

import com.hr.presence.config.ClockConfig;
import com.hr.presence.dto.HoraireDtos.HoraireSiteRequest;
import com.hr.presence.dto.HoraireDtos.HoraireSiteResponse;
import com.hr.presence.dto.HoraireDtos.JourHoraireRequest;
import com.hr.presence.entity.PresenceHoraireSite;
import com.hr.presence.entity.PresenceHoraireSiteHistorique;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceHoraireSiteHistoriqueRepository;
import com.hr.presence.repository.PresenceHoraireSiteRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.ActeurResolver;
import com.hr.presence.security.ActeurResolver.Acteur;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-P11 (SCRUM-48) : saisie, validation, trace et effet sur les demandes de l'horaire hebdomadaire d'un site. */
@ExtendWith(MockitoExtension.class)
class HoraireSiteServiceTest {

	private static final Instant MAINTENANT = Instant.parse("2026-11-02T09:00:00Z");

	@Mock
	private PresenceSiteRepository siteRepository;
	@Mock
	private PresenceHoraireSiteRepository horaireRepository;
	@Mock
	private PresenceHoraireSiteHistoriqueRepository historiqueRepository;
	@Mock
	private HeuresSupAjustementHoraireService ajustementService;
	@Mock
	private ActeurResolver acteurResolver;

	private HoraireSiteService service;
	private PresenceSite site;
	private final List<PresenceHoraireSite> lignesEnBase = new ArrayList<>();

	@BeforeEach
	void setUp() {
		service = new HoraireSiteService(siteRepository, horaireRepository, historiqueRepository, ajustementService,
				acteurResolver, Clock.fixed(MAINTENANT, ClockConfig.ZONE_TUNIS));
		site = new PresenceSite();
		site.setId(UUID.randomUUID());
		lenient().when(siteRepository.findById(site.getId())).thenReturn(Optional.of(site));
		lenient().when(horaireRepository.findBySiteIdOrderByJourSemaine(site.getId())).thenAnswer(inv -> List.copyOf(lignesEnBase));
		lenient().when(horaireRepository.save(any(PresenceHoraireSite.class))).thenAnswer(inv -> {
			PresenceHoraireSite l = inv.getArgument(0);
			if (!lignesEnBase.contains(l)) {
				lignesEnBase.add(l);
			}
			return l;
		});
		lenient().when(acteurResolver.depuisJwt()).thenReturn(new Acteur(UUID.randomUUID().toString(), null, "DRH01", null));
		lenient().when(historiqueRepository.findFirstBySiteIdOrderByModifieLeDesc(site.getId())).thenReturn(Optional.empty());
	}

	private static JourHoraireRequest jour(String jour, String debut, String fin) {
		return new JourHoraireRequest(jour, debut, fin);
	}

	private static List<JourHoraireRequest> semaineDeBureau() {
		return List.of(jour("LUNDI", "06:00", "15:00"), jour("MARDI", "06:00", "15:00"), jour("MERCREDI", "06:00", "15:00"),
				jour("JEUDI", "06:00", "15:00"), jour("VENDREDI", "06:00", "15:00"));
	}

	private static void codeErreur(Runnable action, String code, int statut) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(PresenceMetierException.class, e -> {
			assertThat(e.getCode()).isEqualTo(code);
			assertThat(e.getHttpStatus()).isEqualTo(statut);
		});
	}

	@Test
	void saisieLundiVendredi_toleranceAvant60Apres90_relueIdentique_etTraceeParLAuteur() {
		HoraireSiteResponse r = service.remplacer(site.getId(), new HoraireSiteRequest(60, 90, semaineDeBureau()));

		assertThat(r.avecHoraire()).isTrue();
		assertThat(r.toleranceAvantMinutes()).isEqualTo(60);
		assertThat(r.toleranceApresMinutes()).isEqualTo(90);
		assertThat(r.jours()).hasSize(5);
		assertThat(r.jours().get(0).jour()).isEqualTo("LUNDI");
		assertThat(r.jours().get(0).debut()).isEqualTo("06:00");
		assertThat(r.jours().get(0).fin()).isEqualTo("15:00");
		assertThat(r.jours().get(0).nuit()).isFalse();
		assertThat(site.getToleranceApresMinutes()).isEqualTo(90);
		ArgumentCaptor<PresenceHoraireSiteHistorique> trace = ArgumentCaptor.forClass(PresenceHoraireSiteHistorique.class);
		verify(historiqueRepository).save(trace.capture());
		assertThat(trace.getValue().getModifieParNom()).isEqualTo("DRH01");
		assertThat(trace.getValue().getModifieLe()).isEqualTo(MAINTENANT);
		assertThat(trace.getValue().getAvantJson()).contains("\"jours\":[]");
		assertThat(trace.getValue().getApresJson()).contains("LUNDI").contains("tolerance_apres_minutes\":90");
		verify(ajustementService).appliquer(eq(site), anyMap(), eq(60), anyMap(), any(Acteur.class));
	}

	@Test
	void toleranceDe105Ou50Minutes_400() {
		codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(105, 60, semaineDeBureau())),
				"TOLERANCE_INVALIDE", 400);
		codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(60, 50, semaineDeBureau())),
				"TOLERANCE_INVALIDE", 400);
		codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(-15, 60, semaineDeBureau())),
				"TOLERANCE_INVALIDE", 400);
		verify(horaireRepository, never()).save(any(PresenceHoraireSite.class));
	}

	@Test
	void toleranceAbsente_soixanteParDefaut_quatreVingtDixAcceptee() {
		service.remplacer(site.getId(), new HoraireSiteRequest(null, 90, semaineDeBureau()));

		assertThat(site.getToleranceAvantMinutes()).isEqualTo(60);
		assertThat(site.getToleranceApresMinutes()).isEqualTo(90);
	}

	@Test
	void debutEgalFin_422() {
		codeErreur(() -> service.remplacer(site.getId(),
				new HoraireSiteRequest(60, 60, List.of(jour("LUNDI", "08:00", "08:00")))), "HORAIRE_INVALIDE", 422);
	}

	@Test
	void horaireDeNuit_accepte_etMarqueCommeTel() {
		HoraireSiteResponse r = service.remplacer(site.getId(),
				new HoraireSiteRequest(60, 60, List.of(jour("LUNDI", "22:00", "06:00"))));

		assertThat(r.jours()).singleElement().satisfies(j -> assertThat(j.nuit()).isTrue());
	}

	@Test
	void nuitDuLundiPuisMardi0630_chevauchement422() {
		codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(60, 90,
				List.of(jour("LUNDI", "22:00", "06:00"), jour("MARDI", "06:30", "15:00")))), "HORAIRES_CHEVAUCHANTS", 422);
		verify(horaireRepository, never()).save(any(PresenceHoraireSite.class));
	}

	@Test
	void joursVides_400_pourRetirerLHoraireOnUtiliseLaSuppression() {
		codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(60, 60, List.of())),
				"HORAIRE_FORMAT_INVALIDE", 400);
		codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(60, 60, null)),
				"HORAIRE_FORMAT_INVALIDE", 400);
	}

	@Test
	void jourEnDouble_jourInconnu_heureIllisible_400() {
		codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(60, 60,
				List.of(jour("LUNDI", "06:00", "15:00"), jour("lundi", "07:00", "16:00")))), "HORAIRE_FORMAT_INVALIDE", 400);
		codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(60, 60,
				List.of(jour("FUNDI", "06:00", "15:00")))), "HORAIRE_FORMAT_INVALIDE", 400);
		codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(60, 60,
				List.of(jour("LUNDI", "6h", "15:00")))), "HORAIRE_FORMAT_INVALIDE", 400);
		codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(60, 60,
				List.of(jour("LUNDI", "06:00", "25:00")))), "HORAIRE_FORMAT_INVALIDE", 400);
	}

	@Test
	void siteInconnu_404() {
		codeErreur(() -> service.remplacer(UUID.randomUUID(), new HoraireSiteRequest(60, 60, semaineDeBureau())),
				"SITE_INTROUVABLE", 404);
		codeErreur(() -> service.lire(UUID.randomUUID()), "SITE_INTROUVABLE", 404);
	}

	@Test
	void remplacement_retireLesJoursAbsents_etMetAJourLesAutres() {
		service.remplacer(site.getId(), new HoraireSiteRequest(60, 60, semaineDeBureau()));
		List<PresenceHoraireSite> avant = List.copyOf(lignesEnBase);

		service.remplacer(site.getId(), new HoraireSiteRequest(60, 60,
				List.of(jour("LUNDI", "07:00", "16:00"), jour("MARDI", "06:00", "15:00"))));

		ArgumentCaptor<Iterable<PresenceHoraireSite>> supprimees = ArgumentCaptor.forClass(Iterable.class);
		verify(horaireRepository, org.mockito.Mockito.times(2)).deleteAll(supprimees.capture());
		assertThat(supprimees.getAllValues().get(1)).hasSize(3);
		assertThat(avant.get(0).getHeureDebut()).hasToString("07:00");
	}

	@Test
	void suppression_retourSansHoraire_tracee_etLesDemandesAjustees() {
		service.remplacer(site.getId(), new HoraireSiteRequest(60, 60, semaineDeBureau()));

		service.supprimer(site.getId());

		verify(horaireRepository, org.mockito.Mockito.atLeastOnce()).deleteAll(any(Iterable.class));
		verify(historiqueRepository, org.mockito.Mockito.times(2)).save(any(PresenceHoraireSiteHistorique.class));
		ArgumentCaptor<java.util.Map<java.time.DayOfWeek, FenetreHoraireService.Horaire>> nouveaux = ArgumentCaptor.forClass(java.util.Map.class);
		verify(ajustementService, org.mockito.Mockito.times(2)).appliquer(eq(site), anyMap(), eq(60), nouveaux.capture(), any(Acteur.class));
		assertThat(nouveaux.getValue()).isEmpty();
	}

	@Test
	void lecture_siteSansHoraire_tolerancesParDefaut() {
		HoraireSiteResponse r = service.lire(site.getId());

		assertThat(r.avecHoraire()).isFalse();
		assertThat(r.jours()).isEmpty();
		assertThat(r.toleranceAvantMinutes()).isEqualTo(60);
		assertThat(r.toleranceApresMinutes()).isEqualTo(60);
		assertThat(r.modifiePar()).isNull();
	}

	// --- Recette : A1 saisie identique ---

	@Test
	void saisieIdentique_memeOrdreOuNon_aucuneTraceNiAjustement_reponseInchangee() {
		HoraireSiteResponse premiere = service.remplacer(site.getId(), new HoraireSiteRequest(60, 90, semaineDeBureau()));
		List<JourHoraireRequest> inversee = new ArrayList<>(semaineDeBureau());
		java.util.Collections.reverse(inversee);
		org.mockito.Mockito.clearInvocations(historiqueRepository, ajustementService, horaireRepository, siteRepository);
		// un autre auteur ne change rien : la saisie n'est pas une modification
		lenient().when(acteurResolver.depuisJwt()).thenReturn(new Acteur(UUID.randomUUID().toString(), null, "ADM01", null));

		HoraireSiteResponse seconde = service.remplacer(site.getId(), new HoraireSiteRequest(60, 90, inversee));

		assertThat(seconde).isEqualTo(premiere);
		verify(historiqueRepository, never()).save(any(PresenceHoraireSiteHistorique.class));
		verify(ajustementService, never()).appliquer(any(), anyMap(), org.mockito.ArgumentMatchers.anyInt(), anyMap(), any());
		verify(horaireRepository, never()).save(any(PresenceHoraireSite.class));
		verify(horaireRepository, never()).deleteAll(any(Iterable.class));
		verify(siteRepository, never()).save(any(PresenceSite.class));
	}

	@Test
	void saisieIdentiqueAvecTolerancesParDefaut_siteNeuf_estUneModification_puisIdentique() {
		// tolérances nulles en base = 60 / 60 : la 1re saisie crée l'horaire, la 2e (60 / 60) est identique
		service.remplacer(site.getId(), new HoraireSiteRequest(null, null, semaineDeBureau()));
		org.mockito.Mockito.clearInvocations(historiqueRepository);

		service.remplacer(site.getId(), new HoraireSiteRequest(60, 60, semaineDeBureau()));

		verify(historiqueRepository, never()).save(any(PresenceHoraireSiteHistorique.class));
	}

	@Test
	void uneSeuleHeureOuToleranceDifferente_resteUneModificationTracee() {
		service.remplacer(site.getId(), new HoraireSiteRequest(60, 60, semaineDeBureau()));
		List<JourHoraireRequest> modifiee = new ArrayList<>(semaineDeBureau());
		modifiee.set(0, jour("LUNDI", "06:00", "15:30"));

		service.remplacer(site.getId(), new HoraireSiteRequest(60, 60, modifiee));
		service.remplacer(site.getId(), new HoraireSiteRequest(60, 75, modifiee));

		verify(historiqueRepository, org.mockito.Mockito.times(3)).save(any(PresenceHoraireSiteHistorique.class));
	}

	// --- A2 : début ou fin manquant ---

	@Test
	void debutSansFin_ouFinSansDebut_400_horaireIncomplet() {
		codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(60, 60,
				List.of(jour("LUNDI", "06:00", null)))), "HORAIRE_INCOMPLET", 400);
		codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(60, 60,
				List.of(jour("LUNDI", null, "15:00")))), "HORAIRE_INCOMPLET", 400);
		codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(60, 60,
				List.of(jour("LUNDI", "06:00", " ")))), "HORAIRE_INCOMPLET", 400);
		verify(horaireRepository, never()).save(any(PresenceHoraireSite.class));
	}

	// --- A4 : 400 avant 404 avant 422 ---

	@Test
	void ordreDesErreurs_400AvantLe404_siteInexistant() {
		UUID inconnu = UUID.randomUUID();

		codeErreur(() -> service.remplacer(inconnu, new HoraireSiteRequest(105, 60, semaineDeBureau())), "TOLERANCE_INVALIDE", 400);
		codeErreur(() -> service.remplacer(inconnu, new HoraireSiteRequest(60, 60, List.of(jour("LUNDI", "06:00", null)))),
				"HORAIRE_INCOMPLET", 400);
		codeErreur(() -> service.remplacer(inconnu, new HoraireSiteRequest(60, 60, List.of(jour("LUNDI", "6h", "15:00")))),
				"HORAIRE_FORMAT_INVALIDE", 400);
		codeErreur(() -> service.remplacer(inconnu, new HoraireSiteRequest(60, 60, semaineDeBureau())), "SITE_INTROUVABLE", 404);
	}

	@Test
	void ordreDesErreurs_le404AvantLes422_etLe400AvantLe422() {
		UUID inconnu = UUID.randomUUID();

		codeErreur(() -> service.remplacer(inconnu, new HoraireSiteRequest(60, 60,
				List.of(jour("LUNDI", "08:00", "08:00")))), "SITE_INTROUVABLE", 404);
		codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(60, 60,
				List.of(jour("LUNDI", "08:00", "08:00"), jour("MARDI", "6h", "15:00")))), "HORAIRE_FORMAT_INVALIDE", 400);
	}

	// --- A5 : heures hors 00:00-23:59 ---

	@Test
	void heure24h00_ouHorsPlage_400_formatInvalide() {
		for (String invalide : new String[] {"24:00", "24:01", "23:60", "-1:00", "7:5"}) {
			codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(60, 60,
					List.of(jour("LUNDI", "06:00", invalide)))), "HORAIRE_FORMAT_INVALIDE", 400);
			codeErreur(() -> service.remplacer(site.getId(), new HoraireSiteRequest(60, 60,
					List.of(jour("LUNDI", invalide, "15:00")))), "HORAIRE_FORMAT_INVALIDE", 400);
		}
	}

	@Test
	void bornesValides_0000Et2359_acceptees() {
		HoraireSiteResponse r = service.remplacer(site.getId(),
				new HoraireSiteRequest(0, 0, List.of(jour("LUNDI", "00:00", "23:59"))));

		assertThat(r.jours()).singleElement().satisfies(j -> {
			assertThat(j.debut()).isEqualTo("00:00");
			assertThat(j.fin()).isEqualTo("23:59");
		});
	}

	@Test
	void suppressionD_unSiteDejaSansHoraire_sortieTotSansHistoriqueNiAjustement() {
		service.supprimer(site.getId());

		verify(historiqueRepository, never()).save(any(PresenceHoraireSiteHistorique.class));
		verify(ajustementService, never()).appliquer(any(), anyMap(), org.mockito.ArgumentMatchers.anyInt(), anyMap(), any());
		verify(horaireRepository, never()).deleteAll(any(Iterable.class));
	}
}
