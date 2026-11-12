package com.hr.presence.service;

import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Éligibilité des refus à la lecture (D-P22.a) : une requête pour les sites, une pour les demandes existantes, puis le
 * verdict de {@link EligibiliteManuelService} pour chaque refus. Calculée à chaque lecture (le délai s'écoule).
 * Un pointage accepté n'a pas d'entrée : ses deux champs restent nuls.
 */
@Component
public class EligibiliteManuelLecture {

	private static final Logger log = LoggerFactory.getLogger(EligibiliteManuelLecture.class);

	/** {@code possible} vrai : motif nul ; faux : motif en clair. */
	public record Etat(boolean possible, String motif) {
	}

	private final EligibiliteManuelService eligibilite;
	private final PresenceSiteRepository siteRepository;
	private final PresenceDemandeManuelleRepository demandeRepository;
	private final Clock clock;

	public EligibiliteManuelLecture(EligibiliteManuelService eligibilite, PresenceSiteRepository siteRepository,
									PresenceDemandeManuelleRepository demandeRepository, Clock clock) {
		this.eligibilite = eligibilite;
		this.siteRepository = siteRepository;
		this.demandeRepository = demandeRepository;
		this.clock = clock;
	}

	public Map<UUID, Etat> pour(Collection<PresencePointage> pointages) {
		List<PresencePointage> refus = pointages.stream()
				.filter(p -> p.getStatut() != null && p.getStatut().name().startsWith("REJETE_")).toList();
		Map<UUID, Etat> resultat = new HashMap<>();
		if (refus.isEmpty()) {
			return resultat;
		}
		Map<UUID, PresenceSite> sites = new HashMap<>();
		List<UUID> siteIds = refus.stream().map(PresencePointage::getSiteId).filter(Objects::nonNull).distinct().toList();
		if (!siteIds.isEmpty()) {
			siteRepository.findAllById(siteIds).forEach(s -> sites.put(s.getId(), s));
		}
		Map<UUID, PresenceDemandeManuelle> demandes = new HashMap<>();
		demandeRepository.findByPointageRejeteIdIn(refus.stream().map(PresencePointage::getId).toList())
				.forEach(d -> demandes.put(d.getPointageRejeteId(), d));
		for (PresencePointage p : refus) {
			try {
				EligibiliteManuelService.Verdict v = eligibilite.evaluer(p,
						p.getSiteId() != null ? sites.get(p.getSiteId()) : null, demandes.get(p.getId()), clock.instant());
				resultat.put(p.getId(), new Etat(v.possible(), v.possible() ? null : v.motif()));
			} catch (RuntimeException ex) {
				// Lecture : une panne ne doit pas casser la liste ; le serveur refait le contrôle à la création.
				log.warn("Éligibilité du pointage {} non calculée : {}", p.getId(), ex.getMessage());
			}
		}
		return resultat;
	}
}
