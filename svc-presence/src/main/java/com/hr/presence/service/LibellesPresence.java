package com.hr.presence.service;

import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Function;

/**
 * Libellés français d'origine des statuts et motifs de pointage (D-L01.b) : aucun code brut n'arrive à l'écran.
 * Les libellés servis viennent des paramètres ({@link PresenceParametresService}, D-R02) ; cette classe ne sert que
 * de valeur d'amorçage et de repli quand la ligne de paramètre est absente ou la base indisponible.
 */
public final class LibellesPresence {

	private LibellesPresence() {
	}

	public static String statut(PointageStatut statut) {
		return switch (statut) {
			case VALIDE -> "Pointage validé";
			case VALIDE_MANUEL -> "Pointage manuel validé";
			case REJETE_QR_INVALIDE -> "QR code invalide";
			case REJETE_QR_EXPIRE -> "QR code expiré";
			case REJETE_QR_REVOQUE -> "QR code révoqué";
			case REJETE_HORS_ZONE -> "Hors zone de pointage";
			case REJETE_SITE_INACTIF -> "Site inactif";
			case REJETE_EMPLACEMENT_ABSENT -> "Emplacement du site non défini";
			case REJETE_PRECISION_GPS -> "Précision GPS insuffisante";
			case REJETE_POSITION_SIMULEE -> "Position simulée détectée";
			case REJETE_VISAGE_NON_RECONNU -> "Visage non reconnu";
			case REJETE_VIVACITE -> "Contrôle du vivant échoué";
			case REJETE_VERIFICATION_FACIALE_ABSENTE -> "Vérification faciale absente";
			case REJETE_HORS_HORAIRE -> "Hors des horaires du site";
		};
	}

	public static String type(PointageType type) {
		return type == PointageType.SORTIE ? "Sortie" : "Entrée";
	}

	/** Libellé d'origine d'un statut de demande manuelle (amorçage et repli). */
	public static String statutDemande(StatutDemandeManuelle statut) {
		return switch (statut) {
			case EN_ATTENTE -> "En attente";
			case VALIDEE -> "Validée";
			case REFUSEE -> "Refusée";
			case EXPIREE -> "Expirée";
		};
	}

	/**
	 * Motif d'un pointage refusé, en clair. Hors zone : distance et rayon du site (« Hors zone de pointage
	 * (179,8 m pour une zone de 50 m) ») ; sinon le motif enregistré, à défaut le libellé du statut. Nul pour un
	 * pointage accepté. {@code libelleStatut} donne le libellé courant du statut (paramètres D-R02).
	 */
	public static String motifRejet(PresencePointage p, PresenceSite site, Function<PointageStatut, String> libelleStatut) {
		if (p.getStatut() == null || !p.getStatut().name().startsWith("REJETE_")) {
			return null;
		}
		if (p.getStatut() == PointageStatut.REJETE_HORS_ZONE && p.getDistanceMetres() != null && site != null
				&& site.getRayonMetres() > 0) {
			return "Hors zone de pointage (" + String.format(Locale.FRANCE, "%.1f", p.getDistanceMetres())
					+ " m pour une zone de " + site.getRayonMetres() + " m)";
		}
		if (p.getMotifRejet() != null && !p.getMotifRejet().isBlank()) {
			return p.getMotifRejet();
		}
		return libelleStatut.apply(p.getStatut());
	}

	private static final ZoneId ZONE = ZoneId.of("Africa/Tunis");
	private static final DateTimeFormatter JOUR_HEURE = DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm", Locale.FRENCH).withZone(ZONE);

	/** Mètres au dixième, format français avec espace comme séparateur de milliers : « 3 450,0 m ». */
	public static String metres(double valeur) {
		DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRANCE);
		symboles.setGroupingSeparator(' ');
		symboles.setDecimalSeparator(',');
		return new DecimalFormat("#,##0.0", symboles).format(valeur) + " m";
	}

	/** Instant en heure de Tunis : « 10/11 à 08:02 » (D-P23). */
	public static String jourHeure(Instant instant) {
		return JOUR_HEURE.format(instant);
	}

	/** Délai enregistré (D-P24.e), minutes entières : « 40 min » sous 1 h, sinon « 1 h 40 » / « 26 h 05 ». */
	public static String duree(long minutes) {
		if (minutes < 60) {
			return minutes + " min";
		}
		return (minutes / 60) + " h " + String.format("%02d", minutes % 60);
	}

	/** Délai réglé en phrase : « 2 heures », « 15 minutes », « 1 h 30 ». */
	public static String dureeEnPhrase(long minutes) {
		if (minutes < 60) {
			return minutes + (minutes > 1 ? " minutes" : " minute");
		}
		if (minutes % 60 == 0) {
			long heures = minutes / 60;
			return heures + (heures > 1 ? " heures" : " heure");
		}
		return duree(minutes);
	}

	public static String modeQr(String mode) {
		if (mode == null) {
			return null;
		}
		return "ECRAN".equals(mode) ? "Écran" : "Papier";
	}
}
