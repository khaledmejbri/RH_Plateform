package com.hr.referentiel.service;

import com.hr.referentiel.domain.BaseDecompteConge;
import com.hr.referentiel.domain.PeriodeJour;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.SoldeConge;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.JourFerieRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests du décompte de congé selon le paramétrage société (CA-04.3, D-SOC10, D-SOC11).
 */
class CongeDecompteSocieteTest {

	@Test
	@DisplayName("CA-04.3 & D-SOC10 : vendredi-samedi compte 1 jour en JOURS_TRAVAILLES (LUN..VEN) et 2 jours en JOURS_OUVRABLES")
	void decompteVendrediSamediSelonBase() {
		// Vendredi 20/11/2026 au Samedi 21/11/2026
		LocalDate vendredi = LocalDate.of(2026, 11, 20);
		LocalDate samedi = LocalDate.of(2026, 11, 21);
		var periodes = DecompteJoursOuvres.normaliser(vendredi, samedi, PeriodeJour.JOURNEE, PeriodeJour.JOURNEE);
		Set<DayOfWeek> lunVen = EnumSet.of(
				DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

		BigDecimal joursTravailles = DecompteJoursOuvres.jours(
				periodes, Set.of(), BaseDecompteConge.JOURS_TRAVAILLES, lunVen);
		BigDecimal joursOuvrables = DecompteJoursOuvres.jours(
				periodes, Set.of(), BaseDecompteConge.JOURS_OUVRABLES, lunVen);

		assertThat(joursTravailles).isEqualByComparingTo("1.0");
		assertThat(joursOuvrables).isEqualByComparingTo("2.0");
	}

	@Test
	@DisplayName("D-SOC10 : jours travaillés personnalisés (DIM..JEU) excluent vendredi et samedi mais comptent dimanche")
	void decompteJoursTravaillesPersonnalises() {
		// Vendredi 20/11/2026 au Dimanche 22/11/2026
		LocalDate vendredi = LocalDate.of(2026, 11, 20);
		LocalDate dimanche = LocalDate.of(2026, 11, 22);
		var periodes = DecompteJoursOuvres.normaliser(vendredi, dimanche, PeriodeJour.JOURNEE, PeriodeJour.JOURNEE);
		Set<DayOfWeek> dimJeu = EnumSet.of(
				DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);

		BigDecimal jours = DecompteJoursOuvres.jours(
				periodes, Set.of(), BaseDecompteConge.JOURS_TRAVAILLES, dimJeu);
		assertThat(jours).isEqualByComparingTo("1.0");
	}

	@Test
	@DisplayName("D-SOC11 : le changement de base_decompte_conge s'applique aux nouvelles demandes sans modifier les demandes déjà approuvées")
	void changementBaseNeModifiePasDemandesApprouvees() {
		JourFerieService jourFerieService = mock(JourFerieService.class);
		when(jourFerieService.datesActives()).thenReturn(Set.of());

		SoldeCongeService soldeService = mock(SoldeCongeService.class);
		SoldeConge solde = new SoldeConge();
		solde.setCollaborateurId(UUID.randomUUID());
		solde.setAcquis(new BigDecimal("20.0"));
		when(soldeService.verrouiller(any())).thenReturn(solde);
		when(soldeService.reserve(any())).thenReturn(BigDecimal.ZERO);
		when(soldeService.enregistrer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(new com.hr.referentiel.entity.MouvementSoldeConge());

		DemandeAdministrativeRhRepository demandeRepo = mock(DemandeAdministrativeRhRepository.class);
		when(demandeRepo.findByDemandeurIdAndTypeDemandeOrderByCreeLeDesc(any(), any())).thenReturn(List.of());

		SocieteParametresService paramsService = mock(SocieteParametresService.class);
		when(paramsService.joursTravaillesCourants()).thenReturn(EnumSet.of(
				DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));

		CongeDecompteService decompteService = new CongeDecompteService(
				jourFerieService, soldeService, demandeRepo, mock(JourFerieRepository.class), paramsService);

		Collaborateur demandeur = new Collaborateur();
		demandeur.setId(UUID.randomUUID());

		// 1. Demande créée et approuvée sous JOURS_TRAVAILLES (Ven 20/11 -> Sam 21/11 = 1.0 jour)
		when(paramsService.baseDecompteCongeCourante()).thenReturn(BaseDecompteConge.JOURS_TRAVAILLES);
		DemandeAdministrativeRh approuvee = new DemandeAdministrativeRh();
		approuvee.setDemandeur(demandeur);
		approuvee.setTypeDemande(TypeDemandeAdministrativeRh.CONGE);
		approuvee.setContenu(new HashMap<>(Map.of(
				"type_conge", "ANNUEL",
				"date_debut", "2026-11-20",
				"date_fin", "2026-11-21")));
		decompteService.preparerCreation(demandeur, "ANNUEL", approuvee);
		decompteService.approuver(approuvee, AuteurParametre.systeme());
		approuvee.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		assertThat(approuvee.getJoursDecomptes()).isEqualByComparingTo("1.0");

		// 2. La société passe en JOURS_OUVRABLES : une nouvelle demande sur Ven-Sam compte 2.0 jours, l'ancienne reste à 1.0
		when(paramsService.baseDecompteCongeCourante()).thenReturn(BaseDecompteConge.JOURS_OUVRABLES);
		DemandeAdministrativeRh nouvelle = new DemandeAdministrativeRh();
		nouvelle.setDemandeur(demandeur);
		nouvelle.setTypeDemande(TypeDemandeAdministrativeRh.CONGE);
		nouvelle.setContenu(new HashMap<>(Map.of(
				"type_conge", "ANNUEL",
				"date_debut", "2026-11-27",
				"date_fin", "2026-11-28")));
		decompteService.preparerCreation(demandeur, "ANNUEL", nouvelle);

		assertThat(nouvelle.getJoursDecomptes()).isEqualByComparingTo("2.0");
		assertThat(approuvee.getJoursDecomptes()).isEqualByComparingTo("1.0");
	}
}
