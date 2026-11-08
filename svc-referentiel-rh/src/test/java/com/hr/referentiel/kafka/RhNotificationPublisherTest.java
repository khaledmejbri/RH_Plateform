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

	private static final java.time.Instant INSTANT = java.time.Instant.parse("2026-11-08T10:15:30Z");
	private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

	private RhNotificationPublisher publisher;
	private Collaborateur demandeur;
	private Collaborateur chefService;
	private Collaborateur rh;
	private Collaborateur admin;

	@BeforeEach
	void setUp() {
		publisher = new RhNotificationPublisher(kafkaTemplate, new ObjectMapper(), collaborateurRepository,
				java.time.Clock.fixed(INSTANT, java.time.ZoneOffset.UTC));
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
		publisher.notifierDemandeRecue(demandeur, "CONGE", ID, "DEM-202611-0001", chefService);

		verify(kafkaTemplate).send(eq("rh.notifications"), eq(chefService.getCompteUtilisateurId().toString()), anyString());
	}

	@Test
	@DisplayName("SCRUM-29 : étape RRH notifiée aux RH et ADMIN, sans le demandeur")
	void validationRoAuBackofficeSaufDemandeur() {
		when(collaborateurRepository.findAllBackofficeEcritureActifs()).thenReturn(List.of(rh, admin));

		publisher.notifierValidationRo(rh, "CONGE", chefService, ID, "DEM-202611-0001");

		ArgumentCaptor<String> cles = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate).send(eq("rh.notifications"), cles.capture(), anyString());
		assertThat(cles.getAllValues()).containsExactly(admin.getCompteUtilisateurId().toString());
	}

	@Test
	@DisplayName("SCRUM-28 : l'annulation prévient le valideur retenu, rien si aucun valideur")
	void annulationAuValideur() {
		publisher.notifierAnnulationDemandeur(demandeur, "CONGE", null, ID, "DEM-202611-0001");
		verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());

		publisher.notifierAnnulationDemandeur(demandeur, "CONGE", chefService, ID, "DEM-202611-0001");
		verify(kafkaTemplate).send(eq("rh.notifications"), eq(chefService.getCompteUtilisateurId().toString()), anyString());
	}

	@Test
	@DisplayName("D-L02 : le numéro est un champ JSON, sans suffixe entre crochets dans le texte ; absent s'il n'y en a pas")
	void numeroDansLeMessage() {
		publisher.notifierApprobationRrh(demandeur, "CONGE", ID, "DEM-202611-0003");
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate).send(eq("rh.notifications"), anyString(), json.capture());
		assertThat(json.getValue()).contains("\"numero\":\"DEM-202611-0003\"").doesNotContain("[");

		publisher.notifierApprobationRrh(demandeur, "CONGE", ID, null);
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
		publisher.notifierDemandeRecue(demandeur, "CONGE", ID, n, chefService);
		publisher.notifierDemandeRecue(demandeur, "CONGE", ID, n, null);
		publisher.notifierDemandeRecue(sansUnite, "CONGE", ID, n, null);
		publisher.notifierValidationRo(demandeur, "CONGE", chefService, ID, n);
		publisher.notifierRefusRo(demandeur, "CONGE", null, ID, n);
		publisher.notifierApprobationRrh(demandeur, "CONGE", ID, n);
		publisher.notifierRefusRrh(demandeur, "CONGE", null, ID, n);
		publisher.notifierAnnulationDemandeur(demandeur, "CONGE", chefService, ID, n);
		publisher.notifierDemandeDocumentRecue(demandeur, "ATTESTATION_TRAVAIL", ID, "DOC-202611-0001");
		publisher.notifierDocumentDisponible(demandeur, "ATTESTATION_TRAVAIL", "x", ID, "DOC-202611-0001");
		publisher.notifierDocumentRejete(demandeur, "ATTESTATION_TRAVAIL", null, ID, "DOC-202611-0001");
		publisher.notifierDerogationFifo(rh, "ATTESTATION_TRAVAIL", "urgence", demandeur, ID, "DOC-202611-0001");
		publisher.notifierPlainteExterne(demandeur, ID, "PLT-202611-0001");
		publisher.notifierValidationRoPlainteExterne(demandeur, ID, "PLT-202611-0001", chefService);
		publisher.notifierDemandeFormationSoumise(demandeur, "Excel", ID, "FOR-202611-0001");
		publisher.notifierDemandeFormationIntegree(demandeur, "Excel", ID, "FOR-202611-0001");
		publisher.notifierDemandeFormationRefusee(demandeur, "Excel", null, ID, "FOR-202611-0001");
		publisher.notifierDemandeFormationAnnulee(demandeur, "Excel", ID, "FOR-202611-0001");
		publisher.notifierCollaborateurInviteFormation(demandeur, "Excel", "Org",
				java.time.LocalDate.of(2026, 11, 25), java.time.LocalDate.of(2026, 11, 27), ID, "FOR-202611-0001");

		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate, org.mockito.Mockito.atLeast(15)).send(eq("rh.notifications"), anyString(), json.capture());
		assertThat(textes(json.getAllValues())).isNotEmpty().allSatisfy(m -> {
			assertThat(m).doesNotContainPattern("\\b(RO|RRH|FIFO)\\b");
			assertThat(m).doesNotContainPattern("(?i)\\b(integree|integration|refusee|annulee|validee|precise|ete|superieur|trouve)\\b");
			assertThat(m).doesNotContainPattern("\\d{4}-\\d{2}-\\d{2}");
			// 2F : plus de suffixe entre crochets, numéro lisible au plus une fois
			assertThat(m).doesNotContain("[").doesNotContain("]");
			assertThat(m).doesNotContainPattern("(?s)(DEM|DOC|FOR|PLT)-\\d{6}-\\d{4}.*\\1-\\d{6}-\\d{4}");
		});
		assertThat(String.join(" ", textes(json.getAllValues())))
				.contains("Formation intégrée au plan").contains("Non précisé").contains("ordre d'arrivée")
				.contains("25/11/2026");
	}

	@Test
	@DisplayName("Type de demande inconnu : phrase propre, ni code ni espace avant le point")
	void typeInconnuPhraseePropre() {
		publisher.notifierAnnulationDemandeur(demandeur, "TYPE_INCONNU", chefService, ID, "DEM-202611-0001");
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate).send(eq("rh.notifications"), anyString(), json.capture());
		assertThat(json.getValue()).contains("a annulé sa demande.")
				.doesNotContain("TYPE_INCONNU").doesNotContain(" .");
	}

	@Test
	@DisplayName("Textes : type de formation en clair, document « disponible au téléchargement »")
	void typeFormationEnClairEtDocumentNeutre() {
		when(collaborateurRepository.findAllBackofficeEcritureActifs()).thenReturn(List.of(rh));
		publisher.notifierDemandeFormationSoumise(demandeur, "TECHNIQUE", ID, "FOR-202611-0001");
		publisher.notifierDocumentDisponible(demandeur, "ATTESTATION_TRAVAIL", "x", ID, "DOC-202611-0001");
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate, org.mockito.Mockito.times(2)).send(eq("rh.notifications"), anyString(), json.capture());
		String tout = String.join(" ", textes(json.getAllValues()));
		assertThat(tout).contains("demande de formation technique. Intégration").doesNotContain("TECHNIQUE")
				.contains("est disponible au téléchargement").doesNotContain("prêt à être");
	}

	/** Sujet + contenu seulement : les champs structurés (UUID, date ISO, codes) ne sont pas du texte affiché. */
	private static List<String> textes(List<String> jsons) {
		return jsons.stream().map(j -> {
			com.fasterxml.jackson.databind.JsonNode n = json(j);
			return n.get("subject").asText() + " " + n.get("content").asText();
		}).toList();
	}

	private static com.fasterxml.jackson.databind.JsonNode json(String j) {
		try {
			return new ObjectMapper().readTree(j);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	@DisplayName("2F : demande, document, formation, plainte portent objet_type, objet_id, objet_numero, evenement, emis_le")
	void champsStructures() {
		publisher.notifierApprobationRrh(demandeur, "CONGE", ID, "DEM-202611-0003");
		publisher.notifierDocumentDisponible(demandeur, "ATTESTATION_TRAVAIL", "x", ID, "DOC-202611-0001");
		publisher.notifierDemandeFormationIntegree(demandeur, "Excel", ID, "FOR-202611-0001");
		publisher.notifierChangementStatutPlainte(demandeur, ID, "PLT-202611-0001", "NOUVEAU", "EN_ANALYSE", null);
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate, org.mockito.Mockito.times(4)).send(eq("rh.notifications"), anyString(), json.capture());
		String[][] attendu = {
				{"DEMANDE", "DEMANDE_APPROUVEE", "DEM-202611-0003"},
				{"DOCUMENT", "DOCUMENT_DISPONIBLE", "DOC-202611-0001"},
				{"FORMATION", "FORMATION_INTEGREE", "FOR-202611-0001"},
				{"PLAINTE", "PLAINTE_STATUT_CHANGE", "PLT-202611-0001"}};
		for (int i = 0; i < 4; i++) {
			com.fasterxml.jackson.databind.JsonNode n = json(json.getAllValues().get(i));
			assertThat(n.get("objet_type").asText()).isEqualTo(attendu[i][0]);
			assertThat(n.get("evenement").asText()).isEqualTo(attendu[i][1]);
			assertThat(n.get("objet_numero").asText()).isEqualTo(attendu[i][2]);
			assertThat(n.get("objet_id").asText()).isEqualTo(ID.toString());
			assertThat(n.get("emis_le").asText()).isEqualTo("2026-11-08T10:15:30Z");
			assertThat(UUID.fromString(n.get("notification_id").asText())).isNotNull();
			assertThat(n.get("subject").asText() + n.get("content").asText()).doesNotContain(ID.toString());
		}
	}

	@Test
	@DisplayName("2F : un événement diffusé à plusieurs RH partage un seul notification_id ; deux événements en ont deux")
	void notificationIdParEvenement() {
		when(collaborateurRepository.findAllBackofficeEcritureActifs()).thenReturn(List.of(rh, admin));
		publisher.notifierValidationRo(demandeur, "CONGE", chefService, ID, "DEM-202611-0001");
		publisher.notifierValidationRo(demandeur, "CONGE", chefService, ID, "DEM-202611-0001");
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate, org.mockito.Mockito.times(4)).send(eq("rh.notifications"), anyString(), json.capture());
		List<String> ids = json.getAllValues().stream().map(j -> json(j).get("notification_id").asText()).toList();
		assertThat(ids.get(0)).isEqualTo(ids.get(1));
		assertThat(ids.get(2)).isEqualTo(ids.get(3));
		assertThat(ids.get(0)).isNotEqualTo(ids.get(2));
	}

	@Test
	@DisplayName("2F : sans numéro, objet_numero est absent mais objet_type et objet_id restent")
	void sansNumero() {
		publisher.notifierApprobationRrh(demandeur, "CONGE", ID, null);
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(kafkaTemplate).send(eq("rh.notifications"), anyString(), json.capture());
		assertThat(json(json.getValue()).has("objet_numero")).isFalse();
		assertThat(json(json.getValue()).get("objet_type").asText()).isEqualTo("DEMANDE");
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
