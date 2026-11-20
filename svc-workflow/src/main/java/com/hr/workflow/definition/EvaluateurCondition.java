package com.hr.workflow.definition;

import com.hr.workflow.catalogue.CatalogueTypesObjet.TypeVariable;
import com.hr.workflow.definition.Condition.ConditionEt;
import com.hr.workflow.definition.Condition.ConditionFeuille;
import com.hr.workflow.definition.Condition.ConditionNon;
import com.hr.workflow.definition.Condition.ConditionOu;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Évaluateur pur (sans Spring) des conditions d'une définition de workflow (§ 9.3.1, § 9.3.2).
 * Décimaux en {@link BigDecimal}, comparaisons texte sans casse ni accents, variable vide gérée selon § 9.3.1.
 */
public class EvaluateurCondition {

	private static final Pattern DIACRITIQUES = Pattern.compile("\\p{M}+");

	public boolean evaluer(Condition condition, Map<String, Object> variables, Map<String, TypeVariable> types) {
		if (condition == null) {
			return true;
		}
		Map<String, Object> vars = variables != null ? variables : Map.of();
		Map<String, TypeVariable> typeMap = types != null ? types : Map.of();
		return switch (condition) {
			case ConditionEt et -> et.et() != null && et.et().stream().allMatch(c -> evaluer(c, vars, typeMap));
			case ConditionOu ou -> ou.ou() != null && ou.ou().stream().anyMatch(c -> evaluer(c, vars, typeMap));
			case ConditionNon non -> !evaluer(non.non(), vars, typeMap);
			case ConditionFeuille feuille -> evaluerFeuille(feuille, vars, typeMap);
		};
	}

	private boolean evaluerFeuille(ConditionFeuille feuille, Map<String, Object> vars, Map<String, TypeVariable> types) {
		String champ = feuille.champ();
		String op = feuille.op();
		Object valeurActuelle = vars.get(champ);
		boolean estVide = estVide(valeurActuelle);

		if ("vide".equals(op)) {
			return estVide;
		}
		if ("non_vide".equals(op)) {
			return !estVide;
		}
		if (estVide) {
			// § 9.3.1 : variable vide -> toute comparaison est fausse sauf vide et different (vraies)
			return "different".equals(op);
		}

		TypeVariable type = types.getOrDefault(champ, deduireType(valeurActuelle));
		return switch (type) {
			case ENTIER, DECIMAL -> comparerNombres(valeurActuelle, feuille.valeur(), op);
			case DATE -> comparerDates(valeurActuelle, feuille.valeur(), op);
			case DATE_HEURE -> comparerDateHeures(valeurActuelle, feuille.valeur(), op);
			case TEXTE -> comparerTexte(valeurActuelle, feuille.valeur(), op);
			case LISTE -> comparerListe(valeurActuelle, feuille.valeur(), op);
			case BOOLEEN -> comparerBooleen(valeurActuelle, feuille.valeur(), op);
		};
	}

	private static boolean estVide(Object v) {
		if (v == null) {
			return true;
		}
		if (v instanceof String s) {
			return s.isBlank();
		}
		return false;
	}

	private static boolean comparerNombres(Object actuel, Object attendu, String op) {
		BigDecimal a = versBigDecimal(actuel);
		BigDecimal b = versBigDecimal(attendu);
		if (a == null || b == null) {
			return false;
		}
		int cmp = a.compareTo(b);
		return appliquerComparaison(cmp, op);
	}

	private static boolean comparerDates(Object actuel, Object attendu, String op) {
		LocalDate a = versLocalDate(actuel);
		LocalDate b = versLocalDate(attendu);
		if (a == null || b == null) {
			return false;
		}
		return appliquerComparaison(a.compareTo(b), op);
	}

	private static boolean comparerDateHeures(Object actuel, Object attendu, String op) {
		Instant a = versInstant(actuel);
		Instant b = versInstant(attendu);
		if (a == null || b == null) {
			return false;
		}
		return appliquerComparaison(a.compareTo(b), op);
	}

