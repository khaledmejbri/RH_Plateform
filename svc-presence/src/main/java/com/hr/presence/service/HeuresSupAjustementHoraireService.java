package com.hr.presence.service;

import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.entity.PresenceHeuresSupBeneficiaire;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresenceHeuresSupJour;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceHeuresSupBeneficiaireRepository;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.security.ActeurResolver.Acteur;
import com.hr.presence.service.FenetreHoraireService.Fenetre;
import com.hr.presence.service.FenetreHoraireService.Horaire;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Effet d'un changement d'horaire sur les demandes d'heures sup (décision client n° 2), dans la transaction de la
 * modification : jours devenus fermés retirés tant que leur fenêtre n'a pas ouvert, demande annulée s'il ne reste
 * aucun jour, {@code debut_plage_ts} recalculé. Un simple changement d'heures ne touche pas les demandes.
 */
@Service
public class HeuresSupAjustementHoraireService {

	static final String MOTIF_HORAIRE_MODIFIE = "Horaire du site modifié";

	private final PresenceHeuresSupDemandeRepository demandeRepository;
	private final PresenceHeuresSupJourRepository jourRepository;
	private final PresenceHeuresSupBeneficiaireRepository beneficiaireRepository;
	private final HeuresSupNotifier notifier;
	private final Clock clock;

	public HeuresSupAjustementHoraireService(
			PresenceHeuresSupDemandeRepository demandeRepository,
			PresenceHeuresSupJourRepository jourRepository,
			PresenceHeuresSupBeneficiaireRepository beneficiaireRepository,
			HeuresSupNotifier notifier,
			Clock clock) {
		this.demandeRepository = demandeRepository;
		this.jourRepository = jourRepository;
		this.beneficiaireRepository = beneficiaireRepository;
		this.notifier = notifier;
		this.clock = clock;
	}

	/**
	 * @param anciens          horaires avant la modification
	 * @param toleranceAvantAncienne tolérance avant avant la modification
	 * @param nouveaux         horaires après la modification (vide = site sans horaire)
	 */
	@Transactional
	public void appliquer(PresenceSite site, Map<DayOfWeek, Horaire> anciens, int toleranceAvantAncienne,
						  Map<DayOfWeek, Horaire> nouveaux, Acteur auteur) {
		Instant maintenant = clock.instant();
		LocalDate depuis = maintenant.atZone(ClockConfig.ZONE_TUNIS).toLocalDate().minusDays(1);
		List<PresenceHeuresSupJour> proches = jourRepository.findBySiteIdAndJourneeGreaterThanEqual(site.getId(), depuis);
		List<UUID> demandeIds = proches.stream().map(PresenceHeuresSupJour::getDemandeId).distinct().toList();
		if (demandeIds.isEmpty()) {
			return;
		}
		for (PresenceHeuresSupDemande d : demandeRepository.findAllById(demandeIds)) {
			if (!d.getStatut().actif()) {
				continue;
			}
			ajusterDemande(site, d, anciens, toleranceAvantAncienne, nouveaux, auteur, maintenant);
		}
	}

