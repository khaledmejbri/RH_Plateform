package com.hr.referentiel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration bout-en-bout M01 ↔ {@code svc-workflow} réel (CA-14.2 à CA-14.5).
 * Nécessite les services locaux démarrés : gateway (8080), svc-identite-acces (8081),
 * svc-referentiel-rh (8083), svc-workflow (8087), et la variable d'environnement {@code DEMO_PW}.
 * Ignoré par assomption explicite si {@code svc-workflow} (8087) ou la gateway (8080) ne répond pas.
 */
class DemandeMoteurIT {

	private static final String GATEWAY = System.getProperty("sirh.gateway.url", "http://localhost:8080");
	private static final String WORKFLOW = System.getProperty("sirh.workflow.url", "http://localhost:8087");
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

	private static String demoPw;
	private static String jwtRh;
	private static String jwtColWeb;
	private static String jwtCsWeb;

	@BeforeAll
	static void verifierServicesDisponibles() throws Exception {
		Assumptions.assumeTrue(serviceUp(WORKFLOW + "/actuator/health"),
				"DemandeMoteurIT ignoré : svc-workflow réel (8087) ne répond pas sur " + WORKFLOW);
		Assumptions.assumeTrue(serviceUp(GATEWAY + "/actuator/health"),
				"DemandeMoteurIT ignoré : gateway (8080) ne répond pas sur " + GATEWAY);
		demoPw = System.getenv("DEMO_PW");
		Assumptions.assumeTrue(demoPw != null && !demoPw.isBlank(),
				"DemandeMoteurIT ignoré : variable d'environnement DEMO_PW absente");

		jwtRh = signin("drh01", demoPw);
		jwtColWeb = signin("col.web", demoPw);
		jwtCsWeb = signin("cs.web", demoPw);
	}

	@AfterEach
	void remettreActivationHerite() {
		if (jwtRh != null) {
			try {
				basculerActivation("CONGE", "HERITE", jwtRh);
			} catch (Exception ignored) {
				// nettoyage best effort
			}
		}
	}

	@Test
	void ca14_2_a_14_5_cycleCompletMoteurAncienneRoute409EtRetourHerite() throws Exception {
		// 1. La RH bascule CONGE en mode MOTEUR
		basculerActivation("CONGE", "MOTEUR", jwtRh);
		// Laisse le temps au cache 30 s éventuel de svc-referentiel-rh d'expirer seulement si nécessaire ;
		// en environnement propre (ou après démarrage), la première lecture lit MOTEUR.

		// 2. Solde avant création
		double acquisAvant = lireSoldeAcquis(jwtColWeb);

		// 3. COL-WEB crée un congé ANNUEL (dates futures uniques pour éviter tout chevauchement)
		LocalDate debutMoteur = LocalDate.of(2028, 3, 6); // lundi
		LocalDate finMoteur = LocalDate.of(2028, 3, 6);
		JsonNode creee = creerCongeAnnuel(jwtColWeb, debutMoteur, finMoteur, "[RECETTE-W5] Congé moteur IT");

		String demandeId = creee.get("identifiant").asText();
		assertThat(creee.get("statut").asText()).isEqualTo("EN_COURS");
		assertThat(creee.get("workflow_mode").asText()).isEqualTo("MOTEUR");
		assertThat(creee.get("workflow_instance_id").isNull()).isFalse();
		String instanceId = creee.get("workflow_instance_id").asText();

		// 4. CA-14.3 : ancienne route valider-superieur sur demande MOTEUR → 409 DEMANDE_SOUS_WORKFLOW
		HttpResponse<String> ancienneRoute = post(
				GATEWAY + "/api/rh/v1/demandes-administratives/" + demandeId + "/valider-superieur",
				jwtCsWeb, "{}");
		assertThat(ancienneRoute.statusCode()).isEqualTo(409);
		JsonNode err409 = MAPPER.readTree(ancienneRoute.body());
		assertThat(err409.get("code").asText()).isEqualTo("DEMANDE_SOUS_WORKFLOW");
		assertThat(err409.get("message").asText()).isEqualTo("Cette demande se traite depuis Mes tâches.");

		// 5. CA-14.5 : retour CONGE → HERITE pendant qu'une instance est en cours
		basculerActivation("CONGE", "HERITE", jwtRh);

		// 6. CS-WEB approuve sa tâche chez le moteur → étape suivante RH
		String tacheRo = trouverTachePourInstance(jwtCsWeb, instanceId);
		assertThat(tacheRo).isNotNull();
		deciderTache(jwtCsWeb, tacheRo, "APPROUVER", "[RECETTE-W5] OK RO");

		// 7. RH approuve sa tâche chez le moteur → effet APPROUVER → demande APPROUVEE, solde débité une fois
		String tacheRh = trouverTachePourInstance(jwtRh, instanceId);
		assertThat(tacheRh).isNotNull();
		deciderTache(jwtRh, tacheRh, "APPROUVER", "[RECETTE-W5] OK RH");

		JsonNode apres = getJson(GATEWAY + "/api/rh/v1/demandes-administratives/" + demandeId, jwtColWeb);
		assertThat(apres.get("statut").asText()).isEqualTo("APPROUVEE");
		assertThat(apres.get("workflow_mode").asText()).isEqualTo("MOTEUR");

		double acquisApres = lireSoldeAcquis(jwtColWeb);
		assertThat(acquisAvant - acquisApres).isEqualTo(1.0);
	}