	private static boolean appliquerComparaison(int cmp, String op) {
		return switch (op) {
			case "egal" -> cmp == 0;
			case "different" -> cmp != 0;
			case "inf" -> cmp < 0;
			case "inf_egal" -> cmp <= 0;
			case "sup" -> cmp > 0;
			case "sup_egal" -> cmp >= 0;
			default -> false;
		};
	}

	private static boolean comparerTexte(Object actuel, Object attendu, String op) {
		if (attendu == null) {
			return false;
		}
		String a = normaliserTexte(String.valueOf(actuel));
		String b = normaliserTexte(String.valueOf(attendu));
		return switch (op) {
			case "egal" -> a.equals(b);
			case "different" -> !a.equals(b);
			case "contient" -> a.contains(b);
			case "ne_contient_pas" -> !a.contains(b);
			default -> false;
		};
	}

	private static boolean comparerListe(Object actuel, Object attendu, String op) {
		String a = String.valueOf(actuel).trim();
		return switch (op) {
			case "egal" -> attendu != null && a.equals(String.valueOf(attendu).trim());
			case "different" -> attendu == null || !a.equals(String.valueOf(attendu).trim());
			case "dans" -> attendu instanceof Collection<?> col
					&& col.stream().anyMatch(item -> item != null && a.equals(String.valueOf(item).trim()));
			case "hors" -> attendu instanceof Collection<?> col
					&& col.stream().noneMatch(item -> item != null && a.equals(String.valueOf(item).trim()));
			default -> false;
		};
	}

	private static boolean comparerBooleen(Object actuel, Object attendu, String op) {
		if (!"egal".equals(op)) {
			return false;
		}
		Boolean a = versBooleen(actuel);
		Boolean b = versBooleen(attendu);
		return a != null && a.equals(b);
	}

	public static String normaliserTexte(String s) {
		if (s == null) {
			return "";
		}
		String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
		return DIACRITIQUES.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
	}

	private static BigDecimal versBigDecimal(Object v) {
		if (v instanceof BigDecimal bd) {
			return bd;
		}
		if (v instanceof Number n) {
			return new BigDecimal(n.toString());
		}
		if (v instanceof String s) {
			try {
				return new BigDecimal(s.trim().replace(',', '.'));
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	private static LocalDate versLocalDate(Object v) {
		if (v instanceof LocalDate d) {
			return d;
		}
		if (v instanceof String s) {
			try {
				return LocalDate.parse(s.trim());
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}

	private static Instant versInstant(Object v) {
		if (v instanceof Instant i) {
			return i;
		}
		if (v instanceof OffsetDateTime odt) {
			return odt.toInstant();
		}
		if (v instanceof String s) {
			try {
				return OffsetDateTime.parse(s.trim()).toInstant();
			} catch (Exception e) {
				try {
					return Instant.parse(s.trim());
				} catch (Exception e2) {
					return null;
				}
			}
		}
		return null;
	}

	private static Boolean versBooleen(Object v) {
		if (v instanceof Boolean b) {
			return b;
		}
		if (v instanceof String s) {
			if ("true".equalsIgnoreCase(s.trim())) {
				return Boolean.TRUE;
			}
			if ("false".equalsIgnoreCase(s.trim())) {
				return Boolean.FALSE;
			}
		}
		return null;
	}

	private static TypeVariable deduireType(Object v) {
		if (v instanceof Boolean) {
			return TypeVariable.BOOLEEN;
		}
		if (v instanceof Number) {
			return TypeVariable.DECIMAL;
		}
		if (v instanceof LocalDate) {
			return TypeVariable.DATE;
		}
		if (v instanceof Instant || v instanceof OffsetDateTime) {
			return TypeVariable.DATE_HEURE;
		}
		return TypeVariable.TEXTE;
	}
}
