package com.hr.referentiel.importation;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Fabrique de fichiers de test (jamais committés) : classeurs .xlsx, archives piégées. */
public final class XlsxTestes {

	private XlsxTestes() {
	}

	/** Cellules : String, Number, Boolean ou LocalDate ; {@code null} = cellule vide. */
	public static byte[] classeur(List<List<Object>> lignes) {
		return classeur(List.of(lignes));
	}

	public static byte[] classeur(List<List<List<Object>>> feuilles, String... ignore) {
		ByteArrayOutputStream sortie = new ByteArrayOutputStream();
		try (Workbook wb = new Workbook(sortie, "Test", "1.0")) {
			int n = 1;
			for (List<List<Object>> lignes : feuilles) {
				Worksheet ws = wb.newWorksheet("Feuille" + n++);
				for (int r = 0; r < lignes.size(); r++) {
					List<Object> cellules = lignes.get(r);
					for (int c = 0; c < cellules.size(); c++) {
						Object v = cellules.get(c);
						if (v instanceof String s) {
							ws.value(r, c, s);
						} else if (v instanceof Number num) {
							ws.value(r, c, num.doubleValue());
						} else if (v instanceof Boolean b) {
							ws.value(r, c, b);
						} else if (v instanceof LocalDate d) {
							ws.value(r, c, d);
							ws.style(r, c).format("dd/mm/yyyy").set();
						}
					}
				}
			}
			wb.finish();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return sortie.toByteArray();
	}

	/** Copie d'un .xlsx en remplaçant le contenu d'une entrée de l'archive (lecture par le répertoire central). */
	public static byte[] remplacerEntree(byte[] xlsx, String nomEntree, byte[] nouveau) {
		ByteArrayOutputStream sortie = new ByteArrayOutputStream();
		try (org.apache.commons.compress.archivers.zip.ZipFile in = org.apache.commons.compress.archivers.zip.ZipFile.builder()
				.setSeekableByteChannel(new org.apache.commons.compress.utils.SeekableInMemoryByteChannel(xlsx)).get();
				ZipOutputStream out = new ZipOutputStream(sortie)) {
			var entrees = in.getEntries();
			while (entrees.hasMoreElements()) {
				var e = entrees.nextElement();
				out.putNextEntry(new ZipEntry(e.getName()));
				out.write(e.getName().equals(nomEntree) ? nouveau : in.getInputStream(e).readAllBytes());
				out.closeEntry();
			}
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		return sortie.toByteArray();
	}

	/** Archive .xlsx valide dont une entrée se décompresse en {@code octets} zéros (bombe de décompression). */
	public static byte[] bombe(long octets) {
		ByteArrayOutputStream sortie = new ByteArrayOutputStream();
		try (ZipOutputStream out = new ZipOutputStream(sortie)) {
			out.putNextEntry(new ZipEntry("xl/workbook.xml"));
			out.write("<workbook/>".getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
			out.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
			byte[] bloc = new byte[1024 * 1024];
			for (long ecrit = 0; ecrit < octets; ecrit += bloc.length) {
				out.write(bloc);
			}
			out.closeEntry();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return sortie.toByteArray();
	}

	public static byte[] zipAvec(String entree, String contenu) {
		ByteArrayOutputStream sortie = new ByteArrayOutputStream();
		try (ZipOutputStream out = new ZipOutputStream(sortie)) {
			out.putNextEntry(new ZipEntry(entree));
			out.write(contenu.getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return sortie.toByteArray();
	}
}
