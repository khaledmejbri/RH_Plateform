package com.hr.workflow.catalogue;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Catalogue fermé des types d'objet métier gérés par le moteur en W3 (§ 5.1, § 9.5.1, § 9.5.2, D-W3-05).
 */
@Component
public class CatalogueTypesObjet {

	public enum TypeVariable {
		ENTIER,
		DECIMAL,
		DATE,
		DATE_HEURE,
		TEXTE,
		LISTE,
		BOOLEEN
	}

	public record VariableCatalogue(
			@JsonProperty("nom") String nom,
			@JsonProperty("type") TypeVariable type,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("valeurs") List<String> valeurs,
			@JsonProperty("source_valeurs") String sourceValeurs
	) {
		public static VariableCatalogue simple(String nom, TypeVariable type, String libelle) {
			return new VariableCatalogue(nom, type, libelle, null, null);
		}

		public static VariableCatalogue liste(String nom, String libelle, String sourceValeurs) {
			return new VariableCatalogue(nom, TypeVariable.LISTE, libelle, null, sourceValeurs);
		}
	}

	public record TypeObjetCatalogue(
			@JsonProperty("objet_type") String objetType,
			@JsonProperty("libelle") String libelle,
			@JsonProperty("service_proprietaire") String serviceProprietaire,
			@JsonProperty("chemin_effet") String cheminEffet,
			@JsonProperty("variables") List<VariableCatalogue> variables,
			@JsonProperty("effets") Set<String> effets,
			@JsonProperty("validation_humaine_obligatoire") boolean validationHumaineObligatoire,
			@JsonProperty("supporte_complement") boolean supporteComplement
	) {
		public boolean supporteEffet(String effet) {
			return effets.contains(effet);
		}
	}

	private static final String EFFET_REFERENTIEL = "/internal/referentiel/v1/workflow/effets";
	private static final String EFFET_PRESENCE = "/internal/presence/v1/workflow/effets";

	public static final List<VariableCatalogue> VARIABLES_COMMUNES = List.of(
			VariableCatalogue.liste("demandeur_profil_acces", "Profil d'accès du demandeur", null),
			VariableCatalogue.liste("demandeur_type_noeud", "Type d'unité du demandeur", null),
			VariableCatalogue.simple("demandeur_anciennete_mois", TypeVariable.ENTIER, "Ancienneté du demandeur (mois)"),
			VariableCatalogue.simple("demandeur_est_manager", TypeVariable.BOOLEEN, "Le demandeur est manager")
	);

	private final Map<String, TypeObjetCatalogue> types;

