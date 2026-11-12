package com.hr.presence.config;

import com.hr.presence.domain.TypeLieu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "presence")
public class PresenceProperties {

	private final Qr qr = new Qr();
	private final Geofence geofence = new Geofence();
	private final Alert alert = new Alert();
	private final Manuel manuel = new Manuel();
	private final Referentiel referentiel = new Referentiel();
	private final Facial facial = new Facial();
	private final HeuresSup heuresSup = new HeuresSup();
	private final TempsTravail tempsTravail = new TempsTravail();
	private final Retard retard = new Retard();
	private final AlerteEchecs alerteEchecs = new AlerteEchecs();

	public AlerteEchecs getAlerteEchecs() {
		return alerteEchecs;
	}

	/** Alerte « pointages refusés à répétition » (D-P20) : information seulement, aucun blocage. */
	public static class AlerteEchecs {
		/** Nombre de refus de position (zone, précision, position simulée) dans la journée qui déclenche l'alerte. */
		private int seuil = 5;

		public int getSeuil() {
			return seuil;
		}

		public void setSeuil(int seuil) {
			this.seuil = Math.max(1, seuil);
		}
	}

	public Retard getRetard() {
		return retard;
	}

	/** Retards (D-P19) : information seulement, calculée à la volée. */
	public static class Retard {
		/**
		 * Délai de grâce ajouté au début de l'horaire avant de compter un retard (D-P19.d), en minutes. 0 par défaut ;
		 * paramétrable de 0 à 60 min au lot 3 (D-R06) : il sera alors lu dans les paramètres.
		 */
		private int delaiGraceMinutes = 0;

		public int getDelaiGraceMinutes() {
			return delaiGraceMinutes;
		}

		public void setDelaiGraceMinutes(int delaiGraceMinutes) {
			this.delaiGraceMinutes = Math.clamp(delaiGraceMinutes, 0, 60);
		}
	}

	public TempsTravail getTempsTravail() {
		return tempsTravail;
	}

	/** Vue « Temps de travail » (D-P17). */
	public static class TempsTravail {
		/** Période maximale consultée ou exportée, bornes comprises (jours). */
		private int periodeMaxJours = 31;
		/** Rattachement d'une SORTIE après minuit à l'ENTREE ouverte, sur un site sans horaire (heures, D-P17.b). */
		private int dureeMaxSansHoraireHeures = 16;

		public int getPeriodeMaxJours() {
			return periodeMaxJours;
		}

		public void setPeriodeMaxJours(int periodeMaxJours) {
			this.periodeMaxJours = periodeMaxJours;
		}

		public int getDureeMaxSansHoraireHeures() {
			return dureeMaxSansHoraireHeures;
		}

		public void setDureeMaxSansHoraireHeures(int dureeMaxSansHoraireHeures) {
			this.dureeMaxSansHoraireHeures = dureeMaxSansHoraireHeures;
		}
	}

	public HeuresSup getHeuresSup() {
		return heuresSup;
	}

	/** Heures supplémentaires (D-P13 à D-P16). Le cron d'expiration est lu directement par le planificateur. */
	public static class HeuresSup {
		/** Période maximale des heures comptées et de l'export CSV (jours). */
		private int periodeExportMaxJours = 93;

		public int getPeriodeExportMaxJours() {
			return periodeExportMaxJours;
		}

		public void setPeriodeExportMaxJours(int periodeExportMaxJours) {
			this.periodeExportMaxJours = periodeExportMaxJours;
		}
	}

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

	/** Pointage manuel validé par le responsable (D-P04, D-P21 à D-P26). */
	public static class Manuel {
		private static final Logger log = LoggerFactory.getLogger(Manuel.class);

