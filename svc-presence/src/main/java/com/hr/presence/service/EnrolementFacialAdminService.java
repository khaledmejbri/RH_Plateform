package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.domain.ActionAuditFacial;
import com.hr.presence.domain.StatutEnrolement;
import com.hr.presence.dto.FacialAdminDtos.DeblocageRequest;
import com.hr.presence.dto.FacialAdminDtos.EnrolementDetail;
import com.hr.presence.dto.FacialAdminDtos.EnrolementLigne;
import com.hr.presence.dto.FacialAdminDtos.HistoriqueFacial;
import com.hr.presence.dto.PageResponse;
import com.hr.presence.entity.PresenceAuditFacial;
import com.hr.presence.entity.PresenceEnrolementFacial;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.repository.AuditFacialRepository;
import com.hr.presence.repository.EnrolementFacialRepository;
import com.hr.presence.web.PresenceMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Plateforme RH : liste et détail des enrôlements, déblocage du ré-enrôlement (D-P07.e). Le déblocage exige un motif,
 * est audité, n'est jamais fait par la personne concernée, et invalide l'ancien appareil.
 */
@Service
public class EnrolementFacialAdminService {

	private static final Logger log = LoggerFactory.getLogger(EnrolementFacialAdminService.class);
	private static final int MASQUE_LONGUEUR = 6;

	private final EnrolementFacialRepository enrolementRepository;
	private final AuditFacialRepository auditRepository;
	private final FacialAuditService auditService;
	private final FacialIdentiteService identiteService;
	private final ReferentielClient referentielClient;
	private final PresenceEventPublisher eventPublisher;
	private final Clock clock;

