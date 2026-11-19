package com.hr.referentiel.web;

import com.hr.referentiel.domain.BaseDecompteConge;
import com.hr.referentiel.dto.SocieteParametresDtos.SocieteParametresResponse;
import com.hr.referentiel.dto.WorkflowInterneDtos.AbsenceCollaborateurInterne;
import com.hr.referentiel.dto.WorkflowInterneDtos.CalendrierSocieteInterneResponse;
import com.hr.referentiel.dto.WorkflowInterneDtos.CollaborateurResumeInterne;
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
import com.hr.referentiel.service.IdentiteSocieteClient;
import com.hr.referentiel.service.IdentiteSocieteClient.SocieteResume;
import com.hr.referentiel.service.JourFerieService;
import com.hr.referentiel.service.SocieteParametresService;
import com.hr.referentiel.service.WorkflowResolutionService;
import com.hr.referentiel.test.AvecSocieteContexteExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests d'intégration/service des endpoints internes de résolution pour {@code svc-workflow} (§ 9.5.3, D-H03).
 */
@ExtendWith(AvecSocieteContexteExtension.class)
class ResolutionIntervenantsControllerTest {

	private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-19T09:00:00Z"), ZoneOffset.UTC);

	private CollaborateurRepository collaborateurRepo;
	private UniteOrganisationRepository uniteRepo;
	private FonctionRepository fonctionRepo;
	private FonctionAffectationRepository affectationRepo;
	private DemandeAdministrativeRhRepository demandeRepo;
	private SocieteParametresService paramsService;
	private JourFerieService jourFerieService;
	private IdentiteSocieteClient identiteSocieteClient;
	private WorkflowReferentielInterneController controller;

	private UniteOrganisation uDg;
	private UniteOrganisation uDirRh;
	private UniteOrganisation uDeptSw;
	private UniteOrganisation uServiceWeb;
	private UniteOrganisation uSansManager;

	private Collaborateur dg01;
	private Collaborateur drh01;
	private Collaborateur cdSw;
	private Collaborateur csWeb;
	private Collaborateur colWeb;
	private Collaborateur colSansManager;
	private Collaborateur colInactif;

	@BeforeEach
	void setUp() {
		collaborateurRepo = mock(CollaborateurRepository.class);
		uniteRepo = mock(UniteOrganisationRepository.class);
		fonctionRepo = mock(FonctionRepository.class);
		affectationRepo = mock(FonctionAffectationRepository.class);
		demandeRepo = mock(DemandeAdministrativeRhRepository.class);
		paramsService = mock(SocieteParametresService.class);
		jourFerieService = mock(JourFerieService.class);
		identiteSocieteClient = mock(IdentiteSocieteClient.class);

		WorkflowResolutionService service = new WorkflowResolutionService(
				collaborateurRepo, uniteRepo, fonctionRepo, affectationRepo,
				demandeRepo, paramsService, jourFerieService, identiteSocieteClient, HORLOGE);
		controller = new WorkflowReferentielInterneController(service);

		// Hiérarchie conforme à CONTEXTE.md (D-H03 / D-H04)
		uDg = unite("DG", "Direction Générale", "DIRECTION_GENERALE", null);
		uDirRh = unite("DIR-RH", "Direction RH", "DIRECTION", uDg);
		uDeptSw = unite("DEPT-SW", "Département Software", "DEPARTEMENT", uDg);
		uServiceWeb = unite("SRV-WEB", "Service Web", "SERVICE", uDeptSw);
		uSansManager = unite("SRV-VIDE", "Service Sans Manager", "SERVICE", uDeptSw);

		dg01 = collab("DG01", "Directeur", "Général", "DIRECTION", uDg, true, true);
		drh01 = collab("DRH01", "Amel", "Trabelsi", "RH", uDirRh, true, true);
		cdSw = collab("CD-SW", "Chef", "Département", "RO", uDeptSw, true, true);
		csWeb = collab("CS-WEB", "Chef", "Service", "RO", uServiceWeb, true, true);
		colWeb = collab("COL-WEB", "Sonia", "Mrad", "COLLABORATEUR", uServiceWeb, true, true);
		colSansManager = collab("COL-VIDE", "Nadia", "Kefi", "COLLABORATEUR", uSansManager, true, true);
		colInactif = collab("COL-INACT", "Inactif", "User", "COLLABORATEUR", uServiceWeb, false, true);

		uDg.setManager(dg01);
		uDirRh.setManager(drh01);
		uDeptSw.setManager(cdSw);
		uServiceWeb.setManager(csWeb);

		when(uniteRepo.findAll()).thenReturn(List.of(uDg, uDirRh, uDeptSw, uServiceWeb, uSansManager));
		when(collaborateurRepo.findAllWithUnite()).thenReturn(
				List.of(dg01, drh01, cdSw, csWeb, colWeb, colSansManager, colInactif));
	}

