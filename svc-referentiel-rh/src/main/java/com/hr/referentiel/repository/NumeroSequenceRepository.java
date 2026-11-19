package com.hr.referentiel.repository;

import com.hr.referentiel.entity.NumeroSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NumeroSequenceRepository extends JpaRepository<NumeroSequence, String> {

	/**
	 * Incrémente atomiquement le compteur de la clé pour une société (créé à 1 s'il n'existe pas) et renvoie la nouvelle valeur.
	 * L'upsert pose un verrou de ligne tenu jusqu'à la fin de la transaction appelante : deux créations
	 * simultanées du même mois sont sérialisées, et une transaction annulée ne consomme aucun numéro.
	 */
	@Query(value = "INSERT INTO rh_numero_sequence (societe_id, cle, dernier_numero) VALUES (:societeId, :cle, 1) "
			+ "ON CONFLICT (societe_id, cle) DO UPDATE SET dernier_numero = rh_numero_sequence.dernier_numero + 1 "
			+ "RETURNING dernier_numero", nativeQuery = true)
	int incrementer(@Param("societeId") UUID societeId, @Param("cle") String cle);
}
