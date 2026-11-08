package com.hr.presence.service;

import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.EtatJourHeuresSup;
import com.hr.presence.dto.TempsTravailDtos.LigneTempsTravail;
import com.hr.presence.dto.TempsTravailDtos.PaireDto;
import com.hr.presence.service.TempsTravailService.LigneDetaillee;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Export CSV du temps de travail (D-P17.i), même format de fichier que l'export des heures sup (D-P16.a) : UTF-8
 * avec BOM, séparateur « ; », cellules entre guillemets, neutralisation de l'injection de formule.
 */
@Service
public class TempsTravailCsvService {

	private static final String[] ENTETES = {"Matricule", "Nom", "Direction", "Département", "Service", "Site", "Date",
			"Entrées / sorties", "Nombre d'entrées / sorties", "Première entrée", "Dernière sortie", "Total (hh:mm)", "Total (minutes)",
			"État", "Pointage manuel", "Retard (minutes)", "Retard (hh:mm)"};

	private final TempsTravailService tempsTravailService;

	public TempsTravailCsvService(TempsTravailService tempsTravailService) {
		this.tempsTravailService = tempsTravailService;
	}

	public byte[] exporter(LocalDate du, LocalDate au, UUID collaborateurId, UUID siteId, UUID uniteId,
						   EtatJourHeuresSup etat) {
		return generer(tempsTravailService.exporter(du, au, collaborateurId, siteId, uniteId, etat));
	}

	static byte[] generer(List<LigneDetaillee> lignes) {
		StringBuilder sb = new StringBuilder(HeuresSupCsvService.BOM);
		sb.append(HeuresSupCsvService.ligne(ENTETES)).append("\r\n");
		for (LigneDetaillee d : lignes) {
			LigneTempsTravail l = d.ligne();
			sb.append(HeuresSupCsvService.ligne(
					l.collaborateurMatricule(), l.collaborateurNom(), d.direction(), d.departement(), d.service(),
					l.siteLibelle(), HeuresSupCsvService.DATE.format(l.jour()),
					l.paires().stream().map(pa -> paire(pa, l.jour())).collect(Collectors.joining(" | ")),
					String.valueOf(l.paires().size()),
					heure(l.premiereEntree(), l.jour()), heure(l.derniereSortie(), l.jour()),
					CalculHeuresSupService.formaterHeures(l.minutesTravaillees()),
					String.valueOf(l.minutesTravaillees()),
					l.etat().libelle(),
					d.manuel() ? "oui" : "non",
					// D-P19.g : vide quand le retard n'est pas calculable, 0 et 00:00 quand le pointage est à l'heure
					l.retardMinutes() == null ? null : String.valueOf(l.retardMinutes()),
					l.retardMinutes() == null ? null : CalculHeuresSupService.formaterHeures(l.retardMinutes()))).append("\r\n");
		}
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String paire(PaireDto p, LocalDate jour) {
		return heure(p.entree(), jour) + "-" + heure(p.sortie(), jour);
	}

	/** HH:mm Africa/Tunis ; suffixe « (J+1) » quand l'heure tombe un autre jour que la journée de travail. */
	private static String heure(String iso, LocalDate jour) {
		if (iso == null) {
			return null;
		}
		OffsetDateTime t = OffsetDateTime.parse(iso).atZoneSameInstant(ClockConfig.ZONE_TUNIS).toOffsetDateTime();
		String texte = FenetreHoraireService.HEURE_MINUTE.format(t);
		return jour != null && t.toLocalDate().isAfter(jour) ? texte + " (J+" + java.time.temporal.ChronoUnit.DAYS.between(jour, t.toLocalDate()) + ")" : texte;
	}
}
