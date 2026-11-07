package com.hr.referentiel.service;

import com.hr.referentiel.config.ReferentielEvenementsProperties;
import com.hr.referentiel.domain.StatutDocumentAdministratifDemandeRh;
import com.hr.referentiel.domain.TypeDocumentAdministratifDemandeRh;
import com.hr.referentiel.dto.CheckRestrictionDocumentResponse;
import com.hr.referentiel.dto.DemandeDocumentAdministratifCreationRequest;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeDocumentAdministratifRh;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.DemandeDocumentAdministratifRhRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-F06 : une seule demande ouverte par collaborateur et par type de document. */
@ExtendWith(MockitoExtension.class)
class DemandeDocumentRestrictionServiceTest {

	@Mock
	private DemandeDocumentAdministratifRhRepository repository;
	@Mock
	private CollaborateurConnecteService collaborateurConnecteService;
	@Mock
	private ReferentielEvenementsProperties evenementsProperties;
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
		when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(demandeur);
	}

	private DemandeDocumentAdministratifRh ouverte(TypeDocumentAdministratifDemandeRh type) {
		DemandeDocumentAdministratifRh d = new DemandeDocumentAdministratifRh();
		d.setDemandeur(demandeur);
		d.setTypeDocument(type);
		d.setStatut(StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE);
		d.setCreeLe(Instant.parse("2026-11-04T10:00:00Z"));
		return d;
	}

	private void demandeOuverteExiste(TypeDocumentAdministratifDemandeRh type, DemandeDocumentAdministratifRh d) {
		when(repository.findFirstByDemandeurIdAndTypeDocumentAndStatutInOrderByCreeLeAsc(
				eq(demandeur.getId()), eq(type), eq(DemandeDocumentAdministratifRhService.STATUTS_OUVERTS)))
				.thenReturn(Optional.ofNullable(d));
	}

	@Test
	void checkRestriction_demandeOuverte_canRequestFalseAvecMessageEtDate() {
		demandeOuverteExiste(TypeDocumentAdministratifDemandeRh.ATTESTATION_TRAVAIL,
				ouverte(TypeDocumentAdministratifDemandeRh.ATTESTATION_TRAVAIL));

		CheckRestrictionDocumentResponse r = service.verifierRestriction("ATTESTATION_TRAVAIL", jwt);

		assertFalse(r.canRequest());
		assertEquals("Vous avez déjà une demande de attestation de travail en cours (déposée le 04/11/2026).",
				r.message());
	}

	@Test
	void checkRestriction_aucuneOuverte_canRequestTrue() {
		demandeOuverteExiste(TypeDocumentAdministratifDemandeRh.ATTESTATION_TRAVAIL, null);

		CheckRestrictionDocumentResponse r = service.verifierRestriction("ATTESTATION_TRAVAIL", jwt);

		assertTrue(r.canRequest());
		assertNull(r.message());
	}

	@Test
	void checkRestriction_typeInconnuOuAbsent_400() {
		assertThrows(IllegalArgumentException.class, () -> service.verifierRestriction("INCONNU", jwt));
		assertThrows(IllegalArgumentException.class, () -> service.verifierRestriction(null, jwt));
	}

	@Test
	void soumettre_memeTypeDejaOuvert_409_rienNestCree() {
		demandeOuverteExiste(TypeDocumentAdministratifDemandeRh.ATTESTATION_TRAVAIL,
				ouverte(TypeDocumentAdministratifDemandeRh.ATTESTATION_TRAVAIL));
		DemandeDocumentAdministratifCreationRequest req = new DemandeDocumentAdministratifCreationRequest();
		req.setTypeDocument(TypeDocumentAdministratifDemandeRh.ATTESTATION_TRAVAIL);

		ReferentielMetierException ex = assertThrows(ReferentielMetierException.class,
				() -> service.soumettre(req, jwt));

		assertEquals(409, ex.getHttpStatus());
		assertEquals("DEMANDE_DOCUMENT_DEJA_EN_COURS", ex.getCode());
		verify(collaborateurConnecteService).verrouiller(demandeur);
		verify(repository, never()).save(any());
	}

	@Test
	void soumettre_aucuneOuverteDeCeType_cree() {
		demandeOuverteExiste(TypeDocumentAdministratifDemandeRh.AUTRE, null);
		when(evenementsProperties.delaiSlaHeuresPourDocument(any(), anyInt())).thenReturn(120);
		when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
		DemandeDocumentAdministratifCreationRequest req = new DemandeDocumentAdministratifCreationRequest();
		req.setTypeDocument(TypeDocumentAdministratifDemandeRh.AUTRE);

		service.soumettre(req, jwt);

		verify(repository).save(any(DemandeDocumentAdministratifRh.class));
	}
}
