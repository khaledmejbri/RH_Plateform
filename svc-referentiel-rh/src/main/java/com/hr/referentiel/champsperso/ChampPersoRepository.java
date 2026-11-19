package com.hr.referentiel.champsperso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChampPersoRepository extends JpaRepository<ChampPerso, UUID> {

	List<ChampPerso> findByTypeImport(String typeImport);

	List<ChampPerso> findByTypeImportAndActifTrue(String typeImport);

	List<ChampPerso> findByTypeImportAndCodeIn(String typeImport, Collection<String> codes);
}