	public CatalogueTypesObjet() {
		Map<String, TypeObjetCatalogue> map = new LinkedHashMap<>();

		enregistrer(map, new TypeObjetCatalogue(
				"CONGE", "Congé", "svc-referentiel-rh", EFFET_REFERENTIEL,
				List.of(
						VariableCatalogue.liste("type_conge", "Type de congé",
								"/internal/referentiel/v1/workflow/catalogue/CONGE/valeurs/type_conge"),
						VariableCatalogue.simple("jours_decomptes", TypeVariable.DECIMAL, "Jours décomptés"),
						VariableCatalogue.simple("date_debut", TypeVariable.DATE, "Date de début"),
						VariableCatalogue.simple("date_fin", TypeVariable.DATE, "Date de fin"),
						VariableCatalogue.simple("delai_prevenance_jours", TypeVariable.ENTIER, "Délai de prévenance (jours)"),
						VariableCatalogue.simple("disponible_apres", TypeVariable.DECIMAL, "Solde disponible après demande"),
						VariableCatalogue.simple("piece_jointe_fournie", TypeVariable.BOOLEEN, "Pièce jointe fournie")
				),
				Set.of("CONTROLER", "APPROUVER", "REFUSER", "EXPIRER", "ANNULER"),
				true, true
		));

		enregistrer(map, new TypeObjetCatalogue(
				"AUTORISATION_SORTIE", "Autorisation de sortie", "svc-referentiel-rh", EFFET_REFERENTIEL,
				List.of(
						VariableCatalogue.simple("duree_minutes", TypeVariable.ENTIER, "Durée (minutes)"),
						VariableCatalogue.simple("date_jour", TypeVariable.DATE, "Date"),
						VariableCatalogue.simple("delai_prevenance_heures", TypeVariable.ENTIER, "Délai de prévenance (heures)")
				),
				Set.of("APPROUVER", "REFUSER", "EXPIRER", "ANNULER"),
				false, true
		));

		enregistrer(map, new TypeObjetCatalogue(
				"ORDRE_MISSION", "Ordre de mission", "svc-referentiel-rh", EFFET_REFERENTIEL,
				List.of(
						VariableCatalogue.simple("lieu", TypeVariable.TEXTE, "Lieu de la mission"),
						VariableCatalogue.simple("date_debut", TypeVariable.DATE, "Date de début"),
						VariableCatalogue.simple("date_fin", TypeVariable.DATE, "Date de fin"),
						VariableCatalogue.simple("duree_jours", TypeVariable.ENTIER, "Durée (jours)"),
						VariableCatalogue.simple("delai_prevenance_jours", TypeVariable.ENTIER, "Délai de prévenance (jours)")
				),
				Set.of("APPROUVER", "REFUSER", "EXPIRER", "ANNULER"),
				false, true
		));

		enregistrer(map, new TypeObjetCatalogue(
				"DOCUMENT", "Document administratif", "svc-referentiel-rh", EFFET_REFERENTIEL,
				List.of(
						VariableCatalogue.liste("type_document", "Type de document",
								"/internal/referentiel/v1/workflow/catalogue/DOCUMENT/valeurs/type_document")
				),
				Set.of("APPROUVER", "REFUSER", "ANNULER"),
				false, true
		));

		enregistrer(map, new TypeObjetCatalogue(
				"FORMATION", "Formation", "svc-referentiel-rh", EFFET_REFERENTIEL,
				List.of(
						VariableCatalogue.liste("origine", "Origine de la demande",
								"/internal/referentiel/v1/workflow/catalogue/FORMATION/valeurs/origine"),
						VariableCatalogue.simple("cout_estime", TypeVariable.DECIMAL, "Coût estimé"),
						VariableCatalogue.simple("duree_jours", TypeVariable.ENTIER, "Durée (jours)")
				),
				Set.of("APPROUVER", "REFUSER", "ANNULER"),
				false, true
		));

		enregistrer(map, new TypeObjetCatalogue(
				"POINTAGE_MANUEL", "Pointage manuel", "svc-presence", EFFET_PRESENCE,
				List.of(
						VariableCatalogue.simple("signale", TypeVariable.BOOLEEN, "Demande signalée"),
						VariableCatalogue.simple("nb_signaux", TypeVariable.ENTIER, "Nombre de signaux"),
						VariableCatalogue.liste("type_pointage", "Type de pointage",
								"/internal/presence/v1/workflow/catalogue/POINTAGE_MANUEL/valeurs/type_pointage"),
						VariableCatalogue.simple("distance_metres", TypeVariable.ENTIER, "Distance mesurée (mètres)")
				),
				Set.of("APPROUVER", "REFUSER", "EXPIRER"),
				false, false
		));

		enregistrer(map, new TypeObjetCatalogue(
				"HEURES_SUP", "Heures supplémentaires", "svc-presence", EFFET_PRESENCE,
				List.of(
						VariableCatalogue.simple("nb_beneficiaires", TypeVariable.ENTIER, "Nombre de bénéficiaires"),
						VariableCatalogue.simple("duree_minutes", TypeVariable.ENTIER, "Durée (minutes)"),
						VariableCatalogue.simple("cree_par_rh", TypeVariable.BOOLEEN, "Créée par la RH"),
						VariableCatalogue.simple("debut_fenetre", TypeVariable.DATE_HEURE, "Début de la fenêtre")
				),
				Set.of("APPROUVER", "REFUSER", "EXPIRER", "ANNULER"),
				false, false
		));

		this.types = Collections.unmodifiableMap(map);
	}

	private static void enregistrer(Map<String, TypeObjetCatalogue> map, TypeObjetCatalogue type) {
		map.put(type.objetType(), type);
	}

