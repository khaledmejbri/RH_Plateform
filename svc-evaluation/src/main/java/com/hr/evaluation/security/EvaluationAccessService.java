package com.hr.evaluation.security;

import com.hr.evaluation.entity.Evaluation;
import com.hr.evaluation.entity.EvaluationRh;
import com.hr.evaluation.repository.EvaluationRepository;
import com.hr.evaluation.repository.EvaluationRhRepository;
import com.hr.evaluation.web.EvaluationMetierException;
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
 * L'identité vient uniquement du JWT (compte, fiche résolue au référentiel). L'en-tête mobile
 * {@code X-Collaborateur-Id} n'est jamais une preuve : différent de l'identité du JWT, il donne 403
 * {@code IDENTITE_INCOHERENTE}.
 */
@Service("evaluationAccess")
public class EvaluationAccessService {

	private final EvaluationRepository evaluationRepository;
	private final EvaluationRhRepository evaluationRhRepository;
	private final IdentiteAppelantResolver identiteResolver;

	public EvaluationAccessService(
			EvaluationRepository evaluationRepository,
			EvaluationRhRepository evaluationRhRepository,
			IdentiteAppelantResolver identiteResolver) {
		this.evaluationRepository = evaluationRepository;
		this.evaluationRhRepository = evaluationRhRepository;
		this.identiteResolver = identiteResolver;
	}

	public boolean isBackofficeLecture() {
		return hasAnyRole("RH", "DIRECTION", "ADMIN");
	}

	public boolean isBackofficeEcriture() {
		return hasAnyRole("RH", "ADMIN");
	}

	/**
	 * Identité courante pour ownership : fiche RH de l'appelant résolue depuis le JWT (référentiel {@code /moi}), repli
	 * sur le compte du JWT. {@code X-Collaborateur-Id} n'est pas une preuve : absent, on utilise la fiche du JWT ;
	 * présent et différent, 403 {@code IDENTITE_INCOHERENTE}.
	 */
	public UUID requireCurrentActorId() {
		IdentiteAppelant appelant = identiteResolver.courant();
		UUID acteur = appelant.ficheJwt().orElse(appelant.compteJwt());
		if (acteur == null) {
			throw new SecurityException("Impossible d'identifier l'acteur courant");
		}
		verifierEnTete(acteur);
		return acteur;
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
				.orElseThrow(() -> new IllegalArgumentException("Évaluation introuvable"));
		assertParticipantOf(evaluation.getCollaborateurIdentifiant(), evaluation.getSuperieurIdentifiant());
	}

	/**
	 * Participant (collaborateur ou RO) reconnu par le JWT et le snapshot de compte de l'évaluation. L'en-tête
	 * {@code X-Collaborateur-Id} n'est plus une preuve : s'il est présent et diffère de la fiche de l'acteur reconnu,
	 * 403 {@code IDENTITE_INCOHERENTE} (SCRUM-56, comme SCRUM-52).
	 */
	public Evaluation requireParticipantEvaluation(UUID evaluationId) {
		Evaluation evaluation = loadEvaluation(evaluationId);
		IdentiteAppelant appelant = identiteResolver.courant();
		if (appelant.estCollaborateur(evaluation)) {
			verifierEnTete(evaluation.getCollaborateurIdentifiant());
		} else if (appelant.estSuperieur(evaluation)) {
			verifierEnTete(evaluation.getSuperieurIdentifiant());
		} else {
			throw new SecurityException("Accès non autorisé à cette évaluation");
		}
		return evaluation;
	}

	public Evaluation requireCollaborateurEvaluation(UUID evaluationId) {
		Evaluation evaluation = loadEvaluation(evaluationId);
		if (!identiteResolver.courant().estCollaborateur(evaluation)) {
			throw new SecurityException("Seul le collaborateur évalué peut effectuer cette action");
		}
		verifierEnTete(evaluation.getCollaborateurIdentifiant());
		return evaluation;
	}

	public Evaluation requireManagerEvaluation(UUID evaluationId) {
		Evaluation evaluation = loadEvaluation(evaluationId);
		if (!identiteResolver.courant().estSuperieur(evaluation)) {
			throw new SecurityException("Seul le supérieur de l'évaluation peut effectuer cette action");
		}
		verifierEnTete(evaluation.getSuperieurIdentifiant());
		return evaluation;
	}

	/** En-tête mobile présent et différent de la fiche de l'acteur reconnu : usurpation probable, rien n'est fait. */
	private void verifierEnTete(UUID ficheActeur) {
		UUID entete = readCollaborateurHeader();
		if (entete != null && !entete.equals(ficheActeur)) {
			throw EvaluationMetierException.forbidden("IDENTITE_INCOHERENTE",
					"L'identité annoncée ne correspond pas à celle du jeton.");
		}
	}

	public void assertIsCollaborateurOfRh(UUID evaluationId) {
		EvaluationRh evaluation = evaluationRhRepository.findById(evaluationId)
				.orElseThrow(() -> new IllegalArgumentException("Évaluation introuvable"));
		if (!actorMatches(evaluation.getCollaborateurIdentifiant())) {
			throw new SecurityException("Seul le collaborateur évalué peut valider cette évaluation");
		}
	}

	public void assertIsSuperieurOfRh(UUID evaluationId) {
		EvaluationRh evaluation = evaluationRhRepository.findById(evaluationId)
				.orElseThrow(() -> new IllegalArgumentException("Évaluation introuvable"));
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
		IdentiteAppelant appelant = identiteResolver.courant();
		if (readCollaborateurHeader() != null) {
			UUID acteur = appelant.ficheJwt().orElse(appelant.compteJwt());
			verifierEnTete(acteur);
		}
		if (expected == null) {
			return false;
		}
		return appelant.correspond(null, expected);
	}

	private Evaluation loadEvaluation(UUID evaluationId) {
		return evaluationRepository.findByIdWithCampaign(evaluationId)
				.orElseThrow(() -> new IllegalArgumentException("Évaluation introuvable"));
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
