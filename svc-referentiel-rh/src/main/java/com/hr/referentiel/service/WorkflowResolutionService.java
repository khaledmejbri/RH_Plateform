package com.hr.referentiel.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.SocieteParametresDtos.SocieteParametresResponse;
import com.hr.referentiel.dto.WorkflowInterneDtos.AbsenceCollaborateurInterne;
import com.hr.referentiel.dto.WorkflowInterneDtos.CalendrierSocieteInterneResponse;
import com.hr.referentiel.dto.WorkflowInterneDtos.CollaborateurResumeInterne;
import com.hr.referentiel.dto.WorkflowInterneDtos.IntervenantResolu;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Résolution groupée d'intervenants et lectures internes pour {@code svc-workflow} (§ 9.5.3).
 * Précharge l'arbre des unités, les collaborateurs et les fonctions en lot (0 requête N+1).
 */
@Service
public class WorkflowResolutionService {

	private static final int MAX_REGLES = 50;

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
		LocalDate dateRef = req.dateReference() != null ? req.dateReference() : LocalDate.now(clock);

		// Préchargement unique pour éviter tout N+1
		uniteRepository.findAll();
		List<Collaborateur> tousCollaborateurs = collaborateurRepository.findAllWithUnite();
		Map<UUID, Collaborateur> parId = new HashMap<>(tousCollaborateurs.size() * 2);
		for (Collaborateur c : tousCollaborateurs) {
			parId.put(c.getId(), c);
		}

		Collaborateur demandeur = req.demandeurCollaborateurId() != null
				? parId.get(req.demandeurCollaborateurId())
				: null;

