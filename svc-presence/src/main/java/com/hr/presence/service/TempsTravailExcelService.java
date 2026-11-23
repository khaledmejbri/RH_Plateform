package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.Affectation;
import com.hr.presence.client.ReferentielClient.CalendrierPresence;
import com.hr.presence.client.ReferentielClient.CongeApprouve;
import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.EtatJourHeuresSup;
import com.hr.presence.dto.HeuresSupDtos.HeuresComptees;
import com.hr.presence.entity.PresenceHeuresSupJour;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.service.TempsTravailService.LigneAvecJour;
import com.hr.presence.web.PresenceMetierException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Export Excel (.xlsx) « Pointage MM-YYYY » de la vue « Temps de travail » (modèle « Pointage Octobre 2026 »).
 * <p>
 * Filtres obligatoires : {@code mois} (format {@code YYYY-MM}, cycle du 27 du mois M-1 au 26 du mois M) et
 * {@code collaborateurIds} (au moins un collaborateur sélectionné). Tout filtre manquant ou invalide lève une
 * {@link PresenceMetierException#badRequest(String, String)} traduite en HTTP 400.
 */
@Service
public class TempsTravailExcelService {

	public static final int MAX_COLLABORATEURS = 200;
	public static final int NB_COLONNES = 49;
	private static final BigDecimal PLAFOND_MENSUEL_HEURES = BigDecimal.valueOf(208);
	private static final BigDecimal SOIXANTE = BigDecimal.valueOf(60);

	private static final String[] ENTETES = {
			"Matricule", "Nom", "Prénom", "Qualification", "Affectation", "MOIS",
			"27", "28", "29", "30",
			"1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
			"11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
			"21", "22", "23", "24", "25", "26",
			"27", "28", "29", "30",
			"JOUR TRAVAILLE", "TOTAL HN", "HN/SEMAINE", "Absence", "HS 175%", "JF",
			"Observation", "Heure au dela de 208", "Heure 100%"
	};

	private final TempsTravailService tempsTravailService;
	private final CalculHeuresSupService calculHeuresSupService;
	private final PresenceHeuresSupJourRepository heuresSupJourRepository;
	private final ReferentielClient referentielClient;
	private final Clock clock;

	public TempsTravailExcelService(
			TempsTravailService tempsTravailService,
			CalculHeuresSupService calculHeuresSupService,
			PresenceHeuresSupJourRepository heuresSupJourRepository,
			ReferentielClient referentielClient,
			Clock clock) {
		this.tempsTravailService = tempsTravailService;
		this.calculHeuresSupService = calculHeuresSupService;
		this.heuresSupJourRepository = heuresSupJourRepository;
		this.referentielClient = referentielClient;
		this.clock = clock;
	}

	/** Valide et parse le paramètre {@code mois} ({@code YYYY-MM}) ; lève HTTP 400 sinon. */
	public static YearMonth validerMois(String mois) {
		if (mois == null || mois.isBlank()) {
			throw PresenceMetierException.badRequest("MOIS_OBLIGATOIRE",
					"Le mois (format AAAA-MM) est obligatoire pour exporter le pointage");
		}
		String nettoye = mois.trim();
		if (nettoye.length() != 7 || nettoye.charAt(4) != '-') {
			throw PresenceMetierException.badRequest("MOIS_INVALIDE",
					"Le mois doit être au format AAAA-MM (ex. 2026-10)");
		}
		try {
			return YearMonth.parse(nettoye);
		} catch (DateTimeParseException ex) {
			throw PresenceMetierException.badRequest("MOIS_INVALIDE",
					"Le mois doit être au format AAAA-MM (ex. 2026-10)");
		}
	}

	/** Valide et dédoublonne la liste des collaborateurs ; lève HTTP 400 si vide ou trop longue. */
	public static List<UUID> validerCollaborateurs(List<UUID> collaborateurIds) {
		if (collaborateurIds == null || collaborateurIds.isEmpty()) {
			throw PresenceMetierException.badRequest("COLLABORATEURS_REQUIS",
					"Sélectionnez au moins un collaborateur pour exporter le pointage");
		}
		List<UUID> uniques = new ArrayList<>(new LinkedHashSet<>(
				collaborateurIds.stream().filter(Objects::nonNull).toList()));
		if (uniques.isEmpty()) {
			throw PresenceMetierException.badRequest("COLLABORATEURS_REQUIS",
					"Sélectionnez au moins un collaborateur pour exporter le pointage");
		}
		if (uniques.size() > MAX_COLLABORATEURS) {
			throw PresenceMetierException.badRequest("TROP_DE_COLLABORATEURS",
					"L'export est limité à " + MAX_COLLABORATEURS + " collaborateurs par fichier");
		}
		return List.copyOf(uniques);
	}

	/** Nom du fichier téléchargé : {@code pointage-MM-YYYY.xlsx} (ex. {@code pointage-10-2026.xlsx}). */
	public static String nomFichier(YearMonth ym) {
		return String.format(Locale.ROOT, "pointage-%02d-%04d.xlsx", ym.getMonthValue(), ym.getYear());
	}

	/** Libellé de la feuille et du titre : {@code Pointage MM-YYYY} (ex. {@code Pointage 10-2026}). */
	public static String titreFeuille(YearMonth ym) {
		return String.format(Locale.ROOT, "Pointage %02d-%04d", ym.getMonthValue(), ym.getYear());
	}

	public byte[] exporter(String moisBrut, List<UUID> collaborateurIdsBruts, UUID siteId, UUID uniteId,
						   EtatJourHeuresSup etatFiltre) {
		YearMonth ym = validerMois(moisBrut);
		List<UUID> collaborateurIds = validerCollaborateurs(collaborateurIdsBruts);

		YearMonth moisPrecedent = ym.minusMonths(1);
		LocalDate debut = moisPrecedent.atDay(27);
		LocalDate fin = ym.atDay(26);

		// 1. Affectations et métadonnées des collaborateurs demandés
		Map<UUID, Affectation> affectationsParFiche = new LinkedHashMap<>();
		Map<String, UUID> identiteVersFiche = new HashMap<>();
		for (UUID id : collaborateurIds) {
			identiteVersFiche.put(id.toString(), id);
		}
		for (Affectation a : referentielClient.affectations(collaborateurIds)) {
			if (a != null && a.collaborateurId() != null) {
				affectationsParFiche.put(a.collaborateurId(), a);
				identiteVersFiche.put(a.collaborateurId().toString(), a.collaborateurId());
				if (a.compteId() != null) {
					identiteVersFiche.put(a.compteId().toString(), a.collaborateurId());
				}
			}
		}

		// Si un filtre d'unité est fourni, restreindre les collaborateurs exportés à cette unité
		List<UUID> collaborateursRetenus = collaborateurIds;
		if (uniteId != null) {
			Set<UUID> membresUnite = new LinkedHashSet<>();
			for (ReferentielClient.MembreUnite m : referentielClient.membresDeUnite(uniteId)) {
				if (m != null && m.collaborateurId() != null) {
					membresUnite.add(m.collaborateurId());
				}
			}
			collaborateursRetenus = collaborateurIds.stream().filter(membresUnite::contains).toList();
		}

		// 2. Calendrier de présence (jours travaillés société, jours fériés actifs, congés approuvés)
		CalendrierPresence calendrier = collaborateursRetenus.isEmpty()
				? new CalendrierPresence(List.of("LUN", "MAR", "MER", "JEU", "VEN"), List.of(), List.of())
				: referentielClient.calendrierPresence(collaborateursRetenus, debut, fin);
		Set<DayOfWeek> joursTravailles = parserJoursTravailles(calendrier != null ? calendrier.joursTravailles() : null);
		Set<LocalDate> joursFeries = calendrier != null && calendrier.joursFeries() != null
				? new LinkedHashSet<>(calendrier.joursFeries())
				: Set.of();
		Map<UUID, List<CongeApprouve>> congesParCollab = new HashMap<>();
		if (calendrier != null && calendrier.conges() != null) {
			for (CongeApprouve c : calendrier.conges()) {
				if (c != null && c.collaborateurId() != null && c.dateDebut() != null && c.dateFin() != null) {
					congesParCollab.computeIfAbsent(c.collaborateurId(), k -> new ArrayList<>()).add(c);
				}
			}
		}

		// 3. Pointages et minutes travaillées par (collaborateurId, jour) sur [debut, fin]
		Set<String> identitesRequetees = new LinkedHashSet<>();
		for (UUID id : collaborateursRetenus) {
			identitesRequetees.add(id.toString());
			Affectation a = affectationsParFiche.get(id);
			if (a != null && a.compteId() != null) {
				identitesRequetees.add(a.compteId().toString());
			}
		}

		Map<UUID, Map<LocalDate, Long>> minutesParCollabJour = new HashMap<>();
		if (!identitesRequetees.isEmpty()) {
			List<LigneAvecJour> lignes = tempsTravailService.calculerAvecJours(
					new LocalDate[] {debut, fin}, identitesRequetees, siteId, etatFiltre);
			for (LigneAvecJour l : lignes) {
				String collabBrut = l.detail().ligne().collaborateurId();
				UUID ficheId = identiteVersFiche.get(collabBrut);
				if (ficheId == null) {
					try {
						ficheId = UUID.fromString(collabBrut);
					} catch (IllegalArgumentException ignored) {
						continue;
					}
				}
				LocalDate jour = l.detail().ligne().jour();
				long minutes = l.detail().ligne().minutesTravaillees();
				minutesParCollabJour.computeIfAbsent(ficheId, k -> new HashMap<>())
						.merge(jour, minutes, Long::sum);
			}
		}

		// 4. Heures supplémentaires validées (HS 175%) sur [debut, fin]
		Map<UUID, Long> minutesHsParCollab = new HashMap<>();
		if (!identitesRequetees.isEmpty() && heuresSupJourRepository != null && calculHeuresSupService != null) {
			List<PresenceHeuresSupJour> lignesHs = heuresSupJourRepository.findAll(
					PresenceHeuresSupJourRepository.comptage(debut, fin, siteId, null, null, identitesRequetees, null, null));
			if (lignesHs != null && !lignesHs.isEmpty()) {
				List<HeuresComptees> comptees = calculHeuresSupService.compter(lignesHs);
				if (comptees != null) {
					for (HeuresComptees hc : comptees) {
						if (hc == null || hc.minutesComptees() <= 0 || hc.collaborateurId() == null) {
							continue;
						}
						UUID ficheId = identiteVersFiche.get(hc.collaborateurId());
						if (ficheId == null) {
							try {
								ficheId = UUID.fromString(hc.collaborateurId());
							} catch (IllegalArgumentException ignored) {
								continue;
							}
						}
						minutesHsParCollab.merge(ficheId, hc.minutesComptees(), Long::sum);
					}
				}
			}
		}

		LocalDate aujourdhui = clock.instant().atZone(ClockConfig.ZONE_TUNIS).toLocalDate();
		return genererClasseur(
				ym,
				collaborateursRetenus,
				affectationsParFiche,
				joursTravailles,
				joursFeries,
				congesParCollab,
				minutesParCollabJour,
				minutesHsParCollab,
				aujourdhui);
	}

	private byte[] genererClasseur(
			YearMonth ym,
			List<UUID> collaborateurs,
			Map<UUID, Affectation> affectationsParFiche,
			Set<DayOfWeek> joursTravailles,
			Set<LocalDate> joursFeries,
			Map<UUID, List<CongeApprouve>> congesParCollab,
			Map<UUID, Map<LocalDate, Long>> minutesParCollabJour,
			Map<UUID, Long> minutesHsParCollab,
			LocalDate aujourdhui) {
		try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			String nomFeuille = titreFeuille(ym);
			Sheet sheet = wb.createSheet(nomFeuille);

			StylesClasseur styles = new StylesClasseur(wb);

			YearMonth moisPrecedent = ym.minusMonths(1);
			// Dates correspondant aux colonnes 6..35 (index 0..29 de la grille de jours)
			LocalDate[] datesColonnes = new LocalDate[30];
			for (int j = 27; j <= 30; j++) {
				int colOffset = j - 27; // 0..3 -> cols 6..9
				if (j <= moisPrecedent.lengthOfMonth()) {
					datesColonnes[colOffset] = moisPrecedent.atDay(j);
				}
			}
			for (int j = 1; j <= 26; j++) {
				int colOffset = 4 + (j - 1); // 4..29 -> cols 10..35
				datesColonnes[colOffset] = ym.atDay(j);
			}

			// --- Ligne 0 (index 0) : Titre A1:E1 + 190 en AF1 (col 31) + dimanches en jaune ---
			Row row0 = sheet.createRow(0);
			for (int col = 0; col < NB_COLONNES; col++) {
				Cell c = row0.createCell(col);
				if (estDimancheColonne(col, datesColonnes)) {
					c.setCellStyle(styles.dimancheEntete);
				}
			}
			Cell titreCell = row0.getCell(0);
			titreCell.setCellValue(nomFeuille);
			titreCell.setCellStyle(styles.titre);
			sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

			Cell cell190 = row0.getCell(31);
			cell190.setCellValue(190);
			if (!estDimancheColonne(31, datesColonnes)) {
				cell190.setCellStyle(styles.referenceNombre);
			}

			// --- Ligne 1 (index 1) : 208 en AR2 (col 43) et AV2 (col 47) + dimanches en jaune ---
			Row row1 = sheet.createRow(1);
			for (int col = 0; col < NB_COLONNES; col++) {
				Cell c = row1.createCell(col);
				if (estDimancheColonne(col, datesColonnes)) {
					c.setCellStyle(styles.dimancheEntete);
				}
			}
			Cell cell208Ar = row1.getCell(43);
			cell208Ar.setCellValue(208);
			cell208Ar.setCellStyle(styles.referenceNombre);

			Cell cell208Av = row1.getCell(47);
			cell208Av.setCellValue(208);
			cell208Av.setCellStyle(styles.referenceNombre);

			// --- Ligne 2 (index 2) : 49 en-têtes exactes ---
			Row row2 = sheet.createRow(2);
			for (int col = 0; col < NB_COLONNES; col++) {
				Cell c = row2.createCell(col);
				c.setCellValue(ENTETES[col]);
				c.setCellStyle(estDimancheColonne(col, datesColonnes) ? styles.dimancheEntete : styles.entete);
			}

			// --- Lignes collaborateurs (index >= 3) ---
			String moisIso = ym.atDay(1).toString();
			int rowIdx = 3;
			for (UUID collabId : collaborateurs) {
				Row row = sheet.createRow(rowIdx++);
				Affectation a = affectationsParFiche.get(collabId);
				Map<LocalDate, Long> minutesDuCollab = minutesParCollabJour.getOrDefault(collabId, Map.of());
				List<CongeApprouve> congesDuCollab = congesParCollab.getOrDefault(collabId, List.of());

				String matricule = a != null && a.matricule() != null ? a.matricule() : "";
				String nom = "";
				if (a != null) {
					if (a.nomFamille() != null && !a.nomFamille().isBlank()) {
						nom = a.nomFamille();
					} else if (a.nom() != null) {
						nom = a.nom();
					}
				}
				String prenom = a != null && a.prenom() != null ? a.prenom() : "";
				String qualification = a != null ? a.qualificationAffichee() : "";
				String affectation = a != null ? a.affectationAffichee() : "";

				ecrireTexte(row, 0, matricule, styles.celluleTexte);
				ecrireTexte(row, 1, nom, styles.celluleTexte);
				ecrireTexte(row, 2, prenom, styles.celluleTexte);
				ecrireTexte(row, 3, qualification, styles.celluleTexte);
				ecrireTexte(row, 4, affectation, styles.celluleTexte);
				ecrireTexte(row, 5, moisIso, styles.celluleCentre);

				int joursTravaillesCount = 0;
				int jfCount = 0;
				BigDecimal totalHn = BigDecimal.ZERO;

				// Colonnes 6..35 : jours 27..30 (M-1) et 1..26 (M)
				for (int i = 0; i < 30; i++) {
					int col = 6 + i;
					LocalDate date = datesColonnes[i];
					Cell cell = row.createCell(col);
					if (date == null) {
						cell.setCellStyle(styles.celluleCentre);
						continue;
					}
					boolean dimanche = date.getDayOfWeek() == DayOfWeek.SUNDAY;
					CellStyle styleNormal = dimanche ? styles.dimancheCellule : styles.celluleCentre;

					long minutes = minutesDuCollab.getOrDefault(date, 0L);
					if (minutes > 0) {
						BigDecimal heures = versHeures(minutes);
						cell.setCellValue(heures.doubleValue());
						cell.setCellStyle(styleNormal);
						joursTravaillesCount++;
						totalHn = totalHn.add(heures);
					} else if (estEnConge(date, congesDuCollab)) {
						cell.setCellValue("C");
						cell.setCellStyle(styles.congeBleu);
					} else if (joursFeries.contains(date)) {
						cell.setCellValue("JF");
						cell.setCellStyle(styleNormal);
						jfCount++;
					} else if (!joursTravailles.contains(date.getDayOfWeek())) {
						cell.setCellValue("RH");
						cell.setCellStyle(styleNormal);
					} else if (date.isAfter(aujourdhui)) {
						cell.setCellValue("0");
						cell.setCellStyle(styleNormal);
					} else {
						cell.setCellValue("A");
						cell.setCellStyle(styleNormal);
					}
				}

				// Gestion du 31 du mois précédent (si M-1 a 31 jours) pour ne perdre aucune heure du cycle 27..26
				String observation = "";
				if (moisPrecedent.lengthOfMonth() == 31) {
					LocalDate jour31 = moisPrecedent.atDay(31);
					long min31 = minutesDuCollab.getOrDefault(jour31, 0L);
					if (min31 > 0) {
						BigDecimal h31 = versHeures(min31);
						joursTravaillesCount++;
						totalHn = totalHn.add(h31);
						observation = "31/" + String.format(Locale.ROOT, "%02d", moisPrecedent.getMonthValue())
								+ ": " + h31.stripTrailingZeros().toPlainString() + "h";
					} else if (estEnConge(jour31, congesDuCollab)) {
						observation = "31/" + String.format(Locale.ROOT, "%02d", moisPrecedent.getMonthValue()) + ": C";
					} else if (joursFeries.contains(jour31)) {
						jfCount++;
						observation = "31/" + String.format(Locale.ROOT, "%02d", moisPrecedent.getMonthValue()) + ": JF";
					}
				}

				// Colonnes 36..39 : 27, 28, 29, 30 de fin (toujours 0 selon le modèle)
				for (int col = 36; col <= 39; col++) {
					Cell c = row.createCell(col);
					c.setCellValue(0);
					c.setCellStyle(styles.celluleCentre);
				}

				// Colonnes de synthèse 40..48
				long minHs = minutesHsParCollab.getOrDefault(collabId, 0L);
				BigDecimal hs175 = minHs > 0 ? versHeures(minHs) : BigDecimal.ZERO;
				if (hs175.compareTo(totalHn) > 0) {
					hs175 = totalHn;
				}
				BigDecimal hnSemaine = totalHn.subtract(hs175);
				BigDecimal absence = PLAFOND_MENSUEL_HEURES.subtract(hnSemaine);
				if (absence.compareTo(BigDecimal.ZERO) < 0) {
					absence = BigDecimal.ZERO;
				}

				// 40 JOUR TRAVAILLE
				Cell cJourTrav = row.createCell(40);
				cJourTrav.setCellValue(joursTravaillesCount);
				cJourTrav.setCellStyle(styles.celluleNombre);

				// 41 TOTAL HN
				Cell cTotalHn = row.createCell(41);
				cTotalHn.setCellValue(totalHn.doubleValue());
				cTotalHn.setCellStyle(styles.celluleNombre);

				// 42 HN/SEMAINE
				Cell cHnSemaine = row.createCell(42);
				cHnSemaine.setCellValue(hnSemaine.doubleValue());
				cHnSemaine.setCellStyle(styles.celluleNombre);

				// 43 Absence
				Cell cAbsence = row.createCell(43);
				cAbsence.setCellValue(absence.doubleValue());
				cAbsence.setCellStyle(styles.celluleNombre);

				// 44 HS 175%
				Cell cHs = row.createCell(44);
				cHs.setCellValue(hs175.doubleValue());
				cHs.setCellStyle(styles.celluleNombre);

				// 45 JF
				Cell cJf = row.createCell(45);
				cJf.setCellValue(jfCount);
				cJf.setCellStyle(styles.celluleNombre);

				// 46 Observation
				ecrireTexte(row, 46, observation, styles.celluleTexte);

				// 47 Heure au dela de 208 & 48 Heure 100%
				Cell cAuDela208 = row.createCell(47);
				cAuDela208.setCellStyle(styles.celluleNombre);
				Cell cHeure100 = row.createCell(48);
				cHeure100.setCellStyle(styles.celluleNombre);
				if (hnSemaine.compareTo(PLAFOND_MENSUEL_HEURES) > 0) {
					cAuDela208.setCellValue(PLAFOND_MENSUEL_HEURES.doubleValue());
					cHeure100.setCellValue(hnSemaine.subtract(PLAFOND_MENSUEL_HEURES).doubleValue());
				}
			}

			// Largeurs de colonnes lisibles
			sheet.setColumnWidth(0, 14 * 256);
			sheet.setColumnWidth(1, 18 * 256);
			sheet.setColumnWidth(2, 18 * 256);
			sheet.setColumnWidth(3, 20 * 256);
			sheet.setColumnWidth(4, 18 * 256);
			sheet.setColumnWidth(5, 13 * 256);
			for (int col = 6; col <= 39; col++) {
				sheet.setColumnWidth(col, 5 * 256);
			}
			for (int col = 40; col < NB_COLONNES; col++) {
				sheet.setColumnWidth(col, 16 * 256);
			}

			wb.write(out);
			return out.toByteArray();
		} catch (IOException ex) {
			throw new IllegalStateException("Erreur lors de la génération du classeur Excel de pointage", ex);
		}
	}

	private static boolean estDimancheColonne(int col, LocalDate[] datesColonnes) {
		if (col < 6 || col > 35) {
			return false;
		}
		LocalDate d = datesColonnes[col - 6];
		return d != null && d.getDayOfWeek() == DayOfWeek.SUNDAY;
	}

	private static boolean estEnConge(LocalDate date, List<CongeApprouve> conges) {
		for (CongeApprouve c : conges) {
			if (!date.isBefore(c.dateDebut()) && !date.isAfter(c.dateFin())) {
				return true;
			}
		}
		return false;
	}

	private static BigDecimal versHeures(long minutes) {
		return BigDecimal.valueOf(minutes).divide(SOIXANTE, 2, RoundingMode.HALF_UP).stripTrailingZeros();
	}

	private static void ecrireTexte(Row row, int col, String brut, CellStyle style) {
		Cell cell = row.createCell(col);
		cell.setCellValue(neutraliserFormule(brut));
		cell.setCellStyle(style);
	}

	/** Neutralise l'injection de formules Excel (D-I41) en préfixant par une apostrophe. */
	public static String neutraliserFormule(String valeur) {
		if (valeur == null || valeur.isEmpty()) {
			return "";
		}
		char c = valeur.charAt(0);
		if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
			return "'" + valeur;
		}
		return valeur;
	}

	private static Set<DayOfWeek> parserJoursTravailles(List<String> codes) {
		if (codes == null || codes.isEmpty()) {
			return EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
		}
		Set<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
		for (String code : codes) {
			if (code == null) {
				continue;
			}
			switch (code.trim().toUpperCase(Locale.ROOT)) {
				case "LUN", "MONDAY" -> set.add(DayOfWeek.MONDAY);
				case "MAR", "TUESDAY" -> set.add(DayOfWeek.TUESDAY);
				case "MER", "WEDNESDAY" -> set.add(DayOfWeek.WEDNESDAY);
				case "JEU", "THURSDAY" -> set.add(DayOfWeek.THURSDAY);
				case "VEN", "FRIDAY" -> set.add(DayOfWeek.FRIDAY);
				case "SAM", "SATURDAY" -> set.add(DayOfWeek.SATURDAY);
				case "DIM", "SUNDAY" -> set.add(DayOfWeek.SUNDAY);
				default -> {
				}
			}
		}
		if (set.isEmpty()) {
			return EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
		}
		return set;
	}

	private static final class StylesClasseur {
		final XSSFCellStyle titre;
		final XSSFCellStyle referenceNombre;
		final XSSFCellStyle entete;
		final XSSFCellStyle dimancheEntete;
		final XSSFCellStyle dimancheCellule;
		final XSSFCellStyle congeBleu;
		final XSSFCellStyle celluleTexte;
		final XSSFCellStyle celluleCentre;
		final XSSFCellStyle celluleNombre;

		StylesClasseur(XSSFWorkbook wb) {
			DefaultIndexedColorMap colorMap = new DefaultIndexedColorMap();
			XSSFColor jauneDimanche = new XSSFColor(new byte[] {(byte) 255, (byte) 255, (byte) 0}, colorMap);
			XSSFColor bleuConge = new XSSFColor(new byte[] {(byte) 0, (byte) 176, (byte) 240}, colorMap);
			XSSFColor grisEntete = new XSSFColor(new byte[] {(byte) 217, (byte) 225, (byte) 242}, colorMap);

			XSSFFont policeGras = wb.createFont();
			policeGras.setBold(true);

			XSSFFont policeTitre = wb.createFont();
			policeTitre.setBold(true);
			policeTitre.setFontHeightInPoints((short) 12);

			titre = wb.createCellStyle();
			titre.setFont(policeTitre);
			titre.setAlignment(HorizontalAlignment.CENTER);
			titre.setVerticalAlignment(VerticalAlignment.CENTER);

			referenceNombre = wb.createCellStyle();
			referenceNombre.setFont(policeGras);
			referenceNombre.setAlignment(HorizontalAlignment.CENTER);
			appliquerBordures(referenceNombre);

			entete = wb.createCellStyle();
			entete.setFont(policeGras);
			entete.setAlignment(HorizontalAlignment.CENTER);
			entete.setVerticalAlignment(VerticalAlignment.CENTER);
			entete.setFillForegroundColor(grisEntete);
			entete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			appliquerBordures(entete);

			dimancheEntete = wb.createCellStyle();
			dimancheEntete.setFont(policeGras);
			dimancheEntete.setAlignment(HorizontalAlignment.CENTER);
			dimancheEntete.setVerticalAlignment(VerticalAlignment.CENTER);
			dimancheEntete.setFillForegroundColor(jauneDimanche);
			dimancheEntete.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			appliquerBordures(dimancheEntete);

			dimancheCellule = wb.createCellStyle();
			dimancheCellule.setAlignment(HorizontalAlignment.CENTER);
			dimancheCellule.setVerticalAlignment(VerticalAlignment.CENTER);
			dimancheCellule.setFillForegroundColor(jauneDimanche);
			dimancheCellule.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			appliquerBordures(dimancheCellule);

			congeBleu = wb.createCellStyle();
			congeBleu.setFont(policeGras);
			congeBleu.setAlignment(HorizontalAlignment.CENTER);
			congeBleu.setVerticalAlignment(VerticalAlignment.CENTER);
			congeBleu.setFillForegroundColor(bleuConge);
			congeBleu.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			appliquerBordures(congeBleu);

			celluleTexte = wb.createCellStyle();
			celluleTexte.setAlignment(HorizontalAlignment.LEFT);
			celluleTexte.setVerticalAlignment(VerticalAlignment.CENTER);
			appliquerBordures(celluleTexte);

			celluleCentre = wb.createCellStyle();
			celluleCentre.setAlignment(HorizontalAlignment.CENTER);
			celluleCentre.setVerticalAlignment(VerticalAlignment.CENTER);
			appliquerBordures(celluleCentre);

			celluleNombre = wb.createCellStyle();
			celluleNombre.setAlignment(HorizontalAlignment.CENTER);
			celluleNombre.setVerticalAlignment(VerticalAlignment.CENTER);
			appliquerBordures(celluleNombre);
		}

		private static void appliquerBordures(XSSFCellStyle style) {
			style.setBorderTop(BorderStyle.THIN);
			style.setBorderBottom(BorderStyle.THIN);
			style.setBorderLeft(BorderStyle.THIN);
			style.setBorderRight(BorderStyle.THIN);
		}
	}
}
