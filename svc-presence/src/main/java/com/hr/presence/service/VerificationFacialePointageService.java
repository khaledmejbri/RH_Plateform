package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.ActionAuditFacial;
import com.hr.presence.domain.PlateformeMobile;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.StatutEnrolement;
import com.hr.presence.domain.VerificationFaciale;
import com.hr.presence.dto.CreatePointageRequest;
import com.hr.presence.dto.FacialMobileDtos;
import com.hr.presence.entity.PresenceEnrolementFacial;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.repository.EnrolementFacialRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PointageBloqueException;
import com.hr.presence.web.PresenceMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.hr.presence.service.FacialIdentiteService.texte;

/**
 * Étape « visage » du pointage (D-P08 à D-P10), appelée par {@link PresencePointageService}.
 * <p>
 * Deux temps, dans la transaction du pointage : {@link #preparer} (avant tout contrôle QR / GPS : preuve mal formée,
 * identité, blocage) puis {@link #decider} (après un QR / GPS valides : table de décision statut × appareil × preuve,
 * compteur d'échecs, blocage, alerte). Le serveur décide ; il ne reçoit qu'un score, le résultat du vivant et un
 * {@code device_id}. Réglage INACTIVE, ou ACTIVE avant la date : aucun effet (comportement antérieur).
 */
@Service
public class VerificationFacialePointageService {

