package com.hr.referentiel.service;

import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.domain.ValeursInitialesReferentiel;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.DemandeDocumentAdministratifRh;
import com.hr.referentiel.entity.DemandeFormationRh;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construit les variables envoyées à {@code svc-workflow} pour une demande M01 (§ 5.1, § 9.7.3, D-W5-06).
 * Dates en {@code AAAA-MM-JJ}, décimaux en nombres JSON, textes d'affichage aux formats D-L01.
 */
@Component
public class WorkflowVariablesBuilder {

	private static final DateTimeFormatter FORMAT_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter FORMAT_HEURE = DateTimeFormatter.ofPattern("HH:mm");

	private final SoldeCongeService soldeCongeService;
	private final ParametreService parametreService;
	private final Clock clock;

	@Autowired
	public WorkflowVariablesBuilder(
			SoldeCongeService soldeCongeService,
			ParametreService parametreService,
			Clock clock) {
		this.soldeCongeService = soldeCongeService;
		this.parametreService = parametreService;
		this.clock = clock;
	}

	/** {@code sous_type} : code du type de congé pour {@code CONGE}, {@code null} sinon (D-W5-06). */
	public String sousType(DemandeAdministrativeRh d) {
		if (d.getTypeDemande() == TypeDemandeAdministrativeRh.CONGE && d.getContenu() != null) {
			Object tc = d.getContenu().get("type_conge");
			if (tc != null && !String.valueOf(tc).isBlank()) {
				return String.valueOf(tc).trim();
			}
		}
		return null;
	}

	public Map<String, Object> construire(DemandeAdministrativeRh d) {
		Map<String, Object> vars = new LinkedHashMap<>();
		Map<String, Object> contenu = d.getContenu() != null ? d.getContenu() : Map.of();
		LocalDate aujourdhui = LocalDate.now(clock.withZone(FormatJours.TUNIS));
		LocalDateTime maintenant = LocalDateTime.now(clock.withZone(FormatJours.TUNIS));

		switch (d.getTypeDemande()) {
			case CONGE -> {
				String typeConge = sousType(d);
				LocalDate debut = extraireDate(contenu, "date_debut", d.getPeriodeDebut());
				LocalDate fin = extraireDate(contenu, "date_fin", d.getPeriodeFin());
				BigDecimal jours = d.getJoursDecomptes();
				if (jours == null && debut != null && fin != null) {
					jours = BigDecimal.valueOf(ChronoUnit.DAYS.between(debut, fin) + 1);
				}
				if (typeConge != null) {
					vars.put("type_conge", typeConge);
				}
				if (jours != null) {
					vars.put("jours_decomptes", jours);
				}
				if (debut != null) {
					vars.put("date_debut", debut.toString());
					vars.put("delai_prevenance_jours", ChronoUnit.DAYS.between(aujourdhui, debut));
				}
				if (fin != null) {
					vars.put("date_fin", fin.toString());
				}
				if (CongeDecompteService.CODE_ANNUEL.equals(typeConge) && d.getDemandeur() != null && jours != null) {
					BigDecimal acquis = soldeCongeService.acquis(d.getDemandeur().getId());
					BigDecimal reserve = soldeCongeService.reserve(d.getDemandeur().getId());
					BigDecimal disponibleApres = SoldeCongeService.STATUTS_RESERVANTS.contains(d.getStatut())
							? acquis.subtract(reserve)
							: acquis.subtract(reserve).subtract(jours);
					vars.put("disponible_apres", disponibleApres);
				}
				vars.put("piece_jointe_fournie", DemandeAdministrativeValidationService.aPieceJointe(contenu));
				vars.put("affichage_periode", formaterPeriode(debut, fin));
				vars.put("affichage_duree", jours != null ? FormatJours.jours(jours) : "");
				vars.put("affichage_type", typeConge != null
						? parametreService.libelleOuDefaut("TYPE_CONGE", typeConge)
						: "Congé");
			}
			case AUTORISATION_SORTIE -> {
				LocalDate dateJour = extraireDate(contenu, "date_jour", d.getPeriodeDebut());
				LocalTime heureDebut = extraireHeure(contenu, "heure_debut");
				LocalTime heureFin = extraireHeure(contenu, "heure_fin");
				long dureeMinutes = (heureDebut != null && heureFin != null)
						? Math.max(0, ChronoUnit.MINUTES.between(heureDebut, heureFin))
						: 0;
				vars.put("duree_minutes", dureeMinutes);
				if (dateJour != null) {
					vars.put("date_jour", dateJour.toString());
					if (heureDebut != null) {
						long heures = ChronoUnit.HOURS.between(maintenant, dateJour.atTime(heureDebut));
						vars.put("delai_prevenance_heures", heures);
					}
				}
				String periode = dateJour != null ? "le " + dateJour.format(FORMAT_FR) : "";
				if (heureDebut != null && heureFin != null) {
					periode = (periode.isEmpty() ? "" : periode + " ")
							+ "de " + heureDebut.format(FORMAT_HEURE) + " à " + heureFin.format(FORMAT_HEURE);
				}
				vars.put("affichage_periode", periode);
				vars.put("affichage_duree", formaterDureeMinutes(dureeMinutes));
				vars.put("affichage_type", "Autorisation de sortie");
			}
			case ORDRE_MISSION -> {
				Object lieu = contenu.get("lieu");
				LocalDate debut = extraireDate(contenu, "date_debut", d.getPeriodeDebut());
				LocalDate fin = extraireDate(contenu, "date_fin", d.getPeriodeFin());
				if (lieu != null) {
					vars.put("lieu", String.valueOf(lieu).trim());
				}
				if (debut != null) {
					vars.put("date_debut", debut.toString());
					vars.put("delai_prevenance_jours", ChronoUnit.DAYS.between(aujourdhui, debut));
				}
				if (fin != null) {
					vars.put("date_fin", fin.toString());
				}
				long dureeJours = (debut != null && fin != null)
						? Math.max(1, ChronoUnit.DAYS.between(debut, fin) + 1)
						: 1;
				vars.put("duree_jours", dureeJours);
				vars.put("affichage_periode", formaterPeriode(debut, fin));
				vars.put("affichage_duree", dureeJours == 1 ? "1 jour" : dureeJours + " jours");
				vars.put("affichage_type", "Ordre de mission");
			}
		}
		return vars;
	}

