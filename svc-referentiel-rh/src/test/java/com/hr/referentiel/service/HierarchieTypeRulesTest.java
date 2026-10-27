package com.hr.referentiel.service;

import com.hr.referentiel.domain.TypeNoeudOrganisation;
import com.hr.referentiel.entity.UniteOrganisation;
import com.hr.referentiel.repository.UniteOrganisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static com.hr.referentiel.domain.TypeNoeudOrganisation.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** D-H04 : DG (unique) > Direction > Département > Service. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HierarchieTypeRulesTest {

	@Mock
	private UniteOrganisationRepository uniteRepository;

	private HierarchieTypeRules rules;
	private UniteOrganisation dg;
	private UniteOrganisation direction;
	private UniteOrganisation departement;
	private UniteOrganisation service;

	@BeforeEach
	void setUp() {
		rules = new HierarchieTypeRules(uniteRepository);
		dg = noeud("Direction Générale", null);
		direction = noeud("Direction", dg);
		departement = noeud("Département", direction);
		service = noeud("Service", departement);
		when(uniteRepository.findAll()).thenReturn(List.of(dg, direction, departement, service));
		when(uniteRepository.findByParentId(any())).thenReturn(List.of());
	}

	@Test
	@DisplayName("libellés reconnus sans tenir compte de la casse ni des accents ; type inconnu → 400")
	void exigerType() {
		assertThat(rules.exigerType("direction generale")).isEqualTo(DIRECTION_GENERALE);
		assertThat(rules.exigerType("DÉPARTEMENT")).isEqualTo(DEPARTEMENT);
		assertThatThrownBy(() -> rules.exigerType("Unité")).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Types autorisés");
	}

	@Test
	@DisplayName("une direction créée sans parent est rattachée d'office à la DG")
	void directionRattacheeAutomatiquementALaDg() {
		assertThat(rules.resoudreParentCreation(DIRECTION, null)).isSameAs(dg);
		assertThatThrownBy(() -> rules.resoudreParentCreation(DIRECTION, departement))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("une seule DG, toujours à la racine")
	void dgUnique() {
		assertThatThrownBy(() -> rules.resoudreParentCreation(DIRECTION_GENERALE, null))
				.hasMessageContaining("existe déjà");
		assertThatThrownBy(() -> rules.verifierRattachement(dg, direction))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("département / service : à rattacher (sans parent) ou sous un niveau supérieur")
	void departementEtServiceSousNiveauSuperieur() {
		assertThat(rules.resoudreParentCreation(DEPARTEMENT, null)).isNull();
		assertThat(rules.resoudreParentCreation(SERVICE, null)).isNull();
		assertThat(rules.resoudreParentCreation(DEPARTEMENT, dg)).isSameAs(dg);
		assertThat(rules.resoudreParentCreation(SERVICE, dg)).isSameAs(dg);
		assertThat(rules.resoudreParentCreation(SERVICE, direction)).isSameAs(direction);
		assertThat(rules.resoudreParentCreation(SERVICE, departement)).isSameAs(departement);
		assertThatThrownBy(() -> rules.resoudreParentCreation(DEPARTEMENT, service))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> rules.resoudreParentCreation(SERVICE, service))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("rattachement : une direction ne se détache pas de la DG ; un service peut redevenir à rattacher")
	void rattachement() {
		assertThatThrownBy(() -> rules.verifierRattachement(direction, null))
				.hasMessageContaining("Direction Générale");
		rules.verifierRattachement(service, null);
		rules.verifierRattachement(service, dg);
		assertThatThrownBy(() -> rules.verifierRattachement(departement, departement))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("changement de type refusé s'il contient un enfant de même niveau ou supérieur")
	void changementTypeAvecEnfants() {
		when(uniteRepository.findByParentId(departement.getId())).thenReturn(List.of(service));
		assertThatThrownBy(() -> rules.verifierChangementType(departement, SERVICE))
				.hasMessageContaining("contient");
	}

	private static UniteOrganisation noeud(String type, UniteOrganisation parent) {
		UniteOrganisation u = new UniteOrganisation();
		u.setId(UUID.randomUUID());
		u.setLibelle(type + " test");
		u.setTypeNoeud(type);
		u.setParent(parent);
		return u;
	}

	private static UUID any() {
		return org.mockito.ArgumentMatchers.any();
	}
}
