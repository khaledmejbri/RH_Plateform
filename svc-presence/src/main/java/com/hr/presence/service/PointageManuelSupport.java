package com.hr.presence.service;

import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.web.PresenceMetierException;

import java.time.Instant;
import java.util.UUID;

/**
 * Règles et création du pointage validé partagées entre le circuit historique ({@link PresenceDemandeManuelleService})
 * et les effets moteur ({@link PresenceWorkflowEffetService}), sans dépendance circulaire.
 */
final class PointageManuelSupport {

	private PointageManuelSupport() {
	}

	/**
	 * Contrôle D-P12.c : l'heure déclarée doit être dans la fenêtre horaire du site.
	 * Retourne {@code null} si le site est absent ou sans horaire ; sinon lève 422 {@code HEURE_HORS_FENETRE}.
	 */
	static FenetreHoraireService.DansFenetre controlerFenetre(
			PresenceSiteRepository siteRepository,
			FenetreHoraireService fenetreHoraire,
			UUID siteId,
			String collaborateurId,
			Instant heure,
			PointageType type) {
		if (siteId == null) {
			return null;
		}
		PresenceSite site = siteRepository.findById(siteId).orElse(null);
		if (site == null) {
			return null;
		}
		FenetreHoraireService.Resultat resultat = fenetreHoraire.evaluer(site, collaborateurId, heure, type);
		if (resultat instanceof FenetreHoraireService.HorsFenetre hors) {
			throw PresenceMetierException.unprocessable("HEURE_HORS_FENETRE",
					"L'heure déclarée est hors de la fenêtre de pointage du site : " + hors.message());
		}
		return resultat instanceof FenetreHoraireService.DansFenetre dans ? dans : null;
	}

	/** Enregistre le {@link PresencePointage} {@code VALIDE_MANUEL} rattaché à la demande approuvée. */
	static PresencePointage creerPointageValide(
			PresenceDemandeManuelle d,
			FenetreHoraireService.DansFenetre dansFenetre,
			PresencePointageRepository pointageRepository) {
		PresencePointage rejet = pointageRepository.findById(d.getPointageRejeteId()).orElse(null);
		PresencePointage p = new PresencePointage();
		p.setCollaborateurId(d.getCollaborateurId());
		p.setType(d.getType());
		p.setStatut(PointageStatut.VALIDE_MANUEL);
		p.setServerTs(d.getHeureDeclaree());
		p.setSiteId(d.getSiteId());
		p.setDemandeManuelleId(d.getId());
		if (dansFenetre != null) {
			p.setJournee(dansFenetre.journee());
			p.setHoraireDebutTs(dansFenetre.horaireDebut());
			p.setHoraireFinTs(dansFenetre.horaireFin());
		}
		if (rejet != null) {
			p.setLatitude(rejet.getLatitude());
			p.setLongitude(rejet.getLongitude());
			p.setAccuracyMetres(rejet.getAccuracyMetres());
			p.setDistanceMetres(rejet.getDistanceMetres());
		}
		return pointageRepository.save(p);
	}
}