	/** Construit les variables envoyées à {@code svc-workflow} pour {@code DOCUMENT} (D-W6-07, Q3). */
	public Map<String, Object> construireDocument(DemandeDocumentAdministratifRh d) {
		Map<String, Object> vars = new LinkedHashMap<>();
		String codeType = d.getTypeDocument();
		if (codeType != null) {
			vars.put("type_document", codeType);
		}
		String libelleType = codeType != null ? parametreService.libelleOuDefaut("TYPE_DOCUMENT", codeType) : null;
		if (libelleType == null && codeType != null) {
			libelleType = ValeursInitialesReferentiel.libelleParDefaut(codeType);
		}
		vars.put("affichage_type", libelleType != null ? libelleType : "Document administratif");
		if (d.getDateEcheanceTraitement() != null) {
			ZonedDateTime echeanceTunis = d.getDateEcheanceTraitement().atZone(FormatJours.TUNIS);
			vars.put("affichage_sla",
					"Échéance le " + echeanceTunis.format(FORMAT_FR) + " à " + echeanceTunis.format(FORMAT_HEURE));
		}
		return vars;
	}

	/** Construit les variables envoyées à {@code svc-workflow} pour {@code FORMATION} (D-W6-07, Q5). */
	public Map<String, Object> construireFormation(DemandeFormationRh d) {
		Map<String, Object> vars = new LinkedHashMap<>();
		if (d.getOrigine() != null) {
			vars.put("origine", d.getOrigine().name());
		}
		if (d.getCoutEstime() != null) {
			vars.put("cout_estime", d.getCoutEstime());
			vars.put("affichage_cout", d.getCoutEstime().stripTrailingZeros().toPlainString() + " DT");
		}
		int dureeJours;
		if (d.getDateSouhaiteeDebut() != null && d.getDateSouhaiteeFin() != null) {
			dureeJours = (int) Math.max(1L,
					ChronoUnit.DAYS.between(d.getDateSouhaiteeDebut(), d.getDateSouhaiteeFin()) + 1);
		} else {
			int heures = d.getDureeHeures() != null ? d.getDureeHeures() : 0;
			dureeJours = Math.max(1, (int) Math.ceil(heures / 8.0));
		}
		vars.put("duree_jours", dureeJours);
		vars.put("affichage_type", d.getTypeFormation() != null ? d.getTypeFormation() : "Formation");
		if (d.getOrganisme() != null && !d.getOrganisme().isBlank()) {
			vars.put("affichage_organisme", d.getOrganisme());
		}
		String periode = formaterPeriode(d.getDateSouhaiteeDebut(), d.getDateSouhaiteeFin());
		if (!periode.isEmpty()) {
			vars.put("affichage_periode", periode);
		} else if (d.getDureeHeures() != null) {
			vars.put("affichage_periode", d.getDureeHeures() + " h");
		}
		String dureeTexte = dureeJours == 1 ? "1 jour" : dureeJours + " jours";
		if (d.getDureeHeures() != null) {
			dureeTexte = dureeTexte + " (" + d.getDureeHeures() + " h)";
		}
		vars.put("affichage_duree", dureeTexte);
		return vars;
	}

	private static LocalDate extraireDate(Map<String, Object> contenu, String cle, LocalDate repli) {
		Object v = contenu.get(cle);
		if (v instanceof LocalDate ld) {
			return ld;
		}
		if (v != null && !String.valueOf(v).isBlank()) {
			try {
				return LocalDate.parse(String.valueOf(v).trim());
			} catch (Exception ignored) {
				// repli ci-dessous
			}
		}
		return repli;
	}

	private static LocalTime extraireHeure(Map<String, Object> contenu, String cle) {
		Object v = contenu.get(cle);
		if (v instanceof LocalTime lt) {
			return lt;
		}
		if (v != null && !String.valueOf(v).isBlank()) {
			try {
				return LocalTime.parse(String.valueOf(v).trim());
			} catch (Exception ignored) {
				return null;
			}
		}
		return null;
	}

	private static String formaterPeriode(LocalDate debut, LocalDate fin) {
		if (debut == null) {
			return "";
		}
		if (fin == null || debut.equals(fin)) {
			return "le " + debut.format(FORMAT_FR);
		}
		return "du " + debut.format(FORMAT_FR) + " au " + fin.format(FORMAT_FR);
	}

	private static String formaterDureeMinutes(long minutes) {
		long h = minutes / 60;
		long m = minutes % 60;
		if (h == 0) {
			return m + " min";
		}
		if (m == 0) {
			return h + " h";
		}
		return h + " h " + (m < 10 ? "0" + m : String.valueOf(m));
	}
}
