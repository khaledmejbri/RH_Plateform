package com.hr.presence.service;

import com.hr.presence.config.PresenceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-P05 (SCRUM-39) : fenêtre de validité du QR de borne écran.
 */
class QrDynamiqueServiceTest {

	private QrDynamiqueService service;
	private final UUID siteId = UUID.randomUUID();
	private final Instant t0 = Instant.parse("2026-10-27T09:00:10Z");

	@BeforeEach
	void setUp() {
		PresenceProperties props = new PresenceProperties();
		props.getQr().setHmacSecret("test-secret");
		service = new QrDynamiqueService(props);
	}

	private String qrA(Instant instant) {
		return service.construire(siteId, 3, QrDynamiqueService.fenetre(instant));
	}

	@Test
	void fenetreCourante_valide() {
		QrDynamiqueService.Resultat r = service.verifier(qrA(t0), t0.plusSeconds(5));

		assertThat(r.verdict()).isEqualTo(QrDynamiqueService.Verdict.VALIDE);
		assertThat(r.siteId()).isEqualTo(siteId);
		assertThat(r.borneVersion()).isEqualTo(3);
	}

	@Test
	void fenetreSuivante_toleree() {
		assertThat(service.verifier(qrA(t0), t0.plusSeconds(30)).verdict())
				.isEqualTo(QrDynamiqueService.Verdict.VALIDE);
	}

	@Test
	void deuxFenetresPlusTard_expire() {
		assertThat(service.verifier(qrA(t0), t0.plusSeconds(65)).verdict())
				.isEqualTo(QrDynamiqueService.Verdict.EXPIRE);
	}

	@Test
	void photoScanneeDeuxMinutesPlusTard_expire() {
		assertThat(service.verifier(qrA(t0), t0.plusSeconds(120)).verdict())
				.isEqualTo(QrDynamiqueService.Verdict.EXPIRE);
	}

	@Test
	void signatureAlteree_invalide() {
		String qr = qrA(t0);
		String altere = qr.substring(0, qr.length() - 2) + (qr.endsWith("A") ? "BB" : "AA");

		assertThat(service.verifier(altere, t0).verdict()).isEqualTo(QrDynamiqueService.Verdict.INVALIDE);
	}

	@Test
	void autreSiteForge_invalide() {
		String qr = qrA(t0);
		String forge = qr.replace(siteId.toString(), UUID.randomUUID().toString());

		assertThat(service.verifier(forge, t0).verdict()).isEqualTo(QrDynamiqueService.Verdict.INVALIDE);
	}

	@Test
	void qrPapier_nestPasDynamique() {
		assertThat(QrDynamiqueService.estDynamique("p1.abc.def")).isFalse();
		assertThat(QrDynamiqueService.estDynamique(qrA(t0))).isTrue();
	}

	@Test
	void secondesRestantes() {
		assertThat(QrDynamiqueService.secondesRestantes(t0)).isEqualTo(20);
	}
}
