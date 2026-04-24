package com.hr.referentiel.service;

import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.repository.CollaborateurRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CollaborateurConnecteService {

	private final CollaborateurRepository collaborateurRepository;

	public CollaborateurConnecteService(CollaborateurRepository collaborateurRepository) {
		this.collaborateurRepository = collaborateurRepository;
	}

	public Collaborateur exigerCollaborateur(Jwt jwt) {
		return resoudre(jwt).orElseThrow(() -> new AccessDeniedException(
				"Profil collaborateur introuvable : renseignez compte_utilisateur_id sur le collaborateur "
						+ "ou utilisez un nom d'utilisateur JWT égal au matricule."));
	}

	public Optional<Collaborateur> resoudre(Jwt jwt) {
		String idUser = jwt.getClaimAsString("identifiant_utilisateur");
		if (idUser != null && !idUser.isBlank()) {
			try {
				Optional<Collaborateur> parCompte = collaborateurRepository
						.findByCompteUtilisateurId(UUID.fromString(idUser));
				if (parCompte.isPresent()) {
					return parCompte;
				}
			} catch (IllegalArgumentException ignored) {
				// ignore
			}
		}
		String sub = jwt.getSubject();
		if (sub != null && !sub.isBlank()) {
			return collaborateurRepository.findDetailByMatricule(sub);
		}
		return Optional.empty();
	}
}
