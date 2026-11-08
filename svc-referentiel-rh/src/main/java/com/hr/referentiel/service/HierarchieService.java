package com.hr.referentiel.service;

import com.hr.referentiel.domain.ActionWorkflowAdministratif;
import com.hr.referentiel.domain.TypeNoeudOrganisation;
import com.hr.referentiel.dto.FicheAnnuaireResponse;
import com.hr.referentiel.dto.FicheEquipeResponse;
import com.hr.referentiel.dto.FicheUniteResponse;
import com.hr.referentiel.dto.HierarchieResponse;
import com.hr.referentiel.dto.PersonneResume;
import com.hr.referentiel.dto.UniteResume;
import com.hr.referentiel.dto.ValideurControleResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdminWorkflowHistoryRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fiches de détail en lecture (lot 2 UAT, D-N01 / D-N02) : hiérarchie d'un collaborateur, fiche d'une unité,
 * annuaire, équipe d'un responsable. Lecture seule ; tout est renvoyé avec noms, matricules et libellés (D-L01.a).
 * Le périmètre « équipe » est celui du valideur D-H03 ({@link SuperieurHierarchieRules#resoudreValideurM01}), calculé
 * ici, jamais fourni par le client.
 */
@Service
public class HierarchieService {

	private static final Comparator<PersonneResume> PAR_NOM = Comparator.comparing(
			p -> p.nomComplet() != null ? p.nomComplet().toLowerCase() : "");

	private static final List<ActionWorkflowAdministratif> DECISIONS_RO =
			List.of(ActionWorkflowAdministratif.VALIDATION_RO, ActionWorkflowAdministratif.REFUS_RO);

	private final CollaborateurRepository collaborateurRepository;
	private final UniteOrganisationRepository uniteRepository;
	private final CollaborateurConnecteService collaborateurConnecteService;
	private final DemandeAdministrativeRhRepository demandeRepository;
	private final DemandeAdminWorkflowHistoryRepository historiqueRepository;

	public HierarchieService(CollaborateurRepository collaborateurRepository,
			UniteOrganisationRepository uniteRepository,
			CollaborateurConnecteService collaborateurConnecteService,
			DemandeAdministrativeRhRepository demandeRepository,
			DemandeAdminWorkflowHistoryRepository historiqueRepository) {
		this.collaborateurRepository = collaborateurRepository;
		this.uniteRepository = uniteRepository;
		this.collaborateurConnecteService = collaborateurConnecteService;
		this.demandeRepository = demandeRepository;
		this.historiqueRepository = historiqueRepository;
	}

	// ─── Hiérarchie (A1, A7) ───────────────────────────────────────────────────

	/** Lecture back-office : 404 si la fiche n'existe pas. */
	@Transactional(readOnly = true)
	public HierarchieResponse hierarchieDe(UUID collaborateurId) {
		Collaborateur c = collaborateurRepository.findDetailById(collaborateurId)
				.orElseThrow(() -> ReferentielMetierException.notFound(
						"COLLABORATEUR_INTROUVABLE", "Collaborateur introuvable."));
		return construireHierarchie(c);
	}

	/** « Mon dossier » : identité lue dans le JWT ; compte sans fiche → 403. */
	@Transactional(readOnly = true)
	public HierarchieResponse maHierarchie(Jwt jwt) {
		Collaborateur moi = collaborateurConnecteService.exigerCollaborateur(jwt);
		return construireHierarchie(chargerDetail(moi));
	}

	private HierarchieResponse construireHierarchie(Collaborateur c) {
		Fabrique f = new Fabrique(uniteRepository.findByActifTrueWithParentAndManager());
		Collaborateur responsable = SuperieurHierarchieRules.resoudreSuperieurFiche(c, c.getUnite());
		Collaborateur valideur = SuperieurHierarchieRules.resoudreValideurM01(c);
		List<UniteResume> manages = uniteRepository.findByManagerId(c.getId()).stream()
				.filter(UniteOrganisation::isActif)
				.sorted(Comparator.comparingInt(HierarchieService::niveau))
				.map(Fabrique::unite)
				.toList();
		return new HierarchieResponse(
				f.personne(c),
				chemin(c.getUnite()).stream().map(Fabrique::unite).toList(),
				responsable != null ? f.personne(responsable) : null,
				valideur != null ? f.personne(valideur) : null,
				valideur == null,
				manages,
				encadresPar(c.getId()).stream().map(f::personne).sorted(PAR_NOM).toList());
	}

	// ─── Fiche d'une unité (A2) ────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public FicheUniteResponse ficheUnite(UUID uniteId) {
		UniteOrganisation u = uniteRepository.findById(uniteId)
				.orElseThrow(() -> ReferentielMetierException.notFound("UNITE_INTROUVABLE", "Unité introuvable."));
		Fabrique f = new Fabrique(uniteRepository.findByActifTrueWithParentAndManager());
		List<Collaborateur> actifs = collaborateurRepository.findAllActifsWithUnite();

		Map<UUID, Integer> total = new HashMap<>();
		Map<UUID, Integer> direct = new HashMap<>();
		for (Collaborateur c : actifs) {
			direct.merge(c.getUnite().getId(), 1, Integer::sum);
			for (UniteOrganisation a : ancetresEtSoi(c.getUnite())) {
				total.merge(a.getId(), 1, Integer::sum);
			}
		}

		Collaborateur manager = u.getManager();
		List<FicheUniteResponse.SousUnite> sousUnites = uniteRepository.findByParentId(u.getId()).stream()
				.filter(UniteOrganisation::isActif)
				.sorted(Comparator.comparing(UniteOrganisation::getCode, String.CASE_INSENSITIVE_ORDER))
				.map(s -> new FicheUniteResponse.SousUnite(Fabrique.unite(s), total.getOrDefault(s.getId(), 0)))
				.toList();
		List<PersonneResume> membres = actifs.stream()
				.filter(c -> c.getUnite().getId().equals(u.getId()))
				.map(f::personne)
				.sorted(PAR_NOM)
				.toList();
		TypeNoeudOrganisation type = TypeNoeudOrganisation.depuisLibelle(u.getTypeNoeud()).orElse(null);
		boolean aRattacher = u.getParent() == null
				&& (type == TypeNoeudOrganisation.DEPARTEMENT || type == TypeNoeudOrganisation.SERVICE);
		return new FicheUniteResponse(
				Fabrique.unite(u),
				u.getCode(),
				u.isActif(),
				aRattacher,
				chemin(u).stream().map(Fabrique::unite).toList(),
				u.getParent() != null ? Fabrique.unite(u.getParent()) : null,
				manager != null ? f.personne(manager) : null,
				sousUnites,
				membres,
				direct.getOrDefault(u.getId(), 0),
				total.getOrDefault(u.getId(), 0));
	}

	// ─── Annuaire et équipe (A8 à A10) ─────────────────────────────────────────

	/** Fiche annuaire d'un collaborateur actif. Inconnu ou inactif : 404 (Q7). */
	@Transactional(readOnly = true)
	public FicheAnnuaireResponse annuaire(UUID collaborateurId) {
		Collaborateur c = collaborateurRepository.findDetailById(collaborateurId)
				.filter(SuperieurHierarchieRules::estActif)
				.orElseThrow(() -> ReferentielMetierException.notFound(
						"COLLABORATEUR_INTROUVABLE", "Collaborateur introuvable."));
		return versAnnuaire(c, new Fabrique(uniteRepository.findByActifTrueWithParentAndManager()));
	}

	/** Collaborateurs actifs dont l'appelant (identité JWT) est le valideur D-H03. */
	@Transactional(readOnly = true)
	public List<PersonneResume> monEquipe(Jwt jwt) {
		Collaborateur moi = collaborateurConnecteService.exigerCollaborateur(jwt);
		Fabrique f = new Fabrique(uniteRepository.findByActifTrueWithParentAndManager());
		return encadresPar(moi.getId()).stream().map(f::personne).sorted(PAR_NOM).toList();
	}

	/**
	 * Fiche d'un membre de l'équipe : annuaire + demandes M01 dont l'appelant est le valideur attendu ou a décidé.
	 * Hors périmètre (y compris collaborateur inconnu ou inactif) : 403 {@code HORS_PERIMETRE}, sans distinguer.
	 */
	@Transactional(readOnly = true)
	public FicheEquipeResponse ficheEquipe(UUID collaborateurId, Jwt jwt) {
		Collaborateur moi = collaborateurConnecteService.exigerCollaborateur(jwt);
		Collaborateur membre = collaborateurRepository.findDetailById(collaborateurId)
				.filter(SuperieurHierarchieRules::estActif)
				.filter(c -> estValideurDe(moi.getId(), c))
				.orElseThrow(() -> new ReferentielMetierException(
						"HORS_PERIMETRE", "Ce collaborateur ne fait pas partie de votre équipe.", 403));
		Set<UUID> acteurs = new HashSet<>();
		acteurs.add(moi.getId());
		if (moi.getCompteUtilisateurId() != null) {
			acteurs.add(moi.getCompteUtilisateurId());
		}
		List<FicheEquipeResponse.DemandeEquipe> demandes = demandeRepository
				.findByDemandeurIdOrderByCreeLeDesc(membre.getId()).stream()
				.filter(d -> (d.getValideurAttendu() != null && d.getValideurAttendu().getId().equals(moi.getId()))
						|| historiqueRepository.existsByDemandeAdministrativeIdAndActionInAndActeurIdentifiantIn(
								d.getId(), DECISIONS_RO, acteurs))
				.map(HierarchieService::versDemandeEquipe)
				.toList();
		return new FicheEquipeResponse(
				versAnnuaire(membre, new Fabrique(uniteRepository.findByActifTrueWithParentAndManager())), demandes);
	}

	// ─── Contrôle inter-services (A11) ─────────────────────────────────────────

	/**
	 * Le compte {@code compteId} est-il le valideur D-H03 du collaborateur ? Échec fermé : inconnu, inactif ou
	 * compte sans fiche → {@code est_valideur} faux.
	 */
	@Transactional(readOnly = true)
	public ValideurControleResponse controlerValideur(UUID collaborateurId, UUID compteId) {
		Collaborateur cible = collaborateurRepository.findDetailById(collaborateurId).orElse(null);
		boolean actif = cible != null && SuperieurHierarchieRules.estActif(cible);
		Collaborateur valideur = actif ? SuperieurHierarchieRules.resoudreValideurM01(cible) : null;
		Collaborateur appelant = compteId != null
				? collaborateurRepository.findByCompteUtilisateurId(compteId).orElse(null) : null;
		boolean estValideur = actif && valideur != null && appelant != null
				&& SuperieurHierarchieRules.estActif(appelant) && valideur.getId().equals(appelant.getId());
		return new ValideurControleResponse(estValideur, actif, valideur != null ? valideur.getId() : null);
	}

	// ─── Helpers ───────────────────────────────────────────────────────────────

	private Collaborateur chargerDetail(Collaborateur c) {
		return collaborateurRepository.findDetailById(c.getId()).orElse(c);
	}

	private List<Collaborateur> encadresPar(UUID ficheId) {
		return collaborateurRepository.findAllActifsWithUnite().stream()
				.filter(c -> estValideurDe(ficheId, c))
				.toList();
	}

	private static boolean estValideurDe(UUID ficheId, Collaborateur c) {
		Collaborateur v = SuperieurHierarchieRules.resoudreValideurM01(c);
		return v != null && v.getId().equals(ficheId);
	}

	private FicheAnnuaireResponse versAnnuaire(Collaborateur c, Fabrique f) {
		Collaborateur resp = SuperieurHierarchieRules.resoudreSuperieurFiche(c, c.getUnite());
		PersonneResume p = f.personne(c);
		return new FicheAnnuaireResponse(
				c.getId(), p.nomComplet(), c.getPosteLibelle(), p.intituleRole(),
				c.getUnite() != null ? c.getUnite().getId() : null,
				c.getUnite() != null ? c.getUnite().getLibelle() : null,
				resp != null ? new FicheAnnuaireResponse.Responsable(resp.getId(), nomComplet(resp)) : null,
				c.getCourrielProfessionnel());
	}

	private static FicheEquipeResponse.DemandeEquipe versDemandeEquipe(DemandeAdministrativeRh d) {
		return new FicheEquipeResponse.DemandeEquipe(
				d.getId(), d.getNumero(),
				d.getTypeDemande().name(), d.getTypeDemande().getLibelleDemande(),
				d.getStatut().name(), d.getStatut().getLibelle(),
				d.getPeriodeDebut(), d.getPeriodeFin(), d.getCreeLe());
	}

	/** Nœuds de la DG jusqu'à l'unité donnée (l'unité comprise). */
	static List<UniteOrganisation> chemin(UniteOrganisation unite) {
		List<UniteOrganisation> liste = ancetresEtSoi(unite);
		Collections.reverse(liste);
		return liste;
	}

	/** L'unité puis ses parents, jusqu'à la racine (protégé contre un cycle). */
	private static List<UniteOrganisation> ancetresEtSoi(UniteOrganisation unite) {
		List<UniteOrganisation> liste = new ArrayList<>();
		Set<UUID> vus = new HashSet<>();
		for (UniteOrganisation u = unite; u != null && vus.add(u.getId()); u = u.getParent()) {
			liste.add(u);
		}
		return liste;
	}

	private static int niveau(UniteOrganisation u) {
		return TypeNoeudOrganisation.depuisLibelle(u.getTypeNoeud()).map(TypeNoeudOrganisation::getNiveau)
				.orElse(Integer.MAX_VALUE);
	}

	static String nomComplet(Collaborateur c) {
		String complet = ((c.getPrenom() != null ? c.getPrenom() : "") + " " + (c.getNom() != null ? c.getNom() : ""))
				.trim();
		return complet.isEmpty() ? null : complet;
	}

	/**
	 * Construit les résumés avec l'intitulé du rôle (D-H06) : titre du nœud le plus haut que la personne manage,
	 * sinon libellé du type de ce nœud, sinon son poste. Un seul chargement des nœuds par requête.
	 */
	static final class Fabrique {

		private final Map<UUID, UniteOrganisation> noeudPrincipal = new HashMap<>();

		Fabrique(List<UniteOrganisation> unitesActives) {
			for (UniteOrganisation u : unitesActives) {
				Collaborateur m = u.getManager();
				if (m == null) {
					continue;
				}
				UniteOrganisation actuel = noeudPrincipal.get(m.getId());
				if (actuel == null || niveau(u) < niveau(actuel)) {
					noeudPrincipal.put(m.getId(), u);
				}
			}
		}

		PersonneResume personne(Collaborateur c) {
			UniteOrganisation u = c.getUnite();
			return new PersonneResume(
					c.getId(), nomComplet(c), c.getMatricule(), c.getPosteLibelle(), intitule(c),
					u != null ? u.getId() : null, u != null ? u.getLibelle() : null,
					SuperieurHierarchieRules.estActif(c));
		}

		private String intitule(Collaborateur c) {
			UniteOrganisation n = noeudPrincipal.get(c.getId());
			if (n != null) {
				if (n.getTitrePoste() != null && !n.getTitrePoste().isBlank()) {
					return n.getTitrePoste().trim();
				}
				TypeNoeudOrganisation type = TypeNoeudOrganisation.depuisLibelle(n.getTypeNoeud()).orElse(null);
				if (type != null) {
					return switch (type) {
						case DIRECTION_GENERALE -> "Directeur général";
						case DIRECTION -> "Directeur";
						case DEPARTEMENT -> "Chef de département";
						case SERVICE -> "Chef de service";
					};
				}
			}
			return c.getPosteLibelle();
		}

		static UniteResume unite(UniteOrganisation u) {
			TypeNoeudOrganisation type = TypeNoeudOrganisation.depuisLibelle(u.getTypeNoeud()).orElse(null);
			return new UniteResume(
					u.getId(), u.getLibelle(),
					type != null ? type.name() : u.getTypeNoeud(),
					type != null ? type.getLibelle() : u.getTypeNoeud());
		}
	}
}
