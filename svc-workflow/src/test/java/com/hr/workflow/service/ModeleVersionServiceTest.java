package com.hr.workflow.service;

import com.hr.workflow.catalogue.CatalogueTypesObjet;
import com.hr.workflow.client.WorkflowReferentielClient;
import com.hr.workflow.client.WorkflowReferentielClient.CalendrierSocieteResponse;
import com.hr.workflow.definition.DefinitionCodec;
import com.hr.workflow.definition.DefinitionValidateur;
import com.hr.workflow.definition.DefinitionWorkflow;
import com.hr.workflow.definition.DelaiDefinition;
import com.hr.workflow.definition.EcheanceDefinition;
import com.hr.workflow.definition.EtapeDefinition;
import com.hr.workflow.definition.EtapeDefinition.RepliSiVideDefinition;
import com.hr.workflow.definition.IntervenantsDefinition;
import com.hr.workflow.definition.NotificationsDynamiquesDefinition;
import com.hr.workflow.domain.StatutVersion;
import com.hr.workflow.dto.AdminWorkflowDtos.ActionVersionRequest;
import com.hr.workflow.dto.AdminWorkflowDtos.ComparaisonVersionsResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.CreerModeleRequest;
import com.hr.workflow.dto.AdminWorkflowDtos.ModeleDetailResponse;
import com.hr.workflow.dto.AdminWorkflowDtos.ModifierVersionRequest;
import com.hr.workflow.dto.AdminWorkflowDtos.VersionDetailResponse;
import com.hr.workflow.entity.WfModele;
import com.hr.workflow.entity.WfModeleVersion;
import com.hr.workflow.repository.WfJournalModeleRepository;
import com.hr.workflow.repository.WfModeleRepository;
import com.hr.workflow.repository.WfModeleVersionRepository;
import com.hr.workflow.web.WorkflowMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests CA-06.1 à CA-06.6, quatre yeux (D-W3-06) et marquage des fonctions (D-W3-05). */
@ExtendWith(MockitoExtension.class)
class ModeleVersionServiceTest {

	@Mock private WfModeleRepository modeleRepository;
	@Mock private WfModeleVersionRepository versionRepository;
	@Mock private WfJournalModeleRepository journalRepository;
	@Mock private WorkflowReferentielClient referentielClient;
	@Mock private CacheManager cacheManager;
	@Mock private PlatformTransactionManager txManager;

	private final CatalogueTypesObjet catalogue = new CatalogueTypesObjet();
	private final DefinitionCodec codec = new DefinitionCodec();
	private final DefinitionValidateur validateur = new DefinitionValidateur(catalogue, codec);
	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private ModeleVersionService service;

