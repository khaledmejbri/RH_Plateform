package com.hr.evaluation.security;

import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationRh;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.EvaluationRhRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Résolution d'acteur + garde-fous anti-IDOR (M07).
 * <p>
 * L'app mobile envoie {@code X-Collaborateur-Id} (fiche RH). Le JWT porte
 * {@code identifiant_utilisateur} (compte). Les deux sont acceptés comme
 * candidats d'identité pour l'ownership.
 */
@Service("evaluationAccess")
public class EvaluationAccessService {

	private final EvaluationRepository evaluationRepository;
	private final EvaluationRhRepository evaluationRhRepository;

	public EvaluationAccessService(
			EvaluationRepository evaluationRepository,
			EvaluationRhRepository evaluationRhRepository) {
		this.evaluationRepository = evaluationRepository;
		this.evaluationRhRepository = evaluationRhRepository;
	}

	public boolean isBackofficeLecture() {
		return hasAnyRole("RH", "DIRECTION", "ADMIN");
	}

	public boolean isBackofficeEcriture() {
		return hasAnyRole("RH", "ADMIN");
	}

	/**
	 * Identité courante pour ownership (header collaborateur RH, sinon claims JWT).
	 */
	public UUID requireCurrentActorId() {
		UUID fromHeader = readCollaborateurHeader();
		if (fromHeader != null) {
			return fromHeader;
		}
		Set<UUID> fromJwt = jwtIdentityCandidates();
		if (fromJwt.isEmpty()) {
			throw new SecurityException("Impossible d'identifier l'acteur courant");
		}
		return fromJwt.iterator().next();
	}

	public void assertCanAccessCollaborateurScope(UUID collaborateurIdentifiant) {
		if (isBackofficeLecture()) {
			return;
		}
		if (!actorMatches(collaborateurIdentifiant)) {
			throw new SecurityException("Accès non autorisé aux évaluations de ce collaborateur");
		}
	}

	public void assertCanAccessSuperieurScope(UUID superieurIdentifiant) {
		if (isBackofficeLecture()) {
			return;
		}
		if (!actorMatches(superieurIdentifiant)) {
			throw new SecurityException("Accès non autorisé aux évaluations de ce supérieur");
		}
	}

	public void assertCanAccessEvaluationRh(UUID evaluationId) {
		if (isBackofficeLecture()) {
			return;
		}
		EvaluationRh evaluation = evaluationRhRepository.findById(evaluationId)
				.orElseThrow(() -> new IllegalArgumentException("Evaluation introuvable : " + evaluationId));
		assertParticipantOf(evaluation.getCollaborateurIdentifiant(), evaluation.getSuperieurIdentifiant());
	}

	public Evaluation requireParticipantEvaluation(UUID evaluationId) {
		Evaluation evaluation = loadEvaluation(evaluationId);
		assertParticipantOf(evaluation.getCollaborateurIdentifiant(), evaluation.getSuperieurIdentifiant());
		return evaluation;
	}

	public Evaluation requireCollaborateurEvaluation(UUID evaluationId) {
		Evaluation evaluation = loadEvaluation(evaluationId);
		if (!actorMatches(evaluation.getCollaborateurIdentifiant())) {
			throw new SecurityException("Seul le collaborateur évalué peut effectuer cette action");
		}
		return evaluation;
	}

	public Evaluation requireManagerEvaluation(UUID evaluationId) {
		Evaluation evaluation = loadEvaluation(evaluationId);
		if (!actorMatches(evaluation.getSuperieurIdentifiant())) {
			throw new SecurityException("Seul le supérieur de l'évaluation peut effectuer cette action");
		}
		return evaluation;
	}

	public void assertIsCollaborateurOfRh(UUID evaluationId) {
		EvaluationRh evaluation = evaluationRhRepository.findById(evaluationId)
				.orElseThrow(() -> new IllegalArgumentException("Evaluation introuvable : " + evaluationId));
		if (!actorMatches(evaluation.getCollaborateurIdentifiant())) {
			throw new SecurityException("Seul le collaborateur évalué peut valider cette évaluation");
		}
	}

	public void assertIsSuperieurOfRh(UUID evaluationId) {
		EvaluationRh evaluation = evaluationRhRepository.findById(evaluationId)
				.orElseThrow(() -> new IllegalArgumentException("Evaluation introuvable : " + evaluationId));
		if (!actorMatches(evaluation.getSuperieurIdentifiant())) {
			throw new SecurityException("Seul le supérieur renseigné peut valider cette évaluation");
		}
	}

	private void assertParticipantOf(UUID collaborateurId, UUID superieurId) {
		if (actorMatches(collaborateurId) || actorMatches(superieurId)) {
			return;
		}
		throw new SecurityException("Accès non autorisé à cette évaluation");
	}

	private boolean actorMatches(UUID expected) {
		if (expected == null) {
			return false;
		}
		UUID header = readCollaborateurHeader();
		if (header != null && header.equals(expected)) {
			return true;
		}
		return jwtIdentityCandidates().contains(expected);
	}

	private Evaluation loadEvaluation(UUID evaluationId) {
		return evaluationRepository.findByIdWithCampaign(evaluationId)
				.orElseThrow(() -> new IllegalArgumentException("Évaluation introuvable: " + evaluationId));
	}

	/**
	 * Compte de l'appelant lu uniquement dans le JWT (claim {@code identifiant_utilisateur}, sinon {@code sub}),
	 * sans l'en-tête {@code X-Collaborateur-Id} fourni par le client. Pour tracer l'auteur d'une écriture.
	 */
	public UUID requireJwtActorId() {
		Set<UUID> fromJwt = jwtIdentityCandidates();
		if (fromJwt.isEmpty()) {
			throw new SecurityException("Impossible d'identifier l'acteur courant");
		}
		return fromJwt.iterator().next();
	}

	private UUID readCollaborateurHeader() {
		var attrs = RequestContextHolder.getRequestAttributes();
		if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
			return null;
		}
		String header = servletAttrs.getRequest().getHeader("X-Collaborateur-Id");
		if (header == null || header.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(header.trim());
		} catch (IllegalArgumentException e) {
			throw new SecurityException("X-Collaborateur-Id invalide");
		}
	}

	private Set<UUID> jwtIdentityCandidates() {
		Set<UUID> out = new LinkedHashSet<>();
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return out;
		}
		Object principal = authentication.getPrincipal();
		if (!(principal instanceof Jwt jwt)) {
			return out;
		}
		addUuid(out, jwt.getClaimAsString("identifiant_utilisateur"));
		addUuid(out, jwt.getSubject());
		return out;
	}

	private static void addUuid(Set<UUID> out, String raw) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		try {
			out.add(UUID.fromString(raw.trim()));
		} catch (IllegalArgumentException ignored) {
			// subject may be a username
		}
	}

	private boolean hasAnyRole(String... roles) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return false;
		}
		Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
		if (authorities == null || authorities.isEmpty()) {
			return false;
		}
		for (String role : roles) {
			String expected = "ROLE_" + role;
			for (GrantedAuthority authority : authorities) {
				if (expected.equals(authority.getAuthority())) {
					return true;
				}
			}
		}
		return false;
	}
}
