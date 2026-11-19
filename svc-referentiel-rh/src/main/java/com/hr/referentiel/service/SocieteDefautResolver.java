package com.hr.referentiel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Résout l'identifiant de la société par défaut pour la transition D-SOC15 dans {@code svc-referentiel-rh}
 * (appels {@code /internal/referentiel/**} sans {@code X-Societe-Id} et messages Kafka sans en-tête).
 */
@Component
public class SocieteDefautResolver {

	public static final String CODE_SOCIETE_DEFAUT = "PRINCIPALE";

	private static final Logger log = LoggerFactory.getLogger(SocieteDefautResolver.class);

	private final IdentiteSocieteClient identiteSocieteClient;
	private final String codeDefaut;
	private final boolean repliDefautTransition;
	private volatile UUID idDefautEnCache;

	public SocieteDefautResolver(
			IdentiteSocieteClient identiteSocieteClient,
			@Value("${app.societe.defaut.code:${SOCIETE_DEFAUT_CODE:PRINCIPALE}}") String codeDefaut,
			@Value("${app.societe.repli-defaut-transition:${SOCIETE_REPLI_DEFAUT_TRANSITION:true}}") boolean repliDefautTransition) {
		this.identiteSocieteClient = identiteSocieteClient;
		this.codeDefaut = (codeDefaut == null || codeDefaut.isBlank())
				? CODE_SOCIETE_DEFAUT
				: codeDefaut.trim().toUpperCase(Locale.ROOT);
		this.repliDefautTransition = repliDefautTransition;
	}

	public boolean isRepliDefautTransition() {
		return repliDefautTransition;
	}

	public String getCodeDefaut() {
		return codeDefaut;
	}

	public void enregistrerIdDefaut(UUID id) {
		this.idDefautEnCache = id;
	}

	/**
	 * Renvoie l'identifiant de la société par défaut si {@code app.societe.repli-defaut-transition=true},
	 * sinon {@code null}. Utilisé par {@code SocieteFiltreInterne}.
	 */
	public UUID resoudreIdSiTransitionActive() {
		if (!repliDefautTransition) {
			return null;
		}
		if (idDefautEnCache != null) {
			return idDefautEnCache;
		}
		Optional<UUID> id = identiteSocieteClient.trouverIdParCode(codeDefaut);
		id.ifPresent(uuid -> this.idDefautEnCache = uuid);
		return id.orElse(null);
	}

	public Optional<UUID> resoudrePourTransitionAvecWarn(String origine) {
		if (!repliDefautTransition) {
			return Optional.empty();
		}
		UUID id = resoudreIdSiTransitionActive();
		if (id != null) {
			log.warn("{} sans société : repli sur la société par défaut {} ({}), à supprimer après W2",
					origine, codeDefaut, id);
		}
		return Optional.ofNullable(id);
	}
}
