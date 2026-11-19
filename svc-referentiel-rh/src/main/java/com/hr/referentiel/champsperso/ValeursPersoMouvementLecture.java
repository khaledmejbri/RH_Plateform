package com.hr.referentiel.champsperso;

import com.hr.referentiel.champsperso.DefinitionsPerso.Def;
import com.hr.referentiel.champsperso.ValeursPersoDtos.ValeurAffichee;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Valeurs des champs « soldes » des mouvements d'ajustement (ADR partie 2, P2-9) : une requête par page de mouvements.
 * Vue {@code BACKOFFICE} : champs actifs (ordre réglé) et champs désactivés renseignés. Vue {@code COLLABORATEUR} :
 * champs actifs ET visibles par le collaborateur seulement, jamais de champs désactivés (liste {@code null}, donc clé
 * absente du JSON) ; le filtrage est fait ici, avant la construction du DTO.
 */
@Component
public class ValeursPersoMouvementLecture {

	public static final String TYPE = "SOLDES";

	public enum Vue {
		BACKOFFICE, COLLABORATEUR
	}

	/** Valeurs d'un mouvement ; {@code desactives} est {@code null} en vue collaborateur. */
	public record Extras(List<ValeurAffichee> champs, List<ValeurAffichee> desactives) {
	}

	private final DefinitionsPerso definitions;
	private final ValeursPersoJdbc jdbc;

	public ValeursPersoMouvementLecture(DefinitionsPerso definitions, ValeursPersoJdbc jdbc) {
		this.definitions = definitions;
		this.jdbc = jdbc;
	}

	public Map<UUID, Extras> pour(Collection<UUID> mouvementIds, Vue vue) {
		Map<UUID, Extras> r = new HashMap<>();
		if (mouvementIds.isEmpty()) {
			return r;
		}
		Map<UUID, Map<String, ValeurTypee>> valeurs = jdbc.mouvements(mouvementIds);
		Map<String, Def> defs = valeurs.isEmpty() ? Map.of() : definitions.lire(TYPE);
		for (UUID id : mouvementIds) {
			List<ValeurAffichee> champs = new ArrayList<>();
			List<ValeurAffichee> desactives = new ArrayList<>();
			Map<String, ValeurTypee> v = valeurs.getOrDefault(id, Map.of());
			for (Def d : defs.values()) {
				ValeurTypee val = v.get(d.champ().getCode());
				if (val == null) {
					continue;
				}
				ValeurAffichee a = new ValeurAffichee(d.champ().getCode(), d.champ().getLibelle(), d.champ().getTypeValeur().name(),
						FormatValeurPerso.canonique(val), FormatValeurPerso.affichage(val, d.definition()));
				if (d.champ().isActif() && (vue == Vue.BACKOFFICE || d.champ().isVisibleCollaborateur())) {
					champs.add(a);
				} else if (!d.champ().isActif() && vue == Vue.BACKOFFICE) {
					desactives.add(a);
				}
			}
			r.put(id, new Extras(champs, vue == Vue.BACKOFFICE ? desactives : null));
		}
		return r;
	}

	/** Champs « soldes » actifs, dans l'ordre réglé (colonnes de l'export des mouvements). */
	public List<Def> champsActifs() {
		return definitions.lire(TYPE).values().stream().filter(d -> d.champ().isActif()).toList();
	}

	/** Valeurs brutes (export). */
	public Map<UUID, Map<String, ValeurTypee>> valeurs(Collection<UUID> mouvementIds) {
		return mouvementIds.isEmpty() ? Map.of() : jdbc.mouvements(mouvementIds);
	}
}