	@Test
	@DisplayName("D-H03 (MANAGER_NOEUD) : COL-WEB -> CS-WEB, CS-WEB -> CD-SW, DRH01 -> DG01, DG01 -> CHAINE_TERMINEE, sans manager -> AUCUN_MANAGER")
	void casHierarchieDH03() {
		assertThat(resoudreUnique(colWeb, new RegleResolutionRequest(
				"m1", "MANAGER_NOEUD", null, null, null, null, null, null, null)))
				.satisfies(r -> {
					assertThat(r.raisonVide()).isNull();
					assertThat(r.intervenants()).extracting("matricule").containsExactly("CS-WEB");
				});

		assertThat(resoudreUnique(csWeb, new RegleResolutionRequest(
				"m1", "MANAGER_NOEUD", null, null, null, null, null, null, null)))
				.satisfies(r -> {
					assertThat(r.raisonVide()).isNull();
					assertThat(r.intervenants()).extracting("matricule").containsExactly("CD-SW");
				});

		assertThat(resoudreUnique(drh01, new RegleResolutionRequest(
				"m1", "MANAGER_NOEUD", null, null, null, null, null, null, null)))
				.satisfies(r -> {
					assertThat(r.raisonVide()).isNull();
					assertThat(r.intervenants()).extracting("matricule").containsExactly("DG01");
				});

		assertThat(resoudreUnique(dg01, new RegleResolutionRequest(
				"m1", "MANAGER_NOEUD", null, null, null, null, null, null, null)))
				.satisfies(r -> {
					assertThat(r.intervenants()).isEmpty();
					assertThat(r.raisonVide()).isEqualTo("CHAINE_TERMINEE");
				});

		assertThat(resoudreUnique(colSansManager, new RegleResolutionRequest(
				"m1", "MANAGER_NOEUD", null, null, null, null, null, null, null)))
				.satisfies(r -> {
					assertThat(r.intervenants()).isEmpty();
					assertThat(r.raisonVide()).isEqualTo("AUCUN_MANAGER");
				});
	}