		Set<String> codesFonctions = new HashSet<>();
		for (RegleResolutionRequest r : req.regles()) {
			if ("FONCTION".equalsIgnoreCase(r.type()) && r.fonctionCode() != null && !r.fonctionCode().isBlank()) {
				codesFonctions.add(r.fonctionCode().trim().toUpperCase(Locale.ROOT));
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

		Set<UUID> beneficiaires = req.beneficiaireCollaborateurIds() == null
				? Set.of()
				: new HashSet<>(req.beneficiaireCollaborateurIds());

		List<ResultatResolution> resultats = new ArrayList<>(req.regles().size());
		for (RegleResolutionRequest regle : req.regles()) {
			resultats.add(resoudreUneRegle(
					regle, demandeur, beneficiaires, tousCollaborateurs, parId,
					fonctionsParCode, affectationsParCode));
		}

		return new ResolutionLotResponse(
				demandeur != null ? versResume(demandeur) : null,
				resultats);
	}

	@Transactional
	public CalendrierSocieteInterneResponse calendrier(LocalDate du, LocalDate au) {
		UUID societeId = SocieteContexte.courante();
		SocieteParametresResponse params = societeParametresService.obtenir();
		String fuseau = identiteSocieteClient.trouverParId(societeId)
				.map(SocieteResume::fuseau)
				.filter(f -> f != null && !f.isBlank())
				.orElse("Africa/Tunis");
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
			List<Collaborateur> tousCollaborateurs,
			Map<UUID, Collaborateur> parId,
			Map<String, Fonction> fonctionsParCode,
			Map<String, List<FonctionAffectation>> affectationsParCode) {

		String type = regle.type().trim().toUpperCase(Locale.ROOT);
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
				int niveau = regle.niveau() == null || regle.niveau() < 1 ? 1 : regle.niveau();
				CandidatOuRaison cr = resoudreManagerNiveau(demandeur, niveau);
				if (cr.candidat() != null) {
					candidatsBruts.add(cr.candidat());
				} else {
					raisonInitiale = cr.raisonVide();
				}
			}
			case "RESPONSABLE_TYPE" -> {
				CandidatOuRaison cr = resoudreResponsableType(demandeur, regle.typeUnite());
				if (cr.candidat() != null) {
					candidatsBruts.add(cr.candidat());
				} else {
					raisonInitiale = cr.raisonVide();
				}
			}
			case "ROLE" -> {
				String roleNormalise = normaliserRole(regle.role());
				for (Collaborateur c : tousCollaborateurs) {
					if (possedeProfil(c, roleNormalise)) {
						candidatsBruts.add(c);
					}
				}
				if (candidatsBruts.isEmpty()) {
					raisonInitiale = "AUCUN_TITULAIRE";
				}
			}
			case "FONCTION" -> {
				String code = regle.fonctionCode() == null ? "" : regle.fonctionCode().trim().toUpperCase(Locale.ROOT);
				Fonction f = fonctionsParCode.get(code);
				if (f == null || !f.isActif()) {
					return new ResultatResolution(regle.cle(), List.of(), "FONCTION_INCONNUE");
				}
				boolean perimetreUnite = "UNITE_DEMANDEUR".equalsIgnoreCase(regle.perimetre());
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
				Collaborateur c = regle.collaborateurId() != null ? parId.get(regle.collaborateurId()) : null;
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
			default -> throw ReferentielMetierException.badRequest("CORPS_INVALIDE",
					"Type de règle de résolution inconnu : " + type);
		}

		if (candidatsBruts.isEmpty()) {
			return new ResultatResolution(regle.cle(), List.of(),
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
			return new ResultatResolution(regle.cle(), List.of(), "INACTIF");
		}

		// 2. Exclusions (invariant I1 : demandeur et bénéficiaires exclus sauf règle DEMANDEUR sans exclusion explicite)
		Set<UUID> idsExclus = construireExclusions(type, regle.exclure(), demandeur, beneficiaires);
		Map<UUID, IntervenantResolu> retenus = new LinkedHashMap<>();
		for (Collaborateur c : actifsAvecCompte) {
			if (!idsExclus.contains(c.getId())) {
				retenus.putIfAbsent(c.getId(), new IntervenantResolu(
						c.getId(),
						c.getCompteUtilisateurId(),
						c.getPrenom() + " " + c.getNom(),
						c.getMatricule()));
			}
		}
		if (retenus.isEmpty()) {
			return new ResultatResolution(regle.cle(), List.of(), "TOUS_EXCLUS");
		}
		return new ResultatResolution(regle.cle(), List.copyOf(retenus.values()), null);
	}

	private static String raisonVideManagerNoeud(Collaborateur demandeur) {
		if (demandeur == null || demandeur.getUnite() == null) {
			return "AUCUN_MANAGER";
		}
		UniteOrganisation unite = demandeur.getUnite();
		Collaborateur managerNoeud = SuperieurHierarchieRules.managerActif(unite);
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
		// Trouver le nœud dont niveau1 est manager dans la chaîne du demandeur
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

	private static CandidatOuRaison resoudreResponsableType(Collaborateur demandeur, String typeUnite) {
		if (demandeur == null || demandeur.getUnite() == null || typeUnite == null || typeUnite.isBlank()) {
			return new CandidatOuRaison(null, "AUCUN_MANAGER");
		}
		String cible = typeUnite.trim();
		for (UniteOrganisation u = demandeur.getUnite(); u != null; u = u.getParent()) {
			if (u.getTypeNoeud() != null && u.getTypeNoeud().equalsIgnoreCase(cible)) {
				Collaborateur m = u.getManager();
				if (m == null) {
					return new CandidatOuRaison(null, "AUCUN_MANAGER");
				}
				return new CandidatOuRaison(m, null);
			}
		}
		return new CandidatOuRaison(null, "AUCUN_MANAGER");
	}

	private static String normaliserRole(String role) {
		if (role == null) {
			return "";
		}
		String r = role.trim().toUpperCase(Locale.ROOT);
		return r.startsWith("ROLE_") ? r.substring(5) : r;
	}

	private static boolean possedeProfil(Collaborateur c, String roleNormalise) {
		if (c.getProfilAcces() == null || roleNormalise.isEmpty()) {
			return false;
		}
		String profil = c.getProfilAcces().trim().toUpperCase(Locale.ROOT);
		if (Objects.equals(profil, roleNormalise)) {
			return true;
		}
		if ("RO".equals(roleNormalise) && "RESPONSABLE".equals(profil)) {
			return true;
		}
		return "USER".equals(roleNormalise) && "COLLABORATEUR".equals(profil);
	}

	private static Set<UUID> construireExclusions(
			String typeRegle,
			List<String> exclure,
			Collaborateur demandeur,
			Set<UUID> beneficiaires) {
		Set<UUID> exclus = new HashSet<>();
		Set<String> directives = new HashSet<>();
		if (exclure != null) {
			for (String e : exclure) {
				if (e != null) {
					directives.add(e.trim().toUpperCase(Locale.ROOT));
				}
			}
		}
		boolean estRegleDemandeur = "DEMANDEUR".equals(typeRegle);
		if ((!estRegleDemandeur || directives.contains("DEMANDEUR")) && demandeur != null) {
			exclus.add(demandeur.getId());
		}
		if (!estRegleDemandeur || directives.contains("BENEFICIAIRES")) {
			exclus.addAll(beneficiaires);
		}
		return exclus;
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
