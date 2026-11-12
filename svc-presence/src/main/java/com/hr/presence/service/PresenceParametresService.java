package com.hr.presence.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.domain.TypeLieu;
import com.hr.presence.dto.ParametresDtos.ValeurLibelle;
import com.hr.presence.entity.PresenceParametreValeur;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.entity.PresenceParametres;
import com.hr.presence.repository.PresenceParametreValeurRepository;
import com.hr.presence.repository.PresenceParametresRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lecture des paramètres de présence (ADR lot 3 § 4) : un instantané immuable de tous les libellés et réglages, tenu
 * dans un cache Caffeine dédié (une clé, expiration 30 s). Toute lecture métier passe par lui ; une écriture
 * l'invalide après commit. Une autre instance voit le changement au plus tard à l'expiration (&lt; 1 min, D-R02.j).
 * Table ou base indisponible : repli sur les propriétés et les libellés d'origine, jamais d'échec du pointage.
 */
@Service
public class PresenceParametresService {

	private static final Logger log = LoggerFactory.getLogger(PresenceParametresService.class);

	public static final String STATUT_POINTAGE = "STATUT_POINTAGE";
	public static final String STATUT_DEMANDE_MANUELLE = "STATUT_DEMANDE_MANUELLE";
	public static final String STATUT_HEURES_SUP = "STATUT_HEURES_SUP";
	public static final List<String> REFERENTIELS = List.of(STATUT_POINTAGE, STATUT_DEMANDE_MANUELLE, STATUT_HEURES_SUP);

	private static final String CLE = "instantane";
	static final String VALEUR_NON_RECONNUE = "Valeur non reconnue";

	/** Réglages en vigueur (valeurs entières déjà résolues, repli sur les propriétés). */
	public record Reglages(int seuilPointagesManuelsMois, int delaiGraceRetardMinutes, int rayonDefautExterieurMetres,
						   int rayonDefautInterieurMetres, int plafondDistanceManuelMetres,
						   int delaiDemandeManuelMinutes, int delaiDecisionManuelMinutes) {
	}

	/** Copie immuable de tous les paramètres, partagée entre les requêtes. */
	public record Instantane(Reglages reglages, Map<String, List<ValeurLibelle>> referentiels, String etag) {

		public ValeurLibelle valeur(String referentiel, String code) {
			return referentiels.getOrDefault(referentiel, List.of()).stream()
					.filter(v -> v.code().equals(code)).findFirst().orElse(null);
		}
	}

	private final PresenceParametreValeurRepository valeurRepository;
	private final PresenceParametresRepository parametresRepository;
	private final PresenceProperties properties;
	private final LoadingCache<String, Instantane> cache;

	public PresenceParametresService(
			PresenceParametreValeurRepository valeurRepository,
			PresenceParametresRepository parametresRepository,
			PresenceProperties properties,
			@Value("${app.parametres.cache-ttl-secondes:30}") long ttlSecondes) {
		this.valeurRepository = valeurRepository;
		this.parametresRepository = parametresRepository;
		this.properties = properties;
		long ttl = Math.max(1, ttlSecondes);
		this.cache = Caffeine.newBuilder()
				.expireAfterWrite(Duration.ofSeconds(ttl))
				.refreshAfterWrite(Duration.ofMillis(ttl * 2000 / 3))
				.build(cle -> charger());
	}

	// ------------------------------------------------------------------ instantané

	/** Instantané courant ; jamais nul (repli en cas d'erreur de lecture). */
	public Instantane instantane() {
		try {
			Instantane i = cache.get(CLE);
			if (i != null) {
				return i;
			}
		} catch (RuntimeException ex) {
			log.warn("Paramètres de présence illisibles, repli sur les valeurs d'origine : {}", ex.getMessage());
		}
		return new Instantane(reglagesParDefaut(), Map.of(), "W/\"repli\"");
	}

