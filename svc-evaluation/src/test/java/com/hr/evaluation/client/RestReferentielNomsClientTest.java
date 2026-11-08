package com.hr.evaluation.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrat avec le référentiel et délai d'attente de lecture configurable (ADR lot 2 § 1). */
class RestReferentielNomsClientTest {

	private HttpServer serveur;
	private volatile long latenceMs;
	private final AtomicReference<String> jeton = new AtomicReference<>();
	private final UUID fiche = UUID.randomUUID();

	@BeforeEach
	void demarrer() throws Exception {
		serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		serveur.createContext("/internal/referentiel/collaborateurs/affectations", ech -> {
			try {
				Thread.sleep(latenceMs);
			} catch (InterruptedException ignore) {
				Thread.currentThread().interrupt();
			}
			jeton.set(ech.getRequestHeaders().getFirst("X-Internal-Token"));
			byte[] corps = ("[{\"collaborateur_id\":\"" + fiche + "\",\"matricule\":\"M1\",\"nom\":\"Sarra Khemiri\","
					+ "\"actif\":true,\"poste_libelle\":\"Dev\",\"champ_inconnu\":1}]").getBytes(StandardCharsets.UTF_8);
			ech.getResponseHeaders().add("Content-Type", "application/json");
			try {
				ech.sendResponseHeaders(200, corps.length);
				ech.getResponseBody().write(corps);
			} catch (java.io.IOException ignore) {
				// client parti après son délai d'attente
			}
			ech.close();
		});
		serveur.start();
	}

	@AfterEach
	void arreter() {
		serveur.stop(0);
	}

	private RestReferentielNomsClient client(int lectureMs) {
		return new RestReferentielNomsClient("http://127.0.0.1:" + serveur.getAddress().getPort(), "jeton-test", 500,
				lectureMs);
	}

	@Test
	void lit_nomMatriculeEtChampsFacultatifs_avecLeJetonInterne() {
		List<PersonneResumee> lus = client(2000).resoudre(List.of(fiche));
		assertThat(lus).hasSize(1);
		assertThat(lus.get(0).nomComplet()).isEqualTo("Sarra Khemiri");
		assertThat(lus.get(0).matricule()).isEqualTo("M1");
		assertThat(lus.get(0).posteLibelle()).isEqualTo("Dev");
		assertThat(jeton.get()).isEqualTo("jeton-test");
	}

	@Test
	void referentielTropLent_delaiDeLectureDepasse_exception() {
		latenceMs = 1500;
		assertThatThrownBy(() -> client(300).resoudre(List.of(fiche))).isInstanceOf(RuntimeException.class);
	}
}
