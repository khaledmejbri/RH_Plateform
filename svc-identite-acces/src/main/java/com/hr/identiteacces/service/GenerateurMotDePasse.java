package com.hr.identiteacces.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mot de passe initial d'un compte créé par import (D-I09, ADR § 7) : 16 caractères tirés avec {@link SecureRandom}, au
 * moins une minuscule, une majuscule, un chiffre et un symbole, sans caractères ambigus (0/O, 1/l/I). Le mot de passe est
 * transmis seulement au courriel de bienvenue : il n'est jamais journalisé ni renvoyé par une API.
 */
@Component
public class GenerateurMotDePasse {

	static final int LONGUEUR = 16;
	private static final String MINUSCULES = "abcdefghijkmnopqrstuvwxyz";
	private static final String MAJUSCULES = "ABCDEFGHJKLMNPQRSTUVWXYZ";
	private static final String CHIFFRES = "23456789";
	private static final String SYMBOLES = "!#$%&*+-=?@";
	private static final String TOUS = MINUSCULES + MAJUSCULES + CHIFFRES + SYMBOLES;

	private final SecureRandom hasard = new SecureRandom();

	public String generer() {
		List<Character> c = new ArrayList<>(LONGUEUR);
		c.add(tirer(MINUSCULES));
		c.add(tirer(MAJUSCULES));
		c.add(tirer(CHIFFRES));
		c.add(tirer(SYMBOLES));
		while (c.size() < LONGUEUR) {
			c.add(tirer(TOUS));
		}
		Collections.shuffle(c, hasard);
		StringBuilder sb = new StringBuilder(LONGUEUR);
		c.forEach(sb::append);
		return sb.toString();
	}

	private char tirer(String alphabet) {
		return alphabet.charAt(hasard.nextInt(alphabet.length()));
	}
}
