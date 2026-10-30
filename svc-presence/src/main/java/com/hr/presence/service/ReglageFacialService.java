package com.hr.presence.service;

import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.ActionAuditFacial;
import com.hr.presence.domain.StatutReglageFacial;
import com.hr.presence.dto.FacialAdminDtos.ReglageRequest;
import com.hr.presence.dto.FacialAdminDtos.ReglageResponse;
import com.hr.presence.entity.PresenceConsentementVersion;
import com.hr.presence.entity.PresenceReglageFacial;
import com.hr.presence.repository.ConsentementVersionRepository;
import com.hr.presence.repository.ReglageFacialRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PresenceMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Réglage société de la vérification faciale (D-P06) : INACTIVE par défaut, date de mise en service, seuil.
 * États : INACTIVE (rien), ACTIVE avant la date (consentement et enrôlement, aucun contrôle — D-P06.a),
 * ACTIVE à partir de la date (contrôle exigé, jour calendaire Africa/Tunis).
 */
@Service
public class ReglageFacialService {

	public static final int SEUIL_MIN = 80;
	public static final int SEUIL_MAX = 99;

	/** État calculé du réglage à l'instant courant. */
	public record Etat(StatutReglageFacial statut, LocalDate dateMiseEnService, int seuilScore, boolean controleExige) {
		public boolean actif() {
			return statut == StatutReglageFacial.ACTIVE;
		}
	}

	private final ReglageFacialRepository reglageRepository;
	private final ConsentementVersionRepository consentementRepository;
	private final FacialAuditService auditService;
	private final PresenceAccessService accessService;
	private final PresenceProperties properties;
	private final Clock clock;

	public ReglageFacialService(
			ReglageFacialRepository reglageRepository,
			ConsentementVersionRepository consentementRepository,
			FacialAuditService auditService,
			PresenceAccessService accessService,
			PresenceProperties properties,
			Clock clock) {
		this.reglageRepository = reglageRepository;
		this.consentementRepository = consentementRepository;
		this.auditService = auditService;
		this.accessService = accessService;
		this.properties = properties;
		this.clock = clock;
	}

	/** Ligne absente = INACTIVE avec le seuil par défaut. */
	@Transactional(readOnly = true)
	public Etat etat() {
		Optional<PresenceReglageFacial> ligne = reglageRepository.findById(PresenceReglageFacial.ID_SOCIETE);
		if (ligne.isEmpty()) {
			return new Etat(StatutReglageFacial.INACTIVE, null, properties.getFacial().getSeuilDefaut(), false);
		}
		return calculer(ligne.get());
	}

	@Transactional(readOnly = true)
	public ReglageResponse lire() {
		PresenceReglageFacial r = reglageRepository.findById(PresenceReglageFacial.ID_SOCIETE).orElse(null);
		String courante = consentementRepository.findFirstByOrderByPublieLeDescVersionDesc()
				.map(PresenceConsentementVersion::getVersion).orElse(null);
		if (r == null) {
			return new ReglageResponse(StatutReglageFacial.INACTIVE, null, properties.getFacial().getSeuilDefaut(),
					courante, false, null, null, null, null);
		}
		return new ReglageResponse(r.getStatut(), r.getDateMiseEnService(), r.getSeuilScore(), courante,
				calculer(r).controleExige(), r.getCadreLegalConfirmePar(), r.getCadreLegalConfirmeLe(),
				r.getModifieParCompteId(), r.getModifieLe());
	}

	@Transactional
	public ReglageResponse modifier(ReglageRequest requete) {
		String acteur = accessService.currentCompteId();
		if (acteur == null) {
			throw new SecurityException("Impossible d'identifier le compte courant");
		}
		PresenceReglageFacial r = reglageRepository.findById(PresenceReglageFacial.ID_SOCIETE).orElse(null);
		if (r == null) {
			r = new PresenceReglageFacial();
			r.setSeuilScore(properties.getFacial().getSeuilDefaut());
		}
		StatutReglageFacial statutAvant = r.getStatut();
		int seuilAvant = r.getSeuilScore();
		LocalDate dateAvant = r.getDateMiseEnService();

		int seuil = requete.seuilScore() != null ? requete.seuilScore() : seuilAvant;
		if (seuil < SEUIL_MIN || seuil > SEUIL_MAX) {
			throw new PresenceMetierException("SEUIL_HORS_PLAGE",
					"Le seuil doit être compris entre " + SEUIL_MIN + " et " + SEUIL_MAX, 400);
		}
		LocalDate date = requete.dateMiseEnService() != null ? requete.dateMiseEnService() : dateAvant;
		boolean passageActive = requete.statut() == StatutReglageFacial.ACTIVE && statutAvant != StatutReglageFacial.ACTIVE;

		if (requete.statut() == StatutReglageFacial.ACTIVE) {
			if (consentementRepository.findFirstByOrderByPublieLeDescVersionDesc().isEmpty()) {
				throw PresenceMetierException.unprocessable("VERSION_CONSENTEMENT_NON_PUBLIEE",
						"Publiez une version du texte de consentement avant d'activer la vérification faciale");
			}
			if (passageActive && !Boolean.TRUE.equals(requete.cadreLegalValide())) {
				throw PresenceMetierException.unprocessable("CONFIRMATION_CADRE_LEGAL_REQUISE",
						"Confirmez que le cadre légal est validé avant d'activer la vérification faciale");
			}
			if (date == null) {
				throw PresenceMetierException.unprocessable("DATE_MISE_EN_SERVICE_REQUISE",
						"Une date de mise en service est requise pour activer la vérification faciale");
			}
		}

		Instant maintenant = clock.instant();
		r.setStatut(requete.statut());
		r.setSeuilScore(seuil);
		r.setDateMiseEnService(date);
		r.setModifieParCompteId(acteur);
		r.setModifieLe(maintenant);
		if (passageActive) {
			r.setCadreLegalConfirmePar(acteur);
			r.setCadreLegalConfirmeLe(maintenant);
		}
		reglageRepository.save(r);

		auditService.tracer(ActionAuditFacial.REGLAGE_MODIFIE, null, null, null, null, null,
				"statut " + statutAvant + " -> " + r.getStatut() + ", seuil " + seuilAvant + " -> " + seuil
						+ ", mise en service " + (date != null ? date : "non définie"));
		if (passageActive) {
			auditService.tracer(ActionAuditFacial.CADRE_LEGAL_CONFIRME, null, null, null, null, null,
					"Cadre légal confirmé à l'activation");
		}
		return lire();
	}

	private Etat calculer(PresenceReglageFacial r) {
		boolean actif = r.getStatut() == StatutReglageFacial.ACTIVE;
		boolean dateAtteinte = r.getDateMiseEnService() != null
				&& !LocalDate.now(clock).isBefore(r.getDateMiseEnService());
		return new Etat(r.getStatut(), r.getDateMiseEnService(), r.getSeuilScore(), actif && dateAtteinte);
	}
}