		/** Alerte RH quand un collaborateur dépasse ce nombre de pointages manuels dans le mois. */
		private int seuilAlerteMois = 3;
		/** Plafond de distance et de précision d'un refus rattrapable (D-P22), 100 à 1 000 m. */
		private int plafondDistanceMetres = 300;
		/** Délai pour demander un pointage manuel après le refus (D-P23.a), 15 min à 8 h. */
		private int delaiDemandeMinutes = 120;
		/** Délai de décision avant expiration (D-P23.b), 4 à 72 h. */
		private int delaiDecisionMinutes = 1440;
		/** Délai au-delà duquel une demande porte le signal « tardive » (D-P24.a). */
		private int signalDelaiMinutes = 30;
		/** Cron du planificateur d'expiration (lu par le planificateur, zone Africa/Tunis). */
		private String expirationCron = "0 */5 * * * *";

		public static final int PLAFOND_MIN = 100;
		public static final int PLAFOND_MAX = 1000;
		public static final int DELAI_DEMANDE_MIN = 15;
		public static final int DELAI_DEMANDE_MAX = 480;
		public static final int DELAI_DECISION_MIN = 240;
		public static final int DELAI_DECISION_MAX = 4320;

		public int getSeuilAlerteMois() {
			return seuilAlerteMois;
		}

		public void setSeuilAlerteMois(int seuilAlerteMois) {
			this.seuilAlerteMois = strictementPositif("presence.manuel.seuil-alerte-mois", seuilAlerteMois);
		}

		public int getPlafondDistanceMetres() {
			return plafondDistanceMetres;
		}

		public void setPlafondDistanceMetres(int valeur) {
			this.plafondDistanceMetres = verifier("presence.manuel.plafond-distance-metres", valeur, PLAFOND_MIN, PLAFOND_MAX);
		}

		public int getDelaiDemandeMinutes() {
			return delaiDemandeMinutes;
		}

		public void setDelaiDemandeMinutes(int valeur) {
			this.delaiDemandeMinutes = verifier("presence.manuel.delai-demande-minutes", valeur, DELAI_DEMANDE_MIN, DELAI_DEMANDE_MAX);
		}

		public int getDelaiDecisionMinutes() {
			return delaiDecisionMinutes;
		}

		public void setDelaiDecisionMinutes(int valeur) {
			this.delaiDecisionMinutes = verifier("presence.manuel.delai-decision-minutes", valeur, DELAI_DECISION_MIN, DELAI_DECISION_MAX);
		}

		public int getSignalDelaiMinutes() {
			return signalDelaiMinutes;
		}

		public void setSignalDelaiMinutes(int signalDelaiMinutes) {
			this.signalDelaiMinutes = strictementPositif("presence.manuel.signal-delai-minutes", signalDelaiMinutes);
		}

		public String getExpirationCron() {
			return expirationCron;
		}

		public void setExpirationCron(String expirationCron) {
			this.expirationCron = expirationCron;
		}

		/** Zéro ou négatif : le service refuse de démarrer, en nommant la propriété. */
		static int strictementPositif(String propriete, int valeur) {
			if (valeur <= 0) {
				throw new IllegalArgumentException(propriete + " = " + valeur + " doit être strictement positif");
			}
			return valeur;
		}

		/**
		 * Au-dessus de la borne haute : le service refuse de démarrer (jamais de valeur ramenée en silence, D-P22.b).
		 * Sous la borne basse (plus strict, utile en recette) : démarrage avec un avertissement.
		 */
		static int verifier(String propriete, int valeur, int min, int max) {
			strictementPositif(propriete, valeur);
			if (valeur > max) {
				throw new IllegalArgumentException(propriete + " = " + valeur + " dépasse la borne haute " + max
						+ " (plage autorisée " + min + " à " + max + ")");
			}
			if (valeur < min) {
				log.warn("{} = {} est sous la borne basse {} (plage {} à {}) : réglage plus strict, à réserver à la recette",
						propriete, valeur, min, min, max);
			}
			return valeur;
		}
	}

	public static class Referentiel {
		private String baseUrl = "http://localhost:8083";
		/** Délai d'établissement de la connexion vers le référentiel (ms). */
		private int timeoutConnexionMs = 2000;
		/** Délai d'attente de la réponse du référentiel (ms). */
		private int timeoutLectureMs = 3000;

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
