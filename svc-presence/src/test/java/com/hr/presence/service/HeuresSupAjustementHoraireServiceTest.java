package com.hr.presence.service;

import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.entity.PresenceHeuresSupBeneficiaire;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresenceHeuresSupJour;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceHeuresSupBeneficiaireRepository;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.security.ActeurResolver.Acteur;
import com.hr.presence.service.FenetreHoraireService.Horaire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Décision client n° 2 et D-P11.c : effet d'une modification d'horaire sur les demandes d'heures sup.
 * Site 06:00-15:00 du lundi au vendredi, tolérance 60 / 60 ; demande avec 2 h d'ouverture avant.
 * Calendrier : lundi 2, mardi 3, mercredi 4, lundi 9, mardi 10, mercredi 11 novembre 2026.
 */
@ExtendWith(MockitoExtension.class)
class HeuresSupAjustementHoraireServiceTest {

	private static final LocalDate MAR_3 = LocalDate.of(2026, 11, 3);
	private static final LocalDate MER_4 = LocalDate.of(2026, 11, 4);
	private static final LocalDate MAR_10 = LocalDate.of(2026, 11, 10);
	private static final LocalDate MER_11 = LocalDate.of(2026, 11, 11);

	@Mock
	private PresenceHeuresSupDemandeRepository demandeRepository;
	@Mock
	private PresenceHeuresSupJourRepository jourRepository;
	@Mock
	private PresenceHeuresSupBeneficiaireRepository beneficiaireRepository;
	@Mock
	private HeuresSupNotifier notifier;

	private PresenceSite site;
	private PresenceHeuresSupDemande demande;
	private PresenceHeuresSupBeneficiaire beneficiaire;
	private final List<PresenceHeuresSupJour> lignes = new ArrayList<>();
	private final Acteur auteur = new Acteur(UUID.randomUUID().toString(), null, "DRH01", "Amel Trabelsi");

	@BeforeEach
	void setUp() {
		site = new PresenceSite();
		site.setId(UUID.randomUUID());
		site.setLibelle("Siège");
		demande = new PresenceHeuresSupDemande();
		demande.setId(UUID.randomUUID());
		demande.setReference("HS-0000ABCD");
		demande.setSiteId(site.getId());
		demande.setDateDebut(MAR_3);
		demande.setDateFin(MER_11);
		demande.setOuvertureAvantMinutes(120);
		demande.setStatut(StatutHeuresSup.VALIDEE);
		demande.setDebutPlageTs(instant(MAR_10, 3, 0));
		beneficiaire = new PresenceHeuresSupBeneficiaire();
		beneficiaire.setId(UUID.randomUUID());
		beneficiaire.setCollaborateurId(UUID.randomUUID());
		lenient().when(demandeRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<UUID>>any()))
				.thenAnswer(inv -> List.of(demande));
		lenient().when(demandeRepository.save(any(PresenceHeuresSupDemande.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(jourRepository.findBySiteIdAndJourneeGreaterThanEqual(eq(site.getId()), any()))
				.thenAnswer(inv -> List.copyOf(lignes));
		lenient().when(jourRepository.findByDemandeId(demande.getId())).thenAnswer(inv -> List.copyOf(lignes));
		lenient().when(beneficiaireRepository.findByDemandeId(demande.getId())).thenReturn(List.of(beneficiaire));
		lenient().doAnswer(inv -> {
			Iterable<?> retirees = inv.getArgument(0);
			retirees.forEach(lignes::remove);
			return null;
		}).when(jourRepository).deleteAll(org.mockito.ArgumentMatchers.<Iterable<PresenceHeuresSupJour>>any());
	}

	private static Instant instant(LocalDate jour, int heure, int minute) {
		return LocalDateTime.of(jour, LocalTime.of(heure, minute)).atZone(ClockConfig.ZONE_TUNIS).toInstant();
	}

	private HeuresSupAjustementHoraireService serviceA(LocalDate jour, int heure) {
		return new HeuresSupAjustementHoraireService(demandeRepository, jourRepository, beneficiaireRepository, notifier,
				Clock.fixed(instant(jour, heure, 0), ClockConfig.ZONE_TUNIS));
	}

	private void ligne(LocalDate journee) {
		PresenceHeuresSupJour l = new PresenceHeuresSupJour();
		l.setId(UUID.randomUUID());
		l.setDemandeId(demande.getId());
		l.setBeneficiaireId(beneficiaire.getId());
		l.setCollaborateurId(beneficiaire.getCollaborateurId().toString());
		l.setSiteId(site.getId());
		l.setJournee(journee);
		lignes.add(l);
	}

	private static Map<DayOfWeek, Horaire> semaine(DayOfWeek... fermes) {
		Map<DayOfWeek, Horaire> m = new EnumMap<>(DayOfWeek.class);
		for (DayOfWeek jour : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
				DayOfWeek.FRIDAY)) {
			m.put(jour, new Horaire(LocalTime.of(6, 0), LocalTime.of(15, 0)));
		}
		for (DayOfWeek ferme : fermes) {
			m.remove(ferme);
		}
		return m;
	}

	private List<LocalDate> journeesRestantes() {
		return lignes.stream().map(PresenceHeuresSupJour::getJournee).sorted().toList();
	}

	// --- Jours retirés ---

	@Test
	void jourDevenuFermeEtFenetrePasEncoreOuverte_retire_demandeValideeGardeSonStatut_leDebutDePlageEstRecalcule() {
		ligne(MAR_10);
		ligne(MER_11);

		// lundi 9 novembre 09:00 : on ferme le mardi
		serviceA(LocalDate.of(2026, 11, 9), 9).appliquer(site, semaine(), 60, semaine(DayOfWeek.TUESDAY), auteur);

		assertThat(journeesRestantes()).containsExactly(MER_11);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.VALIDEE);
		// premier jour restant : mercredi 11, 06:00 - 2 h - 60 min = 03:00
		assertThat(demande.getDebutPlageTs()).isEqualTo(instant(MER_11, 3, 0));
		verify(notifier).joursRetires(eq(demande), anyString(), eq("10/11/2026"), eq(false), eq(true), any());
	}

