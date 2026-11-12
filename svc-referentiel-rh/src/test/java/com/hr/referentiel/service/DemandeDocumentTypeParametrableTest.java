package com.hr.referentiel.service;

import com.hr.referentiel.domain.NatureParametre;
import com.hr.referentiel.dto.DemandeDocumentAdministratifCreationRequest;
import com.hr.referentiel.dto.DemandeDocumentAdministratifRhResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeDocumentAdministratifRh;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.DemandeDocumentAdministratifRhRepository;
import com.hr.referentiel.service.ParametresCache.ParametreVue;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ticket 3B : le type de document est un paramètre (SLA copié, inactif 422, inconnu 400, marque « utilisé »). */
@ExtendWith(MockitoExtension.class)
class DemandeDocumentTypeParametrableTest {

	@Mock
	private DemandeDocumentAdministratifRhRepository repository;
	@Mock
	private CollaborateurConnecteService collaborateurConnecteService;
	@Mock
	private ParametreService parametreService;
	@Mock
	private RhNotificationPublisher notificationPublisher;
	@Mock
	private NumeroLisibleService numeroLisibleService;
	@InjectMocks
	private DemandeDocumentAdministratifRhService service;

	private final Jwt jwt = mock(Jwt.class);
	private Collaborateur demandeur;

	@BeforeEach
	void setUp() {
		demandeur = new Collaborateur();
		demandeur.setId(UUID.randomUUID());
		demandeur.setPrenom("Sarra");
		demandeur.setNom("Khemiri");
		lenient().when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(demandeur);
		lenient().when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
		lenient().when(parametreService.libelleOuDefaut(eq("TYPE_DOCUMENT"), any())).thenReturn("Attestation de stage");
		lenient().when(parametreService.libelleOuInconnu(eq("STATUT_DOCUMENT"), any())).thenReturn("En file");
	}

	private ParametreVue type(boolean actif, Integer sla) {
		return new ParametreVue("TYPE_DOCUMENT", "ATTESTATION_STAGE", NatureParametre.VALEUR, "Attestation de stage",
				null, null, 8, actif, false, sla, null, null);
	}

	private DemandeDocumentAdministratifCreationRequest req(String code) {
		DemandeDocumentAdministratifCreationRequest r = new DemandeDocumentAdministratifCreationRequest();
		r.setTypeDocument(code);
		return r;
	}

	@Test
	@DisplayName("SLA 72 h lu dans le type : copié dans la demande, échéance = dépôt + 72 h, type marqué utilisé")
	void slaCopieALaCreation() {
		when(parametreService.lire("TYPE_DOCUMENT", "ATTESTATION_STAGE")).thenReturn(Optional.of(type(true, 72)));

		DemandeDocumentAdministratifRhResponse r = service.soumettre(req("ATTESTATION_STAGE"), jwt);

		assertThat(r.getDelaiSlaHeures()).isEqualTo(72);
		assertThat(Duration.between(r.getCreeLe(), r.getDateEcheanceTraitement())).isEqualTo(Duration.ofHours(72));
		assertThat(r.getTypeDocument()).isEqualTo("ATTESTATION_STAGE");
		assertThat(r.getTypeDocumentLibelle()).isEqualTo("Attestation de stage");
		assertThat(r.getStatutLibelle()).isEqualTo("En file");
		verify(parametreService).marquerUtilisee("TYPE_DOCUMENT", "ATTESTATION_STAGE");
		ArgumentCaptor<DemandeDocumentAdministratifRh> c = ArgumentCaptor.forClass(DemandeDocumentAdministratifRh.class);
		verify(repository).save(c.capture());
		Instant echeance = c.getValue().getDateEcheanceTraitement();

		// le SLA du type peut changer ensuite : la demande déposée garde sa copie (champ propre, pas de lien au type)
		assertThat(c.getValue().getDelaiSlaHeures()).isEqualTo(72);
		assertThat(c.getValue().getDateEcheanceTraitement()).isEqualTo(echeance);
	}

	@Test
	@DisplayName("type désactivé : 422 TYPE_DOCUMENT_INACTIF, rien n'est créé")
	void typeInactif() {
		when(parametreService.lire("TYPE_DOCUMENT", "ATTESTATION_STAGE")).thenReturn(Optional.of(type(false, 72)));

		ReferentielMetierException e = assertThrows(ReferentielMetierException.class,
				() -> service.soumettre(req("ATTESTATION_STAGE"), jwt));

		assertThat(e.getHttpStatus()).isEqualTo(422);
		assertThat(e.getCode()).isEqualTo("TYPE_DOCUMENT_INACTIF");
		verify(repository, never()).save(any());
	}

	@Test
	@DisplayName("type inconnu : 400 TYPE_DOCUMENT_INVALIDE")
	void typeInconnu() {
		when(parametreService.lire("TYPE_DOCUMENT", "NOPE")).thenReturn(Optional.empty());

		ReferentielMetierException e = assertThrows(ReferentielMetierException.class,
				() -> service.soumettre(req("NOPE"), jwt));

		assertThat(e.getHttpStatus()).isEqualTo(400);
		assertThat(e.getCode()).isEqualTo("TYPE_DOCUMENT_INVALIDE");
	}
}
