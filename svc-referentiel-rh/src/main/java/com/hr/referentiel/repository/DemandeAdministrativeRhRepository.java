package com.hr.referentiel.repository;

import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DemandeAdministrativeRhRepository extends JpaRepository<DemandeAdministrativeRh, UUID> {

	/** D-L02 : recherche RH par numéro lisible (le numéro est stocké en majuscules ; correspondance exacte). */
	@Query("select d from DemandeAdministrativeRh d join fetch d.demandeur where d.numero = :numero")
	List<DemandeAdministrativeRh> findPourRhByNumero(@Param("numero") String numero);

	List<DemandeAdministrativeRh> findByDemandeurIdOrderByCreeLeDesc(UUID demandeurId);

	/** @deprecated Préférer {@link #findByValideurAttenduIdAndStatutOrderByCreeLeDesc} (manager nœud). */
	List<DemandeAdministrativeRh> findByDemandeurSuperieurIdAndStatutOrderByCreeLeDesc(
			UUID superieurId, StatutDemandeAdministrativeRh statut);

	List<DemandeAdministrativeRh> findByValideurAttenduIdAndStatutOrderByCreeLeDesc(
			UUID valideurAttenduId, StatutDemandeAdministrativeRh statut);

	List<DemandeAdministrativeRh> findByDemandeurIdAndTypeDemandeOrderByCreeLeDesc(UUID demandeurId,
			TypeDemandeAdministrativeRh typeDemande);

	@Query("select d from DemandeAdministrativeRh d join fetch d.demandeur order by d.creeLe desc")
	List<DemandeAdministrativeRh> findAllPourRhOrderByCreeLeDesc();

	@Query("select d from DemandeAdministrativeRh d join fetch d.demandeur where d.typeDemande = :type order by d.creeLe desc")
	List<DemandeAdministrativeRh> findAllPourRhByTypeDemandeOrderByCreeLeDesc(
			@Param("type") TypeDemandeAdministrativeRh type);

	@Query("select d from DemandeAdministrativeRh d join fetch d.demandeur where d.demandeur.id = :demandeurId "
			+ "and d.periodeDebut is not null and d.periodeFin is not null "
			+ "and d.periodeDebut <= :jour and d.periodeFin >= :jour "
			+ "and (:type is null or d.typeDemande = :type) "
			+ "and (:statut is null or d.statut = :statut) order by d.periodeDebut asc")
	List<DemandeAdministrativeRh> findByDemandeurCouvrantJour(@Param("demandeurId") UUID demandeurId,
			@Param("jour") LocalDate jour, @Param("type") TypeDemandeAdministrativeRh type,
			@Param("statut") StatutDemandeAdministrativeRh statut);

	@Query("select d from DemandeAdministrativeRh d join fetch d.demandeur where d.periodeDebut is not null "
			+ "and d.periodeFin is not null and d.periodeDebut <= :jour and d.periodeFin >= :jour "
			+ "and (:type is null or d.typeDemande = :type) "
			+ "and (:statut is null or d.statut = :statut) order by d.periodeDebut asc, d.creeLe desc")
	List<DemandeAdministrativeRh> findPourRhCouvrantJour(@Param("jour") LocalDate jour,
			@Param("type") TypeDemandeAdministrativeRh type, @Param("statut") StatutDemandeAdministrativeRh statut);

	/** Verrou d'écriture sur la demande (premier verrou de l'ordre demande puis solde, ADR § 4). */
	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from DemandeAdministrativeRh d where d.id = :id")
	java.util.Optional<DemandeAdministrativeRh> findByIdForUpdate(@Param("id") UUID id);

	/** Réservé : jours des demandes ANNUEL décomptées en cours de validation (D-S04). */
	@Query("select coalesce(sum(d.joursDecomptes), 0) from DemandeAdministrativeRh d "
			+ "where d.demandeur.id = :id and d.statut in :statuts and d.joursDecomptes is not null")
	java.math.BigDecimal sommeReserve(@Param("id") UUID collaborateurId,
			@Param("statuts") java.util.Collection<StatutDemandeAdministrativeRh> statuts);

	@Query("select d.demandeur.id, sum(d.joursDecomptes) from DemandeAdministrativeRh d "
			+ "where d.demandeur.id in :ids and d.statut in :statuts and d.joursDecomptes is not null "
			+ "group by d.demandeur.id")
	List<Object[]> sommesReserve(@Param("ids") java.util.Collection<UUID> collaborateurIds,
			@Param("statuts") java.util.Collection<StatutDemandeAdministrativeRh> statuts);

	/** Congés (tout type) du collaborateur dans les statuts donnés, pour le contrôle de chevauchement. */
	@Query("select d from DemandeAdministrativeRh d where d.demandeur.id = :id and d.typeDemande = :type "
			+ "and d.statut in :statuts")
	List<DemandeAdministrativeRh> findPourChevauchement(@Param("id") UUID collaborateurId,
			@Param("type") TypeDemandeAdministrativeRh type,
			@Param("statuts") java.util.Collection<StatutDemandeAdministrativeRh> statuts);
}