	@BeforeEach
	void setUp() {
		lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		service = new ModeleVersionService(
				modeleRepository, versionRepository, journalRepository, catalogue, codec,
				validateur, referentielClient, cacheManager, txManager,
				Clock.fixed(Instant.parse("2026-11-20T11:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	void ca06_1_creerModeleActifExistant_409_MODELE_ACTIF_EXISTANT() {
		when(modeleRepository.existsByCode("CONGE_2")).thenReturn(false);
		when(modeleRepository.findActifParObjetTypeEtSousType("CONGE", null)).thenReturn(Optional.of(new WfModele()));

		assertThatThrownBy(() -> service.creerModele(
				new CreerModeleRequest("CONGE_2", "Congé 2", null, "CONGE", null), UUID.randomUUID(), "DRH"))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 409)
				.hasFieldOrPropertyWithValue("code", "MODELE_ACTIF_EXISTANT");
	}

	@Test
	void ca06_6_modifierVersionPubliee409_etConflitVersion409() throws Exception {
		WfModele m = new WfModele();
		m.setId(UUID.randomUUID());
		m.setObjetType("CONGE");
		WfModeleVersion vPub = new WfModeleVersion();
		vPub.setId(UUID.randomUUID());
		vPub.setModele(m);
		vPub.setStatut(StatutVersion.PUBLIEE);
		vPub.setVersion(1L);
		when(versionRepository.findByIdAvecModele(vPub.getId())).thenReturn(Optional.of(vPub));

		assertThatThrownBy(() -> service.modifierBrouillon(vPub.getId(),
				new ModifierVersionRequest(jsonMapper.readTree("{}"), 1L), UUID.randomUUID(), "DRH"))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 409)
				.hasFieldOrPropertyWithValue("code", "VERSION_NON_BROUILLON");

		WfModeleVersion vBrouillon = new WfModeleVersion();
		vBrouillon.setId(UUID.randomUUID());
		vBrouillon.setModele(m);
		vBrouillon.setStatut(StatutVersion.BROUILLON);
		vBrouillon.setVersion(3L);
		when(versionRepository.findByIdAvecModele(vBrouillon.getId())).thenReturn(Optional.of(vBrouillon));

		assertThatThrownBy(() -> service.modifierBrouillon(vBrouillon.getId(),
				new ModifierVersionRequest(jsonMapper.readTree("{}"), 2L), UUID.randomUUID(), "DRH"))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 409)
				.hasFieldOrPropertyWithValue("code", "MODIFIE_ENTRE_TEMPS");
	}

