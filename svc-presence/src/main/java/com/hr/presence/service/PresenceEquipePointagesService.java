package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.CollaborateurResolu;
import com.hr.presence.config.ClockConfig;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.dto.PointagesEquipeDtos.PointageEquipeItem;
import com.hr.presence.dto.PointagesEquipeDtos.PointagesEquipePage;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.service.FacialIdentiteService.Identite;
import com.hr.presence.web.PresenceMetierException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Liste paginée, filtrable et recherchable des pointages de l'équipe D-H03 d'un responsable sur mobile (R-10 à R-13).
 * Aucune transaction englobante (les appels au référentiel s'exécutent hors connexion JDBC).
 */
@Service
public class PresenceEquipePointagesService {

	private static final int PERIODE_DEFAUT_JOURS = 7;
	private static final int RECHERCHE_MAX_CARACTERES = 100;
	private static final Pattern DIACRITIQUES = Pattern.compile("\\p{M}+");

	private final TempsTravailService tempsTravailService;
	private final FacialIdentiteService facialIdentiteService;
	private final ReferentielClient referentielClient;
	private final PresencePointageRepository pointageRepository;
	private final PresenceSiteRepository siteRepository;
	private final PresenceParametresService parametres;

	public PresenceEquipePointagesService(
			TempsTravailService tempsTravailService,
			FacialIdentiteService facialIdentiteService,
			ReferentielClient referentielClient,
			PresencePointageRepository pointageRepository,
			PresenceSiteRepository siteRepository,
			PresenceParametresService parametres) {
		this.tempsTravailService = tempsTravailService;
		this.facialIdentiteService = facialIdentiteService;
		this.referentielClient = referentielClient;
		this.pointageRepository = pointageRepository;
		this.siteRepository = siteRepository;
		this.parametres = parametres;
	}

	public PointagesEquipePage lister(
			LocalDate du,
			LocalDate au,
			String q,
			UUID collaborateurId,
			UUID siteId,
			int page,
			int size) {
		LocalDate[] periode = tempsTravailService.periode(du, au, PERIODE_DEFAUT_JOURS);
		String qTrim = q == null ? null : q.trim();
		if (qTrim != null && qTrim.length() > RECHERCHE_MAX_CARACTERES) {
			throw PresenceMetierException.badRequest("RECHERCHE_TROP_LONGUE",
					"La recherche ne peut pas dépasser 100 caractères");
		}
		int pageNum = Math.max(page, 0);
		int pageSize = Math.clamp(size, 1, 100);

		Identite moi = facialIdentiteService.resoudre();
		List<CollaborateurResolu> equipe = referentielClient.perimetreValidation(moi.fiche().collaborateurId());
		Map<UUID, CollaborateurResolu> parFiche = new LinkedHashMap<>();
		for (CollaborateurResolu membre : equipe) {
			if (membre != null && membre.collaborateurId() != null) {
				parFiche.put(membre.collaborateurId(), membre);
			}
		}
		int effectifEquipe = parFiche.size();

		if (collaborateurId != null && !parFiche.containsKey(collaborateurId)) {
			throw PresenceMetierException.forbidden("HORS_PERIMETRE",
					"Ce collaborateur ne fait pas partie de votre équipe.");
		}
		if (parFiche.isEmpty()) {
			return new PointagesEquipePage(
					List.of(), pageNum, pageSize, 0, 0, periode[0], periode[1], 0);
		}

		List<CollaborateurResolu> candidats = collaborateurId != null
				? List.of(parFiche.get(collaborateurId))
				: List.copyOf(parFiche.values());

		if (qTrim != null && !qTrim.isEmpty()) {
			String[] jetons = normaliserRecherche(qTrim).split("\\s+");
			List<CollaborateurResolu> filtres = new ArrayList<>();
			for (CollaborateurResolu m : candidats) {
				String cible = normaliserRecherche(
						(m.nom() != null ? m.nom() : "") + " " + (m.matricule() != null ? m.matricule() : ""));
				if (Arrays.stream(jetons).allMatch(cible::contains)) {
					filtres.add(m);
				}
			}
			candidats = filtres;
		}

		if (candidats.isEmpty()) {
			return new PointagesEquipePage(
					List.of(), pageNum, pageSize, 0, 0, periode[0], periode[1], effectifEquipe);
		}

		Set<String> identites = new LinkedHashSet<>();
		Map<String, CollaborateurResolu> membreParIdentite = new HashMap<>();
		for (CollaborateurResolu m : candidats) {
			String ficheStr = m.collaborateurId().toString();
			identites.add(ficheStr);
			membreParIdentite.put(ficheStr, m);
			if (m.compteId() != null) {
				String compteStr = m.compteId().toString();
				identites.add(compteStr);
				membreParIdentite.put(compteStr, m);
			}
		}

		Instant debut = periode[0].atStartOfDay(ClockConfig.ZONE_TUNIS).toInstant();
		Instant finExclue = periode[1].plusDays(1).atStartOfDay(ClockConfig.ZONE_TUNIS).toInstant();

		Page<PresencePointage> pageResult = pointageRepository.findAll(
				PresencePointageRepository.filtreEquipePointages(identites, debut, finExclue, siteId),
				PageRequest.of(pageNum, pageSize, Sort.by(Sort.Order.desc("serverTs"), Sort.Order.desc("id"))));

		List<PresencePointage> contenu = pageResult.getContent();
		if (contenu.isEmpty()) {
			return new PointagesEquipePage(
					List.of(), pageResult.getNumber(), pageResult.getSize(),
					pageResult.getTotalElements(), pageResult.getTotalPages(),
					periode[0], periode[1], effectifEquipe);
		}

		List<UUID> siteIds = contenu.stream()
				.map(PresencePointage::getSiteId)
				.filter(Objects::nonNull)
				.distinct()
				.toList();
		Map<UUID, PresenceSite> sites = new HashMap<>();
		if (!siteIds.isEmpty()) {
			siteRepository.findAllById(siteIds).forEach(s -> sites.put(s.getId(), s));
		}

		Map<PointageStatut, String> libellesStatuts = parametres.libellesStatutsPointage();
		List<PointageEquipeItem> elements = new ArrayList<>(contenu.size());
		for (PresencePointage p : contenu) {
			CollaborateurResolu membre = membreParIdentite.get(p.getCollaborateurId());
			UUID ficheCanonique = membre != null ? membre.collaborateurId() : versUuid(p.getCollaborateurId());
			PresenceSite site = p.getSiteId() != null ? sites.get(p.getSiteId()) : null;
			String statutLibelle = p.getStatut() != null
					? libellesStatuts.getOrDefault(p.getStatut(), LibellesPresence.statut(p.getStatut()))
					: null;
			String motifRejet = LibellesPresence.motifRejet(p, site,
					s -> libellesStatuts.getOrDefault(s, LibellesPresence.statut(s)));
			LocalDate journee = p.getJournee() != null
					? p.getJournee()
					: (p.getServerTs() != null ? TempsTravailCalcul.dateTunis(p.getServerTs()) : null);

			elements.add(new PointageEquipeItem(
					p.getId(),
					ficheCanonique,
					membre != null ? membre.nom() : null,
					membre != null ? membre.matricule() : null,
					p.getType(),
					p.getType() != null ? LibellesPresence.type(p.getType()) : null,
					p.getStatut(),
					statutLibelle,
					p.getServerTs(),
					journee,
					p.getSiteId(),
					site != null ? site.getCode() : null,
					site != null ? site.getLibelle() : null,
					TempsTravailCalcul.estManuel(p),
					motifRejet
			));
		}

		return new PointagesEquipePage(
				elements,
				pageResult.getNumber(),
				pageResult.getSize(),
				pageResult.getTotalElements(),
				pageResult.getTotalPages(),
				periode[0],
				periode[1],
				effectifEquipe);
	}

	static String normaliserRecherche(String texte) {
		if (texte == null || texte.isBlank()) {
			return "";
		}
		String nfd = Normalizer.normalize(texte.trim(), Normalizer.Form.NFD);
		return DIACRITIQUES.matcher(nfd).replaceAll("").toLowerCase(Locale.ROOT);
	}

	private static UUID versUuid(String raw) {
		if (raw == null) {
			return null;
		}
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
