package com.hr.presence.service;

import com.hr.presence.entity.PresenceHeuresSupBeneficiaire;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.kafka.ObjetNotification;
import com.hr.presence.kafka.PresenceEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Notifications des heures supplémentaires (D-P15), adressées au compte avec repli sur la fiche. L'envoi attend le
 * commit ({@link PresenceEventPublisher#notifier}). Le corps ne contient ni matricule ni motif de refus pour les
 * bénéficiaires.
 */
@Component
public class HeuresSupNotifier {

	private static final java.time.format.DateTimeFormatter DATE =
			java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy", java.util.Locale.FRENCH);

	private final PresenceEventPublisher eventPublisher;

	public HeuresSupNotifier(PresenceEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	/** Résumé commun des messages : site, dates et fenêtre élargie du premier jour (ex. 03:00–16:00, dont heures sup 04:00–06:00). */
	public static String resume(String siteLibelle, java.time.LocalDate debut, java.time.LocalDate fin, String fenetre) {
		String dates = debut.equals(fin) ? "le " + DATE.format(debut) : "du " + DATE.format(debut) + " au " + DATE.format(fin);
		return "site " + siteLibelle + ", " + dates + (fenetre != null ? ", pointage ouvert " + fenetre : "");
	}

	/**
	 * Fenêtre d'ouverture élargie et heures sup incluses : « 03:00–16:00, dont heures sup 04:00–06:00 »
	 * (et / ou « 15:00–16:00 » après l'horaire). La tolérance n'est jamais une heure sup.
	 */
	public static String fenetreEtHeuresSup(FenetreHoraireService.Fenetre f, int avantMinutes, int apresMinutes) {
		java.time.format.DateTimeFormatter h = FenetreHoraireService.HEURE_MINUTE;
		StringBuilder sb = new StringBuilder(h.format(f.ouvertureDebut())).append("–").append(h.format(f.ouvertureFin()));
		List<String> plages = new java.util.ArrayList<>();
		if (avantMinutes > 0) {
			plages.add(h.format(f.horaireDebut().minusMinutes(avantMinutes)) + "–" + h.format(f.horaireDebut()));
		}
		if (apresMinutes > 0) {
			plages.add(h.format(f.horaireFin()) + "–" + h.format(f.horaireFin().plusMinutes(apresMinutes)));
		}
		if (!plages.isEmpty()) {
			sb.append(", dont heures sup ").append(String.join(" et ", plages));
		}
		return sb.toString();
	}

	public static String date(java.time.LocalDate jour) {
		return DATE.format(jour);
	}

	/** Demande créée EN_ATTENTE : tous les comptes RH. */
	public void creeeEnAttente(PresenceHeuresSupDemande d, String resume) {
		ObjetNotification objet = objet(d, "HEURES_SUP_CREEE");
		eventPublisher.notifier(PresenceEventPublisher.DESTINATAIRE_RH,
				"Heures supplémentaires à valider — " + d.getReference(),
				nomDemandeur(d) + " demande des heures supplémentaires (" + resume + ") : " + d.getMotif(), objet);
	}

	/** Demande validée : demandeur et bénéficiaires. */
	public void validee(PresenceHeuresSupDemande d, String resume, List<PresenceHeuresSupBeneficiaire> beneficiaires) {
		ObjetNotification objet = objet(d, "HEURES_SUP_VALIDEE");
		eventPublisher.notifier(adresse(d.getDemandeurCompteId(), d.getDemandeurFicheId()),
				"Heures supplémentaires validées — " + d.getReference(),
				"Votre demande d'heures supplémentaires (" + resume + ") a été validée.", objet);
		notifierBeneficiaires(beneficiaires, "Heures supplémentaires validées — " + d.getReference(),
				"Des heures supplémentaires vous sont accordées (" + resume + ").", objet(d, "HEURES_SUP_VALIDEE"));
	}

	/** Création directe validée par RH / ADMIN : bénéficiaires seulement (ni l'auteur, ni RH). */
	public void creeeValidee(PresenceHeuresSupDemande d, String resume, List<PresenceHeuresSupBeneficiaire> beneficiaires) {
		ObjetNotification objet = objet(d, "HEURES_SUP_ACCORDEES");
		notifierBeneficiaires(beneficiaires, "Heures supplémentaires validées — " + d.getReference(),
				"Des heures supplémentaires vous sont accordées (" + resume + ").", objet);
	}

	/** Demande refusée : demandeur seul, avec le motif. */
	public void refusee(PresenceHeuresSupDemande d, String resume) {
		ObjetNotification objet = objet(d, "HEURES_SUP_REFUSEE");
		eventPublisher.notifier(adresse(d.getDemandeurCompteId(), d.getDemandeurFicheId()),
				"Heures supplémentaires refusées — " + d.getReference(),
				"Votre demande d'heures supplémentaires (" + resume + ") a été refusée. Motif : " + d.getMotifRefus(), objet);
	}

	/** Annulation par RH / ADMIN : demandeur et bénéficiaires. */
	public void annulee(PresenceHeuresSupDemande d, String resume, List<PresenceHeuresSupBeneficiaire> beneficiaires) {
		ObjetNotification objet = objet(d, "HEURES_SUP_ANNULEE");
		eventPublisher.notifier(adresse(d.getDemandeurCompteId(), d.getDemandeurFicheId()),
				"Heures supplémentaires annulées — " + d.getReference(),
				"La demande d'heures supplémentaires (" + resume + ") a été annulée par " + d.getAnnuleParNom() + ".", objet);
		notifierBeneficiaires(beneficiaires, "Heures supplémentaires annulées — " + d.getReference(),
				"Les heures supplémentaires prévues (" + resume + ") sont annulées.", objet(d, "HEURES_SUP_ANNULEE"));
	}

	/** Demande expirée : demandeur seul. */
	public void expiree(PresenceHeuresSupDemande d, String resume) {
		ObjetNotification objet = objet(d, "HEURES_SUP_EXPIREE");
		eventPublisher.notifier(adresse(d.getDemandeurCompteId(), d.getDemandeurFicheId()),
				"Heures supplémentaires expirées — " + d.getReference(),
				"Votre demande d'heures supplémentaires (" + resume + ") n'a pas été traitée avant l'ouverture"
						+ " de la fenêtre de pointage : elle a expiré.", objet);
	}

	/**
	 * Jours retirés par une modification d'horaire du site (annulation totale ou partielle) : demandeur, et
	 * bénéficiaires si la demande était validée.
	 */
	public void joursRetires(PresenceHeuresSupDemande d, String resume, String jours, boolean totale,
							 boolean etaitValidee, List<PresenceHeuresSupBeneficiaire> beneficiaires) {
		ObjetNotification objet = objet(d, "HEURES_SUP_JOURS_RETIRES");
		String sujet = (totale ? "Heures supplémentaires annulées — " : "Heures supplémentaires modifiées — ")
				+ d.getReference();
		String corps = "L'horaire du site a été modifié : " + (totale ? "la demande" : "les jours " + jours + " de la demande")
				+ " d'heures supplémentaires (" + resume + ")"
				+ (totale ? " est annulée (jours " + jours + " fermés)." : " sont retirés.");
		eventPublisher.notifier(adresse(d.getDemandeurCompteId(), d.getDemandeurFicheId()), sujet, corps, objet);
		if (etaitValidee) {
			notifierBeneficiaires(beneficiaires, sujet, corps, objet);
		}
	}

	/** Un seul objet (donc un seul notification_id) par événement, partagé par tous ses destinataires. */
	private static ObjetNotification objet(PresenceHeuresSupDemande d, String evenement) {
		return ObjetNotification.de(ObjetNotification.HEURES_SUP, evenement, d.getId(), d.getReference());
	}

	private void notifierBeneficiaires(List<PresenceHeuresSupBeneficiaire> beneficiaires, String sujet, String corps,
									   ObjetNotification objet) {
		for (PresenceHeuresSupBeneficiaire b : beneficiaires) {
			eventPublisher.notifier(adresse(texte(b.getCompteId()), texte(b.getCollaborateurId())), sujet, corps, objet);
		}
	}

	/** Compte (claim STOMP), repli sur la fiche — cf. SCRUM-27. */
	static String adresse(String compteId, String ficheId) {
		return compteId != null ? compteId : ficheId;
	}

	private static String nomDemandeur(PresenceHeuresSupDemande d) {
		return d.getDemandeurNom() != null ? d.getDemandeurNom() : "Un responsable";
	}

	private static String texte(UUID id) {
		return id != null ? id.toString() : null;
	}
}
