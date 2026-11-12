package com.hr.presence.service;

import com.hr.presence.client.ReferentielClient.ValideurPointage;
import com.hr.presence.domain.PointageStatut;
import com.hr.presence.domain.PointageType;
import com.hr.presence.domain.SignalVigilance;
import com.hr.presence.domain.StatutDemandeManuelle;
import com.hr.presence.dto.DemandeManuelleCreationRequest;
import com.hr.presence.dto.DemandeManuelleResponse;
import com.hr.presence.entity.PresenceDemandeManuelle;
import com.hr.presence.entity.PresencePointage;
import com.hr.presence.kafka.ObjetNotification;
import com.hr.presence.kafka.PresenceEventPublisher;
import com.hr.presence.web.PresenceMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.hr.presence.service.ManuelTestSupport.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D-P04, D-P21 à D-P24 (SCRUM-83, SCRUM-84) : création (ordre des contrôles, données figées), validation, refus.
 */
class PresenceDemandeManuelleServiceTest {

	private ManuelTestSupport t;
	private PresenceDemandeManuelleService service;

	@BeforeEach
	void setUp() {
		t = new ManuelTestSupport();
		service = t.service;
		t.siteSansHoraire();
	}

	private DemandeManuelleCreationRequest requete(String justification) {
		return new DemandeManuelleCreationRequest(t.rejet.getId(), null, null, justification);
	}

