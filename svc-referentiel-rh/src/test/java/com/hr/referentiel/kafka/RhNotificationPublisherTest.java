package com.hr.referentiel.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RhNotificationPublisherTest {

	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;
	@Mock
	private CollaborateurRepository collaborateurRepository;

	private RhNotificationPublisher publisher;
	private Collaborateur demandeur;
	private Collaborateur chefService;
	private Collaborateur rh;
	private Collaborateur admin;

	@BeforeEach
	void setUp() {
		publisher = new RhNotificationPublisher(kafkaTemplate, new ObjectMapper(), collaborateurRepository);
		UniteOrganisation service = new UniteOrganisation();
		service.setId(UUID.randomUUID());
		service.setLibelle("Service Web");
		demandeur = collab("Sarra", true);
		demandeur.setUnite(service);
		chefService = collab("Omar", true);
		rh = collab("Amel", true);
		admin = collab("Nizar", true);
	}

	@Test
	@DisplayName("SCRUM-27 : le destinataire est le compte lié à la fiche (identité de la session STOMP)")
	void adresseLeCompte() {
		assertThat(RhNotificationPublisher.adresse(chefService)).isEqualTo(chefService.getCompteUtilisateurId().toString());
		Collaborateur sansCompte = collab("Legacy", false);
		assertThat(RhNotificationPublisher.adresse(sansCompte)).isEqualTo(sansCompte.getId().toString());
	}

	@Test
	@DisplayName("SCRUM-28 : la demande reçue est notifiée au valideur retenu, pas au manager du nœud")
	void demandeRecueAuValideur() {
		publisher.notifierDemandeRecue(demandeur, "CONGE", "ref", chefService);

		verify(kafkaTemplate).send(eq("rh.notifications"), eq(chefService.getCompteUtilisateurId().toString()), anyString());
	}

	@Test
	@DisplayName("SCRUM-29 : étape RRH notifiée aux RH et ADMIN, sans le demandeur")
	void validationRoAuBackofficeSaufDemandeur() {
		when(collaborateurRepository.findAllBackofficeEcritureActifs()).thenReturn(List.of(rh, admin));

		publisher.notifierValidationRo(rh, "CONGE", chefService);

		ArgumentCaptor<String> cles = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate).send(eq("rh.notifications"), cles.capture(), anyString());
		assertThat(cles.getAllValues()).containsExactly(admin.getCompteUtilisateurId().toString());
	}

	@Test
	@DisplayName("SCRUM-28 : l'annulation prévient le valideur retenu, rien si aucun valideur")
	void annulationAuValideur() {
		publisher.notifierAnnulationDemandeur(demandeur, "CONGE", null);
		verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());

		publisher.notifierAnnulationDemandeur(demandeur, "CONGE", chefService);
		verify(kafkaTemplate).send(eq("rh.notifications"), eq(chefService.getCompteUtilisateurId().toString()), anyString());
	}

	private static Collaborateur collab(String prenom, boolean avecCompte) {
		Collaborateur c = new Collaborateur();
		c.setId(UUID.randomUUID());
		c.setPrenom(prenom);
		c.setNom("Test");
		c.setStatut("ACTIF");
		if (avecCompte) {
			c.setCompteUtilisateurId(UUID.randomUUID());
		}
		return c;
	}
}
