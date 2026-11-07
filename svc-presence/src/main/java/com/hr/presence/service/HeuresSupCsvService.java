package com.hr.presence.service;

import com.hr.presence.config.ClockConfig;
import com.hr.presence.dto.HeuresSupDtos.HeuresComptees;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Export CSV des heures comptées (D-P16.a) : UTF-8 avec BOM, séparateur « ; », cellules entre guillemets, et
 * neutralisation de l'injection de formule (cellule commençant par = + - @ tabulation ou retour chariot).
 */
@Service
public class HeuresSupCsvService {

	static final char SEPARATEUR = ';';
	/** Date française jj/mm/aaaa (D-L01.c). */
	static final java.time.format.DateTimeFormatter DATE = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
	static final String BOM = "﻿";
	private static final String[] ENTETES = {"Matricule", "Nom", "Site", "Date", "Horaire du jour", "Plage validée",
			"Entrée", "Sortie", "Heures comptées (hh:mm)", "Référence de la demande", "État"};

	private final HeuresSupService heuresSupService;

	public HeuresSupCsvService(HeuresSupService heuresSupService) {
		this.heuresSupService = heuresSupService;
	}

	public byte[] exporter(LocalDate du, LocalDate au, UUID siteId, UUID collaborateurId, UUID directionId) {
		return generer(heuresSupService.comptees(du, au, siteId, collaborateurId, directionId));
	}

	static byte[] generer(List<HeuresComptees> lignes) {
		StringBuilder sb = new StringBuilder(BOM);
		sb.append(ligne(ENTETES)).append("\r\n");
		for (HeuresComptees l : lignes) {
			sb.append(ligne(
					l.matricule(), l.nom(), l.siteLibelle(), l.journee() != null ? DATE.format(l.journee()) : null,
					l.horaireDebut() != null ? l.horaireDebut() + "-" + l.horaireFin() : null,
					plages(l), heure(l.entree()), heure(l.sortie()), l.heuresComptees(), l.referenceDemande(),
					l.etat() != null ? l.etat().libelle() : null)).append("\r\n");
		}
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String plages(HeuresComptees l) {
		if (l.plageAvant() != null && l.plageApres() != null) {
			return l.plageAvant() + " + " + l.plageApres();
		}
		return l.plageAvant() != null ? l.plageAvant() : l.plageApres();
	}

	private static String heure(Instant instant) {
		return instant == null ? null
				: FenetreHoraireService.HEURE_MINUTE.format(ZonedDateTime.ofInstant(instant, ClockConfig.ZONE_TUNIS));
	}

	static String ligne(String... cellules) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < cellules.length; i++) {
			if (i > 0) {
				sb.append(SEPARATEUR);
			}
			sb.append(cellule(cellules[i]));
		}
		return sb.toString();
	}

	/** Cellule entre guillemets (doublés), préfixée par une apostrophe si elle ressemble à une formule. */
	static String cellule(String valeur) {
		String texte = valeur == null ? "" : valeur;
		if (!texte.isEmpty() && "=+-@\t\r".indexOf(texte.charAt(0)) >= 0) {
			texte = "'" + texte;
		}
		return "\"" + texte.replace("\"", "\"\"") + "\"";
	}
}
