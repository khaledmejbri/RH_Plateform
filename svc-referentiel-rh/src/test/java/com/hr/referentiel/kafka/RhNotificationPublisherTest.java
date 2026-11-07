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
		publisher.notifierDemandeRecue(demandeur, "CONGE", "DEM-202611-0001", chefService);

		verify(kafkaTemplate).send(eq("rh.notifications"), eq(chefService.getCompteUtilisateurId().toString()), anyString());
	}

	@Test
	@DisplayName("SCRUM-29 : étape RRH notifiée aux RH et ADMIN, sans le demandeur")
	void validationRoAuBackofficeSaufDemandeur() {
		when(collaborateurRepository.findAllBackofficeEcritureActifs()).thenReturn(List.of(rh, admin));

		publisher.notifierValidationRo(rh, "CONGE", chefService, "DEM-202611-0001");

		ArgumentCaptor<String> cles = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate).send(eq("rh.notifications"), cles.capture(), anyString());
		assertThat(cles.getAllValues()).containsExactly(admin.getCompteUtilisateurId().toString());
	}

	@Test
	@DisplayName("SCRUM-28 : l'annulation prévient le valideur retenu, rien si aucun valideur")
	void annulationAuValideur() {
		publisher.notifierAnnulationDemandeur(demandeur, "CONGE", null, "DEM-202611-0001");
		verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());

		publisher.notifierAnnulationDemandeur(demandeur, "CONGE", chefService, "DEM-202611-0001");
		verify(kafkaTemplate).send(eq("rh.notifications"), eq(chefService.getCompteUtilisateurId().toString()), anyString());
	}

	@Test
	@DisplayName("D-L02 : le numéro est un champ JSON et figure dans le texte ; absent s'il n'y en a pas")
	void numeroDansLeMessage() {
		publisher.notifierApprobationRrh(demandeur, "CONGE", "DEM-202611-0003");
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate).send(eq("rh.notifications"), anyString(), json.capture());
		assertThat(json.getValue()).contains("\"numero\":\"DEM-202611-0003\"").contains("[DEM-202611-0003]");

		publisher.notifierApprobationRrh(demandeur, "CONGE", null);
		verify(kafkaTemplate, org.mockito.Mockito.times(2)).send(eq("rh.notifications"), anyString(), json.capture());
		assertThat(json.getValue()).doesNotContain("numero");
	}

	@Test
	@DisplayName("D-L01.d/e : textes publiés accentués, sans sigle RO / RRH / FIFO ni faute connue")
	void textesPublies() {
		when(collaborateurRepository.findAllBackofficeEcritureActifs()).thenReturn(List.of(rh));
		Collaborateur sansUnite = collab("Sans", true);
		demandeur.setSuperieur(chefService);
		String n = "DEM-202611-0001";
		publisher.notifierDemandeRecue(demandeur, "CONGE", n, chefService);
		publisher.notifierDemandeRecue(demandeur, "CONGE", n, null);
		publisher.notifierDemandeRecue(sansUnite, "CONGE", n, null);
		publisher.notifierValidationRo(demandeur, "CONGE", chefService, n);
		publisher.notifierRefusRo(demandeur, "CONGE", null, n);
		publisher.notifierApprobationRrh(demandeur, "CONGE", n);
		publisher.notifierRefusRrh(demandeur, "CONGE", null, n);
		publisher.notifierAnnulationDemandeur(demandeur, "CONGE", chefService, n);
		publisher.notifierDemandeDocumentRecue(demandeur, "ATTESTATION_TRAVAIL", "DOC-202611-0001");
		publisher.notifierDocumentDisponible(demandeur, "ATTESTATION_TRAVAIL", "x", "DOC-202611-0001");
		publisher.notifierDocumentRejete(demandeur, "ATTESTATION_TRAVAIL", null, "DOC-202611-0001");
		publisher.notifierDerogationFifo(rh, "ATTESTATION_TRAVAIL", "urgence", demandeur, "DOC-202611-0001");
		publisher.notifierPlainteExterne(demandeur, "PLT-202611-0001");
		publisher.notifierValidationRoPlainteExterne(demandeur, "PLT-202611-0001", chefService);
		publisher.notifierDemandeFormationSoumise(demandeur, "Excel", "FOR-202611-0001");
		publisher.notifierDemandeFormationIntegree(demandeur, "Excel", "FOR-202611-0001");
		publisher.notifierDemandeFormationRefusee(demandeur, "Excel", null, "FOR-202611-0001");
		publisher.notifierDemandeFormationAnnulee(demandeur, "Excel", "FOR-202611-0001");
		publisher.notifierCollaborateurInviteFormation(demandeur, "Excel", "Org",
				java.time.LocalDate.of(2026, 11, 25), java.time.LocalDate.of(2026, 11, 27), "FOR-202611-0001");

		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate, org.mockito.Mockito.atLeast(15)).send(eq("rh.notifications"), anyString(), json.capture());
		assertThat(json.getAllValues()).isNotEmpty().allSatisfy(m -> {
			assertThat(m).doesNotContainPattern("\\b(RO|RRH|FIFO)\\b");
			assertThat(m).doesNotContainPattern("(?i)\\b(integree|integration|refusee|annulee|validee|precise|ete|superieur|trouve)\\b");
			assertThat(m).doesNotContainPattern("\\d{4}-\\d{2}-\\d{2}");
		});
		assertThat(String.join(" ", json.getAllValues()))
				.contains("Formation intégrée au plan").contains("Non précisé").contains("ordre d'arrivée")
				.contains("25/11/2026");
	}

	@Test
	@DisplayName("Type de demande inconnu : phrase propre, ni code ni espace avant le point")
	void typeInconnuPhraseePropre() {
		publisher.notifierAnnulationDemandeur(demandeur, "TYPE_INCONNU", chefService, "DEM-202611-0001");
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate).send(eq("rh.notifications"), anyString(), json.capture());
		assertThat(json.getValue()).contains("a annulé sa demande. [DEM-202611-0001]")
				.doesNotContain("TYPE_INCONNU").doesNotContain(" .");
	}

	@Test
	@DisplayName("Textes : type de formation en clair, document « disponible au téléchargement »")
	void typeFormationEnClairEtDocumentNeutre() {
		when(collaborateurRepository.findAllBackofficeEcritureActifs()).thenReturn(List.of(rh));
		publisher.notifierDemandeFormationSoumise(demandeur, "TECHNIQUE", "FOR-202611-0001");
		publisher.notifierDocumentDisponible(demandeur, "ATTESTATION_TRAVAIL", "x", "DOC-202611-0001");
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate, org.mockito.Mockito.times(2)).send(eq("rh.notifications"), anyString(), json.capture());
		String tout = String.join(" ", json.getAllValues());
		assertThat(tout).contains("formation : technique. Intégration").doesNotContain("TECHNIQUE")
				.contains("est disponible au téléchargement").doesNotContain("prêt à être");
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
