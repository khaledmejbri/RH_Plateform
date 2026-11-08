package com.hr.referentiel.service;

import com.hr.referentiel.dto.FicheAnnuaireResponse;
import com.hr.referentiel.dto.FicheEquipeResponse;
import com.hr.referentiel.dto.FicheUniteResponse;
import com.hr.referentiel.dto.HierarchieResponse;
import com.hr.referentiel.dto.PersonneResume;
import com.hr.referentiel.dto.UniteResume;
import com.hr.referentiel.dto.ValideurControleResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.repository.DemandeAdminWorkflowHistoryRepository;
import com.hr.referentiel.repository.DemandeAdministrativeRhRepository;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot 2 UAT, ticket 2A (D-N01, D-N02). Organigramme de test :
 * DG (Karim) > Direction Technique (sans manager) > Département Logiciel (Youssef) > Service Web (Omar ; Sarra, Ahmed)
 * et Service Mobile (Salma ; Mariem). Amel (DRH) est rattachée à la DG, dont Karim est le manager.
 */
class HierarchieServiceTest {

	private final CollaborateurRepository collaborateurs = mock(CollaborateurRepository.class);
	private final UniteOrganisationRepository unites = mock(UniteOrganisationRepository.class);
	private final CollaborateurConnecteService connecte = mock(CollaborateurConnecteService.class);
	private final DemandeAdministrativeRhRepository demandes = mock(DemandeAdministrativeRhRepository.class);
	private final DemandeAdminWorkflowHistoryRepository historique = mock(DemandeAdminWorkflowHistoryRepository.class);
	private final HierarchieService service =
			new HierarchieService(collaborateurs, unites, connecte, demandes, historique);

	private UniteOrganisation dg, direction, departement, serviceWeb, serviceMobile;
	private Collaborateur karim, youssef, omar, salma, sarra, ahmed, mariem, amel, archive;

	private static UniteOrganisation unite(String libelle, String type, UniteOrganisation parent, Collaborateur manager) {
		UniteOrganisation u = new UniteOrganisation();
		u.setId(UUID.randomUUID());
		u.setCode(libelle.toUpperCase());
		u.setLibelle(libelle);
		u.setTypeNoeud(type);
		u.setParent(parent);
		u.setManager(manager);
		u.setActif(true);
		return u;
	}

	private static Collaborateur collab(String matricule, String prenom, String nom, String statut, UniteOrganisation u) {
		Collaborateur c = new Collaborateur();
		c.setId(UUID.randomUUID());
		c.setMatricule(matricule);
		c.setPrenom(prenom);
		c.setNom(nom);
		c.setStatut(statut);
		c.setCompteUtilisateurId(UUID.randomUUID());
		c.setUnite(u);
		c.setPosteLibelle("Poste " + matricule);
		c.setCourrielProfessionnel(matricule.toLowerCase() + "@rh-evenement.test");
		return c;
	}

	@BeforeEach
	void setUp() {
		karim = collab("DG01", "Karim", "Ben Salah", "ACTIF", null);
		youssef = collab("CD-SW", "Youssef", "Chaabane", "ACTIF", null);
		omar = collab("CS-WEB", "Omar", "Belhadj", "ACTIF", null);
		salma = collab("CS-MOB", "Salma", "Trabelsi", "ACTIF", null);
		dg = unite("Direction Générale", "Direction Générale", null, karim);
		direction = unite("Direction Technique", "Direction", dg, null);
		departement = unite("Logiciel", "Département", direction, youssef);
		serviceWeb = unite("Web", "Service", departement, omar);
		serviceMobile = unite("Mobile", "Service", departement, salma);
		karim.setUnite(dg);
		youssef.setUnite(departement);
		omar.setUnite(serviceWeb);
		salma.setUnite(serviceMobile);
		sarra = collab("COL-WEB", "Sarra", "Khemiri", "ACTIF", serviceWeb);
		ahmed = collab("COL-WEB2", "Ahmed", "Nasri", "ACTIF", serviceWeb);
		mariem = collab("COL-MOB", "Mariem", "Guesmi", "ACTIF", serviceMobile);
		amel = collab("DRH01", "Amel", "Trabelsi", "ACTIF", dg);
		archive = collab("OLD", "Ancien", "Collab", "INACTIF", serviceWeb);

		List<Collaborateur> actifs = new ArrayList<>(List.of(karim, youssef, omar, salma, sarra, ahmed, mariem, amel));
		when(collaborateurs.findAllActifsWithUnite()).thenReturn(actifs);
		when(unites.findByActifTrueWithParentAndManager()).thenReturn(List.of(dg, direction, departement, serviceWeb, serviceMobile));
		for (Collaborateur c : List.of(karim, youssef, omar, salma, sarra, ahmed, mariem, amel, archive)) {
			when(collaborateurs.findDetailById(c.getId())).thenReturn(Optional.of(c));
			when(collaborateurs.findByCompteUtilisateurId(c.getCompteUtilisateurId())).thenReturn(Optional.of(c));
		}
		when(unites.findByManagerId(any())).thenReturn(List.of());
	}

