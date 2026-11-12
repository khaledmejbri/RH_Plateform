package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.Affectation;
import com.hr.presence.domain.SignalVigilance;
import com.hr.presence.dto.DemandeManuelleResponse;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresencePointageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Construit la ressource {@link DemandeManuelleResponse} (liste et détail, contrat de l'annexe A de l'ADR). Deux vues :
 * le demandeur ne voit ni signaux, ni compteurs, ni plafond, ni commentaire du valideur (D-P24.d) ; qui décide et le
 * back-office voient tout. Aucune coordonnée (latitude / longitude) n'est jamais servie (C-L-03).
 */
@Component
public class DemandeManuelleReponseMapper {

	private static final Logger log = LoggerFactory.getLogger(DemandeManuelleReponseMapper.class);
	private static final ZoneId ZONE = ZoneId.of("Africa/Tunis");

	/** Qui lit la demande. */
	public enum Vue {
		/** Le collaborateur qui a fait la demande. */
		DEMANDEUR,
		/** Valideur attendu, RH, ADMIN, DIRECTION. */
		DECIDEUR
	}

	private final PresenceDemandeManuelleRepository demandeRepository;
	private final PresencePointageRepository pointageRepository;
	private final ReferentielClient referentielClient;
	private final PresenceParametresService parametres;

	public DemandeManuelleReponseMapper(PresenceDemandeManuelleRepository demandeRepository,
										PresencePointageRepository pointageRepository, ReferentielClient referentielClient,
										PresenceParametresService parametres) {
		this.demandeRepository = demandeRepository;
		this.pointageRepository = pointageRepository;
		this.referentielClient = referentielClient;
		this.parametres = parametres;
	}

	/** Réponses par lot : noms et matricules résolus par le serveur (D-L01.a), un seul appel au référentiel. */
	public List<DemandeManuelleResponse> versReponses(List<PresenceDemandeManuelle> demandes, Vue vue,
													  Function<PresenceDemandeManuelle, String> motifRejet) {
		Map<String, Affectation> identites = identites(demandes);
		Map<String, Long> memoMois = new HashMap<>();
		Map<String, Long> memoJour = new HashMap<>();
		return demandes.stream()
				.map(d -> versReponse(d, identites.get(d.getCollaborateurId()), vue, motifRejet.apply(d), memoMois, memoJour))
				.toList();
	}

	/** Une réponse avec une identité déjà résolue (détail). */
	public DemandeManuelleResponse versReponse(PresenceDemandeManuelle d, Affectation identite, Vue vue, String motifRejet) {
		return versReponse(d, identite, vue, motifRejet, new HashMap<>(), new HashMap<>());
	}

	private DemandeManuelleResponse versReponse(PresenceDemandeManuelle d, Affectation identite, Vue vue, String motifRejet,
												Map<String, Long> memoMois, Map<String, Long> memoJour) {
		String nom = d.getCollaborateurNom() != null ? d.getCollaborateurNom()
				: identite != null && identite.nom() != null ? identite.nom() : "Collaborateur introuvable";
		boolean decideur = vue == Vue.DECIDEUR;
		Long duMois = decideur ? memoMois.computeIfAbsent(d.getCollaborateurId() + "|" + debutMois(d.getHeureDeclaree()),
				k -> manuelsDuMois(d.getCollaborateurId(), d.getHeureDeclaree())) : null;
		Long refusDuJour = decideur ? memoJour.computeIfAbsent(d.getCollaborateurId() + "|" + d.getHeureDeclaree().atZone(ZONE).toLocalDate(),
				k -> refusDuJour(d.getCollaborateurId(), d.getHeureDeclaree())) : null;
		int seuil = parametres.seuilPointagesManuelsMois();
		List<DemandeManuelleResponse.Signal> signaux = decideur ? libellesSignaux(d) : null;
		return new DemandeManuelleResponse(
				d.getId(), d.getNumero(), d.getStatut(), d.getType(), d.getHeureDeclaree(), d.getJustification(),
				d.getCollaborateurId(), nom, identite != null ? identite.matricule() : null, d.getSiteId(),
				d.getPointageRejeteId(), motifRejet, d.getValideurNom(), d.validationParRh(), d.getDecidePar(),
				d.getDecideLe(), d.getMotifRefus(), d.getPointageId(), d.getCreeLe(),
				duMois, duMois != null ? duMois > seuil : null,
				parametres.libelleStatutDemande(d.getStatut()),
				d.getRefusStatut(), libelleRefus(d.getRefusStatut()),
				d.getDistanceMetres(), d.getPrecisionMetres(), d.getRayonMetres(),
				d.getModeQr(), LibellesPresence.modeQr(d.getModeQr()),
				decideur ? d.getDelaiDemandeMinutes() : null, d.getEcheance(),
					decideur ? d.getDelaiDecisionMinutes() : null,
				decideur ? d.getPlafondMetres() : null,
				decideur ? d.isReprise() : null,
				decideur ? !d.getSignaux().isEmpty() : null,
				signaux,
				decideur ? seuil : null,
				refusDuJour,
				decideur ? d.getCommentaireValideur() : null);
	}