	@Test
	void demandeEnAttente_notifieLeDemandeurSeulement_lesBeneficiairesNeSontPasPrevenus() {
		demande.setStatut(StatutHeuresSup.EN_ATTENTE);
		ligne(MAR_10);
		ligne(MER_11);

		serviceA(LocalDate.of(2026, 11, 9), 9).appliquer(site, semaine(), 60, semaine(DayOfWeek.TUESDAY), auteur);

		// le notifieur n'écrit aux bénéficiaires que si etaitValidee est vrai
		verify(notifier).joursRetires(eq(demande), anyString(), anyString(), eq(false), eq(false), any());
	}

	@Test
	void plusAucunJour_demandeAnnulee_avecMotifEtAuteur_demandeurEtBeneficiairesNotifies() {
		ligne(MAR_10);
		ligne(MER_11);

		// DELETE de l'horaire (site sans horaire) avant l'ouverture des fenêtres
		serviceA(LocalDate.of(2026, 11, 9), 9).appliquer(site, semaine(), 60, new EnumMap<>(DayOfWeek.class), auteur);

		assertThat(lignes).isEmpty();
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.ANNULEE);
		assertThat(demande.getMotifAnnulation()).isEqualTo("Horaire du site modifié");
		assertThat(demande.getAnnuleParNom()).isEqualTo("Amel Trabelsi");
		assertThat(demande.getAnnuleParCompteId()).isEqualTo(auteur.compteId());
		assertThat(demande.getAnnuleLe()).isEqualTo(instant(LocalDate.of(2026, 11, 9), 9, 0));
		verify(notifier).joursRetires(eq(demande), anyString(), eq("10/11/2026, 11/11/2026"), eq(true), eq(true), any());
	}

	@Test
	void annulationTotaleDUneDemandeEnAttente_leDemandeurSeulEstPrevenu() {
		demande.setStatut(StatutHeuresSup.EN_ATTENTE);
		ligne(MAR_10);

		serviceA(LocalDate.of(2026, 11, 9), 9).appliquer(site, semaine(), 60, semaine(DayOfWeek.TUESDAY), auteur);

		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.ANNULEE);
		verify(notifier).joursRetires(eq(demande), anyString(), anyString(), eq(true), eq(false), any());
	}

	// --- Journées déjà ouvertes (D-P11.c) ---

	@Test
	void journeeDontLaFenetreADejaOuvert_conservee_heuresDejaComptees() {
		ligne(MAR_10);

		// mardi 10 novembre 09:00 : la fenêtre (03:00) est ouverte ; on ferme le mardi
		serviceA(MAR_10, 9).appliquer(site, semaine(), 60, semaine(DayOfWeek.TUESDAY), auteur);

		assertThat(journeesRestantes()).containsExactly(MAR_10);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.VALIDEE);
		verify(jourRepository, never()).deleteAll(org.mockito.ArgumentMatchers.<Iterable<PresenceHeuresSupJour>>any());
		verifyNoInteractions(notifier);
	}

	@Test
	void changementDeSeulesHeures_demandeIntacte_lAnneauDeLaFenetreSuitLeNouvelHoraire() {
		ligne(MER_11);
		demande.setDebutPlageTs(instant(MER_11, 3, 0));
		Map<DayOfWeek, Horaire> nouveaux = semaine();
		nouveaux.put(DayOfWeek.WEDNESDAY, new Horaire(LocalTime.of(7, 0), LocalTime.of(16, 0)));

		serviceA(LocalDate.of(2026, 11, 9), 9).appliquer(site, semaine(), 60, nouveaux, auteur);

		assertThat(journeesRestantes()).containsExactly(MER_11);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.VALIDEE);
		// 07:00 - 2 h - 60 min = 04:00
		assertThat(demande.getDebutPlageTs()).isEqualTo(instant(MER_11, 4, 0));
		verifyNoInteractions(notifier);
	}

	@Test
	void aucuneLigneProche_rienNEstFait() {
		serviceA(LocalDate.of(2026, 11, 9), 9).appliquer(site, semaine(), 60, semaine(DayOfWeek.TUESDAY), auteur);

		verifyNoInteractions(notifier);
		verify(demandeRepository, never()).save(any(PresenceHeuresSupDemande.class));
	}

	@Test
	void demandeDejaDecidee_ignoree() {
		demande.setStatut(StatutHeuresSup.REFUSEE);
		ligne(MAR_10);

		serviceA(LocalDate.of(2026, 11, 9), 9).appliquer(site, semaine(), 60, semaine(DayOfWeek.TUESDAY), auteur);

		assertThat(lignes).hasSize(1);
		verifyNoInteractions(notifier);
	}

	// --- Deux modifications successives (revue : journées passées jamais perdues) ---

	@Test
	void deuxModificationsSuccessives_laJourneePasseeFermeeLaPremiereFois_n_estJamaisPerdue() {
		ligne(MAR_3);
		ligne(MER_4);
		ligne(MAR_10);
		ligne(MER_11);

		// 1) lundi 9 novembre : le mardi est fermé. Le mardi 3 (passé) est conservé, le mardi 10 (futur) est retiré.
		Map<DayOfWeek, Horaire> sansMardi = semaine(DayOfWeek.TUESDAY);
		serviceA(LocalDate.of(2026, 11, 9), 9).appliquer(site, semaine(), 60, sansMardi, auteur);
		assertThat(journeesRestantes()).containsExactly(MAR_3, MER_4, MER_11);
		org.mockito.Mockito.clearInvocations(notifier);

		// 2) mercredi 11 novembre : changement de tolérance (le mardi n'existe plus dans l'ancien horaire)
		serviceA(MER_11, 9).appliquer(site, sansMardi, 60, sansMardi, auteur);

		assertThat(journeesRestantes()).containsExactly(MAR_3, MER_4, MER_11);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.VALIDEE);
		verifyNoInteractions(notifier);
	}

	@Test
	void suppressionDeLHoraireApresUneFermetureAnterieure_neRetireRienDePasseNiDOuvert() {
		ligne(MAR_3);
		ligne(MER_4);
		ligne(MER_11);
		Map<DayOfWeek, Horaire> sansMardi = semaine(DayOfWeek.TUESDAY);

		// mercredi 11 novembre 09:00 : DELETE de l'horaire ; mardi 3 : pas d'ancien horaire, mercredi 4 et 11 : fenêtres ouvertes
		serviceA(MER_11, 9).appliquer(site, sansMardi, 60, new EnumMap<>(DayOfWeek.class), auteur);

		assertThat(journeesRestantes()).containsExactly(MAR_3, MER_4, MER_11);
		assertThat(demande.getStatut()).isEqualTo(StatutHeuresSup.VALIDEE);
		verifyNoInteractions(notifier);
	}

	@Test
	void debutDePlage_calculeSeulementSurLesLignesDontLHoraireEstConnu_pasDeNpe() {
		ligne(MAR_3);
		ligne(MER_11);
		Map<DayOfWeek, Horaire> sansMardi = semaine(DayOfWeek.TUESDAY);
		demande.setDebutPlageTs(instant(MER_11, 1, 0));

		// ancien horaire sans mardi : la ligne du mardi 3 n'a aucun horaire connu, elle est ignorée du calcul
		serviceA(LocalDate.of(2026, 11, 9), 9).appliquer(site, sansMardi, 60, sansMardi, auteur);

		assertThat(demande.getDebutPlageTs()).isEqualTo(instant(MER_11, 3, 0));
		assertThat(journeesRestantes()).containsExactly(MAR_3, MER_11);
	}

	@Test
	void sansAucunHoraireConnu_leDebutDePlageExistantEstConserve() {
		ligne(MAR_3);
		Instant avant = demande.getDebutPlageTs();

		serviceA(MER_11, 9).appliquer(site, semaine(DayOfWeek.TUESDAY), 60, semaine(DayOfWeek.TUESDAY), auteur);

		assertThat(demande.getDebutPlageTs()).isEqualTo(avant);
		ArgumentCaptor<PresenceHeuresSupDemande> sauvee = ArgumentCaptor.forClass(PresenceHeuresSupDemande.class);
		verify(demandeRepository, org.mockito.Mockito.atMost(1)).save(sauvee.capture());
	}

	// --- Un debut de plage deja passe n'est jamais repousse ---

	@Test
	void changementDHeuresDuJourMemeApresOuvertureDeLaFenetre_debutDePlageInchange() {
		ligne(MAR_10);
		demande.setDebutPlageTs(instant(MAR_10, 3, 0));
		Map<DayOfWeek, Horaire> nouveaux = semaine();
		nouveaux.put(DayOfWeek.TUESDAY, new Horaire(LocalTime.of(14, 0), LocalTime.of(22, 0)));

		// mardi 10 novembre 10:00 : la fenetre ouverte a 03:00, la RH passe le jour en 14:00-22:00 (aurait donne 11:00)
		serviceA(MAR_10, 10).appliquer(site, semaine(), 60, nouveaux, auteur);

		assertThat(demande.getDebutPlageTs()).isEqualTo(instant(MAR_10, 3, 0));
		verify(demandeRepository, never()).save(any(PresenceHeuresSupDemande.class));
		verifyNoInteractions(notifier);
	}

	@Test
	void demandeEnAttenteEchue_resteEchue_apresUnChangementDHeures() {
		demande.setStatut(StatutHeuresSup.EN_ATTENTE);
		ligne(MAR_10);
		demande.setDebutPlageTs(instant(MAR_10, 3, 0));
		Map<DayOfWeek, Horaire> nouveaux = semaine();
		nouveaux.put(DayOfWeek.TUESDAY, new Horaire(LocalTime.of(14, 0), LocalTime.of(22, 0)));

		serviceA(MAR_10, 10).appliquer(site, semaine(), 60, nouveaux, auteur);

		// debut <= maintenant : l'expiration paresseuse et l'annulation RH (409) s'appuient toujours dessus
		assertThat(demande.getDebutPlageTs()).isBeforeOrEqualTo(instant(MAR_10, 10, 0));
	}

	@Test
	void debutDePlageFutur_resteRecalculePourSuivreLeNouvelHoraire() {
		ligne(MAR_10);
		demande.setDebutPlageTs(instant(MAR_10, 3, 0));
		Map<DayOfWeek, Horaire> nouveaux = semaine();
		nouveaux.put(DayOfWeek.TUESDAY, new Horaire(LocalTime.of(14, 0), LocalTime.of(22, 0)));

		// lundi 9 novembre 09:00 : le debut n'est pas passe, il suit le nouvel horaire (14:00 - 2 h - 60 min = 11:00)
		serviceA(LocalDate.of(2026, 11, 9), 9).appliquer(site, semaine(), 60, nouveaux, auteur);

		assertThat(demande.getDebutPlageTs()).isEqualTo(instant(MAR_10, 11, 0));
	}
}
