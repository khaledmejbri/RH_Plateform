package com.hr.presence.repository;

import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.VerificationFaciale;
import com.hr.presence.entity.PresencePointage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.criteria.Predicate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PresencePointageRepository extends JpaRepository<PresencePointage, UUID>,
		JpaSpecificationExecutor<PresencePointage> {

	Optional<PresencePointage> findByCollaborateurIdAndIdempotencyKey(String collaborateurId, String idempotencyKey);

	/** Pointages acceptés d'un site sur une période de journées de rattachement des collaborateurs demandés (fiche ou compte), pour le comptage des heures sup (D-P14). */
	List<PresencePointage> findBySiteIdAndJourneeBetweenAndStatutInAndCollaborateurIdInOrderByServerTsAsc(
			UUID siteId, java.time.LocalDate du, java.time.LocalDate au, java.util.Collection<PointageStatut> statuts,
			java.util.Collection<String> collaborateurIds);

	Page<PresencePointage> findByCollaborateurIdOrderByServerTsDesc(String collaborateurId, Pageable pageable);

	/**
	 * Pointages retenus pour la vue « Temps de travail » (D-P17.a) : statuts demandés, fenêtre sur l'heure serveur
	 * (marge autour de la période, la journée de travail est filtrée ensuite), site et identités (fiche ou compte)
	 * optionnels. Filtres construits dynamiquement (pas de paramètre NULL en JPQL sous PostgreSQL).
	 */
	static Specification<PresencePointage> filtreTempsTravail(
			java.util.Collection<PointageStatut> statuts, Instant from, Instant to, UUID siteId,
			java.util.Collection<String> collaborateurIds) {
		return (root, query, cb) -> {
			List<Predicate> predicats = new ArrayList<>();
			predicats.add(root.get("statut").in(statuts));
			predicats.add(cb.greaterThanOrEqualTo(root.get("serverTs"), from));
			predicats.add(cb.lessThan(root.get("serverTs"), to));
			if (siteId != null) predicats.add(cb.equal(root.get("siteId"), siteId));
			if (collaborateurIds != null) predicats.add(root.get("collaborateurId").in(collaborateurIds));
			return cb.and(predicats.toArray(Predicate[]::new));
		};
	}

	/**
	 * Recherche admin : filtres optionnels construits dynamiquement (SCRUM-11).
	 * Une requête JPQL {@code (:p is null or ...)} échouait sous PostgreSQL quand tous les filtres étaient NULL
	 * (« could not determine data type of parameter »).
	 */
	static Specification<PresencePointage> filtreAdmin(
			UUID siteId, PointageStatut statut, PointageType type, Instant from, Instant to) {
		return filtreAdmin(siteId, statut, type, from, to, null);
	}

	/** + filtre sur l'indicateur de vérification faciale (liste, ex. NON_CONSENTIE, NON_DISPONIBLE). */
	static Specification<PresencePointage> filtreAdmin(
			UUID siteId, PointageStatut statut, PointageType type, Instant from, Instant to,
			List<VerificationFaciale> verificationFaciale) {
		return filtreAdmin(siteId, statut, type, from, to, verificationFaciale, null);
	}

	/** + filtre sur la fiche du collaborateur (identifiant stocké en texte). */
	static Specification<PresencePointage> filtreAdmin(
			UUID siteId, PointageStatut statut, PointageType type, Instant from, Instant to,
			List<VerificationFaciale> verificationFaciale, UUID collaborateurId) {
		return filtreAdminIdentites(siteId, statut, type, from, to, verificationFaciale,
				collaborateurId == null ? null : java.util.Set.of(collaborateurId.toString()));
	}

	/** + filtre sur les identités d'un collaborateur (fiche et compte : les lignes anciennes portent l'un ou l'autre). */
	static Specification<PresencePointage> filtreAdminIdentites(
			UUID siteId, PointageStatut statut, PointageType type, Instant from, Instant to,
			List<VerificationFaciale> verificationFaciale, java.util.Collection<String> identites) {
		return (root, query, cb) -> {
			List<Predicate> predicats = new ArrayList<>();
			if (siteId != null) predicats.add(cb.equal(root.get("siteId"), siteId));
			if (identites != null) predicats.add(root.get("collaborateurId").in(identites));
			if (statut != null) predicats.add(cb.equal(root.get("statut"), statut));
			if (type != null) predicats.add(cb.equal(root.get("type"), type));
			if (from != null) predicats.add(cb.greaterThanOrEqualTo(root.get("serverTs"), from));
			if (to != null) predicats.add(cb.lessThan(root.get("serverTs"), to));
			if (verificationFaciale != null && !verificationFaciale.isEmpty()) {
				predicats.add(root.get("verificationFaciale").in(verificationFaciale));
			}
			return cb.and(predicats.toArray(Predicate[]::new));
		};
	}
}