	private void exigeErreur(Runnable action, int http, String code) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(PresenceMetierException.class, e -> {
			assertThat(e.getHttpStatus()).isEqualTo(http);
			assertThat(e.getCode()).isEqualTo(code);
		});
	}

	// ---- Création -------------------------------------------------------------------------------------------------

	@Test
	void creation_figeLesDonneesEtNotifieLeCompteDuResponsable() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		DemandeManuelleResponse r = service.creer(requete("GPS faible au rez-de-chaussée"));

		assertThat(r.statut()).isEqualTo(StatutDemandeManuelle.EN_ATTENTE);
		assertThat(r.numero()).matches("PM-\\d{6}-0001");
		assertThat(r.heureDeclaree()).isEqualTo(t.rejet.getServerTs());
		assertThat(r.refusStatut()).isEqualTo("REJETE_HORS_ZONE");
		assertThat(r.distanceMetres()).isEqualTo(80.0);
		assertThat(r.precisionMetres()).isEqualTo(12.0);
		assertThat(r.rayonMetres()).isEqualTo(50);
		assertThat(r.modeQr()).isEqualTo("ECRAN");
		assertThat(r.delaiDemandeMinutes()).isNull(); // D-P24.d : délai masqué au demandeur
		assertThat(r.delaiDecisionMinutes()).isNull();
		assertThat(r.echeance()).isEqualTo(t.rejet.getServerTs().plusSeconds(24 * 3600));
		assertThat(r.valideurNom()).isEqualTo("Omar Belhadj");
		verify(t.eventPublisher).notifier(eq(t.compteRo.toString()), startsWith("Pointage manuel à valider"), anyString(), any());
		ArgumentCaptor<PresenceDemandeManuelle> d = ArgumentCaptor.forClass(PresenceDemandeManuelle.class);
		verify(t.demandeRepository).saveAndFlush(d.capture());
		assertThat(d.getValue().getPlafondMetres()).isEqualTo(300);
		assertThat(d.getValue().isReprise()).isFalse();
		assertThat(d.getValue().getSignaux()).isEmpty();
	}

	@Test
	void creation_litLePlafondEtLeDelaiDeDecisionDesParametres_D_P22_D_P23() {
		com.hr.presence.entity.PresenceParametres ligne = new com.hr.presence.entity.PresenceParametres();
		ligne.setPlafondDistanceManuelMetres(150);
		ligne.setDelaiDecisionManuelMinutes(480);
		when(t.parametresRepository.findById(com.hr.presence.entity.PresenceParametres.ID_UNIQUE))
				.thenReturn(java.util.Optional.of(ligne));
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		DemandeManuelleResponse r = service.creer(requete("GPS faible au rez-de-chaussée"));

		assertThat(r.echeance()).isEqualTo(t.rejet.getServerTs().plusSeconds(8 * 3600));
		ArgumentCaptor<PresenceDemandeManuelle> d = ArgumentCaptor.forClass(PresenceDemandeManuelle.class);
		verify(t.demandeRepository).saveAndFlush(d.capture());
		assertThat(d.getValue().getPlafondMetres()).isEqualTo(150); // figé à la création (D-P22)
	}

	@Test
	void creation_demandeurNeVoitNiSignauxNiCompteursNiPlafond() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		DemandeManuelleResponse r = service.creer(requete("GPS faible au rez-de-chaussée"));

		assertThat(r.signaux()).isNull();
		assertThat(r.avecSignal()).isNull();
		assertThat(r.plafondMetres()).isNull();
		assertThat(r.manuelsDuMois()).isNull();
		assertThat(r.seuilMois()).isNull();
		assertThat(r.alerteSeuil()).isNull();
		assertThat(r.refusDuJour()).isNull();
		assertThat(r.commentaireValideur()).isNull();
	}

	@Test
	void creation_signauxCalculesEtFiges() {
		// Site papier, distance > 2 x rayon, précision > rayon, facial n'est pas en jeu, demande 45 min après
		t.site.setModeQr(com.hr.presence.domain.ModeQr.PAPIER);
		t.rejet.setDistanceMetres(120.0);
		t.rejet.setAccuracyMetres(60.0);
		t.rejet.setServerTs(MAINTENANT.minusSeconds(45 * 60));
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		service.creer(requete("GPS faible au rez-de-chaussée"));

		ArgumentCaptor<PresenceDemandeManuelle> d = ArgumentCaptor.forClass(PresenceDemandeManuelle.class);
		verify(t.demandeRepository).saveAndFlush(d.capture());
		assertThat(d.getValue().getSignaux()).containsExactlyInAnyOrder(SignalVigilance.DISTANCE_SUP_2X_RAYON,
				SignalVigilance.PRECISION_SUP_RAYON, SignalVigilance.SITE_QR_PAPIER, SignalVigilance.DEMANDE_TARDIVE);
		assertThat(d.getValue().getDelaiDemandeMinutes()).isEqualTo(45L);
	}

	@Test
	void creationSansResponsable_notifieLesRh() {
		t.connecteCommeCollaborateur(null, null);

		DemandeManuelleResponse r = service.creer(requete("GPS faible au rez-de-chaussée"));

		assertThat(r.validationParRh()).isTrue();
		verify(t.eventPublisher).notifier(eq(PresenceEventPublisher.DESTINATAIRE_RH), anyString(), anyString(), any());
	}

	@Test
	void creation_enteteFalsifie_403IdentiteIncoherente_avantTout_rienNEstSauvegarde() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);
		when(t.accessService.collaborateurHeaderOuNull()).thenReturn(UUID.randomUUID().toString());

		// Corps invalide aussi : l'identité passe avant la validation du corps (D-P21.c)
		exigeErreur(() -> service.creer(requete("court")), 403, "IDENTITE_INCOHERENTE");

		verify(t.demandeRepository, never()).saveAndFlush(any());
		verifyNoInteractions(t.eventPublisher);
	}

	@Test
	void creation_enteteEgalALaFiche_ok() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);
		when(t.accessService.collaborateurHeaderOuNull()).thenReturn(t.ficheCollab.toString());

		assertThat(service.creer(requete("GPS faible au rez-de-chaussée")).statut())
				.isEqualTo(StatutDemandeManuelle.EN_ATTENTE);
	}

	@Test
	void mesDemandesEtAValider_enteteFalsifie_403() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);
		when(t.accessService.collaborateurHeaderOuNull()).thenReturn(UUID.randomUUID().toString());

		exigeErreur(() -> service.mesDemandes(), 403, "IDENTITE_INCOHERENTE");
		exigeErreur(() -> service.aValider(), 403, "IDENTITE_INCOHERENTE");
		verify(t.demandeRepository, never()).findByCollaborateurIdOrderByCreeLeDesc(anyString());
	}

	@Test
	void creation_justificationInvalide_400() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		exigeErreur(() -> service.creer(requete("123456789")), 400, "VALIDATION_ERROR");
		exigeErreur(() -> service.creer(requete("         x         ")), 400, "VALIDATION_ERROR");
		exigeErreur(() -> service.creer(requete(null)), 400, "VALIDATION_ERROR");
		exigeErreur(() -> service.creer(requete("x".repeat(501))), 400, "VALIDATION_ERROR");
		exigeErreur(() -> service.creer(new DemandeManuelleCreationRequest(null, null, null, "GPS faible ici")),
				400, "VALIDATION_ERROR");
	}

	@Test
	void creation_justificationDe10Et500Caracteres_acceptee_etTronquee() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		assertThat(service.creer(requete("  1234567890  ")).justification()).isEqualTo("1234567890");
	}

	@Test
	void creation_pointageInconnu_403HorsPerimetre() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		exigeErreur(() -> service.creer(new DemandeManuelleCreationRequest(UUID.randomUUID(), null, null,
				"GPS faible au rez-de-chaussée")), 403, "HORS_PERIMETRE");
		verifyNoInteractions(t.eventPublisher);
	}

	@Test
	void creation_pointageDUnAutreCollaborateur_403HorsPerimetre_memeReponseQueInconnu() {
		t.rejet.setCollaborateurId(UUID.randomUUID().toString());
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		assertThatThrownBy(() -> service.creer(requete("GPS faible au rez-de-chaussée")))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(403);
					assertThat(e.getCode()).isEqualTo("HORS_PERIMETRE");
					assertThat(e.getMessage()).isEqualTo("Ce pointage n'existe pas ou ne vous appartient pas.");
				});
	}

	@Test
	void creation_heureDifferente_400ChampNonModifiable() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		exigeErreur(() -> service.creer(new DemandeManuelleCreationRequest(t.rejet.getId(), null,
				t.rejet.getServerTs().plusSeconds(60), "GPS faible au rez-de-chaussée")), 400, "CHAMP_NON_MODIFIABLE");
		verify(t.demandeRepository, never()).saveAndFlush(any());
	}

	@Test
	void creation_typeDifferent_400ChampNonModifiable() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		exigeErreur(() -> service.creer(new DemandeManuelleCreationRequest(t.rejet.getId(), "SORTIE", null,
				"GPS faible au rez-de-chaussée")), 400, "CHAMP_NON_MODIFIABLE");
	}

	@Test
	void creation_heureEtTypeIdentiques_acceptesEtIgnores_heureALaSeconde() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);
		t.rejet.setServerTs(MAINTENANT.minusSeconds(600).plusMillis(250));

		DemandeManuelleResponse r = service.creer(new DemandeManuelleCreationRequest(t.rejet.getId(), "entree",
				MAINTENANT.minusSeconds(600), "GPS faible au rez-de-chaussée"));

		assertThat(r.type()).isEqualTo(PointageType.ENTREE);
		assertThat(r.heureDeclaree()).isEqualTo(t.rejet.getServerTs());
	}

	@Test
	void creation_champNonModifiableAvantPointageNonRejete() {
		t.rejet.setStatut(PointageStatut.VALIDE);
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		exigeErreur(() -> service.creer(new DemandeManuelleCreationRequest(t.rejet.getId(), "SORTIE", null,
				"GPS faible au rez-de-chaussée")), 400, "CHAMP_NON_MODIFIABLE");
	}

	@Test
	void creation_pointageValide_422PointageNonRejete() {
		t.rejet.setStatut(PointageStatut.VALIDE);
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		exigeErreur(() -> service.creer(requete("GPS faible au rez-de-chaussée")), 422, "POINTAGE_NON_REJETE");
	}

	@Test
	void creation_demandeDejaFaite_409AvecLeNumero_avantNonRattrapable() {
		t.rejet.setStatut(PointageStatut.REJETE_POSITION_SIMULEE);
		PresenceDemandeManuelle existante = t.demandeEnAttente();
		when(t.demandeRepository.findByPointageRejeteId(t.rejet.getId())).thenReturn(Optional.of(existante));
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		assertThatThrownBy(() -> service.creer(requete("GPS faible au rez-de-chaussée")))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(409);
					assertThat(e.getCode()).isEqualTo("DEMANDE_DEJA_FAITE");
					assertThat(e.getMessage()).contains("PM-202611-0007");
				});
	}

	@Test
	void creation_demandeDejaFaite_memeApresRefusOuExpiration() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);
		for (StatutDemandeManuelle statut : List.of(StatutDemandeManuelle.REFUSEE, StatutDemandeManuelle.EXPIREE,
				StatutDemandeManuelle.VALIDEE)) {
			PresenceDemandeManuelle existante = t.demandeEnAttente();
			existante.setStatut(statut);
			when(t.demandeRepository.findByPointageRejeteId(t.rejet.getId())).thenReturn(Optional.of(existante));

			exigeErreur(() -> service.creer(requete("GPS faible au rez-de-chaussée")), 409, "DEMANDE_DEJA_FAITE");
		}
	}

	@Test
	void creation_courseSurLaContrainteUnique_409DemandeDejaFaite_avecLeNumeroDeLaGagnante() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);
		when(t.demandeRepository.saveAndFlush(any(PresenceDemandeManuelle.class)))
				.thenThrow(new org.springframework.dao.DataIntegrityViolationException("could not execute statement",
						new RuntimeException("ERREUR: la valeur d'une clé dupliquée rompt la contrainte unique « uk_demande_manuelle_rejet »")));
		when(t.demandeRepository.numeroParPointageRejeteId(t.rejet.getId())).thenReturn(Optional.of("PM-202611-0007"));

		assertThatThrownBy(() -> service.creer(requete("GPS faible au rez-de-chaussée")))
				.isInstanceOfSatisfying(PresenceMetierException.class, e -> {
					assertThat(e.getHttpStatus()).isEqualTo(409);
					assertThat(e.getCode()).isEqualTo("DEMANDE_DEJA_FAITE");
					assertThat(e.getMessage()).contains("PM-202611-0007");
				});
		verifyNoInteractions(t.eventPublisher);
	}

	@Test
	void creation_autreViolationDIntegrite_estRelancee() {
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);
		org.springframework.dao.DataIntegrityViolationException autre =
				new org.springframework.dao.DataIntegrityViolationException("uk_demande_manuelle_numero");
		when(t.demandeRepository.saveAndFlush(any(PresenceDemandeManuelle.class))).thenThrow(autre);

		assertThatThrownBy(() -> service.creer(requete("GPS faible au rez-de-chaussée"))).isSameAs(autre);
		verifyNoInteractions(t.eventPublisher);
	}

	@Test
	void creation_refusDOffice_nEnvoieAucuneNotification_etNeCreeRien() {
		t.rejet.setStatut(PointageStatut.REJETE_QR_EXPIRE);
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		exigeErreur(() -> service.creer(requete("GPS faible au rez-de-chaussée")), 422, "POINTAGE_NON_RATTRAPABLE");
		verify(t.demandeRepository, never()).saveAndFlush(any());
		verifyNoInteractions(t.eventPublisher);
	}

	@Test
	void creation_heureHorsFenetre_422() {
		when(t.fenetreHoraire.evaluer(any(), any(), any(), any()))
				.thenReturn(new FenetreHoraireService.HorsFenetre("Pointage ouvert de 05:00 à 16:00"));
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		exigeErreur(() -> service.creer(requete("Incident réseau")), 422, "HEURE_HORS_FENETRE");
		verify(t.demandeRepository, never()).saveAndFlush(any());
	}

	@Test
	void creation_heureDansLaFenetre_ok() {
		when(t.fenetreHoraire.evaluer(any(), any(), any(), any())).thenReturn(
				new FenetreHoraireService.DansFenetre(java.time.LocalDate.of(2026, 11, 11), MAINTENANT, MAINTENANT));
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		assertThat(service.creer(requete("Incident réseau")).statut()).isEqualTo(StatutDemandeManuelle.EN_ATTENTE);
	}

	@Test
	void creation_signalManuelsDuMoisSeuil_comptesValideesDuMoisDeLaTentative() {
		when(t.demandeRepository.compterValideesSurPeriode(eq(t.ficheCollab.toString()), any(), any())).thenReturn(3L);
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		service.creer(requete("GPS faible au rez-de-chaussée"));

		ArgumentCaptor<PresenceDemandeManuelle> d = ArgumentCaptor.forClass(PresenceDemandeManuelle.class);
		verify(t.demandeRepository).saveAndFlush(d.capture());
		assertThat(d.getValue().getSignaux()).containsExactly(SignalVigilance.MANUELS_MOIS_SEUIL);
		// mois civil de Tunis de la tentative : novembre 2026
		ArgumentCaptor<Instant> du = ArgumentCaptor.forClass(Instant.class);
		ArgumentCaptor<Instant> au = ArgumentCaptor.forClass(Instant.class);
		verify(t.demandeRepository).compterValideesSurPeriode(anyString(), du.capture(), au.capture());
		assertThat(du.getValue()).isEqualTo(Instant.parse("2026-10-31T23:00:00Z"));
		assertThat(au.getValue()).isEqualTo(Instant.parse("2026-11-30T23:00:00Z"));
	}

	// ---- Validation ------------------------------------------------------------------------------------------------

	@Test
	void validationParLeResponsable_creeUnPointageValideManuel_etFigeLaDecision() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());

		DemandeManuelleResponse r = service.valider(d.getId(), null);

		assertThat(r.statut()).isEqualTo(StatutDemandeManuelle.VALIDEE);
		assertThat(r.decidePar()).isEqualTo("Omar Belhadj");
		assertThat(r.delaiDecisionMinutes()).isEqualTo(10L);
		ArgumentCaptor<PresencePointage> p = ArgumentCaptor.forClass(PresencePointage.class);
		verify(t.pointageRepository).save(p.capture());
		assertThat(p.getValue().getStatut()).isEqualTo(PointageStatut.VALIDE_MANUEL);
		assertThat(p.getValue().getServerTs()).isEqualTo(d.getHeureDeclaree());
		assertThat(p.getValue().getDemandeManuelleId()).isEqualTo(d.getId());
		ArgumentCaptor<ObjetNotification> objet = ArgumentCaptor.forClass(ObjetNotification.class);
		verify(t.eventPublisher).notifier(eq(t.compteCollab.toString()), eq("Pointage manuel validé — PM-202611-0007"),
				anyString(), objet.capture());
		assertThat(objet.getValue().type()).isEqualTo("POINTAGE_MANUEL");
		assertThat(objet.getValue().evenement()).isEqualTo("POINTAGE_MANUEL_VALIDE");
	}

	@Test
	void validation_commentaireFourniSansSignal_estStockeSansEspacesDeBord() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());

		DemandeManuelleResponse r = service.valider(d.getId(), "   Vu avec le chef de site   ");

		assertThat(r.commentaireValideur()).isEqualTo("Vu avec le chef de site");
	}

	@Test
	void validation_avecSignal_commentaireObligatoire_videEtEspacesSeuls() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setSignaux(List.of(SignalVigilance.SITE_QR_PAPIER));
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());

		exigeErreur(() -> service.valider(d.getId(), null), 400, "COMMENTAIRE_OBLIGATOIRE");
		exigeErreur(() -> service.valider(d.getId(), ""), 400, "COMMENTAIRE_OBLIGATOIRE");
		exigeErreur(() -> service.valider(d.getId(), "      "), 400, "COMMENTAIRE_OBLIGATOIRE");
		verify(t.pointageRepository, never()).save(any());
		assertThat(d.getStatut()).isEqualTo(StatutDemandeManuelle.EN_ATTENTE);
	}

	@Test
	void validation_commentaireTropCourt_avecOuSansSignal() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());

		exigeErreur(() -> service.valider(d.getId(), "court"), 400, "COMMENTAIRE_TROP_COURT");
		exigeErreur(() -> service.valider(d.getId(), "  123456789  "), 400, "COMMENTAIRE_TROP_COURT");
		d.setSignaux(List.of(SignalVigilance.REFUS_FACIAL));
		exigeErreur(() -> service.valider(d.getId(), "court"), 400, "COMMENTAIRE_TROP_COURT");
	}

	@Test
	void validation_commentaireTropLong_501CaracteresApresTrim() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());

		exigeErreur(() -> service.valider(d.getId(), " " + "x".repeat(501) + " "), 400, "COMMENTAIRE_TROP_LONG");
	}

	@Test
	void validation_commentaireDe10Et500Caracteres_accepte_avecSignal() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setSignaux(List.of(SignalVigilance.SITE_QR_PAPIER));
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());

		assertThat(service.valider(d.getId(), "1234567890").statut()).isEqualTo(StatutDemandeManuelle.VALIDEE);
		PresenceDemandeManuelle autre = t.demandeEnAttente();
		autre.setSignaux(List.of(SignalVigilance.SITE_QR_PAPIER));
		assertThat(service.valider(autre.getId(), "x".repeat(500)).commentaireValideur()).hasSize(500);
	}

	@Test
	void validation_commentaireObligatoireMemePourUnRh() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setSignaux(List.of(SignalVigilance.DEMANDE_TARDIVE));
		when(t.accessService.isBackofficeEcriture()).thenReturn(true);
		when(t.accessService.currentCompteId()).thenReturn(UUID.randomUUID().toString());
		when(t.referentielClient.monValideurPointage()).thenReturn(new ValideurPointage(
				UUID.randomUUID(), UUID.randomUUID(), "Leila DRH", null, null, null));

		exigeErreur(() -> service.valider(d.getId(), null), 400, "COMMENTAIRE_OBLIGATOIRE");
	}

	@Test
	void validationParUnAutreResponsable_403_sansVerrouNiEcriture() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		when(t.referentielClient.monValideurPointage()).thenReturn(new ValideurPointage(
				UUID.randomUUID(), UUID.randomUUID(), "Autre RO", null, null, null));

		assertThatThrownBy(() -> service.valider(d.getId(), null)).isInstanceOf(SecurityException.class);
		verify(t.pointageRepository, never()).save(any());
		verify(t.demandeRepository, never()).findByIdPourDecision(any());
	}

	@Test
	void validationDeSaPropreDemande_403() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setValideurId(t.ficheCollab.toString());
		t.connecteCommeCollaborateur(t.ficheRo, t.compteRo);

		assertThatThrownBy(() -> service.valider(d.getId(), null)).isInstanceOf(SecurityException.class);
	}

	@Test
	void rhNeValidePasSaPropreDemande() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		when(t.accessService.isBackofficeEcriture()).thenReturn(true);
		when(t.accessService.currentCompteId()).thenReturn(t.compteCollab.toString());

		assertThatThrownBy(() -> service.valider(d.getId(), null)).isInstanceOf(SecurityException.class);
	}

	@Test
	void rhValideUneDemandeSansResponsable() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setValideurId(null);
		when(t.accessService.isBackofficeEcriture()).thenReturn(true);
		when(t.accessService.currentCompteId()).thenReturn(UUID.randomUUID().toString());
		when(t.referentielClient.monValideurPointage()).thenReturn(new ValideurPointage(
				UUID.randomUUID(), UUID.randomUUID(), "Leila DRH", null, null, null));

		assertThat(service.valider(d.getId(), null).decidePar()).isEqualTo("Leila DRH (RH)");
	}

	@Test
	void validation_demandeInconnue_404() {
		exigeErreur(() -> service.valider(UUID.randomUUID(), null), 404, "DEMANDE_INTROUVABLE");
	}

	@Test
	void auDelaDuSeuilMensuel_alerteRh() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());
		when(t.demandeRepository.compterValideesSurPeriode(eq(t.ficheCollab.toString()), any(), any())).thenReturn(4L);

		DemandeManuelleResponse r = service.valider(d.getId(), null);

		assertThat(r.alerteSeuil()).isTrue();
		verify(t.eventPublisher).notifier(eq(PresenceEventPublisher.DESTINATAIRE_RH), startsWith("Pointages manuels fréquents"),
				anyString(), any());
	}

	@Test
	void seuilRegleADeux_alerteRhALaTroisiemeDemandeDuMois() {
		com.hr.presence.entity.PresenceParametres ligne = new com.hr.presence.entity.PresenceParametres();
		ligne.setSeuilPointagesManuelsMois(2);
		when(t.parametresRepository.findById(com.hr.presence.entity.PresenceParametres.ID_UNIQUE))
				.thenReturn(java.util.Optional.of(ligne));
		PresenceDemandeManuelle d = t.demandeEnAttente();
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());
		// 2 validées dans le mois : sous le seuil de 3 par défaut, mais la 3e demande dépasse le seuil réglé à 2
		when(t.demandeRepository.compterValideesSurPeriode(eq(t.ficheCollab.toString()), any(), any())).thenReturn(3L);

		DemandeManuelleResponse r = service.valider(d.getId(), null);

		assertThat(r.alerteSeuil()).isTrue();
		verify(t.eventPublisher).notifier(eq(PresenceEventPublisher.DESTINATAIRE_RH), startsWith("Pointages manuels fréquents"),
				anyString(), any());
	}

	@Test
	void seuilParDefaut_troisDemandes_pasDAlerte() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());
		when(t.demandeRepository.compterValideesSurPeriode(eq(t.ficheCollab.toString()), any(), any())).thenReturn(3L);

		DemandeManuelleResponse r = service.valider(d.getId(), null);

		assertThat(r.alerteSeuil()).isFalse();
	}

	@Test
	void demandeDejaTraitee_409DemandeDejaTraitee() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		d.setStatut(StatutDemandeManuelle.REFUSEE);
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());

		exigeErreur(() -> service.valider(d.getId(), null), 409, "DEMANDE_DEJA_TRAITEE");
		exigeErreur(() -> service.refuser(d.getId(), "Absent ce matin-là"), 409, "DEMANDE_DEJA_TRAITEE");
	}

	@Test
	void validation_horaireModifieEntreTemps_heureDevenueHorsFenetre_422() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		when(t.fenetreHoraire.evaluer(any(), any(), any(), any()))
				.thenReturn(new FenetreHoraireService.HorsFenetre("Site fermé aujourd'hui"));
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());

		exigeErreur(() -> service.valider(d.getId(), null), 422, "HEURE_HORS_FENETRE");
		verify(t.pointageRepository, never()).save(any(PresencePointage.class));
	}

	@Test
	void validation_dansLaFenetre_poseLInstantaneSurLePointageManuel() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		java.time.LocalDate journee = java.time.LocalDate.of(2026, 11, 3);
		Instant debut = Instant.parse("2026-11-03T05:00:00Z");
		Instant fin = Instant.parse("2026-11-03T14:00:00Z");
		when(t.fenetreHoraire.evaluer(any(), any(), any(), any()))
				.thenReturn(new FenetreHoraireService.DansFenetre(journee, debut, fin));
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());

		service.valider(d.getId(), null);

		ArgumentCaptor<PresencePointage> captor = ArgumentCaptor.forClass(PresencePointage.class);
		verify(t.pointageRepository).save(captor.capture());
		assertThat(captor.getValue().getJournee()).isEqualTo(journee);
		assertThat(captor.getValue().getHoraireDebutTs()).isEqualTo(debut);
	}

	// ---- Refus -----------------------------------------------------------------------------------------------------

	@Test
	void refus_notifieLeDemandeurAvecLeMotif_etFigeLeDelaiDeDecision() {
		PresenceDemandeManuelle d = t.demandeEnAttente();
		when(t.referentielClient.monValideurPointage()).thenReturn(t.ro());

		DemandeManuelleResponse r = service.refuser(d.getId(), "Absent ce matin-là");

		assertThat(r.statut()).isEqualTo(StatutDemandeManuelle.REFUSEE);
		assertThat(r.motifRefus()).isEqualTo("Absent ce matin-là");
		assertThat(r.delaiDecisionMinutes()).isEqualTo(10L);
		verify(t.eventPublisher).notifier(eq(t.compteCollab.toString()), contains("Pointage manuel refusé"), anyString(), any());
		verify(t.pointageRepository, never()).save(any());
	}
}
