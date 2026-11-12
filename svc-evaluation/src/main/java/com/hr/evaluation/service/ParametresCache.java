package com.hr.evaluation.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.hr.evaluation.entity.EvaluationParametres;
import com.hr.evaluation.entity.ParametreValeur;
import com.hr.evaluation.repository.EvaluationParametresRepository;
import com.hr.evaluation.repository.ParametreValeurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Cache Caffeine dédié des paramètres (ADR lot 3 § 4) : une seule clé, l'instantané complet.
 * {@code expireAfterWrite} = durée de vie maximale (30 s) ; {@code refreshAfterWrite} recharge en arrière-plan avant.
 * Écriture : invalidation locale après commit. Plusieurs instances : une autre instance voit le changement au plus tard
 * à l'expiration (&lt; 1 min, D-R02.j), sans diffusion.
 */
@Component
public class ParametresCache {

    private static final String CLE = "parametres";

    private final LoadingCache<String, ParametresInstantane> cache;

    @Autowired
    public ParametresCache(ParametreValeurRepository valeurs, EvaluationParametresRepository reglages,
                           @Value("${app.parametres.cache-ttl-secondes:30}") long ttlSecondes) {
        this(() -> charger(valeurs, reglages), ttlSecondes);
    }

    /** Constructeur de test : chargeur libre. */
    public ParametresCache(Supplier<ParametresInstantane> chargeur, long ttlSecondes) {
        long ttl = Math.max(1, ttlSecondes);
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttl))
                .refreshAfterWrite(Duration.ofSeconds(Math.max(1, ttl * 2 / 3)))
                .build(cle -> chargeur.get());
    }

    public ParametresInstantane obtenir() {
        return cache.get(CLE);
    }

    /** Invalidation immédiate (tests, amorçage). */
    public void invalider() {
        cache.invalidateAll();
    }

    /** Invalide après le commit de la transaction en cours ; immédiatement s'il n'y en a pas. */
    public void invaliderApresCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalider();
                }
            });
        } else {
            invalider();
        }
    }

    static ParametresInstantane charger(ParametreValeurRepository valeurs, EvaluationParametresRepository reglages) {
        List<ParametresInstantane.Valeur> lues = valeurs.findAll().stream()
                .sorted(Comparator.comparing(ParametreValeur::getReferentiel)
                        .thenComparingInt(ParametreValeur::getOrdre)
                        .thenComparing(ParametreValeur::getCode))
                .map(ParametresCache::copie)
                .toList();
        Integer seuil = reglages.findById(EvaluationParametres.IDENTIFIANT_UNIQUE)
                .map(EvaluationParametres::getSeuilInsuffisance).orElse(null);
        return ParametresInstantane.de(lues, seuil);
    }

    static ParametresInstantane.Valeur copie(ParametreValeur v) {
        return new ParametresInstantane.Valeur(v.getReferentiel(), v.getCode(), v.getNature(), v.getLibelle(),
                v.getDescription(), v.getCouleur(), v.getOrdre(), v.isActif(), v.isSysteme(), v.getSeuilEcart());
    }
}
