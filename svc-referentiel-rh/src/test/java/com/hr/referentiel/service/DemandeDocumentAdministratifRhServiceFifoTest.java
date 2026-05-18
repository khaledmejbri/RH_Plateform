package com.hr.referentiel.service;

import com.hr.referentiel.config.ReferentielEvenementsProperties;
import com.hr.referentiel.domain.StatutDocumentAdministratifDemandeRh;
import com.hr.referentiel.dto.DemandeDocumentAdministratifRhResponse;
import com.hr.referentiel.dto.DemandeDocumentDisponibleRequest;
import com.hr.referentiel.dto.DocumentRejetRhRequest;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeDocumentAdministratifRh;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.DemandeDocumentAdministratifRhRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour la validation FIFO dans {@link DemandeDocumentAdministratifRhService}.
 *
 * <p>Scénarios couverts :</p>
 * <ul>
 *   <li>Traitement autorisé si la demande est la plus ancienne en attente</li>
 *   <li>Traitement autorisé si aucune demande en attente</li>
 *   <li>Traitement bloqué si demande hors ordre FIFO sans justification</li>
 *   <li>Traitement autorisé si demande hors ordre FIFO avec justification</li>
 *   <li>Justification vide ou blanche bloque le traitement</li>
 *   <li>Validation FIFO intégrée dans marquerDisponible()</li>
 *   <li>Validation FIFO intégrée dans rejeter()</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DemandeDocumentAdministratifRhServiceFifoTest {

	@Mock
	private DemandeDocumentAdministratifRhRepository repository;

	@Mock
	private CollaborateurConnecteService collaborateurConnecteService;

	@Mock
	private ReferentielEvenementsProperties evenementsProperties;

	@Mock
	private RhNotificationPublisher notificationPublisher;

	@InjectMocks
	private DemandeDocumentAdministratifRhService service;

	private DemandeDocumentAdministratifRh demandeA; // oldest
	private DemandeDocumentAdministratifRh demandeB; // newer
	private DemandeDocumentAdministratifRh demandeC; // newest
	private Collaborateur demandeur;

	@BeforeEach
	void setUp() {
		demandeur = new Collaborateur();
		demandeur.setId(UUID.randomUUID());
		demandeur.setPrenom("Jean");
		demandeur.setNom("Dupont");
		demandeur.setStatut("ACTIF");

		Instant base = Instant.now().minus(3, ChronoUnit.HOURS);

		demandeA = creerDemande(base);                                   // 08:00 — oldest
		demandeB = creerDemande(base.plus(10, ChronoUnit.MINUTES));     // 08:10
		demandeC = creerDemande(base.plus(20, ChronoUnit.MINUTES));     // 08:20
	}

	private DemandeDocumentAdministratifRh creerDemande(Instant creeLe) {
		DemandeDocumentAdministratifRh d = new DemandeDocumentAdministratifRh();
		d.setId(UUID.randomUUID());
		d.setDemandeur(demandeur);
		d.setStatut(StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE);
		d.setCreeLe(creeLe);
		d.setDelaiSlaHeures(24);
		d.setDateEcheanceTraitement(creeLe.plus(24, ChronoUnit.HOURS));
		return d;
	}

	// ─── validerOrdreFifo() direct tests ─────────────────────────────────────

	@Nested
	@DisplayName("validerOrdreFifo()")
	class ValiderOrdreFifoTests {

		@Test
		@DisplayName("✅ Autorisé — la demande courante est la plus ancienne en attente")
		void autorise_quandDemandeEstLaPlusAncienne() {
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.of(demandeA));

			// Traiter demandeA (la plus ancienne) → OK, pas d'exception
			assertDoesNotThrow(() -> service.validerOrdreFifo(demandeA, null, null));
		}

		@Test
		@DisplayName("✅ Autorisé — aucune demande en attente dans la file")
		void autorise_quandAucuneDemandeEnAttente() {
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.empty());

			assertDoesNotThrow(() -> service.validerOrdreFifo(demandeB, null, null));
		}

		@Test
		@DisplayName("❌ Bloqué — demande hors ordre FIFO sans justification")
		void bloque_quandHorsOrdreFifoSansJustification() {
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.of(demandeA));

			// Traiter demandeB (pas la plus ancienne) sans justification → blocage
			IllegalStateException ex = assertThrows(
					IllegalStateException.class,
					() -> service.validerOrdreFifo(demandeB, null, null)
			);
			assertTrue(ex.getMessage().contains("justification"));
		}

		@Test
		@DisplayName("❌ Bloqué — demande hors ordre FIFO avec justification vide")
		void bloque_quandJustificationVide() {
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.of(demandeA));

			IllegalStateException ex = assertThrows(
					IllegalStateException.class,
					() -> service.validerOrdreFifo(demandeC, "", null)
			);
			assertTrue(ex.getMessage().contains("justification"));
		}

		@Test
		@DisplayName("❌ Bloqué — demande hors ordre FIFO avec justification blanche (espaces)")
		void bloque_quandJustificationBlanche() {
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.of(demandeA));

			IllegalStateException ex = assertThrows(
					IllegalStateException.class,
					() -> service.validerOrdreFifo(demandeC, "   ", null)
			);
			assertTrue(ex.getMessage().contains("justification"));
		}

		@Test
		@DisplayName("✅ Autorisé — demande hors ordre FIFO avec justification valide")
		void autorise_quandJustificationFournie() {
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.of(demandeA));

			String justification = "Demande urgente du PDG";

			// Traiter demandeC hors ordre mais avec justification → OK
			assertDoesNotThrow(() -> service.validerOrdreFifo(demandeC, justification, null));

			// La justification doit être enregistrée sur l'entité
			assertEquals(justification, demandeC.getJustificationDerogationFifo());
		}

		@Test
		@DisplayName("✅ Justification est trim() avant enregistrement")
		void justificationEstTrimmee() {
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.of(demandeA));

			service.validerOrdreFifo(demandeC, "  Contrat requis aujourd'hui  ", null);

			assertEquals("Contrat requis aujourd'hui", demandeC.getJustificationDerogationFifo());
		}
	}

	// ─── Integration with marquerDisponible() ─────────────────────────────────

	@Nested
	@DisplayName("marquerDisponible() — FIFO intégrée")
	class MarquerDisponibleFifoTests {

		@Test
		@DisplayName("❌ marquerDisponible bloqué si hors FIFO sans justification")
		void marquerDisponible_bloqueHorsFifoSansJustification() {
			// demandeB is EN_TRAITEMENT_RH but demandeA is still EN_ATTENTE_FILE
			demandeB.setStatut(StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH);

			when(repository.findById(demandeB.getId())).thenReturn(Optional.of(demandeB));
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.of(demandeA));

			DemandeDocumentDisponibleRequest req = new DemandeDocumentDisponibleRequest();
			req.setReferenceLivrable("REF-001");
			req.setJustificationDerogationFifo(null);

			assertThrows(IllegalStateException.class,
					() -> service.marquerDisponible(demandeB.getId(), req, null));

			// save should NOT have been called
			verify(repository, never()).save(any());
		}

		@Test
		@DisplayName("✅ marquerDisponible autorisé si hors FIFO avec justification")
		void marquerDisponible_autoriseAvecJustification() {
			demandeB.setStatut(StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH);

			when(repository.findById(demandeB.getId())).thenReturn(Optional.of(demandeB));
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.of(demandeA));
			when(repository.save(any())).thenReturn(demandeB);
			when(repository.countByStatutAndCreeLeBefore(any(), any())).thenReturn(0L);

			DemandeDocumentDisponibleRequest req = new DemandeDocumentDisponibleRequest();
			req.setReferenceLivrable("REF-001");
			req.setJustificationDerogationFifo("Contrat requis aujourd'hui");

			DemandeDocumentAdministratifRhResponse resp = service.marquerDisponible(demandeB.getId(), req, null);

			assertNotNull(resp);
			verify(repository).save(demandeB);
			assertEquals("Contrat requis aujourd'hui", demandeB.getJustificationDerogationFifo());
		}

		@Test
		@DisplayName("✅ marquerDisponible autorisé si demande est la prochaine en FIFO")
		void marquerDisponible_autorisePremiereFifo() {
			demandeA.setStatut(StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH);

			when(repository.findById(demandeA.getId())).thenReturn(Optional.of(demandeA));
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.empty()); // No more pending after demandeA taken
			when(repository.save(any())).thenReturn(demandeA);
			when(repository.countByStatutAndCreeLeBefore(any(), any())).thenReturn(0L);

			DemandeDocumentDisponibleRequest req = new DemandeDocumentDisponibleRequest();
			req.setReferenceLivrable("REF-002");

			assertDoesNotThrow(() -> service.marquerDisponible(demandeA.getId(), req, null));
			verify(repository).save(demandeA);
		}
	}

	// ─── Integration with rejeter() ───────────────────────────────────────────

	@Nested
	@DisplayName("rejeter() — FIFO intégrée")
	class RejeterFifoTests {

		@Test
		@DisplayName("❌ rejeter bloqué si hors FIFO sans justification")
		void rejeter_bloqueHorsFifoSansJustification() {
			when(repository.findById(demandeC.getId())).thenReturn(Optional.of(demandeC));
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.of(demandeA));

			DocumentRejetRhRequest req = new DocumentRejetRhRequest();
			req.setMotif("Document incorrect");
			req.setJustificationDerogationFifo(null);

			assertThrows(IllegalStateException.class,
					() -> service.rejeter(demandeC.getId(), req, null));

			verify(repository, never()).save(any());
		}

		@Test
		@DisplayName("✅ rejeter autorisé si hors FIFO avec justification")
		void rejeter_autoriseAvecJustification() {
			when(repository.findById(demandeC.getId())).thenReturn(Optional.of(demandeC));
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.of(demandeA));
			when(repository.save(any())).thenReturn(demandeC);
			when(repository.countByStatutAndCreeLeBefore(any(), any())).thenReturn(0L);

			DocumentRejetRhRequest req = new DocumentRejetRhRequest();
			req.setMotif("Document incorrect");
			req.setJustificationDerogationFifo("Besoin administratif critique");

			DemandeDocumentAdministratifRhResponse resp = service.rejeter(demandeC.getId(), req, null);

			assertNotNull(resp);
			verify(repository).save(demandeC);
			assertEquals("Besoin administratif critique", demandeC.getJustificationDerogationFifo());
		}

		@Test
		@DisplayName("✅ rejeter autorisé si demande est la plus ancienne")
		void rejeter_autorisePremiereFifo() {
			when(repository.findById(demandeA.getId())).thenReturn(Optional.of(demandeA));
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.of(demandeA));
			when(repository.save(any())).thenReturn(demandeA);
			when(repository.countByStatutAndCreeLeBefore(any(), any())).thenReturn(0L);

			DocumentRejetRhRequest req = new DocumentRejetRhRequest();
			req.setMotif("Demande invalide");

			assertDoesNotThrow(() -> service.rejeter(demandeA.getId(), req, null));
			verify(repository).save(demandeA);
		}
	}

	// ─── Edge Cases ──────────────────────────────────────────────────────────

	@Nested
	@DisplayName("Cas limites")
	class EdgeCases {

		@Test
		@DisplayName("✅ Seule demande en attente → traitement autorisé sans justification")
		void seuleDemandeEnAttente() {
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.of(demandeA));

			// demandeA is both the current and the oldest → OK
			assertDoesNotThrow(() -> service.validerOrdreFifo(demandeA, null, null));
		}

		@Test
		@DisplayName("✅ Demande EN_TRAITEMENT_RH avec d'autres EN_ATTENTE mais même ID → autorisé")
		void demandeEnTraitementMaisIdCorrespondALaPlusAncienne() {
			demandeA.setStatut(StatutDocumentAdministratifDemandeRh.EN_TRAITEMENT_RH);

			// The oldest pending is different but demandeA's ID matches oldest
			// This simulates the case where demandeA was taken from queue but there's
			// another pending — we check against EN_ATTENTE_FILE, so if no older pending exists, it's fine
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.of(demandeB)); // demandeB is now oldest pending

			// demandeA (EN_TRAITEMENT_RH) != demandeB (oldest pending)
			// But demandeA needs justification since it's not the oldest pending
			assertThrows(IllegalStateException.class,
					() -> service.validerOrdreFifo(demandeA, null, null));
		}

		@Test
		@DisplayName("❌ Message d'erreur contient le texte attendu")
		void messageErreurContientTexteAttendu() {
			when(repository.findFirstByStatutOrderByCreeLeAsc(
					StatutDocumentAdministratifDemandeRh.EN_ATTENTE_FILE))
					.thenReturn(Optional.of(demandeA));

			IllegalStateException ex = assertThrows(
					IllegalStateException.class,
					() -> service.validerOrdreFifo(demandeB, null, null)
			);

			assertEquals(DemandeDocumentAdministratifRhService.MSG_VIOLATION_FIFO, ex.getMessage());
		}
	}
}
