package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.CollaborateurResolu;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.dto.HeuresSupDtos.CreationHeuresSupRequest;
import com.hr.presence.dto.HeuresSupDtos.DemandeHeuresSupResponse;
import com.hr.presence.entity.PresenceHeuresSupBeneficiaire;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresenceHeuresSupJour;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceHeuresSupBeneficiaireRepository;
import com.hr.presence.repository.PresenceHeuresSupDemandeRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.ActeurResolver;
import com.hr.presence.security.ActeurResolver.Acteur;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.service.FenetreHoraireService.Fenetre;
import com.hr.presence.service.FenetreHoraireService.Horaire;
import com.hr.presence.web.ContraintesUniques;
import com.hr.presence.web.PresenceMetierException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Création d'une demande d'heures supplémentaires (D-P13) : par un RO / chef de département (EN_ATTENTE, périmètre
 * D-H03) ou directement validée par RH / ADMIN (D-P13.c). Identité du demandeur lue dans le JWT uniquement.
 */
@Service
public class HeuresSupCreationService {

	private static final Logger log = LoggerFactory.getLogger(HeuresSupCreationService.class);

	static final int OUVERTURE_MIN_MINUTES = 30;
	static final int OUVERTURE_MAX_MINUTES = 240;
	static final int OUVERTURE_PAS_MINUTES = 30;
	static final int PLAGE_MAX_JOURS = 31;
	static final int MOTIF_MAX_CARACTERES = 500;

	private final PresenceHeuresSupDemandeRepository demandeRepository;
	private final PresenceHeuresSupBeneficiaireRepository beneficiaireRepository;
	private final PresenceHeuresSupJourRepository jourRepository;
	private final PresenceSiteRepository siteRepository;
	private final FenetreHoraireService fenetreService;
	private final ReferentielClient referentielClient;
	private final PresenceAccessService accessService;
	private final ActeurResolver acteurResolver;
	private final HeuresSupNotifier notifier;
	private final HeuresSupReponseMapper mapper;
	private final Clock clock;

	public HeuresSupCreationService(
			PresenceHeuresSupDemandeRepository demandeRepository,
			PresenceHeuresSupBeneficiaireRepository beneficiaireRepository,
			PresenceHeuresSupJourRepository jourRepository,
			PresenceSiteRepository siteRepository,
			FenetreHoraireService fenetreService,
			ReferentielClient referentielClient,
			PresenceAccessService accessService,
			ActeurResolver acteurResolver,
			HeuresSupNotifier notifier,
			HeuresSupReponseMapper mapper,
			Clock clock) {
		this.demandeRepository = demandeRepository;
		this.beneficiaireRepository = beneficiaireRepository;
		this.jourRepository = jourRepository;
		this.siteRepository = siteRepository;
		this.fenetreService = fenetreService;
		this.referentielClient = referentielClient;
		this.accessService = accessService;
		this.acteurResolver = acteurResolver;
		this.notifier = notifier;
		this.mapper = mapper;
		this.clock = clock;
	}

	/** Demande d'un RO / chef de département : EN_ATTENTE, bénéficiaires dans son périmètre D-H03. */
	@Transactional
	public DemandeHeuresSupResponse creerParDemandeur(CreationHeuresSupRequest request) {
		return creer(request, false);
	}

	/** Création directe par RH / ADMIN : VALIDEE d'emblée, tracée, sans contrôle de périmètre (D-P13.c). */
	@Transactional
	public DemandeHeuresSupResponse creerDirecte(CreationHeuresSupRequest request) {
		return creer(request, true);
	}

