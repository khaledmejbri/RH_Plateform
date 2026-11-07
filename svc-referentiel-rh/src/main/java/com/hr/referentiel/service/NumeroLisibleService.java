package com.hr.referentiel.service;

import com.hr.referentiel.repository.NumeroSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * D-L02 : attribue le numéro lisible {@code PPP-AAAAMM-NNNN} (rang dans le mois de création, fuseau Africa/Tunis).
 *
 * <p>Mécanisme anti-doublon : un compteur par préfixe et par mois, incrémenté par un upsert atomique
 * PostgreSQL ({@code INSERT … ON CONFLICT DO UPDATE … RETURNING}). Le verrou de ligne pris par l'upsert est
 * tenu jusqu'au commit de la transaction qui crée la demande : deux créations simultanées reçoivent deux
 * rangs différents, et une création annulée ne laisse pas de trou. La contrainte d'unicité de la colonne
 * {@code numero} reste le filet de sécurité.</p>
 */
@Service
public class NumeroLisibleService {

	public static final ZoneId FUSEAU_TUNIS = ZoneId.of("Africa/Tunis");
	private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("yyyyMM");

	private final NumeroSequenceRepository sequenceRepository;

	public NumeroLisibleService(NumeroSequenceRepository sequenceRepository) {
		this.sequenceRepository = sequenceRepository;
	}

	/** À appeler dans la transaction qui enregistre l'objet, avec son instant de création. */
	@Transactional(propagation = Propagation.MANDATORY)
	public String attribuer(PrefixeNumero prefixe, Instant creation) {
		return attribuerAvec(sequenceRepository, prefixe, creation);
	}

	/** Partagé avec la reprise des anciennes lignes, qui gère elle-même sa transaction. */
	public static String attribuerAvec(NumeroSequenceRepository repository, PrefixeNumero prefixe, Instant creation) {
		String mois = MOIS.format(creation.atZone(FUSEAU_TUNIS));
		int rang = repository.incrementer(prefixe.name() + "-" + mois);
		return formater(prefixe, mois, rang);
	}

	/**
	 * Critère de recherche {@code q} des listes RH : numéro exact, insensible à la casse et aux espaces autour.
	 * Renvoie {@code null} si aucun critère n'est saisi.
	 */
	public static String normaliserRecherche(String q) {
		if (q == null || q.isBlank()) {
			return null;
		}
		return q.trim().toUpperCase(Locale.ROOT);
	}

	/** {@code NNNN} sur 4 chiffres, 5 au-delà de 9999. */
	static String formater(PrefixeNumero prefixe, String mois, int rang) {
		return String.format(Locale.ROOT, "%s-%s-%04d", prefixe.name(), mois, rang);
	}
}