	private void ajusterDemande(PresenceSite site, PresenceHeuresSupDemande d, Map<DayOfWeek, Horaire> anciens,
								int toleranceAvantAncienne, Map<DayOfWeek, Horaire> nouveaux, Acteur auteur,
								Instant maintenant) {
		List<PresenceHeuresSupJour> lignes = jourRepository.findByDemandeId(d.getId());
		List<PresenceHeuresSupJour> retirees = new ArrayList<>();
		for (PresenceHeuresSupJour l : lignes) {
			DayOfWeek jourSemaine = l.getJournee().getDayOfWeek();
			if (nouveaux.containsKey(jourSemaine)) {
				continue;
			}
			// Jour fermé (ou site sans horaire) : retiré seulement si sa fenêtre n'a pas encore ouvert (D-P11.c).
			// Sans ancien horaire pour ce jour de semaine (déjà fermé par une modification antérieure), la ligne est une
			// journée passée conservée : on la garde, ses heures sont déjà comptées.
			Horaire ancien = anciens.get(jourSemaine);
			if (ancien == null || fenetreDejaOuverte(l, d, ancien, toleranceAvantAncienne, maintenant)) {
				continue;
			}
			retirees.add(l);
		}
		List<PresenceHeuresSupJour> restantes = new ArrayList<>(lignes);
		restantes.removeAll(retirees);

		Instant nouveauDebut = premierDebutPlage(site, d, restantes, anciens, toleranceAvantAncienne, nouveaux);
		if (d.getDebutPlageTs() != null && !d.getDebutPlageTs().isAfter(maintenant)) {
			// Une fenetre deja ouverte ne se referme pas : un changement d'heures du jour meme ne repousse jamais
			// un debut de plage passe (sinon une annulation RH ou une validation redeviendrait possible).
			nouveauDebut = null;
		}
		boolean debutChange = nouveauDebut != null && !nouveauDebut.equals(d.getDebutPlageTs());
		if (retirees.isEmpty()) {
			if (debutChange) {
				d.setDebutPlageTs(nouveauDebut);
				demandeRepository.save(d);
			}
			return;
		}

		jourRepository.deleteAll(retirees);
		boolean etaitValidee = d.getStatut() == StatutHeuresSup.VALIDEE;
		boolean totale = restantes.isEmpty();
		if (totale) {
			d.setStatut(StatutHeuresSup.ANNULEE);
			d.setAnnuleParCompteId(auteur.compteId());
			d.setAnnuleParNom(auteur.libelle());
			d.setAnnuleLe(maintenant);
			d.setMotifAnnulation(MOTIF_HORAIRE_MODIFIE);
		} else if (debutChange) {
			d.setDebutPlageTs(nouveauDebut);
		}
		demandeRepository.save(d);

		List<PresenceHeuresSupBeneficiaire> beneficiaires = beneficiaireRepository.findByDemandeId(d.getId());
		String jours = retirees.stream().map(PresenceHeuresSupJour::getJournee).collect(Collectors.toCollection(TreeSet::new))
				.stream().map(HeuresSupNotifier::date).collect(Collectors.joining(", "));
		String resume = HeuresSupNotifier.resume(site.getLibelle(), d.getDateDebut(), d.getDateFin(), null);
		notifier.joursRetires(d, resume, jours, totale, etaitValidee, beneficiaires);
	}

	private static boolean fenetreDejaOuverte(PresenceHeuresSupJour ligne, PresenceHeuresSupDemande d, Horaire ancien,
											  int toleranceAvantAncienne, Instant maintenant) {
		return !FenetreHoraireService.fenetre(ligne.getJournee(), ancien, toleranceAvantAncienne, 0,
				d.getOuvertureAvantMinutes(), 0).ouvertureDebut().toInstant().isAfter(maintenant);
	}

	/**
	 * Ouverture de la fenêtre du premier jour restant dont l'horaire est connu : le nouvel horaire, ou l'ancien pour une
	 * journée fermée depuis mais conservée. Une ligne sans horaire connu est ignorée ; sans aucune, {@code null}
	 * (le {@code debut_plage_ts} existant est conservé).
	 */
	private static Instant premierDebutPlage(PresenceSite site, PresenceHeuresSupDemande d,
											 List<PresenceHeuresSupJour> restantes, Map<DayOfWeek, Horaire> anciens,
											 int toleranceAvantAncienne, Map<DayOfWeek, Horaire> nouveaux) {
		return restantes.stream()
				.map(PresenceHeuresSupJour::getJournee)
				.distinct()
				.sorted(Comparator.naturalOrder())
				.map(jour -> {
					Horaire nouveau = nouveaux.get(jour.getDayOfWeek());
					if (nouveau != null) {
						return FenetreHoraireService.fenetre(jour, nouveau, site.getToleranceAvantMinutes(),
								site.getToleranceApresMinutes(), d.getOuvertureAvantMinutes(), d.getOuvertureApresMinutes());
					}
					Horaire ancien = anciens.get(jour.getDayOfWeek());
					return ancien == null ? null : FenetreHoraireService.fenetre(jour, ancien, toleranceAvantAncienne, 0,
							d.getOuvertureAvantMinutes(), d.getOuvertureApresMinutes());
				})
				.filter(java.util.Objects::nonNull)
				.findFirst()
				.map(f -> f.ouvertureDebut().toInstant())
				.orElse(null);
	}
}