	private DemandeHeuresSupResponse creer(CreationHeuresSupRequest r, boolean directe) {
		// 1. Contrôles d'entrée, dans l'ordre du contrat.
		String motif = r.motif() == null ? "" : r.motif().trim();
		if (motif.isEmpty()) {
			throw PresenceMetierException.badRequest("MOTIF_OBLIGATOIRE", "Le motif est obligatoire");
		}
		if (motif.length() > MOTIF_MAX_CARACTERES) {
			throw PresenceMetierException.badRequest("MOTIF_TROP_LONG",
					"Le motif ne peut pas dépasser " + MOTIF_MAX_CARACTERES + " caractères");
		}
		int avant = ouverture(r.ouvertureAvantMinutes());
		int apres = ouverture(r.ouvertureApresMinutes());
		if (avant == 0 && apres == 0) {
			throw PresenceMetierException.badRequest("OUVERTURE_ABSENTE",
					"Renseignez une ouverture avant et / ou après l'horaire");
		}
		LocalDate debut = r.dateDebut();
		LocalDate fin = r.dateFin() != null ? r.dateFin() : debut;
		if (ChronoUnit.DAYS.between(debut, fin) + 1 > PLAGE_MAX_JOURS) {
			throw PresenceMetierException.badRequest("PLAGE_TROP_LONGUE",
					"La plage ne peut pas dépasser " + PLAGE_MAX_JOURS + " jours");
		}
		if (fin.isBefore(debut)) {
			throw PresenceMetierException.badRequest("PERIODE_INVALIDE", "La date de fin précède la date de début");
		}

		// 2. Site, horaire, jours ouverts, plage pas encore commencée.
		PresenceSite site = siteRepository.findById(r.siteId())
				.orElseThrow(() -> PresenceMetierException.notFound("SITE_INTROUVABLE", "Site introuvable"));
		Map<DayOfWeek, Horaire> horaires = fenetreService.horaires(site.getId());
		if (horaires.isEmpty()) {
			throw PresenceMetierException.unprocessable("SITE_SANS_HORAIRE",
					"Ce site n'a pas d'horaire : les heures supplémentaires n'ont pas de sens");
		}
		List<LocalDate> joursOuverts = debut.datesUntil(fin.plusDays(1))
				.filter(j -> horaires.containsKey(j.getDayOfWeek())).toList();
		if (joursOuverts.isEmpty()) {
			throw PresenceMetierException.unprocessable("AUCUN_JOUR_OUVERT", "Tous les jours de la plage sont fermés");
		}
		Fenetre premiere = fenetre(site, horaires, joursOuverts.get(0), avant, apres);
		Instant maintenant = clock.instant();
		if (!premiere.ouvertureDebut().toInstant().isAfter(maintenant)) {
			throw PresenceMetierException.unprocessable("PLAGE_DEJA_COMMENCEE",
					"La fenêtre de pointage du premier jour ouvert est déjà ouverte");
		}

		// 3. Bénéficiaires : résolus au référentiel, actifs, dédoublonnés.
		List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(r.collaborateurIds()));
		Map<UUID, CollaborateurResolu> resolus = referentielClient.resoudreCollaborateurs(ids).stream()
				.collect(Collectors.toMap(CollaborateurResolu::collaborateurId, Function.identity(), (a, b) -> a));
		for (UUID id : ids) {
			CollaborateurResolu c = resolus.get(id);
			if (c == null || !c.actif()) {
				throw PresenceMetierException.unprocessable("COLLABORATEUR_INVALIDE",
						"Collaborateur introuvable ou inactif : " + id);
			}
		}

		// 4. Demandeur (JWT) et périmètre.
		Acteur demandeur = directe ? acteurResolver.avecFiche() : demandeurRo();
		if (directe) {
			interdireAutoValidation(demandeur, ids, resolus);
		} else {
			interdirePerimetre(demandeur, ids, resolus);
		}

