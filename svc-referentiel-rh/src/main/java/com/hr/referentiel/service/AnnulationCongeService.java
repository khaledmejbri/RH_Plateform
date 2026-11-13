package com.hr.referentiel.service;

import com.hr.referentiel.domain.ActionWorkflowAdministratif;
import com.hr.referentiel.domain.OrigineMouvementSolde;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeMouvementSolde;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdminWorkflowHistory;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdminWorkflowHistoryRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Annulation par la RH d'un congé déjà approuvé (D-S06.d, D4) : RH / ADMIN autre que le demandeur, motif 5 à 500,
 * congé non commencé (jour de Tunis). Recrédit pour un congé annuel décompté, historique tracé, notifications après
 * commit (demandeur et valideur D-H03 figé, jamais l'auteur). Une demande encore en cours de validation n'est pas
 * annulable ici : 409 {@code DEMANDE_NON_APPROUVEE} (la RH la refuse par le circuit normal).
 */
@Service
public class AnnulationCongeService {

	public static final int MOTIF_MIN = 5;
	public static final int MOTIF_MAX = 500;

	private final DemandeAdministrativeRhRepository demandeRepository;
	private final DemandeAdminWorkflowHistoryRepository historiqueRepository;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final CollaborateurRepository collaborateurRepository;
	private final AuteurParametreResolver auteurResolver;
	private final SoldeCongeService soldeService;
	private final RhNotificationPublisher notificationPublisher;
	private final Clock clock;

	public AnnulationCongeService(DemandeAdministrativeRhRepository demandeRepository,
			DemandeAdminWorkflowHistoryRepository historiqueRepository,
			CollaborateurConnecteService collaborateurConnecteService, CollaborateurRepository collaborateurRepository,
			AuteurParametreResolver auteurResolver, SoldeCongeService soldeService,
			RhNotificationPublisher notificationPublisher, Clock clock) {
		this.demandeRepository = demandeRepository;
		this.historiqueRepository = historiqueRepository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.collaborateurRepository = collaborateurRepository;
		this.auteurResolver = auteurResolver;
		this.soldeService = soldeService;
		this.notificationPublisher = notificationPublisher;
		this.clock = clock;
	}

	@Transactional
	public DemandeAdministrativeRh annuler(UUID demandeId, String motifBrut, Jwt jwt) {
		// 404 avant 403 identité avant 400 corps avant 409 état (D-S15.f)
		// verrou d'abord : l'état lu est celui d'après toute décision concurrente (ordre des verrous : demande puis solde)
		DemandeAdministrativeRh d = demandeRepository.findByIdForUpdate(demandeId).orElseThrow(
				() -> ReferentielMetierException.notFound("DEMANDE_INTROUVABLE", "Demande introuvable."));
		DemandeAdministrativeRh apercu = d;
		Collaborateur appelant = collaborateurConnecteService.resoudre(jwt).orElse(null);
		if (appelant != null && appelant.getId().equals(apercu.getDemandeur().getId())) {
			throw ReferentielMetierException.forbidden("AUTO_ANNULATION_INTERDITE",
					"Vous ne pouvez pas annuler votre propre congé : un autre RH ou un administrateur doit le faire.");
		}
		String motif = motifBrut == null ? "" : motifBrut.trim();
		if (motif.length() < MOTIF_MIN || motif.length() > MOTIF_MAX) {
			throw ReferentielMetierException.badRequest("MOTIF_OBLIGATOIRE",
					"Le motif est obligatoire (" + MOTIF_MIN + " à " + MOTIF_MAX + " caractères).");
		}
		verifierEtat(d);

		AuteurParametre auteur = auteurResolver.depuis(jwt);
		String mention = null;
		if (d.getJoursDecomptes() != null) {
			var solde = soldeService.verrouiller(d.getDemandeur().getId());
			soldeService.enregistrer(solde, TypeMouvementSolde.RECREDIT_ANNULATION, d.getJoursDecomptes(), null, motif,
					d.getId(), d.getNumero(), null, null, auteur);
		} else if (CongeDecompteService.estAnnuel(d)) {
			mention = CongeDecompteService.MENTION_ANTERIEURE;
		}
		d.setStatut(StatutDemandeAdministrativeRh.ANNULEE);
		d.setMotifAnnulation(motif);
		d.setAnnuleApresApprobationLe(clock.instant());
		d.setAnnuleParNom(auteur.nom());
		demandeRepository.save(d);

		DemandeAdminWorkflowHistory h = new DemandeAdminWorkflowHistory();
		h.setDemandeAdministrative(d);
		h.setAction(ActionWorkflowAdministratif.ANNULATION_APRES_APPROBATION);
		h.setActeurIdentifiant(appelant == null ? null : appelant.getId());
		h.setActeurNom(auteur.nom());
		h.setCommentaire("Annulée après approbation par " + auteur.nom() + ". Motif : " + motif
				+ (mention == null ? "" : ". " + mention));
		historiqueRepository.save(h);

		Collaborateur demandeur = collaborateurRepository.findDetailById(d.getDemandeur().getId()).orElse(d.getDemandeur());
		Collaborateur valideur = d.getValideurAttendu();
		UUID auteurFiche = appelant == null ? null : appelant.getId();
		LocalDate debut = d.getPeriodeDebut();
		LocalDate fin = d.getPeriodeFin();
		ApresCommit.executer(() -> notificationPublisher.notifierCongeAnnuleApresApprobation(demandeur, valideur,
				auteurFiche, auteur.nom(), debut, fin, motif, d.getId(), d.getNumero()));
		return d;
	}

	/** Statut et type (409) : décidée, non approuvée, non annulable, déjà commencée. */
	private void verifierEtat(DemandeAdministrativeRh d) {
		StatutDemandeAdministrativeRh s = d.getStatut();
		if (s == StatutDemandeAdministrativeRh.ANNULEE || s == StatutDemandeAdministrativeRh.REFUSEE) {
			throw ReferentielMetierException.conflict("DEMANDE_DEJA_DECIDEE",
					"Cette demande est déjà décidée (« " + s.getLibelle() + " »).");
		}
		if (s != StatutDemandeAdministrativeRh.APPROUVEE) {
			throw ReferentielMetierException.conflict("DEMANDE_NON_APPROUVEE",
					"Cette demande n'est pas encore approuvée : refusez-la par le circuit de validation.");
		}
		if (d.getTypeDemande() != TypeDemandeAdministrativeRh.CONGE) {
			throw ReferentielMetierException.conflict("DEMANDE_NON_ANNULABLE",
					"Seul un congé approuvé peut être annulé après approbation.");
		}
		LocalDate aujourdhui = LocalDate.now(clock.withZone(FormatJours.TUNIS));
		if (d.getPeriodeDebut() != null && !d.getPeriodeDebut().isAfter(aujourdhui)) {
			throw ReferentielMetierException.conflict("CONGE_DEJA_COMMENCE",
					"Ce congé a déjà commencé : il ne peut plus être annulé.");
		}
	}
}
