package com.hr.referentiel.champsperso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChampPersoValeurListeRepository extends JpaRepository<ChampPersoValeurListe, UUID> {

	List<ChampPersoValeurListe> findByChampPersoIdInOrderByOrdreAsc(Collection<UUID> champIds);

	List<ChampPersoValeurListe> findByChampPersoIdOrderByOrdreAsc(UUID champId);
}
