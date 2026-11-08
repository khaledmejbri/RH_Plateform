package com.hr.presence.service;

import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;

import java.util.Locale;

/**
 * Libellés français des statuts et motifs de pointage (D-L01.b) : aucun code brut n'arrive à l'écran. En attendant la
 * table de libellés paramétrable du lot 3, la correspondance est ici, à un seul endroit.
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

	public static String statutDemande(StatutDemandeManuelle statut) {
		return switch (statut) {
			case EN_ATTENTE -> "En attente";
			case VALIDEE -> "Validée";
			case REFUSEE -> "Refusée";
		};
	}

	/**
	 * Motif d'un pointage refusé, en clair. Hors zone : distance et rayon du site (« Hors zone de pointage
	 * (179,8 m pour une zone de 50 m) ») ; sinon le motif enregistré, à défaut le libellé du statut. Nul pour un
	 * pointage accepté.
	 */
	public static String motifRejet(PresencePointage p, PresenceSite site) {
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
		return statut(p.getStatut());
	}
}
