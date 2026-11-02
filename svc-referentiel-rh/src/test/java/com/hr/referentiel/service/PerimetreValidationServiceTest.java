package com.hr.referentiel.service;

import com.hr.referentiel.dto.CollaborateurPointageResponse;
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
 * D-P13.a : périmètre de validation D-H03 des heures supplémentaires. Organigramme de test :
 * DG > Direction Technique > Département Logiciel (chef Youssef) > Service Web (chef Omar, membres Sarra et Ahmed).
 */
class PerimetreValidationServiceTest {

	private final CollaborateurRepository repository = mock(CollaborateurRepository.class);
	private final PerimetreValidationService service = new PerimetreValidationService(repository);

	private UniteOrganisation dg;
	private UniteOrganisation direction;
	private UniteOrganisation departement;
	private UniteOrganisation serviceWeb;
	private Collaborateur karim;
	private Collaborateur youssef;
	private Collaborateur omar;
	private Collaborateur sarra;
	private Collaborateur ahmed;

	private static UniteOrganisation unite(String libelle, String type, UniteOrganisation parent, Collaborateur manager) {
		UniteOrganisation u = new UniteOrganisation();
		u.setId(UUID.randomUUID());
		u.setLibelle(libelle);
		u.setTypeNoeud(type);
		u.setParent(parent);
		u.setManager(manager);
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
		karim = collab("DG01", "Karim", "Ben Salah", "ACTIF", null);
		youssef = collab("CD-SW", "Youssef", "Chaabane", "ACTIF", null);
		omar = collab("CS-WEB", "Omar", "Belhadj", "ACTIF", null);
		dg = unite("Direction Générale", "Direction Générale", null, karim);
		direction = unite("Direction Technique", "Direction", dg, null);
		departement = unite("Logiciel", "Département", direction, youssef);
		serviceWeb = unite("Web", "Service", departement, omar);
		karim.setUnite(dg);
		youssef.setUnite(departement);
		omar.setUnite(serviceWeb);
		sarra = collab("COL-WEB", "Sarra", "Khemiri", "ACTIF", serviceWeb);
		ahmed = collab("COL-WEB2", "Ahmed", "Nasri", "ACTIF", serviceWeb);
		when(repository.findAllActifsWithUnite()).thenReturn(List.of(karim, youssef, omar, sarra, ahmed));
	}

	@Test
	void resolution_membreDeService_valideurEstLeChefDeService_directionRenseignee() {
		when(repository.findAllById(List.of(sarra.getId()))).thenReturn(List.of(sarra));

		List<CollaborateurPointageResponse> r = service.resoudre(List.of(sarra.getId()));

		assertThat(r).singleElement().satisfies(c -> {
			assertThat(c.collaborateurId()).isEqualTo(sarra.getId());
			assertThat(c.compteId()).isEqualTo(sarra.getCompteUtilisateurId());
			assertThat(c.matricule()).isEqualTo("COL-WEB");
			assertThat(c.nom()).isEqualTo("Sarra Khemiri");
			assertThat(c.actif()).isTrue();
			assertThat(c.valideurId()).isEqualTo(omar.getId());
			assertThat(c.valideurCompteId()).isEqualTo(omar.getCompteUtilisateurId());
			assertThat(c.directionId()).isEqualTo(direction.getId());
			assertThat(c.directionLibelle()).isEqualTo("Direction Technique");
		});
	}

	@Test
	void resolution_chefDeService_valideurEstLeChefDeDepartement_leResponsableDuDemandeurHorsPerimetre() {
		when(repository.findAllById(List.of(omar.getId()))).thenReturn(List.of(omar));

		CollaborateurPointageResponse r = service.resoudre(List.of(omar.getId())).get(0);

		assertThat(r.valideurId()).isEqualTo(youssef.getId());
		assertThat(r.valideurId()).isNotEqualTo(omar.getId());
	}

	@Test
	void resolution_collaborateurInactif_actifFaux_etIdentifiantInconnuAbsent() {
		Collaborateur parti = collab("OLD", "Ancien", "Collab", "INACTIF", serviceWeb);
		when(repository.findAllById(List.of(parti.getId(), UUID.fromString("00000000-0000-0000-0000-000000000001"))))
				.thenReturn(List.of(parti));

		List<CollaborateurPointageResponse> r = service.resoudre(
				List.of(parti.getId(), UUID.fromString("00000000-0000-0000-0000-000000000001")));

		assertThat(r).singleElement().satisfies(c -> assertThat(c.actif()).isFalse());
	}

	@Test
	void resolution_sansDirection_directionNulle() {
		when(repository.findAllById(List.of(karim.getId()))).thenReturn(List.of(karim));

		CollaborateurPointageResponse r = service.resoudre(List.of(karim.getId())).get(0);

		assertThat(r.directionId()).isNull();
		assertThat(r.directionLibelle()).isNull();
		assertThat(r.valideurId()).isNull();
	}

	@Test
	void perimetre_chefDeService_sesDeuxCollaborateursActifs_pasLuiMemeNiSonChef() {
		List<CollaborateurPointageResponse> r = service.perimetreDe(omar.getId());

		assertThat(r).extracting(CollaborateurPointageResponse::matricule).containsExactly("COL-WEB2", "COL-WEB");
	}

	@Test
	void perimetre_chefDeDepartement_leChefDeServiceSeulement() {
		List<CollaborateurPointageResponse> r = service.perimetreDe(youssef.getId());

		assertThat(r).extracting(CollaborateurPointageResponse::matricule).containsExactly("CS-WEB");
	}

	@Test
	void perimetre_unCollaborateurSimple_listeVide() {
		assertThat(service.perimetreDe(sarra.getId())).isEmpty();
	}

	@Test
	void perimetre_managerInactif_sesMembresPassentParLeValideurSuivant() {
		omar.setStatut("INACTIF");

		assertThat(service.perimetreDe(omar.getId())).isEmpty();
	}
}
