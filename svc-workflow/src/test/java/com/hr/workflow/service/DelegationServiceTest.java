package com.hr.workflow.service;

import com.hr.commun.planif.VerrouPlanificateur;
import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.AbsenceCollaborateur;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.client.WorkflowReferentielClient.CollaborateurResume;
import com.hr.workflow.client.WorkflowReferentielClient.IntervenantResolu;
import com.hr.workflow.domain.OrigineCandidat;
import com.hr.workflow.domain.OrigineDelegation;
import com.hr.workflow.domain.StatutInstance;
import com.hr.workflow.domain.StatutTache;
import com.hr.workflow.domain.TypeTache;
import com.hr.workflow.dto.DelegationDtos.CreerDelegationAdminRequest;
import com.hr.workflow.dto.DelegationDtos.CreerDelegationMoiRequest;
import com.hr.workflow.dto.DelegationDtos.DelegationResponse;
import com.hr.workflow.entity.WfDelegation;
import com.hr.workflow.entity.WfInstance;
import com.hr.workflow.entity.WfTache;
import com.hr.workflow.entity.WfTacheCandidat;
import com.hr.workflow.entity.WfTacheCandidatId;
import com.hr.workflow.moteur.DelegationAutoScheduler;
import com.hr.workflow.moteur.DelegationResolutionService;
import com.hr.workflow.moteur.DelegationResolutionService.CandidatSupplementaire;
import com.hr.workflow.moteur.OutboxService;
import com.hr.workflow.moteur.ResolutionIntervenantsService;
import com.hr.workflow.moteur.ResolutionIntervenantsService.LotResolu;
import com.hr.workflow.moteur.ResolutionIntervenantsService.RegleAResoudre;
import com.hr.workflow.moteur.ResolutionIntervenantsService.ResultatRegleResolue;
import com.hr.workflow.repository.WfBeneficiaireRepository;
import com.hr.workflow.repository.WfDelegationRepository;
import com.hr.workflow.repository.WfHistoriqueRepository;
import com.hr.workflow.repository.WfInstanceRepository;
import com.hr.workflow.repository.WfTacheCandidatRepository;
import com.hr.workflow.repository.WfTacheRepository;
import com.hr.workflow.web.WorkflowMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires des délégations manuelles et automatiques ({@link DelegationService},
 * {@link DelegationResolutionService}, {@link DelegationAutoScheduler} — CA-11.1 à CA-11.7, D-W21, D-W22, Q5).
 */
@ExtendWith(MockitoExtension.class)
class DelegationServiceTest {

	@Mock private WfDelegationRepository delegationRepository;
	@Mock private WfTacheRepository tacheRepository;
	@Mock private WfInstanceRepository instanceRepository;
	@Mock private WfTacheCandidatRepository candidatRepository;
	@Mock private WfBeneficiaireRepository beneficiaireRepository;
	@Mock private WfHistoriqueRepository historiqueRepository;
	@Mock private WorkflowReferentielClient referentielClient;
	@Mock private ResolutionIntervenantsService resolutionService;
	@Mock private OutboxService outboxService;
	@Mock private VerrouPlanificateur verrouPlanificateur;
	@Mock private PlatformTransactionManager txManager;

	private final CatalogueTypesObjet catalogue = new CatalogueTypesObjet();
	// 2026-11-20T09:00:00Z -> 10:00 Africa/Tunis, date locale 2026-11-20
	private final Instant maintenant = Instant.parse("2026-11-20T09:00:00Z");
	private final LocalDate aujourdhui = LocalDate.of(2026, 11, 20);
	private final Clock clock = Clock.fixed(maintenant, ZoneOffset.UTC);

	private DelegationService delegationService;
	private DelegationResolutionService resolutionDelegationService;
	private DelegationAutoScheduler autoScheduler;

	private UUID delegantCollabId, delegantCompteId;
	private UUID delegueCollabId, delegueCompteId;
	private UUID tiersCollabId, tiersCompteId;
	private CollaborateurResume delegantResume, delegueResume, tiersResume;

