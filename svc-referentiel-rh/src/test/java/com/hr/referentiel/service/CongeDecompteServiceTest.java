package com.hr.referentiel.service;

import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeMouvementSolde;
import com.hr.referentiel.dto.SoldeDtos.Simulation;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.SoldeConge;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.JourFerieRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C1-03 à C1-06, C1-09, C1-14, C1-15, C1-17 : contrôles de solde, chevauchement, approbation, simulation. */
@ExtendWith(MockitoExtension.class)
class CongeDecompteServiceTest {

	private static final LocalDate LUN = LocalDate.of(2026, 12, 7);

	@Mock
	private JourFerieService jourFerieService;
	@Mock
	private SoldeCongeService soldeService;
	@Mock
	private DemandeAdministrativeRhRepository demandeRepository;
	@Mock
	private JourFerieRepository jourFerieRepository;

	private CongeDecompteService service;
	private Collaborateur demandeur;
	private SoldeConge solde;

	@BeforeEach
	void setUp() {
		service = new CongeDecompteService(jourFerieService, soldeService, demandeRepository, jourFerieRepository);
		demandeur = new Collaborateur();
		demandeur.setId(UUID.randomUUID());
		solde = new SoldeConge();
		solde.setCollaborateurId(demandeur.getId());
		lenient().when(jourFerieService.datesActives()).thenReturn(Set.of());
		lenient().when(soldeService.verrouiller(demandeur.getId())).thenReturn(solde);
		lenient().when(soldeService.reserve(demandeur.getId())).thenReturn(new BigDecimal("0.0"));
		lenient().when(soldeService.acquis(demandeur.getId())).thenAnswer(i -> solde.getAcquis());
		lenient().when(demandeRepository.findPourChevauchement(any(), any(), any())).thenReturn(List.of());
	}

	private DemandeAdministrativeRh demande(LocalDate debut, LocalDate fin, String type, String premier, String dernier) {
		DemandeAdministrativeRh d = new DemandeAdministrativeRh();
		d.setId(UUID.randomUUID());
		d.setNumero("DEM-202612-0001");
		d.setTypeDemande(TypeDemandeAdministrativeRh.CONGE);
		d.setDemandeur(demandeur);
		Map<String, Object> c = new HashMap<>();
		c.put("date_debut", debut.toString());
		c.put("date_fin", fin.toString());
		c.put("type_conge", type);
		if (premier != null) {
			c.put("periode_premier_jour", premier);
		}
		if (dernier != null) {
			c.put("periode_dernier_jour", dernier);
		}
		d.setContenu(c);
		d.setPeriodeDebut(debut);
		d.setPeriodeFin(fin);
		return d;
	}

	private static String code(Throwable t) {
		return ((ReferentielMetierException) t).getCode();
	}

	@Test
	@DisplayName("C1-01 : acquis 10, semaine pleine -> 5 jours décomptés, demande acceptée")
	void creationSemainePleine() {
		solde.setAcquis(new BigDecimal("10.0"));
		DemandeAdministrativeRh d = demande(LUN, LUN.plusDays(4), "ANNUEL", null, null);
		service.preparerCreation(demandeur, "ANNUEL", d);
		assertThat(d.getJoursDecomptes()).isEqualByComparingTo("5");
		assertThat(d.getContenu()).containsEntry("periode_premier_jour", "JOURNEE").containsEntry("periode_dernier_jour", "JOURNEE");
	}

	@Test
	@DisplayName("C1-03 : disponible 3, demande de 3 jours -> acceptée ; 3,5 jours -> 422 SOLDE_INSUFFISANT avec message chiffré")
	void soldeInsuffisantALaCreation() {
		solde.setAcquis(new BigDecimal("3.0"));
		service.preparerCreation(demandeur, "ANNUEL", demande(LUN, LUN.plusDays(2), "ANNUEL", null, null));
		DemandeAdministrativeRh trop = demande(LUN, LUN.plusDays(3), "ANNUEL", null, "MATIN");
		trop.getContenu().put("periode_dernier_jour", "JOURNEE");
		solde.setAcquis(new BigDecimal("3.0"));
		assertThatThrownBy(() -> service.preparerCreation(demandeur, "ANNUEL", trop))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(e -> {
					ReferentielMetierException m = (ReferentielMetierException) e;
					assertThat(m.getCode()).isEqualTo("SOLDE_INSUFFISANT");
					assertThat(m.getHttpStatus()).isEqualTo(422);
					assertThat(m.getDetails()).containsKeys("jours_demandes", "solde_disponible");
					assertThat(m.getMessage()).isEqualTo("Solde insuffisant : 4 jours demandés, 3 jours disponibles");
				});
		assertThat(trop.getJoursDecomptes()).isNull();
	}

