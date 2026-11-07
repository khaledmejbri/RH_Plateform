package com.hr.presence.numero;

import com.hr.presence.config.ClockConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Locale;

/**
 * Numéro lisible {@code PP-AAAAMM-NNNN} (D-L02) : {@code NNNN} = rang dans le mois de création (Africa/Tunis),
 * 4 chiffres, 5 au-delà de 9999. Attribué une seule fois, dans la transaction de la création.
 */
@Service
public class NumeroDemandeService {

	private final CompteurNumero compteur;

	public NumeroDemandeService(CompteurNumero compteur) {
		this.compteur = compteur;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public String attribuer(PrefixeNumero prefixe, Instant creeLe) {
		YearMonth mois = YearMonth.from(creeLe.atZone(ClockConfig.ZONE_TUNIS));
		return formater(prefixe, mois, compteur.suivant(cle(prefixe, mois)));
	}

	static String cle(PrefixeNumero prefixe, YearMonth mois) {
		return String.format(Locale.ROOT, "%s-%d%02d", prefixe.name(), mois.getYear(), mois.getMonthValue());
	}

	static String formater(PrefixeNumero prefixe, YearMonth mois, long rang) {
		return String.format(Locale.ROOT, "%s-%d%02d-%04d", prefixe.name(), mois.getYear(), mois.getMonthValue(), rang);
	}
}
