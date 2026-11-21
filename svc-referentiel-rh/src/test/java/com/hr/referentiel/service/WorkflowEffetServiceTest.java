package com.hr.referentiel.service;

import com.hr.referentiel.domain.ActionWorkflowAdministratif;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.WorkflowEffetDtos.ActeurEffet;
import com.hr.referentiel.dto.WorkflowEffetDtos.RequeteEffet;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdminWorkflowHistory;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.RhWorkflowEffet;
import com.hr.referentiel.repository.DemandeAdminWorkflowHistoryRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.RhWorkflowEffetRepository;
import com.hr.referentiel.service.WorkflowEffetService.ResultatEffet;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEffetServiceTest {

	@Mock
	private DemandeAdministrativeRhRepository demandeRepo;
	@Mock
	private DemandeAdminWorkflowHistoryRepository historyRepo;
	@Mock
	private RhWorkflowEffetRepository effetRepo;
	@Mock
	private CongeDecompteService congeDecompteService;

	private WorkflowEffetService service;

	@BeforeEach
	void setUp() {
		service = new WorkflowEffetService(demandeRepo, historyRepo, effetRepo, congeDecompteService);
	}

	private DemandeAdministrativeRh demandeMoteur(TypeDemandeAdministrativeRh type, StatutDemandeAdministrativeRh statut) {
		Collaborateur demandeur = new Collaborateur();
		demandeur.setId(UUID.randomUUID());
		demandeur.setPrenom("Ali");
		demandeur.setNom("Ben Salah");

		DemandeAdministrativeRh d = new DemandeAdministrativeRh();
		d.setId(UUID.randomUUID());
		d.setNumero("DEM-202611-0001");
		d.setTypeDemande(type);
		d.setDemandeur(demandeur);
		d.setStatut(statut);
		d.setWorkflowMode("MOTEUR");
		d.setWorkflowEtat("DEMARRE");
		Map<String, Object> contenu = new HashMap<>();
		if (type == TypeDemandeAdministrativeRh.CONGE) {
			contenu.put("type_conge", "ANNUEL");
			d.setJoursDecomptes(new BigDecimal("2.0"));
		}
		d.setContenu(contenu);
		return d;
	}

	private RequeteEffet requete(String mode, DemandeAdministrativeRh d, String cle, String effet, String commentaire) {
		return new RequeteEffet(
				cle, mode, d.getTypeDemande().name(), d.getId(), effet,
				new ActeurEffet(UUID.randomUUID(), UUID.randomUUID(), "Sami RH", null),
				commentaire, UUID.randomUUID(), "Validation RH");
	}

	@ParameterizedTest
	@EnumSource(TypeDemandeAdministrativeRh.class)
	void appliquerApprouverPourChaqueType(TypeDemandeAdministrativeRh type) {
		DemandeAdministrativeRh d = demandeMoteur(type, StatutDemandeAdministrativeRh.EN_COURS);
		when(effetRepo.findById("cle-app-" + type)).thenReturn(Optional.empty());
		when(demandeRepo.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));

		ResultatEffet res = service.appliquer(requete("APPLIQUER", d, "cle-app-" + type, "APPROUVER", null));

		assertThat(res.statutHttp()).isEqualTo(200);
		assertThat(res.corps()).containsEntry("statut", "APPROUVEE");
		verify(congeDecompteService).approuver(any(), any());
		verify(historyRepo).save(any(DemandeAdminWorkflowHistory.class));
		verify(effetRepo).save(any(RhWorkflowEffet.class));
	}

	@ParameterizedTest
	@EnumSource(TypeDemandeAdministrativeRh.class)
	void appliquerRefuserMettreACompleterAnnulerExpirerPourChaqueType(TypeDemandeAdministrativeRh type) {
		for (String effet : new String[]{"REFUSER", "METTRE_A_COMPLETER", "ANNULER", "EXPIRER"}) {
			DemandeAdministrativeRh d = demandeMoteur(type, StatutDemandeAdministrativeRh.EN_COURS);
			String cle = "cle-" + effet + "-" + type;
			when(effetRepo.findById(cle)).thenReturn(Optional.empty());
			when(demandeRepo.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));

			ResultatEffet res = service.appliquer(requete("APPLIQUER", d, cle, effet, "Motif test"));

			assertThat(res.statutHttp()).isEqualTo(200);
			StatutDemandeAdministrativeRh attendu = switch (effet) {
				case "REFUSER" -> StatutDemandeAdministrativeRh.REFUSEE;
				case "METTRE_A_COMPLETER" -> StatutDemandeAdministrativeRh.A_COMPLETER;
				case "ANNULER", "EXPIRER" -> StatutDemandeAdministrativeRh.ANNULEE;
				default -> throw new AssertionError();
			};
			assertThat(d.getStatut()).isEqualTo(attendu);
			assertThat(res.corps()).containsEntry("statut", attendu.name());
			if ("EXPIRER".equals(effet)) {
				assertThat(d.getMotifAnnulation()).isEqualTo(WorkflowEffetService.MOTIF_EXPIRATION);
			}
			if ("REFUSER".equals(effet)) {
				assertThat(d.getMotifRefus()).isEqualTo("Motif test");
			}
		}
	}

	@Test
	void controlerEstLectureSeuleSansEcritureNiMemorisation() {
		DemandeAdministrativeRh d = demandeMoteur(TypeDemandeAdministrativeRh.CONGE, StatutDemandeAdministrativeRh.EN_COURS);
		when(demandeRepo.findById(d.getId())).thenReturn(Optional.of(d));
		doNothing().when(congeDecompteService).controlerValidationLectureSeule(d);

		ResultatEffet res = service.controler(requete("CONTROLER", d, null, "APPROUVER", null));

		assertThat(res.statutHttp()).isEqualTo(200);
		assertThat(res.corps()).containsEntry("ok", true);
		verify(congeDecompteService).controlerValidationLectureSeule(d);
		verify(demandeRepo, never()).save(any());
		verify(historyRepo, never()).save(any());
		verify(effetRepo, never()).save(any());
	}

	@Test
	void idempotenceMemeCleRenvoieMemeReponseSansDoubleDebitNiDoubleHistorique() {
		DemandeAdministrativeRh d = demandeMoteur(TypeDemandeAdministrativeRh.CONGE, StatutDemandeAdministrativeRh.EN_COURS);
		String cle = "wf:inst-1:APPROUVER";
		RhWorkflowEffet dejaFait = new RhWorkflowEffet(
				cle, d.getId(), "APPROUVER", "APPROUVEE", 200, Map.of("statut", "APPROUVEE"));

		when(effetRepo.findById(cle)).thenReturn(Optional.empty()).thenReturn(Optional.of(dejaFait));
		when(demandeRepo.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));

		ResultatEffet r1 = service.appliquer(requete("APPLIQUER", d, cle, "APPROUVER", null));
		ResultatEffet r2 = service.appliquer(requete("APPLIQUER", d, cle, "APPROUVER", null));

		assertThat(r1.statutHttp()).isEqualTo(200);
		assertThat(r2.statutHttp()).isEqualTo(200);
		assertThat(r2.corps()).containsEntry("statut", "APPROUVEE");
		verify(congeDecompteService, times(1)).approuver(any(), any());
		verify(historyRepo, times(1)).save(any());
		verify(effetRepo, times(1)).save(any());
	}

	@Test
	void demandeDejaDecideeRenvoie409EtEstMemorisee() {
		DemandeAdministrativeRh d = demandeMoteur(TypeDemandeAdministrativeRh.CONGE, StatutDemandeAdministrativeRh.APPROUVEE);
		when(effetRepo.findById("cle-409")).thenReturn(Optional.empty());
		when(demandeRepo.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));

		ResultatEffet res = service.appliquer(requete("APPLIQUER", d, "cle-409", "REFUSER", "Trop tard"));

		assertThat(res.statutHttp()).isEqualTo(409);
		assertThat(res.corps()).containsEntry("code", "OBJET_DEJA_DECIDE");
		verify(effetRepo).save(any(RhWorkflowEffet.class));
		verify(historyRepo, never()).save(any());
	}

	@Test
	void demandeEnModeHeriteRenvoie409() {
		DemandeAdministrativeRh d = demandeMoteur(TypeDemandeAdministrativeRh.CONGE, StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		d.setWorkflowMode("HERITE");
		when(effetRepo.findById("cle-herite")).thenReturn(Optional.empty());
		when(demandeRepo.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));

		ResultatEffet res = service.appliquer(requete("APPLIQUER", d, "cle-herite", "APPROUVER", null));

		assertThat(res.statutHttp()).isEqualTo(409);
		assertThat(res.corps()).containsEntry("code", "OBJET_DEJA_DECIDE");
	}

	@Test
	void soldeInsuffisant422NonMemorisePuisMemeCleRejouee200ApresAjustement() {
		DemandeAdministrativeRh d = demandeMoteur(TypeDemandeAdministrativeRh.CONGE, StatutDemandeAdministrativeRh.EN_COURS);
		String cle = "wf:inst-ca078:APPROUVER";
		when(effetRepo.findById(cle)).thenReturn(Optional.empty());
		when(demandeRepo.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));
		doThrow(new ReferentielMetierException("SOLDE_INSUFFISANT", "Solde insuffisant : 2 jours demandés, 1 jour disponible", 422))
				.when(congeDecompteService).approuver(any(), any());

		assertThatThrownBy(() -> service.appliquer(requete("APPLIQUER", d, cle, "APPROUVER", null)))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(ex -> {
					ReferentielMetierException rme = (ReferentielMetierException) ex;
					assertThat(rme.getHttpStatus()).isEqualTo(422);
					assertThat(rme.getCode()).isEqualTo("SOLDE_INSUFFISANT");
				});
		verify(effetRepo, never()).save(any());
		verify(historyRepo, never()).save(any());

		// Après ajustement du solde, le moteur rejoue la MÊME clé → 200
		org.mockito.Mockito.doReturn(Optional.empty()).when(congeDecompteService).approuver(any(), any());

		ResultatEffet res = service.appliquer(requete("APPLIQUER", d, cle, "APPROUVER", null));
		assertThat(res.statutHttp()).isEqualTo(200);
		assertThat(res.corps()).containsEntry("statut", "APPROUVEE");
		verify(effetRepo, times(1)).save(any());
	}

	@Test
	void approuverAccepteEtatACompleterEtTraceDelegationDansHistorique() {
		DemandeAdministrativeRh d = demandeMoteur(TypeDemandeAdministrativeRh.ORDRE_MISSION, StatutDemandeAdministrativeRh.A_COMPLETER);
		when(effetRepo.findById("cle-acomp")).thenReturn(Optional.empty());
		when(demandeRepo.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));

		RequeteEffet req = new RequeteEffet("cle-acomp", "APPLIQUER", "ORDRE_MISSION", d.getId(), "APPROUVER",
				new ActeurEffet(UUID.randomUUID(), UUID.randomUUID(), "Nadia Interim", "Sami RH"),
				null, UUID.randomUUID(), "Validation RH");
		ResultatEffet res = service.appliquer(req);

		assertThat(res.statutHttp()).isEqualTo(200);
		ArgumentCaptor<DemandeAdminWorkflowHistory> cap = ArgumentCaptor.forClass(DemandeAdminWorkflowHistory.class);
		verify(historyRepo).save(cap.capture());
		assertThat(cap.getValue().getAction()).isEqualTo(ActionWorkflowAdministratif.APPROBATION_RRH);
		assertThat(cap.getValue().getActeurNom()).isEqualTo("Nadia Interim (pour le compte de Sami RH)");
	}

	@Test
	void typeObjetNonGereRenvoie422() {
		RequeteEffet req = new RequeteEffet("cle-doc", "APPLIQUER", "DEMANDE_DOCUMENT", UUID.randomUUID(),
				"APPROUVER", null, null, null, null);
		assertThatThrownBy(() -> service.appliquer(req))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(ex -> {
					ReferentielMetierException rme = (ReferentielMetierException) ex;
					assertThat(rme.getHttpStatus()).isEqualTo(422);
					assertThat(rme.getCode()).isEqualTo("TYPE_OBJET_NON_GERE");
				});
	}
}
