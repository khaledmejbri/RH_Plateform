package com.hr.workflow.moteur;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Gabarits de textes des notifications du moteur de workflow (D-W27, sans code technique, D-L01).
 */
public final class TextesNotificationWorkflow {

	private static final ZoneId FUSEAU_DEFAUT = ZoneId.of("Africa/Tunis");
	private static final DateTimeFormatter FORMAT_DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private TextesNotificationWorkflow() {
	}

	public static String tacheCreee(String typeLibelle, String demandeur, String etape, Instant echeance, String numero) {
		String echStr = formaterInstant(echeance);
		return "Demande " + typeLibelle + " de " + demandeur + " à traiter : " + etape
				+ (echStr != null ? ". Échéance : " + echStr : "") + "." + suffixeNumero(numero);
	}

	public static String etapeApprouvee(String typeLibelle, String decideur, String etape, String etapeSuivante, String numero) {
		return "Votre demande " + typeLibelle + " a été validée par " + decideur + " (" + etape + "). "
				+ "Prochaine étape : " + etapeSuivante + "." + suffixeNumero(numero);
	}

	public static String approuvee(String typeLibelle, String numero) {
		return "Votre demande " + typeLibelle + " est approuvée." + suffixeNumero(numero);
	}

	public static String refusee(String typeLibelle, String decideur, String motif, String numero) {
		return "Votre demande " + typeLibelle + " est refusée par " + decideur + " : " + motif + "." + suffixeNumero(numero);
	}

	public static String renvoyee(String typeLibelle, String commentaire, String numero) {
		return "Votre demande " + typeLibelle + " vous est renvoyée pour complément : " + commentaire + "." + suffixeNumero(numero);
	}

	public static String annuleeParDemandeur(String typeLibelle, String demandeur, String numero) {
		return "Demande " + typeLibelle + " de " + demandeur + " annulée par le demandeur." + suffixeNumero(numero);
	}

	public static String bloquee(String etape, String typeLibelle, String demandeur, String numero) {
		return "Aucun intervenant pour l'étape " + etape + " de la demande " + typeLibelle
				+ " de " + demandeur + ". À réaffecter." + suffixeNumero(numero);
	}

	public static String erreurEffet(String typeLibelle, String numero) {
		return "Enregistrement définitif impossible pour la demande " + typeLibelle
				+ " après plusieurs tentatives. Intervention administrateur requise." + suffixeNumero(numero);
	}

	public static String information(String etape, String typeLibelle, String demandeur, String numero) {
		return etape + " — Demande " + typeLibelle + " de " + demandeur + "." + suffixeNumero(numero);
	}

	private static String formaterInstant(Instant instant) {
		if (instant == null) {
			return null;
		}
		return FORMAT_DATE_HEURE.format(instant.atZone(FUSEAU_DEFAUT));
	}

	private static String suffixeNumero(String numero) {
		return (numero != null && !numero.isBlank()) ? " [" + numero + "]" : "";
	}
}