	@Test
	@DisplayName("C1-03 : le réservé est déduit du disponible (acquis 5, 3 jours déjà réservés, nouvelle demande de 3 -> 422)")
	void reserveDeduit() {
		solde.setAcquis(new BigDecimal("5.0"));
		when(soldeService.reserve(demandeur.getId())).thenReturn(new BigDecimal("3.0"));
		assertThatThrownBy(() -> service.preparerCreation(demandeur, "ANNUEL",
				demande(LUN, LUN.plusDays(2), "ANNUEL", null, null))).satisfies(e -> assertThat(code(e)).isEqualTo("SOLDE_INSUFFISANT"));
	}

	@Test
	@DisplayName("C1-04 : aucune anticipation des acquisitions futures")
	void pasDAnticipation() {
		solde.setAcquis(new BigDecimal("2.0"));
		assertThatThrownBy(() -> service.preparerCreation(demandeur, "ANNUEL",
				demande(LUN, LUN.plusDays(4), "ANNUEL", null, null))).satisfies(e -> assertThat(code(e)).isEqualTo("SOLDE_INSUFFISANT"));
	}

	@Test
	@DisplayName("C1-02 : week-end seul -> 422 AUCUN_JOUR_OUVRE, solde non verrouillé")
	void aucunJourOuvre() {
		DemandeAdministrativeRh d = demande(LUN.plusDays(5), LUN.plusDays(6), "ANNUEL", null, null);
		assertThatThrownBy(() -> service.preparerCreation(demandeur, "ANNUEL", d))
				.satisfies(e -> assertThat(code(e)).isEqualTo("AUCUN_JOUR_OUVRE"));
		verify(soldeService, never()).verrouiller(any());
	}

	@Test
	@DisplayName("C1-14 : demi-journée envoyée pour un autre type que ANNUEL -> 400 CORPS_INVALIDE ; MALADIE sans contrôle de solde")
	void autreTypeSansSolde() {
		DemandeAdministrativeRh demi = demande(LUN, LUN, "MALADIE", "MATIN", null);
		assertThatThrownBy(() -> service.preparerCreation(demandeur, "MALADIE", demi))
				.satisfies(e -> assertThat(code(e)).isEqualTo("CORPS_INVALIDE"));
		DemandeAdministrativeRh maladie = demande(LUN, LUN.plusDays(4), "MALADIE", null, null);
		service.preparerCreation(demandeur, "MALADIE", maladie);
		assertThat(maladie.getJoursDecomptes()).isNull();
		verify(soldeService, never()).verrouiller(any());
	}

	@Test
	@DisplayName("C1-14 : chevauchement à l'après-midi d'un congé approuvé -> 409 ; matin du même jour -> accepté")
	void chevauchement() {
		solde.setAcquis(new BigDecimal("10.0"));
		DemandeAdministrativeRh existant = demande(LUN, LUN.plusDays(2), "ANNUEL", "JOURNEE", "JOURNEE");
		existant.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		DemandeAdministrativeRh apresMidiVendredi = demande(LUN.plusDays(4), LUN.plusDays(4), "ANNUEL", "APRES_MIDI", "APRES_MIDI");
		apresMidiVendredi.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		when(demandeRepository.findPourChevauchement(eq(demandeur.getId()), any(), any()))
				.thenReturn(List.of(existant, apresMidiVendredi));

		DemandeAdministrativeRh recouvre = demande(LUN.plusDays(2), LUN.plusDays(3), "ANNUEL", null, null);
		assertThatThrownBy(() -> service.preparerCreation(demandeur, "ANNUEL", recouvre))
				.satisfies(e -> assertThat(code(e)).isEqualTo("CONGE_CHEVAUCHEMENT"));
		DemandeAdministrativeRh matinVendredi = demande(LUN.plusDays(4), LUN.plusDays(4), "ANNUEL", "MATIN", "MATIN");
		service.preparerCreation(demandeur, "ANNUEL", matinVendredi);
		assertThat(matinVendredi.getJoursDecomptes()).isEqualByComparingTo("0.5");
		DemandeAdministrativeRh apresMidi = demande(LUN.plusDays(4), LUN.plusDays(4), "ANNUEL", "APRES_MIDI", "APRES_MIDI");
		assertThatThrownBy(() -> service.preparerCreation(demandeur, "ANNUEL", apresMidi))
				.satisfies(e -> assertThat(code(e)).isEqualTo("CONGE_CHEVAUCHEMENT"));
	}

