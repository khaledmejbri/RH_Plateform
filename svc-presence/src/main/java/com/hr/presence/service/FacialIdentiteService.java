package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PresenceMetierException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Identité de l'appelant des endpoints faciaux : compte lu dans le JWT, fiche lue au référentiel avec ce même JWT.
 * Un {@code X-Collaborateur-Id} présent et différent de la fiche est refusé (403 IDENTITE_INCOHERENTE).
 */
@Service
public class FacialIdentiteService {

	public static final String IDENTITE_INCOHERENTE = "IDENTITE_INCOHERENTE";

	/** Compte (JWT) et fiche + valideur D-H03 (référentiel). */
	public record Identite(String compteId, ValideurPointage fiche) {
		public String ficheId() {
			return texte(fiche.collaborateurId());
		}
	}

	private final PresenceAccessService accessService;
	private final ReferentielClient referentielClient;

	public FacialIdentiteService(PresenceAccessService accessService, ReferentielClient referentielClient) {
		this.accessService = accessService;
		this.referentielClient = referentielClient;
	}

	public Identite resoudre() {
		String compteId = compteObligatoire();
		ValideurPointage fiche = referentielClient.monValideurPointage();
		verifierEntete(texte(fiche.collaborateurId()));
		return new Identite(compteId, fiche);
	}

	/** Compte du JWT, sinon refus (403). */
	public String compteObligatoire() {
		String compteId = accessService.currentCompteId();
		if (compteId == null) {
			throw new SecurityException("Impossible d'identifier le compte courant");
		}
		return compteId;
	}

	/** 403 IDENTITE_INCOHERENTE si l'en-tête {@code X-Collaborateur-Id} est présent et diffère de la fiche. */
	public void verifierEntete(String ficheId) {
		String entete = accessService.collaborateurHeaderOuNull();
		if (entete != null && !entete.equalsIgnoreCase(ficheId)) {
			throw new PresenceMetierException(IDENTITE_INCOHERENTE,
					"X-Collaborateur-Id ne correspond pas au compte connecté", 403);
		}
	}

	public static String texte(UUID id) {
		return id != null ? id.toString() : null;
	}
}