	private static final Logger log = LoggerFactory.getLogger(VerificationFacialePointageService.class);
	private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH);

	/** Contexte du pointage en cours : ligne d'enrôlement verrouillée (si elle existe) et fiche résolue. */
	public record Preparation(boolean controleExige, int seuil, String compteId, String collaborateurId,
			PresenceEnrolementFacial enrolement) {
		static Preparation inactive(String collaborateurId) {
			return new Preparation(false, 0, null, collaborateurId, null);
		}
	}

	/** Issue de l'étape visage, à appliquer au pointage. */
	public record Decision(PointageStatut statut, String motif, VerificationFaciale indicateur, Short score,
			Boolean vivacite, String modeleVersion, PlateformeMobile plateforme, Integer echecs, Instant bloqueJusqua) {

		static Decision sansControle() {
			return new Decision(PointageStatut.VALIDE, null, null, null, null, null, null, null, null);
		}

		public boolean rejet() {
			return statut != PointageStatut.VALIDE;
		}

		public void appliquerA(PresencePointage pointage) {
			pointage.setStatut(statut);
			pointage.setMotifRejet(motif);
			pointage.setVerificationFaciale(indicateur);
			pointage.setScoreFacial(score);
			pointage.setVivaciteReussie(vivacite);
			pointage.setModeleFacialVersion(modeleVersion);
			pointage.setPlateforme(plateforme);
		}
	}

	private final ReglageFacialService reglageService;
	private final EnrolementFacialRepository enrolementRepository;
	private final PresenceAccessService accessService;
	private final ReferentielClient referentielClient;
	private final FacialAuditService auditService;
	private final PresenceEventPublisher eventPublisher;
	private final PresenceProperties properties;
	private final Clock clock;

	public VerificationFacialePointageService(
			ReglageFacialService reglageService,
			EnrolementFacialRepository enrolementRepository,
			PresenceAccessService accessService,
			ReferentielClient referentielClient,
			FacialAuditService auditService,
			PresenceEventPublisher eventPublisher,
			PresenceProperties properties,
			Clock clock) {
		this.reglageService = reglageService;
		this.enrolementRepository = enrolementRepository;
		this.accessService = accessService;
		this.referentielClient = referentielClient;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Avant tout contrôle QR / GPS (D-P08.a) : 400 {@code PREUVE_FACIALE_INVALIDE}, 403 {@code IDENTITE_INCOHERENTE}.
	 * Le 409 {@code POINTAGE_BLOQUE_VISAGE} suit via {@link #controlerBlocage} (rien n'est persisté, rien n'est compté).
	 */
	public Preparation preparer(String collaborateurDemande, CreatePointageRequest requete, Instant maintenant) {
		ReglageFacialService.Etat reglage = reglageService.etat();
		if (!reglage.controleExige()) {
			return Preparation.inactive(collaborateurDemande);
		}
		verifierFormatPreuve(requete);

		String compteId = accessService.currentCompteId();
		if (compteId == null) {
			throw new SecurityException("Impossible d'identifier le compte courant");
		}
		// Verrou pessimiste : sérialise les pointages d'un même compte et protège le compteur d'échecs.
		PresenceEnrolementFacial ligne = enrolementRepository.findByCompteIdForUpdate(compteId).orElse(null);
		// La fiche du pointage est celle de l'enrôlement ; sans ligne, elle se lit au référentiel avec le JWT.
		String fiche = ligne != null
				? ligne.getCollaborateurId()
				: texte(referentielClient.monValideurPointage().collaborateurId());
		String entete = accessService.collaborateurHeaderOuNull();
		if (entete != null && !entete.equalsIgnoreCase(fiche)) {
			throw new PresenceMetierException(FacialIdentiteService.IDENTITE_INCOHERENTE,
					"X-Collaborateur-Id ne correspond pas au compte connecté", 403);
		}
		return new Preparation(true, reglage.seuilScore(), compteId, fiche, ligne);
	}

	/** Après un QR / GPS valides : table de décision de l'ADR (ordre ABSENTE, VIVACITE, NON_RECONNU, VERIFIEE). */
	public Decision decider(Preparation preparation, CreatePointageRequest requete, Instant maintenant) {
		if (!preparation.controleExige()) {
			return Decision.sansControle();
		}
		PresenceEnrolementFacial ligne = preparation.enrolement();
		StatutEnrolement statut = ligne == null ? StatutEnrolement.NON_ENROLE : ligne.getStatut();
		boolean web = requete.plateforme() == PlateformeMobile.WEB;
		String appareil = requete.deviceId();

		switch (statut) {
			case NON_ENROLE -> {
				boolean incompatible = ligne != null && appareil != null
						&& appareil.equalsIgnoreCase(ligne.getDeviceIdIncompatible());
				if (web || incompatible) {
					return valide(VerificationFaciale.NON_DISPONIBLE, requete);
				}
				return neutre("Terminez votre enrôlement facial pour pointer", requete);
			}
			case REFUSE, REVOQUE -> {
				return valide(VerificationFaciale.NON_CONSENTIE, requete);
			}
			default -> {
				return decisionEnrole(preparation, ligne, requete, maintenant, web);
			}
		}
	}

	private Decision decisionEnrole(Preparation preparation, PresenceEnrolementFacial ligne,
			CreatePointageRequest requete, Instant maintenant, boolean web) {
		boolean appareilEnrole = !web && requete.deviceId() != null && requete.deviceId().equalsIgnoreCase(ligne.getDeviceId());
		Boolean vivacite = requete.vivaciteReussie();
		Integer score = requete.scoreFacial();
		boolean preuveAbsente = vivacite == null || (vivacite && score == null);
		if (!appareilEnrole || preuveAbsente) {
			return neutre(appareilEnrole
					? "Vérification du visage absente : mettez à jour l'application et réessayez"
					: "Pointez depuis l'appareil enrôlé (ou demandez un ré-enrôlement)", requete);
		}
		if (!vivacite) {
			return echec(PointageStatut.REJETE_VIVACITE, "Contrôle du vivant échoué", ligne, requete, maintenant);
		}
		if (score < preparation.seuil()) {
			return echec(PointageStatut.REJETE_VISAGE_NON_RECONNU, "Visage non reconnu", ligne, requete, maintenant);
		}
		// Réussite : le compteur repart de zéro (D-P10.1).
		if (ligne.getEchecsConsecutifs() != 0 || ligne.getDernierEchecLe() != null) {
			ligne.setEchecsConsecutifs(0);
			ligne.setDernierEchecLe(null);
			ligne.setModifieLe(maintenant);
			enrolementRepository.save(ligne);
		}
		return new Decision(PointageStatut.VALIDE, null, VerificationFaciale.VERIFIEE, score.shortValue(), Boolean.TRUE,
				requete.modeleFacialVersion(), requete.plateforme(), 0, null);
	}

	/** + 1 échec ; au seuil : blocage, audit, alerte au responsable D-H03 (une seule fois), compteur remis à zéro. */
	private Decision echec(PointageStatut statut, String motif, PresenceEnrolementFacial ligne,
			CreatePointageRequest requete, Instant maintenant) {
		var zone = clock.getZone();
		LocalDate aujourdhui = LocalDate.ofInstant(maintenant, zone);
		if (ligne.getDernierEchecLe() == null || !LocalDate.ofInstant(ligne.getDernierEchecLe(), zone).equals(aujourdhui)) {
			ligne.setEchecsConsecutifs(0);
		}
		int echecs = ligne.getEchecsConsecutifs() + 1;
		ligne.setDernierEchecLe(maintenant);
		ligne.setModifieLe(maintenant);
		Instant bloqueJusqua = null;
		if (echecs >= properties.getFacial().getEchecsMax()) {
			bloqueJusqua = maintenant.plusSeconds(properties.getFacial().getBlocageMinutes() * 60L);
			ligne.setBloqueJusqua(bloqueJusqua);
			ligne.setEchecsConsecutifs(0); // le début du blocage équivaut à sa fin (D-P10.a)
			enrolementRepository.save(ligne);
			auditService.tracer(ActionAuditFacial.BLOCAGE_ECHECS, ligne.getId(), ligne.getStatut(), ligne.getStatut(),
					ligne.getDeviceId(), ligne.getVersionConsentement(),
					echecs + " échecs faciaux consécutifs : pointage bloqué jusqu'à " + HEURE.format(bloqueJusqua.atZone(zone)));
			alerterResponsable(ligne, bloqueJusqua);
			log.warn("Blocage de pointage facial compte={} jusqu'à {}", ligne.getCompteId(), bloqueJusqua);
		} else {
			ligne.setEchecsConsecutifs(echecs);
			enrolementRepository.save(ligne);
		}
		return new Decision(statut, motif, null, requete.scoreFacial() != null ? requete.scoreFacial().shortValue() : null,
				requete.vivaciteReussie(), requete.modeleFacialVersion(), requete.plateforme(), echecs, bloqueJusqua);
	}

	/** Alerte au compte du responsable D-H03 ; repli sur l'instantané, puis destinataire {@code RH} ; jamais ADMIN. */
	private void alerterResponsable(PresenceEnrolementFacial ligne, Instant bloqueJusqua) {
		try {
			ValideurPointage frais = referentielClient.monValideurPointage();
			ligne.setValideurId(texte(frais.valideurId()));
			ligne.setValideurCompteId(texte(frais.valideurCompteId()));
			ligne.setValideurNom(frais.valideurNom());
			if (frais.collaborateurNom() != null) {
				ligne.setCollaborateurNom(frais.collaborateurNom());
			}
			enrolementRepository.save(ligne);
		} catch (RuntimeException ex) {
			log.warn("Responsable D-H03 illisible, repli sur l'instantané compte={} : {}", ligne.getCompteId(), ex.getMessage());
		}
		String destinataire = ligne.getValideurId() == null
				? PresenceEventPublisher.DESTINATAIRE_RH
				: (ligne.getValideurCompteId() != null ? ligne.getValideurCompteId() : ligne.getValideurId());
		String nom = ligne.getCollaborateurNom() != null ? ligne.getCollaborateurNom() : "Un collaborateur";
		eventPublisher.notifier(destinataire, "Échecs de vérification du visage — " + nom,
				nom + " a échoué " + properties.getFacial().getEchecsMax() + " fois de suite à la vérification du visage : "
						+ "son pointage est bloqué jusqu'à " + HEURE.format(bloqueJusqua.atZone(clock.getZone()))
						+ ". Un pointage manuel reste possible.");
	}

	/**
	 * 409 si un blocage est en cours. Appelé par le pointage après le contrôle d'identité et la relecture
	 * d'idempotence : un rejeu du même pointage renvoie sa réponse d'origine même pendant le blocage.
	 */
	public void controlerBlocage(Preparation preparation, Instant maintenant) {
		if (preparation.controleExige() && preparation.enrolement() != null) {
			verifierPasBloque(preparation.enrolement(), maintenant);
		}
	}

	private void verifierPasBloque(PresenceEnrolementFacial ligne, Instant maintenant) {
		boolean statutControle = ligne.getStatut() == StatutEnrolement.ENROLE || ligne.getStatut() == StatutEnrolement.A_REENROLER;
		if (statutControle && ligne.getBloqueJusqua() != null && ligne.getBloqueJusqua().isAfter(maintenant)) {
			log.warn("Pointage refusé : blocage facial en cours compte={} jusqu'à {}", ligne.getCompteId(), ligne.getBloqueJusqua());
			throw new PointageBloqueException(
					"Pointage bloqué après des échecs de vérification du visage jusqu'à "
							+ HEURE.format(ligne.getBloqueJusqua().atZone(clock.getZone()))
							+ " : utilisez le pointage manuel si nécessaire",
					ligne.getBloqueJusqua());
		}
	}

	/** Requête mal formée : score hors 0-100, ou score sans résultat du vivant (D-P08.b) : 400, jamais compté. */
	private static void verifierFormatPreuve(CreatePointageRequest requete) {
		Integer score = requete.scoreFacial();
		if (score != null && (score < 0 || score > 100)) {
			throw preuveInvalide("Le score facial doit être compris entre 0 et 100");
		}
		String appareil = requete.deviceId();
		if (appareil != null && !appareil.matches(FacialMobileDtos.UUID_REGEX)) {
			throw preuveInvalide("device_id doit être un UUID");
		}
		if (score != null && requete.vivaciteReussie() == null) {
			throw preuveInvalide("Le résultat du contrôle du vivant est obligatoire avec un score facial");
		}
	}

	private static PresenceMetierException preuveInvalide(String message) {
		return new PresenceMetierException("PREUVE_FACIALE_INVALIDE", message, 400);
	}

	/** Rejet neutre pour le compteur (preuve absente, autre appareil, web, enrôlement à terminer). */
	private static Decision neutre(String motif, CreatePointageRequest requete) {
		return new Decision(PointageStatut.REJETE_VERIFICATION_FACIALE_ABSENTE, motif, null, null, null, null,
				requete.plateforme(), null, null);
	}

	/** Pointage VALIDE sans contrôle du visage (refus, retrait, appareil incompatible, web) : rien de biométrique stocké. */
	private static Decision valide(VerificationFaciale indicateur, CreatePointageRequest requete) {
		return new Decision(PointageStatut.VALIDE, null, indicateur, null, null, null, requete.plateforme(), null, null);
	}
}
