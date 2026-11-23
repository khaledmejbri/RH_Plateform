package com.hr.referentiel.service;

import com.hr.referentiel.dto.AffectationCollaborateurResponse;
import com.hr.referentiel.dto.MembreUniteResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * D-P17.g : filtre d'unité (sous-unités comprises) et libellés d'affectation de la vue « Temps de travail ».
 * Organigramme : DG > Direction Technique > Département Logiciel > services Web et Mobile (COL-MOB dans Mobile).
 */
class AffectationServiceTest {

	private final CollaborateurRepository repository = mock(CollaborateurRepository.class);
	private final AffectationService service = new AffectationService(repository);

	private UniteOrganisation direction;
	private UniteOrganisation departement;
	private UniteOrganisation serviceWeb;
	private UniteOrganisation serviceMobile;
	private Collaborateur sarra;
	private Collaborateur ahmed;
	private Collaborateur mariem;
	private Collaborateur parti;

	private static UniteOrganisation unite(String libelle, String type, UniteOrganisation parent) {
		UniteOrganisation u = new UniteOrganisation();
		u.setId(UUID.randomUUID());
		u.setLibelle(libelle);
		u.setTypeNoeud(type);
		u.setParent(parent);
		return u;
	}

	private static Collaborateur collab(String matricule, String prenom, String nom, String statut, UniteOrganisation unite) {
		Collaborateur c = new Collaborateur();
		c.setId(UUID.randomUUID());
		c.setMatricule(matricule);
		c.setPrenom(prenom);
		c.setNom(nom);
		c.setStatut(statut);
		c.setCompteUtilisateurId(UUID.randomUUID());
		c.setUnite(unite);
		return c;
	}

	@BeforeEach
	void setUp() {
		UniteOrganisation dg = unite("Direction Générale", "Direction Générale", null);
		direction = unite("Direction Technique", "Direction", dg);
		departement = unite("Logiciel", "Département", direction);
		serviceWeb = unite("Web", "Service", departement);
		serviceMobile = unite("Mobile", "Service", departement);
		sarra = collab("COL-WEB", "Sarra", "Khemiri", "ACTIF", serviceWeb);
		ahmed = collab("COL-WEB2", "Ahmed", "Nasri", "ACTIF", serviceWeb);
		mariem = collab("COL-MOB", "Mariem", "Guesmi", "ACTIF", serviceMobile);
		parti = collab("OLD", "Ancien", "Collab", "INACTIF", serviceWeb);
		when(repository.findAllWithUnite()).thenReturn(List.of(sarra, ahmed, mariem, parti));
	}

	private List<UUID> fiches(List<MembreUniteResponse> membres) {
		return membres.stream().map(MembreUniteResponse::collaborateurId).toList();
	}

	@Test
	void membresDeUnite_service_seulementSesCollaborateurs_inactifsCompris() {
		List<MembreUniteResponse> r = service.membresDeUnite(serviceWeb.getId());

		assertThat(fiches(r)).containsExactlyInAnyOrder(sarra.getId(), ahmed.getId(), parti.getId());
		assertThat(r).extracting(MembreUniteResponse::compteId).contains(sarra.getCompteUtilisateurId());
	}

	@Test
	void membresDeUnite_departementOuDirection_sousUnitesComprises() {
		assertThat(fiches(service.membresDeUnite(departement.getId())))
				.containsExactlyInAnyOrder(sarra.getId(), ahmed.getId(), mariem.getId(), parti.getId());
		assertThat(fiches(service.membresDeUnite(direction.getId()))).hasSize(4);
	}

	@Test
	void membresDeUnite_uniteInconnue_listeVide() {
		assertThat(service.membresDeUnite(UUID.randomUUID())).isEmpty();
	}

	@Test
	void affectations_libellesDirectionDepartementService() {
		when(repository.findAllById(List.of(sarra.getId()))).thenReturn(List.of(sarra));

		AffectationCollaborateurResponse r = service.affectations(List.of(sarra.getId())).get(0);

		assertThat(r.matricule()).isEqualTo("COL-WEB");
		assertThat(r.nom()).isEqualTo("Sarra Khemiri");
		assertThat(r.directionLibelle()).isEqualTo("Direction Technique");
		assertThat(r.departementLibelle()).isEqualTo("Logiciel");
		assertThat(r.serviceLibelle()).isEqualTo("Web");
	}

	@Test
	void affectations_identifiantDeCompte_retrouveLaFiche_sansDoublon() {
		when(repository.findAllById(List.of(sarra.getCompteUtilisateurId(), sarra.getId()))).thenReturn(List.of(sarra));
		when(repository.findAllByCompteUtilisateurIdIn(List.of(sarra.getCompteUtilisateurId(), sarra.getId())))
				.thenReturn(List.of(sarra));

		List<AffectationCollaborateurResponse> r = service.affectations(List.of(sarra.getCompteUtilisateurId(), sarra.getId()));

		assertThat(r).singleElement().satisfies(a -> assertThat(a.collaborateurId()).isEqualTo(sarra.getId()));
	}

