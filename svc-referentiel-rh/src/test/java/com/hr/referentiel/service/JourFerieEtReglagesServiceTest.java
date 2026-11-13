package com.hr.referentiel.service;

import com.hr.referentiel.domain.ActionJournalParametre;
import com.hr.referentiel.dto.JourFerieDtos.JourFerieRequest;
import com.hr.referentiel.dto.JourFerieDtos.JourFerieResponse;
import com.hr.referentiel.dto.ParametreDto.Reglage;
import com.hr.referentiel.entity.JourFerie;
import com.hr.referentiel.entity.ParametresConges;
import com.hr.referentiel.repository.AcquisitionMoisRepository;
import com.hr.referentiel.repository.JourFerieRepository;
import com.hr.referentiel.repository.ReglagesCongesRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T8 (C8-02 à C8-05) : jours fériés (date unique, libellé, année, version, journal) et réglage « Jours acquis par mois ». */
class JourFerieEtReglagesServiceTest {

	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneOffset.UTC);
	private static final AuteurParametre DRH = new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi");

	private JourFerieRepository repository;
	private JournalParametres journal;
	private JourFerieService service;

	@BeforeEach
	void setUp() {
		repository = mock(JourFerieRepository.class);
		journal = mock(JournalParametres.class);
		service = new JourFerieService(repository, journal, HORLOGE, 30);
		when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
		when(repository.findByDateJour(any())).thenReturn(Optional.empty());
	}

	private static String code(Throwable t) {
		return ((ReferentielMetierException) t).getCode();
	}

	private static JourFerie existant(LocalDate date, boolean actif) {
		JourFerie j = new JourFerie();
		j.setDateJour(date);
		j.setLibelle("Existant");
		j.setActif(actif);
		return j;
	}

	@Test
	@DisplayName("critère 2 / C8-03 : création -> actif, journalisée (auteur, après) ; date déjà existante, même inactive -> 409 JOUR_FERIE_EN_DOUBLE")
	void creation() {
		JourFerieResponse r = service.creer(new JourFerieRequest(LocalDate.of(2027, 3, 20), " Aïd el-Fitr ", null), DRH);
		assertThat(r.libelle()).isEqualTo("Aïd el-Fitr");
		assertThat(r.actif()).isTrue();
		verify(journal).tracer(eq(JournalParametres.REF_JOUR_FERIE), eq("2027-03-20"), eq(ActionJournalParametre.CREATION), any(),
				any(), eq(DRH));
		when(repository.findByDateJour(LocalDate.of(2027, 3, 20))).thenReturn(Optional.of(existant(LocalDate.of(2027, 3, 20), false)));
		assertThatThrownBy(() -> service.creer(new JourFerieRequest(LocalDate.of(2027, 3, 20), "Autre", null), DRH))
				.satisfies(e -> {
					assertThat(code(e)).isEqualTo("JOUR_FERIE_EN_DOUBLE");
					assertThat(((ReferentielMetierException) e).getHttpStatus()).isEqualTo(409);
					assertThat(e.getMessage()).isEqualTo("Ce jour férié existe déjà.");
				});
	}

	@Test
	@DisplayName("C8-04 : libellé 1 / 81 caractères -> 400 CORPS_INVALIDE, 2 / 80 OK ; date absente -> 400 ; année hors N-1 à N+5 -> 400 DATE_INVALIDE ; libellé en double accepté")
	void validations() {
		for (String libelle : new String[] {"a", "x".repeat(81), " ", null}) {
			assertThatThrownBy(() -> service.creer(new JourFerieRequest(LocalDate.of(2027, 3, 20), libelle, null), DRH))
					.satisfies(e -> assertThat(code(e)).isEqualTo("CORPS_INVALIDE"));
		}
		service.creer(new JourFerieRequest(LocalDate.of(2027, 3, 21), "ab", null), DRH);
		service.creer(new JourFerieRequest(LocalDate.of(2027, 3, 22), "x".repeat(80), null), DRH);
		assertThatThrownBy(() -> service.creer(new JourFerieRequest(null, "Test", null), DRH))
				.satisfies(e -> assertThat(code(e)).isEqualTo("CORPS_INVALIDE"));
		assertThatThrownBy(() -> service.creer(new JourFerieRequest(LocalDate.of(2032, 1, 1), "Test", null), DRH))
				.satisfies(e -> assertThat(code(e)).isEqualTo("DATE_INVALIDE"));
		assertThatThrownBy(() -> service.creer(new JourFerieRequest(LocalDate.of(2024, 12, 31), "Test", null), DRH))
				.satisfies(e -> assertThat(code(e)).isEqualTo("DATE_INVALIDE"));
		service.creer(new JourFerieRequest(LocalDate.of(2031, 12, 31), "Limite haute", null), DRH);
		service.creer(new JourFerieRequest(LocalDate.of(2025, 1, 1), "Limite basse", null), DRH);
		// un même libellé sur trois jours consécutifs est accepté
		for (int j = 1; j <= 3; j++) {
			service.creer(new JourFerieRequest(LocalDate.of(2027, 4, j), "Aïd", null), DRH);
		}
	}

	@Test
	@DisplayName("C8-03 : version périmée -> 409 PARAMETRE_MODIFIE_ENTRE_TEMPS ; version absente -> 400 ; modification vers une date existante -> 409 ; désactivation / réactivation / suppression journalisées")
	void versionEtEtats() {
		JourFerie j = existant(LocalDate.of(2027, 5, 1), true);
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.of(j));
		assertThatThrownBy(() -> service.modifier(id, new JourFerieRequest(null, "Nouveau", 5L), DRH))
				.satisfies(e -> assertThat(code(e)).isEqualTo("PARAMETRE_MODIFIE_ENTRE_TEMPS"));
		assertThatThrownBy(() -> service.modifier(id, new JourFerieRequest(null, "Nouveau", null), DRH))
				.satisfies(e -> assertThat(code(e)).isEqualTo("CORPS_INVALIDE"));
		when(repository.findByDateJour(LocalDate.of(2027, 7, 25))).thenReturn(Optional.of(existant(LocalDate.of(2027, 7, 25), false)));
		assertThatThrownBy(() -> service.modifier(id, new JourFerieRequest(LocalDate.of(2027, 7, 25), null, 0L), DRH))
				.satisfies(e -> assertThat(code(e)).isEqualTo("JOUR_FERIE_EN_DOUBLE"));
		assertThat(service.modifier(id, new JourFerieRequest(null, "Fête du Travail", 0L), DRH).libelle()).isEqualTo("Fête du Travail");
		assertThat(service.desactiver(id, 0L, DRH).actif()).isFalse();
		assertThat(service.reactiver(id, 0L, DRH).actif()).isTrue();
		service.supprimer(id, 0L, DRH);
		verify(repository).delete(j);
		verify(journal).tracer(any(), any(), eq(ActionJournalParametre.MODIFICATION), any(), any(), any());
		verify(journal).tracer(any(), any(), eq(ActionJournalParametre.DESACTIVATION), any(), any(), any());
		verify(journal).tracer(any(), any(), eq(ActionJournalParametre.REACTIVATION), any(), any(), any());
		verify(journal).tracer(any(), any(), eq(ActionJournalParametre.SUPPRESSION), any(), eq(null), any());
		UUID inconnu = UUID.randomUUID();
		when(repository.findById(inconnu)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.desactiver(inconnu, 0L, DRH)).satisfies(e -> assertThat(code(e)).isEqualTo("JOUR_FERIE_INTROUVABLE"));
	}

	@Test
	@DisplayName("critère 4 / C8-06 : les dates actives sont mises en cache (TTL) puis relues après une écriture ; un férié désactivé n'est plus retenu")
	void cacheDesDatesActives() {
		when(repository.datesActives()).thenReturn(List.of(LocalDate.of(2028, 3, 20)));
		assertThat(service.datesActives()).containsExactly(LocalDate.of(2028, 3, 20));
		assertThat(service.datesActives()).hasSize(1);
		verify(repository, times(1)).datesActives();
		service.creer(new JourFerieRequest(LocalDate.of(2028, 5, 1), "Fête du Travail", null), DRH);
		when(repository.datesActives()).thenReturn(List.of());
		assertThat(service.datesActives()).isEmpty();
		verify(repository, times(2)).datesActives();
	}

	@Test
	@DisplayName("C8-08 : aucun 409 PARAMETRE_UTILISE à la suppression (suppression libre)")
	void suppressionLibre() {
		JourFerie j = existant(LocalDate.of(2027, 5, 1), true);
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.of(j));
		service.supprimer(id, 0L, DRH);
		verify(repository, never()).findAll();
	}

	// ─── Réglage ───────────────────────────────────────────────────────────────

	private ReglagesCongesRepository reglagesRepository;
	private ReglagesCongeService reglages;
	private ParametresConges ligne;

	private void prepareReglage() {
		reglagesRepository = mock(ReglagesCongesRepository.class);
		ligne = new ParametresConges();
		ligne.setJoursAcquisParMois(new BigDecimal("1.5"));
		when(reglagesRepository.findById(ParametresConges.ID_UNIQUE)).thenReturn(Optional.of(ligne));
		when(reglagesRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
		reglages = new ReglagesCongeService(reglagesRepository, mock(AcquisitionMoisRepository.class), journal, HORLOGE);
	}

	@Test
	@DisplayName("critère 3 / C8-05 : 0, 0,5, 2, 5 acceptés ; −0,5, 1,2, 5,5, 6 -> 400 REGLAGE_HORS_BORNES ; journalisé (avant -> après)")
	void reglageBornes() {
		prepareReglage();
		for (String ok : new String[] {"0", "0.5", "2", "5"}) {
			Reglage r = reglages.modifier(ReglagesCongeService.CLE_JOURS_PAR_MOIS, new BigDecimal(ok), 0L, DRH);
			assertThat(r.valeur()).isEqualByComparingTo(ok);
			assertThat(r.pas()).isEqualByComparingTo("0.5");
			assertThat(r.max()).isEqualByComparingTo("5");
		}
		for (String ko : new String[] {"-0.5", "1.2", "5.5", "6"}) {
			assertThatThrownBy(() -> reglages.modifier(ReglagesCongeService.CLE_JOURS_PAR_MOIS, new BigDecimal(ko), 0L, DRH))
					.satisfies(e -> assertThat(code(e)).isEqualTo("REGLAGE_HORS_BORNES"));
		}
		assertThatThrownBy(() -> reglages.modifier(ReglagesCongeService.CLE_JOURS_PAR_MOIS, null, 0L, DRH))
				.satisfies(e -> assertThat(code(e)).isEqualTo("REGLAGE_HORS_BORNES"));
		verify(journal, times(4)).tracer(eq(JournalParametres.REF_REGLAGE), eq("jours_acquis_par_mois"),
				eq(ActionJournalParametre.REGLAGE), any(Map.class), any(Map.class), eq(DRH));
	}

	@Test
	@DisplayName("C8-03 / critère 1 : réglage inconnu -> 404 REGLAGE_INCONNU ; version périmée -> 409 ; valeur par défaut 1,5 jamais vide")
	void reglageErreurs() {
		prepareReglage();
		assertThatThrownBy(() -> reglages.modifier("autre", BigDecimal.ONE, 0L, DRH))
				.satisfies(e -> assertThat(((ReferentielMetierException) e).getHttpStatus()).isEqualTo(404));
		assertThatThrownBy(() -> reglages.modifier(ReglagesCongeService.CLE_JOURS_PAR_MOIS, BigDecimal.ONE, 3L, DRH))
				.satisfies(e -> assertThat(code(e)).isEqualTo("PARAMETRE_MODIFIE_ENTRE_TEMPS"));
		assertThat(reglages.reglages().get(0).valeur()).isEqualByComparingTo("1.5");
		when(reglagesRepository.findById(ParametresConges.ID_UNIQUE)).thenReturn(Optional.empty());
		assertThat(reglages.reglages().get(0).valeur()).isEqualByComparingTo("1.5");
		assertThat(reglages.joursParMois()).isEqualByComparingTo("1.5");
		assertThat(ReglagesCongeService.debutParDefaut(LocalDate.of(2026, 11, 12))).isEqualTo(LocalDate.of(2026, 12, 1));
		assertThat(ReglagesCongeService.debutParDefaut(LocalDate.of(2026, 12, 31))).isEqualTo(LocalDate.of(2027, 1, 1));
	}
}
