package com.hr.workflow.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Caches Caffeine du moteur de workflow (§ 9.11, D-W3-01 : clés sans société).
 */
@Configuration
public class CacheConfig {

	public static final String CACHE_VERSIONS_PUBLIEES = "versions-publiees";
	public static final String CACHE_CATALOGUE = "catalogue";
	public static final String CACHE_CALENDRIER_SOCIETE = "calendrier-societe";
	public static final String CACHE_ACTIVATIONS = "activations";
	public static final String CACHE_COLLABORATEUR_PAR_COMPTE = "collaborateur-par-compte";

	@Bean
	public CacheManager cacheManager() {
		SimpleCacheManager manager = new SimpleCacheManager();
		manager.setCaches(List.of(
				new CaffeineCache(CACHE_VERSIONS_PUBLIEES,
						Caffeine.newBuilder().maximumSize(1_000).build()),
				new CaffeineCache(CACHE_CATALOGUE,
						Caffeine.newBuilder().maximumSize(20).expireAfterWrite(Duration.ofMinutes(5)).build()),
				new CaffeineCache(CACHE_CALENDRIER_SOCIETE,
						Caffeine.newBuilder().maximumSize(50).expireAfterWrite(Duration.ofMinutes(5)).build()),
				new CaffeineCache(CACHE_ACTIVATIONS,
						Caffeine.newBuilder().maximumSize(50).expireAfterWrite(Duration.ofSeconds(30)).build()),
				new CaffeineCache(CACHE_COLLABORATEUR_PAR_COMPTE,
						Caffeine.newBuilder().maximumSize(2_000).expireAfterWrite(Duration.ofSeconds(60)).build())
		));
		return manager;
	}
}
