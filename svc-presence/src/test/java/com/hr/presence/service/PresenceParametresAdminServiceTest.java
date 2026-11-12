package com.hr.presence.service;

import com.hr.presence.config.PresenceProperties;
import com.hr.presence.domain.ActionParametre;
import com.hr.presence.dto.ParametresDtos.Reglage;
import com.hr.presence.dto.ParametresDtos.ValeurRequest;
import com.hr.presence.entity.PresenceParametreJournal;
import com.hr.presence.entity.PresenceParametreValeur;
import com.hr.presence.entity.PresenceParametres;
import com.hr.presence.repository.PresenceParametreJournalRepository;
import com.hr.presence.repository.PresenceParametreValeurRepository;
import com.hr.presence.repository.PresenceParametresRepository;
import com.hr.presence.security.PresenceAccessService;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ticket 3D : bornes des réglages, version, codes de comportement (405), journal. */
class PresenceParametresAdminServiceTest {

	private static final String REF = PresenceParametresService.STATUT_POINTAGE;

	private final PresenceParametreValeurRepository valeurs = mock(PresenceParametreValeurRepository.class);
	private final PresenceParametreJournalRepository journal = mock(PresenceParametreJournalRepository.class);
	private final PresenceParametresRepository reglagesRepo = mock(PresenceParametresRepository.class);
	private final PresenceAccessService acces = mock(PresenceAccessService.class);
	private final com.hr.presence.client.ReferentielClient referentiel = mock(com.hr.presence.client.ReferentielClient.class);
	private final PresenceParametreUtilisations utilisations = mock(PresenceParametreUtilisations.class);
	private PresenceParametresAdminService service;
	private PresenceParametres ligne;
	private PresenceParametreValeur qr;
	private PresenceParametreValeur expire;

	@BeforeEach
	void setUp() {
		ligne = new PresenceParametres();
		when(reglagesRepo.findById(PresenceParametres.ID_UNIQUE)).thenReturn(Optional.of(ligne));
		when(reglagesRepo.saveAndFlush(any(PresenceParametres.class))).thenAnswer(inv -> inv.getArgument(0));
		when(valeurs.saveAndFlush(any(PresenceParametreValeur.class))).thenAnswer(inv -> inv.getArgument(0));
		qr = valeur("REJETE_QR_INVALIDE", "QR code invalide", 1);
		expire = valeur("REJETE_QR_EXPIRE", "QR code expiré", 2);
		when(valeurs.findByReferentielOrderByOrdreAscCodeAsc(REF)).thenReturn(List.of(qr, expire));
		when(valeurs.findByReferentielAndCode(REF, "REJETE_QR_INVALIDE")).thenReturn(Optional.of(qr));
		when(acces.currentCompteId()).thenReturn("compte-rh");
		when(acces.currentActorLabel()).thenReturn("compte-rh");
		PresenceParametresService lecture = new PresenceParametresService(valeurs, reglagesRepo, new PresenceProperties(), 30);
		service = new PresenceParametresAdminService(valeurs, journal, reglagesRepo, lecture, acces, referentiel,
				utilisations);
	}

	private static PresenceParametreValeur valeur(String code, String libelle, int ordre) {
		PresenceParametreValeur v = new PresenceParametreValeur();
		v.setReferentiel(REF);
		v.setCode(code);
		v.setLibelle(libelle);
		v.setLibelleNormalise(PresenceParametresAdminService.normaliser(libelle));
		v.setOrdre(ordre);
		return v;
	}

