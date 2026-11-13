package com.hr.referentiel.service;

import com.hr.referentiel.domain.ActionWorkflowAdministratif;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeMouvementSolde;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdminWorkflowHistory;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.SoldeConge;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdminWorkflowHistoryRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** C1-10 à C1-13, critères 12 à 17 : annulation par la RH d'un congé approuvé. */
@ExtendWith(MockitoExtension.class)
class AnnulationCongeServiceTest {

	@Mock
	private DemandeAdministrativeRhRepository demandeRepository;
	@Mock
	private DemandeAdminWorkflowHistoryRepository historiqueRepository;
	@Mock
	private CollaborateurConnecteService connecte;
	@Mock
	private CollaborateurRepository collaborateurRepository;
	@Mock
	private AuteurParametreResolver auteurResolver;
	@Mock
	private SoldeCongeService soldeService;
	@Mock
	private RhNotificationPublisher publisher;

	private AnnulationCongeService service;
	private Collaborateur colWeb;
	private Collaborateur csWeb;
	private Collaborateur drh;
	private DemandeAdministrativeRh demande;
	private SoldeConge solde;
	private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("DRH01")
			.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

	@BeforeEach
	void setUp() {
		// 12/12/2026 10:00 UTC = 11:00 à Tunis
		Clock horloge = Clock.fixed(Instant.parse("2026-12-12T10:00:00Z"), ZoneOffset.UTC);
		service = new AnnulationCongeService(demandeRepository, historiqueRepository, connecte, collaborateurRepository,
				auteurResolver, soldeService, publisher, horloge);
		colWeb = fiche("COL-WEB", "Sarra", "Khemiri");
		csWeb = fiche("CS-WEB", "Omar", "Belhadj");
		drh = fiche("DRH01", "Amel", "Trabelsi");
		demande = new DemandeAdministrativeRh();
		demande.setId(UUID.randomUUID());
		demande.setNumero("DEM-202612-0001");
		demande.setTypeDemande(TypeDemandeAdministrativeRh.CONGE);
		demande.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		demande.setDemandeur(colWeb);
		demande.setValideurAttendu(csWeb);
		demande.setContenu(new HashMap<>(java.util.Map.of("type_conge", "ANNUEL")));
		demande.setPeriodeDebut(LocalDate.of(2026, 12, 14));
		demande.setPeriodeFin(LocalDate.of(2026, 12, 18));
		demande.setJoursDecomptes(new BigDecimal("5.0"));
		solde = new SoldeConge();
		solde.setCollaborateurId(colWeb.getId());
		lenient().when(demandeRepository.findById(demande.getId())).thenReturn(Optional.of(demande));
		lenient().when(demandeRepository.findByIdForUpdate(demande.getId())).thenReturn(Optional.of(demande));
		lenient().when(connecte.resoudre(jwt)).thenReturn(Optional.of(drh));
		lenient().when(auteurResolver.depuis(jwt)).thenReturn(new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi"));
		lenient().when(soldeService.verrouiller(colWeb.getId())).thenReturn(solde);
		lenient().when(collaborateurRepository.findDetailById(colWeb.getId())).thenReturn(Optional.of(colWeb));
	}

	private static Collaborateur fiche(String matricule, String prenom, String nom) {
		Collaborateur c = new Collaborateur();
		c.setId(UUID.randomUUID());
		c.setMatricule(matricule);
		c.setPrenom(prenom);
		c.setNom(nom);
		return c;
	}

	private static String code(Throwable t) {
		return ((ReferentielMetierException) t).getCode();
	}

	@Test
	@DisplayName("critère 12 : RH annule avec motif -> ANNULEE, recrédit +5 lié à la demande, historique, COL-WEB et CS-WEB notifiés")
	void annulationRecredite() {
		DemandeAdministrativeRh r = service.annuler(demande.getId(), "  Besoin de service  ", jwt);
		assertThat(r.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.ANNULEE);
		assertThat(r.getMotifAnnulation()).isEqualTo("Besoin de service");
		assertThat(r.getAnnuleParNom()).isEqualTo("Amel Trabelsi");
		assertThat(r.getAnnuleApresApprobationLe()).isEqualTo(Instant.parse("2026-12-12T10:00:00Z"));
		verify(soldeService).enregistrer(eq(solde), eq(TypeMouvementSolde.RECREDIT_ANNULATION), eq(new BigDecimal("5.0")),
				any(), eq("Besoin de service"), eq(demande.getId()), eq("DEM-202612-0001"), any(), any(), any());
		ArgumentCaptor<DemandeAdminWorkflowHistory> h = ArgumentCaptor.forClass(DemandeAdminWorkflowHistory.class);
		verify(historiqueRepository).save(h.capture());
		assertThat(h.getValue().getAction()).isEqualTo(ActionWorkflowAdministratif.ANNULATION_APRES_APPROBATION);
		assertThat(h.getValue().getCommentaire()).contains("Amel Trabelsi").contains("Besoin de service");
		verify(publisher).notifierCongeAnnuleApresApprobation(eq(colWeb), eq(csWeb), eq(drh.getId()), eq("Amel Trabelsi"),
				eq(LocalDate.of(2026, 12, 14)), eq(LocalDate.of(2026, 12, 18)), eq("Besoin de service"), eq(demande.getId()),
				eq("DEM-202612-0001"));
	}

	@Test
	@DisplayName("critère 13 / C1-10 : début aujourd'hui (jour de Tunis) -> 409 CONGE_DEJA_COMMENCE, aucun mouvement ; 23:30 UTC = lendemain à Tunis")
	void congeDejaCommence() {
		demande.setPeriodeDebut(LocalDate.of(2026, 12, 12));
		assertThatThrownBy(() -> service.annuler(demande.getId(), "Motif ok", jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("CONGE_DEJA_COMMENCE"));
		verify(soldeService, never()).enregistrer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

		Clock tard = Clock.fixed(Instant.parse("2026-12-12T23:30:00Z"), ZoneOffset.UTC); // 00:30 le 13/12 à Tunis
		AnnulationCongeService apresMinuit = new AnnulationCongeService(demandeRepository, historiqueRepository, connecte,
				collaborateurRepository, auteurResolver, soldeService, publisher, tard);
		demande.setPeriodeDebut(LocalDate.of(2026, 12, 13));
		assertThatThrownBy(() -> apresMinuit.annuler(demande.getId(), "Motif ok", jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("CONGE_DEJA_COMMENCE"));
		demande.setPeriodeDebut(LocalDate.of(2026, 12, 14));
		assertThat(apresMinuit.annuler(demande.getId(), "Motif ok", jwt).getStatut())
				.isEqualTo(StatutDemandeAdministrativeRh.ANNULEE);
	}

	@Test
	@DisplayName("critère 14 / C1-11 : motif absent, de 4 ou 501 caractères, ou fait d'espaces -> 400 MOTIF_OBLIGATOIRE ; 5 et 500 acceptés")
	void motifBornes() {
		for (String m : new String[] {null, "", "     ", "abcd", " abcd ", "x".repeat(501)}) {
			assertThatThrownBy(() -> service.annuler(demande.getId(), m, jwt))
					.satisfies(e -> assertThat(code(e)).isEqualTo("MOTIF_OBLIGATOIRE"));
		}
		assertThat(demande.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.APPROUVEE);
		service.annuler(demande.getId(), "abcde", jwt);
		demande.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		service.annuler(demande.getId(), "x".repeat(500), jwt);
	}

	@Test
	@DisplayName("critère 16 / C1-12 : annuler son propre congé -> 403 avant le contrôle du motif")
	void propreConge() {
		lenient().when(connecte.resoudre(jwt)).thenReturn(Optional.of(colWeb));
		assertThatThrownBy(() -> service.annuler(demande.getId(), "", jwt))
				.satisfies(e -> {
					assertThat(code(e)).isEqualTo("AUTO_ANNULATION_INTERDITE");
					assertThat(((ReferentielMetierException) e).getHttpStatus()).isEqualTo(403);
				});
	}

	@Test
	@DisplayName("critère 17 : demande déjà annulée ou refusée -> 409 DEMANDE_DEJA_DECIDEE ; en cours -> 409 DEMANDE_NON_APPROUVEE ; ordre de mission -> 409 DEMANDE_NON_ANNULABLE")
	void etats() {
		demande.setStatut(StatutDemandeAdministrativeRh.ANNULEE);
		assertThatThrownBy(() -> service.annuler(demande.getId(), "Motif ok", jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("DEMANDE_DEJA_DECIDEE"));
		demande.setStatut(StatutDemandeAdministrativeRh.REFUSEE);
		assertThatThrownBy(() -> service.annuler(demande.getId(), "Motif ok", jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("DEMANDE_DEJA_DECIDEE"));
		demande.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		assertThatThrownBy(() -> service.annuler(demande.getId(), "Motif ok", jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("DEMANDE_NON_APPROUVEE"));
		demande.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		demande.setTypeDemande(TypeDemandeAdministrativeRh.ORDRE_MISSION);
		assertThatThrownBy(() -> service.annuler(demande.getId(), "Motif ok", jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("DEMANDE_NON_ANNULABLE"));
	}

	@Test
	@DisplayName("404 DEMANDE_INTROUVABLE pour une demande inconnue")
	void inconnue() {
		assertThatThrownBy(() -> service.annuler(UUID.randomUUID(), "Motif ok", jwt))
				.satisfies(e -> assertThat(((ReferentielMetierException) e).getHttpStatus()).isEqualTo(404));
	}

	@Test
	@DisplayName("C1-12 : congé MALADIE approuvé non commencé -> 200 sans mouvement")
	void maladieSansMouvement() {
		demande.setContenu(new HashMap<>(java.util.Map.of("type_conge", "MALADIE")));
		demande.setJoursDecomptes(null);
		assertThat(service.annuler(demande.getId(), "Motif ok", jwt).getStatut()).isEqualTo(StatutDemandeAdministrativeRh.ANNULEE);
		verify(soldeService, never()).enregistrer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("C1-15 : congé ANNUEL antérieur aux soldes -> aucun recrédit, mention dans l'historique")
	void demandeAnterieure() {
		demande.setJoursDecomptes(null);
		service.annuler(demande.getId(), "Motif ok", jwt);
		verify(soldeService, never()).enregistrer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
		ArgumentCaptor<DemandeAdminWorkflowHistory> h = ArgumentCaptor.forClass(DemandeAdminWorkflowHistory.class);
		verify(historiqueRepository).save(h.capture());
		assertThat(h.getValue().getCommentaire()).contains("Demande antérieure aux soldes : non décomptée");
	}

	@Test
	@DisplayName("M1 : la demande est verrouillée avant tout contrôle ; un second appel après la première annulation -> 409 DEMANDE_DEJA_DECIDEE, un seul recrédit")
	void doubleAnnulation() {
		service.annuler(demande.getId(), "Motif ok", jwt);
		assertThatThrownBy(() -> service.annuler(demande.getId(), "Motif ok", jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("DEMANDE_DEJA_DECIDEE"));
		verify(soldeService, org.mockito.Mockito.times(1)).enregistrer(any(), eq(TypeMouvementSolde.RECREDIT_ANNULATION), any(),
				any(), any(), any(), any(), any(), any(), any());
		verify(demandeRepository, never()).findById(any());
	}

	@Test
	@DisplayName("M1 : congé MALADIE annulé deux fois -> le second appel est refusé (409), jamais deux annulations")
	void doubleAnnulationMaladie() {
		demande.setContenu(new HashMap<>(java.util.Map.of("type_conge", "MALADIE")));
		demande.setJoursDecomptes(null);
		service.annuler(demande.getId(), "Motif ok", jwt);
		assertThatThrownBy(() -> service.annuler(demande.getId(), "Motif ok", jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("DEMANDE_DEJA_DECIDEE"));
		verify(historiqueRepository, org.mockito.Mockito.times(1)).save(any());
	}
}