	@Test
	@DisplayName("Toutes les règles (MANAGER_NIVEAU, RESPONSABLE_TYPE, ROLE, FONCTION, COLLABORATEUR, DEMANDEUR) et toutes les valeurs raison_vide")
	void toutesReglesEtRaisonsVides() {
		Fonction fnAchat = new Fonction("APPROBATEUR_ACHAT", "Approbateur Achat", "approbateur achat", null, Instant.now(HORLOGE));
		FonctionAffectation affDept = new FonctionAffectation(
				fnAchat, cdSw, uDeptSw, LocalDate.of(2026, 1, 1), null, Instant.now(HORLOGE));
		when(fonctionRepo.findAll()).thenReturn(List.of(fnAchat));
		when(affectationRepo.findActivesParCodesEtDate(any(), any())).thenReturn(List.of(affDept));

		ResolutionLotResponse lot = controller.resoudreIntervenants(new ResolutionLotRequest(
				colWeb.getId(),
				List.of(csWeb.getId()), // CS-WEB est bénéficiaire -> sera exclu par I1 !
				LocalDate.of(2026, 11, 19),
				List.of(
						// 1. MANAGER_NOEUD de colWeb est csWeb, mais csWeb est bénéficiaire -> TOUS_EXCLUS
						new RegleResolutionRequest("r_exclus", "MANAGER_NOEUD", null, null, null, null, null, null, List.of("DEMANDEUR", "BENEFICIAIRES")),
						// 2. MANAGER_NIVEAU 2 de colWeb -> CD-SW
						new RegleResolutionRequest("r_niv2", "MANAGER_NIVEAU", 2, null, null, null, null, null, null),
						// 3. MANAGER_NIVEAU 5 de colWeb -> CHAINE_TERMINEE
						new RegleResolutionRequest("r_niv5", "MANAGER_NIVEAU", 5, null, null, null, null, null, null),
						// 4. RESPONSABLE_TYPE DEPARTEMENT -> CD-SW
						new RegleResolutionRequest("r_type", "RESPONSABLE_TYPE", null, "DEPARTEMENT", null, null, null, null, null),
						// 5. ROLE RH -> DRH01
						new RegleResolutionRequest("r_role", "ROLE", null, null, "RH", null, null, null, null),
						// 6. ROLE inexistant -> AUCUN_TITULAIRE
						new RegleResolutionRequest("r_role_vide", "ROLE", null, null, "ROLE_INEXISTANT", null, null, null, null),
						// 7. FONCTION avec périmètre UNITE_DEMANDEUR (colWeb descend de uDeptSw) -> CD-SW
						new RegleResolutionRequest("r_fn_ok", "FONCTION", null, null, null, "APPROBATEUR_ACHAT", "UNITE_DEMANDEUR", null, null),
						// 8. FONCTION inconnue -> FONCTION_INCONNUE
						new RegleResolutionRequest("r_fn_ko", "FONCTION", null, null, null, "INCONNUE", "SOCIETE", null, null),
						// 9. COLLABORATEUR inactif -> INACTIF
						new RegleResolutionRequest("r_inactif", "COLLABORATEUR", null, null, null, null, null, colInactif.getId(), null),
						// 10. DEMANDEUR -> colWeb
						new RegleResolutionRequest("r_demandeur", "DEMANDEUR", null, null, null, null, null, null, null)
				)));

		assertThat(lot.contexteDemandeur().matricule()).isEqualTo("COL-WEB");
		Map<String, ResultatResolution> parCle = new java.util.HashMap<>();
		lot.resultats().forEach(r -> parCle.put(r.cle(), r));

		assertThat(parCle.get("r_exclus").raisonVide()).isEqualTo("TOUS_EXCLUS");
		assertThat(parCle.get("r_niv2").intervenants()).extracting("matricule").containsExactly("CD-SW");
		assertThat(parCle.get("r_niv5").raisonVide()).isEqualTo("CHAINE_TERMINEE");
		assertThat(parCle.get("r_type").intervenants()).extracting("matricule").containsExactly("CD-SW");
		assertThat(parCle.get("r_role").intervenants()).extracting("matricule").containsExactly("DRH01");
		assertThat(parCle.get("r_role_vide").raisonVide()).isEqualTo("AUCUN_TITULAIRE");
		assertThat(parCle.get("r_fn_ok").intervenants()).extracting("matricule").containsExactly("CD-SW");
		assertThat(parCle.get("r_fn_ko").raisonVide()).isEqualTo("FONCTION_INCONNUE");
		assertThat(parCle.get("r_inactif").raisonVide()).isEqualTo("INACTIF");
		assertThat(parCle.get("r_demandeur").intervenants()).extracting("matricule").containsExactly("COL-WEB");
	}

