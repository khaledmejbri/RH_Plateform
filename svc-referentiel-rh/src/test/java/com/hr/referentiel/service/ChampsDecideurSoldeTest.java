package com.hr.referentiel.service;

import com.hr.referentiel.domain.StatutDemandeAdministrativeRh;
import com.hr.referentiel.domain.TypeDemandeAdministrativeRh;
import com.hr.referentiel.dto.DemandeAdministrativeRhResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.DemandeAdministrativeRh;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** C1-18, D5 : champs du décideur et indicateurs d'annulation, y compris pour un RH / ADMIN sans fiche (m3) et pour le demandeur (M3). */
class ChampsDecideurSoldeTest {

	private CollaborateurConnecteService connecte;
	private SoldeCongeService soldeService;
	private ChampsDecideurSolde champs;
	private Collaborateur colWeb;
	private Collaborateur csWeb;
	private DemandeAdministrativeRh demande;
	private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("x").build();

	@BeforeEach
	void setUp() {
		connecte = mock(CollaborateurConnecteService.class);
		soldeService = mock(SoldeCongeService.class);
		champs = new ChampsDecideurSolde(connecte, soldeService, Clock.fixed(Instant.parse("2026-12-12T10:00:00Z"), ZoneOffset.UTC));
		colWeb = fiche("COL-WEB");
		csWeb = fiche("CS-WEB");
		demande = new DemandeAdministrativeRh();
		demande.setId(UUID.randomUUID());
		demande.setTypeDemande(TypeDemandeAdministrativeRh.CONGE);
		demande.setDemandeur(colWeb);
		demande.setValideurAttendu(csWeb);
		demande.setContenu(new HashMap<>(Map.of("type_conge", "ANNUEL")));
		demande.setJoursDecomptes(new BigDecimal("3.0"));
		demande.setPeriodeDebut(LocalDate.of(2026, 12, 20));
		demande.setPeriodeFin(LocalDate.of(2026, 12, 22));
		when(soldeService.acquisGroupes(any())).thenReturn(Map.of(colWeb.getId(), new BigDecimal("10.0")));
		when(soldeService.reservesGroupes(any())).thenReturn(Map.of(colWeb.getId(), new BigDecimal("5.0")));
	}

	@AfterEach
	void nettoyer() {
		SecurityContextHolder.clearContext();
	}

	private static Collaborateur fiche(String matricule) {
		Collaborateur c = new Collaborateur();
		c.setId(UUID.randomUUID());
		c.setMatricule(matricule);
		return c;
	}

