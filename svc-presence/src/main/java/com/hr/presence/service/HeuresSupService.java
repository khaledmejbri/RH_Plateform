package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.CollaborateurResolu;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.StatutHeuresSup;
import com.hr.presence.dto.HeuresSupDtos.BeneficiairePossibleResponse;
import com.hr.presence.dto.HeuresSupDtos.DemandeHeuresSupResponse;
import com.hr.presence.dto.HeuresSupDtos.HeuresComptees;
import com.hr.presence.dto.HeuresSupDtos.HeuresSupAVenir;
import com.hr.presence.dto.HeuresSupDtos.MesHeuresSupResponse;
import com.hr.presence.dto.PageResponse;
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
import com.hr.presence.web.PresenceMetierException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Décisions, annulations, expiration et lectures des demandes d'heures supplémentaires (D-P13, D-P16). Les méthodes
 * qui peuvent expirer paresseusement une demande ne rétablissent pas la transaction sur {@link PresenceMetierException} :
 * le passage en EXPIREE doit survivre au 409 qui suit.
 */
@Service
public class HeuresSupService {

	private final PresenceHeuresSupDemandeRepository demandeRepository;
	private final PresenceHeuresSupBeneficiaireRepository beneficiaireRepository;
	private final PresenceHeuresSupJourRepository jourRepository;
	private final PresenceSiteRepository siteRepository;
	private final FenetreHoraireService fenetreService;
	private final CalculHeuresSupService calculService;
	private final ReferentielClient referentielClient;
	private final PresenceAccessService accessService;
	private final ActeurResolver acteurResolver;
	private final HeuresSupNotifier notifier;
	private final HeuresSupReponseMapper mapper;
	private final Clock clock;

	public HeuresSupService(
			PresenceHeuresSupDemandeRepository demandeRepository,
			PresenceHeuresSupBeneficiaireRepository beneficiaireRepository,
			PresenceHeuresSupJourRepository jourRepository,
			PresenceSiteRepository siteRepository,
			FenetreHoraireService fenetreService,
			CalculHeuresSupService calculService,
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
		this.calculService = calculService;
		this.referentielClient = referentielClient;
		this.accessService = accessService;
		this.acteurResolver = acteurResolver;
		this.notifier = notifier;
		this.mapper = mapper;
		this.clock = clock;
	}

	// ---- Décisions RH / ADMIN ----------------------------------------------------------------------------------

	@Transactional(noRollbackFor = PresenceMetierException.class)
	public DemandeHeuresSupResponse valider(UUID id) {
		PresenceHeuresSupDemande d = charger(id);
		List<PresenceHeuresSupBeneficiaire> beneficiaires = beneficiaireRepository.findByDemandeId(d.getId());
		Acteur decideur = acteurResolver.avecFiche();
		interdireAutoValidation(d, beneficiaires, decideur);
		expirerSiEchue(d);
		exigerStatut(d, StatutHeuresSup.EN_ATTENTE);

		d.setStatut(StatutHeuresSup.VALIDEE);
		d.setDecideParCompteId(decideur.compteId());
		d.setDecideParNom(decideur.libelle());
		d.setDecideLe(clock.instant());
		PresenceHeuresSupDemande saved = demandeRepository.save(d);
		notifier.validee(saved, resume(saved), beneficiaires);
		return mapper.versReponse(saved);
	}

	@Transactional(noRollbackFor = PresenceMetierException.class)
	public DemandeHeuresSupResponse refuser(UUID id, String motif) {
		if (motif == null || motif.isBlank()) {
			throw PresenceMetierException.badRequest("MOTIF_OBLIGATOIRE", "Le motif du refus est obligatoire");
		}
		exigerMotifCourt(motif);
		PresenceHeuresSupDemande d = charger(id);
		List<PresenceHeuresSupBeneficiaire> beneficiaires = beneficiaireRepository.findByDemandeId(d.getId());
		Acteur decideur = acteurResolver.avecFiche();
		interdireAutoValidation(d, beneficiaires, decideur);
		expirerSiEchue(d);
		exigerStatut(d, StatutHeuresSup.EN_ATTENTE);

		d.setStatut(StatutHeuresSup.REFUSEE);
		d.setMotifRefus(motif.trim());
		d.setDecideParCompteId(decideur.compteId());
		d.setDecideParNom(decideur.libelle());
		d.setDecideLe(clock.instant());
		PresenceHeuresSupDemande saved = demandeRepository.save(d);
		jourRepository.deleteByDemandeId(saved.getId());
		notifier.refusee(saved, resume(saved));
		return mapper.versReponse(saved);
	}

