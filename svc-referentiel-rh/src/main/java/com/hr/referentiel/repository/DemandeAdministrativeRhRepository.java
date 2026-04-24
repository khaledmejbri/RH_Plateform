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

	List<DemandeAdministrativeRh> findByDemandeurIdOrderByCreeLeDesc(UUID demandeurId);

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
}
