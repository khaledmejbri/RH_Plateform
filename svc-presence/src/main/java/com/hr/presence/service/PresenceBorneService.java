package com.hr.presence.service;

import com.hr.presence.domain.ModeQr;
import com.hr.presence.dto.BorneGenereeResponse;
import com.hr.presence.dto.BorneQrResponse;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PresenceMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Borne écran (D-P05) : lien dédié par site, révocable, qui affiche un QR renouvelé toutes les 30 s
 * sans compte RH connecté. Le jeton de borne n'est montré qu'une fois ; seule son empreinte est stockée.
 */
@Service
public class PresenceBorneService {

	private static final Logger log = LoggerFactory.getLogger(PresenceBorneService.class);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final PresenceSiteService siteService;
	private final PresenceSiteRepository siteRepository;
	private final QrDynamiqueService qrDynamiqueService;
	private final QrPngService qrPngService;
	private final PresenceAccessService accessService;

	public PresenceBorneService(
			PresenceSiteService siteService,
			PresenceSiteRepository siteRepository,
			QrDynamiqueService qrDynamiqueService,
			QrPngService qrPngService,
			PresenceAccessService accessService) {
		this.siteService = siteService;
		this.siteRepository = siteRepository;
		this.qrDynamiqueService = qrDynamiqueService;
		this.qrPngService = qrPngService;
		this.accessService = accessService;
	}

	/** Crée (ou remplace) la borne du site et passe le site en mode écran. */
	@Transactional
	public BorneGenereeResponse generer(UUID siteId) {
		PresenceSite site = siteService.requireSite(siteId);
		byte[] brut = new byte[32];
		RANDOM.nextBytes(brut);
		String jeton = Base64.getUrlEncoder().withoutPadding().encodeToString(brut);
		site.setBorneJetonHash(QrDynamiqueService.empreinte(jeton));
		site.setBorneVersion(site.getBorneVersion() + 1);
		site.setBorneCreeLe(Instant.now());
		site.setModeQr(ModeQr.ECRAN);
		site.setUpdatedBy(accessService.currentActorLabel());
		siteRepository.save(site);
		log.info("Borne écran générée siteId={} version={}", site.getId(), site.getBorneVersion());
		return new BorneGenereeResponse(site.getId(), jeton, "/borne/" + jeton);
	}

	/** Désactive la borne : son lien et ses QR deviennent invalides immédiatement. */
	@Transactional
	public void revoquer(UUID siteId) {
		PresenceSite site = siteService.requireSite(siteId);
		site.setBorneJetonHash(null);
		site.setBorneVersion(site.getBorneVersion() + 1);
		site.setUpdatedBy(accessService.currentActorLabel());
		siteRepository.save(site);
	}

	/** Appelé par la page borne (sans compte) : QR de la fenêtre de 30 s en cours. */
	@Transactional(readOnly = true)
	public BorneQrResponse qrCourant(String jeton) {
		PresenceSite site = siteRepository.findByBorneJetonHash(QrDynamiqueService.empreinte(jeton))
				.filter(PresenceSite::isActif)
				.filter(s -> s.getModeQr() == ModeQr.ECRAN)
				.orElseThrow(() -> PresenceMetierException.notFound("BORNE_INCONNUE",
						"Cette borne n'existe pas ou a été désactivée par les RH"));
		Instant maintenant = Instant.now();
		long fenetre = QrDynamiqueService.fenetre(maintenant);
		String qr = qrDynamiqueService.construire(site.getId(), site.getBorneVersion(), fenetre);
		return new BorneQrResponse(
				site.getLibelle(),
				qr,
				Base64.getEncoder().encodeToString(qrPngService.toPng(qr)),
				QrDynamiqueService.secondesRestantes(maintenant),
				QrDynamiqueService.FENETRE_SECONDES);
	}
}