	private void connecter(Collaborateur fiche, String... roles) {
		when(connecte.resoudre(jwt)).thenReturn(Optional.ofNullable(fiche));
		var autorites = java.util.Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, autorites));
	}

	private DemandeAdministrativeRhResponse annoter() {
		DemandeAdministrativeRhResponse r = new DemandeAdministrativeRhResponse();
		champs.annoter(List.of(demande), List.of(r));
		return r;
	}

	@Test
	@DisplayName("C1-18 : le valideur attendu voit disponible, solde après et réservé des autres demandes à l'étape responsable")
	void valideurAttendu() {
		demande.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR);
		connecter(csWeb, "USER", "RO");
		DemandeAdministrativeRhResponse r = annoter();
		assertThat(r.getSoldeDisponibleDemandeur()).isEqualByComparingTo("10");
		assertThat(r.getSoldeApresDemande()).isEqualByComparingTo("7");
		assertThat(r.getSoldeReserveAutresDemandes()).isEqualByComparingTo("2");
		assertThat(r.getPeutAnnuler()).isFalse();
	}

	@Test
	@DisplayName("C1-18 : champs absents pour un RH à l'étape responsable, pour le demandeur, pour DIRECTION et sur une demande décidée")
	void absentsAilleurs() {
		demande.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR);
		connecter(fiche("DRH01"), "USER", "RH");
		assertThat(annoter().getSoldeDisponibleDemandeur()).isNull();
		connecter(colWeb, "USER");
		assertThat(annoter().getSoldeDisponibleDemandeur()).isNull();
		demande.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		connecter(fiche("DG01"), "USER", "DIRECTION");
		assertThat(annoter().getSoldeDisponibleDemandeur()).isNull();
		demande.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		connecter(fiche("DRH01"), "USER", "RH");
		assertThat(annoter().getSoldeDisponibleDemandeur()).isNull();
	}

	@Test
	@DisplayName("m3 : un RH / ADMIN sans fiche reçoit les champs décideur de l'étape RH et peut_annuler_apres_approbation")
	void decideurSansFiche() {
		demande.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		connecter(null, "USER", "ADMIN");
		DemandeAdministrativeRhResponse r = annoter();
		assertThat(r.getSoldeDisponibleDemandeur()).isEqualByComparingTo("10");
		assertThat(r.getSoldeApresDemande()).isEqualByComparingTo("7");
		demande.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		assertThat(annoter().getPeutAnnulerApresApprobation()).isTrue();
		// sans fiche, le valideur attendu ne peut pas être reconnu : pas de champs à l'étape responsable
		demande.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR);
		assertThat(annoter().getSoldeDisponibleDemandeur()).isNull();
	}

	@Test
	@DisplayName("D4 : peut_annuler_apres_approbation seulement pour RH / ADMIN autre que le demandeur, congé approuvé non commencé ; jamais pour DIRECTION ni le demandeur")
	void peutAnnulerApresApprobation() {
		demande.setStatut(StatutDemandeAdministrativeRh.APPROUVEE);
		connecter(fiche("DRH01"), "USER", "RH");
		assertThat(annoter().getPeutAnnulerApresApprobation()).isTrue();
		demande.setPeriodeDebut(LocalDate.of(2026, 12, 12));
		assertThat(annoter().getPeutAnnulerApresApprobation()).isFalse();
		demande.setPeriodeDebut(LocalDate.of(2026, 12, 20));
		connecter(fiche("DG01"), "USER", "DIRECTION");
		assertThat(annoter().getPeutAnnulerApresApprobation()).isFalse();
		connecter(colWeb, "USER", "RH");
		assertThat(annoter().getPeutAnnulerApresApprobation()).isFalse();
	}

	@Test
	@DisplayName("M3 : peut_annuler vrai seulement pour le demandeur d'une demande en cours de validation")
	void peutAnnuler() {
		connecter(colWeb, "USER");
		demande.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR);
		assertThat(annoter().getPeutAnnuler()).isTrue();
		demande.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		assertThat(annoter().getPeutAnnuler()).isTrue();
		for (StatutDemandeAdministrativeRh s : List.of(StatutDemandeAdministrativeRh.APPROUVEE, StatutDemandeAdministrativeRh.REFUSEE,
				StatutDemandeAdministrativeRh.ANNULEE)) {
			demande.setStatut(s);
			assertThat(annoter().getPeutAnnuler()).as(s.name()).isFalse();
		}
		demande.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_SUPERIEUR);
		connecter(csWeb, "USER", "RO");
		assertThat(annoter().getPeutAnnuler()).isFalse();
		connecter(fiche("DRH01"), "USER", "RH");
		assertThat(annoter().getPeutAnnuler()).isFalse();
		// ordre de mission du demandeur en cours de validation : annulable aussi
		demande.setTypeDemande(TypeDemandeAdministrativeRh.ORDRE_MISSION);
		connecter(colWeb, "USER");
		assertThat(annoter().getPeutAnnuler()).isTrue();
	}

	@Test
	@DisplayName("sans contexte de sécurité authentifié, rien n'est ajouté")
	void sansContexte() {
		demande.setStatut(StatutDemandeAdministrativeRh.EN_VALIDATION_RRH);
		DemandeAdministrativeRhResponse r = annoter();
		assertThat(r.getPeutAnnuler()).isNull();
		assertThat(r.getSoldeDisponibleDemandeur()).isNull();
	}
}
