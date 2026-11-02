package com.hr.presence.repository;

import com.hr.presence.entity.PresenceHeuresSupJour;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PresenceHeuresSupJourRepository extends JpaRepository<PresenceHeuresSupJour, UUID>,
		JpaSpecificationExecutor<PresenceHeuresSupJour> {

	List<PresenceHeuresSupJour> findByDemandeId(UUID demandeId);

	List<PresenceHeuresSupJour> findByDemandeIdIn(Collection<UUID> demandeIds);

	void deleteByDemandeId(UUID demandeId);

	/** Lignes d'un pointeur (fiche ou compte, un pointage peut porter l'un ou l'autre) pour un site et des journées. */
	@Query("select j from PresenceHeuresSupJour j where j.siteId = :siteId and j.journee in :journees "
			+ "and (j.collaborateurId = :id or j.compteId = :id)")
	List<PresenceHeuresSupJour> findPourPointeur(
			@Param("siteId") UUID siteId, @Param("journees") Collection<LocalDate> journees, @Param("id") String id);

	/** Lignes de bénéficiaires (par fiche) sur une période, pour les contrôles de doublon et de chevauchement. */
	List<PresenceHeuresSupJour> findBySiteIdAndJourneeBetweenAndCollaborateurIdIn(
			UUID siteId, LocalDate du, LocalDate au, Collection<String> collaborateurIds);

	/** Heures sup à venir d'un bénéficiaire, par son compte (JWT) ; le statut de la demande est filtré par l'appelant. */
	List<PresenceHeuresSupJour> findByCompteIdAndJourneeGreaterThanEqualOrderByJournee(String compteId, LocalDate depuis);

	/** Lignes encore susceptibles d'être touchées par un changement d'horaire (fenêtre pas encore ouverte). */
	List<PresenceHeuresSupJour> findBySiteIdAndJourneeGreaterThanEqual(UUID siteId, LocalDate depuis);

	/**
	 * Lignes à compter (D-P14) : journées de la période appartenant à une demande VALIDEE. Filtres optionnels construits
	 * dynamiquement (pas de paramètre NULL en JPQL sous PostgreSQL) :
	 * site, collaborateur (fiche), direction, identités du pointeur (fiche ou compte), demandeur (compte) et demande.
	 */
	static Specification<PresenceHeuresSupJour> comptage(
			LocalDate du, LocalDate au, UUID siteId, String collaborateurId, UUID directionId,
			Collection<String> pointeurs, String demandeurCompteId, UUID demandeId) {
		return (root, query, cb) -> {
			List<Predicate> predicats = new ArrayList<>();
			predicats.add(cb.between(root.get("journee"), du, au));
			if (siteId != null) predicats.add(cb.equal(root.get("siteId"), siteId));
			if (collaborateurId != null) predicats.add(cb.equal(root.get("collaborateurId"), collaborateurId));
			if (directionId != null) predicats.add(cb.equal(root.get("directionId"), directionId));
			if (pointeurs != null) {
				predicats.add(cb.or(root.get("collaborateurId").in(pointeurs), root.get("compteId").in(pointeurs)));
			}
			Subquery<UUID> validee = query.subquery(UUID.class);
			Root<PresenceHeuresSupDemande> d = validee.from(PresenceHeuresSupDemande.class);
			List<Predicate> conditions = new ArrayList<>();
			conditions.add(cb.equal(d.get("id"), root.get("demandeId")));
			conditions.add(cb.equal(d.get("statut"), StatutHeuresSup.VALIDEE));
			if (demandeurCompteId != null) conditions.add(cb.equal(d.get("demandeurCompteId"), demandeurCompteId));
			if (demandeId != null) conditions.add(cb.equal(d.get("id"), demandeId));
			validee.select(d.get("id")).where(conditions.toArray(Predicate[]::new));
			predicats.add(cb.exists(validee));
			return cb.and(predicats.toArray(Predicate[]::new));
		};
	}
}
