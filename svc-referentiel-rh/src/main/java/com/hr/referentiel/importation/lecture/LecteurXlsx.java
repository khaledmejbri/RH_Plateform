package com.hr.referentiel.importation.lecture;

import com.hr.referentiel.importation.lecture.Fichier.LigneBrute;
import com.hr.referentiel.web.ReferentielMetierException;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Lecture d'un classeur .xlsx en flux (fastexcel-reader, DTD désactivée) : feuilles parcourues dans l'ordre jusqu'à la
 * première qui contient des lignes ; formule = valeur en cache ; nombre entier sans décimale ; cellule texte telle quelle
 * (zéros de tête conservés) ; cellule de date rendue en {@code aaaa-mm-jj} ; nombre issu d'une formule arrondi au
 * millième (D-S09.g). Plafond de 500 000 cellules non vides (400 FICHIER_TROP_COMPLEXE).
 */
public final class LecteurXlsx {

	private LecteurXlsx() {
	}

	public static Fichier lire(byte[] octets) {
		GardeArchiveXlsx.verifier(octets);
		try (ReadableWorkbook classeur = new ReadableWorkbook(new ByteArrayInputStream(octets),
				new org.dhatim.fastexcel.reader.ReadingOptions(true, false))) {
			for (Sheet feuille : (Iterable<Sheet>) classeur.getSheets()::iterator) {
				Fichier f = lireFeuille(feuille);
				if (!f.lignes().isEmpty()) {
					return f;
				}
			}
			return new Fichier(List.of(), false);
		} catch (ReferentielMetierException e) {
			throw e;
		} catch (IOException | RuntimeException e) {
			throw ReferentielMetierException.badRequest("FORMAT_NON_SUPPORTE",
					"Format non supporté : le fichier est illisible ou protégé.");
		}
	}

	private static Fichier lireFeuille(Sheet feuille) throws IOException {
		List<LigneBrute> lignes = new ArrayList<>();
		long cellules = 0;
		boolean tronque = false;
		try (Stream<Row> flux = feuille.openStream()) {
			java.util.Iterator<Row> it = flux.iterator();
			while (it.hasNext()) {
				Row row = it.next();
				List<String> valeurs = new ArrayList<>();
				boolean nonVide = false;
				int n = row.getCellCount();
				if (n > Fichier.COLONNES_MAX) {
					// la lecture s'arrête à la première ligne fautive : une seule ligne large est jamais gardée en mémoire
					for (int i = Fichier.COLONNES_MAX; i < n; i++) {
						if (!texte(row.getCell(i)).isEmpty()) {
							throw ReferentielMetierException.badRequest("FICHIER_TROP_COMPLEXE",
									"Le fichier est trop complexe à lire (plus de " + Fichier.COLONNES_MAX + " colonnes).");
						}
					}
					n = Fichier.COLONNES_MAX;
				}
				for (int i = 0; i < n; i++) {
					String v = texte(row.getCell(i));
					valeurs.add(v);
					if (!v.isEmpty()) {
						nonVide = true;
						if (++cellules > Fichier.CELLULES_MAX) {
							throw ReferentielMetierException.badRequest("FICHIER_TROP_COMPLEXE",
									"Le fichier est trop complexe à lire (plus de 500 000 cellules renseignées).");
						}
					}
				}
				if (!nonVide) {
					continue;
				}
				while (!valeurs.isEmpty() && valeurs.get(valeurs.size() - 1).isEmpty()) {
					valeurs.remove(valeurs.size() - 1); // cellules vides en fin de ligne coupées
				}
				if (lignes.size() >= Fichier.LIGNES_MAX_LUES) {
					tronque = true;
					break;
				}
				lignes.add(new LigneBrute(row.getRowNum(), List.copyOf(valeurs)));
			}
		}
		return new Fichier(lignes, tronque);
	}

	static String texte(Cell cell) {
		if (cell == null || cell.getType() == CellType.EMPTY) {
			return "";
		}
		try {
			Object valeur = cell.getValue();
			if (valeur == null) {
				return "";
			}
			if (cell.getType() == CellType.NUMBER || valeur instanceof BigDecimal) {
				String format = cell.getDataFormatString();
				if (estFormatDate(format)) {
					LocalDate jour = cell.asDate().toLocalDate();
					return jour.toString();
				}
				BigDecimal n = cell.asNumber();
				if (cell.getFormula() != null) {
					n = n.setScale(3, RoundingMode.HALF_UP);
				}
				return n.stripTrailingZeros().toPlainString();
			}
			String t = String.valueOf(valeur);
			return t.length() > Fichier.CELLULE_MAX ? t.substring(0, Fichier.CELLULE_MAX) : t;
		} catch (RuntimeException e) {
			return "";
		}
	}

	private static boolean estFormatDate(String format) {
		if (format == null) {
			return false;
		}
		String f = format.toLowerCase(Locale.ROOT).replaceAll("\\[[^]]*]|\"[^\"]*\"", "");
		return f.contains("yy") || f.contains("dd") || f.contains("mm/") || f.contains("/mm") || f.contains("d/m");
	}
}
