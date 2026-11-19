package com.hr.referentiel.champsperso;

import com.hr.referentiel.importation.parametrage.ImportChampRegle;
import com.hr.referentiel.importation.parametrage.ImportChampRegleRepository;
import com.hr.referentiel.importation.parametrage.ImportParametrageRepository;
import com.hr.referentiel.importation.parametrage.ParametrageImport.DefinitionPerso;
import com.hr.referentiel.importation.parametrage.ParametrageImport.ValeurListe;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lecture des définitions DIRECTEMENT en base (jamais le cache de 30 s), sous le verrou {@code FOR SHARE} de la ligne du
 * type quand l'appelant écrit des valeurs : un changement de type ou une suppression concurrents attendent la fin de
 * l'écriture (ADR partie 2, P2-6).
 */
@Component
public class DefinitionsPerso {

	/** Définition complète : entité (visibilité, actif, finalité…) et vue de contrôle. */
	public record Def(ChampPerso champ, DefinitionPerso definition) {
	}

	private final ChampPersoRepository persoRepository;
	private final ChampPersoValeurListeRepository valeurRepository;
	private final ImportChampRegleRepository regleRepository;
	private final ImportParametrageRepository parametrageRepository;

	public DefinitionsPerso(ChampPersoRepository persoRepository, ChampPersoValeurListeRepository valeurRepository,
			ImportChampRegleRepository regleRepository, ImportParametrageRepository parametrageRepository) {
		this.persoRepository = persoRepository;
		this.valeurRepository = valeurRepository;
		this.regleRepository = regleRepository;
		this.parametrageRepository = parametrageRepository;
	}

	/** Prend le verrou partagé sur la ligne du type puis lit les définitions (actives et inactives), dans l'ordre réglé. */
	public Map<String, Def> lireSousVerrou(String typeImport) {
		parametrageRepository.findByIdForShare(typeImport);
		return lire(typeImport);
	}

	public Map<String, Def> lire(String typeImport) {
		List<ChampPerso> persos = persoRepository.findByTypeImport(typeImport);
		Map<UUID, List<ChampPersoValeurListe>> valeurs = persos.isEmpty() ? Map.of()
				: valeurRepository.findByChampPersoIdInOrderByOrdreAsc(persos.stream().map(ChampPerso::getId).toList()).stream()
						.collect(Collectors.groupingBy(ChampPersoValeurListe::getChampPersoId));
		Map<String, Integer> ordres = regleRepository.findByTypeImportOrderByOrdreAsc(typeImport).stream()
				.collect(Collectors.toMap(ImportChampRegle::getChampCode, ImportChampRegle::getOrdre, (a, b) -> a));
		List<ChampPerso> tries = new ArrayList<>(persos);
		tries.sort((a, b) -> {
			if (a.isActif() != b.isActif()) {
				return a.isActif() ? -1 : 1;
			}
			int c = Integer.compare(ordres.getOrDefault(a.getCode(), Integer.MAX_VALUE), ordres.getOrDefault(b.getCode(), Integer.MAX_VALUE));
			return c != 0 ? c : a.getCreeLe().compareTo(b.getCreeLe());
		});
		Map<String, Def> r = new LinkedHashMap<>();
		for (ChampPerso p : tries) {
			List<ValeurListe> liste = valeurs.getOrDefault(p.getId(), List.of()).stream()
					.map(v -> new ValeurListe(v.getCode(), v.getLibelle(), v.isActif())).toList();
			r.put(p.getCode(), new Def(p, new DefinitionPerso(p.getCode(), p.getLibelle(), p.getTypeValeur(), p.regles(), liste)));
		}
		return r;
	}
}
