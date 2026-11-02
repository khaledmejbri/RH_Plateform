package com.hr.presence.security;

import com.hr.presence.client.ReferentielClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Identité de l'appelant pour la trace (auteur d'une modification d'horaire, d'une décision) : compte lu uniquement
 * dans le JWT, nom lu au référentiel avec le JWT de l'appelant ; un compte sans fiche (ex. ADMIN) reste valide.
 */
@Component
public class ActeurResolver {

	/** Auteur d'une action : compte (JWT), fiche éventuelle, libellé d'affichage. */
	public record Acteur(String compteId, String ficheId, String login, String nom) {
		public String libelle() {
			return nom != null ? nom : (login != null ? login : compteId);
		}
	}

	private final PresenceAccessService accessService;
	private final ReferentielClient referentielClient;

	public ActeurResolver(PresenceAccessService accessService, ReferentielClient referentielClient) {
		this.accessService = accessService;
		this.referentielClient = referentielClient;
	}

	/** Sans appel réseau : compte et identifiant de connexion du jeton. */
	public Acteur depuisJwt() {
		return new Acteur(accessService.currentCompteId(), null, login(), null);
	}

	/** Avec la fiche (nom, identifiant) quand elle existe ; sinon l'acteur du jeton seul. */
	public Acteur avecFiche() {
		try {
			ReferentielClient.ValideurPointage moi = referentielClient.monValideurPointage();
			return new Acteur(accessService.currentCompteId(), moi.collaborateurId().toString(), login(),
					moi.collaborateurNom());
		} catch (RuntimeException ex) {
			return depuisJwt();
		}
	}

	private static String login() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
			return jwt.getSubject();
		}
		return auth != null ? auth.getName() : null;
	}
}