		// 5. Chevauchements : fenêtres élargies des jours voisins, puis doublon (collaborateur, site, journée).
		List<String> fiches = ids.stream().map(UUID::toString).toList();
		List<PresenceHeuresSupJour> existantes = jourRepository.findBySiteIdAndJourneeBetweenAndCollaborateurIdIn(
				site.getId(), debut.minusDays(1), fin.plusDays(1), fiches);
		Map<UUID, PresenceHeuresSupDemande> demandesExistantes = demandeRepository.findAllById(
						existantes.stream().map(PresenceHeuresSupJour::getDemandeId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(PresenceHeuresSupDemande::getId, Function.identity()));
		verifierChevauchementJoursVoisins(site, horaires, joursOuverts, avant, apres, existantes, demandesExistantes);
		for (PresenceHeuresSupJour e : existantes) {
			if (joursOuverts.contains(e.getJournee())) {
				PresenceHeuresSupDemande conflit = demandesExistantes.get(e.getDemandeId());
				throw PresenceMetierException.conflict("CHEVAUCHEMENT_HEURES_SUP",
						"Une demande en attente ou validée existe déjà pour ce collaborateur, ce site et le "
								+ HeuresSupNotifier.date(e.getJournee())
								+ (conflit != null ? " (" + conflit.getReference() + ")" : ""));
			}
		}

		// 6. Création.
		PresenceHeuresSupDemande d = new PresenceHeuresSupDemande();
		d.setSiteId(site.getId());
		d.setDateDebut(debut);
		d.setDateFin(fin);
		d.setOuvertureAvantMinutes(avant);
		d.setOuvertureApresMinutes(apres);
		d.setMotif(motif);
		d.setDebutPlageTs(premiere.ouvertureDebut().toInstant());
		d.setDemandeurFicheId(demandeur.ficheId());
		d.setDemandeurCompteId(demandeur.compteId());
		d.setDemandeurNom(demandeur.libelle());
		d.setCreeLe(maintenant);
		if (directe) {
			d.setStatut(StatutHeuresSup.VALIDEE);
			d.setCreeeValidee(true);
			d.setDecideParCompteId(demandeur.compteId());
			d.setDecideParNom(demandeur.libelle());
			d.setDecideLe(maintenant);
		} else {
			d.setStatut(StatutHeuresSup.EN_ATTENTE);
		}
		try {
			PresenceHeuresSupDemande saved = demandeRepository.saveAndFlush(d);
			List<PresenceHeuresSupBeneficiaire> beneficiaires = new ArrayList<>();
			for (UUID id : ids) {
				CollaborateurResolu c = resolus.get(id);
				PresenceHeuresSupBeneficiaire b = new PresenceHeuresSupBeneficiaire();
				b.setDemandeId(saved.getId());
				b.setCollaborateurId(c.collaborateurId());
				b.setCompteId(c.compteId());
				b.setMatricule(c.matricule());
				b.setNom(c.nom());
				b.setDirectionId(c.directionId());
				b.setDirectionLibelle(c.directionLibelle());
				beneficiaires.add(beneficiaireRepository.save(b));
			}
			for (PresenceHeuresSupBeneficiaire b : beneficiaires) {
				for (LocalDate jour : joursOuverts) {
					PresenceHeuresSupJour ligne = new PresenceHeuresSupJour();
					ligne.setDemandeId(saved.getId());
					ligne.setBeneficiaireId(b.getId());
					ligne.setCollaborateurId(b.getCollaborateurId().toString());
					ligne.setCompteId(b.getCompteId() != null ? b.getCompteId().toString() : null);
					ligne.setSiteId(site.getId());
					ligne.setJournee(jour);
					ligne.setDirectionId(b.getDirectionId());
					jourRepository.save(ligne);
				}
			}
			jourRepository.flush();
			String resume = HeuresSupNotifier.resume(site.getLibelle(), debut, fin, HeuresSupNotifier.fenetreEtHeuresSup(premiere, avant, apres));
			if (directe) {
				notifier.creeeValidee(saved, resume, beneficiaires);
			} else {
				notifier.creeeEnAttente(saved, resume);
			}
			log.info("Demande d'heures sup {} {} site={} bénéficiaires={}", saved.getReference(), saved.getStatut(),
					site.getId(), beneficiaires.size());
			return mapper.versReponse(saved);
		} catch (DataIntegrityViolationException ex) {
			// Deux demandes simultanées pour le même (collaborateur, site, journée) : la contrainte unique tranche.
			// Toute autre violation d'intégrité reste une erreur serveur (pas de faux 409).
			if (!ContraintesUniques.viole(ex, ContraintesUniques.HEURES_SUP_JOUR)) {
				throw ex;
			}
			throw PresenceMetierException.conflict("CHEVAUCHEMENT_HEURES_SUP",
					"Une demande en attente ou validée existe déjà pour un de ces collaborateurs sur ce site et ces jours");
		}
	}

	/** Identité du RO / chef de département : fiche lue au référentiel avec son JWT, jamais l'en-tête mobile. */
	private Acteur demandeurRo() {
		ValideurPointage moi = referentielClient.monValideurPointage();
		String compte = accessService.currentCompteId();
		if (compte == null && moi.collaborateurCompteId() != null) {
			compte = moi.collaborateurCompteId().toString();
		}
		return new Acteur(compte, moi.collaborateurId().toString(), null, moi.collaborateurNom());
	}

	/** D-P13.a : le demandeur est le valideur D-H03 de chacun des bénéficiaires ; jamais le sien (403 pour toute la demande). */
	private static void interdirePerimetre(Acteur demandeur, List<UUID> ids, Map<UUID, CollaborateurResolu> resolus) {
		UUID fiche = UUID.fromString(demandeur.ficheId());
		if (ids.contains(fiche)) {
			throw PresenceMetierException.forbidden("HORS_PERIMETRE", "Vous ne pouvez pas demander d'heures pour vous-même");
		}
		for (UUID id : ids) {
			if (!fiche.equals(resolus.get(id).valideurId())) {
				throw PresenceMetierException.forbidden("HORS_PERIMETRE",
						"Un des collaborateurs n'est pas dans votre périmètre de validation");
			}
		}
	}