	@Test
	void hierarchieDeCollaborateur_cheminResponsableEtValideurSontOmar() {
		HierarchieResponse h = service.hierarchieDe(sarra.getId());

		assertThat(h.chemin()).extracting(UniteResume::libelle)
				.containsExactly("Direction Générale", "Direction Technique", "Logiciel", "Web");
		assertThat(h.chemin().get(2).typeLibelle()).isEqualTo("Département");
		assertThat(h.responsableDirect().matricule()).isEqualTo("CS-WEB");
		assertThat(h.valideur().nomComplet()).isEqualTo("Omar Belhadj");
		assertThat(h.valideurEstRh()).isFalse();
		assertThat(h.collaborateur().uniteLibelle()).isEqualTo("Web");
		assertThat(h.collaborateursEncadres()).isEmpty();
	}

	@Test
	void hierarchieDuChefDeService_valideurEstLeChefDeDepartement_encadresSansLAutreService() {
		when(unites.findByManagerId(omar.getId())).thenReturn(List.of(serviceWeb));

		HierarchieResponse h = service.hierarchieDe(omar.getId());

		assertThat(h.valideur().matricule()).isEqualTo("CD-SW");
		assertThat(h.collaborateur().intituleRole()).isEqualTo("Chef de service");
		assertThat(h.noeudsManages()).extracting(UniteResume::libelle).containsExactly("Web");
		assertThat(h.collaborateursEncadres()).extracting(PersonneResume::matricule)
				.containsExactlyInAnyOrder("COL-WEB", "COL-WEB2");
		assertThat(h.collaborateursEncadres()).extracting(PersonneResume::matricule).doesNotContain("COL-MOB");
	}

	@Test
	void collaborateurSansSuperieur_valideurAbsentEtDemandesALaRh() {
		// DRH01 est dans la DG dont Karim est manager : valideur = Karim. Sans manager, retombée sur la RH.
		assertThat(service.hierarchieDe(amel.getId()).valideur().matricule()).isEqualTo("DG01");
		dg.setManager(null);

		HierarchieResponse h = service.hierarchieDe(amel.getId());

		assertThat(h.valideur()).isNull();
		assertThat(h.valideurEstRh()).isTrue();
	}

