package com.hr.identiteacces.repository;

import com.hr.identiteacces.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByUsername(String username);

	Optional<User> findByEmail(String email);

	boolean existsByUsername(String username);

	/** Identifiants (en minuscules) déjà portés par un compte, parmi une liste en minuscules. */
	@Query("select lower(u.username) from User u where lower(u.username) in :noms")
	List<String> findUsernamesParMinuscules(@Param("noms") Collection<String> noms);

	@Query("select lower(u.username) from User u where u.societeId = :societeId and lower(u.username) in :noms")
	List<String> findUsernamesParMinusculesEtSociete(
			@Param("societeId") UUID societeId,
			@Param("noms") Collection<String> noms);

	boolean existsByEmail(String email);

	@Query("select (count(u) > 0) from User u where lower(u.email) = lower(:email)")
	boolean existsByEmailIgnoreCase(@Param("email") String email);

	List<User> findByRolesContaining(String role);

	@Query("select distinct u from User u join u.roles r where u.societeId = :societeId and r = :role")
	List<User> findBySocieteIdAndRolesContaining(@Param("societeId") UUID societeId, @Param("role") String role);

	@Query("select u from User u where u.societeId = :societeId and lower(u.username) = lower(:username)")
	Optional<User> findBySocieteIdAndUsernameIgnoreCase(
			@Param("societeId") UUID societeId,
			@Param("username") String username);

	@Query("select u from User u where u.societeId = :societeId and (lower(u.username) = lower(:identifiant) or lower(u.email) = lower(:identifiant))")
	Optional<User> findBySocieteIdAndIdentifiantIgnoreCase(
			@Param("societeId") UUID societeId,
			@Param("identifiant") String identifiant);

	@Query("select u from User u where u.societeId is null and (lower(u.username) = lower(:identifiant) or lower(u.email) = lower(:identifiant))")
	Optional<User> findPlateformeByIdentifiantIgnoreCase(@Param("identifiant") String identifiant);

	@Query("select (count(u) > 0) from User u join u.roles r where u.societeId = :societeId and r = :role")
	boolean existsBySocieteIdAndRole(@Param("societeId") UUID societeId, @Param("role") String role);

	@Query("select (count(u) > 0) from User u join u.roles r where r = :role")
	boolean existsByRole(@Param("role") String role);

	@Modifying
	@Query(value = "UPDATE users u SET societe_id = :societeId "
			+ "WHERE u.societe_id IS NULL "
			+ "AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role = 'PLATEFORME')",
			nativeQuery = true)
	int rattacherComptesSansSociete(@Param("societeId") UUID societeId);
}