	@BeforeEach
	void setUp() {
		lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		lenient().when(referentielClient.calendrierSociete(any(), any())).thenReturn(CalendrierSocieteResponse.defaut());

		delegationService = new DelegationService(
				delegationRepository, tacheRepository, instanceRepository, candidatRepository,
				beneficiaireRepository, historiqueRepository, catalogue, referentielClient,
				outboxService, txManager, clock);
		resolutionDelegationService = new DelegationResolutionService(
				delegationRepository, referentielClient, resolutionService, txManager);
		autoScheduler = new DelegationAutoScheduler(
				delegationRepository, tacheRepository, instanceRepository, candidatRepository,
				beneficiaireRepository, historiqueRepository, catalogue, referentielClient,
				resolutionDelegationService, outboxService, verrouPlanificateur, txManager, clock);

		delegantCollabId = UUID.randomUUID();
		delegantCompteId = UUID.randomUUID();
		delegueCollabId = UUID.randomUUID();
		delegueCompteId = UUID.randomUUID();
		tiersCollabId = UUID.randomUUID();
		tiersCompteId = UUID.randomUUID();

		delegantResume = new CollaborateurResume(delegantCollabId, delegantCompteId, "Amel Chef", "M-01", "ACTIF");
		delegueResume = new CollaborateurResume(delegueCollabId, delegueCompteId, "Sami Délégué", "M-02", "ACTIF");
		tiersResume = new CollaborateurResume(tiersCollabId, tiersCompteId, "Nadia Tiers", "M-03", "ACTIF");

		lenient().when(referentielClient.collaborateurParCompte(delegantCompteId)).thenReturn(Optional.of(delegantResume));
		lenient().when(referentielClient.collaborateurParId(delegantCollabId)).thenReturn(Optional.of(delegantResume));
		lenient().when(referentielClient.collaborateurParId(delegueCollabId)).thenReturn(Optional.of(delegueResume));
		lenient().when(referentielClient.collaborateurParCompte(tiersCompteId)).thenReturn(Optional.of(tiersResume));

		lenient().when(delegationRepository.saveAndFlush(any())).thenAnswer(inv -> {
			WfDelegation d = inv.getArgument(0);
			if (d.getId() == null) {
				d.setId(UUID.randomUUID());
			}
			return d;
		});
	}

	@Test
	void ca11_1_creerMoi_activeAujourdhui_appliqueSurTachesFiltrees_notifieEtReactiveSansDoublon() {
		WfInstance instConge = nouvelleInstance("CONGE", tiersCollabId);
		WfTache tConge = nouvelleTache(instConge);
		WfInstance instMission = nouvelleInstance("ORDRE_MISSION", tiersCollabId);
		WfTache tMission = nouvelleTache(instMission);

		WfTacheCandidat titulaireConge = nouveauCandidat(tConge.getId(), delegantCollabId, delegantCompteId, "Amel Chef", OrigineCandidat.TITULAIRE);
		WfTacheCandidat delegueRetire = nouveauCandidat(tConge.getId(), delegueCollabId, delegueCompteId, "Sami Délégué", OrigineCandidat.DELEGUE);
		delegueRetire.setRetireLe(maintenant.minus(1, ChronoUnit.DAYS));

		when(tacheRepository.findTachesOuvertesPourTitulaire(delegantCollabId)).thenReturn(List.of(tConge, tMission));
		when(instanceRepository.findByIdForUpdate(instConge.getId())).thenReturn(Optional.of(instConge));
		when(tacheRepository.findByIdForUpdate(tConge.getId())).thenReturn(Optional.of(tConge));
		when(candidatRepository.findById(new WfTacheCandidatId(tConge.getId(), delegantCollabId)))
				.thenReturn(Optional.of(titulaireConge));
		when(candidatRepository.findById(new WfTacheCandidatId(tConge.getId(), delegueCollabId)))
				.thenReturn(Optional.of(delegueRetire));

		DelegationResponse rep = delegationService.creerMoi(
				delegantCompteId,
				new CreerDelegationMoiRequest(delegueCollabId, aujourdhui, aujourdhui.plusDays(5), List.of("CONGE"), "Congés"));

		assertThat(rep.active()).isTrue();
		assertThat(rep.origine()).isEqualTo(OrigineDelegation.MANUELLE);
		assertThat(rep.objetTypes()).containsExactly("CONGE");

		// Notification WF_DELEGATION envoyée au délégué
		verify(outboxService).publierNotification(any(), eq(delegueCompteId.toString()), eq("WEBSOCKET"),
				eq("WF_DELEGATION"), eq("Délégation reçue"), contains("Amel Chef"), eq("DELEGATION"), eq(rep.id()), eq(null));

		// Réactivation du candidat DELEGUE sans doublon de PK, avec pour_nom et delegation_id
		assertThat(delegueRetire.getRetireLe()).isNull();
		assertThat(delegueRetire.getOrigine()).isEqualTo(OrigineCandidat.DELEGUE);
		assertThat(delegueRetire.getPourCollaborateurId()).isEqualTo(delegantCollabId);
		assertThat(delegueRetire.getPourNom()).isEqualTo("Amel Chef");
		assertThat(delegueRetire.getDelegationId()).isEqualTo(rep.id());
		verify(candidatRepository).save(delegueRetire);
		verify(historiqueRepository).save(argThat(h -> "DELEGATION_APPLIQUEE".equals(h.getTypeEvenement())
				&& "Amel Chef".equals(h.getPourNom())));
		verify(outboxService).publierNotification(any(), eq(delegueCompteId.toString()), eq("WEBSOCKET"),
				eq("WF_TACHE_CREEE"), eq("Demande à traiter"), any(), eq("CONGE"), any(), any());

		// La tâche MISSION hors périmètre objet_types n'est pas touchée
		verify(instanceRepository, never()).findByIdForUpdate(instMission.getId());
	}

