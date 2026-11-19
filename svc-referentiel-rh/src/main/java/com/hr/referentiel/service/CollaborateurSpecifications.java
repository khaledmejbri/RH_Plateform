package com.hr.referentiel.service;

import com.hr.referentiel.champsperso.DefinitionsPerso.Def;
import com.hr.referentiel.champsperso.TypeValeurPerso;
import com.hr.referentiel.champsperso.ValeurPersoFiche;
import com.hr.referentiel.entity.Collaborateur;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.hr.referentiel.service.FiltreCollaborateurs.invalide;

/**
 * Spécification JPA (Criteria, portable H2 et PostgreSQL) du filtre commun de la liste des collaborateurs. La recherche
 * {@code q} neutralise les accents du français avec {@code translate} ; chaque critère personnalisé est un {@code EXISTS}
 * corrélé sur {@code rh_valeur_perso_fiche} ({@code NOT EXISTS} pour « Vide »). Tous les critères sont validés AVANT la
 * requête : champ inconnu ou inactif, critère incompatible avec le type, valeur illisible, {@code etat} combiné.
 */
final class CollaborateurSpecifications {

	private static final String ACCENTS = "àâäáãåçéèêëíìîïñóòôöõúùûüýÿ";
	private static final String SANS_ACCENT = "aaaaaaceeeeiiiinooooouuuuyy";

	private CollaborateurSpecifications() {
	}

	/**
	 * @param unites ensemble d'unités retenu (intersection des sous-arbres direction / département / service) ;
	 *        {@code null} = aucune contrainte
	 * @param defs définitions des champs personnalisés « collaborateurs » (actifs et inactifs)
	 */
	@SuppressWarnings("unchecked")
	static Specification<Collaborateur> construire(FiltreCollaborateurs f, Set<UUID> unites, Map<String, Def> defs) {
		List<String> mots = f.mots();
		List<CritereValide> criteres = valider(f, defs);
		return (root, query, cb) -> {
			boolean comptage = query != null && (query.getResultType() == Long.class || query.getResultType() == long.class);
			Join<Object, Object> unite;
			if (comptage) {
				unite = root.join("unite", JoinType.LEFT);
			} else { // une seule jointure, aussi chargée (évite une requête par fiche pour l'unité et le supérieur)
				unite = (Join<Object, Object>) root.fetch("unite", JoinType.LEFT);
				root.fetch("superieur", JoinType.LEFT);
			}
			List<Predicate> p = new ArrayList<>();
			for (String mot : mots) {
				p.add(contientMot(root, unite, cb, mot));
			}
			if (f.statut() != null) {
				p.add(cb.equal(cb.lower(root.get("statut")), f.statut().toLowerCase(Locale.ROOT)));
			}
			if (f.profil() != null) {
				p.add(cb.equal(cb.lower(root.get("profilAcces")), f.profil().toLowerCase(Locale.ROOT)));
			}
			if (f.famille() != null) {
				p.add(cb.equal(root.get("familleMetierCode"), f.famille()));
			}
			if (f.niveau() != null) {
				p.add(cb.equal(root.get("niveauSeniorite"), f.niveau()));
			}
			if (f.uniteIdentifiant() != null) {
				p.add(cb.equal(unite.get("id"), f.uniteIdentifiant()));
			}
			if (unites != null) {
				p.add(unites.isEmpty() ? cb.disjunction() : unite.get("id").in(unites));
			}
			if (f.metierACompleter()) { // D-E13.c : fiche active dont la famille ou le niveau manque
				p.add(cb.equal(cb.upper(root.get("statut")), "ACTIF"));
				p.add(cb.or(vide(cb, root.get("familleMetierCode")), vide(cb, root.get("niveauSeniorite"))));
			}
			for (CritereValide c : criteres) {
				p.add(c.predicat(root, query, cb));
			}
			return cb.and(p.toArray(new Predicate[0]));
		};
	}

	private static Predicate vide(CriteriaBuilder cb, Path<String> chemin) {
		return cb.or(cb.isNull(chemin), cb.equal(cb.trim(chemin), ""));
	}

	/** Un mot normalisé doit figurer dans l'un des champs de recherche (sans accents ni casse). */
	private static Predicate contientMot(Root<Collaborateur> root, Join<Object, Object> unite, CriteriaBuilder cb, String mot) {
		String motif = "%" + echapper(mot) + "%";
		List<Expression<String>> champs = List.of(root.get("prenom"), root.get("nom"), root.get("matricule"),
				root.get("courrielProfessionnel"), root.get("posteLibelle"), root.get("fonction"),
				root.get("departementLibelle"), unite.get("libelle"), unite.get("code"));
		List<Predicate> ou = new ArrayList<>();
		for (Expression<String> c : champs) {
			Expression<String> sansAccent = cb.function("translate", String.class, cb.lower(c), cb.literal(ACCENTS),
					cb.literal(SANS_ACCENT));
			ou.add(cb.like(sansAccent, motif, '\\'));
		}
		return cb.or(ou.toArray(new Predicate[0]));
	}

