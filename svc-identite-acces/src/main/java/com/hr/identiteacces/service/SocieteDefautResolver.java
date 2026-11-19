package com.hr.identiteacces.service;

import com.hr.identiteacces.config.SocieteProprietes;
import com.hr.identiteacces.entity.Societe;
import com.hr.identiteacces.repository.SocieteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Résout l'identifiant de la société par défaut pour la transition D-SOC03.e (appels internes et Kafka sans société
 * tant que W1 et W2 ne sont pas livrés).
 */
@Component
public class SocieteDefautResolver {

	private static final Logger log = LoggerFactory.getLogger(SocieteDefautResolver.class);

	private final SocieteRepository societeRepository;
	private final String codeDefaut;
	private final boolean repliDefautTransition;

	@Autowired
	public SocieteDefautResolver(
			SocieteRepository societeRepository,
			SocieteProprietes societeProprietes) {
		this.societeRepository = societeRepository;
		this.codeDefaut = societeProprietes.getDefaut().getCode();
		this.repliDefautTransition = societeProprietes.isRepliDefautTransition();
	}

	public boolean isRepliDefautTransition() {
		return repliDefautTransition;
	}

	public String getCodeDefaut() {
		return codeDefaut;
	}

	/**
	 * Renvoie l'identifiant de la société par défaut si et seulement si {@code app.societe.repli-defaut-transition=true},
	 * sinon {@code null}. Utilisé par {@code SocieteFiltreInterne}.
	 */
	@Transactional(readOnly = true)
	public UUID resoudreIdSiTransitionActive() {
		if (!repliDefautTransition) {
			return null;
		}
		return societeRepository.findByCodeIgnoreCase(codeDefaut)
				.map(Societe::getId)
				.orElse(null);
	}

	/**
	 * Résout l'identifiant de la société par défaut en journalisant un WARN pour un appel ou événement sans société
	 * pendant la transition D-SOC03.e. Renvoie {@link Optional#empty()} si la propriété de transition vaut {@code false}.
	 */
	@Transactional(readOnly = true)
	public Optional<UUID> resoudrePourTransitionAvecWarn(String origine) {
		if (!repliDefautTransition) {
			return Optional.empty();
		}
		Optional<UUID> id = societeRepository.findByCodeIgnoreCase(codeDefaut).map(Societe::getId);
		id.ifPresent(uuid -> log.warn(
				"{} sans société : repli sur la société par défaut {} ({}), à supprimer après W2",
				origine, codeDefaut, uuid));
		return id;
	}
}