	public EnrolementFacialAdminService(
			EnrolementFacialRepository enrolementRepository,
			AuditFacialRepository auditRepository,
			FacialAuditService auditService,
			FacialIdentiteService identiteService,
			ReferentielClient referentielClient,
			PresenceEventPublisher eventPublisher,
			Clock clock) {
		this.enrolementRepository = enrolementRepository;
		this.auditRepository = auditRepository;
		this.auditService = auditService;
		this.identiteService = identiteService;
		this.referentielClient = referentielClient;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<EnrolementLigne> lister(
			StatutEnrolement statut, String q, Boolean appareilIncompatible, int page, int size) {
		int taille = Math.clamp(size, 1, 50);
		Page<PresenceEnrolementFacial> resultat = enrolementRepository.findAll(
				EnrolementFacialRepository.filtreAdmin(statut, q, appareilIncompatible),
				PageRequest.of(Math.max(page, 0), taille, Sort.by(Sort.Direction.DESC, "modifieLe")));
		List<EnrolementLigne> contenu = resultat.getContent().stream().map(EnrolementFacialAdminService::ligne).toList();
		return new PageResponse<>(contenu, resultat.getNumber(), resultat.getSize(),
				resultat.getTotalElements(), resultat.getTotalPages());
	}

	@Transactional(readOnly = true)
	public EnrolementDetail detail(UUID id) {
		PresenceEnrolementFacial e = enrolementRepository.findById(id).orElseThrow(EnrolementFacialAdminService::introuvable);
		List<HistoriqueFacial> historique = auditRepository.findByEnrolementIdOrderByHorodatageDesc(id).stream()
				.map(EnrolementFacialAdminService::historique)
				.toList();
		return new EnrolementDetail(e.getId(), e.getCompteId(), e.getCollaborateurId(), e.getCollaborateurNom(),
				e.getStatut(), e.getVersionConsentement(), e.getDecideLe(), e.getEnroleLe(),
				e.getDemandeReenrolementLe(), masquer(e.getDeviceId()), masquer(e.getDeviceIdDemande()),
				e.getDeviceIdIncompatible() != null, e.getEchecsConsecutifs(), e.getBloqueJusqua(), historique);
	}

	/** A_REENROLER vers NON_ENROLE : appareils et compteur remis à zéro, collaborateur et responsable notifiés. */
	@Transactional
	public EnrolementDetail debloquer(UUID id, DeblocageRequest requete) {
		String acteur = identiteService.compteObligatoire();
		PresenceEnrolementFacial e = enrolementRepository.findByIdForUpdate(id).orElseThrow(EnrolementFacialAdminService::introuvable);

		ValideurPointage maFiche = maFicheOuNull();
		boolean memePersonne = acteur.equals(e.getCompteId())
				|| (maFiche != null && e.getCollaborateurId() != null
						&& e.getCollaborateurId().equalsIgnoreCase(FacialIdentiteService.texte(maFiche.collaborateurId())));
		if (memePersonne) {
			throw new PresenceMetierException("AUTO_DEBLOCAGE_INTERDIT",
					"Vous ne pouvez pas débloquer votre propre ré-enrôlement : demandez à un autre RH ou à un ADMIN", 403);
		}
		if (e.getStatut() == StatutEnrolement.ENROLE) {
			throw PresenceMetierException.conflict("DEJA_ENROLE", "Ce collaborateur est déjà enrôlé : rien à débloquer");
		}
		if (e.getStatut() != StatutEnrolement.A_REENROLER) {
			throw PresenceMetierException.conflict("RIEN_A_DEBLOQUER", "Aucune demande de ré-enrôlement en attente");
		}

		Instant maintenant = clock.instant();
		String ancienAppareil = e.getDeviceId();
		e.setStatut(StatutEnrolement.NON_ENROLE);
		e.setDeviceId(null);
		e.setDeviceIdDemande(null);
		e.setEchecsConsecutifs(0);
		e.setDernierEchecLe(null);
		e.setBloqueJusqua(null);
		e.setModifieLe(maintenant);
		enrolementRepository.save(e);

		String motif = requete.motif().trim();
		auditService.tracer(ActionAuditFacial.DEBLOCAGE_RH, e.getId(), StatutEnrolement.A_REENROLER,
				StatutEnrolement.NON_ENROLE, ancienAppareil, e.getVersionConsentement(), motif,
				maFiche != null ? maFiche.collaborateurNom() : null);
		log.info("Déblocage facial enrolement={} par compte={}", e.getId(), acteur);

		eventPublisher.notifier(e.getCompteId(), "Ré-enrôlement facial débloqué",
				"Les RH ont débloqué votre ré-enrôlement facial. Ouvrez l'application pour enrôler votre visage "
						+ "sur votre appareil.");
		String nom = e.getCollaborateurNom() != null ? e.getCollaborateurNom() : "Un collaborateur";
		String responsable = e.getValideurCompteId() != null ? e.getValideurCompteId() : e.getValideurId();
		eventPublisher.notifier(responsable, "Ré-enrôlement facial débloqué — " + nom,
				"Le ré-enrôlement facial de " + nom + " a été débloqué par les RH. Motif : " + motif);
		return detail(id);
	}

	/** Fiche de l'acteur, si elle existe (un ADMIN peut ne pas en avoir) : sert au nom d'audit et à l'auto-déblocage. */
	private ValideurPointage maFicheOuNull() {
		try {
			return referentielClient.monValideurPointage();
		} catch (RuntimeException ex) {
			log.debug("Fiche de l'acteur indisponible pour le déblocage : {}", ex.getMessage());
			return null;
		}
	}

	private static EnrolementLigne ligne(PresenceEnrolementFacial e) {
		return new EnrolementLigne(e.getId(), e.getCompteId(), e.getCollaborateurId(), e.getCollaborateurNom(),
				e.getStatut(), e.getVersionConsentement(), e.getDecideLe(), e.getEnroleLe(),
				e.getDemandeReenrolementLe(), masquer(e.getDeviceId()), masquer(e.getDeviceIdDemande()),
				e.getDeviceIdIncompatible() != null, e.getEchecsConsecutifs(), e.getBloqueJusqua());
	}

	private static HistoriqueFacial historique(PresenceAuditFacial a) {
		String acteur = a.getActeurNom() != null ? a.getActeurNom() : a.getActeurCompteId();
		return new HistoriqueFacial(a.getAction().name(), a.getStatutAvant(), a.getStatutApres(), acteur,
				a.getActeurRole(), a.getMotif(), a.getVersion(), a.getHorodatage());
	}

	/** Les 6 derniers caractères seulement (le {@code device_id} complet n'est pas exposé en liste). */
	static String masquer(String deviceId) {
		if (deviceId == null) {
			return null;
		}
		return deviceId.length() <= MASQUE_LONGUEUR ? deviceId : deviceId.substring(deviceId.length() - MASQUE_LONGUEUR);
	}

	private static PresenceMetierException introuvable() {
		return PresenceMetierException.notFound("ENROLEMENT_INTROUVABLE", "Enrôlement introuvable");
	}
}
