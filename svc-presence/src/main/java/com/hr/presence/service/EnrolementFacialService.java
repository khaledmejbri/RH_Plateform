package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.domain.ActionAuditFacial;
import com.hr.presence.domain.StatutEnrolement;
import com.hr.presence.dto.FacialMobileDtos;
import com.hr.presence.dto.FacialMobileDtos.AppareilIncompatibleRequest;
import com.hr.presence.dto.FacialMobileDtos.ConsentementCourant;
import com.hr.presence.dto.FacialMobileDtos.ConsentementDecisionRequest;
import com.hr.presence.dto.FacialMobileDtos.DemandeReenrolementRequest;
import com.hr.presence.dto.FacialMobileDtos.EnrolementRequest;
import com.hr.presence.dto.FacialMobileDtos.EtatFacialMoi;
import com.hr.presence.entity.PresenceConsentementVersion;
import com.hr.presence.entity.PresenceEnrolementFacial;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.repository.EnrolementFacialRepository;
import com.hr.presence.service.FacialIdentiteService.Identite;
import com.hr.presence.service.ReglageFacialService.Etat;
import com.hr.presence.web.PresenceMetierException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static com.hr.presence.service.FacialIdentiteService.texte;

/**
 * Parcours mobile de la vérification faciale (D-P07) : état, consentement, enrôlement, retrait, appareil incompatible,
 * demande de ré-enrôlement. Le serveur ne reçoit ni image, ni gabarit, ni vecteur : uniquement des décisions
 * (compte, fiche, {@code device_id}, version du consentement). L'identité vient du JWT.
 */
@Service
public class EnrolementFacialService {

	private static final String INACTIVE = "VERIFICATION_FACIALE_INACTIVE";

	/** Résultat d'un enrôlement : {@code cree} = 201, sinon rejeu idempotent = 200. */
	public record ResultatEnrolement(EtatFacialMoi etat, boolean cree) {
	}

	private final EnrolementFacialRepository enrolementRepository;
	private final FacialIdentiteService identiteService;
	private final ReglageFacialService reglageService;
	private final ConsentementFacialService consentementService;
	private final FacialAuditService auditService;
	private final PresenceEventPublisher eventPublisher;
	private final Clock clock;