	/** RH / ADMIN : annule une demande VALIDEE dont la plage n'a pas commencé ; une EN_ATTENTE se refuse (409). */
	@Transactional(noRollbackFor = PresenceMetierException.class)
	public DemandeHeuresSupResponse annulerParBackoffice(UUID id, String motif) {
		exigerMotifCourt(motif);
		PresenceHeuresSupDemande d = charger(id);
		List<PresenceHeuresSupBeneficiaire> beneficiaires = beneficiaireRepository.findByDemandeId(d.getId());
		Acteur auteur = acteurResolver.avecFiche();
		// Ordre : 404, puis 403 (concerné par la demande), puis 409 (expirée, statut, plage commencée)
		interdireAutoValidation(d, beneficiaires, auteur);
		expirerSiEchue(d);
		exigerStatut(d, StatutHeuresSup.VALIDEE);
		if (d.getDebutPlageTs() != null && !d.getDebutPlageTs().isAfter(clock.instant())) {
			throw PresenceMetierException.conflict("STATUT_INCOMPATIBLE",
					"La plage a déjà commencé : la demande ne peut plus être annulée");
		}

		marquerAnnulee(d, auteur, motif);
		PresenceHeuresSupDemande saved = demandeRepository.save(d);
		jourRepository.deleteByDemandeId(saved.getId());
		notifier.annulee(saved, resume(saved), beneficiaires);
		return mapper.versReponse(saved);
	}

	// ---- Annulation par le demandeur (mobile) ------------------------------------------------------------------

	/** Le demandeur annule sa demande EN_ATTENTE ; aucune notification. Pas le demandeur, ou VALIDEE : 403. */
	@Transactional(noRollbackFor = PresenceMetierException.class)
	public DemandeHeuresSupResponse annulerParDemandeur(UUID id) {
		PresenceHeuresSupDemande d = charger(id);
		String compte = accessService.currentCompteId();
		if (compte == null || !compte.equals(d.getDemandeurCompteId())) {
			throw new SecurityException("Cette demande ne vous appartient pas");
		}
		if (d.getStatut() == StatutHeuresSup.VALIDEE) {
			throw new SecurityException("Une demande validée ne peut être annulée que par les RH");
		}
		expirerSiEchue(d);
		exigerStatut(d, StatutHeuresSup.EN_ATTENTE);
		marquerAnnulee(d, acteurResolver.depuisJwt(), null);
		PresenceHeuresSupDemande saved = demandeRepository.save(d);
		jourRepository.deleteByDemandeId(saved.getId());
		return mapper.versReponse(saved);
	}

	// ---- Expiration (D-P13.d) ----------------------------------------------------------------------------------

	/** Identifiants des demandes EN_ATTENTE dont la fenêtre du premier jour est ouverte. */
	@Transactional(readOnly = true)
	public List<UUID> idsAExpirer() {
		return demandeRepository.findByStatutAndDebutPlageTsLessThanEqual(StatutHeuresSup.EN_ATTENTE, clock.instant())
				.stream().map(PresenceHeuresSupDemande::getId).toList();
	}

	/** Une transaction par demande ; idempotent (une demande déjà décidée n'est pas touchée). */
	@Transactional
	public void expirer(UUID id) {
		demandeRepository.findById(id).ifPresent(d -> {
			if (estEchue(d)) {
				passerEnExpiree(d);
			}
		});
	}

	private boolean estEchue(PresenceHeuresSupDemande d) {
		return d.getStatut() == StatutHeuresSup.EN_ATTENTE && d.getDebutPlageTs() != null
				&& !d.getDebutPlageTs().isAfter(clock.instant());
	}

	/** Expiration paresseuse : la demande passe EXPIREE (et le reste), puis la décision demandée reçoit un 409. */
	private void expirerSiEchue(PresenceHeuresSupDemande d) {
		if (estEchue(d)) {
			passerEnExpiree(d);
			throw PresenceMetierException.conflict("STATUT_INCOMPATIBLE",
					"La demande a expiré : la fenêtre de pointage est ouverte");
		}
	}

