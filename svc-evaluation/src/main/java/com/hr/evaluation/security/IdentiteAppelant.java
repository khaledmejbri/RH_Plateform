package com.hr.evaluation.security;

import com.hr.evaluation.client.MoiReferentiel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Identité de l'appelant lue <b>uniquement dans le JWT</b> (jamais l'en-tête {@code X-Collaborateur-Id}).
 * Le compte vient du claim {@code identifiant_utilisateur} ; la fiche n'est lue au référentiel (une fois, paresseusement)
 * que si un compte de comparaison manque (repli fiche).
 */
public final class IdentiteAppelant {

	private final UUID compteJwt;
	private final String sujet;
	private final boolean ecritureBackoffice;
	private final Supplier<Optional<MoiReferentiel>> fournisseurMoi;

	private boolean moiCharge;
	private Optional<MoiReferentiel> moi = Optional.empty();

	public IdentiteAppelant(UUID compteJwt, String sujet, boolean ecritureBackoffice,
			Supplier<Optional<MoiReferentiel>> fournisseurMoi) {
		this.compteJwt = compteJwt;
		this.sujet = sujet;
		this.ecritureBackoffice = ecritureBackoffice;
		this.fournisseurMoi = fournisseurMoi;
	}

	/** Compte de l'appelant (peut être nul si le jeton n'en porte pas). */
	public UUID compteJwt() {
		return compteJwt;
	}

	/** Compte de l'appelant, ou 403 si le jeton n'en porte pas (traçabilité de l'auteur). */
	public UUID compteJwtObligatoire() {
		if (compteJwt == null) {
			throw new SecurityException("Impossible d'identifier l'acteur courant");
		}
		return compteJwt;
	}

	/** Vrai pour RH et ADMIN (droit d'écriture back-office). */
	public boolean peutEcrire() {
		return ecritureBackoffice;
	}

	/** Fiche de l'appelant au référentiel (paresseux, mémorisé). Peut lever une exception si le référentiel est en panne. */
	public Optional<UUID> ficheJwt() {
		return moi().map(MoiReferentiel::identifiant);
	}

	/** {@code prénom nom} de l'appelant si le référentiel répond, sinon le sujet du jeton. Ne lève jamais. */
	public String libelle() {
		try {
			Optional<String> nom = moi().map(MoiReferentiel::nomComplet);
			if (nom.isPresent()) {
				return nom.get();
			}
		} catch (RuntimeException ignore) {
			// repli sur le sujet
		}
		return sujet != null ? sujet : "Utilisateur inconnu";
	}

	/**
	 * L'appelant est-il la personne décrite par ce snapshot ? Compte connu : comparaison au compte du JWT. Sinon repli :
	 * identifiant de l'ancien format égal au compte du JWT, puis fiche de l'appelant égale à la fiche attendue.
	 */
	public boolean correspond(UUID compteSnapshot, UUID ficheSnapshot) {
		if (compteSnapshot != null) {
			return compteSnapshot.equals(compteJwt);
		}
		if (ficheSnapshot == null) {
			return false;
		}
		if (ficheSnapshot.equals(compteJwt)) {
			return true;
		}
		return ficheJwt().filter(ficheSnapshot::equals).isPresent();
	}

	public boolean estSuperieur(com.hr.evaluation.entity.Evaluation evaluation) {
		return correspond(evaluation.getSuperieurCompteId(), evaluation.getSuperieurIdentifiant());
	}

	public boolean estCollaborateur(com.hr.evaluation.entity.Evaluation evaluation) {
		return correspond(evaluation.getCollaborateurCompteId(), evaluation.getCollaborateurIdentifiant());
	}

	private Optional<MoiReferentiel> moi() {
		if (!moiCharge) {
			moi = fournisseurMoi.get();
			moiCharge = true;
		}
		return moi;
	}
}
