package com.hr.referentiel.service;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Auteur d'une modification de paramètre, toujours tiré du JWT (jamais d'un en-tête ni du corps) : compte lu dans le
 * claim {@code identifiant_utilisateur}, nom lu sur la fiche liée. Sans fiche : « Utilisateur sans fiche » (jamais
 * d'identifiant technique, D-L01.a).
 */
@Component
public class AuteurParametreResolver {

	static final String NOM_SANS_FICHE = "Utilisateur sans fiche";

	private final CollaborateurConnecteService collaborateurConnecteService;

	public AuteurParametreResolver(CollaborateurConnecteService collaborateurConnecteService) {
		this.collaborateurConnecteService = collaborateurConnecteService;
	}

	public AuteurParametre depuis(Jwt jwt) {
		UUID compte = null;
		String brut = jwt.getClaimAsString("identifiant_utilisateur");
		if (brut != null && !brut.isBlank()) {
			try {
				compte = UUID.fromString(brut);
			} catch (IllegalArgumentException ignore) {
				// claim mal formé : auteur sans compte
			}
		}
		String nom = collaborateurConnecteService.resoudre(jwt)
				.map(c -> ((c.getPrenom() != null ? c.getPrenom() : "") + " "
						+ (c.getNom() != null ? c.getNom() : "")).trim())
				.filter(n -> !n.isEmpty())
				.orElse(NOM_SANS_FICHE);
		return new AuteurParametre(compte, nom);
	}
}