	private void passerEnExpiree(PresenceHeuresSupDemande d) {
		d.setStatut(StatutHeuresSup.EXPIREE);
		PresenceHeuresSupDemande saved = demandeRepository.save(d);
		jourRepository.deleteByDemandeId(saved.getId());
		notifier.expiree(saved, resume(saved));
	}

	// ---- Lectures RH / DIRECTION / ADMIN -----------------------------------------------------------------------

	@Transactional(readOnly = true)
	public PageResponse<DemandeHeuresSupResponse> lister(StatutHeuresSup statut, UUID siteId, UUID collaborateurId,
			UUID directionId, LocalDate du, LocalDate au, int page, int size) {
		if (du != null && au != null && au.isBefore(du)) {
			throw PresenceMetierException.badRequest("PERIODE_INVALIDE", "La date de fin précède la date de début");
		}
		int taille = Math.clamp(size, 1, 50);
		Page<PresenceHeuresSupDemande> resultat = demandeRepository.findAll(
				filtre(statut, siteId, collaborateurId, directionId, du, au),
				PageRequest.of(Math.max(page, 0), taille, Sort.by(Sort.Direction.DESC, "creeLe")));
		return new PageResponse<>(mapper.versReponses(resultat.getContent()), resultat.getNumber(), resultat.getSize(),
				resultat.getTotalElements(), resultat.getTotalPages());
	}

	@Transactional(readOnly = true)
	public DemandeHeuresSupResponse obtenir(UUID id) {
		return mapper.versReponse(charger(id));
	}

	private static Specification<PresenceHeuresSupDemande> filtre(StatutHeuresSup statut, UUID siteId, UUID collaborateurId,
			UUID directionId, LocalDate du, LocalDate au) {
		return (root, query, cb) -> {
			List<Predicate> predicats = new ArrayList<>();
			if (statut != null) predicats.add(cb.equal(root.get("statut"), statut));
			if (siteId != null) predicats.add(cb.equal(root.get("siteId"), siteId));
			if (du != null) predicats.add(cb.greaterThanOrEqualTo(root.get("dateFin"), du));
			if (au != null) predicats.add(cb.lessThanOrEqualTo(root.get("dateDebut"), au));
			if (collaborateurId != null || directionId != null) {
				var sousRequete = query.subquery(UUID.class);
				var b = sousRequete.from(PresenceHeuresSupBeneficiaire.class);
				List<Predicate> conditions = new ArrayList<>();
				conditions.add(cb.equal(b.get("demandeId"), root.get("id")));
				if (collaborateurId != null) conditions.add(cb.equal(b.get("collaborateurId"), collaborateurId));
				if (directionId != null) conditions.add(cb.equal(b.get("directionId"), directionId));
				sousRequete.select(b.get("id")).where(conditions.toArray(Predicate[]::new));
				predicats.add(cb.exists(sousRequete));
			}
			return cb.and(predicats.toArray(Predicate[]::new));
		};
	}

	/** Heures comptées, une page de lignes bénéficiaire × site × journée (D-P16.a). */
	@Transactional(readOnly = true)
	public PageResponse<HeuresComptees> comptees(LocalDate du, LocalDate au, UUID siteId, UUID collaborateurId,
			UUID directionId, int page, int size) {
		LocalDate[] periode = calculService.periode(du, au);
		int taille = Math.clamp(size, 1, 100);
		Page<PresenceHeuresSupJour> lignes = jourRepository.findAll(
				PresenceHeuresSupJourRepository.comptage(periode[0], periode[1], siteId,
						collaborateurId != null ? collaborateurId.toString() : null, directionId, null, null, null),
				PageRequest.of(Math.max(page, 0), taille, Sort.by(Sort.Direction.DESC, "journee").and(Sort.by("id"))));
		return new PageResponse<>(calculService.compter(lignes.getContent()), lignes.getNumber(), lignes.getSize(),
				lignes.getTotalElements(), lignes.getTotalPages());
	}

	/** Toutes les lignes du filtre (export CSV), plafonnées par la période maximale. */
	@Transactional(readOnly = true)
	public List<HeuresComptees> comptees(LocalDate du, LocalDate au, UUID siteId, UUID collaborateurId, UUID directionId) {
		LocalDate[] periode = calculService.periode(du, au);
		return calculService.compter(jourRepository.findAll(
				PresenceHeuresSupJourRepository.comptage(periode[0], periode[1], siteId,
						collaborateurId != null ? collaborateurId.toString() : null, directionId, null, null, null),
				Sort.by("journee").and(Sort.by("id"))));
	}

