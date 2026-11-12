package com.hr.referentiel.service;

import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.DemandeAdministrativeRhResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdminWorkflowHistoryRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandeAdministrativeRhServiceValideurNoeudTest {

	@Mock
	private DemandeAdministrativeRhRepository demandeRepo;
	@Mock
	private CollaborateurRepository collaborateurRepository;
	@Mock
	private CollaborateurConnecteService collaborateurConnecteService;
	@Mock
	private DemandeAdministrativeValidationService validationService;
	@Mock
	private RhNotificationPublisher notificationPublisher;
	@Mock
	private DemandeAdminWorkflowHistoryRepository workflowHistoryRepository;

	@Mock
	private NumeroLisibleService numeroLisibleService;
	@Mock
	private ParametreService parametreService;

	@InjectMocks
	private DemandeAdministrativeRhService service;

	private Jwt jwt;
	private UniteOrganisation noeud;
	private Collaborateur demandeur;
	private Collaborateur managerActif;
	private Collaborateur roAutreNoeud;
	private Collaborateur managerInactif;
	private DemandeAdministrativeRh demande;
	private UUID demandeId;

	@BeforeEach
	void setUp() {
		org.mockito.Mockito.lenient().when(parametreService.libelleOuDefaut(org.mockito.ArgumentMatchers.eq("TYPE_CONGE"),
				org.mockito.ArgumentMatchers.any())).thenReturn("Congé annuel");
		jwt = Jwt.withTokenValue("token")
				.header("alg", "none")
				.subject("M-MGR")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(3600))
				.build();

		noeud = new UniteOrganisation();
		noeud.setId(UUID.randomUUID());
		noeud.setCode("IT");
		noeud.setLibelle("Informatique");

		managerActif = collab(UUID.randomUUID(), "M-MGR", "Ada", "Lovelace", "ACTIF", "COLLABORATEUR");
		managerInactif = collab(UUID.randomUUID(), "M-INACT", "Alan", "Turing", "INACTIF", "RO");
		roAutreNoeud = collab(UUID.randomUUID(), "M-RO2", "Grace", "Hopper", "ACTIF", "RO");

		demandeur = collab(UUID.randomUUID(), "M-EMP", "Bob", "Martin", "ACTIF", "COLLABORATEUR");
		demandeur.setUnite(noeud);
		noeud.setManager(managerActif);

		demandeId = UUID.randomUUID();
		demande = new DemandeAdministrativeRh();
		demande.setId(demandeId);
		demande.setTypeDemande(TypeDemandeAdministrativeRh.CONGE);
		demande.setDemandeur(demandeur);
		demande.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR);
		demande.setValideurAttendu(managerActif);
		demande.setContenu(new HashMap<>(Map.of(
				"date_debut", "2026-09-01",
				"date_fin", "2026-09-05",
				"type_conge", "ANNUEL")));
	}

	@Test
	@DisplayName("manager ACTIF du nœud peut valider M01")
	void validerSuperieur_managerActif_ok() {
		when(demandeRepo.findById(demandeId)).thenReturn(Optional.of(demande));
		when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(managerActif);
		when(collaborateurRepository.findDetailById(demandeur.getId())).thenReturn(Optional.of(demandeur));
		when(demandeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(workflowHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		DemandeAdministrativeRhResponse response = service.validerSuperieur(demandeId, jwt);

		assertThat(response.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		verify(notificationPublisher).notifierValidationRo(eq(demandeur), eq("CONGE"), eq(managerActif), any(), isNull());
	}

	@Test
	@DisplayName("lot 3 : la réponse porte statut_libelle et type_conge_libelle (libellé courant, code inconnu signalé)")
	void reponsePorteLesLibellesParametres() throws Exception {
		when(demandeRepo.findById(demandeId)).thenReturn(Optional.of(demande));
		when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(managerActif);
		when(collaborateurRepository.findDetailById(demandeur.getId())).thenReturn(Optional.of(demandeur));
		when(demandeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(workflowHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(parametreService.libelleOuInconnu("STATUT_DEMANDE", "EN_VALIDATION_RRH")).thenReturn("Chez les RH");
		when(parametreService.libelleOuInconnu("TYPE_CONGE", "ANNUEL")).thenReturn("Congé payé");

		DemandeAdministrativeRhResponse response = service.validerSuperieur(demandeId, jwt);

		assertThat(response.getStatutLibelle()).isEqualTo("Chez les RH");
		assertThat(response.getTypeCongeLibelle()).isEqualTo("Congé payé");
		String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(response);
		assertThat(json).contains("\"type_conge_libelle\":\"Congé payé\"")
				.contains("\"statut_libelle\":\"Chez les RH\"");
	}

	@Test
	@DisplayName("JWT RO d'un autre nœud → 403 (pas 400)")
	void validerSuperieur_roAutreNoeud_403() {
		when(demandeRepo.findById(demandeId)).thenReturn(Optional.of(demande));
		when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(roAutreNoeud);

		assertThatThrownBy(() -> service.validerSuperieur(demandeId, jwt))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("manager actif du nœud");
	}

	@Test
	@DisplayName("manager du nœud inactif → 403")
	void validerSuperieur_managerInactif_403() {
		demande.setValideurAttendu(managerInactif);
		when(demandeRepo.findById(demandeId)).thenReturn(Optional.of(demande));
		when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(managerInactif);

		assertThatThrownBy(() -> service.validerSuperieur(demandeId, jwt))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("manager actif du nœud");
	}

	@Test
	@DisplayName("création : snapshot du manager nœud ACTIF")
	void creer_snapshotManagerNoeud() {
		Jwt jwtDemandeur = Jwt.withTokenValue("t")
				.header("alg", "none")
				.subject("M-EMP")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(3600))
				.build();

		when(collaborateurConnecteService.exigerCollaborateur(jwtDemandeur)).thenReturn(demandeur);
		when(collaborateurRepository.findDetailById(demandeur.getId())).thenReturn(Optional.of(demandeur));
		when(demandeRepo.save(any())).thenAnswer(inv -> {
			DemandeAdministrativeRh d = inv.getArgument(0);
			d.setId(UUID.randomUUID());
			return d;
		});
		when(workflowHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var req = new com.hr.referentiel.dto.DemandeAdministrativeRhCreationRequest();
		req.setTypeDemande(TypeDemandeAdministrativeRh.CONGE);
		req.setContenu(Map.of(
				"date_debut", "2026-09-01",
				"date_fin", "2026-09-05",
				"type_conge", "ANNUEL"));

		DemandeAdministrativeRhResponse response = service.creer(req, jwtDemandeur);

		assertThat(response.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR);
		verify(demandeRepo).save(org.mockito.ArgumentMatchers.argThat(d ->
				d.getValideurAttendu() != null
						&& d.getValideurAttendu().getId().equals(managerActif.getId())));
	}

	@Test
	@DisplayName("création sans manager ACTIF → EN_VALIDATION_RRH + snapshot null")
	void creer_sansManager_skipRrh() {
		noeud.setManager(null);
		Jwt jwtDemandeur = Jwt.withTokenValue("t")
				.header("alg", "none")
				.subject("M-EMP")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(3600))
				.build();

		when(collaborateurConnecteService.exigerCollaborateur(jwtDemandeur)).thenReturn(demandeur);
		when(collaborateurRepository.findDetailById(demandeur.getId())).thenReturn(Optional.of(demandeur));
		when(demandeRepo.save(any())).thenAnswer(inv -> {
			DemandeAdministrativeRh d = inv.getArgument(0);
			d.setId(UUID.randomUUID());
			return d;
		});
		when(workflowHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var req = new com.hr.referentiel.dto.DemandeAdministrativeRhCreationRequest();
		req.setTypeDemande(TypeDemandeAdministrativeRh.CONGE);
		req.setContenu(Map.of(
				"date_debut", "2026-09-01",
				"date_fin", "2026-09-05",
				"type_conge", "ANNUEL"));

		DemandeAdministrativeRhResponse response = service.creer(req, jwtDemandeur);

		assertThat(response.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		assertThat(response.getValideurAttenduIdentifiant()).isNull();
		verify(demandeRepo).save(org.mockito.ArgumentMatchers.argThat(d -> d.getValideurAttendu() == null));
	}

	@Test
	@DisplayName("H2 suivi : valideur snapshot + pas de libellé Direction")
	void suivi_avecManager_exposeValideur() {
		Jwt jwtDemandeur = Jwt.withTokenValue("t")
				.header("alg", "none")
				.subject("M-EMP")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(3600))
				.build();

		when(demandeRepo.findById(demandeId)).thenReturn(Optional.of(demande));
		when(collaborateurConnecteService.exigerCollaborateur(jwtDemandeur)).thenReturn(demandeur);

		var suivi = service.suivi(demandeId, jwtDemandeur, false);

		assertThat(suivi.isEtapeSuperieurRequise()).isTrue();
		assertThat(suivi.getValideurAttenduIdentifiant()).isEqualTo(managerActif.getId());
		assertThat(suivi.getValideurAttenduNomComplet()).isEqualTo("Ada Lovelace");
		assertThat(suivi.getValideurAttenduLibelleRole()).isEqualTo("Responsable de service");
		assertThat(suivi.getMessageExplication()).contains("responsable de votre service");
		assertThat(suivi.getEtapes()).anySatisfy(e -> {
			assertThat(e.getCode()).isEqualTo("RO");
			assertThat(e.getLibelle()).isEqualTo("Validation — Ada Lovelace");
			assertThat(e.getLibelle()).doesNotContainIgnoringCase("Direction");
		});
		assertThat(suivi.getEtapes()).noneMatch(e ->
				e.getLibelle() != null && e.getLibelle().toLowerCase().contains("direction"));
	}

	@Test
	@DisplayName("H2 suivi skip RRH : pas d'étape RO + message explication")
	void suivi_sansSnapshot_skipRrhSansEtapeFantome() {
		demande.setValideurAttendu(null);
		demande.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		// Manager live présent ne doit PAS réintroduire une étape RO (H2-R06)
		noeud.setManager(managerActif);

		Jwt jwtDemandeur = Jwt.withTokenValue("t")
				.header("alg", "none")
				.subject("M-EMP")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(3600))
				.build();

		when(demandeRepo.findById(demandeId)).thenReturn(Optional.of(demande));
		when(collaborateurConnecteService.exigerCollaborateur(jwtDemandeur)).thenReturn(demandeur);

		var suivi = service.suivi(demandeId, jwtDemandeur, false);

		assertThat(suivi.isEtapeSuperieurRequise()).isFalse();
		assertThat(suivi.getValideurAttenduIdentifiant()).isNull();
		assertThat(suivi.getMessageExplication()).contains("directement en validation RH");
		assertThat(suivi.getEtapes()).noneMatch(e -> "RO".equals(e.getCode()));
	}

	@Test
	@DisplayName("H3 NR : changement manager nœud ne mute pas le snapshot valideur_attendu")
	void validerSuperieur_apresChangementManagerNoeud_ancienSnapshotSeuleAutorise() {
		Collaborateur nouveauManager = collab(UUID.randomUUID(), "M-NEW", "New", "Boss", "ACTIF", "COLLABORATEUR");
		noeud.setManager(nouveauManager);
		// snapshot reste l'ancien manager
		demande.setValideurAttendu(managerActif);

		when(demandeRepo.findById(demandeId)).thenReturn(Optional.of(demande));
		when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(nouveauManager);

		assertThatThrownBy(() -> service.validerSuperieur(demandeId, jwt))
				.isInstanceOf(AccessDeniedException.class);

		when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(managerActif);
		when(collaborateurRepository.findDetailById(demandeur.getId())).thenReturn(Optional.of(demandeur));
		when(demandeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(workflowHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		DemandeAdministrativeRhResponse ok = service.validerSuperieur(demandeId, jwt);
		assertThat(ok.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		assertThat(demande.getValideurAttendu().getId()).isEqualTo(managerActif.getId());
	}

	@Test
	@DisplayName("SCRUM-25 : un autre collaborateur lit / suit / annule la demande → 403 (pas 400)")
	void demandeDunAutre_403() {
		when(demandeRepo.findById(demandeId)).thenReturn(Optional.of(demande));
		when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(roAutreNoeud);

		assertThatThrownBy(() -> service.obtenir(demandeId, jwt, false)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> service.suivi(demandeId, jwt, false)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> service.obtenirHistorique(demandeId, jwt, false))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> service.annuler(demandeId, jwt)).isInstanceOf(AccessDeniedException.class);
	}

	// ─── D-H03 : personne ne valide sa propre demande (SCRUM-23 / SCRUM-24) ──────────

	@Test
	@DisplayName("D-L02 / D-L01.b : numéro attribué, exposé, commentaire d'historique sans code")
	void creer_numeroEtCommentaireSansCode() {
		when(numeroLisibleService.attribuer(eq(PrefixeNumero.DEM), any())).thenReturn("DEM-202611-0001");
		when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(demandeur);
		when(collaborateurRepository.findDetailById(demandeur.getId())).thenReturn(Optional.of(demandeur));
		when(demandeRepo.save(any())).thenAnswer(inv -> {
			DemandeAdministrativeRh d = inv.getArgument(0);
			d.setId(UUID.randomUUID());
			return d;
		});
		when(workflowHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		var req = new com.hr.referentiel.dto.DemandeAdministrativeRhCreationRequest();
		req.setTypeDemande(TypeDemandeAdministrativeRh.CONGE);
		req.setContenu(Map.of("date_debut", "2026-09-01", "date_fin", "2026-09-05", "type_conge", "ANNUEL"));

		DemandeAdministrativeRhResponse r = service.creer(req, jwt);

		assertThat(r.getNumero()).isEqualTo("DEM-202611-0001");
		var captor = org.mockito.ArgumentCaptor.forClass(com.hr.referentiel.entity.DemandeAdminWorkflowHistory.class);
		verify(workflowHistoryRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
		assertThat(captor.getAllValues()).allSatisfy(h -> {
			assertThat(h.getCommentaire()).doesNotContainPattern("[A-Z]{3,}_[A-Z_]+").doesNotContain("CONGE");
			assertThat(h.getActeurNom()).isEqualTo("Bob Martin");
		});
		assertThat(captor.getAllValues().get(0).getCommentaire()).isEqualTo("Demande de congé annuel créée");
		verify(notificationPublisher).notifierDemandeRecue(eq(demandeur), eq("CONGE"), any(), eq("DEM-202611-0001"), eq(managerActif));
	}

	@Test
	@DisplayName("D-L01.b : commentaires de création pour chaque type")
	void commentaireCreationParType() {
		assertThat(DemandeAdministrativeRhService.commentaireCreation(TypeDemandeAdministrativeRh.AUTORISATION_SORTIE, Map.of(), c -> null))
				.isEqualTo("Demande d'autorisation de sortie créée");
		assertThat(DemandeAdministrativeRhService.commentaireCreation(TypeDemandeAdministrativeRh.ORDRE_MISSION, Map.of(), c -> null))
				.isEqualTo("Demande d'ordre de mission créée");
		assertThat(DemandeAdministrativeRhService.commentaireCreation(TypeDemandeAdministrativeRh.CONGE, Map.of("type_conge", "MALADIE"),
				c -> ParametreService.enMinuscule("Congé maladie")))
				.isEqualTo("Demande de congé maladie créée");
	}

	@Test
	@DisplayName("D-L02 : recherche q insensible à la casse, combinée aux filtres, aucune requête hors numéro")
	void listerRh_rechercheParNumero() {
		demande.setNumero("DEM-202611-0001");
		when(demandeRepo.findPourRhByNumero("DEM-202611-0001")).thenReturn(List.of(demande));

		assertThat(service.listerToutPourRh(null, null, null, " dem-202611-0001 "))
				.extracting(DemandeAdministrativeRhResponse::getNumero).containsExactly("DEM-202611-0001");
		assertThat(service.listerToutPourRh(TypeDemandeAdministrativeRh.ORDRE_MISSION, null, null, "dem-202611-0001")).isEmpty();
		verify(demandeRepo, org.mockito.Mockito.never()).findAllPourRhOrderByCreeLeDesc();
	}

	@Test
	@DisplayName("Suivi : demande annulée avant le responsable → étapes responsable et RH non atteintes")
	void suivi_annuleeAvantResponsable_etapesNonAtteintes() {
		demande.setStatut(StatutDemandeAdministrativeRh.ANNULEE);
		when(demandeRepo.findById(demandeId)).thenReturn(Optional.of(demande));
		when(workflowHistoryRepository.findByDemandeAdministrativeIdOrderByDateActionAsc(demandeId)).thenReturn(List.of());

		var etapes = service.suivi(demandeId, jwt, true).getEtapes();

		assertThat(etapes).extracting(com.hr.referentiel.dto.WorkflowEtapeResponse::getCode)
				.containsExactly("DEPOT", "RO", "RRH", "CLOTURE");
		assertThat(etapes.get(1).isTerminee()).isFalse();
		assertThat(etapes.get(2).isTerminee()).isFalse();
		assertThat(etapes.get(3).getLibelle()).isEqualTo("Annulée par le demandeur");
		assertThat(etapes.get(3).isTerminee()).isTrue();
	}

	@Test
	@DisplayName("Approuver une demande déjà approuvée → 409 DEMANDE_DEJA_DECIDEE avec le libellé du statut")
	void validerRrh_dejaApprouvee_409() {
		demande.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		when(demandeRepo.findById(demandeId)).thenReturn(Optional.of(demande));

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.validerRrh(demandeId, jwt))
				.isInstanceOfSatisfying(com.hr.referentiel.web.ReferentielMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(409);
					assertThat(e.getCode()).isEqualTo("DEMANDE_DEJA_DECIDEE");
					assertThat(e.getMessage()).contains("Approuvée").doesNotContain("APPROUVEE");
				});
	}

	private DemandeAdministrativeRh creerPour(Collaborateur auteur) {
		when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(auteur);
		when(collaborateurRepository.findDetailById(auteur.getId())).thenReturn(Optional.of(auteur));
		when(demandeRepo.save(any())).thenAnswer(inv -> {
			DemandeAdministrativeRh d = inv.getArgument(0);
			d.setId(UUID.randomUUID());
			return d;
		});
		when(workflowHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		var req = new com.hr.referentiel.dto.DemandeAdministrativeRhCreationRequest();
		req.setTypeDemande(TypeDemandeAdministrativeRh.CONGE);
		req.setContenu(Map.of("date_debut", "2026-09-01", "date_fin", "2026-09-05", "type_conge", "ANNUEL"));
		service.creer(req, jwt);
		var captor = org.mockito.ArgumentCaptor.forClass(DemandeAdministrativeRh.class);
		verify(demandeRepo).save(captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("SCRUM-23 : la demande du manager du nœud part au manager du nœud parent")
	void creer_demandeurManagerDuNoeud_remonteAuParent() {
		UniteOrganisation departement = new UniteOrganisation();
		departement.setId(UUID.randomUUID());
		Collaborateur chefDepartement = collab(UUID.randomUUID(), "M-CD", "Chef", "Departement", "ACTIF", "RESPONSABLE");
		departement.setManager(chefDepartement);
		noeud.setParent(departement);
		managerActif.setUnite(noeud);

		DemandeAdministrativeRh d = creerPour(managerActif);

		assertThat(d.getValideurAttendu().getId()).isEqualTo(chefDepartement.getId());
		assertThat(d.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR);
	}

	@Test
	@DisplayName("SCRUM-23 : parent sans manager → remontée au premier ancêtre managé")
	void creer_demandeurManager_parentSansManager_remonteAuGrandParent() {
		UniteOrganisation departement = new UniteOrganisation();
		departement.setId(UUID.randomUUID());
		UniteOrganisation direction = new UniteOrganisation();
		direction.setId(UUID.randomUUID());
		Collaborateur directeur = collab(UUID.randomUUID(), "M-DIR", "Dir", "Ecteur", "ACTIF", "RO");
		direction.setManager(directeur);
		departement.setParent(direction);
		noeud.setParent(departement);
		managerActif.setUnite(noeud);

		DemandeAdministrativeRh d = creerPour(managerActif);

		assertThat(d.getValideurAttendu().getId()).isEqualTo(directeur.getId());
	}

	@Test
	@DisplayName("SCRUM-23 : manager d'un nœud racine (DG) → directement en validation RRH")
	void creer_demandeurManagerRacine_passageRrh() {
		managerActif.setUnite(noeud);

		DemandeAdministrativeRh d = creerPour(managerActif);

		assertThat(d.getValideurAttendu()).isNull();
		assertThat(d.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
	}

	@Test
	@DisplayName("SCRUM-23 : valider sa propre demande (snapshot ancien = soi-même) → 403")
	void validerSuperieur_propreDemande_403() {
		demande.setDemandeur(managerActif);
		demande.setValideurAttendu(managerActif);
		when(demandeRepo.findById(demandeId)).thenReturn(Optional.of(demande));
		when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(managerActif);

		assertThatThrownBy(() -> service.validerSuperieur(demandeId, jwt))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessageContaining("propre demande");
	}

	@Test
	@DisplayName("SCRUM-24 : un RH n'approuve ni ne refuse sa propre demande à l'étape RRH")
	void rrh_propreDemande_403() {
		demande.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		when(demandeRepo.findById(demandeId)).thenReturn(Optional.of(demande));
		when(collaborateurConnecteService.resoudre(jwt)).thenReturn(Optional.of(demandeur));
		var refus = new com.hr.referentiel.dto.DemandeRefusRequest();
		refus.setMotifRefus("motif");

		assertThatThrownBy(() -> service.validerRrh(demandeId, jwt)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> service.refuserRrh(demandeId, jwt, refus)).isInstanceOf(AccessDeniedException.class);
		assertThat(demande.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
	}

	@Test
	@DisplayName("SCRUM-24 : un autre RH approuve et l'historique trace son identité")
	void rrh_autreRh_approuveEtTrace() {
		Collaborateur autreRh = collab(UUID.randomUUID(), "M-RH", "Rita", "Hache", "ACTIF", "RH");
		demande.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		when(demandeRepo.findById(demandeId)).thenReturn(Optional.of(demande));
		when(collaborateurConnecteService.resoudre(jwt)).thenReturn(Optional.of(autreRh));
		when(collaborateurRepository.findDetailById(demandeur.getId())).thenReturn(Optional.of(demandeur));
		when(demandeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(workflowHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		DemandeAdministrativeRhResponse r = service.validerRrh(demandeId, jwt);

		assertThat(r.getStatut()).isEqualTo(StatutDemandeAdministrativeRh.APPROUVEE);
		var captor = org.mockito.ArgumentCaptor.forClass(com.hr.referentiel.entity.DemandeAdminWorkflowHistory.class);
		verify(workflowHistoryRepository).save(captor.capture());
		assertThat(captor.getValue().getActeurIdentifiant()).isEqualTo(autreRh.getId());
		assertThat(captor.getValue().getActeurNom()).contains("Rita Hache");
	}

	private static Collaborateur collab(UUID id, String matricule, String prenom, String nom,
			String statut, String profil) {
		Collaborateur c = new Collaborateur();
		c.setId(id);
		c.setMatricule(matricule);
		c.setPrenom(prenom);
		c.setNom(nom);
		c.setStatut(statut);
		c.setProfilAcces(profil);
		return c;
	}
}