	// --- Helpers HTTP ---

	private static boolean serviceUp(String url) {
		try {
			HttpResponse<String> r = HTTP.send(
					HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			return r.statusCode() == 200;
		} catch (Exception e) {
			return false;
		}
	}

	private static String signin(String login, String password) throws Exception {
		String body = MAPPER.writeValueAsString(Map.of("username", login, "password", password));
		HttpResponse<String> r = HTTP.send(
				HttpRequest.newBuilder(URI.create(GATEWAY + "/api/auth/signin"))
						.header("Content-Type", "application/json")
						.timeout(Duration.ofSeconds(5))
						.POST(HttpRequest.BodyPublishers.ofString(body))
						.build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(r.statusCode()).as("Connexion %s", login).isEqualTo(200);
		JsonNode root = MAPPER.readTree(r.body());
		return root.has("token") ? root.get("token").asText() : root.get("accessToken").asText();
	}

	private static void basculerActivation(String objetType, String mode, String jwt) throws Exception {
		String body = MAPPER.writeValueAsString(Map.of("mode", mode));
		HttpResponse<String> r = HTTP.send(
				HttpRequest.newBuilder(URI.create(GATEWAY + "/api/rh/v1/workflow/activations/" + objetType))
						.header("Content-Type", "application/json")
						.header("Authorization", "Bearer " + jwt)
						.timeout(Duration.ofSeconds(5))
						.PUT(HttpRequest.BodyPublishers.ofString(body))
						.build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(r.statusCode()).as("Activation %s -> %s", objetType, mode).isBetween(200, 204);
	}

	private static JsonNode creerCongeAnnuel(String jwt, LocalDate debut, LocalDate fin, String motif) throws Exception {
		Map<String, Object> req = Map.of(
				"type_demande", "CONGE",
				"contenu", Map.of(
						"type_conge", "ANNUEL",
						"date_debut", debut.toString(),
						"date_fin", fin.toString(),
						"motif", motif));
		HttpResponse<String> r = post(GATEWAY + "/api/rh/v1/demandes-administratives", jwt,
				MAPPER.writeValueAsString(req));
		assertThat(r.statusCode()).as("Création congé : %s", r.body()).isEqualTo(201);
		return MAPPER.readTree(r.body());
	}

	private static double lireSoldeAcquis(String jwt) throws Exception {
		JsonNode n = getJson(GATEWAY + "/api/rh/v1/soldes-conge/moi", jwt);
		return n.get("acquis").asDouble();
	}

	private static String trouverTachePourInstance(String jwt, String instanceId) throws Exception {
		JsonNode root = getJson(GATEWAY + "/api/rh/v1/workflow/mes-taches", jwt);
		JsonNode items = root.isArray() ? root : (root.has("content") ? root.get("content") : root.get("taches"));
		if (items != null && items.isArray()) {
			for (JsonNode t : items) {
				String inst = t.has("instance_id") ? t.get("instance_id").asText() : "";
				if (instanceId.equals(inst)) {
					return t.has("id") ? t.get("id").asText() : t.get("identifiant").asText();
				}
			}
		}
		return null;
	}

	private static void deciderTache(String jwt, String tacheId, String decision, String commentaire) throws Exception {
		String body = MAPPER.writeValueAsString(Map.of(
				"decision", decision,
				"commentaire", commentaire,
				"cle_idempotence", "it-w5-" + UUID.randomUUID()));
		HttpResponse<String> r = post(
				GATEWAY + "/api/rh/v1/workflow/taches/" + tacheId + "/decision", jwt, body);
		assertThat(r.statusCode()).as("Décision %s sur tâche %s : %s", decision, tacheId, r.body()).isEqualTo(200);
	}

	private static JsonNode getJson(String url, String jwt) throws Exception {
		HttpResponse<String> r = HTTP.send(
				HttpRequest.newBuilder(URI.create(url))
						.header("Authorization", "Bearer " + jwt)
						.timeout(Duration.ofSeconds(5))
						.GET()
						.build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(r.statusCode()).as("GET %s : %s", url, r.body()).isEqualTo(200);
		return MAPPER.readTree(r.body());
	}

	private static HttpResponse<String> post(String url, String jwt, String json) throws Exception {
		return HTTP.send(
				HttpRequest.newBuilder(URI.create(url))
						.header("Content-Type", "application/json")
						.header("Authorization", "Bearer " + jwt)
						.timeout(Duration.ofSeconds(8))
						.POST(HttpRequest.BodyPublishers.ofString(json))
						.build(),
				HttpResponse.BodyHandlers.ofString());
	}
}