	@Test
	@DisplayName("Endpoints internes : calendrier société, absences du jour, résumé par id et par compte")
	void calendrierAbsencesEtResumes() {
		when(paramsService.obtenir()).thenReturn(new SocieteParametresResponse(
				UUID.randomUUID(), AvecSocieteContexteExtension.SOCIETE_TEST,
				List.of("LUN", "MAR", "MER", "JEU", "VEN"), 2400,
				Map.of("LUN", 480, "MAR", 480, "MER", 480, "JEU", 480, "VEN", 480),
				"08:00", "17:00", BaseDecompteConge.JOURS_TRAVAILLES, new BigDecimal("1.5"),
				120, true, false, 0L, Instant.now(HORLOGE), "Système"));
		when(identiteSocieteClient.trouverParId(any())).thenReturn(Optional.of(
				new SocieteResume(AvecSocieteContexteExtension.SOCIETE_TEST, "PRINCIPALE", "Société Principale", "ACTIVE", "Africa/Tunis")));
		when(jourFerieService.datesActives()).thenReturn(Set.of(LocalDate.of(2026, 12, 17)));

		CalendrierSocieteInterneResponse cal = controller.calendrierSociete(
				LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
		assertThat(cal.fuseau()).isEqualTo("Africa/Tunis");
		assertThat(cal.joursFeries()).containsExactly(LocalDate.of(2026, 12, 17));

		DemandeAdministrativeRh conge = new DemandeAdministrativeRh();
		conge.setDemandeur(colWeb);
		conge.setPeriodeDebut(LocalDate.of(2026, 11, 18));
		conge.setPeriodeFin(LocalDate.of(2026, 11, 20));
		conge.setContenu(Map.of("type_conge", "ANNUEL"));
		when(demandeRepo.findPourRhCouvrantJour(any(), any(), any())).thenReturn(List.of(conge));

		List<AbsenceCollaborateurInterne> abs = controller.absencesDuJour(LocalDate.of(2026, 11, 19));
		assertThat(abs).hasSize(1);
		assertThat(abs.get(0).matricule()).isEqualTo("COL-WEB");

		when(collaborateurRepo.findDetailById(colWeb.getId())).thenReturn(Optional.of(colWeb));
		when(collaborateurRepo.findByCompteUtilisateurId(colWeb.getCompteUtilisateurId())).thenReturn(Optional.of(colWeb));

		CollaborateurResumeInterne r1 = controller.resumeCollaborateur(colWeb.getId());
		CollaborateurResumeInterne r2 = controller.resumeCollaborateurParCompte(colWeb.getCompteUtilisateurId());
		assertThat(r1.matricule()).isEqualTo("COL-WEB");
		assertThat(r2.id()).isEqualTo(colWeb.getId());
	}

	private ResultatResolution resoudreUnique(Collaborateur demandeur, RegleResolutionRequest regle) {
		ResolutionLotResponse resp = controller.resoudreIntervenants(new ResolutionLotRequest(
				demandeur.getId(), List.of(), LocalDate.of(2026, 11, 19), List.of(regle)));
		return resp.resultats().get(0);
	}

	private static UniteOrganisation unite(String code, String nom, String type, UniteOrganisation parent) {
		UniteOrganisation u = new UniteOrganisation();
		u.setId(UUID.randomUUID());
		u.setCode(code);
		u.setLibelle(nom);
		u.setTypeNoeud(type);
		u.setParent(parent);
		return u;
	}

	private static Collaborateur collab(
			String matricule, String prenom, String nom, String profil,
			UniteOrganisation unite, boolean actif, boolean avecCompte) {
		Collaborateur c = new Collaborateur();
		c.setId(UUID.randomUUID());
		c.setMatricule(matricule);
		c.setPrenom(prenom);
		c.setNom(nom);
		c.setProfilAcces(profil);
		c.setUnite(unite);
		c.setStatut(actif ? "ACTIF" : "INACTIF");
		if (avecCompte) {
			c.setCompteUtilisateurId(UUID.randomUUID());
		}
		return c;
	}
}