	/** Vide le cache local, après commit si une transaction est en cours. */
	public void invaliderApresCommit() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					cache.invalidateAll();
				}
			});
		} else {
			cache.invalidateAll();
		}
	}

	private Instantane charger() {
		PresenceParametres ligne = parametresRepository.findById(PresenceParametres.ID_UNIQUE).orElse(null);
		Reglages reglages = ligne == null ? reglagesParDefaut() : new Reglages(
				valeurOu(ligne.getSeuilPointagesManuelsMois(), properties.getManuel().getSeuilAlerteMois()),
				valeurOu(ligne.getDelaiGraceRetardMinutes(), properties.getRetard().getDelaiGraceMinutes()),
				valeurOu(ligne.getRayonDefautExterieurMetres(),
						properties.getGeofence().getDefaultRadiusExterieurMetres()),
				valeurOu(ligne.getRayonDefautInterieurMetres(),
						properties.getGeofence().getDefaultRadiusInterieurMetres()),
				valeurOu(ligne.getPlafondDistanceManuelMetres(), properties.getManuel().getPlafondDistanceMetres()),
				valeurOu(ligne.getDelaiDemandeManuelMinutes(), properties.getManuel().getDelaiDemandeMinutes()),
				valeurOu(ligne.getDelaiDecisionManuelMinutes(), properties.getManuel().getDelaiDecisionMinutes()));
		Map<String, List<ValeurLibelle>> referentiels = new LinkedHashMap<>();
		for (String ref : REFERENTIELS) {
			referentiels.put(ref, valeurRepository.findByReferentielOrderByOrdreAscCodeAsc(ref).stream()
					.map(PresenceParametresService::versLibelle).toList());
		}
		String etag = "W/\"" + Integer.toHexString(31 * reglages.hashCode() + referentiels.hashCode()) + "\"";
		return new Instantane(reglages, Map.copyOf(referentiels), etag);
	}

	private static ValeurLibelle versLibelle(PresenceParametreValeur v) {
		return new ValeurLibelle(v.getCode(), v.getLibelle(), v.getDescription(), v.getCouleur(), v.getOrdre(), v.isActif());
	}

	private static int valeurOu(Integer valeur, int repli) {
		return valeur != null ? valeur : repli;
	}

	private Reglages reglagesParDefaut() {
		return new Reglages(properties.getManuel().getSeuilAlerteMois(), properties.getRetard().getDelaiGraceMinutes(),
				properties.getGeofence().getDefaultRadiusExterieurMetres(),
				properties.getGeofence().getDefaultRadiusInterieurMetres(),
				properties.getManuel().getPlafondDistanceMetres(), properties.getManuel().getDelaiDemandeMinutes(),
				properties.getManuel().getDelaiDecisionMinutes());
	}

	// ------------------------------------------------------------------ accesseurs métier

	/** Seuil mensuel de pointages manuels validés (D-P04), 1 à 31. */
	public int seuilPointagesManuelsMois() {
		return instantane().reglages().seuilPointagesManuelsMois();
	}

	/** Plafond de distance et de précision d'un refus rattrapable (D-P22), 100 à 1 000 m. */
	public int plafondDistanceManuelMetres() {
		return instantane().reglages().plafondDistanceManuelMetres();
	}

	/** Délai pour demander un pointage manuel après le refus (D-P23.a), 15 à 480 minutes. */
	public Duration delaiDemandeManuel() {
		return Duration.ofMinutes(instantane().reglages().delaiDemandeManuelMinutes());
	}

	/** Délai de décision avant expiration d'une demande (D-P23.b), 240 à 4 320 minutes. */
	public Duration delaiDecisionManuel() {
		return Duration.ofMinutes(instantane().reglages().delaiDecisionManuelMinutes());
	}

	/** Délai de grâce ajouté au début de l'horaire avant de compter un retard (D-P19.d), 0 à 60 minutes. */
	public Duration delaiGraceRetard() {
		return Duration.ofMinutes(instantane().reglages().delaiGraceRetardMinutes());
	}

	/** Rayon par défaut d'un nouveau site (D-P01), appliqué seulement à la création d'un site sans rayon fourni. */
	public int rayonDefaut(TypeLieu typeLieu) {
		Reglages r = instantane().reglages();
		return typeLieu == TypeLieu.INTERIEUR ? r.rayonDefautInterieurMetres() : r.rayonDefautExterieurMetres();
	}

	/** Libellé courant d'un statut de pointage (valeurs inactives comprises) ; repli sur le libellé d'origine. */
	public String libelleStatutPointage(PointageStatut statut) {
		if (statut == null) {
			return null;
		}
		ValeurLibelle v = instantane().valeur(STATUT_POINTAGE, statut.name());
		return v != null ? v.libelle() : LibellesPresence.statut(statut);
	}

	/** Idem pour un code stocké en texte (statut du refus figé) ; code inconnu : « Valeur non reconnue » (D-L01). */
	public String libelleStatutPointage(String code) {
		if (code == null) {
			return null;
		}
		try {
			return libelleStatutPointage(PointageStatut.valueOf(code));
		} catch (IllegalArgumentException ex) {
			return VALEUR_NON_RECONNUE;
		}
	}

	/** Libellé courant d'un statut de demande de pointage manuel ; repli sur le libellé d'origine. */
	public String libelleStatutDemande(StatutDemandeManuelle statut) {
		if (statut == null) {
			return null;
		}
		ValeurLibelle v = instantane().valeur(STATUT_DEMANDE_MANUELLE, statut.name());
		return v != null ? v.libelle() : LibellesPresence.statutDemande(statut);
	}

	/** Motif d'un pointage refusé, avec le libellé courant du statut en repli. */
	public String motifRejet(PresencePointage pointage, PresenceSite site) {
		return LibellesPresence.motifRejet(pointage, site, this::libelleStatutPointage);
	}

	/** Libellés courants des statuts de pointage, par code (un seul instantané pour toute une page). */
	public Map<PointageStatut, String> libellesStatutsPointage() {
		Instantane i = instantane();
		Map<PointageStatut, String> out = new HashMap<>();
		for (PointageStatut s : PointageStatut.values()) {
			ValeurLibelle v = i.valeur(STATUT_POINTAGE, s.name());
			out.put(s, v != null ? v.libelle() : LibellesPresence.statut(s));
		}
		return out;
	}
}
