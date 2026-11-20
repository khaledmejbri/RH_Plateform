package com.hr.referentiel.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.SocieteParametresDtos.SocieteParametresResponse;
import com.hr.referentiel.dto.WorkflowInterneDtos.AbsenceCollaborateurInterne;
import com.hr.referentiel.dto.WorkflowInterneDtos.CalendrierSocieteInterneResponse;
import com.hr.referentiel.dto.WorkflowInterneDtos.CollaborateurResumeInterne;
import com.hr.referentiel.dto.WorkflowInterneDtos.ContexteDemandeur;
import com.hr.referentiel.dto.WorkflowInterneDtos.IntervenantResolu;
import com.hr.referentiel.dto.WorkflowInterneDtos.ParamsRegleResolution;
import com.hr.referentiel.dto.WorkflowInterneDtos.RegleResolutionRequest;
import com.hr.referentiel.dto.WorkflowInterneDtos.ResolutionLotRequest;
import com.hr.referentiel.dto.WorkflowInterneDtos.ResolutionLotResponse;
import com.hr.referentiel.dto.WorkflowInterneDtos.ResultatResolution;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.Fonction;
import com.hr.referentiel.entity.FonctionAffectation;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.FonctionAffectationRepository;
import com.hr.referentiel.repository.FonctionRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.service.IdentiteSocieteClient.SocieteResume;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Résolution groupée d'intervenants et lectures internes pour {@code svc-workflow} (§ 9.5.3).
 * Précharge l'arbre des unités, les collaborateurs et les fonctions en lot (0 requête N+1).
 */
@Service
public class WorkflowResolutionService {

	private static final int MAX_REGLES = 50;
	private static final Set<String> ROLES_AUTORISES = Set.of("RH", "ADMIN");
	private static final Set<String> TYPES_NOEUDS_RESPONSABLE = Set.of("DEPARTEMENT", "DIRECTION", "DIRECTION_GENERALE");
	private static final Set<String> PORTEES_FONCTION = Set.of("SOCIETE", "UNITE_DEMANDEUR");

	private final CollaborateurRepository collaborateurRepository;
	private final UniteOrganisationRepository uniteRepository;
	private final FonctionRepository fonctionRepository;
	private final FonctionAffectationRepository affectationRepository;
	private final DemandeAdministrativeRhRepository demandeRepository;
	private final SocieteParametresService societeParametresService;
	private final JourFerieService jourFerieService;
	private final IdentiteSocieteClient identiteSocieteClient;
	private final Clock clock;