	@Test
	void hierarchieInconnue_404() {
		UUID inconnu = UUID.randomUUID();
		when(collaborateurs.findDetailById(inconnu)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.hierarchieDe(inconnu))
				.isInstanceOfSatisfying(ReferentielMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(404);
					assertThat(e.getCode()).isEqualTo("COLLABORATEUR_INTROUVABLE");
				});
	}

	@Test
	void ficheUnite_effectifsEtMembres() {
		when(unites.findById(departement.getId())).thenReturn(Optional.of(departement));
		when(unites.findByParentId(departement.getId())).thenReturn(List.of(serviceWeb, serviceMobile));

		FicheUniteResponse f = service.ficheUnite(departement.getId());

		assertThat(f.responsable().matricule()).isEqualTo("CD-SW");
		assertThat(f.chemin()).extracting(UniteResume::libelle)
				.containsExactly("Direction Générale", "Direction Technique", "Logiciel");
		assertThat(f.membres()).extracting(PersonneResume::matricule).containsExactly("CD-SW");
		assertThat(f.effectifDirect()).isEqualTo(1);
		// Youssef + Omar, Sarra, Ahmed + Salma, Mariem
		assertThat(f.effectifTotal()).isEqualTo(6);
		assertThat(f.sousUnites()).extracting(s -> s.unite().libelle() + ":" + s.effectifTotal())
				.containsExactly("Mobile:2", "Web:3");
		assertThat(f.aRattacher()).isFalse();
	}

	@Test
	void ficheUnite_departementSansParent_aRattacher() {
		UniteOrganisation libre = unite("Libre", "Département", null, null);
		when(unites.findById(libre.getId())).thenReturn(Optional.of(libre));
		when(unites.findByParentId(libre.getId())).thenReturn(List.of());

		FicheUniteResponse f = service.ficheUnite(libre.getId());

		assertThat(f.aRattacher()).isTrue();
		assertThat(f.responsable()).isNull();
		assertThat(f.effectifTotal()).isZero();
	}

	@Test
	void ficheUniteInconnue_404() {
		UUID inconnu = UUID.randomUUID();
		when(unites.findById(inconnu)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.ficheUnite(inconnu))
				.isInstanceOfSatisfying(ReferentielMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(404));
	}

	@Test
	void annuaire_actif_champsAnnuaireSeulement() {
		FicheAnnuaireResponse a = service.annuaire(amel.getId());

		assertThat(a.nomComplet()).isEqualTo("Amel Trabelsi");
		assertThat(a.uniteLibelle()).isEqualTo("Direction Générale");
		assertThat(a.responsable().nomComplet()).isEqualTo("Karim Ben Salah");
		assertThat(a.courrielProfessionnel()).isEqualTo("drh01@rh-evenement.test");
	}

	@Test
	void annuaire_collaborateurArchive_404() {
		assertThatThrownBy(() -> service.annuaire(archive.getId()))
				.isInstanceOfSatisfying(ReferentielMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(404));
	}

	@Test
	void monEquipe_chefDeService_etCollaborateurSansEquipe() {
		when(connecte.exigerCollaborateur(any())).thenReturn(omar);
		assertThat(service.monEquipe(null)).extracting(PersonneResume::matricule)
				.containsExactlyInAnyOrder("COL-WEB", "COL-WEB2");

		when(connecte.exigerCollaborateur(any())).thenReturn(sarra);
		assertThat(service.monEquipe(null)).isEmpty();
	}

	@Test
	void ficheEquipe_valideurVoitSonMembre_200() {
		when(connecte.exigerCollaborateur(any())).thenReturn(omar);
		when(demandes.findByDemandeurIdOrderByCreeLeDesc(sarra.getId())).thenReturn(List.of());

		FicheEquipeResponse f = service.ficheEquipe(sarra.getId(), null);

		assertThat(f.annuaire().nomComplet()).isEqualTo("Sarra Khemiri");
		assertThat(f.demandes()).isEmpty();
	}

	@Test
	void ficheEquipe_membreDUneAutreEquipe_403HorsPerimetre() {
		when(connecte.exigerCollaborateur(any())).thenReturn(omar);

		assertThatThrownBy(() -> service.ficheEquipe(mariem.getId(), null))
				.isInstanceOfSatisfying(ReferentielMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(403);
					assertThat(e.getCode()).isEqualTo("HORS_PERIMETRE");
				});
	}

	@Test
	void ficheEquipe_collegueSansPerimetreOuInconnu_403_memeReponse() {
		when(connecte.exigerCollaborateur(any())).thenReturn(sarra);
		UUID inconnu = UUID.randomUUID();
		when(collaborateurs.findDetailById(inconnu)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.ficheEquipe(ahmed.getId(), null))
				.isInstanceOfSatisfying(ReferentielMetierException.class, e -> assertThat(e.getCode()).isEqualTo("HORS_PERIMETRE"));
		assertThatThrownBy(() -> service.ficheEquipe(inconnu, null))
				.isInstanceOfSatisfying(ReferentielMetierException.class, e -> assertThat(e.getCode()).isEqualTo("HORS_PERIMETRE"));
	}

	@Test
	void ficheEquipe_membreArchive_403() {
		when(connecte.exigerCollaborateur(any())).thenReturn(omar);

		assertThatThrownBy(() -> service.ficheEquipe(archive.getId(), null))
				.isInstanceOfSatisfying(ReferentielMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(403));
	}

	@Test
	void controlerValideur_vraiPourOmarFauxPourLesAutres() {
		assertThat(service.controlerValideur(sarra.getId(), omar.getCompteUtilisateurId()))
				.isEqualTo(new ValideurControleResponse(true, true, omar.getId()));
		assertThat(service.controlerValideur(sarra.getId(), salma.getCompteUtilisateurId()).estValideur()).isFalse();
		assertThat(service.controlerValideur(sarra.getId(), sarra.getCompteUtilisateurId()).estValideur()).isFalse();
	}

	@Test
	void controlerValideur_echecFerme_inconnuInactifOuCompteSansFiche() {
		UUID inconnu = UUID.randomUUID();
		when(collaborateurs.findDetailById(inconnu)).thenReturn(Optional.empty());
		UUID compteSansFiche = UUID.randomUUID();
		when(collaborateurs.findByCompteUtilisateurId(compteSansFiche)).thenReturn(Optional.empty());

		assertThat(service.controlerValideur(inconnu, omar.getCompteUtilisateurId()).estValideur()).isFalse();
		assertThat(service.controlerValideur(archive.getId(), omar.getCompteUtilisateurId()))
				.isEqualTo(new ValideurControleResponse(false, false, null));
		assertThat(service.controlerValideur(sarra.getId(), compteSansFiche).estValideur()).isFalse();
		assertThat(service.controlerValideur(sarra.getId(), null).estValideur()).isFalse();
	}

	@Test
	void intituleRole_titrePosteDuNoeudPrimeSurLeTypeEtLePosteSertAuxAutres() {
		departement.setTitrePoste("Responsable Software");
		when(unites.findByManagerId(youssef.getId())).thenReturn(List.of(departement));

		HierarchieResponse h = service.hierarchieDe(youssef.getId());

		assertThat(h.collaborateur().intituleRole()).isEqualTo("Responsable Software");
		assertThat(service.hierarchieDe(sarra.getId()).collaborateur().intituleRole()).isEqualTo("Poste COL-WEB");
	}
}