	@ParameterizedTest
	@CsvSource({
			"delai_grace_retard_minutes,-1,false", "delai_grace_retard_minutes,0,true", "delai_grace_retard_minutes,60,true",
			"delai_grace_retard_minutes,61,false",
			"rayon_defaut_exterieur_metres,9,false", "rayon_defaut_exterieur_metres,10,true",
			"rayon_defaut_exterieur_metres,100,true", "rayon_defaut_exterieur_metres,101,false",
			"rayon_defaut_interieur_metres,9,false", "rayon_defaut_interieur_metres,10,true",
			"rayon_defaut_interieur_metres,100,true", "rayon_defaut_interieur_metres,101,false",
			"seuil_pointages_manuels_mois,0,false", "seuil_pointages_manuels_mois,1,true",
			"seuil_pointages_manuels_mois,31,true", "seuil_pointages_manuels_mois,32,false",
			"plafond_distance_manuel_metres,99,false", "plafond_distance_manuel_metres,100,true",
			"plafond_distance_manuel_metres,1000,true", "plafond_distance_manuel_metres,1001,false",
			"delai_demande_manuel_minutes,14,false", "delai_demande_manuel_minutes,15,true",
			"delai_demande_manuel_minutes,480,true", "delai_demande_manuel_minutes,481,false",
			"delai_decision_manuel_minutes,239,false", "delai_decision_manuel_minutes,240,true",
			"delai_decision_manuel_minutes,4320,true", "delai_decision_manuel_minutes,4321,false"})
	void bornesDesReglages(String cle, int valeur, boolean accepte) {
		if (accepte) {
			Reglage r = service.modifierReglage(cle, valeur, 0L);
			assertThat(r.valeur()).isEqualTo(valeur);
		} else {
			assertThatThrownBy(() -> service.modifierReglage(cle, valeur, 0L))
					.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
						assertThat(e.getHttpStatus()).isEqualTo(400);
						assertThat(e.getCode()).isEqualTo("REGLAGE_HORS_BORNES");
					});
			verify(reglagesRepo, never()).saveAndFlush(any());
		}
	}

	@Test
	void reglageInconnu_404() {
		assertThatThrownBy(() -> service.modifierReglage("inexistant", 5, 0L))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(404));
	}

	@Test
	void versionPerimee_409() {
		assertThatThrownBy(() -> service.modifierReglage("delai_grace_retard_minutes", 5, 7L))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(409);
					assertThat(e.getCode()).isEqualTo("PARAMETRE_MODIFIE_ENTRE_TEMPS");
				});
		verify(reglagesRepo, never()).saveAndFlush(any());
	}

	@Test
	void conflitDeVerrouOptimiste_409() {
		when(reglagesRepo.saveAndFlush(any(PresenceParametres.class)))
				.thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(PresenceParametres.class, 1));
		assertThatThrownBy(() -> service.modifierReglage("delai_grace_retard_minutes", 5, 0L))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(409));
	}

	@Test
	void modificationDeReglage_estJournalisee() {
		ligne.setDelaiGraceRetardMinutes(0);
		service.modifierReglage("delai_grace_retard_minutes", 5, 0L);

		ArgumentCaptor<PresenceParametreJournal> c = ArgumentCaptor.forClass(PresenceParametreJournal.class);
		verify(journal).save(c.capture());
		assertThat(c.getValue().getAction()).isEqualTo(ActionParametre.REGLAGE);
		assertThat(c.getValue().getCode()).isEqualTo("delai_grace_retard_minutes");
		assertThat(c.getValue().getAuteurCompteId()).isEqualTo("compte-rh");
		assertThat(c.getValue().getAvantJson()).contains("0");
		assertThat(c.getValue().getApresJson()).contains("5");
	}

	@Test
	void journalDesReglages_auteurNomResoluDepuisLeCompte() {
		java.util.UUID compte = java.util.UUID.randomUUID();
		when(acces.currentCompteId()).thenReturn(compte.toString());
		when(acces.currentActorLabel()).thenReturn(compte.toString());
		when(referentiel.affectations(List.of(compte))).thenReturn(List.of(
				new com.hr.presence.client.ReferentielClient.Affectation(java.util.UUID.randomUUID(), compte, "DRH01",
						"Amel Trabelsi", null, null, null)));
		ligne.setDelaiGraceRetardMinutes(0);

		service.modifierReglage("delai_grace_retard_minutes", 5, 0L);

		ArgumentCaptor<PresenceParametreJournal> c = ArgumentCaptor.forClass(PresenceParametreJournal.class);
		verify(journal).save(c.capture());
		assertThat(c.getValue().getAuteurNom()).isEqualTo("Amel Trabelsi");
	}

	@Test
	void journal_referentielIndisponible_replisurLIdentifiantSansFaireEchouerLEcriture() {
		java.util.UUID compte = java.util.UUID.randomUUID();
		when(acces.currentCompteId()).thenReturn(compte.toString());
		when(acces.currentActorLabel()).thenReturn(compte.toString());
		when(referentiel.affectations(any())).thenThrow(PresenceMetierException.indisponible("REFERENTIEL_INDISPONIBLE", "x"));
		ligne.setDelaiGraceRetardMinutes(0);

		service.modifierReglage("delai_grace_retard_minutes", 5, 0L);

		ArgumentCaptor<PresenceParametreJournal> c = ArgumentCaptor.forClass(PresenceParametreJournal.class);
		verify(journal).save(c.capture());
		assertThat(c.getValue().getAuteurNom()).isEqualTo(compte.toString());
	}

	@Test
	void utilisations_compteesParCode_enUneRequeteGroupee() {
		when(utilisations.parCode(REF)).thenReturn(java.util.Map.of("REJETE_QR_INVALIDE", 55L));

		var liste = service.valeurs(REF);

		assertThat(liste.get(0).utilisations()).isEqualTo(55);
		assertThat(liste.get(0).dejaUtilisee()).isTrue();
		assertThat(liste.get(1).utilisations()).isZero();
		assertThat(liste.get(1).dejaUtilisee()).isFalse();
		verify(utilisations, org.mockito.Mockito.times(1)).parCode(REF);
	}

	@Test
	void putSansChangement_niJournalNiEcritureNiVersion() {
		var r = service.modifier(REF, "REJETE_QR_INVALIDE", new ValeurRequest("QR code invalide", null, null, null, 0L));

		assertThat(r.libelle()).isEqualTo("QR code invalide");
		verify(journal, never()).save(any());
		verify(valeurs, never()).saveAndFlush(any());

		ligne.setDelaiGraceRetardMinutes(5);
		var reglage = service.modifierReglage("delai_grace_retard_minutes", 5, 0L);
		assertThat(reglage.valeur()).isEqualTo(5);
		verify(journal, never()).save(any());
		verify(reglagesRepo, never()).saveAndFlush(any());

		service.ordonner(REF, List.of("REJETE_QR_INVALIDE", "REJETE_QR_EXPIRE"));
		verify(journal, never()).save(any());
	}

	@Test
	void descriptionNull_inchangee_chaineVide_efface() {
		qr.setDescription("aide RH");
		qr.setCouleur("ROUGE");

		service.modifier(REF, "REJETE_QR_INVALIDE", new ValeurRequest("QR code invalide", null, null, null, 0L));
		assertThat(qr.getDescription()).isEqualTo("aide RH");
		assertThat(qr.getCouleur()).isEqualTo("ROUGE");

		service.modifier(REF, "REJETE_QR_INVALIDE", new ValeurRequest("QR code invalide", "", "", null, 0L));
		assertThat(qr.getDescription()).isNull();
		assertThat(qr.getCouleur()).isNull();
	}

	@Test
	void creationSimultanee_deLaLigneDeReglages_409_pasUne500() {
		when(reglagesRepo.findById(PresenceParametres.ID_UNIQUE)).thenReturn(Optional.empty());
		when(reglagesRepo.saveAndFlush(any(PresenceParametres.class)))
				.thenThrow(new org.springframework.dao.DataIntegrityViolationException("pk en double"));

		assertThatThrownBy(() -> service.modifierReglage("delai_grace_retard_minutes", 5, 0L))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(409);
					assertThat(e.getCode()).isEqualTo("PARAMETRE_MODIFIE_ENTRE_TEMPS");
				});
	}

	@Test
	void creationSuppressionDesactivation_405_surUnStatut() {
		assertThatThrownBy(() -> service.creer(REF)).isInstanceOfSatisfying(PresenceMetierException.class, e -> {
			assertThat(e.getHttpStatus()).isEqualTo(405);
			assertThat(e.getCode()).isEqualTo("PARAMETRE_COMPORTEMENT");
		});
		assertThatThrownBy(() -> service.supprimer(REF))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(405));
		assertThatThrownBy(() -> service.desactiver(REF))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(405));
	}

	@Test
	void referentielInconnu_404() {
		assertThatThrownBy(() -> service.valeurs("TYPE_INCONNU"))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("REFERENTIEL_INCONNU"));
		assertThatThrownBy(() -> service.creer("TYPE_INCONNU"))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(404));
	}

	@Test
	void renommerUnMotif_libelleEtAideEnregistres_etJournalises() {
		var r = service.modifier(REF, "REJETE_QR_INVALIDE",
				new ValeurRequest("QR non reconnu", "Le QR scanné ne correspond à aucun site.", "rouge", null, 0L));

		assertThat(r.libelle()).isEqualTo("QR non reconnu");
		assertThat(r.couleur()).isEqualTo("ROUGE");
		assertThat(qr.getDescription()).isEqualTo("Le QR scanné ne correspond à aucun site.");
		ArgumentCaptor<PresenceParametreJournal> c = ArgumentCaptor.forClass(PresenceParametreJournal.class);
		verify(journal).save(c.capture());
		assertThat(c.getValue().getAction()).isEqualTo(ActionParametre.MODIFICATION);
		assertThat(c.getValue().getAvantJson()).contains("QR code invalide");
		assertThat(c.getValue().getApresJson()).contains("QR non reconnu");
	}

	@Test
	void validationsDeLibelle() {
		assertThatThrownBy(() -> service.modifier(REF, "REJETE_QR_INVALIDE", new ValeurRequest(" a ", null, null, null, 0L)))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("LIBELLE_INVALIDE"));
		assertThatThrownBy(() -> service.modifier(REF, "REJETE_QR_INVALIDE", new ValeurRequest("QR  CODE expiré", null, null, null, 0L)))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("LIBELLE_EN_DOUBLE"));
		assertThatThrownBy(() -> service.modifier(REF, "REJETE_QR_INVALIDE", new ValeurRequest("Autre", null, "FUCHSIA", null, 0L)))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("COULEUR_INVALIDE"));
		assertThatThrownBy(() -> service.modifier(REF, "REJETE_QR_INVALIDE", new ValeurRequest("Autre", "x".repeat(301), null, null, 0L)))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("DESCRIPTION_TROP_LONGUE"));
		assertThatThrownBy(() -> service.modifier(REF, "REJETE_QR_INVALIDE", new ValeurRequest("Autre", null, null, null, 9L)))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(409));
		assertThatThrownBy(() -> service.modifier(REF, "CODE_INCONNU", new ValeurRequest("Autre", null, null, null, 0L)))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(404));
	}

	@Test
	void ordreIncomplet_400_etOrdreComplet_200() {
		assertThatThrownBy(() -> service.ordonner(REF, List.of("REJETE_QR_INVALIDE")))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> assertThat(e.getCode()).isEqualTo("ORDRE_INCOMPLET"));
		service.ordonner(REF, List.of("REJETE_QR_EXPIRE", "REJETE_QR_INVALIDE"));
		assertThat(expire.getOrdre()).isEqualTo(1);
		assertThat(qr.getOrdre()).isEqualTo(2);
	}

	@Test
	void libellesNonBackoffice_actifsSansDescription() {
		qr.setDescription("aide RH");
		expire.setActif(false);
		when(acces.isBackofficeLecture()).thenReturn(false);

		var l = service.libelles(true);

		assertThat(l.referentiels().get(REF)).extracting("code").containsExactly("REJETE_QR_INVALIDE");
		assertThat(l.referentiels().get(REF).get(0).description()).isNull();
	}

	@Test
	void libellesBackoffice_toutAvecAide() {
		qr.setDescription("aide RH");
		expire.setActif(false);
		when(acces.isBackofficeLecture()).thenReturn(true);

		assertThat(service.libelles(true).referentiels().get(REF)).hasSize(1);
		var tout = service.libelles(false).referentiels().get(REF);
		assertThat(tout).hasSize(2);
		assertThat(tout.get(0).description()).isEqualTo("aide RH");
	}
}