	static String echapper(String s) {
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	// ─── Critères personnalisés ────────────────────────────────────────────────

	/** Critère validé, convertible en prédicat. */
	private interface CritereValide {
		Predicate predicat(Root<Collaborateur> root, jakarta.persistence.criteria.CriteriaQuery<?> query, CriteriaBuilder cb);
	}

	private static List<CritereValide> valider(FiltreCollaborateurs f, Map<String, Def> defs) {
		List<CritereValide> r = new ArrayList<>();
		for (Map.Entry<String, Map<String, String>> e : f.criteres().entrySet()) {
			Def d = defs.get(e.getKey());
			if (d == null || !d.champ().isActif()) {
				throw invalide("Champ personnalisé inconnu ou désactivé dans le filtre : " + e.getKey() + ".");
			}
			r.add(critere(d, e.getValue()));
		}
		return r;
	}

	private static CritereValide critere(Def d, Map<String, String> c) {
		String code = d.champ().getCode();
		String nom = d.champ().getLibelle();
		TypeValeurPerso type = d.champ().getTypeValeur();
		String etat = c.get("etat");
		if (etat != null) {
			if (c.size() > 1) {
				throw invalide("« " + nom + " » : l'état ne se combine pas avec une valeur.");
			}
			boolean renseigne = "RENSEIGNE".equalsIgnoreCase(etat);
			if (!renseigne && !"VIDE".equalsIgnoreCase(etat)) {
				throw invalide("« " + nom + " » : état inconnu (RENSEIGNE ou VIDE).");
			}
			return (root, q, cb) -> {
				Predicate existe = cb.exists(sous(q, cb, root, code, null));
				return renseigne ? existe : cb.not(existe);
			};
		}
		Set<String> permis = switch (type) {
			case TEXTE -> Set.of("contient");
			case NOMBRE -> Set.of("min", "max");
			case DATE -> Set.of("du", "au");
			case LISTE -> Set.of("valeurs");
		};
		for (String k : c.keySet()) {
			if (!permis.contains(k)) {
				throw invalide("« " + nom + " » : critère « " + k + " » incompatible avec le type " + type.libelle() + ".");
			}
		}
		return switch (type) {
			case TEXTE -> {
				String mot = NormalisationLibelle.normaliser(c.get("contient"));
				if (mot.isEmpty() || mot.length() > 100) {
					throw invalide("« " + nom + " » : la recherche contient de 1 à 100 caractères.");
				}
				yield (root, q, cb) -> cb.exists(sous(q, cb, root, code, (v, sq) -> cb.like(v.<String>get("texteRecherche"),
						"%" + echapper(mot) + "%", '\\')));
			}
			case NOMBRE -> {
				BigDecimal min = nombre(c.get("min"), nom);
				BigDecimal max = nombre(c.get("max"), nom);
				yield (root, q, cb) -> cb.exists(sous(q, cb, root, code, (v, sq) -> {
					List<Predicate> p = new ArrayList<>();
					if (min != null) {
						p.add(cb.greaterThanOrEqualTo(v.<BigDecimal>get("valeurNombre"), min));
					}
					if (max != null) {
						p.add(cb.lessThanOrEqualTo(v.<BigDecimal>get("valeurNombre"), max));
					}
					return cb.and(p.toArray(new Predicate[0]));
				}));
			}
			case DATE -> {
				LocalDate du = date(c.get("du"), nom);
				LocalDate au = date(c.get("au"), nom);
				yield (root, q, cb) -> cb.exists(sous(q, cb, root, code, (v, sq) -> {
					List<Predicate> p = new ArrayList<>();
					if (du != null) {
						p.add(cb.greaterThanOrEqualTo(v.<LocalDate>get("valeurDate"), du));
					}
					if (au != null) {
						p.add(cb.lessThanOrEqualTo(v.<LocalDate>get("valeurDate"), au));
					}
					return cb.and(p.toArray(new Predicate[0]));
				}));
			}
			case LISTE -> {
				String brut = c.get("valeurs");
				List<String> codes = new ArrayList<>();
				for (String v : brut == null ? new String[0] : brut.split(",")) {
					if (!v.isBlank()) {
						codes.add(v.strip());
					}
				}
				if (codes.isEmpty()) {
					throw invalide("« " + nom + " » : au moins une valeur est attendue.");
				}
				for (String v : codes) {
					if (d.definition().valeur(v) == null) {
						throw invalide("« " + nom + " » : valeur inconnue « " + v + " ».");
					}
				}
				yield (root, q, cb) -> cb.exists(sous(q, cb, root, code, (v, sq) -> v.get("valeurListeCode").in(codes)));
			}
		};
	}

	private interface Restriction {
		Predicate de(Root<ValeurPersoFiche> v, Subquery<Integer> sq);
	}

	private static Subquery<Integer> sous(jakarta.persistence.criteria.CriteriaQuery<?> q, CriteriaBuilder cb,
			Root<Collaborateur> root, String code, Restriction r) {
		Subquery<Integer> sq = q.subquery(Integer.class);
		Root<ValeurPersoFiche> v = sq.from(ValeurPersoFiche.class);
		List<Predicate> p = new ArrayList<>();
		p.add(cb.equal(v.get("collaborateurId"), root.get("id")));
		p.add(cb.equal(v.get("champCode"), code));
		if (r != null) {
			p.add(r.de(v, sq));
		}
		return sq.select(cb.literal(1)).where(p.toArray(new Predicate[0]));
	}

	private static BigDecimal nombre(String brut, String nom) {
		if (brut == null || brut.isBlank()) {
			return null;
		}
		try {
			return new BigDecimal(brut.strip().replace(',', '.'));
		} catch (NumberFormatException e) {
			throw invalide("« " + nom + " » : nombre illisible « " + brut + " ».");
		}
	}

	private static LocalDate date(String brut, String nom) {
		if (brut == null || brut.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(brut.strip());
		} catch (DateTimeParseException e) {
			throw invalide("« " + nom + " » : date illisible « " + brut + " » (aaaa-mm-jj).");
		}
	}
}