	private static void interdireAutoValidation(Acteur auteur, List<UUID> ids, Map<UUID, CollaborateurResolu> resolus) {
		for (UUID id : ids) {
			CollaborateurResolu c = resolus.get(id);
			boolean parCompte = auteur.compteId() != null && c.compteId() != null
					&& auteur.compteId().equals(c.compteId().toString());
			boolean parFiche = auteur.ficheId() != null && auteur.ficheId().equals(id.toString());
			if (parCompte || parFiche) {
				throw PresenceMetierException.forbidden("AUTO_VALIDATION_INTERDITE",
						"Vous ne pouvez pas créer des heures supplémentaires pour vous-même");
			}
		}
	}

	/**
	 * D-P11.a appliqué aux fenêtres élargies : pour chaque jour demandé et chaque bénéficiaire, la fenêtre ne dépasse pas
	 * celle du jour voisin (celle de cette demande, d'une demande active existante, ou la fenêtre de base).
	 */
	private void verifierChevauchementJoursVoisins(PresenceSite site, Map<DayOfWeek, Horaire> horaires,
			List<LocalDate> joursOuverts, int avant, int apres, List<PresenceHeuresSupJour> existantes,
			Map<UUID, PresenceHeuresSupDemande> demandesExistantes) {
		// {@code existantes} ne contient que des lignes des bénéficiaires demandés : toute demande active sur un jour
		// voisin d'un jour demandé compte, sinon la fenêtre de base du voisin fait foi.
		for (LocalDate jour : joursOuverts) {
			Fenetre courante = fenetre(site, horaires, jour, avant, apres);
			for (int decalage : new int[] {-1, 1}) {
				LocalDate voisin = jour.plusDays(decalage);
				if (!horaires.containsKey(voisin.getDayOfWeek())) {
					continue;
				}
				List<Fenetre> candidates = new ArrayList<>();
				if (joursOuverts.contains(voisin)) {
					candidates.add(fenetre(site, horaires, voisin, avant, apres));
				} else {
					candidates.add(fenetre(site, horaires, voisin, 0, 0));
					for (PresenceHeuresSupJour e : existantes) {
						PresenceHeuresSupDemande autre = demandesExistantes.get(e.getDemandeId());
						if (e.getJournee().equals(voisin) && autre != null) {
							candidates.add(fenetre(site, horaires, voisin, autre.getOuvertureAvantMinutes(),
									autre.getOuvertureApresMinutes()));
						}
					}
				}
				for (Fenetre f : candidates) {
					boolean chevauche = decalage < 0
							? f.ouvertureFin().isAfter(courante.ouvertureDebut())
							: courante.ouvertureFin().isAfter(f.ouvertureDebut());
					if (chevauche) {
						throw PresenceMetierException.unprocessable("HEURES_SUP_CHEVAUCHENT_JOUR_VOISIN",
								"La fenêtre élargie du " + HeuresSupNotifier.date(jour)
										+ " chevauche celle du " + HeuresSupNotifier.date(voisin));
					}
				}
			}
		}
	}

	private static Fenetre fenetre(PresenceSite site, Map<DayOfWeek, Horaire> horaires, LocalDate jour, int avant, int apres) {
		return FenetreHoraireService.fenetre(jour, horaires.get(jour.getDayOfWeek()),
				site.getToleranceAvantMinutes(), site.getToleranceApresMinutes(), avant, apres);
	}

	/** Une ouverture est absente (null ou 0) ou valide : 30 à 240 minutes par pas de 30 (D-P13). */
	static int ouverture(Integer minutes) {
		if (minutes == null || minutes == 0) {
			return 0;
		}
		if (minutes < OUVERTURE_MIN_MINUTES || minutes > OUVERTURE_MAX_MINUTES || minutes % OUVERTURE_PAS_MINUTES != 0) {
			throw PresenceMetierException.badRequest("DUREE_HEURES_SUP_INVALIDE",
					"L'ouverture doit être comprise entre " + OUVERTURE_MIN_MINUTES + " et " + OUVERTURE_MAX_MINUTES
							+ " minutes, par pas de " + OUVERTURE_PAS_MINUTES);
		}
		return minutes;
	}
}
