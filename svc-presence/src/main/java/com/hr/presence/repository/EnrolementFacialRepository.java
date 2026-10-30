package com.hr.presence.repository;

import com.hr.presence.domain.StatutEnrolement;
import com.hr.presence.entity.PresenceEnrolementFacial;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public interface EnrolementFacialRepository extends JpaRepository<PresenceEnrolementFacial, UUID>,
		JpaSpecificationExecutor<PresenceEnrolementFacial> {

	char ECHAPPEMENT_LIKE = '!';

	Optional<PresenceEnrolementFacial> findByCompteId(String compteId);

	/** Verrou pessimiste : sérialise les décisions et le compteur d'échecs d'un même compte (D-P10). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from PresenceEnrolementFacial e where e.compteId = :compteId")
	Optional<PresenceEnrolementFacial> findByCompteIdForUpdate(@Param("compteId") String compteId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from PresenceEnrolementFacial e where e.id = :id")
	Optional<PresenceEnrolementFacial> findByIdForUpdate(@Param("id") UUID id);

	/** Filtres optionnels construits dynamiquement (même raison que {@code PresencePointageRepository#filtreAdmin}). */
	static Specification<PresenceEnrolementFacial> filtreAdmin(
			StatutEnrolement statut, String q, Boolean appareilIncompatible) {
		return (root, query, cb) -> {
			List<Predicate> predicats = new ArrayList<>();
			if (statut != null) {
				predicats.add(cb.equal(root.get("statut"), statut));
			}
			if (q != null && !q.isBlank()) {
				String motif = "%" + echapperLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
				predicats.add(cb.like(cb.lower(root.get("collaborateurNom")), motif, ECHAPPEMENT_LIKE));
			}
			if (appareilIncompatible != null) {
				predicats.add(appareilIncompatible
						? cb.isNotNull(root.get("deviceIdIncompatible"))
						: cb.isNull(root.get("deviceIdIncompatible")));
			}
			return cb.and(predicats.toArray(Predicate[]::new));
		};
	}

	/** Neutralise les jokers LIKE saisis par l'utilisateur. */
	private static String echapperLike(String texte) {
		String e = ECHAPPEMENT_LIKE + "";
		return texte.replace(e, e + e).replace("%", e + "%").replace("_", e + "_");
	}
}
