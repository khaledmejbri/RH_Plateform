package com.hr.identiteacces.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

/**
 * Propriétés multi-société de {@code svc-identite-acces} (D-SOC01 à D-SOC03.e, amélioration 11).
 */
@ConfigurationProperties(prefix = "app.societe")
public class SocieteProprietes {

	public static final String CODE_DEFAUT_INITIAL = "PRINCIPALE";
	public static final String RAISON_SOCIALE_DEFAUT_INITIALE = "Société principale";
	public static final String FUSEAU_DEFAUT = "Africa/Tunis";

	private final Defaut defaut = new Defaut();
	private boolean repliDefautTransition = true;

	public Defaut getDefaut() {
		return defaut;
	}

	public boolean isRepliDefautTransition() {
		return repliDefautTransition;
	}

	public void setRepliDefautTransition(boolean repliDefautTransition) {
		this.repliDefautTransition = repliDefautTransition;
	}

	public static class Defaut {
		private String code = CODE_DEFAUT_INITIAL;
		private String raisonSociale = RAISON_SOCIALE_DEFAUT_INITIALE;

		public String getCode() {
			return (code == null || code.isBlank())
					? CODE_DEFAUT_INITIAL
					: code.trim().toUpperCase(Locale.ROOT);
		}

		public void setCode(String code) {
			this.code = code;
		}

		public String getRaisonSociale() {
			return (raisonSociale == null || raisonSociale.isBlank())
					? RAISON_SOCIALE_DEFAUT_INITIALE
					: raisonSociale.trim();
		}

		public void setRaisonSociale(String raisonSociale) {
			this.raisonSociale = raisonSociale;
		}
	}
}
