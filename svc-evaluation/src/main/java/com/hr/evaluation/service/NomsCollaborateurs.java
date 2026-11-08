package com.hr.evaluation.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hr.evaluation.client.PersonneResumee;
import com.hr.evaluation.client.ReferentielNomsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Résolveur par lot des noms, matricules et rattachements (D-L01.a, ADR lot 2 § 1). Une page de 50 lignes coûte au
 * plus un appel au référentiel (zéro si tout est en cache). Les identifiants peuvent être des fiches ou des comptes.
 * <p>
 * Lecture dégradée : si le référentiel ne répond pas, on renvoie ce qui est en cache et les autres identifiants sont
 * absents du résultat (l'appelant retombe sur l'instantané local, sinon sur {@code null}). Jamais d'exception pour
 * une liste. Une personne inconnue n'est pas mise en cache.
 */
@Component
public class NomsCollaborateurs {

	private static final Logger log = LoggerFactory.getLogger(NomsCollaborateurs.class);

	private final ReferentielNomsClient client;
	private final Cache<UUID, PersonneResumee> cache;

	public NomsCollaborateurs(
			ReferentielNomsClient client,
			@Value("${evaluation.noms.cache-ttl-minutes}") long ttlMinutes,
			@Value("${evaluation.noms.cache-taille-max}") long tailleMax) {
		this.client = client;
		this.cache = Caffeine.newBuilder()
				.expireAfterWrite(Duration.ofMinutes(ttlMinutes))
				.maximumSize(tailleMax)
				.build();
	}

	/** Personnes connues parmi les identifiants demandés (clé = identifiant tel que demandé). */
	public Map<UUID, PersonneResumee> resoudre(Collection<UUID> fichesOuComptes) {
		Set<UUID> demandes = new LinkedHashSet<>();
		for (UUID id : fichesOuComptes) {
			if (id != null) {
				demandes.add(id);
			}
		}
		Map<UUID, PersonneResumee> resultat = new HashMap<>();
		List<UUID> manquants = new ArrayList<>();
		for (UUID id : demandes) {
			PersonneResumee connue = cache.getIfPresent(id);
			if (connue != null) {
				resultat.put(id, connue);
			} else {
				manquants.add(id);
			}
		}
		for (int debut = 0; debut < manquants.size(); debut += ReferentielNomsClient.LOT_MAX) {
			List<UUID> lot = manquants.subList(debut, Math.min(manquants.size(), debut + ReferentielNomsClient.LOT_MAX));
			try {
				for (PersonneResumee personne : client.resoudre(lot)) {
					memoriser(personne);
				}
			} catch (RuntimeException ex) {
				log.warn("Noms indisponibles au référentiel ({} identifiants) : {}", lot.size(), ex.getMessage());
				break;
			}
		}
		for (UUID id : manquants) {
			PersonneResumee lue = cache.getIfPresent(id);
			if (lue != null) {
				resultat.put(id, lue);
			}
		}
		return resultat;
	}

	private void memoriser(PersonneResumee personne) {
		if (personne.collaborateurId() != null) {
			cache.put(personne.collaborateurId(), personne);
		}
		if (personne.compteId() != null) {
			cache.put(personne.compteId(), personne);
		}
	}

	/** Nom lisible : le référentiel, sinon l'instantané local, sinon {@code null} (jamais un identifiant). */
	public static String nom(Map<UUID, PersonneResumee> personnes, UUID id, String instantane) {
		PersonneResumee p = id == null ? null : personnes.get(id);
		if (p != null && p.nomComplet() != null && !p.nomComplet().isBlank()) {
			return p.nomComplet();
		}
		return instantane != null && !instantane.isBlank() ? instantane : null;
	}

	public static String matricule(Map<UUID, PersonneResumee> personnes, UUID id) {
		PersonneResumee p = id == null ? null : personnes.get(id);
		return p != null ? p.matricule() : null;
	}
}
