package com.hr.presence.service;

import com.hr.presence.dto.HeuresSupDtos.BeneficiaireResponse;
import com.hr.presence.dto.HeuresSupDtos.DemandeHeuresSupResponse;
import com.hr.presence.entity.PresenceHeuresSupBeneficiaire;
import com.hr.presence.entity.PresenceHeuresSupDemande;
import com.hr.presence.entity.PresenceHeuresSupJour;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.repository.PresenceHeuresSupBeneficiaireRepository;
import com.hr.presence.repository.PresenceHeuresSupJourRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Construit les {@link DemandeHeuresSupResponse} par lot (bénéficiaires, jours restants, sites : une requête chacun). */
@Component
public class HeuresSupReponseMapper {

	private final PresenceHeuresSupBeneficiaireRepository beneficiaireRepository;
	private final PresenceHeuresSupJourRepository jourRepository;
	private final PresenceSiteRepository siteRepository;

	public HeuresSupReponseMapper(
			PresenceHeuresSupBeneficiaireRepository beneficiaireRepository,
			PresenceHeuresSupJourRepository jourRepository,
			PresenceSiteRepository siteRepository) {
		this.beneficiaireRepository = beneficiaireRepository;
		this.jourRepository = jourRepository;
		this.siteRepository = siteRepository;
	}

	public List<DemandeHeuresSupResponse> versReponses(Collection<PresenceHeuresSupDemande> demandes) {
		if (demandes.isEmpty()) {
			return List.of();
		}
		List<UUID> ids = demandes.stream().map(PresenceHeuresSupDemande::getId).toList();
		Map<UUID, List<PresenceHeuresSupBeneficiaire>> beneficiaires = beneficiaireRepository.findByDemandeIdIn(ids).stream()
				.collect(Collectors.groupingBy(PresenceHeuresSupBeneficiaire::getDemandeId));
		Map<UUID, List<PresenceHeuresSupJour>> jours = jourRepository.findByDemandeIdIn(ids).stream()
				.collect(Collectors.groupingBy(PresenceHeuresSupJour::getDemandeId));
		Map<UUID, PresenceSite> sites = siteRepository.findAllById(
						demandes.stream().map(PresenceHeuresSupDemande::getSiteId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(PresenceSite::getId, s -> s));
		return demandes.stream()
				.map(d -> versReponse(d, beneficiaires.getOrDefault(d.getId(), List.of()),
						jours.getOrDefault(d.getId(), List.of()), sites.get(d.getSiteId())))
				.toList();
	}

	public DemandeHeuresSupResponse versReponse(PresenceHeuresSupDemande d) {
		return versReponses(List.of(d)).get(0);
	}

	private static DemandeHeuresSupResponse versReponse(PresenceHeuresSupDemande d,
			List<PresenceHeuresSupBeneficiaire> beneficiaires, List<PresenceHeuresSupJour> jours, PresenceSite site) {
		List<LocalDate> joursOuverts = jours.stream().map(PresenceHeuresSupJour::getJournee).distinct()
				.sorted(Comparator.naturalOrder()).toList();
		List<BeneficiaireResponse> benefs = beneficiaires.stream()
				.sorted(Comparator.comparing(b -> b.getNom() != null ? b.getNom() : ""))
				.map(b -> new BeneficiaireResponse(b.getCollaborateurId(), b.getMatricule(), b.getNom()))
				.toList();
		return new DemandeHeuresSupResponse(
				d.getId(), d.getReference(), d.getNumero(), d.getStatut(), d.getSiteId(), site != null ? site.getLibelle() : null,
				d.getDateDebut(), d.getDateFin(), joursOuverts,
				d.getOuvertureAvantMinutes(), d.getOuvertureApresMinutes(), d.getDebutPlageTs(),
				d.getMotif(), d.getDemandeurNom(), benefs, d.isCreeeValidee(),
				d.getDecideParNom(), d.getDecideLe(), d.getMotifRefus(),
				d.getAnnuleParNom(), d.getAnnuleLe(), d.getMotifAnnulation(), d.getCreeLe());
	}
}