	@Test
	void brouillonInvalide_senregistreAvecSesErreurs() throws Exception {
		WfModele m = new WfModele();
		m.setId(UUID.randomUUID());
		m.setObjetType("CONGE");
		WfModeleVersion v = new WfModeleVersion();
		v.setId(UUID.randomUUID());
		v.setModele(m);
		v.setNumero(1);
		v.setStatut(StatutVersion.BROUILLON);
		v.setEmpreinte("0".repeat(64));
		v.setVersion(1L);
		when(versionRepository.findByIdAvecModele(v.getId())).thenReturn(Optional.of(v));
		when(versionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

		String invalidJson = "{\"format\":1,\"objet_type\":\"CONGE\",\"etapes\":[]}";
		VersionDetailResponse rep = service.modifierBrouillon(v.getId(),
				new ModifierVersionRequest(jsonMapper.readTree(invalidJson), 1L), UUID.randomUUID(), "DRH");
		assertThat(rep.erreurs()).isNotEmpty();
		verify(versionRepository).saveAndFlush(v);
	}

	@Test
	void ca06_5_etQuatreYeux_etMarquageFonction503() {
		UUID auteur1 = UUID.randomUUID();
		UUID auteur2 = UUID.randomUUID();

		WfModele m = new WfModele();
		m.setId(UUID.randomUUID());
		m.setObjetType("CONGE");
		EtapeDefinition eFunc = new EtapeDefinition("E1", "Validation", "VALIDATION", null,
				new IntervenantsDefinition("FONCTION", Map.of("fonction_code", "GEST_PAIE", "portee", "SOCIETE"), "UN_SEUL"),
				new RepliSiVideDefinition("BLOQUER", null), null, null, List.of("APPROUVER", "REFUSER"),
				false, null, new DelaiDefinition(2, "JOURS_OUVRES", null), null,
				new EcheanceDefinition("RIEN", null, null), null, null, null);
		DefinitionWorkflow def = new DefinitionWorkflow(1, "CONGE", null, List.of(eFunc));

		WfModeleVersion v = new WfModeleVersion();
		v.setId(UUID.randomUUID());
		v.setModele(m);
		v.setNumero(2);
		v.setStatut(StatutVersion.BROUILLON);
		v.setDefinition(codec.serialiser(def));
		v.setEmpreinte(codec.calculerEmpreinte(def));
		v.setModifieParCompteId(auteur1);
		v.setVersion(1L);

		WfModeleVersion ancienne = new WfModeleVersion();
		ancienne.setId(UUID.randomUUID());
		ancienne.setModele(m);
		ancienne.setNumero(1);
		ancienne.setStatut(StatutVersion.PUBLIEE);

		when(versionRepository.findByIdAvecModele(v.getId())).thenReturn(Optional.of(v));
		when(versionRepository.findByModeleIdAndStatut(m.getId(), StatutVersion.PUBLIEE)).thenReturn(Optional.of(ancienne));
		when(versionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

		// 1. Quatre yeux actif -> même auteur refusé 403 PUBLICATION_QUATRE_YEUX
		CalendrierSocieteResponse cal4Yeux = new CalendrierSocieteResponse(
				"Africa/Tunis", List.of("LUNDI"), "08:00", "17:00", true, true, List.of());
		when(referentielClient.calendrierSociete(any(), any())).thenReturn(cal4Yeux);
		assertThatThrownBy(() -> service.publier(v.getId(), new ActionVersionRequest(1L), auteur1, "DRH01"))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 403)
				.hasFieldOrPropertyWithValue("code", "PUBLICATION_QUATRE_YEUX");

		// 2. Référentiel indisponible lors du marquage fonction -> 503, rien publié (D-W3-05)
		doThrow(WorkflowMetierException.indisponible("SERVICE_INDISPONIBLE", "Down"))
				.when(referentielClient).marquerFonctionUtilisee("GEST_PAIE");
		assertThatThrownBy(() -> service.publier(v.getId(), new ActionVersionRequest(1L), auteur2, "RH02"))
				.isInstanceOf(WorkflowMetierException.class)
				.hasFieldOrPropertyWithValue("httpStatus", 503);
		assertThat(v.getStatut()).isEqualTo(StatutVersion.BROUILLON);

		// 3. Publication réussie par auteur2 : v2 PUBLIEE, v1 RETIREE (CA-06.5)
		org.mockito.Mockito.doNothing().when(referentielClient).marquerFonctionUtilisee("GEST_PAIE");
		VersionDetailResponse pub = service.publier(v.getId(), new ActionVersionRequest(1L), auteur2, "RH02");
		assertThat(pub.statut()).isEqualTo(StatutVersion.PUBLIEE);
		assertThat(ancienne.getStatut()).isEqualTo(StatutVersion.RETIREE);
	}

	@Test
	void pointage_creationBrouillonDefaut_publicationV1CocheePuisV2Decochee_etComparaison() {
		UUID auteur = UUID.randomUUID();
		UUID modeleId = UUID.randomUUID();
		UUID v1Id = UUID.randomUUID();
		UUID v2Id = UUID.randomUUID();

		when(modeleRepository.existsByCode("WF_POINTAGE_RO")).thenReturn(false);
		when(modeleRepository.findActifParObjetTypeEtSousType("POINTAGE", null)).thenReturn(Optional.empty());
		when(modeleRepository.saveAndFlush(any(WfModele.class))).thenAnswer(inv -> {
			WfModele m = inv.getArgument(0);
			m.setId(modeleId);
			m.setVersion(0L);
			return m;
		});
		final WfModeleVersion[] stock = new WfModeleVersion[2];
		when(versionRepository.saveAndFlush(any(WfModeleVersion.class))).thenAnswer(inv -> {
			WfModeleVersion ver = inv.getArgument(0);
			if (ver.getId() == null) {
				ver.setId(ver.getNumero() == 1 ? v1Id : v2Id);
				ver.setVersion(0L);
			}
			stock[ver.getNumero() - 1] = ver;
			return ver;
		});
		when(versionRepository.findByModeleIdAndStatut(modeleId, StatutVersion.BROUILLON)).thenAnswer(inv ->
				java.util.Arrays.stream(stock).filter(v -> v != null && v.getStatut() == StatutVersion.BROUILLON).findFirst());
		when(versionRepository.findByModeleIdAndStatut(modeleId, StatutVersion.PUBLIEE)).thenAnswer(inv ->
				java.util.Arrays.stream(stock).filter(v -> v != null && v.getStatut() == StatutVersion.PUBLIEE).findFirst());

		ModeleDetailResponse cree = service.creerModele(
				new CreerModeleRequest("WF_POINTAGE_RO", "Notification pointage RO", null, "POINTAGE", null),
				auteur, "DRH01");
		assertThat(cree.objetType()).isEqualTo("POINTAGE");
		assertThat(cree.brouillonId()).isEqualTo(v1Id);
		DefinitionWorkflow defV1Init = codec.deserialiser(stock[0].getDefinition());
		assertThat(defV1Init.etapes()).isEmpty();
		assertThat(defV1Init.notificationsDynamiques()).isEqualTo(NotificationsDynamiquesDefinition.parDefautPointage());

		// Publication v1 avec notifier_ro = true
		DefinitionWorkflow defV1Cochee = new DefinitionWorkflow(1, "POINTAGE",
				new DefinitionWorkflow.OptionsWorkflow(false), List.of(),
				new NotificationsDynamiquesDefinition(true, "TOUTE_LA_SOCIETE", List.of(), List.of("ENTREE", "SORTIE"), "WEBSOCKET"));
		stock[0].setDefinition(codec.serialiser(defV1Cochee));
		when(versionRepository.findByIdAvecModele(v1Id)).thenReturn(Optional.of(stock[0]));
		when(referentielClient.calendrierSociete(any(), any())).thenReturn(
				new CalendrierSocieteResponse("Africa/Tunis", List.of("LUNDI"), "08:00", "17:00", false, false, List.of()));

		VersionDetailResponse pubV1 = service.publier(v1Id, new ActionVersionRequest(0L), auteur, "DRH01");
		assertThat(pubV1.statut()).isEqualTo(StatutVersion.PUBLIEE);

		// Création brouillon v2, décochage notifier_ro = false, publication v2
		when(modeleRepository.findById(modeleId)).thenReturn(Optional.of(stock[0].getModele()));
		when(versionRepository.existsByModeleIdAndStatut(modeleId, StatutVersion.BROUILLON)).thenReturn(false);
		when(versionRepository.findTopByModeleIdOrderByNumeroDesc(modeleId)).thenReturn(Optional.of(stock[0]));
		VersionDetailResponse v2Brouillon = service.creerBrouillon(modeleId, auteur, "DRH01");
		assertThat(v2Brouillon.numero()).isEqualTo(2);

		DefinitionWorkflow defV2Decochee = new DefinitionWorkflow(1, "POINTAGE",
				new DefinitionWorkflow.OptionsWorkflow(false), List.of(),
				new NotificationsDynamiquesDefinition(false, "TOUTE_LA_SOCIETE", List.of(), List.of("ENTREE", "SORTIE"), "WEBSOCKET"));
		stock[1].setDefinition(codec.serialiser(defV2Decochee));
		when(versionRepository.findByIdAvecModele(v2Id)).thenReturn(Optional.of(stock[1]));

		VersionDetailResponse pubV2 = service.publier(v2Id, new ActionVersionRequest(0L), auteur, "DRH01");
		assertThat(pubV2.statut()).isEqualTo(StatutVersion.PUBLIEE);
		assertThat(stock[0].getStatut()).isEqualTo(StatutVersion.RETIREE);

		when(versionRepository.findById(v1Id)).thenReturn(Optional.of(stock[0]));
		when(versionRepository.findById(v2Id)).thenReturn(Optional.of(stock[1]));
		ComparaisonVersionsResponse cmp = service.comparerVersions(v1Id, v2Id);
		assertThat(cmp.etapes()).hasSize(1);
		assertThat(cmp.etapes().get(0).code()).isEqualTo("NOTIFICATIONS_DYNAMIQUES");
		assertThat(cmp.etapes().get(0).champsModifies()).containsExactly("notifier_ro");
	}
}