	@Test
	@DisplayName("C1-05 : validation responsable avec jours > acquis -> 422, jours non modifiés")
	void validationRefuseeSiAcquisInsuffisant() {
		solde.setAcquis(new BigDecimal("2.0"));
		DemandeAdministrativeRh d = demande(LUN, LUN.plusDays(3), "ANNUEL", "JOURNEE", "JOURNEE");
		d.setJoursDecomptes(new BigDecimal("4.0"));
		assertThatThrownBy(() -> service.controlerValidation(d))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SOLDE_INSUFFISANT"));
		assertThat(d.getJoursDecomptes()).isEqualByComparingTo("4.0");
	}

	@Test
	@DisplayName("C1-09 : férié ajouté entre création et validation -> jours recalculés 5 -> 4 ; désactivé -> 4 -> 5 mais 422 si l'acquis ne suffit plus")
	void joursRecalcules() {
		solde.setAcquis(new BigDecimal("5.0"));
		DemandeAdministrativeRh d = demande(LUN, LUN.plusDays(4), "ANNUEL", "JOURNEE", "JOURNEE");
		d.setJoursDecomptes(new BigDecimal("5.0"));
		when(jourFerieService.datesActives()).thenReturn(Set.of(LUN.plusDays(2)));
		service.controlerValidation(d);
		assertThat(d.getJoursDecomptes()).isEqualByComparingTo("4.0");
		solde.setAcquis(new BigDecimal("4.0"));
		when(jourFerieService.datesActives()).thenReturn(Set.of());
		assertThatThrownBy(() -> service.controlerValidation(d)).satisfies(e -> assertThat(code(e)).isEqualTo("SOLDE_INSUFFISANT"));
	}

	@Test
	@DisplayName("C1-06 : approbation -> mouvement CONGE_PRIS négatif lié à la demande, jours figés")
	void approbationEcritLeMouvement() {
		solde.setAcquis(new BigDecimal("10.0"));
		DemandeAdministrativeRh d = demande(LUN, LUN.plusDays(4), "ANNUEL", "JOURNEE", "JOURNEE");
		d.setJoursDecomptes(new BigDecimal("5.0"));
		AuteurParametre auteur = new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi");
		when(soldeService.enregistrer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(new com.hr.referentiel.entity.MouvementSoldeConge());
		service.approuver(d, auteur);
		verify(soldeService).enregistrer(eq(solde), eq(TypeMouvementSolde.CONGE_PRIS), eq(new BigDecimal("-5.0")), any(),
				any(), eq(d.getId()), eq("DEM-202612-0001"), any(), any(), eq(auteur));
	}

	@Test
	@DisplayName("C1-15 : demande antérieure aux soldes (jours non décomptés) -> approbation sans contrôle ni mouvement")
	void demandeAnterieure() {
		DemandeAdministrativeRh d = demande(LUN, LUN.plusDays(4), "ANNUEL", null, null);
		assertThat(service.approuver(d, AuteurParametre.systeme())).isEmpty();
		service.controlerValidation(d);
		verify(soldeService, never()).verrouiller(any());
		verify(soldeService, never()).enregistrer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("C1-17 : simulation sans écriture, insuffisance = 200 avec blocage libellé, corps invalide = 400")
	void simulation() {
		solde.setAcquis(new BigDecimal("3.0"));
		Simulation ok = service.simuler(demandeur.getId(), LUN, LUN.plusDays(1), null, null);
		assertThat(ok.joursDecomptes()).isEqualByComparingTo("2");
		assertThat(ok.disponibleApres()).isEqualByComparingTo("1");
		assertThat(ok.blocage()).isNull();
		Simulation trop = service.simuler(demandeur.getId(), LUN, LUN.plusDays(4), null, null);
		assertThat(trop.blocage().code()).isEqualTo("SOLDE_INSUFFISANT");
		assertThat(trop.blocage().libelle()).isEqualTo("Solde insuffisant : 5 jours demandés, 3 jours disponibles");
		Simulation weekEnd = service.simuler(demandeur.getId(), LUN.plusDays(5), LUN.plusDays(6), null, null);
		assertThat(weekEnd.blocage().code()).isEqualTo("AUCUN_JOUR_OUVRE");
		assertThatThrownBy(() -> service.simuler(demandeur.getId(), LUN.plusDays(5), LUN.plusDays(5), "MATIN", null))
				.satisfies(e -> assertThat(code(e)).isEqualTo("DEMI_JOURNEE_JOUR_NON_OUVRE"));
		assertThatThrownBy(() -> service.simuler(demandeur.getId(), LUN.plusDays(3), LUN, null, null))
				.satisfies(e -> assertThat(code(e)).isEqualTo("CORPS_INVALIDE"));
		verify(soldeService, never()).verrouiller(any());
		verify(soldeService, never()).enregistrer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
	}
}
