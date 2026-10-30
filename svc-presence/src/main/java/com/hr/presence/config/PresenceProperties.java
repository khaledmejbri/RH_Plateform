package com.hr.presence.config;

import com.hr.presence.domain.TypeLieu;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "presence")
public class PresenceProperties {

	private final Qr qr = new Qr();
	private final Geofence geofence = new Geofence();
	private final Alert alert = new Alert();
	private final Manuel manuel = new Manuel();
	private final Referentiel referentiel = new Referentiel();
	private final Facial facial = new Facial();

	public Facial getFacial() {
		return facial;
	}

	/** Reconnaissance faciale au pointage (D-P06 à D-P10). */
	public static class Facial {
		/** Seuil de score par défaut tant que RH / ADMIN n'en a pas réglé un (D-P08). */
		private int seuilDefaut = 92;
		/** Échecs faciaux consécutifs qui déclenchent le blocage (D-P10.1). */
		private int echecsMax = 3;
		/** Durée du blocage de pointage, en minutes (D-P10.1). */
		private int blocageMinutes = 15;

		public int getSeuilDefaut() {
			return seuilDefaut;
		}

		public void setSeuilDefaut(int seuilDefaut) {
			this.seuilDefaut = seuilDefaut;
		}

		public int getEchecsMax() {
			return echecsMax;
		}

		public void setEchecsMax(int echecsMax) {
			this.echecsMax = echecsMax;
		}

		public int getBlocageMinutes() {
			return blocageMinutes;
		}

		public void setBlocageMinutes(int blocageMinutes) {
			this.blocageMinutes = blocageMinutes;
		}
	}

	public Manuel getManuel() {
		return manuel;
	}

	public Referentiel getReferentiel() {
		return referentiel;
	}

	/** Pointage manuel validé par le responsable (D-P04). */
	public static class Manuel {
		/** Alerte RH quand un collaborateur dépasse ce nombre de pointages manuels dans le mois. */
		private int seuilAlerteMois = 3;
		/** Heure déclarée au plus N jours dans le passé. */
		private int ancienneteMaxJours = 7;

		public int getSeuilAlerteMois() {
			return seuilAlerteMois;
		}

		public void setSeuilAlerteMois(int seuilAlerteMois) {
			this.seuilAlerteMois = seuilAlerteMois;
		}

		public int getAncienneteMaxJours() {
			return ancienneteMaxJours;
		}

		public void setAncienneteMaxJours(int ancienneteMaxJours) {
			this.ancienneteMaxJours = ancienneteMaxJours;
		}
	}

	public static class Referentiel {
		private String baseUrl = "http://localhost:8083";

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}
	}

	public Qr getQr() {
		return qr;
	}

	public Geofence getGeofence() {
		return geofence;
	}

	public Alert getAlert() {
		return alert;
	}

	public static class Qr {
		/** Secret HMAC — env PRESENCE_QR_HMAC_SECRET (défaut DEV non-prod uniquement). */
		private String hmacSecret = "dev-only-presence-qr-hmac-change-me";
		private int validityDays = 90;

		public String getHmacSecret() {
			return hmacSecret;
		}

		public void setHmacSecret(String hmacSecret) {
			this.hmacSecret = hmacSecret;
		}

		public int getValidityDays() {
			return validityDays;
		}

		public void setValidityDays(int validityDays) {
			this.validityDays = validityDays;
		}
	}

	public static class Geofence {
		private int defaultRadiusMetres = 50;
		/** Plafond absolu de précision acceptée ; la règle D-P01 exige en plus précision ≤ rayon du site. */
		private double maxAccuracyMetres = 100;
		/** Rayons par défaut et bornes (D-P01). */
		private int defaultRadiusExterieurMetres = 20;
		private int defaultRadiusInterieurMetres = 50;
		private int minRadiusMetres = 10;
		private int maxRadiusMetres = 100;
		/** Précision exigée à la pose de l'emplacement du site (D-P03). */
		private double anchorAccuracyExterieurMetres = 10;
		private double anchorAccuracyInterieurMetres = 25;

		public int defaultRadius(TypeLieu typeLieu) {
			return typeLieu == TypeLieu.INTERIEUR
					? defaultRadiusInterieurMetres
					: defaultRadiusExterieurMetres;
		}

		public double anchorAccuracy(TypeLieu typeLieu) {
			return typeLieu == TypeLieu.INTERIEUR
					? anchorAccuracyInterieurMetres
					: anchorAccuracyExterieurMetres;
		}

		public int getDefaultRadiusExterieurMetres() {
			return defaultRadiusExterieurMetres;
		}

		public void setDefaultRadiusExterieurMetres(int defaultRadiusExterieurMetres) {
			this.defaultRadiusExterieurMetres = defaultRadiusExterieurMetres;
		}

		public int getDefaultRadiusInterieurMetres() {
			return defaultRadiusInterieurMetres;
		}

		public void setDefaultRadiusInterieurMetres(int defaultRadiusInterieurMetres) {
			this.defaultRadiusInterieurMetres = defaultRadiusInterieurMetres;
		}

		public int getMinRadiusMetres() {
			return minRadiusMetres;
		}

		public void setMinRadiusMetres(int minRadiusMetres) {
			this.minRadiusMetres = minRadiusMetres;
		}

		public int getMaxRadiusMetres() {
			return maxRadiusMetres;
		}

		public void setMaxRadiusMetres(int maxRadiusMetres) {
			this.maxRadiusMetres = maxRadiusMetres;
		}

		public double getAnchorAccuracyExterieurMetres() {
			return anchorAccuracyExterieurMetres;
		}

		public void setAnchorAccuracyExterieurMetres(double anchorAccuracyExterieurMetres) {
			this.anchorAccuracyExterieurMetres = anchorAccuracyExterieurMetres;
		}

		public double getAnchorAccuracyInterieurMetres() {
			return anchorAccuracyInterieurMetres;
		}

		public void setAnchorAccuracyInterieurMetres(double anchorAccuracyInterieurMetres) {
			this.anchorAccuracyInterieurMetres = anchorAccuracyInterieurMetres;
		}

		public int getDefaultRadiusMetres() {
			return defaultRadiusMetres;
		}

		public void setDefaultRadiusMetres(int defaultRadiusMetres) {
			this.defaultRadiusMetres = defaultRadiusMetres;
		}

		public double getMaxAccuracyMetres() {
			return maxAccuracyMetres;
		}

		public void setMaxAccuracyMetres(double maxAccuracyMetres) {
			this.maxAccuracyMetres = maxAccuracyMetres;
		}
	}

	public static class Alert {
		private boolean j30Enabled = true;
		private String j30Cron = "0 0 8 * * *";

		public boolean isJ30Enabled() {
			return j30Enabled;
		}

		public void setJ30Enabled(boolean j30Enabled) {
			this.j30Enabled = j30Enabled;
		}

		public String getJ30Cron() {
			return j30Cron;
		}

		public void setJ30Cron(String j30Cron) {
			this.j30Cron = j30Cron;
		}
	}
}