	// ---- Mobile : demandeur ------------------------------------------------------------------------------------

	/** Aide de saisie du RO : collaborateurs dont il est le valideur D-H03 (le contrôle serveur reste celui de la création). */
	@Transactional(readOnly = true)
	public List<BeneficiairePossibleResponse> beneficiairesPossibles() {
		ValideurPointage moi = referentielClient.monValideurPointage();
		return referentielClient.perimetreValidation(moi.collaborateurId()).stream()
				.filter(CollaborateurResolu::actif)
				.map(c -> new BeneficiairePossibleResponse(c.collaborateurId(), c.matricule(), c.nom()))
				.sorted(Comparator.comparing(b -> b.nom() != null ? b.nom() : ""))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<DemandeHeuresSupResponse> mesDemandes(StatutHeuresSup statut) {
		String compte = compteAppelant();
		List<PresenceHeuresSupDemande> demandes = statut == null
				? demandeRepository.findByDemandeurCompteIdOrderByCreeLeDesc(compte)
				: demandeRepository.findByDemandeurCompteIdAndStatutOrderByCreeLeDesc(compte, statut);
		return mapper.versReponses(demandes);
	}

	/**
	 * Vue du RO (décision client n° 4) : heures comptées des bénéficiaires de ses propres demandes VALIDEE, filtre serveur
	 * sur le compte du JWT. Une demande d'un autre demandeur : 403.
	 */
	@Transactional(readOnly = true)
	public List<HeuresComptees> mesDemandesComptees(LocalDate du, LocalDate au, UUID demandeId) {
		String compte = compteAppelant();
		// 400 (période) avant 404 / 403 (demande)
		LocalDate[] periode = calculService.periode(du, au);
		if (demandeId != null) {
			PresenceHeuresSupDemande d = charger(demandeId);
			if (!compte.equals(d.getDemandeurCompteId())) {
				throw new SecurityException("Cette demande ne vous appartient pas");
			}
		}
		return calculService.compter(jourRepository.findAll(
				PresenceHeuresSupJourRepository.comptage(periode[0], periode[1], null, null, null,
						null, compte, demandeId),
				Sort.by(Sort.Direction.DESC, "journee").and(Sort.by("id"))));
	}

	// ---- Mobile : bénéficiaire ---------------------------------------------------------------------------------

	/** Mes heures sup validées à venir et mes heures comptées : identité du JWT, aucun paramètre d'identité. */
	@Transactional(readOnly = true)
	public MesHeuresSupResponse mesHeures(LocalDate du, LocalDate au) {
		String compte = compteAppelant();
		LocalDate aujourdhui = clock.instant().atZone(ClockConfig.ZONE_TUNIS).toLocalDate();
		List<PresenceHeuresSupJour> avenir = jourRepository.findByCompteIdAndJourneeGreaterThanEqualOrderByJournee(compte, aujourdhui);
		Map<UUID, PresenceHeuresSupDemande> demandes = demandeRepository.findAllById(
						avenir.stream().map(PresenceHeuresSupJour::getDemandeId).collect(Collectors.toSet())).stream()
				.filter(d -> d.getStatut() == StatutHeuresSup.VALIDEE)
				.collect(Collectors.toMap(PresenceHeuresSupDemande::getId, d -> d));
		Map<UUID, PresenceSite> sites = siteRepository.findAllById(
						avenir.stream().map(PresenceHeuresSupJour::getSiteId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(PresenceSite::getId, s -> s));
		List<HeuresSupAVenir> aVenir = avenir.stream()
				.filter(l -> demandes.containsKey(l.getDemandeId()))
				.map(l -> {
					PresenceHeuresSupDemande d = demandes.get(l.getDemandeId());
					PresenceSite site = sites.get(l.getSiteId());
					return new HeuresSupAVenir(d.getReference(), l.getSiteId(), site != null ? site.getLibelle() : null,
							l.getJournee(), d.getOuvertureAvantMinutes(), d.getOuvertureApresMinutes());
				})
				.toList();
		LocalDate[] periode = calculService.periode(du, au);
		List<HeuresComptees> comptees = calculService.compter(jourRepository.findAll(
				PresenceHeuresSupJourRepository.comptage(periode[0], periode[1], null, null, null,
						Set.of(compte), null, null),
				Sort.by(Sort.Direction.DESC, "journee").and(Sort.by("id"))));
		return new MesHeuresSupResponse(aVenir, comptees);
	}

	// ---- Utilitaires -------------------------------------------------------------------------------------------

	private PresenceHeuresSupDemande charger(UUID id) {
		return demandeRepository.findById(id)
				.orElseThrow(() -> PresenceMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable"));
	}

	private static void exigerStatut(PresenceHeuresSupDemande d, StatutHeuresSup attendu) {
		if (d.getStatut() != attendu) {
			throw PresenceMetierException.conflict("STATUT_INCOMPATIBLE",
					"Cette demande est " + d.getStatut() + " : l'action demandée n'est pas possible");
		}
	}

	/** Personne ne décide d'une demande dont il est le demandeur ou un bénéficiaire (compte JWT et fiche). */
	private static void interdireAutoValidation(PresenceHeuresSupDemande d,
			List<PresenceHeuresSupBeneficiaire> beneficiaires, Acteur acteur) {
		Set<String> comptes = beneficiaires.stream().map(b -> b.getCompteId() != null ? b.getCompteId().toString() : null)
				.filter(java.util.Objects::nonNull).collect(Collectors.toSet());
		Set<String> fiches = beneficiaires.stream().map(b -> b.getCollaborateurId().toString()).collect(Collectors.toSet());
		boolean parCompte = acteur.compteId() != null
				&& (acteur.compteId().equals(d.getDemandeurCompteId()) || comptes.contains(acteur.compteId()));
		boolean parFiche = acteur.ficheId() != null
				&& (acteur.ficheId().equals(d.getDemandeurFicheId()) || fiches.contains(acteur.ficheId()));
		if (parCompte || parFiche) {
			throw PresenceMetierException.forbidden("AUTO_VALIDATION_INTERDITE",
					"Vous ne pouvez pas traiter une demande d'heures supplémentaires qui vous concerne");
		}
	}

	/** Meme plafond que le motif de creation (500 caracteres) : 400 avant tout acces a la demande. */
	private static void exigerMotifCourt(String motif) {
		if (motif != null && motif.trim().length() > HeuresSupCreationService.MOTIF_MAX_CARACTERES) {
			throw PresenceMetierException.badRequest("MOTIF_TROP_LONG",
					"Le motif ne peut pas depasser " + HeuresSupCreationService.MOTIF_MAX_CARACTERES + " caracteres");
		}
	}

	private void marquerAnnulee(PresenceHeuresSupDemande d, Acteur auteur, String motif) {
		d.setStatut(StatutHeuresSup.ANNULEE);
		d.setAnnuleParCompteId(auteur.compteId());
		d.setAnnuleParNom(auteur.libelle());
		d.setAnnuleLe(clock.instant());
		d.setMotifAnnulation(motif != null && !motif.isBlank() ? motif.trim() : null);
	}

	private String compteAppelant() {
		String compte = accessService.currentCompteId();
		if (compte == null) {
			throw new SecurityException("Impossible d'identifier le compte courant");
		}
		return compte;
	}

	/** Résumé des notifications : site, dates, fenêtre élargie du premier jour (horaire courant). */
	private String resume(PresenceHeuresSupDemande d) {
		PresenceSite site = siteRepository.findById(d.getSiteId()).orElse(null);
		String libelle = site != null ? site.getLibelle() : "?";
		String fenetre = null;
		if (site != null) {
			Map<DayOfWeek, Horaire> horaires = fenetreService.horaires(site.getId());
			Optional<LocalDate> premier = d.getDateDebut().datesUntil(d.getDateFin().plusDays(1))
					.filter(j -> horaires.containsKey(j.getDayOfWeek())).findFirst();
			if (premier.isPresent()) {
				Fenetre f = FenetreHoraireService.fenetre(premier.get(), horaires.get(premier.get().getDayOfWeek()),
						site.getToleranceAvantMinutes(), site.getToleranceApresMinutes(),
						d.getOuvertureAvantMinutes(), d.getOuvertureApresMinutes());
				fenetre = HeuresSupNotifier.fenetreEtHeuresSup(f, d.getOuvertureAvantMinutes(), d.getOuvertureApresMinutes());
			}
		}
		return HeuresSupNotifier.resume(libelle, d.getDateDebut(), d.getDateFin(), fenetre);
	}
}
