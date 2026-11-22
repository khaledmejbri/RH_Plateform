package com.hr.workflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Propriétés de configuration du moteur de workflow (D-W3-03, § 9.11).
 */
@ConfigurationProperties(prefix = "workflow")
public class WorkflowProperties {

	private final Referentiel referentiel = new Referentiel();
	private final Proprietaires proprietaires = new Proprietaires();
	private final Outbox outbox = new Outbox();
	private final Effets effets = new Effets();
	private final Echeances echeances = new Echeances();
	private final Delegations delegations = new Delegations();

	public Referentiel getReferentiel() {
		return referentiel;
	}

	public Proprietaires getProprietaires() {
		return proprietaires;
	}

	public Outbox getOutbox() {
		return outbox;
	}

	public Effets getEffets() {
		return effets;
	}

	public Echeances getEcheances() {
		return echeances;
	}

	public Delegations getDelegations() {
		return delegations;
	}

	public static class Referentiel {
		private String baseUrl = "http://localhost:8083";
		private int timeoutConnexionMs = 1000;
		private int timeoutLectureMs = 3000;
		private int maxAppelsSimultanes = 20;

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public int getTimeoutConnexionMs() {
			return timeoutConnexionMs;
		}

		public void setTimeoutConnexionMs(int timeoutConnexionMs) {
			this.timeoutConnexionMs = timeoutConnexionMs;
		}

		public int getTimeoutLectureMs() {
			return timeoutLectureMs;
		}

		public void setTimeoutLectureMs(int timeoutLectureMs) {
			this.timeoutLectureMs = timeoutLectureMs;
		}

		public int getMaxAppelsSimultanes() {
			return maxAppelsSimultanes;
		}

		public void setMaxAppelsSimultanes(int maxAppelsSimultanes) {
			this.maxAppelsSimultanes = maxAppelsSimultanes;
		}
	}

	public static class Proprietaires {
		private int timeoutConnexionMs = 1000;
		private int timeoutLectureMs = 3000;
		private final ProprietaireEndpoint svcReferentielRh = new ProprietaireEndpoint("http://localhost:8083");
		private final ProprietaireEndpoint svcPresence = new ProprietaireEndpoint("http://localhost:8086");

		public int getTimeoutConnexionMs() {
			return timeoutConnexionMs;
		}

		public void setTimeoutConnexionMs(int timeoutConnexionMs) {
			this.timeoutConnexionMs = timeoutConnexionMs;
		}

		public int getTimeoutLectureMs() {
			return timeoutLectureMs;
		}

		public void setTimeoutLectureMs(int timeoutLectureMs) {
			this.timeoutLectureMs = timeoutLectureMs;
		}

		public ProprietaireEndpoint getSvcReferentielRh() {
			return svcReferentielRh;
		}

		public ProprietaireEndpoint getSvcPresence() {
			return svcPresence;
		}

		public String baseUrlPour(String serviceProprietaire) {
			if ("svc-referentiel-rh".equals(serviceProprietaire)) {
				return svcReferentielRh.getBaseUrl();
			}
			if ("svc-presence".equals(serviceProprietaire)) {
				return svcPresence.getBaseUrl();
			}
			throw new IllegalArgumentException("Service propriétaire inconnu : " + serviceProprietaire);
		}

		public Map<String, String> toutesLesUrls() {
			Map<String, String> urls = new HashMap<>();
			urls.put("svc-referentiel-rh", svcReferentielRh.getBaseUrl());
			urls.put("svc-presence", svcPresence.getBaseUrl());
			return urls;
		}
	}

	public static class ProprietaireEndpoint {
		private String baseUrl;

		public ProprietaireEndpoint() {
		}

		public ProprietaireEndpoint(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}
	}

	public static class Outbox {
		private long delaiMs = 2000;

		public long getDelaiMs() {
			return delaiMs;
		}

		public void setDelaiMs(long delaiMs) {
			this.delaiMs = delaiMs;
		}
	}

	public static class Effets {
		private String repriseCron = "0 * * * * *";

		public String getRepriseCron() {
			return repriseCron;
		}

		public void setRepriseCron(String repriseCron) {
			this.repriseCron = repriseCron;
		}
	}

	public static class Echeances {
		private String cron = "0 * * * * *";

		public String getCron() {
			return cron;
		}

		public void setCron(String cron) {
			this.cron = cron;
		}
	}

	public static class Delegations {
		private String cron = "0 30 0 * * *";

		public String getCron() {
			return cron;
		}

		public void setCron(String cron) {
			this.cron = cron;
		}
	}
}
