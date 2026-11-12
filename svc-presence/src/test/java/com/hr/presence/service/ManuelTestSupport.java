package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient;
import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.ModeQr;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.entity.PresenceSite;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.repository.PresenceDemandeManuelleRepository;
import com.hr.presence.repository.PresencePointageRepository;
import com.hr.presence.repository.PresenceSiteRepository;
import com.hr.presence.security.PresenceAccessService;
import jakarta.persistence.EntityManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Montage commun des tests du pointage manuel sécurisé : horloge figée, vrais composants d'éligibilité et de mise en
 * forme, dépôts et référentiel simulés.
 */
final class ManuelTestSupport {

	static final Instant MAINTENANT = Instant.parse("2026-11-11T09:00:00Z");
	static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

	final PresenceProperties properties = new PresenceProperties();
	final PresenceDemandeManuelleRepository demandeRepository = mock(PresenceDemandeManuelleRepository.class);
	final PresencePointageRepository pointageRepository = mock(PresencePointageRepository.class);
	final PresenceSiteRepository siteRepository = mock(PresenceSiteRepository.class);
	final ReferentielClient referentielClient = mock(ReferentielClient.class);
	final PresenceAccessService accessService = mock(PresenceAccessService.class);
	final PresenceEventPublisher eventPublisher = mock(PresenceEventPublisher.class);
	final FenetreHoraireService fenetreHoraire = mock(FenetreHoraireService.class);
	final EntityManager entityManager = mock(EntityManager.class);
	final Map<String, Long> rangs = new HashMap<>();
	final Clock clock = Clock.fixed(MAINTENANT, TUNIS);

	final com.hr.presence.repository.PresenceParametresRepository parametresRepository =
			mock(com.hr.presence.repository.PresenceParametresRepository.class);
	final PresenceParametresService parametres = new PresenceParametresService(
			mock(com.hr.presence.repository.PresenceParametreValeurRepository.class), parametresRepository, properties, 30);
	final EligibiliteManuelService eligibilite =
			new EligibiliteManuelService(properties, parametres, new HaversineService(), fenetreHoraire);
	final DemandeManuelleReponseMapper mapper =
			new DemandeManuelleReponseMapper(demandeRepository, pointageRepository, referentielClient, parametres);
	final PresenceDemandeManuelleService service;

	final UUID ficheCollab = UUID.randomUUID();
	final UUID compteCollab = UUID.randomUUID();
	final UUID ficheRo = UUID.randomUUID();
	final UUID compteRo = UUID.randomUUID();
	final UUID siteId = UUID.randomUUID();
	PresenceSite site;
	PresencePointage rejet;

	ManuelTestSupport() {
		service = new PresenceDemandeManuelleService(demandeRepository, pointageRepository, referentielClient,
				accessService, new FacialIdentiteService(accessService, referentielClient), eventPublisher, parametres,
				siteRepository, fenetreHoraire,
				new com.hr.presence.numero.NumeroDemandeService(cle -> rangs.merge(cle, 1L, Long::sum)),
				eligibilite, mapper, entityManager, clock);

		// Site écran de 50 m, emplacement connu : un refus à 80 m ne porte aucun signal de distance
		site = new PresenceSite();
		site.setId(siteId);
		site.setRayonMetres(50);
		site.setModeQr(ModeQr.ECRAN);
		site.setLatitude(36.8);
		site.setLongitude(10.18);
		lenient().when(siteRepository.findById(siteId)).thenReturn(java.util.Optional.of(site));

		rejet = refus(PointageStatut.REJETE_HORS_ZONE, 80.0, 12.0);
		lenient().when(pointageRepository.findById(rejet.getId())).thenReturn(java.util.Optional.of(rejet));
		lenient().when(demandeRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(PresenceDemandeManuelle.class)))
				.thenAnswer(inv -> sauver(inv.getArgument(0)));
		lenient().when(demandeRepository.save(org.mockito.ArgumentMatchers.any(PresenceDemandeManuelle.class)))
				.thenAnswer(inv -> sauver(inv.getArgument(0)));
		lenient().when(pointageRepository.save(org.mockito.ArgumentMatchers.any(PresencePointage.class))).thenAnswer(inv -> {
			PresencePointage p = inv.getArgument(0);
			if (p.getId() == null) {
				p.setId(UUID.randomUUID());
			}
			return p;
		});
	}

	private static PresenceDemandeManuelle sauver(PresenceDemandeManuelle d) {
		if (d.getId() == null) {
			d.setId(UUID.randomUUID());
		}
		return d;
	}

	/** Refus du collaborateur, 10 minutes avant {@link #MAINTENANT}, position connue. */
	PresencePointage refus(PointageStatut statut, Double distance, Double precision) {
		PresencePointage p = new PresencePointage();
		p.setId(UUID.randomUUID());
		p.setCollaborateurId(ficheCollab.toString());
		p.setSiteId(siteId);
		p.setType(PointageType.ENTREE);
		p.setStatut(statut);
		p.setServerTs(MAINTENANT.minusSeconds(600));
		p.setLatitude(36.8);
		p.setLongitude(10.18);
		p.setDistanceMetres(distance);
		p.setAccuracyMetres(precision);
		p.setMotifRejet("Hors zone de pointage");
		return p;
	}

	ValideurPointage collab(UUID valideurFiche, UUID valideurCompte) {
		return new ValideurPointage(ficheCollab, compteCollab, "Sarra Khemiri", valideurFiche, valideurCompte,
				valideurFiche != null ? "Omar Belhadj" : null);
	}

	ValideurPointage ro() {
		return new ValideurPointage(ficheRo, compteRo, "Omar Belhadj", UUID.randomUUID(), UUID.randomUUID(), "Chef");
	}

	/** Demande en attente du collaborateur, adressée à {@link #ficheRo}, échéance dans 23 h. */
	PresenceDemandeManuelle demandeEnAttente() {
		PresenceDemandeManuelle d = new PresenceDemandeManuelle();
		d.setId(UUID.randomUUID());
		d.setNumero("PM-202611-0007");
		d.setPointageRejeteId(rejet.getId());
		d.setCollaborateurId(ficheCollab.toString());
		d.setCollaborateurCompteId(compteCollab.toString());
		d.setCollaborateurNom("Sarra Khemiri");
		d.setSiteId(siteId);
		d.setType(PointageType.ENTREE);
		d.setHeureDeclaree(rejet.getServerTs());
		d.setJustification("GPS faible au rez-de-chaussée");
		d.setValideurId(ficheRo.toString());
		d.setValideurCompteId(compteRo.toString());
		d.setValideurNom("Omar Belhadj");
		d.setEcheance(MAINTENANT.plusSeconds(23 * 3600));
		lenient().when(demandeRepository.findById(d.getId())).thenReturn(java.util.Optional.of(d));
		lenient().when(demandeRepository.findByIdPourDecision(d.getId())).thenReturn(java.util.Optional.of(d));
		return d;
	}

	void siteSansHoraire() {
		lenient().when(fenetreHoraire.evaluer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(new FenetreHoraireService.SansHoraire());
	}

	void connecteCommeCollaborateur(UUID valideurFiche, UUID valideurCompte) {
		when(referentielClient.monValideurPointage()).thenReturn(collab(valideurFiche, valideurCompte));
	}
}