	public WorkflowResolutionService(
			CollaborateurRepository collaborateurRepository,
			UniteOrganisationRepository uniteRepository,
			FonctionRepository fonctionRepository,
			FonctionAffectationRepository affectationRepository,
			DemandeAdministrativeRhRepository demandeRepository,
			SocieteParametresService societeParametresService,
			JourFerieService jourFerieService,
			IdentiteSocieteClient identiteSocieteClient,
			Clock clock) {
		this.collaborateurRepository = collaborateurRepository;
		this.uniteRepository = uniteRepository;
		this.fonctionRepository = fonctionRepository;
		this.affectationRepository = affectationRepository;
		this.demandeRepository = demandeRepository;
		this.societeParametresService = societeParametresService;
		this.jourFerieService = jourFerieService;
		this.identiteSocieteClient = identiteSocieteClient;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ResolutionLotResponse resoudreLot(ResolutionLotRequest req) {
		if (req.regles() == null || req.regles().isEmpty() || req.regles().size() > MAX_REGLES) {
			throw ReferentielMetierException.badRequest("CORPS_INVALIDE",
					"Le lot de résolution doit contenir entre 1 et 50 règles.");
		}
		LocalDate dateRef = req.date() != null ? req.date() : LocalDate.now(clock);

		// Préchargement unique pour éviter tout N+1
		List<UniteOrganisation> toutesUnites = uniteRepository.findAll();
		List<Collaborateur> tousCollaborateurs = collaborateurRepository.findAllWithUnite();
		Map<UUID, Collaborateur> parId = new HashMap<>(tousCollaborateurs.size() * 2);
		for (Collaborateur c : tousCollaborateurs) {
			parId.put(c.getId(), c);
		}

		Collaborateur demandeur = null;
		if (req.demandeurId() != null) {
			demandeur = parId.get(req.demandeurId());
			if (demandeur == null) {
				throw ReferentielMetierException.notFound("COLLABORATEUR_INTROUVABLE",
						"Demandeur introuvable : " + req.demandeurId());
			}
		}

		Set<String> codesFonctions = new HashSet<>();
		for (RegleResolutionRequest r : req.regles()) {
			ParamsRegleResolution params = r.params() != null ? r.params() : ParamsRegleResolution.VIDE;
			if ("FONCTION".equalsIgnoreCase(r.regle())
					&& params.fonctionCode() != null && !params.fonctionCode().isBlank()) {
				codesFonctions.add(params.fonctionCode().trim().toUpperCase(Locale.ROOT));
			}
		}
		Map<String, Fonction> fonctionsParCode = new HashMap<>();
		Map<String, List<FonctionAffectation>> affectationsParCode = new HashMap<>();
		if (!codesFonctions.isEmpty()) {
			for (Fonction f : fonctionRepository.findAll()) {
				fonctionsParCode.put(f.getCode().toUpperCase(Locale.ROOT), f);
			}
			for (FonctionAffectation a : affectationRepository.findActivesParCodesEtDate(codesFonctions, dateRef)) {
				String code = a.getFonction().getCode().toUpperCase(Locale.ROOT);
				affectationsParCode.computeIfAbsent(code, k -> new ArrayList<>()).add(a);
			}
		}

		Set<UUID> beneficiaires = req.beneficiaires() == null ? Set.of() : new HashSet<>(req.beneficiaires());
		Set<UUID> exclusionsGlobales = req.exclure() == null ? Set.of() : new HashSet<>(req.exclure());

		Map<String, ResultatResolution> resultats = new LinkedHashMap<>();
		for (RegleResolutionRequest regle : req.regles()) {
			if (resultats.containsKey(regle.cle())) {
				throw ReferentielMetierException.badRequest("CORPS_INVALIDE",
						"Clé de règle en double : " + regle.cle());
			}
			resultats.put(regle.cle(), resoudreUneRegle(
					regle, demandeur, beneficiaires, exclusionsGlobales,
					tousCollaborateurs, parId, fonctionsParCode, affectationsParCode));
		}

		ContexteDemandeur contexteDemandeur = demandeur != null
				? construireContexteDemandeur(demandeur, dateRef, toutesUnites)
				: null;

		return new ResolutionLotResponse(resultats, contexteDemandeur);
	}

	/**
	 * Lit le calendrier de la société. Aucun {@code @Transactional} ici afin que l'appel HTTP vers
	 * {@code svc-identite-acces} s'exécute hors transaction base de données.
	 */
	public CalendrierSocieteInterneResponse calendrier(LocalDate du, LocalDate au) {
		UUID societeId = SocieteContexte.courante();
		String fuseau = identiteSocieteClient.trouverParId(societeId)
				.map(SocieteResume::fuseau)
				.filter(f -> f != null && !f.isBlank())
				.orElse("Africa/Tunis");
		SocieteParametresResponse params = societeParametresService.obtenir();
		Set<LocalDate> feriesActifs = jourFerieService.datesActives();
		List<LocalDate> feriesFiltres = feriesActifs.stream()
				.filter(d -> (du == null || !d.isBefore(du)) && (au == null || !d.isAfter(au)))
				.sorted()
				.toList();
		return new CalendrierSocieteInterneResponse(
				societeId,
				fuseau,
				params.joursTravailles(),
				params.dureeHebdoMinutes(),
				params.dureeJourMinutes(),
				params.heureDebutJournee(),
				params.heureFinJournee(),
				params.baseDecompteConge(),
				params.delegationAutoConges(),
				params.publicationQuatreYeux(),
				feriesFiltres);
	}

	@Transactional(readOnly = true)
	public List<AbsenceCollaborateurInterne> absences(LocalDate date) {
		LocalDate jour = date != null ? date : LocalDate.now(clock);
		List<DemandeAdministrativeRh> demandes = demandeRepository.findPourRhCouvrantJour(
				jour, TypeDemandeAdministrativeRh.CONGE, StatutDemandeAdministrativeRh.APPROUVEE);
		List<AbsenceCollaborateurInterne> resultat = new ArrayList<>(demandes.size());
		for (DemandeAdministrativeRh d : demandes) {
			Collaborateur c = d.getDemandeur();
			String typeConge = d.getContenu() != null && d.getContenu().get("type_conge") != null
					? String.valueOf(d.getContenu().get("type_conge"))
					: null;
			resultat.add(new AbsenceCollaborateurInterne(
					c.getId(),
					c.getCompteUtilisateurId(),
					c.getPrenom() + " " + c.getNom(),
					c.getMatricule(),
					d.getPeriodeDebut(),
					d.getPeriodeFin(),
					typeConge));
		}
		return resultat;
	}

	@Transactional(readOnly = true)
	public CollaborateurResumeInterne resumeParId(UUID id) {
		Collaborateur c = collaborateurRepository.findDetailById(id)
				.orElseThrow(() -> ReferentielMetierException.notFound("COLLABORATEUR_INTROUVABLE",
						"Collaborateur introuvable : " + id));
		return versResume(c);
	}

	@Transactional(readOnly = true)
	public CollaborateurResumeInterne resumeParCompte(UUID compteId) {
		Collaborateur c = collaborateurRepository.findByCompteUtilisateurId(compteId)
				.orElseThrow(() -> ReferentielMetierException.notFound("COLLABORATEUR_INTROUVABLE",
						"Collaborateur introuvable pour le compte : " + compteId));
		return versResume(c);
	}

	private ResultatResolution resoudreUneRegle(
			RegleResolutionRequest regle,
			Collaborateur demandeur,
			Set<UUID> beneficiaires,
			Set<UUID> exclusionsGlobales,
			List<Collaborateur> tousCollaborateurs,
			Map<UUID, Collaborateur> parId,
			Map<String, Fonction> fonctionsParCode,
			Map<String, List<FonctionAffectation>> affectationsParCode) {

		String type = regle.regle().trim().toUpperCase(Locale.ROOT);
		ParamsRegleResolution params = regle.params() != null ? regle.params() : ParamsRegleResolution.VIDE;
		List<Collaborateur> candidatsBruts = new ArrayList<>();
		String raisonInitiale = null;

		switch (type) {
			case "MANAGER_NOEUD" -> {
				Collaborateur valideur = SuperieurHierarchieRules.resoudreValideurM01(demandeur);
				if (valideur != null) {
					candidatsBruts.add(valideur);
				} else {
					raisonInitiale = raisonVideManagerNoeud(demandeur);
				}
			}
			case "MANAGER_NIVEAU" -> {
				Integer niveau = params.niveau();
				if (niveau == null || niveau < 1 || niveau > 4) {
					throw ReferentielMetierException.badRequest("DEFINITION_INVALIDE",
							"Le paramètre niveau de MANAGER_NIVEAU doit être compris entre 1 et 4.");
				}
				CandidatOuRaison cr = resoudreManagerNiveau(demandeur, niveau);
				if (cr.candidat() != null) {
					candidatsBruts.add(cr.candidat());
				} else {
					raisonInitiale = cr.raisonVide();
				}
			}
			case "RESPONSABLE_TYPE" -> {
				String typeNoeud = params.typeNoeud() == null ? "" : params.typeNoeud().trim().toUpperCase(Locale.ROOT);
				if (!TYPES_NOEUDS_RESPONSABLE.contains(typeNoeud)) {
					throw ReferentielMetierException.badRequest("DEFINITION_INVALIDE",
							"type_noeud invalide pour RESPONSABLE_TYPE : " + params.typeNoeud());
				}
				CandidatOuRaison cr = resoudreResponsableType(demandeur, typeNoeud);
				if (cr.candidat() != null) {
					candidatsBruts.add(cr.candidat());
				} else {
					raisonInitiale = cr.raisonVide();
				}
			}
			case "ROLE" -> {
				Set<String> rolesDemandes = validerEtNormaliserRoles(params.roles());
				for (Collaborateur c : tousCollaborateurs) {
					if (c.getProfilAcces() != null
							&& rolesDemandes.contains(c.getProfilAcces().trim().toUpperCase(Locale.ROOT))) {
						candidatsBruts.add(c);
					}
				}
				if (candidatsBruts.isEmpty()) {
					raisonInitiale = "AUCUN_TITULAIRE";
				}
			}
			case "FONCTION" -> {
				if (params.fonctionCode() == null || params.fonctionCode().isBlank()) {
					throw ReferentielMetierException.badRequest("DEFINITION_INVALIDE",
							"fonction_code est obligatoire pour la règle FONCTION.");
				}
				String portee = params.portee() == null || params.portee().isBlank()
						? "SOCIETE"
						: params.portee().trim().toUpperCase(Locale.ROOT);
				if (!PORTEES_FONCTION.contains(portee)) {
					throw ReferentielMetierException.badRequest("DEFINITION_INVALIDE",
							"portee invalide pour FONCTION : " + params.portee());
				}
				String code = params.fonctionCode().trim().toUpperCase(Locale.ROOT);
				Fonction f = fonctionsParCode.get(code);
				if (f == null || !f.isActif()) {
					return new ResultatResolution(List.of(), "FONCTION_INCONNUE");
				}
				boolean perimetreUnite = "UNITE_DEMANDEUR".equals(portee);
				List<FonctionAffectation> affs = affectationsParCode.getOrDefault(code, List.of());
				for (FonctionAffectation a : affs) {
					if (perimetreUnite && a.getUnitePerimetre() != null) {
						if (demandeur == null || demandeur.getUnite() == null
								|| !SuperieurHierarchieRules.descendDe(demandeur.getUnite(), a.getUnitePerimetre().getId())) {
							continue;
						}
					}
					candidatsBruts.add(a.getCollaborateur());
				}
				if (candidatsBruts.isEmpty()) {
					raisonInitiale = "AUCUN_TITULAIRE";
				}
			}
			case "COLLABORATEUR" -> {
				if (params.collaborateurId() == null) {
					throw ReferentielMetierException.badRequest("DEFINITION_INVALIDE",
							"collaborateur_id est obligatoire pour la règle COLLABORATEUR.");
				}
				Collaborateur c = parId.get(params.collaborateurId());
				if (c != null) {
					candidatsBruts.add(c);
				} else {
					raisonInitiale = "AUCUN_TITULAIRE";
				}
			}
			case "DEMANDEUR" -> {
				if (demandeur != null) {
					candidatsBruts.add(demandeur);
				} else {
					raisonInitiale = "AUCUN_TITULAIRE";
				}
			}
			default -> throw ReferentielMetierException.badRequest("DEFINITION_INVALIDE",
					"Type de règle de résolution inconnu : " + type);
		}

		if (candidatsBruts.isEmpty()) {
			return new ResultatResolution(List.of(),
					raisonInitiale != null ? raisonInitiale : "AUCUN_TITULAIRE");
		}

		// 1. Filtrage des collaborateurs actifs ayant un compte utilisateur
		List<Collaborateur> actifsAvecCompte = new ArrayList<>();
		for (Collaborateur c : candidatsBruts) {
			if (SuperieurHierarchieRules.estActif(c) && c.getCompteUtilisateurId() != null) {
				actifsAvecCompte.add(c);
			}
		}
		if (actifsAvecCompte.isEmpty()) {
			return new ResultatResolution(List.of(), "INACTIF");
		}

		// 2. Exclusions (I1 : demandeur et bénéficiaires exclus sauf règle DEMANDEUR + exclusions globales)
		Set<UUID> idsExclus = new HashSet<>(exclusionsGlobales);
		if (!"DEMANDEUR".equals(type)) {
			if (demandeur != null) {
				idsExclus.add(demandeur.getId());
			}
			idsExclus.addAll(beneficiaires);
		}

		Map<UUID, IntervenantResolu> retenus = new LinkedHashMap<>();
		for (Collaborateur c : actifsAvecCompte) {
			if (!idsExclus.contains(c.getId())) {
				String intitule = c.getPosteLibelle() != null && !c.getPosteLibelle().isBlank()
						? c.getPosteLibelle()
						: c.getFonction();
				retenus.putIfAbsent(c.getId(), new IntervenantResolu(
						c.getId(),
						c.getCompteUtilisateurId(),
						c.getPrenom() + " " + c.getNom(),
						intitule));
			}
		}
		if (retenus.isEmpty()) {
			return new ResultatResolution(List.of(), "TOUS_EXCLUS");
		}
		return new ResultatResolution(List.copyOf(retenus.values()), null);
	}

	private static Set<String> validerEtNormaliserRoles(List<String> roles) {
		if (roles == null || roles.isEmpty()) {
			throw ReferentielMetierException.badRequest("DEFINITION_INVALIDE",
					"La règle ROLE exige au moins un rôle parmi RH et ADMIN.");
		}
		Set<String> resultat = new HashSet<>();
		for (String r : roles) {
			if (r == null || r.isBlank()) {
				throw ReferentielMetierException.badRequest("DEFINITION_INVALIDE",
						"Rôle vide non autorisé dans la règle ROLE.");
			}
			String normalise = r.trim().toUpperCase(Locale.ROOT);
			if (!ROLES_AUTORISES.contains(normalise)) {
				throw ReferentielMetierException.badRequest("DEFINITION_INVALIDE",
						"Rôle non autorisé pour la règle ROLE : " + r + " (seuls RH et ADMIN sont acceptés).");
			}
			resultat.add(normalise);
		}
		return resultat;
	}

	private static String raisonVideManagerNoeud(Collaborateur demandeur) {
		if (demandeur == null || demandeur.getUnite() == null) {
			return "AUCUN_MANAGER";
		}
		Collaborateur managerNoeud = SuperieurHierarchieRules.managerActif(demandeur.getUnite());
		if (managerNoeud != null && managerNoeud.getId().equals(demandeur.getId())) {
			return "CHAINE_TERMINEE";
		}
		return "AUCUN_MANAGER";
	}

	private record CandidatOuRaison(Collaborateur candidat, String raisonVide) {
	}

	private static CandidatOuRaison resoudreManagerNiveau(Collaborateur demandeur, int niveau) {
		Collaborateur niveau1 = SuperieurHierarchieRules.resoudreValideurM01(demandeur);
		if (niveau1 == null) {
			return new CandidatOuRaison(null, raisonVideManagerNoeud(demandeur));
		}
		if (niveau == 1) {
			return new CandidatOuRaison(niveau1, null);
		}
		UniteOrganisation curseur = trouverNoeudDeManagerDansChaine(demandeur.getUnite(), niveau1.getId());
		Collaborateur courant = niveau1;
		for (int k = 2; k <= niveau; k++) {
			Collaborateur suivant = null;
			for (UniteOrganisation u = curseur != null ? curseur.getParent() : null; u != null; u = u.getParent()) {
				Collaborateur m = SuperieurHierarchieRules.managerActif(u);
				if (m != null && !m.getId().equals(courant.getId()) && !m.getId().equals(demandeur.getId())) {
					suivant = m;
					curseur = u;
					break;
				}
			}
			if (suivant == null) {
				return new CandidatOuRaison(null, "CHAINE_TERMINEE");
			}
			courant = suivant;
		}
		return new CandidatOuRaison(courant, null);
	}

	private static UniteOrganisation trouverNoeudDeManagerDansChaine(UniteOrganisation depart, UUID managerId) {
		for (UniteOrganisation u = depart; u != null; u = u.getParent()) {
			if (u.getManager() != null && managerId.equals(u.getManager().getId())) {
				return u;
			}
		}
		return depart;
	}

	private static CandidatOuRaison resoudreResponsableType(Collaborateur demandeur, String typeNoeud) {
		if (demandeur == null || demandeur.getUnite() == null) {
			return new CandidatOuRaison(null, "AUCUN_MANAGER");
		}
		for (UniteOrganisation u = demandeur.getUnite(); u != null; u = u.getParent()) {
			if (u.getTypeNoeud() != null && u.getTypeNoeud().equalsIgnoreCase(typeNoeud)) {
				Collaborateur m = SuperieurHierarchieRules.managerActif(u);
				if (m == null || m.getId().equals(demandeur.getId())) {
					return new CandidatOuRaison(null, "AUCUN_MANAGER");
				}
				return new CandidatOuRaison(m, null);
			}
		}
		return new CandidatOuRaison(null, "AUCUN_MANAGER");
	}

	private static ContexteDemandeur construireContexteDemandeur(
			Collaborateur demandeur,
			LocalDate dateRef,
			List<UniteOrganisation> toutesUnites) {
		int ancienneteMois = 0;
		if (demandeur.getDateRecrutement() != null && !demandeur.getDateRecrutement().isAfter(dateRef)) {
			Period p = Period.between(demandeur.getDateRecrutement(), dateRef);
			ancienneteMois = p.getYears() * 12 + p.getMonths();
		}
		boolean estManager = false;
		for (UniteOrganisation u : toutesUnites) {
			if (u.isActif() && u.getManager() != null && demandeur.getId().equals(u.getManager().getId())) {
				estManager = true;
				break;
			}
		}
		String typeNoeud = demandeur.getUnite() != null ? demandeur.getUnite().getTypeNoeud() : null;
		return new ContexteDemandeur(demandeur.getProfilAcces(), typeNoeud, ancienneteMois, estManager);
	}

	private static CollaborateurResumeInterne versResume(Collaborateur c) {
		UniteOrganisation u = c.getUnite();
		return new CollaborateurResumeInterne(
				c.getId(),
				c.getCompteUtilisateurId(),
				c.getPrenom() + " " + c.getNom(),
				c.getMatricule(),
				c.getStatut(),
				u != null ? u.getId() : null,
				u != null ? u.getCode() : null,
				u != null ? u.getTypeNoeud() : null,
				c.getFamilleMetierCode(),
				c.getNiveauSeniorite());
	}
}
