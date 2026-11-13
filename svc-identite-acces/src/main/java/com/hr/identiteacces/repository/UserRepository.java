package com.hr.identiteacces.repository;

import com.hr.identiteacces.entity.User;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    /** Identifiants (en minuscules) déjà portés par un compte, parmi une liste en minuscules. */
    @org.springframework.data.jpa.repository.Query("select lower(u.username) from User u where lower(u.username) in :noms")
    java.util.List<String> findUsernamesParMinuscules(@org.springframework.data.repository.query.Param("noms") java.util.Collection<String> noms);

    boolean existsByEmail(String email);

    java.util.List<User> findByRolesContaining(String role);
}