	@Test
	void ca11_1_creerMoi_future_neTouchePasTachesOuvertesAvantDateDebut() {
		DelegationResponse rep = delegationService.creerMoi(
				delegantCompteId,
				new CreerDelegationMoiRequest(delegueCollabId, aujourdhui.plusDays(2), aujourdhui.plusDays(6), null, null));

		assertThat(rep.active()).isFalse();
		verify(tacheRepository, never()).findTachesOuvertesPourTitulaire(any());
		verify(outboxService).publierNotification(any(), eq(delegueCompteId.toString()), eq("WEBSOCKET"),
				eq("WF_DELEGATION"), any(), any(), eq("DELEGATION"), eq(rep.id()), eq(null));
	}

	@Test
	void ca11_1_creerAdmin_listerMoiEtAdmin() {
		when(tacheRepository.findTachesOuvertesPourTitulaire(delegantCollabId)).thenReturn(List.of());
		DelegationResponse creee = delegationService.creerAdmin(
				new CreerDelegationAdminRequest(delegantCollabId, delegueCollabId, aujourdhui, aujourdhui.plusDays(90), null, "Mission"),
				tiersCompteId);
		assertThat(creee.active()).isTrue();
		assertThat(creee.delegantId()).isEqualTo(delegantCollabId);

		WfDelegation entite = new WfDelegation();
		entite.setId(creee.id());
		entite.setDelegantCollaborateurId(delegantCollabId);
		entite.setDelegantNom("Amel Chef");
		entite.setDelegueCollaborateurId(delegueCollabId);
		entite.setDelegueCompteId(delegueCompteId);
		entite.setDelegueNom("Sami Délégué");
		entite.setDu(aujourdhui);
		entite.setAu(aujourdhui.plusDays(1));
		entite.setOrigine(OrigineDelegation.MANUELLE);

		when(delegationRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(entite));
		when(delegationRepository.findAll(any(Specification.class), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(entite)));

		assertThat(delegationService.listerMoi(delegantCompteId, "DELEGANT", true)).hasSize(1);
		assertThat(delegationService.listerAdmin(true, delegantCollabId, null, 0, 20).elements()).hasSize(1);
	}

