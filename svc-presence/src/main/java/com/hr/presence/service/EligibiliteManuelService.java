package com.hr.presence.service;

import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.ModeQr;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.SignalVigilance;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.web.PresenceMetierException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Éligibilité d'un refus au pointage manuel (D-P21 à D-P23.a), à un seul endroit : elle fait foi à la création et
 * alimente {@code manuel_possible} / {@code manuel_impossible_motif} à la lecture. Les contrôles suivent l'ordre
 * D-P21.c (étapes 6 à 13) : le premier en échec donne le verdict. Le composant ne lit rien en base lui-même : refus,
 * site et demande existante lui sont fournis (seul l'horaire du site passe par {@link FenetreHoraireService}).
 */
@Component
public class EligibiliteManuelService {

	/** Refus rattrapables (D-P21) ; tous les autres refus sont définitifs. */
	static final Set<PointageStatut> RATTRAPABLES = EnumSet.of(
			PointageStatut.REJETE_HORS_ZONE, PointageStatut.REJETE_PRECISION_GPS,
			PointageStatut.REJETE_VISAGE_NON_RECONNU, PointageStatut.REJETE_VIVACITE,
			PointageStatut.REJETE_VERIFICATION_FACIALE_ABSENTE);

	static final Set<PointageStatut> FACIAUX = EnumSet.of(
			PointageStatut.REJETE_VISAGE_NON_RECONNU, PointageStatut.REJETE_VIVACITE,
			PointageStatut.REJETE_VERIFICATION_FACIALE_ABSENTE);

	/** Mesures de la tentative refusée, figées sur la demande. */
	public record Mesures(Double distance, Double precision, int rayon, ModeQr modeQr) {
	}

	/**
	 * Verdict : {@code code} nul = rattrapable. {@code message} est celui de la réponse 422 / 409 ; {@code motif} est le
	 * texte court de D-P21.d servi dans {@code manuel_impossible_motif}.
	 */
	public record Verdict(String code, int httpStatus, String message, String motif, Mesures mesures) {

		public boolean possible() {
			return code == null;
		}

		public PresenceMetierException exception() {
			return new PresenceMetierException(code, message, httpStatus);
		}
	}

	/** Données figées à la création (ou à la reprise) à partir de la tentative refusée. */
	public record Figees(String refusStatut, Double distance, Double precision, int rayon, ModeQr modeQr,
						 long delaiDemandeMinutes, List<SignalVigilance> signaux) {
	}

	private final PresenceProperties properties;
	private final HaversineService haversine;
	private final FenetreHoraireService fenetreHoraire;

	public EligibiliteManuelService(PresenceProperties properties, HaversineService haversine,
									FenetreHoraireService fenetreHoraire) {
		this.properties = properties;
		this.haversine = haversine;
		this.fenetreHoraire = fenetreHoraire;
	}

	/**
	 * Étapes 6 à 13 de D-P21.c pour un pointage rejeté.
	 *
	 * @param existante demande déjà faite pour ce refus, ou nul
	 */
	public Verdict evaluer(PresencePointage rejet, PresenceSite site, PresenceDemandeManuelle existante, Instant maintenant) {
		PointageStatut statut = rejet.getStatut();
		Mesures mesures = null;
		if (statut == null || !statut.name().startsWith("REJETE_")) {
			return refus("POINTAGE_NON_REJETE", 422, "Seul un pointage refusé peut faire l'objet d'une demande manuelle",
					null, null);
		}
		if (existante != null) {
			String ref = existante.getNumero() != null ? " (" + existante.getNumero() + ")" : "";
			return refus("DEMANDE_DEJA_FAITE", 409,
					"Une demande de pointage manuel a déjà été faite pour ce refus" + ref + ".",
					"une demande a déjà été faite pour ce refus" + ref, null);
		}
		if (!RATTRAPABLES.contains(statut)) {
			String motif = motifDefinitif(statut);
			return refus("POINTAGE_NON_RATTRAPABLE", 422,
					"Ce refus ne peut pas faire l'objet d'un pointage manuel : " + motif + ".", motif, null);
		}

		int plafond = properties.getManuel().getPlafondDistanceMetres();
		Double distance = distanceDe(rejet, site);
		Double precision = rejet.getAccuracyMetres();
		if (precision == null || distance == null || !Double.isFinite(rejet.getLatitude())
				|| !Double.isFinite(rejet.getLongitude())) {
			return refus("POSITION_ABSENTE", 422,
					"Position GPS absente : un pointage manuel n'est pas possible. Activez la localisation et pointez à nouveau sur le site.",
					"position GPS absente", null);
		}
		mesures = new Mesures(distance, precision, rayonDe(site), site != null ? site.getModeQr() : ModeQr.PAPIER);
		if (precision > plafond) {
			return refus("PRECISION_TROP_FAIBLE", 422,
					"La précision de votre position (± " + LibellesPresence.metres(precision)
							+ ") est insuffisante pour un pointage manuel (" + plafond + " m au plus).",
					"précision de votre position insuffisante (± " + LibellesPresence.metres(precision) + ", "
							+ plafond + " m au plus)", mesures);
		}
		if (distance > plafond) {
			return refus("DISTANCE_TROP_GRANDE", 422,
					"Vous étiez à " + LibellesPresence.metres(distance) + " du site : un pointage manuel n'est possible qu'à "
							+ plafond + " m au plus. Rendez-vous sur le site et pointez à nouveau.",
					"vous étiez à " + LibellesPresence.metres(distance) + " du site, " + plafond + " m au plus", mesures);
		}
		long delaiMax = properties.getManuel().getDelaiDemandeMinutes();
		if (maintenant.isAfter(rejet.getServerTs().plus(Duration.ofMinutes(delaiMax)))) {
			String quand = LibellesPresence.jourHeure(rejet.getServerTs());
			String delai = LibellesPresence.dureeEnPhrase(delaiMax);
			return refus("DELAI_DEMANDE_DEPASSE", 422,
					"La demande doit être faite dans les " + delai + " qui suivent le pointage refusé (refus du " + quand + ").",
					"délai de demande dépassé (" + delai + " après le pointage refusé du " + quand + ")", mesures);
		}
		// D-P12.c : l'heure de la tentative doit tomber dans la fenêtre du jour du site (horaire en vigueur).
		if (site != null && fenetreHoraire.evaluer(site, rejet.getCollaborateurId(), rejet.getServerTs(), rejet.getType())
				instanceof FenetreHoraireService.HorsFenetre hors) {
			return refus("HEURE_HORS_FENETRE", 422,
					"L'heure déclarée est hors de la fenêtre de pointage du site : " + hors.message(),
					"heure hors de la fenêtre de pointage du site : " + hors.message(), mesures);
		}
		return new Verdict(null, 0, null, null, mesures);
	}

	/** Données figées et signaux de vigilance (D-P24.a, D-P24.c) ; suppose un verdict rattrapable. */
	public Figees figer(PresencePointage rejet, PresenceSite site, Duration delaiDemande, long manuelsValidesDuMois) {
		Double distance = distanceDe(rejet, site);
		Double precision = rejet.getAccuracyMetres();
		int rayon = rayonDe(site);
		ModeQr mode = site != null ? site.getModeQr() : ModeQr.PAPIER;
		List<SignalVigilance> signaux = new ArrayList<>();
		if (distance != null && distance > 2.0 * rayon) {
			signaux.add(SignalVigilance.DISTANCE_SUP_2X_RAYON);
		}
		if (precision != null && precision > rayon) {
			signaux.add(SignalVigilance.PRECISION_SUP_RAYON);
		}
		if (mode == ModeQr.PAPIER) {
			signaux.add(SignalVigilance.SITE_QR_PAPIER);
		}
		if (manuelsValidesDuMois >= properties.getManuel().getSeuilAlerteMois()) {
			signaux.add(SignalVigilance.MANUELS_MOIS_SEUIL);
		}
		if (FACIAUX.contains(rejet.getStatut())) {
			signaux.add(SignalVigilance.REFUS_FACIAL);
		}
		if (delaiDemande.compareTo(Duration.ofMinutes(properties.getManuel().getSignalDelaiMinutes())) > 0) {
			signaux.add(SignalVigilance.DEMANDE_TARDIVE);
		}
		return new Figees(rejet.getStatut().name(), distance, precision, rayon, mode,
				Math.max(0, delaiDemande.toMinutes()), signaux);
	}

	/** Distance enregistrée, sinon recalculée depuis la position et l'emplacement du site (refus de précision GPS). */
	private Double distanceDe(PresencePointage rejet, PresenceSite site) {
		if (rejet.getDistanceMetres() != null) {
			return rejet.getDistanceMetres();
		}
		if (site == null || !site.hasEmplacement() || !Double.isFinite(rejet.getLatitude())
				|| !Double.isFinite(rejet.getLongitude())) {
			return null;
		}
		return haversine.distanceMetres(site.getLatitude(), site.getLongitude(), rejet.getLatitude(), rejet.getLongitude());
	}

	/** Rayon du site ; à défaut le rayon par défaut de son type (20 m extérieur, 50 m intérieur ou sans type). */
	int rayonDe(PresenceSite site) {
		if (site == null) {
			return properties.getGeofence().getDefaultRadiusInterieurMetres();
		}
		if (site.getRayonMetres() > 0) {
			return site.getRayonMetres();
		}
		return site.isTypeLieuDefini()
				? properties.getGeofence().defaultRadius(site.getTypeLieu())
				: properties.getGeofence().getDefaultRadiusInterieurMetres();
	}

	private static String motifDefinitif(PointageStatut statut) {
		return switch (statut) {
			case REJETE_POSITION_SIMULEE -> "position simulée détectée";
			case REJETE_HORS_HORAIRE -> "pointage hors de l'horaire du site";
			case REJETE_QR_INVALIDE -> "code QR non reconnu, pointez avec le code QR du site";
			case REJETE_QR_EXPIRE -> "code QR expiré, prévenez les RH";
			case REJETE_QR_REVOQUE -> "code QR révoqué, prévenez les RH";
			case REJETE_SITE_INACTIF -> "site inactif, prévenez les RH";
			case REJETE_EMPLACEMENT_ABSENT -> "emplacement du site non défini, prévenez les RH";
			default -> "ce refus n'est pas rattrapable";
		};
	}

	private static Verdict refus(String code, int http, String message, String motif, Mesures mesures) {
		return new Verdict(code, http, message, motif, mesures);
	}
}
