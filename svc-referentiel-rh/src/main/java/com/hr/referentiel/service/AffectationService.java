package com.hr.referentiel.service;

import com.hr.referentiel.domain.JourSemaine;
import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeNoeudOrganisation;
import com.hr.referentiel.dto.AffectationCollaborateurResponse;
import com.hr.referentiel.dto.CalendrierPresenceResponse;
import com.hr.referentiel.dto.CalendrierPresenceResponse.CongeApprouveItem;
import com.hr.referentiel.dto.MembreUniteResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import com.hr.referentiel.entity.SocieteParametres;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.SocieteParametresRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Affectation actuelle des collaborateurs pour la vue « Temps de travail » de svc-presence (D-P17.g) : filtre par
 * unité (sous-unités comprises) et libellés direction / département / service. Lecture seule, tous statuts confondus
 * (l'historique d'un collaborateur inactif reste visible), exposée par les endpoints {@code /internal} uniquement.
 */
@Service
public class AffectationService {

	/** Nombre maximal d'identifiants par appel de résolution. */
	public static final int RESOLUTION_MAX = 100;
	/** Fenêtre maximale en jours pour le calendrier de présence (27 mois M-1 -> 26 mois M <= 31 jours, plafond 62). */
	public static final int FENETRE_CALENDRIER_MAX_JOURS = 62;

	private final CollaborateurRepository collaborateurRepository;
	private final DemandeAdministrativeRhRepository demandeRepository;
	private final SocieteParametresRepository societeParametresRepository;
	private final JourFerieService jourFerieService;

	public AffectationService(CollaborateurRepository collaborateurRepository) {
		this(collaborateurRepository, null, null, null);
	}

	@Autowired
	public AffectationService(
			CollaborateurRepository collaborateurRepository,
			DemandeAdministrativeRhRepository demandeRepository,
			SocieteParametresRepository societeParametresRepository,
			JourFerieService jourFerieService) {
		this.collaborateurRepository = collaborateurRepository;
		this.demandeRepository = demandeRepository;
		this.societeParametresRepository = societeParametresRepository;
		this.jourFerieService = jourFerieService;
	}

	/** Collaborateurs dont l'unité est {@code uniteId} ou en descend. Unité inconnue : liste vide. */
	@Transactional(readOnly = true)
	public List<MembreUniteResponse> membresDeUnite(UUID uniteId) {
		return collaborateurRepository.findAllWithUnite().stream()
				.filter(c -> descendDe(c.getUnite(), uniteId))
				.map(c -> new MembreUniteResponse(c.getId(), c.getCompteUtilisateurId()))
				.toList();
	}

	/**
	 * Affectation de collaborateurs désignés par leur fiche <em>ou</em> leur compte (un pointage ancien porte
	 * l'identifiant du compte). Un identifiant inconnu est absent du résultat ; une fiche n'apparaît qu'une fois.
	 */
	@Transactional(readOnly = true)
	public List<AffectationCollaborateurResponse> affectations(Collection<UUID> identifiants) {
		Map<UUID, Collaborateur> trouves = new LinkedHashMap<>();
		collaborateurRepository.findAllById(identifiants).forEach(c -> trouves.put(c.getId(), c));
		collaborateurRepository.findAllByCompteUtilisateurIdIn(identifiants).forEach(c -> trouves.putIfAbsent(c.getId(), c));
		return trouves.values().stream().map(AffectationService::versReponse).toList();
	}

