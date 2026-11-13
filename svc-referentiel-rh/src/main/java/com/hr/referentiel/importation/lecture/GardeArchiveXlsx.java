package com.hr.referentiel.importation.lecture;

import com.hr.referentiel.web.ReferentielMetierException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Garde d'archive appliquée AVANT de passer le fichier à fastexcel (D-I11) : total décompressé au plus 100 Mo, rapport de
 * compression au plus 100, arrêt dès le dépassement (pas de décompression complète), refus des classeurs à macros et des
 * archives qui ne sont pas un classeur Excel. L'archive est lue en mémoire par son répertoire central (jamais écrite sur
 * disque) ; les tailles déclarées ne sont pas crues, on compte les octets réellement décompressés. Aucune ressource
 * externe n'est jamais résolue.
 */
public final class GardeArchiveXlsx {

	public static final long DECOMPRESSE_MAX = 100L * 1024 * 1024;
	public static final int RAPPORT_MAX = 100;
	private static final int ENTREES_MAX = 10_000;
	/** Plancher du rapport : un très petit classeur ne doit pas être refusé pour son taux de compression. */
	private static final long PLANCHER_OCTETS = 1024;

	private GardeArchiveXlsx() {
	}

	public static void verifier(byte[] octets) {
		long plafond = Math.min(DECOMPRESSE_MAX, (long) RAPPORT_MAX * Math.max(octets.length, PLANCHER_OCTETS));
		long total = 0;
		int entrees = 0;
		boolean classeur = false;
		byte[] tampon = new byte[64 * 1024];
		try (ZipFile zip = ZipFile.builder().setSeekableByteChannel(new SeekableInMemoryByteChannel(octets)).get()) {
			Enumeration<ZipArchiveEntry> liste = zip.getEntries();
			while (liste.hasMoreElements()) {
				ZipArchiveEntry e = liste.nextElement();
				if (++entrees > ENTREES_MAX) {
					throw complexe();
				}
				String nom = e.getName().toLowerCase(Locale.ROOT);
				if (nom.equals("xl/workbook.xml")) {
					classeur = true;
				}
				if (nom.equals("xl/vbaproject.bin")) {
					throw nonSupporte();
				}
				StringBuilder types = nom.equals("[content_types].xml") ? new StringBuilder() : null;
				try (InputStream in = zip.getInputStream(e)) {
					int n;
					while ((n = in.read(tampon)) > 0) {
						total += n;
						if (total > plafond) {
							throw complexe();
						}
						if (types != null && types.length() < 200_000) {
							types.append(new String(tampon, 0, n, StandardCharsets.ISO_8859_1));
						}
					}
				}
				if (types != null && types.toString().toLowerCase(Locale.ROOT).contains("macroenabled")) {
					throw nonSupporte();
				}
			}
		} catch (IOException | RuntimeException e) {
			if (e instanceof ReferentielMetierException m) {
				throw m;
			}
			throw nonSupporte();
		}
		if (!classeur) {
			throw nonSupporte();
		}
	}

	private static ReferentielMetierException complexe() {
		return ReferentielMetierException.badRequest("FICHIER_TROP_COMPLEXE",
				"Le fichier est trop complexe à lire (archive trop volumineuse une fois décompressée).");
	}

	private static ReferentielMetierException nonSupporte() {
		return ReferentielMetierException.badRequest("FORMAT_NON_SUPPORTE",
				"Format non supporté : utilisez un classeur .xlsx sans macro ou un fichier .csv.");
	}
}
