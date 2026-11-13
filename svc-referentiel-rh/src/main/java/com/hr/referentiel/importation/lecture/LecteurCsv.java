package com.hr.referentiel.importation.lecture;

import com.hr.referentiel.importation.lecture.Fichier.LigneBrute;
import com.hr.referentiel.web.ReferentielMetierException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lecture CSV : UTF-8 avec ou sans BOM, sinon Windows-1252 ; séparateur « ; » ou « , » selon le plus fréquent dans la
 * première ligne non vide ; guillemets standard ; lignes vides ignorées.
 */
public final class LecteurCsv {

	private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

	private LecteurCsv() {
	}

	public static Fichier lire(byte[] octets) {
		String texte = decoder(octets);
		if (texte.startsWith("﻿")) {
			texte = texte.substring(1);
		}
		char separateur = separateur(texte);
		CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(separateur).setIgnoreEmptyLines(false)
				.setTrim(false).build();
		List<LigneBrute> lignes = new ArrayList<>();
		long cellules = 0;
		boolean tronque = false;
		try (CSVParser parser = format.parse(new StringReader(texte))) {
			for (CSVRecord r : parser) {
				List<String> valeurs = new ArrayList<>(Math.min(r.size(), Fichier.COLONNES_MAX));
				boolean nonVide = false;
				if (r.size() > Fichier.COLONNES_MAX) {
					for (int i = Fichier.COLONNES_MAX; i < r.size(); i++) {
						if (!r.get(i).isBlank()) {
							throw ReferentielMetierException.badRequest("FICHIER_TROP_COMPLEXE",
									"Le fichier est trop complexe à lire (plus de " + Fichier.COLONNES_MAX + " colonnes).");
						}
					}
				}
				int largeur = Math.min(r.size(), Fichier.COLONNES_MAX);
				for (int i = 0; i < largeur; i++) {
					String v = r.get(i);
					String c = v == null ? "" : v;
					if (c.length() > Fichier.CELLULE_MAX) {
						c = c.substring(0, Fichier.CELLULE_MAX);
					}
					if (!c.isBlank()) {
						nonVide = true;
						if (++cellules > Fichier.CELLULES_MAX) {
							throw ReferentielMetierException.badRequest("FICHIER_TROP_COMPLEXE",
									"Le fichier est trop complexe à lire (plus de 500 000 cellules renseignées).");
						}
					}
					valeurs.add(c);
				}
				if (!nonVide) {
					continue;
				}
				while (!valeurs.isEmpty() && valeurs.get(valeurs.size() - 1).isEmpty()) {
					valeurs.remove(valeurs.size() - 1);
				}
				if (lignes.size() >= Fichier.LIGNES_MAX_LUES) {
					tronque = true;
					break;
				}
				lignes.add(new LigneBrute((int) r.getRecordNumber(), List.copyOf(valeurs)));
			}
		} catch (IOException | RuntimeException e) {
			if (e instanceof ReferentielMetierException m) {
				throw m;
			}
			throw ReferentielMetierException.badRequest("FORMAT_NON_SUPPORTE", "Format non supporté : le fichier CSV est illisible.");
		}
		return new Fichier(lignes, tronque);
	}

	static String decoder(byte[] octets) {
		try {
			return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(octets)).toString();
		} catch (CharacterCodingException e) {
			return new String(octets, WINDOWS_1252);
		}
	}

	/** Séparateur le plus fréquent sur les dix premières lignes non vides (une ligne de titre ne doit pas fausser le choix). */
	static char separateur(String texte) {
		long pointsVirgules = 0;
		long virgules = 0;
		int vues = 0;
		for (String ligne : texte.split("\\R", -1)) {
			if (ligne.isBlank()) {
				continue;
			}
			pointsVirgules += ligne.chars().filter(c -> c == ';').count();
			virgules += ligne.chars().filter(c -> c == ',').count();
			if (++vues >= 10) {
				break;
			}
		}
		return virgules > pointsVirgules ? ',' : ';';
	}
}
