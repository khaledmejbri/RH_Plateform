package com.hr.presence.service;

import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.SignalVigilance;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresenceSite;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Construction des variables envoyées à {@code svc-workflow} pour {@code POINTAGE_MANUEL} et {@code HEURES_SUP}.
 * Noms, types et textes d'affichage alignés sur {@code CatalogueTypesObjet} de {@code svc-workflow}.
 */
@Component
public class PresenceWorkflowVariablesBuilder {

	public static final String TYPE_POINTAGE_MANUEL = "POINTAGE_MANUEL";
	public static final String TYPE_HEURES_SUP = "HEURES_SUP";

	private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");
	private static final DateTimeFormatter FORMAT_DATE_HEURE_FR =
			DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(TUNIS);
	private static final DateTimeFormatter FORMAT_DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	public Map<String, Object> pourPointageManuel(PresenceDemandeManuelle d) {
		Map<String, Object> vars = new LinkedHashMap<>();
		List<SignalVigilance> signaux = d.getSignaux();
		vars.put("signale", !signaux.isEmpty());
		vars.put("nb_signaux", signaux.size());
		if (d.getType() != null) {
			vars.put("type_pointage", d.getType().name());
			vars.put("affichage_type", d.getType() == PointageType.SORTIE ? "Sortie manuelle" : "Entrée manuelle");
		} else {
			vars.put("affichage_type", "Pointage manuel");
		}
		if (d.getDistanceMetres() != null) {
			vars.put("distance_metres", (int) Math.round(d.getDistanceMetres()));
		}
		if (d.getHeureDeclaree() != null) {
			vars.put("affichage_heure", FORMAT_DATE_HEURE_FR.format(d.getHeureDeclaree()));
		}
		vars.put("affichage_signaux", signaux.isEmpty()
				? "Aucun signal"
				: signaux.stream().map(PresenceWorkflowVariablesBuilder::libelleSignal).collect(Collectors.joining(" · ")));
		return vars;
	}

	private static String libelleSignal(SignalVigilance signal) {
		return switch (signal) {
			case DISTANCE_SUP_2X_RAYON -> "Position > 2× rayon du site";
			case PRECISION_SUP_RAYON -> "Précision GPS > rayon du site";
			case SITE_QR_PAPIER -> "Site à QR papier";
			case MANUELS_MOIS_SEUIL -> "Seuil mensuel atteint";
			case REFUS_FACIAL -> "Refus facial";
			case DEMANDE_TARDIVE -> "Demande tardive";
		};
	}

	public Map<String, Object> pourHeuresSup(
			PresenceHeuresSupDemande d,
			int nbBeneficiaires,
			PresenceSite site) {
		Map<String, Object> vars = new LinkedHashMap<>();
		vars.put("nb_beneficiaires", nbBeneficiaires);
		vars.put("duree_minutes", d.getOuvertureAvantMinutes() + d.getOuvertureApresMinutes());
		vars.put("cree_par_rh", d.isCreeeValidee());
		if (d.getDebutPlageTs() != null) {
			vars.put("debut_fenetre", d.getDebutPlageTs().toString());
		}
		vars.put("affichage_type", "Heures supplémentaires");
		vars.put("affichage_periode", formaterPeriode(d));
		vars.put("affichage_duree", formaterOuverture(d));
		if (site != null && site.getLibelle() != null && !site.getLibelle().isBlank()) {
			vars.put("affichage_site", site.getLibelle());
		}
		vars.put("affichage_beneficiaires", nbBeneficiaires + (nbBeneficiaires <= 1 ? " bénéficiaire" : " bénéficiaires"));
		return vars;
	}

	private static String formaterPeriode(PresenceHeuresSupDemande d) {
		if (d.getDateDebut() == null) {
			return "";
		}
		if (d.getDateFin() == null || d.getDateDebut().equals(d.getDateFin())) {
			return "le " + d.getDateDebut().format(FORMAT_DATE_FR);
		}
		return "du " + d.getDateDebut().format(FORMAT_DATE_FR) + " au " + d.getDateFin().format(FORMAT_DATE_FR);
	}

	private static String formaterOuverture(PresenceHeuresSupDemande d) {
		int avant = d.getOuvertureAvantMinutes();
		int apres = d.getOuvertureApresMinutes();
		if (avant > 0 && apres > 0) {
			return "+" + avant + " min avant / +" + apres + " min après";
		}
		if (avant > 0) {
			return "+" + avant + " min avant";
		}
		return "+" + apres + " min après";
	}
}