	/** Pointages manuels validés dans le mois civil (Tunis) de la référence (alerte RH D-P04, signal D-P24.a). */
	public long manuelsDuMois(String collaborateurId, Instant reference) {
		ZonedDateTime debut = reference.atZone(ZONE).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
		return demandeRepository.compterValideesSurPeriode(
				collaborateurId, debut.toInstant(), debut.plusMonths(1).toInstant());
	}

	private static String debutMois(Instant reference) {
		return reference.atZone(ZONE).withDayOfMonth(1).toLocalDate().toString();
	}

	/** Compteur D-P20 : refus de position de la journée (Tunis) de la tentative, tous sites. */
	private long refusDuJour(String collaborateurId, Instant reference) {
		LocalDate jour = reference.atZone(ZONE).toLocalDate();
		return pointageRepository.countByCollaborateurIdAndStatutInAndServerTsGreaterThanEqualAndServerTsLessThan(
				collaborateurId, AlerteEchecsPointageService.STATUTS_COMPTES,
				jour.atStartOfDay(ZONE).toInstant(), jour.plusDays(1).atStartOfDay(ZONE).toInstant());
	}

	private String libelleRefus(String code) {
		return parametres.libelleStatutPointage(code);
	}

	/** Signaux en clair, rédigés d'après les valeurs figées (D-P24.a) ; liste vide si aucun. */
	List<DemandeManuelleResponse.Signal> libellesSignaux(PresenceDemandeManuelle d) {
		List<DemandeManuelleResponse.Signal> liste = new ArrayList<>();
		for (SignalVigilance s : d.getSignaux()) {
			liste.add(new DemandeManuelleResponse.Signal(s.name(), libelle(s, d)));
		}
		return liste;
	}

	private String libelle(SignalVigilance signal, PresenceDemandeManuelle d) {
		String zone = d.getRayonMetres() != null ? " (zone de " + d.getRayonMetres() + " m)" : "";
		return switch (signal) {
			case DISTANCE_SUP_2X_RAYON -> "Position à "
					+ (d.getDistanceMetres() != null ? LibellesPresence.metres(d.getDistanceMetres()) : "?") + " du site" + zone;
			case PRECISION_SUP_RAYON -> "Précision GPS de ± "
					+ (d.getPrecisionMetres() != null ? LibellesPresence.metres(d.getPrecisionMetres()) : "?")
					+ ", supérieure à la zone du site" + zone;
			case SITE_QR_PAPIER -> "Site à QR papier : le code peut avoir été photographié";
			case MANUELS_MOIS_SEUIL -> "Seuil de " + parametres.seuilPointagesManuelsMois()
					+ " pointages manuels validés dans le mois atteint";
			case REFUS_FACIAL -> "Refus d'origine : vérification faciale";
			case DEMANDE_TARDIVE -> "Demande faite "
					+ (d.getDelaiDemandeMinutes() != null ? LibellesPresence.duree(d.getDelaiDemandeMinutes()) : "tardivement")
					+ " après la tentative";
		};
	}

	/** Identités (matricule, nom) des collaborateurs des demandes, indexées par l'identifiant stocké (fiche ou compte). */
	public Map<String, Affectation> identites(List<PresenceDemandeManuelle> demandes) {
		List<UUID> ids = demandes.stream().map(PresenceDemandeManuelle::getCollaborateurId).distinct()
				.map(DemandeManuelleReponseMapper::uuidOuNull).filter(Objects::nonNull).toList();
		Map<String, Affectation> resultat = new HashMap<>();
		if (ids.isEmpty()) {
			return resultat;
		}
		try {
			for (Affectation a : referentielClient.affectations(ids)) {
				if (a.collaborateurId() != null) {
					resultat.put(a.collaborateurId().toString(), a);
				}
				if (a.compteId() != null) {
					resultat.put(a.compteId().toString(), a);
				}
			}
		} catch (RuntimeException ex) {
			log.warn("Identités des demandes manuelles non résolues : {}", ex.getMessage());
		}
		return resultat;
	}

	private static UUID uuidOuNull(String valeur) {
		try {
			return valeur == null ? null : UUID.fromString(valeur);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
