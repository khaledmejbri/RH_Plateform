package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.kafka.ObjetNotification;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Alerte « pointages refusés à répétition » (D-P20). Quand un collaborateur atteint le seuil de refus de position
 * dans la journée calendaire (fuseau de l'horloge, Africa/Tunis), son responsable D-H03 et les RH reçoivent une seule
 * notification. Information seulement : aucun blocage ni sanction. Le service ne lève jamais d'exception.
 *
 * <p>Une seule alerte par jour : le compte est fait après l'enregistrement du refus et l'alerte part quand il est
 * égal au seuil. Un rejeu d'idempotence ne passe jamais ici (la réponse d'origine est renvoyée sans nouvelle ligne).
 * Deux refus strictement simultanés pourraient doubler l'alerte : accepté (information sans conséquence).
 */
@Service
public class AlerteEchecsPointageService {

	private static final Logger log = LoggerFactory.getLogger(AlerteEchecsPointageService.class);

	/** Seuls les refus de position comptent (D-P20) ; QR, horaire, site et visage ont leurs propres règles. */
	static final Set<PointageStatut> STATUTS_COMPTES = EnumSet.of(
			PointageStatut.REJETE_HORS_ZONE, PointageStatut.REJETE_PRECISION_GPS, PointageStatut.REJETE_POSITION_SIMULEE);

	private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

	private final PresencePointageRepository pointageRepository;
	private final PresenceSiteRepository siteRepository;
	private final ReferentielClient referentielClient;
	private final PresenceEventPublisher eventPublisher;
	private final PresenceProperties properties;
	private final Clock clock;

	public AlerteEchecsPointageService(
			PresencePointageRepository pointageRepository,
			PresenceSiteRepository siteRepository,
			ReferentielClient referentielClient,
			PresenceEventPublisher eventPublisher,
			PresenceProperties properties,
			Clock clock) {
		this.pointageRepository = pointageRepository;
		this.siteRepository = siteRepository;
		this.referentielClient = referentielClient;
		this.eventPublisher = eventPublisher;
		this.properties = properties;
		this.clock = clock;
	}

	/** À appeler juste après l'enregistrement d'un pointage refusé, dans la requête du collaborateur (JWT présent). */
	public void apresRefus(PresencePointage refus) {
		try {
			if (refus == null || !STATUTS_COMPTES.contains(refus.getStatut())) {
				return;
			}
			ZoneId zone = clock.getZone();
			LocalDate jour = LocalDate.ofInstant(refus.getServerTs(), zone);
			Instant debut = jour.atStartOfDay(zone).toInstant();
			Instant fin = jour.plusDays(1).atStartOfDay(zone).toInstant();
			long total = pointageRepository.countByCollaborateurIdAndStatutInAndServerTsGreaterThanEqualAndServerTsLessThan(
					refus.getCollaborateurId(), STATUTS_COMPTES, debut, fin);
			if (total != properties.getAlerteEchecs().getSeuil()) {
				return;
			}
			alerter(refus, (int) total, zone);
		} catch (RuntimeException ex) {
			log.warn("Alerte de refus répétés non émise collab={} : {}",
					refus != null ? refus.getCollaborateurId() : null, ex.getMessage());
		}
	}

	private void alerter(PresencePointage refus, int total, ZoneId zone) {
		UUID ficheId = versUuid(refus.getCollaborateurId());
		String nom = null;
		String destinataireResponsable = null;
		try {
			ValideurPointage v = referentielClient.monValideurPointage();
			if (v.collaborateurId() != null) {
				ficheId = v.collaborateurId();
			}
			nom = v.collaborateurNom();
			if (v.valideurCompteId() != null) {
				destinataireResponsable = v.valideurCompteId().toString();
			} else if (v.valideurId() != null) {
				destinataireResponsable = v.valideurId().toString();
			}
		} catch (RuntimeException ex) {
			// Référentiel en panne : seuls les RH sont alertés, sous un nom générique
			log.warn("Responsable D-H03 illisible pour l'alerte de refus répétés collab={} : {}",
					refus.getCollaborateurId(), ex.getMessage());
		}
		String matricule = matriculeDe(ficheId);
		String qui = nom != null && !nom.isBlank() ? nom : "Un collaborateur";

		PresenceSite site = refus.getSiteId() == null ? null : siteRepository.findById(refus.getSiteId()).orElse(null);
		String motif = LibellesPresence.motifRejet(refus, site);
		String surSite = site != null ? " sur le site « " + site.getLibelle() + " »" : "";
		String corps = qui + " a eu " + total + " pointages refusés aujourd'hui. Dernier refus à "
				+ HEURE.format(refus.getServerTs().atZone(zone)) + surSite + " : " + enMinuscule(motif)
				+ ". Le pointage manuel reste possible.";
		String sujet = "Pointages refusés à répétition — " + qui;

		// Un seul identifiant de notification pour tous les destinataires de l'évènement
		ObjetNotification objet = ObjetNotification.de(
				ObjetNotification.COLLABORATEUR, "POINTAGE_ECHECS_REPETES", ficheId, matricule);
		if (destinataireResponsable != null) {
			eventPublisher.notifier(destinataireResponsable, sujet, corps, objet);
		}
		eventPublisher.notifier(PresenceEventPublisher.DESTINATAIRE_RH, sujet, corps, objet);
		log.info("Alerte de refus répétés émise collab={} total={}", refus.getCollaborateurId(), total);
	}

	private String matriculeDe(UUID ficheId) {
		if (ficheId == null) {
			return null;
		}
		try {
			return referentielClient.resoudreCollaborateurs(List.of(ficheId)).stream()
					.map(ReferentielClient.CollaborateurResolu::matricule)
					.filter(m -> m != null && !m.isBlank()).findFirst().orElse(null);
		} catch (RuntimeException ex) {
			log.warn("Matricule non résolu pour l'alerte de refus répétés : {}", ex.getMessage());
			return null;
		}
	}

	private static String enMinuscule(String motif) {
		if (motif == null || motif.isBlank()) {
			return "motif non précisé";
		}
		String m = motif.strip();
		String sansPoint = m.endsWith(".") ? m.substring(0, m.length() - 1) : m;
		return Character.toLowerCase(sansPoint.charAt(0)) + sansPoint.substring(1);
	}

	private static UUID versUuid(String valeur) {
		try {
			return valeur == null ? null : UUID.fromString(valeur);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