	public EnrolementFacialService(
			EnrolementFacialRepository enrolementRepository,
			FacialIdentiteService identiteService,
			ReglageFacialService reglageService,
			ConsentementFacialService consentementService,
			FacialAuditService auditService,
			PresenceEventPublisher eventPublisher,
			Clock clock) {
		this.enrolementRepository = enrolementRepository;
		this.identiteService = identiteService;
		this.reglageService = reglageService;
		this.consentementService = consentementService;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public EtatFacialMoi moi(String deviceId) {
		// Un device_id fourni doit être un UUID (comme à l'enrôlement) : 400 plutôt que de l'ignorer en silence.
		if (deviceId != null && !deviceId.matches(FacialMobileDtos.UUID_REGEX)) {
			throw new PresenceMetierException("PARAMETRE_INVALIDE", "device_id doit être un UUID", 400);
		}
		Identite identite = identiteService.resoudre();
		PresenceEnrolementFacial ligne = enrolementRepository.findByCompteIdForUpdate(identite.compteId()).orElse(null);
		if (ligne != null) {
			majInstantane(ligne, identite);
		}
		return construireEtat(ligne, deviceId);
	}

	@Transactional(readOnly = true)
	public ConsentementCourant consentementCourant() {
		identiteService.resoudre();
		return consentementService.courantePourMobile();
	}

	@Transactional
	public ResultatEnrolement enroler(EnrolementRequest requete) {
		Identite identite = identiteService.resoudre();
		PresenceEnrolementFacial ligne = enrolementRepository.findByCompteIdForUpdate(identite.compteId()).orElse(null);

		// Rejeu d'une requête déjà appliquée (même clé, même appareil) : 200 identique.
		if (ligne != null && ligne.getStatut() == StatutEnrolement.ENROLE
				&& requete.idempotencyKey().equalsIgnoreCase(ligne.getIdempotencyKeyEnrolement())
				&& requete.deviceId().equalsIgnoreCase(ligne.getDeviceId())) {
			majInstantane(ligne, identite);
			return new ResultatEnrolement(construireEtat(ligne, requete.deviceId()), false);
		}

		exigerActive();
		StatutEnrolement avant = statut(ligne);
		switch (avant) {
			case ENROLE -> {
				if (requete.deviceId().equalsIgnoreCase(ligne.getDeviceId())) {
					throw PresenceMetierException.conflict("DEJA_ENROLE", "Ce collaborateur est déjà enrôlé sur cet appareil");
				}
				throw PresenceMetierException.conflict("REENROLEMENT_A_DEBLOQUER",
						"Un autre appareil est enrôlé : le ré-enrôlement passe par un déblocage RH");
			}
			case REVOQUE, A_REENROLER -> throw PresenceMetierException.conflict("REENROLEMENT_A_DEBLOQUER",
					"Le ré-enrôlement exige un déblocage RH");
			default -> {
			}
		}
		exigerVersionCourante(requete.versionConsentement());
		if (!Boolean.TRUE.equals(requete.vivaciteReussie())) {
			throw PresenceMetierException.unprocessable("VIVACITE_ECHOUEE", "Le contrôle du vivant a échoué");
		}

		Instant maintenant = clock.instant();
		PresenceEnrolementFacial cible = ligne != null ? ligne : nouvelleLigne(identite, maintenant);
		majInstantane(cible, identite);
		// Un enrôlement réussi prouve que l'appareil est capable : le marquage « incompatible » est effacé.
		boolean marquageEfface = requete.deviceId().equalsIgnoreCase(cible.getDeviceIdIncompatible());
		if (marquageEfface) {
			cible.setDeviceIdIncompatible(null);
		}
		cible.setStatut(StatutEnrolement.ENROLE);
		cible.setDeviceId(requete.deviceId());
		cible.setDeviceIdDemande(null);
		cible.setVersionConsentement(requete.versionConsentement());
		cible.setModeleVersion(requete.modeleVersion());
		cible.setIdempotencyKeyEnrolement(requete.idempotencyKey());
		cible.setDecideLe(maintenant);
		cible.setEnroleLe(maintenant);
		reinitialiserCompteur(cible);
		toucher(cible, maintenant);
		PresenceEnrolementFacial enregistre = enregistrer(cible);
		auditService.tracer(ActionAuditFacial.ENROLEMENT, enregistre.getId(), avant, StatutEnrolement.ENROLE,
				requete.deviceId(), requete.versionConsentement(), "Modèle " + requete.modeleVersion()
						+ (marquageEfface ? " ; marquage appareil incompatible effacé" : ""));
		return new ResultatEnrolement(construireEtat(enregistre, requete.deviceId()), true);
	}

	/** Reconsentement après une nouvelle version du texte, sans ré-enrôlement (D-P07.f). */
	@Transactional
	public EtatFacialMoi accepterConsentement(ConsentementDecisionRequest requete) {
		Identite identite = identiteService.resoudre();
		PresenceEnrolementFacial ligne = enrolementRepository.findByCompteIdForUpdate(identite.compteId()).orElse(null);
		exigerActive();
		StatutEnrolement statut = statut(ligne);
		String courante = versionCourante();
		if ((statut != StatutEnrolement.ENROLE && statut != StatutEnrolement.A_REENROLER)
				|| (courante != null && courante.equals(ligne.getVersionConsentement()))) {
			throw PresenceMetierException.conflict("RECONSENTEMENT_NON_ATTENDU", "Aucun reconsentement n'est attendu");
		}
		exigerVersionCourante(requete.versionConsentement());

		Instant maintenant = clock.instant();
		majInstantane(ligne, identite);
		ligne.setVersionConsentement(requete.versionConsentement());
		ligne.setDecideLe(maintenant);
		toucher(ligne, maintenant);
		enrolementRepository.save(ligne);
		auditService.tracer(ActionAuditFacial.CONSENTEMENT_ACCEPTE, ligne.getId(), statut, statut,
				ligne.getDeviceId(), requete.versionConsentement(), "Reconsentement à la nouvelle version");
		return construireEtat(ligne, ligne.getDeviceId());
	}

	/** NON_ENROLE : REFUSE ; ENROLE / A_REENROLER en reconsentement : REVOQUE et appareil invalidé (D-P07.f). */
	@Transactional
	public EtatFacialMoi refuserConsentement(ConsentementDecisionRequest requete) {
		Identite identite = identiteService.resoudre();
		PresenceEnrolementFacial ligne = enrolementRepository.findByCompteIdForUpdate(identite.compteId()).orElse(null);
		exigerActive();
		StatutEnrolement avant = statut(ligne);
		String courante = versionCourante();
		boolean reconsentementAttendu = (avant == StatutEnrolement.ENROLE || avant == StatutEnrolement.A_REENROLER)
				&& courante != null && !courante.equals(ligne.getVersionConsentement());
		if (avant == StatutEnrolement.REFUSE || avant == StatutEnrolement.REVOQUE
				|| ((avant == StatutEnrolement.ENROLE || avant == StatutEnrolement.A_REENROLER) && !reconsentementAttendu)) {
			throw PresenceMetierException.conflict("DECISION_DEJA_ENREGISTREE", "Une décision est déjà enregistrée");
		}
		exigerVersionCourante(requete.versionConsentement());

		Instant maintenant = clock.instant();
		PresenceEnrolementFacial cible = ligne != null ? ligne : nouvelleLigne(identite, maintenant);
		majInstantane(cible, identite);
		StatutEnrolement apres = avant == StatutEnrolement.NON_ENROLE ? StatutEnrolement.REFUSE : StatutEnrolement.REVOQUE;
		cible.setStatut(apres);
		cible.setVersionConsentement(requete.versionConsentement());
		cible.setDecideLe(maintenant);
		if (apres == StatutEnrolement.REVOQUE) {
			invaliderAppareil(cible);
		}
		toucher(cible, maintenant);
		PresenceEnrolementFacial enregistre = enregistrer(cible);
		auditService.tracer(ActionAuditFacial.CONSENTEMENT_REFUSE, enregistre.getId(), avant, apres,
				null, requete.versionConsentement(), null);
		return construireEtat(enregistre, null);
	}

	/** Retrait à tout moment, même réglage INACTIVE (D-P06.b) ; un retrait déjà appliqué est rejoué sans effet. */
	@Transactional
	public EtatFacialMoi retirerConsentement() {
		Identite identite = identiteService.resoudre();
		PresenceEnrolementFacial ligne = enrolementRepository.findByCompteIdForUpdate(identite.compteId()).orElse(null);
		StatutEnrolement avant = statut(ligne);
		if (avant == StatutEnrolement.REVOQUE) {
			return construireEtat(ligne, null);
		}
		if (avant != StatutEnrolement.ENROLE && avant != StatutEnrolement.A_REENROLER) {
			throw PresenceMetierException.conflict("AUCUN_CONSENTEMENT_ACTIF", "Aucun consentement actif à retirer");
		}
		Instant maintenant = clock.instant();
		String deviceAvant = ligne.getDeviceId();
		majInstantane(ligne, identite);
		ligne.setStatut(StatutEnrolement.REVOQUE);
		ligne.setDecideLe(maintenant);
		invaliderAppareil(ligne);
		toucher(ligne, maintenant);
		enrolementRepository.save(ligne);
		auditService.tracer(ActionAuditFacial.RETRAIT, ligne.getId(), avant, StatutEnrolement.REVOQUE,
				deviceAvant, ligne.getVersionConsentement(), "Retrait du consentement");
		return construireEtat(ligne, null);
	}

	/** Incompatibilité constatée par l'appli (D-P07.g) : le collaborateur reste NON_ENROLE. */
	@Transactional
	public EtatFacialMoi signalerAppareilIncompatible(AppareilIncompatibleRequest requete) {
		Identite identite = identiteService.resoudre();
		PresenceEnrolementFacial ligne = enrolementRepository.findByCompteIdForUpdate(identite.compteId()).orElse(null);
		exigerActive();
		if (statut(ligne) != StatutEnrolement.NON_ENROLE) {
			throw PresenceMetierException.conflict("STATUT_INCOMPATIBLE",
					"Seul un collaborateur non enrôlé peut signaler un appareil incompatible");
		}
		Instant maintenant = clock.instant();
		PresenceEnrolementFacial cible = ligne != null ? ligne : nouvelleLigne(identite, maintenant);
		majInstantane(cible, identite);
		cible.setDeviceIdIncompatible(requete.deviceId());
		toucher(cible, maintenant);
		PresenceEnrolementFacial enregistre = enregistrer(cible);
		auditService.tracer(ActionAuditFacial.APPAREIL_INCOMPATIBLE, enregistre.getId(),
				StatutEnrolement.NON_ENROLE, StatutEnrolement.NON_ENROLE,
				requete.deviceId(), null, requete.raison().name());
		return construireEtat(enregistre, requete.deviceId());
	}

	/**
	 * Demande explicite de ré-enrôlement depuis un nouvel appareil, ou depuis l'appareil enrôlé sans gabarit
	 * (D-P07.c). L'ancien appareil reste accepté jusqu'au déblocage RH.
	 */
	@Transactional
	public EtatFacialMoi demanderReenrolement(DemandeReenrolementRequest requete) {
		Identite identite = identiteService.resoudre();
		PresenceEnrolementFacial ligne = enrolementRepository.findByCompteIdForUpdate(identite.compteId()).orElse(null);
		exigerActive();
		StatutEnrolement avant = statut(ligne);
		switch (avant) {
			case NON_ENROLE, REFUSE -> throw PresenceMetierException.conflict("STATUT_INCOMPATIBLE",
					"Aucun enrôlement à refaire : enrôlez-vous directement");
			case A_REENROLER -> throw PresenceMetierException.conflict("DEMANDE_DEJA_EN_COURS",
					"Une demande de ré-enrôlement est déjà en attente de déblocage RH");
			case ENROLE -> {
				if (requete.deviceId().equalsIgnoreCase(ligne.getDeviceId()) && Boolean.TRUE.equals(requete.gabaritPresent())) {
					throw PresenceMetierException.conflict("DEJA_ENROLE",
							"Cet appareil est enrôlé et conserve son gabarit");
				}
			}
			default -> {
			}
		}
		Instant maintenant = clock.instant();
		majInstantane(ligne, identite);
		ligne.setStatut(StatutEnrolement.A_REENROLER);
		ligne.setDeviceIdDemande(requete.deviceId());
		ligne.setDemandeReenrolementLe(maintenant);
		toucher(ligne, maintenant);
		enrolementRepository.save(ligne);
		auditService.tracer(ActionAuditFacial.DEMANDE_REENROLEMENT, ligne.getId(), avant, StatutEnrolement.A_REENROLER,
				requete.deviceId(), ligne.getVersionConsentement(), null);
		String nom = ligne.getCollaborateurNom() != null ? ligne.getCollaborateurNom() : "Un collaborateur";
		eventPublisher.notifier(PresenceEventPublisher.DESTINATAIRE_RH,
				"Ré-enrôlement facial à débloquer — " + nom,
				nom + " demande à enrôler de nouveau son visage (nouvel appareil ou gabarit perdu). "
						+ "Débloquez la demande dans l'écran « Vérification faciale ».");
		return construireEtat(ligne, requete.deviceId());
	}

	// --- état exposé au mobile ---

	private EtatFacialMoi construireEtat(PresenceEnrolementFacial ligne, String deviceId) {
		Etat reglage = reglageService.etat();
		String courante = versionCourante();
		StatutEnrolement statut = statut(ligne);
		boolean enroleOuAReenroler = statut == StatutEnrolement.ENROLE || statut == StatutEnrolement.A_REENROLER;

		boolean reconsentement = reglage.actif() && enroleOuAReenroler && courante != null
				&& !courante.equals(ligne.getVersionConsentement());
		boolean ecran = reglage.actif() && (statut == StatutEnrolement.NON_ENROLE || reconsentement);

		Boolean appareilCorrespond = null;
		if (ligne != null && ligne.getDeviceId() != null && deviceId != null) {
			appareilCorrespond = ligne.getDeviceId().equalsIgnoreCase(deviceId);
		}
		boolean incompatible = ligne != null && deviceId != null && deviceId.equalsIgnoreCase(ligne.getDeviceIdIncompatible());
		boolean gabaritAEffacer = !enroleOuAReenroler || Boolean.FALSE.equals(appareilCorrespond);

		Instant maintenant = clock.instant();
		boolean bloque = ligne != null && ligne.getBloqueJusqua() != null && ligne.getBloqueJusqua().isAfter(maintenant);
		return new EtatFacialMoi(
				reglage.actif(),
				reglage.controleExige(),
				reglage.dateMiseEnService(),
				courante,
				statut,
				enroleOuAReenroler ? ligne.getVersionConsentement() : null,
				reconsentement,
				ecran,
				appareilCorrespond,
				incompatible,
				gabaritAEffacer,
				ligne == null ? 0 : echecsEffectifs(ligne, maintenant),
				bloque ? ligne.getBloqueJusqua() : null,
				ligne != null ? ligne.getDecideLe() : null);
	}

	/** Compteur du jour : un échec d'un jour précédent ne compte plus (remise à zéro à minuit, heure de Tunis). */
	private int echecsEffectifs(PresenceEnrolementFacial ligne, Instant maintenant) {
		if (ligne.getDernierEchecLe() == null) {
			return ligne.getEchecsConsecutifs();
		}
		LocalDate jourEchec = LocalDate.ofInstant(ligne.getDernierEchecLe(), clock.getZone());
		return jourEchec.equals(LocalDate.ofInstant(maintenant, clock.getZone())) ? ligne.getEchecsConsecutifs() : 0;
	}

	// --- utilitaires ---

	private void exigerActive() {
		if (!reglageService.etat().actif()) {
			throw PresenceMetierException.conflict(INACTIVE, "La vérification faciale n'est pas activée");
		}
	}

	private String versionCourante() {
		return consentementService.courante().map(PresenceConsentementVersion::getVersion).orElse(null);
	}

	private void exigerVersionCourante(String version) {
		String courante = versionCourante();
		if (courante == null || !courante.equals(version)) {
			throw PresenceMetierException.unprocessable("VERSION_CONSENTEMENT_OBSOLETE",
					"Cette version du texte de consentement n'est plus la version courante");
		}
	}

	/**
	 * Création concurrente de la ligne du compte (contrainte unique) : 409 plutôt que 500, l'appli relit son état.
	 */
	private PresenceEnrolementFacial enregistrer(PresenceEnrolementFacial ligne) {
		if (ligne.getId() != null) {
			return enrolementRepository.save(ligne);
		}
		try {
			return enrolementRepository.saveAndFlush(ligne);
		} catch (DataIntegrityViolationException e) {
			throw PresenceMetierException.conflict("DECISION_DEJA_ENREGISTREE",
					"Une décision vient d'être enregistrée pour ce compte : relisez votre état");
		}
	}

	private static StatutEnrolement statut(PresenceEnrolementFacial ligne) {
		return ligne == null ? StatutEnrolement.NON_ENROLE : ligne.getStatut();
	}

	private static PresenceEnrolementFacial nouvelleLigne(Identite identite, Instant maintenant) {
		PresenceEnrolementFacial ligne = new PresenceEnrolementFacial();
		ligne.setCompteId(identite.compteId());
		ligne.setCollaborateurId(identite.ficheId());
		ligne.setStatut(StatutEnrolement.NON_ENROLE);
		ligne.setCreeLe(maintenant);
		ligne.setModifieLe(maintenant);
		return ligne;
	}

	/** Instantané de la fiche et du valideur D-H03 au dernier appel du collaborateur (alerte et déblocage). */
	private static void majInstantane(PresenceEnrolementFacial ligne, Identite identite) {
		ValideurPointage fiche = identite.fiche();
		ligne.setCollaborateurId(identite.ficheId());
		ligne.setCollaborateurNom(fiche.collaborateurNom());
		ligne.setValideurId(texte(fiche.valideurId()));
		ligne.setValideurCompteId(texte(fiche.valideurCompteId()));
		ligne.setValideurNom(fiche.valideurNom());
	}

	/** L'appareil enrôlé est invalidé : le gabarit local est effacé à la prochaine synchronisation ({@code /moi}). */
	private static void invaliderAppareil(PresenceEnrolementFacial ligne) {
		ligne.setDeviceId(null);
		ligne.setDeviceIdDemande(null);
		reinitialiserCompteur(ligne);
	}

	private static void reinitialiserCompteur(PresenceEnrolementFacial ligne) {
		ligne.setEchecsConsecutifs(0);
		ligne.setDernierEchecLe(null);
		ligne.setBloqueJusqua(null);
	}

	private static void toucher(PresenceEnrolementFacial ligne, Instant maintenant) {
		ligne.setModifieLe(maintenant);
	}
}
