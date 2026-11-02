package com.hr.presence.service;

import com.hr.presence.config.ClockConfig;
import com.hr.presence.dto.HeuresSupDtos.FenetreDuJourResponse;
import com.hr.presence.dto.HeuresSupDtos.HeureSupDuJour;
import com.hr.presence.dto.HeuresSupDtos.SiteMobileResponse;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceHoraireSiteRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.service.FenetreHoraireService.Fenetre;
import com.hr.presence.service.FenetreHoraireService.Horaire;
import com.hr.presence.web.PresenceMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Données de l'écran de pointage (D-P16.c) : horaire du jour, fenêtre et heures sup du jour du site scanné, et liste
 * des sites actifs. Le collaborateur ne lit que la fenêtre du jour, jamais l'horaire hebdomadaire complet.
 * Aucun pointage n'est enregistré ; le compte vient du JWT.
 */
@Service
public class FenetreMobileService {

	private final PresenceSiteRepository siteRepository;
	private final PresenceHoraireSiteRepository horaireRepository;
	private final QrTokenService qrTokenService;
	private final QrDynamiqueService qrDynamiqueService;
	private final FenetreHoraireService fenetreService;
	private final PresenceAccessService accessService;
	private final Clock clock;

	public FenetreMobileService(
			PresenceSiteRepository siteRepository,
			PresenceHoraireSiteRepository horaireRepository,
			QrTokenService qrTokenService,
			QrDynamiqueService qrDynamiqueService,
			FenetreHoraireService fenetreService,
			PresenceAccessService accessService,
			Clock clock) {
		this.siteRepository = siteRepository;
		this.horaireRepository = horaireRepository;
		this.qrTokenService = qrTokenService;
		this.qrDynamiqueService = qrDynamiqueService;
		this.fenetreService = fenetreService;
		this.accessService = accessService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public FenetreDuJourResponse fenetreDuJour(String qrToken) {
		PresenceSite site = siteDuQr(qrToken);
		Instant maintenant = clock.instant();
		LocalDate aujourdhui = maintenant.atZone(ClockConfig.ZONE_TUNIS).toLocalDate();
		Map<DayOfWeek, Horaire> horaires = fenetreService.horaires(site.getId());
		boolean avecHoraire = !horaires.isEmpty();
		String compte = accessService.currentCompteId();
		// Même choix de journée que le pointage : la veille si on est encore dans sa fenêtre de nuit.
		LocalDate veille = aujourdhui.minusDays(1);
		LocalDate journee = aujourdhui;
		Optional<Fenetre> fenetre = fenetreService.fenetreDuJour(site, compte, veille)
				.filter(f -> f.contient(maintenant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)));
		if (fenetre.isPresent()) {
			journee = veille;
		} else {
			fenetre = fenetreService.fenetreDuJour(site, compte, aujourdhui);
		}
		if (fenetre.isEmpty()) {
			return new FenetreDuJourResponse(site.getId(), site.getLibelle(), avecHoraire, aujourdhui, avecHoraire,
					null, null, null, null, List.of());
		}
		Fenetre f = fenetre.get();
		List<HeureSupDuJour> heuresSup = fenetreService.ouverturesValidees(site.getId(), compte, List.of(journee))
				.values().stream()
				.map(o -> new HeureSupDuJour(o.reference(), o.avantMinutes(), o.apresMinutes())).toList();
		return new FenetreDuJourResponse(site.getId(), site.getLibelle(), true, journee, false,
				FenetreHoraireService.HEURE_MINUTE.format(f.horaireDebut()),
				FenetreHoraireService.HEURE_MINUTE.format(f.horaireFin()),
				FenetreHoraireService.HEURE_MINUTE.format(f.ouvertureDebut()),
				FenetreHoraireService.HEURE_MINUTE.format(f.ouvertureFin()),
				heuresSup);
	}

	@Transactional(readOnly = true)
	public List<SiteMobileResponse> sitesActifs() {
		Set<UUID> avecHoraire = new HashSet<>(horaireRepository.findSiteIdsAvecHoraire());
		return siteRepository.findAll().stream()
				.filter(PresenceSite::isActif)
				.sorted(Comparator.comparing(PresenceSite::getLibelle, String.CASE_INSENSITIVE_ORDER))
				.map(s -> new SiteMobileResponse(s.getId(), s.getLibelle(), avecHoraire.contains(s.getId())))
				.toList();
	}

	/** Site du QR (signature vérifiée, rien n'est enregistré) : QR illisible ou signature fausse = 422 QR_INVALIDE. */
	private PresenceSite siteDuQr(String qrToken) {
		UUID siteId;
		if (QrDynamiqueService.estDynamique(qrToken)) {
			QrDynamiqueService.Resultat r = qrDynamiqueService.verifier(qrToken, Instant.now(clock));
			if (r.verdict() == QrDynamiqueService.Verdict.INVALIDE) {
				throw PresenceMetierException.unprocessable("QR_INVALIDE", "Jeton QR invalide ou signature incorrecte");
			}
			siteId = r.siteId();
		} else {
			siteId = qrTokenService.parseAndVerify(qrToken)
					.orElseThrow(() -> PresenceMetierException.unprocessable("QR_INVALIDE",
							"Jeton QR invalide ou signature incorrecte"))
					.siteId();
		}
		return siteRepository.findById(siteId)
				.filter(PresenceSite::isActif)
				.orElseThrow(() -> PresenceMetierException.notFound("SITE_INTROUVABLE", "Site introuvable"));
	}
}