	@Test
	void ca11_2_invariantI1_exclutDelegueSiDemandeurOuBeneficiaire_immediateEtNouvelleTache() {
		// 1. Application immédiate : tâche dont le délégué est demandeur -> ignorée
		WfInstance instDem = nouvelleInstance("CONGE", delegueCollabId);
		WfTache tDem = nouvelleTache(instDem);
		WfTacheCandidat titDem = nouveauCandidat(tDem.getId(), delegantCollabId, delegantCompteId, "Amel Chef", OrigineCandidat.TITULAIRE);

		// 2. Application immédiate : tâche dont le délégué est bénéficiaire -> ignorée
		WfInstance instBenef = nouvelleInstance("CONGE", tiersCollabId);
		WfTache tBenef = nouvelleTache(instBenef);
		WfTacheCandidat titBenef = nouveauCandidat(tBenef.getId(), delegantCollabId, delegantCompteId, "Amel Chef", OrigineCandidat.TITULAIRE);

		when(tacheRepository.findTachesOuvertesPourTitulaire(delegantCollabId)).thenReturn(List.of(tDem, tBenef));
		when(instanceRepository.findByIdForUpdate(instDem.getId())).thenReturn(Optional.of(instDem));
		when(tacheRepository.findByIdForUpdate(tDem.getId())).thenReturn(Optional.of(tDem));
		when(candidatRepository.findById(new WfTacheCandidatId(tDem.getId(), delegantCollabId))).thenReturn(Optional.of(titDem));

		when(instanceRepository.findByIdForUpdate(instBenef.getId())).thenReturn(Optional.of(instBenef));
		when(tacheRepository.findByIdForUpdate(tBenef.getId())).thenReturn(Optional.of(tBenef));
		when(candidatRepository.findById(new WfTacheCandidatId(tBenef.getId(), delegantCollabId))).thenReturn(Optional.of(titBenef));
		when(beneficiaireRepository.existsByIdInstanceIdAndIdCollaborateurId(instBenef.getId(), delegueCollabId)).thenReturn(true);

		delegationService.creerMoi(delegantCompteId,
				new CreerDelegationMoiRequest(delegueCollabId, aujourdhui, aujourdhui.plusDays(3), null, null));

		verify(candidatRepository, never()).save(any());

		// 3. Création d'une nouvelle tâche (DelegationResolutionService) : I1 exclu aussi
		WfDelegation del = new WfDelegation();
		del.setId(UUID.randomUUID());
		del.setDelegantCollaborateurId(delegantCollabId);
		del.setDelegantNom("Amel Chef");
		del.setDelegueCollaborateurId(delegueCollabId);
		del.setDelegueCompteId(delegueCompteId);
		del.setDelegueNom("Sami Délégué");
		del.setDu(aujourdhui);
		del.setAu(aujourdhui.plusDays(3));
		when(delegationRepository.findActivesPourDelegantsALaDate(Set.of(delegantCollabId), aujourdhui))
				.thenReturn(List.of(del));

		IntervenantResolu titulaire = new IntervenantResolu(delegantCollabId, delegantCompteId, "Amel Chef", "Manager");
		assertThat(resolutionDelegationService.resoudreDeleguesPourTache(
				aujourdhui, "CONGE", delegueCollabId, List.of(), List.of(titulaire), false)).isEmpty();
		assertThat(resolutionDelegationService.resoudreDeleguesPourTache(
				aujourdhui, "CONGE", tiersCollabId, List.of(delegueCollabId), List.of(titulaire), false)).isEmpty();
		assertThat(resolutionDelegationService.resoudreDeleguesPourTache(
				aujourdhui, "CONGE", tiersCollabId, List.of(), List.of(titulaire), false))
				.extracting(CandidatSupplementaire::collaborateurId, CandidatSupplementaire::origine, CandidatSupplementaire::pourNom)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(delegueCollabId, OrigineCandidat.DELEGUE, "Amel Chef"));
	}

	@Test
	void ca11_3_validations422_delegationInvalide() {
		// du < aujourd'hui
		assertThatThrownBy(() -> delegationService.creerMoi(delegantCompteId,
				new CreerDelegationMoiRequest(delegueCollabId, aujourdhui.minusDays(1), aujourdhui.plusDays(1), null, null)))
				.isInstanceOf(WorkflowMetierException.class)
				.extracting("httpStatus", "code")
				.containsExactly(422, "DELEGATION_INVALIDE");

		// au < du
		assertThatThrownBy(() -> delegationService.creerMoi(delegantCompteId,
				new CreerDelegationMoiRequest(delegueCollabId, aujourdhui.plusDays(3), aujourdhui.plusDays(1), null, null)))
				.isInstanceOf(WorkflowMetierException.class)
				.extracting("httpStatus", "code")
				.containsExactly(422, "DELEGATION_INVALIDE");

		// durée > 90 jours
		assertThatThrownBy(() -> delegationService.creerMoi(delegantCompteId,
				new CreerDelegationMoiRequest(delegueCollabId, aujourdhui, aujourdhui.plusDays(91), null, null)))
				.isInstanceOf(WorkflowMetierException.class)
				.extracting("httpStatus", "code")
				.containsExactly(422, "DELEGATION_INVALIDE");

		// délégation à soi-même (moi et admin)
		assertThatThrownBy(() -> delegationService.creerMoi(delegantCompteId,
				new CreerDelegationMoiRequest(delegantCollabId, aujourdhui, aujourdhui.plusDays(2), null, null)))
				.isInstanceOf(WorkflowMetierException.class)
				.extracting("httpStatus", "code")
				.containsExactly(422, "DELEGATION_INVALIDE");
		assertThatThrownBy(() -> delegationService.creerAdmin(
				new CreerDelegationAdminRequest(delegantCollabId, delegantCollabId, aujourdhui, aujourdhui.plusDays(2), null, null),
				tiersCompteId))
				.isInstanceOf(WorkflowMetierException.class)
				.extracting("httpStatus", "code")
				.containsExactly(422, "DELEGATION_INVALIDE");

		// objet_type inconnu
		assertThatThrownBy(() -> delegationService.creerMoi(delegantCompteId,
				new CreerDelegationMoiRequest(delegueCollabId, aujourdhui, aujourdhui.plusDays(2), List.of("OBJET_INCONNU"), null)))
				.isInstanceOf(WorkflowMetierException.class)
				.extracting("httpStatus", "code")
				.containsExactly(422, "DELEGATION_INVALIDE");

		// délégué inactif
		UUID inactifId = UUID.randomUUID();
		when(referentielClient.collaborateurParId(inactifId))
				.thenReturn(Optional.of(new CollaborateurResume(inactifId, UUID.randomUUID(), "Inactif", "M-99", "INACTIF")));
		assertThatThrownBy(() -> delegationService.creerMoi(delegantCompteId,
				new CreerDelegationMoiRequest(inactifId, aujourdhui, aujourdhui.plusDays(2), null, null)))
				.isInstanceOf(WorkflowMetierException.class)
				.extracting("httpStatus", "code")
				.containsExactly(422, "DELEGATION_INVALIDE");
	}

	@Test
	void ca11_3_chevauchement409_memeAvecObjetTypesDisjoints_q5() {
		when(delegationRepository.existsChevauchement(delegantCollabId, aujourdhui, aujourdhui.plusDays(5)))
				.thenReturn(true);

		assertThatThrownBy(() -> delegationService.creerMoi(delegantCompteId,
				new CreerDelegationMoiRequest(delegueCollabId, aujourdhui, aujourdhui.plusDays(5), List.of("ORDRE_MISSION"), "Q5")))
				.isInstanceOf(WorkflowMetierException.class)
				.extracting("httpStatus", "code")
				.containsExactly(409, "DELEGATION_CHEVAUCHEMENT");

		// Q5 : le contrôle ne filtre pas par objet_types
		verify(delegationRepository).existsChevauchement(delegantCollabId, aujourdhui, aujourdhui.plusDays(5));
	}

	@Test
	void ca11_4_annulation_retireDelegueSurTachesOuvertes_etAntiIdor404PourTiers() {
		UUID delId = UUID.randomUUID();
		WfDelegation del = new WfDelegation();
		del.setId(delId);
		del.setDelegantCollaborateurId(delegantCollabId);
		del.setDelegueCollaborateurId(delegueCollabId);

		WfInstance inst = nouvelleInstance("CONGE", tiersCollabId);
		WfTache t = nouvelleTache(inst);
		WfTacheCandidat candDelegue = nouveauCandidat(t.getId(), delegueCollabId, delegueCompteId, "Sami Délégué", OrigineCandidat.DELEGUE);
		candDelegue.setDelegationId(delId);

		when(delegationRepository.findById(delId)).thenReturn(Optional.of(del));
		when(candidatRepository.findActifsParDelegationSurTachesOuvertes(delId)).thenReturn(List.of(candDelegue));
		when(tacheRepository.findById(t.getId())).thenReturn(Optional.of(t));
		when(instanceRepository.findByIdForUpdate(inst.getId())).thenReturn(Optional.of(inst));
		when(tacheRepository.findByIdForUpdate(t.getId())).thenReturn(Optional.of(t));
		when(candidatRepository.findById(candDelegue.getId())).thenReturn(Optional.of(candDelegue));

		// Un tiers reçoit strictement 404 DELEGATION_INTROUVABLE ("Délégation introuvable.")
		assertThatThrownBy(() -> delegationService.annulerMoi(delId, tiersCompteId))
				.isInstanceOf(WorkflowMetierException.class)
				.extracting("httpStatus", "code", "message")
				.containsExactly(404, "DELEGATION_INTROUVABLE", "Délégation introuvable.");

		// Le délégant annule : candidat DELEGUE retiré sous verrou wf_instance puis wf_tache
		delegationService.annulerMoi(delId, delegantCompteId);
		assertThat(del.getAnnuleeLe()).isEqualTo(maintenant);
		assertThat(del.getAnnuleeParCompteId()).isEqualTo(delegantCompteId);
		assertThat(candDelegue.getRetireLe()).isEqualTo(maintenant);
	}

	@Test
	void ca11_5_delegationAutoConge_resolutionPrioriteManuelle_etDesactivationCalendrier() {
		UUID absentId = UUID.randomUUID();
		UUID absentCompteId = UUID.randomUUID();
		UUID autreAbsentId = UUID.randomUUID();
		UUID autreAbsentCompteId = UUID.randomUUID();
		UUID managerId = UUID.randomUUID();
		UUID managerCompteId = UUID.randomUUID();

		IntervenantResolu titulaire1 = new IntervenantResolu(absentId, absentCompteId, "Karim Absent", "Manager");
		IntervenantResolu titulaire2 = new IntervenantResolu(autreAbsentId, autreAbsentCompteId, "Leila Absente", "Manager");

		// titulaire2 possède une délégation manuelle qui doit primer sur l'auto congé
		WfDelegation manuelle = new WfDelegation();
		manuelle.setId(UUID.randomUUID());
		manuelle.setDelegantCollaborateurId(autreAbsentId);
		manuelle.setDelegantNom("Leila Absente");
		manuelle.setDelegueCollaborateurId(delegueCollabId);
		manuelle.setDelegueCompteId(delegueCompteId);
		manuelle.setDelegueNom("Sami Délégué");
		manuelle.setDu(aujourdhui);
		manuelle.setAu(aujourdhui.plusDays(4));

		when(delegationRepository.findActivesPourDelegantsALaDate(Set.of(absentId, autreAbsentId), aujourdhui))
				.thenReturn(List.of(manuelle));
		when(referentielClient.absences(aujourdhui)).thenReturn(List.of(
				new AbsenceCollaborateur(absentId, absentCompteId, "Karim Absent", "M-10", aujourdhui, aujourdhui.plusDays(2), "ANNUEL"),
				new AbsenceCollaborateur(autreAbsentId, autreAbsentCompteId, "Leila Absente", "M-11", aujourdhui, aujourdhui.plusDays(2), "ANNUEL")));

		when(resolutionService.resoudre(eq(aujourdhui), eq(absentId), any(), any(), any())).thenReturn(new LotResolu(
				Map.of("delegation_auto", new ResultatRegleResolue(
						List.of(new IntervenantResolu(managerId, managerCompteId, "Directeur Pôle", "N+1")),
						false, false, null)),
				null));

		List<CandidatSupplementaire> res = resolutionDelegationService.resoudreDeleguesPourTache(
				aujourdhui, "CONGE", tiersCollabId, List.of(), List.of(titulaire1, titulaire2), true);

		assertThat(res).hasSize(2);
		assertThat(res).anySatisfy(c -> {
			assertThat(c.collaborateurId()).isEqualTo(managerId);
			assertThat(c.origine()).isEqualTo(OrigineCandidat.DELEGUE_AUTO);
			assertThat(c.pourCollaborateurId()).isEqualTo(absentId);
			assertThat(c.pourNom()).isEqualTo("Karim Absent");
		});
		assertThat(res).anySatisfy(c -> {
			assertThat(c.collaborateurId()).isEqualTo(delegueCollabId);
			assertThat(c.origine()).isEqualTo(OrigineCandidat.DELEGUE);
			assertThat(c.pourCollaborateurId()).isEqualTo(autreAbsentId);
		});

		// Vérifier que la règle de résolution utilise bien MANAGER_NOEUD avec repli ROLE [RH, ADMIN]
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<RegleAResoudre>> reglesCap = ArgumentCaptor.forClass(List.class);
		verify(resolutionService).resoudre(eq(aujourdhui), eq(absentId), any(), any(), reglesCap.capture());
		RegleAResoudre regleAuto = reglesCap.getValue().get(0);
		assertThat(regleAuto.intervenants().regle()).isEqualTo("MANAGER_NOEUD");
		assertThat(regleAuto.repliSiVide().action()).isEqualTo("ROLE");
		assertThat(regleAuto.repliSiVide().roles()).containsExactly("RH", "ADMIN");

		// Quand delegationAutoActive = false, aucun appel aux absences
		when(delegationRepository.findActivesPourDelegantsALaDate(Set.of(absentId), aujourdhui)).thenReturn(List.of());
		assertThat(resolutionDelegationService.resoudreDeleguesPourTache(
				aujourdhui, "CONGE", tiersCollabId, List.of(), List.of(titulaire1), false)).isEmpty();
	}

	@Test
	void ca11_5_scheduler_synchroniseDemarrageFinManuelle_ajouteEtRetireAutoConge_etRespecteToggleFalse() {
		WfInstance inst = nouvelleInstance("CONGE", tiersCollabId);
		WfTache t = nouvelleTache(inst);

		// 1. Retrait d'un DELEGUE manuel expiré + retrait d'un DELEGUE_AUTO dont le titulaire est revenu
		WfTacheCandidat delegueExpire = nouveauCandidat(t.getId(), delegueCollabId, delegueCompteId, "Sami Délégué", OrigineCandidat.DELEGUE);
		WfTacheCandidat titulaireAbsent = nouveauCandidat(t.getId(), delegantCollabId, delegantCompteId, "Amel Chef", OrigineCandidat.TITULAIRE);
		UUID revenuId = UUID.randomUUID();
		WfTacheCandidat autoRevenu = nouveauCandidat(t.getId(), UUID.randomUUID(), UUID.randomUUID(), "Ancien Remplaçant", OrigineCandidat.DELEGUE_AUTO);
		autoRevenu.setPourCollaborateurId(revenuId);

		when(candidatRepository.findCandidatsDeleguesExpiresSurTachesOuvertes(aujourdhui)).thenReturn(List.of(delegueExpire));
		when(tacheRepository.findById(t.getId())).thenReturn(Optional.of(t));
		when(instanceRepository.findByIdForUpdate(inst.getId())).thenReturn(Optional.of(inst));
		when(tacheRepository.findByIdForUpdate(t.getId())).thenReturn(Optional.of(t));
		when(candidatRepository.findById(delegueExpire.getId())).thenReturn(Optional.of(delegueExpire));
		when(candidatRepository.findById(autoRevenu.getId())).thenReturn(Optional.of(autoRevenu));

		when(delegationRepository.findActivesALaDate(aujourdhui)).thenReturn(List.of());
		when(referentielClient.absences(aujourdhui)).thenReturn(List.of(
				new AbsenceCollaborateur(delegantCollabId, delegantCompteId, "Amel Chef", "M-01", aujourdhui, aujourdhui, "ANNUEL")));
		when(tacheRepository.findTachesOuvertesAvecInstance()).thenReturn(List.of(t));
		when(candidatRepository.findByIdTacheIdIn(List.of(t.getId()))).thenReturn(List.of(titulaireAbsent, autoRevenu));
		when(candidatRepository.findByIdTacheId(t.getId())).thenReturn(List.of(titulaireAbsent, autoRevenu));
		when(beneficiaireRepository.findByIdInstanceIdIn(List.of(inst.getId()))).thenReturn(List.of());
		when(beneficiaireRepository.findByIdInstanceId(inst.getId())).thenReturn(List.of());

		UUID rempId = UUID.randomUUID();
		UUID rempCompteId = UUID.randomUUID();
		when(resolutionService.resoudre(eq(aujourdhui), eq(delegantCollabId), any(), any(), any()))
				.thenReturn(new LotResolu(Map.of("delegation_auto", new ResultatRegleResolue(
						List.of(new IntervenantResolu(rempId, rempCompteId, "Directeur Remp", "N+1")),
						false, false, null)), null));

		int mouvements = autoScheduler.synchroniserDelegationsDuJour();
		assertThat(mouvements).isEqualTo(3); // 1 expiré retiré + 1 autoRevenu retiré + 1 auto ajouté
		assertThat(delegueExpire.getRetireLe()).isEqualTo(maintenant);
		assertThat(autoRevenu.getRetireLe()).isEqualTo(maintenant);
		verify(candidatRepository).save(argThat(c ->
				c.getId().getCollaborateurId().equals(rempId)
						&& c.getOrigine() == OrigineCandidat.DELEGUE_AUTO
						&& "Amel Chef".equals(c.getPourNom())));
		verify(outboxService).publierNotification(any(), eq(rempCompteId.toString()), eq("WEBSOCKET"),
				eq("WF_TACHE_CREEE"), any(), any(), eq("CONGE"), any(), any());

		// 2. Toggle delegation_auto_conges = false -> retire tous les DELEGUE_AUTO ouverts sans appeler /absences
		when(referentielClient.calendrierSociete(any(), any())).thenReturn(new CalendrierSocieteResponse(
				"Africa/Tunis", List.of("LUNDI"), "08:00", "17:00", false, false, List.of()));
		WfTacheCandidat autoActif = nouveauCandidat(t.getId(), rempId, rempCompteId, "Directeur Remp", OrigineCandidat.DELEGUE_AUTO);
		when(candidatRepository.findCandidatsDeleguesExpiresSurTachesOuvertes(aujourdhui)).thenReturn(List.of());
		when(candidatRepository.findCandidatsDeleguesAutoSurTachesOuvertes()).thenReturn(List.of(autoActif));
		when(candidatRepository.findById(autoActif.getId())).thenReturn(Optional.of(autoActif));

		int retiresDesact = autoScheduler.synchroniserDelegationsDuJour();
		assertThat(retiresDesact).isEqualTo(1);
		assertThat(autoActif.getRetireLe()).isEqualTo(maintenant);

		// Vérifier délégation du verrou
		autoScheduler.executer();
		verify(verrouPlanificateur).executerSiLibre(eq("wf-delegations"), any());
	}

	@Test
	void ca11_7_pasDeChaineDeDelegation_delegantNonTitulaireEstIgnore() {
		// B est uniquement DELEGUE sur la tâche (pour A), pas TITULAIRE -> sa délégation vers C ne doit pas s'appliquer en chaîne
		WfInstance inst = nouvelleInstance("CONGE", tiersCollabId);
		WfTache t = nouvelleTache(inst);
		WfTacheCandidat bEstDelegue = nouveauCandidat(t.getId(), delegantCollabId, delegantCompteId, "Amel Chef", OrigineCandidat.DELEGUE);

		when(tacheRepository.findTachesOuvertesPourTitulaire(delegantCollabId)).thenReturn(List.of(t));
		when(instanceRepository.findByIdForUpdate(inst.getId())).thenReturn(Optional.of(inst));
		when(tacheRepository.findByIdForUpdate(t.getId())).thenReturn(Optional.of(t));
		when(candidatRepository.findById(new WfTacheCandidatId(t.getId(), delegantCollabId)))
				.thenReturn(Optional.of(bEstDelegue));

		delegationService.creerMoi(delegantCompteId,
				new CreerDelegationMoiRequest(delegueCollabId, aujourdhui, aujourdhui.plusDays(3), null, "Chaîne interdite"));

		verify(candidatRepository, never()).save(any());
	}

	private WfInstance nouvelleInstance(String objetType, UUID demandeurCollab) {
		WfInstance inst = new WfInstance();
		inst.setId(UUID.randomUUID());
		inst.setObjetType(objetType);
		inst.setObjetId(UUID.randomUUID());
		inst.setObjetNumero("DEM-202611-0300");
		inst.setDemandeurCollaborateurId(demandeurCollab);
		inst.setDemandeurNom("Demandeur Test");
		inst.setStatut(StatutInstance.EN_COURS);
		return inst;
	}

	private WfTache nouvelleTache(WfInstance inst) {
		WfTache t = new WfTache();
		t.setId(UUID.randomUUID());
		t.setInstance(inst);
		t.setType(TypeTache.VALIDATION);
		t.setLibelle("Validation Manager");
		t.setStatut(StatutTache.A_TRAITER);
		return t;
	}

	private WfTacheCandidat nouveauCandidat(UUID tacheId, UUID collabId, UUID compteId, String nom, OrigineCandidat origine) {
		WfTacheCandidat c = new WfTacheCandidat();
		c.setId(new WfTacheCandidatId(tacheId, collabId));
		c.setCompteId(compteId);
		c.setNom(nom);
		c.setOrigine(origine);
		c.setAjouteLe(maintenant.minus(2, ChronoUnit.DAYS));
		return c;
	}
}