	public Optional<TypeObjetCatalogue> findByObjetType(String objetType) {
		if (objetType == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(types.get(objetType));
	}

	public boolean existe(String objetType) {
		return objetType != null && types.containsKey(objetType);
	}

	public List<TypeObjetCatalogue> listerTypes() {
		return List.copyOf(types.values());
	}

	/** Toutes les variables disponibles pour un type (variables propres + variables communes). */
	public Map<String, VariableCatalogue> variablesPour(String objetType) {
		Map<String, VariableCatalogue> map = new LinkedHashMap<>();
		findByObjetType(objetType).ifPresent(t -> {
			for (VariableCatalogue v : t.variables()) {
				map.put(v.nom(), v);
			}
		});
		for (VariableCatalogue v : VARIABLES_COMMUNES) {
			map.put(v.nom(), v);
		}
		return map;
	}

	/** Libellé d'affichage d'une règle d'intervenant (§ 9.5.2). */
	public static String libelleRegle(String regle, Map<String, Object> params) {
		if (regle == null) {
			return "";
		}
		Map<String, Object> p = params != null ? params : Map.of();
		return switch (regle) {
			case "MANAGER_NOEUD" -> "Responsable direct";
			case "MANAGER_NIVEAU" -> "Responsable N+" + p.getOrDefault("niveau", 1);
			case "RESPONSABLE_TYPE" -> {
				String typeNoeud = String.valueOf(p.getOrDefault("type_noeud", ""));
				yield switch (typeNoeud) {
					case "DEPARTEMENT" -> "Chef de département";
					case "DIRECTION" -> "Directeur";
					case "DIRECTION_GENERALE" -> "Directeur général";
					default -> "Responsable hiérarchique";
				};
			}
			case "ROLE" -> {
				Object rolesObj = p.get("roles");
				if (rolesObj instanceof List<?> list) {
					boolean hasRh = list.stream().anyMatch(r -> "RH".equals(String.valueOf(r)));
					boolean hasAdmin = list.stream().anyMatch(r -> "ADMIN".equals(String.valueOf(r)));
					if (hasRh && hasAdmin) {
						yield "Ressources humaines";
					}
					if (hasRh) {
						yield "Ressources humaines";
					}
					if (hasAdmin) {
						yield "Administrateur";
					}
				}
				yield "Ressources humaines";
			}
			case "FONCTION" -> String.valueOf(p.getOrDefault("fonction_code", "Fonction"));
			case "COLLABORATEUR" -> "Personne désignée";
			case "DEMANDEUR" -> "Demandeur";
			default -> regle;
		};
	}

	/** Payload complet de {@code GET /api/rh/v1/admin/workflow/catalogue} (§ 9.7.2, D-W3-05). */
	public Map<String, Object> construireReponseCatalogue() {
		Map<String, Object> res = new LinkedHashMap<>();
		res.put("types", listerTypes());
		res.put("variables_communes", VARIABLES_COMMUNES);
		res.put("effets", List.of("CONTROLER", "APPROUVER", "REFUSER", "EXPIRER", "ANNULER"));
		res.put("regles", List.of(
				Map.of("code", "MANAGER_NOEUD", "libelle", "Responsable direct"),
				Map.of("code", "MANAGER_NIVEAU", "libelle", "Responsable N+k"),
				Map.of("code", "RESPONSABLE_TYPE", "libelle", "Responsable d'un type d'unité"),
				Map.of("code", "ROLE", "libelle", "Rôle applicatif (RH / Administrateur)"),
				Map.of("code", "FONCTION", "libelle", "Fonction métier"),
				Map.of("code", "COLLABORATEUR", "libelle", "Collaborateur nommé"),
				Map.of("code", "DEMANDEUR", "libelle", "Demandeur")
		));
		res.put("operateurs", Map.of(
				"ENTIER", List.of("egal", "different", "inf", "inf_egal", "sup", "sup_egal", "vide", "non_vide"),
				"DECIMAL", List.of("egal", "different", "inf", "inf_egal", "sup", "sup_egal", "vide", "non_vide"),
				"DATE", List.of("egal", "different", "inf", "inf_egal", "sup", "sup_egal", "vide", "non_vide"),
				"DATE_HEURE", List.of("egal", "different", "inf", "inf_egal", "sup", "sup_egal", "vide", "non_vide"),
				"TEXTE", List.of("egal", "different", "contient", "ne_contient_pas", "vide", "non_vide"),
				"LISTE", List.of("egal", "different", "dans", "hors", "vide", "non_vide"),
				"BOOLEEN", List.of("egal")
		));
		res.put("publics", List.of("DEMANDEUR", "CANDIDATS", "ROLE:RH", "ROLE:ADMIN", "FONCTION:<code>", "MANAGER_NOEUD"));
		// D-W3-05 : pas de liste interne des fonctions en W3
		res.put("fonctions", List.of());
		res.put("fonctions_disponibles", false);
		return res;
	}
}
