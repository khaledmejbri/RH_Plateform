package com.hr.referentiel.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.hr.commun.societe.SocieteContexte;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Cache local en mémoire (Caffeine) : réduit la charge base pour lectures fréquentes.
 * En cluster multi-instances, TTL court + invalidation à l'écriture ; pour cache partagé, migrer vers Redis.
 */
@Configuration
public class CacheConfig {

	public static final String CACHE_UNITES = "unites";
	public static final String CACHE_COLLABORATEUR_ID = "collaborateurs-par-id";
	public static final String CACHE_COLLABORATEUR_MATRICULE = "collaborateurs-par-matricule";

	/** Instantané des paramètres (ADR-UAT-lot3 § 4) : cache dédié, durée de vie courte et propre (D-R02.j). */
	public static final String CACHE_PARAMETRES = "parametres";

	/** Clé de cache multi-société (§ 9.2.2) : isole systématiquement chaque entrée par {@code societeId}. */
	public record CleSociete(UUID societeId, Object cle) {
	}

	public static CleSociete cle(Object cle) {
		return new CleSociete(SocieteContexte.couranteOuVide().orElse(SocieteContexte.AUCUNE), cle);
	}

	/** Paramétrage des champs d'import par type (ADR-IMPORT-CHAMPS § 6) : même durée de vie courte. */
	public static final String CACHE_IMPORT_PARAMETRAGE = "import-parametrage";

	@Bean
	public CacheManager cacheManager(
			@Value("${app.parametres.cache-ttl-secondes:30}") long ttlParametresSecondes) {
		CaffeineCacheManager manager = new CaffeineCacheManager(
				CACHE_UNITES, CACHE_COLLABORATEUR_ID, CACHE_COLLABORATEUR_MATRICULE);
		manager.setCaffeine(Caffeine.newBuilder()
				.expireAfterWrite(5, TimeUnit.MINUTES)
				.maximumSize(20_000)
				.recordStats());
		// Hors de la spec commune (5 min) : un paramètre modifié doit être vu partout en moins d'une minute.
		manager.registerCustomCache(CACHE_PARAMETRES, Caffeine.newBuilder()
				.expireAfterWrite(ttlParametresSecondes, TimeUnit.SECONDS)
				.maximumSize(500)
				.build());
		manager.registerCustomCache(CACHE_IMPORT_PARAMETRAGE, Caffeine.newBuilder()
				.expireAfterWrite(ttlParametresSecondes, TimeUnit.SECONDS)
				.maximumSize(500)
				.build());
		return manager;
	}
}