	@Test
	void affectations_collaborateurRattacheADirection_departementEtServiceNuls() {
		Collaborateur directeur = collab("DIR", "Dir", "Ecteur", "ACTIF", direction);
		when(repository.findAllById(List.of(directeur.getId()))).thenReturn(List.of(directeur));

		AffectationCollaborateurResponse r = service.affectations(List.of(directeur.getId())).get(0);

		assertThat(r.directionLibelle()).isEqualTo("Direction Technique");
		assertThat(r.departementLibelle()).isNull();
		assertThat(r.serviceLibelle()).isNull();
	}

	@Test
	void affectations_exposePrenomNomFamilleQualificationEtAffectation() {
		sarra.setPosteLibelle("Ingénieure Fullstack");
		sarra.setFonction("Développeuse Senior");
		sarra.setQualite("OP");
		sarra.setQualificationAffectation("Cadre technique");
		sarra.setAffectation("CHANTIER");
		when(repository.findAllById(List.of(sarra.getId()))).thenReturn(List.of(sarra));

		AffectationCollaborateurResponse r = service.affectations(List.of(sarra.getId())).get(0);

		assertThat(r.prenom()).isEqualTo("Sarra");
		assertThat(r.nomFamille()).isEqualTo("Khemiri");
		assertThat(r.nom()).isEqualTo("Sarra Khemiri");
		assertThat(r.posteLibelle()).isEqualTo("Ingénieure Fullstack");
		assertThat(r.fonction()).isEqualTo("Développeuse Senior");
		assertThat(r.qualite()).isEqualTo("OP");
		assertThat(r.qualificationAffectation()).isEqualTo("Cadre technique");
		assertThat(r.affectation()).isEqualTo("CHANTIER");
	}

	@Test
	void calendrierPresence_retourneJoursTravaillesFeriesEtCongesApprouves() {
		var demandeRepo = mock(com.hr.referentiel.repository.DemandeAdministrativeRhRepository.class);
		var paramsRepo = mock(com.hr.referentiel.repository.SocieteParametresRepository.class);
		var feriesService = mock(JourFerieService.class);
		var svc = new AffectationService(repository, demandeRepo, paramsRepo, feriesService);

		var params = new com.hr.referentiel.entity.SocieteParametres();
		params.setJoursTravailles(List.of("LUN", "MAR", "MER", "JEU", "VEN", "SAM"));
		when(paramsRepo.findFirstByOrderByIdAsc()).thenReturn(java.util.Optional.of(params));
		when(feriesService.datesActives()).thenReturn(java.util.Set.of(
				java.time.LocalDate.of(2026, 9, 15),
				java.time.LocalDate.of(2026, 10, 15),
				java.time.LocalDate.of(2026, 11, 15)));

		var conge = new com.hr.referentiel.entity.DemandeAdministrativeRh();
		conge.setDemandeur(sarra);
		conge.setTypeDemande(com.hr.referentiel.domain.TypeDemandeAdministrativeRh.CONGE);
		conge.setStatut(com.hr.referentiel.domain.StatutDemandeAdministrativeRh.APPROUVEE);
		conge.setPeriodeDebut(java.time.LocalDate.of(2026, 10, 5));
		conge.setPeriodeFin(java.time.LocalDate.of(2026, 10, 7));
		conge.setContenu(java.util.Map.of("type_conge", "ANNUEL", "periode_premier_jour", "JOURNEE_ENTIERE", "periode_dernier_jour", "JOURNEE_ENTIERE"));

		java.time.LocalDate du = java.time.LocalDate.of(2026, 9, 27);
		java.time.LocalDate au = java.time.LocalDate.of(2026, 10, 26);
		when(demandeRepo.findCongesApprouvesSurPeriode(
				List.of(sarra.getId()),
				com.hr.referentiel.domain.TypeDemandeAdministrativeRh.CONGE,
				com.hr.referentiel.domain.StatutDemandeAdministrativeRh.APPROUVEE,
				du, au)).thenReturn(List.of(conge));

		var res = svc.calendrierPresence(List.of(sarra.getId()), du, au);

		assertThat(res.joursTravailles()).containsExactly("LUN", "MAR", "MER", "JEU", "VEN", "SAM");
		assertThat(res.joursFeries()).containsExactly(java.time.LocalDate.of(2026, 10, 15));
		assertThat(res.conges()).singleElement().satisfies(c -> {
			assertThat(c.collaborateurId()).isEqualTo(sarra.getId());
			assertThat(c.dateDebut()).isEqualTo(java.time.LocalDate.of(2026, 10, 5));
			assertThat(c.dateFin()).isEqualTo(java.time.LocalDate.of(2026, 10, 7));
			assertThat(c.typeConge()).isEqualTo("ANNUEL");
		});
	}
}