	/**
	 * Calendrier de présence pour l'export Temps de travail : jours travaillés de la société, jours fériés actifs dans
	 * {@code [du, au]} et congés approuvés ({@code CONGE} / {@code APPROUVEE}) des collaborateurs demandés.
	 */
	@Transactional(readOnly = true)
	public CalendrierPresenceResponse calendrierPresence(Collection<UUID> collaborateurIds, LocalDate du, LocalDate au) {
		List<String> joursTravailles = societeParametresRepository != null
				? societeParametresRepository.findFirstByOrderByIdAsc()
						.map(SocieteParametres::getJoursTravailles)
						.filter(j -> j != null && !j.isEmpty())
						.orElse(JourSemaine.DEFAUT_CODES)
				: JourSemaine.DEFAUT_CODES;

		List<LocalDate> feries = jourFerieService != null
				? jourFerieService.datesActives().stream()
						.filter(d -> !d.isBefore(du) && !d.isAfter(au))
						.sorted()
						.toList()
				: List.of();

		List<CongeApprouveItem> conges = new ArrayList<>();
		if (demandeRepository != null && collaborateurIds != null && !collaborateurIds.isEmpty()) {
			List<DemandeAdministrativeRh> demandes = demandeRepository.findCongesApprouvesSurPeriode(
					collaborateurIds,
					TypeDemandeAdministrativeRh.CONGE,
					StatutDemandeAdministrativeRh.APPROUVEE,
					du,
					au);
			for (DemandeAdministrativeRh d : demandes) {
				Map<String, Object> contenu = d.getContenu();
				String typeConge = valeurTexte(contenu, "type_conge", "ANNUEL");
				String periodePremier = valeurTexte(contenu, "periode_premier_jour", "JOURNEE_ENTIERE");
				String periodeDernier = valeurTexte(contenu, "periode_dernier_jour", "JOURNEE_ENTIERE");
				conges.add(new CongeApprouveItem(
						d.getDemandeur().getId(),
						d.getPeriodeDebut(),
						d.getPeriodeFin(),
						typeConge,
						periodePremier,
						periodeDernier));
			}
		}

		return new CalendrierPresenceResponse(List.copyOf(joursTravailles), feries, conges);
	}

	private static String valeurTexte(Map<String, Object> contenu, String cle, String defaut) {
		if (contenu == null) {
			return defaut;
		}
		Object v = contenu.get(cle);
		if (v == null) {
			return defaut;
		}
		String s = String.valueOf(v).trim();
		return s.isEmpty() ? defaut : s;
	}

	private static boolean descendDe(UniteOrganisation unite, UUID uniteId) {
		Set<UUID> vus = new HashSet<>();
		for (UniteOrganisation u = unite; u != null && vus.add(u.getId()); u = u.getParent()) {
			if (uniteId.equals(u.getId())) {
				return true;
			}
		}
		return false;
	}

	private static AffectationCollaborateurResponse versReponse(Collaborateur c) {
		String departement = libelleDuType(c.getUnite(), TypeNoeudOrganisation.DEPARTEMENT);
		if (departement == null && c.getDepartementLibelle() != null && !c.getDepartementLibelle().isBlank()) {
			departement = c.getDepartementLibelle();
		}
		return new AffectationCollaborateurResponse(
				c.getId(),
				c.getCompteUtilisateurId(),
				c.getMatricule(),
				nomComplet(c),
				libelleDuType(c.getUnite(), TypeNoeudOrganisation.DIRECTION),
				departement,
				libelleDuType(c.getUnite(), TypeNoeudOrganisation.SERVICE),
				SuperieurHierarchieRules.estActif(c),
				c.getPosteLibelle(),
				c.getUnite() != null ? c.getUnite().getId() : null,
				c.getUnite() != null ? c.getUnite().getLibelle() : null,
				c.getPrenom(),
				c.getNom(),
				c.getFonction(),
				c.getQualite(),
				c.getQualificationAffectation(),
				c.getAffectation());
	}

	/** Premier nœud du type voulu en remontant depuis l'unité du collaborateur (lui compris). */
	private static String libelleDuType(UniteOrganisation unite, TypeNoeudOrganisation type) {
		Set<UUID> vus = new HashSet<>();
		for (UniteOrganisation u = unite; u != null && vus.add(u.getId()); u = u.getParent()) {
			if (TypeNoeudOrganisation.depuisLibelle(u.getTypeNoeud()).orElse(null) == type) {
				return u.getLibelle();
			}
		}
		return null;
	}

	private static String nomComplet(Collaborateur c) {
		String complet = ((c.getPrenom() != null ? c.getPrenom() : "") + " " + (c.getNom() != null ? c.getNom() : "")).trim();
		return complet.isEmpty() ? null : complet;
	}
}
