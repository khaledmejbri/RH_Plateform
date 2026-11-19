package com.hr.referentiel.importation;

import com.hr.referentiel.importation.parametrage.ExigenceChamp;
import com.hr.referentiel.importation.parametrage.ParametrageImport;
import com.hr.referentiel.importation.parametrage.ParametrageImport.ChampRegle;
import com.hr.referentiel.importation.parametrage.ParametrageImportCache;
import com.hr.referentiel.importation.parametrage.TypeImportParametrable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Cache de paramétrage sans base pour les tests : valeurs du code, ou paramétrage défini par le test. */
public class CacheParametrageDeTest extends ParametrageImportCache {

	private final Map<TypeImportParametrable, ParametrageImport> valeurs = new EnumMap<>(TypeImportParametrable.class);

	public CacheParametrageDeTest() {
		super(null, null, null, null, null, null);
	}

	public void definir(ParametrageImport parametrage) {
		valeurs.put(TypeImportParametrable.parTypeImport(parametrage.typeImport()).orElseThrow(), parametrage);
	}

	@Override
	public ParametrageImport courant(TypeImportParametrable type) {
		return valeurs.getOrDefault(type, ParametrageImport.parDefaut(type));
	}

	/** Copie du paramétrage dont un champ est réglé ; un argument {@code null} laisse la valeur en place. */
	public static ParametrageImport regler(ParametrageImport p, String code, String enTete, Boolean dansModele, ExigenceChamp exigence,
			List<String> formesEnPlus) {
		List<ChampRegle> champs = new ArrayList<>();
		for (ChampRegle c : p.champs()) {
			if (!c.code().equals(code)) {
				champs.add(c);
				continue;
			}
			List<String> formes = new ArrayList<>(c.formesActives());
			if (formesEnPlus != null) {
				formes.addAll(formesEnPlus);
			}
			champs.add(new ChampRegle(c.code(), enTete != null ? enTete : c.enTete(), dansModele != null ? dansModele : c.dansModele(),
					exigence != null ? exigence : c.exigence(), c.ordre(), formes));
		}
		return new ParametrageImport(p.typeImport(), p.version(), champs);
	}

	public static ParametrageImport version(ParametrageImport p, long version) {
		return new ParametrageImport(p.typeImport(), version, p.champs());
	}

	/** Copie sans une forme active d'un champ (synonyme désactivé ou supprimé). */
	public static ParametrageImport sansForme(ParametrageImport p, String code, String forme) {
		List<ChampRegle> champs = new ArrayList<>();
		for (ChampRegle c : p.champs()) {
			List<String> formes = new ArrayList<>(c.formesActives());
			if (c.code().equals(code)) {
				formes.remove(forme);
			}
			champs.add(new ChampRegle(c.code(), c.enTete(), c.dansModele(), c.exigence(), c.ordre(), formes));
		}
		return new ParametrageImport(p.typeImport(), p.version(), champs);
	}

	/** Copie dont le champ {@code code} est placé en position {@code position} (ordre réglé). */
	public static ParametrageImport deplacer(ParametrageImport p, String code, int position) {
		List<ChampRegle> tri = new ArrayList<>(p.champs());
		tri.sort(java.util.Comparator.comparingInt(ChampRegle::ordre));
		ChampRegle cible = tri.stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
		tri.remove(cible);
		tri.add(position, cible);
		List<ChampRegle> champs = new ArrayList<>();
		for (int i = 0; i < tri.size(); i++) {
			ChampRegle c = tri.get(i);
			champs.add(new ChampRegle(c.code(), c.enTete(), c.dansModele(), c.exigence(), i, c.formesActives()));
		}
		return new ParametrageImport(p.typeImport(), p.version(), champs);
	}
}
