package com.hr.referentiel.importation;

/**
 * Protection contre l'injection de formule dans les fichiers produits (tableur) : toute cellule texte commençant par
 * {@code =}, {@code +}, {@code -}, {@code @}, une tabulation ou un retour chariot est préfixée d'une apostrophe.
 * Les nombres calculés par le serveur ne passent pas par ici.
 */
public final class EchappementFormule {

	private EchappementFormule() {
	}

	public static String proteger(String texte) {
		if (texte == null || texte.isEmpty()) {
			return texte;
		}
		char c = texte.charAt(0);
		return (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') ? "'" + texte : texte;
	}
}
