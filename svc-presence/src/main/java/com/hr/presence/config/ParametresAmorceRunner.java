package com.hr.presence.config;

import com.hr.presence.domain.NatureParametre;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.ReglagePresence;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.entity.PresenceParametreValeur;
import com.hr.presence.entity.PresenceParametres;
import com.hr.presence.repository.PresenceParametreValeurRepository;
import com.hr.presence.repository.PresenceParametresRepository;
import com.hr.presence.service.LibellesPresence;
import com.hr.presence.service.PresenceParametresService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Amorçage idempotent des paramètres de présence (ADR lot 3 § 8), rejoué à chaque démarrage : une ligne par valeur
 * d'enum manquante (un statut ajouté au code apparaît seul) et la ligne unique des réglages, remplie depuis les
 * propriétés actuelles. Une ligne déjà présente n'est jamais réécrite (la RH a pu la modifier) ; une ligne dont le code
 * n'existe plus dans l'enum est laissée et signalée en WARN. Une panne n'empêche pas le démarrage.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ParametresAmorceRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ParametresAmorceRunner.class);

	private static final Map<String, String> LIBELLES_STATUTS = Map.of(
			"EN_ATTENTE", "En attente", "VALIDEE", "Validée", "REFUSEE", "Refusée", "ANNULEE", "Annulée", "EXPIREE", "Expirée");

	private final PresenceParametreValeurRepository valeurRepository;
	private final PresenceParametresRepository parametresRepository;
	private final PresenceProperties properties;
	private final PresenceParametresService parametresService;

	public ParametresAmorceRunner(PresenceParametreValeurRepository valeurRepository,
								  PresenceParametresRepository parametresRepository,
								  PresenceProperties properties,
								  PresenceParametresService parametresService) {
		this.valeurRepository = valeurRepository;
		this.parametresRepository = parametresRepository;
		this.properties = properties;
		this.parametresService = parametresService;
	}

	@Override
	public void run(ApplicationArguments args) {
		try {
			amorcerReglages();
			amorcer(PresenceParametresService.STATUT_POINTAGE, codes(PointageStatut.values()),
					code -> LibellesPresence.statut(PointageStatut.valueOf(code)),
					ParametresAmorceRunner::aidePointage, ParametresAmorceRunner::couleurPointage);
			amorcer(PresenceParametresService.STATUT_DEMANDE_MANUELLE, codes(StatutDemandeManuelle.values()),
					ParametresAmorceRunner::libelleStatut, code -> null, ParametresAmorceRunner::couleurStatut);
			amorcer(PresenceParametresService.STATUT_HEURES_SUP, codes(StatutHeuresSup.values()),
					ParametresAmorceRunner::libelleStatut, code -> null, ParametresAmorceRunner::couleurStatut);
		} catch (RuntimeException ex) {
			log.warn("Amorçage des paramètres de présence incomplet : {}", ex.getMessage());
		} finally {
			// la reprise des demandes (SmartInitializingSingleton) a pu lire un instantané antérieur à l'amorçage
			parametresService.invaliderApresCommit();
		}
	}

	private void amorcerReglages() {
		PresenceParametres existante = parametresRepository.findById(PresenceParametres.ID_UNIQUE).orElse(null);
		if (existante != null) {
			completerReglagesManuel(existante);
			return;
		}
		PresenceParametres ligne = new PresenceParametres();
		ligne.setSeuilPointagesManuelsMois(borne(ReglagePresence.SEUIL_POINTAGES_MANUELS_MOIS, properties.getManuel().getSeuilAlerteMois()));
		ligne.setDelaiGraceRetardMinutes(borne(ReglagePresence.DELAI_GRACE_RETARD_MINUTES, properties.getRetard().getDelaiGraceMinutes()));
		ligne.setRayonDefautExterieurMetres(borne(ReglagePresence.RAYON_DEFAUT_EXTERIEUR_METRES,
				properties.getGeofence().getDefaultRadiusExterieurMetres()));
		ligne.setRayonDefautInterieurMetres(borne(ReglagePresence.RAYON_DEFAUT_INTERIEUR_METRES,
				properties.getGeofence().getDefaultRadiusInterieurMetres()));
		remplirReglagesManuel(ligne);
		ligne.setModifieLe(Instant.now());
		ligne.setModifiePar("Système (automatique)");
		try {
			parametresRepository.saveAndFlush(ligne);
		} catch (RuntimeException ex) {
			log.info("Réglages de présence déjà amorcés par une autre instance");
		}
	}

	/** Les trois réglages D-P22 / D-P23 viennent des propriétés ; une valeur déjà posée n'est jamais réécrite. */
	private void remplirReglagesManuel(PresenceParametres ligne) {
		if (ligne.getPlafondDistanceManuelMetres() == null) {
			ligne.setPlafondDistanceManuelMetres(borne(ReglagePresence.PLAFOND_DISTANCE_MANUEL_METRES,
					properties.getManuel().getPlafondDistanceMetres()));
		}
		if (ligne.getDelaiDemandeManuelMinutes() == null) {
			ligne.setDelaiDemandeManuelMinutes(borne(ReglagePresence.DELAI_DEMANDE_MANUEL_MINUTES,
					properties.getManuel().getDelaiDemandeMinutes()));
		}
		if (ligne.getDelaiDecisionManuelMinutes() == null) {
			ligne.setDelaiDecisionManuelMinutes(borne(ReglagePresence.DELAI_DECISION_MANUEL_MINUTES,
					properties.getManuel().getDelaiDecisionMinutes()));
		}
	}

	/** Base déjà amorcée par le lot 3 : complète les colonnes ajoutées par D-P22 / D-P23, sans toucher aux autres. */
	private void completerReglagesManuel(PresenceParametres ligne) {
		if (ligne.getPlafondDistanceManuelMetres() != null && ligne.getDelaiDemandeManuelMinutes() != null
				&& ligne.getDelaiDecisionManuelMinutes() != null) {
			return;
		}
		remplirReglagesManuel(ligne);
		try {
			parametresRepository.saveAndFlush(ligne);
		} catch (RuntimeException ex) {
			log.info("Réglages du pointage manuel déjà complétés par une autre instance");
		}
	}

	private void amorcer(String referentiel, List<String> codes, Function<String, String> libelle,
						 Function<String, String> aide, Function<String, String> couleur) {
		List<PresenceParametreValeur> existantes = valeurRepository.findByReferentielOrderByOrdreAscCodeAsc(referentiel);
		Set<String> codesExistants = new HashSet<>();
		Set<String> libellesPris = new HashSet<>();
		existantes.forEach(e -> {
			codesExistants.add(e.getCode());
			libellesPris.add(e.getLibelleNormalise());
		});
		int ordre = existantes.stream().mapToInt(PresenceParametreValeur::getOrdre).max().orElse(0);
		for (int i = 0; i < codes.size(); i++) {
			String code = codes.get(i);
			if (codesExistants.contains(code)) {
				continue;
			}
			String texte = libelle.apply(code);
			if (libellesPris.contains(normaliser(texte))) {
				texte = texte + " (" + code + ")";
			}
			PresenceParametreValeur v = new PresenceParametreValeur();
			v.setReferentiel(referentiel);
			v.setCode(code);
			v.setNature(NatureParametre.COMPORTEMENT);
			v.setLibelle(texte);
			v.setLibelleNormalise(normaliser(texte));
			v.setDescription(aide.apply(code));
			v.setCouleur(couleur.apply(code));
			v.setOrdre(++ordre);
			v.setActif(true);
			v.setSysteme(true);
			v.setPremiereUtilisationLe(Instant.now());
			try {
				valeurRepository.saveAndFlush(v);
				libellesPris.add(v.getLibelleNormalise());
			} catch (RuntimeException ex) {
				log.info("Valeur {} / {} déjà amorcée par une autre instance", referentiel, code);
			}
		}
		existantes.stream().filter(e -> !codes.contains(e.getCode()))
				.forEach(e -> log.warn("Paramètre {} / {} : le code n'existe plus dans l'enum, ligne laissée", referentiel, e.getCode()));
	}

	private static List<String> codes(Enum<?>[] valeurs) {
		return java.util.Arrays.stream(valeurs).map(Enum::name).toList();
	}

	/** Ramène la valeur initiale dans les bornes du réglage ; un écart est signalé en WARN (D-P22.b). */
	private static int borne(ReglagePresence r, int valeur) {
		int bornee = Math.clamp(valeur, r.min(), r.max());
		if (bornee != valeur) {
			log.warn("Valeur initiale {} du réglage {} hors des bornes [{} ; {}] : ramenée à {}",
					valeur, r, r.min(), r.max(), bornee);
		}
		return bornee;
	}

	static String normaliser(String libelle) {
		return Normalizer.normalize(libelle, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
				.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
	}

	private static String libelleStatut(String code) {
		String connu = LIBELLES_STATUTS.get(code);
		if (connu != null) {
			return connu;
		}
		String texte = code.toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
	}

	private static String couleurStatut(String code) {
		return switch (code) {
			case "VALIDEE" -> "VERT";
			case "REFUSEE" -> "ROUGE";
			case "EN_ATTENTE", "EXPIREE" -> "AMBRE";
			default -> "NEUTRE";
		};
	}

	private static String couleurPointage(String code) {
		return code.startsWith("VALIDE") ? "VERT" : "ROUGE";
	}

	/** Aide pour la RH : quand ce motif de refus apparaît. Nulle pour un statut sans explication à donner. */
	private static String aidePointage(String code) {
		return switch (code) {
			case "REJETE_QR_INVALIDE" -> "Le QR code scanné n'est lié à aucun site.";
			case "REJETE_QR_EXPIRE" -> "Le QR code a dépassé sa durée de validité.";
			case "REJETE_QR_REVOQUE" -> "Le QR code a été remplacé ou révoqué par la RH.";
			case "REJETE_HORS_ZONE" -> "Le collaborateur est en dehors du rayon du site.";
			case "REJETE_SITE_INACTIF" -> "Le site a été désactivé.";
			case "REJETE_EMPLACEMENT_ABSENT" -> "La position du site n'a pas encore été posée.";
			case "REJETE_PRECISION_GPS" -> "Le signal GPS est trop imprécis par rapport au rayon du site.";
			case "REJETE_POSITION_SIMULEE" -> "Une application de position fictive a été détectée.";
			case "REJETE_VISAGE_NON_RECONNU" -> "Le visage ne correspond pas à celui enrôlé.";
			case "REJETE_VIVACITE" -> "Le contrôle du vivant n'a pas abouti.";
			case "REJETE_VERIFICATION_FACIALE_ABSENTE" -> "La preuve de vérification faciale est absente ou invalide.";
			case "REJETE_HORS_HORAIRE" -> "Le pointage est hors de la fenêtre d'horaire du site.";
			default -> null;
		};
	}
}
