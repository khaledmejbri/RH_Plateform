package com.hr.referentiel.service;

import com.hr.referentiel.domain.TypeMouvementSolde;
import com.hr.referentiel.dto.SoldeDtos.AjustementRequest;
import com.hr.referentiel.dto.SoldeDtos.AjustementResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.MouvementSoldeConge;
import com.hr.referentiel.entity.SoldeConge;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.CollaborateurRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C3-01 à C3-06 : ajustement manuel, ordre des contrôles D-S15.f. */
@ExtendWith(MockitoExtension.class)
class AjustementSoldeServiceTest {

	@Mock
	private CollaborateurRepository collaborateurRepository;
	@Mock
	private CollaborateurConnecteService connecte;
	@Mock
	private AuteurParametreResolver auteurResolver;
	@Mock
	private SoldeCongeService soldeService;
	@Mock
	private SoldeCongeLectureService lecture;
	@Mock
	private RhNotificationPublisher publisher;

	private AjustementSoldeService service;
	private Collaborateur cible;
	private Collaborateur drh;
	private SoldeConge solde;
	private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("DRH01")
			.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

	@BeforeEach
	void setUp() {
		service = new AjustementSoldeService(collaborateurRepository, connecte, auteurResolver, soldeService, lecture, publisher);
		cible = fiche("COL-WEB");
		drh = fiche("DRH01");
		solde = new SoldeConge();
		solde.setCollaborateurId(cible.getId());
		solde.setAcquis(new BigDecimal("10.0"));
		solde.setVersion(3);
		org.mockito.Mockito.lenient().when(collaborateurRepository.findDetailById(cible.getId())).thenReturn(Optional.of(cible));
		org.mockito.Mockito.lenient().when(connecte.resoudre(jwt)).thenReturn(Optional.of(drh));
		org.mockito.Mockito.lenient().when(auteurResolver.depuis(jwt)).thenReturn(new AuteurParametre(UUID.randomUUID(), "Amel Trabelsi"));
		org.mockito.Mockito.lenient().when(soldeService.verrouiller(cible.getId())).thenReturn(solde);
		org.mockito.Mockito.lenient().when(soldeService.reserve(cible.getId())).thenReturn(new BigDecimal("0.0"));
		org.mockito.Mockito.lenient().when(soldeService.enregistrer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
				.thenAnswer(i -> {
					BigDecimal n = i.getArgument(2);
					solde.setAcquis(solde.getAcquis().add(n));
					return new MouvementSoldeConge(cible.getId(), "AJUSTEMENT", n, solde.getAcquis(), "SAISIE_MANUELLE",
							i.getArgument(4), null, null, null, null, null, "Amel Trabelsi", Instant.now());
				});
	}

	private static Collaborateur fiche(String matricule) {
		Collaborateur c = new Collaborateur();
		c.setId(UUID.randomUUID());
		c.setMatricule(matricule);
		c.setPrenom("P");
		c.setNom("N");
		return c;
	}

	private static String code(Throwable t) {
		return ((ReferentielMetierException) t).getCode();
	}

	private AjustementRequest ecart(Object e, String motif, Long version) {
		return new AjustementRequest(e, null, motif, version);
	}

	@Test
	@DisplayName("C3-01 : +2 « Report 2025 » -> mouvement AJUSTEMENT +2 origine saisie manuelle, collaborateur notifié (après commit)")
	void ajustementPositif() {
		AjustementResponse r = service.ajuster(cible.getId(), ecart(2, "Report 2025", 3L), jwt);
		assertThat(r.avertissement()).isNull();
		verify(soldeService).enregistrer(eq(solde), eq(TypeMouvementSolde.AJUSTEMENT), eq(new BigDecimal("2.0")),
				eq(com.hr.referentiel.domain.OrigineMouvementSolde.SAISIE_MANUELLE), eq("Report 2025"), any(), any(), any(),
				any(), any());
		verify(publisher).notifierSoldeAjuste(eq(cible), eq(new BigDecimal("2.0")), eq(new BigDecimal("12.0")), eq("Report 2025"));
	}

	@Test
	@DisplayName("C3-01 : nouveau solde 7,5 depuis 10 -> écart -2,5")
	void nouveauSolde() {
		service.ajuster(cible.getId(), new AjustementRequest(null, "7,5", "Correction", 3L), jwt);
		verify(soldeService).enregistrer(any(), any(), eq(new BigDecimal("-2.5")), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("C3-02 : formats et bornes -> 400 VALEUR_INVALIDE ; ±365 acceptés ; deux modes ou aucun -> 400")
	void valeursInvalides() {
		for (Object v : new Object[] {1.3, 0.25, "abc", 365.5, -366, 400}) {
			assertThatThrownBy(() -> service.ajuster(cible.getId(), ecart(v, "Motif ok", 3L), jwt))
					.satisfies(e -> assertThat(code(e)).isEqualTo("VALEUR_INVALIDE"));
		}
		assertThatThrownBy(() -> service.ajuster(cible.getId(), ecart(null, "Motif ok", 3L), jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("VALEUR_INVALIDE"));
		assertThatThrownBy(() -> service.ajuster(cible.getId(), new AjustementRequest(1, 2, "Motif ok", 3L), jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("VALEUR_INVALIDE"));
		assertThatThrownBy(() -> service.ajuster(cible.getId(), new AjustementRequest(null, -0.5, "Motif ok", 3L), jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("VALEUR_INVALIDE"));
		assertThat(service.valider(ecart(365, "Motif ok", 3L)).ecart()).isEqualByComparingTo("365");
		assertThat(service.valider(ecart(-365, "Motif ok", 3L)).ecart()).isEqualByComparingTo("-365");
		assertThat(service.valider(new AjustementRequest(null, 365, "Motif ok", 3L)).nouveauSolde()).isEqualByComparingTo("365");
	}

	@Test
	@DisplayName("C3-03 : motif de 4 caractères -> 400 MOTIF_OBLIGATOIRE ; 5 et 500 acceptés ; 501 refusé ; espaces de bord retirés")
	void motif() {
		assertThatThrownBy(() -> service.ajuster(cible.getId(), ecart(1, "abcd", 3L), jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("MOTIF_OBLIGATOIRE"));
		assertThatThrownBy(() -> service.ajuster(cible.getId(), ecart(1, "  abcd  ", 3L), jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("MOTIF_OBLIGATOIRE"));
		assertThatThrownBy(() -> service.ajuster(cible.getId(), ecart(1, "x".repeat(501), 3L), jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("MOTIF_OBLIGATOIRE"));
		assertThat(service.valider(ecart(1, "abcde", 3L)).motif()).isEqualTo("abcde");
		assertThat(service.valider(ecart(1, "x".repeat(500), 3L)).motif()).hasSize(500);
		// valeur et motif invalides : la réponse liste les deux champs
		assertThatThrownBy(() -> service.ajuster(cible.getId(), ecart(1.3, "ab", 3L), jwt)).satisfies(e -> {
			ReferentielMetierException m = (ReferentielMetierException) e;
			assertThat(((List<?>) m.getDetails().get("champs")).stream().map(String::valueOf).toList()).contains("ecart", "motif");
		});
	}

	@Test
	@DisplayName("C3-06 : version absente -> 400 CORPS_INVALIDE")
	void versionAbsente() {
		assertThatThrownBy(() -> service.ajuster(cible.getId(), ecart(1, "Motif ok", null), jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("CORPS_INVALIDE"));
	}

	@Test
	@DisplayName("C3-04 : écart 0 -> 422 AJUSTEMENT_NUL ; acquis 2 et écart -2,5 -> 422 SOLDE_NEGATIF ; écart -2 -> solde 0")
	void regles422() {
		assertThatThrownBy(() -> service.ajuster(cible.getId(), ecart(0, "Motif ok", 3L), jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("AJUSTEMENT_NUL"));
		assertThatThrownBy(() -> service.ajuster(cible.getId(), new AjustementRequest(null, 10, "Motif ok", 3L), jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("AJUSTEMENT_NUL"));
		solde.setAcquis(new BigDecimal("2.0"));
		assertThatThrownBy(() -> service.ajuster(cible.getId(), ecart(-2.5, "Motif ok", 3L), jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("SOLDE_NEGATIF"));
		verify(soldeService, never()).enregistrer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
		service.ajuster(cible.getId(), ecart(-2, "Motif ok", 3L), jwt);
		assertThat(solde.getAcquis()).isEqualByComparingTo("0");
	}

	@Test
	@DisplayName("C3-04 : acquis sous le réservé après ajustement -> 200 avec avertissement")
	void avertissementSousReserve() {
		solde.setAcquis(new BigDecimal("5.0"));
		when(soldeService.reserve(cible.getId())).thenReturn(new BigDecimal("4.0"));
		AjustementResponse r = service.ajuster(cible.getId(), ecart(-3, "Motif ok", 3L), jwt);
		assertThat(r.avertissement()).isEqualTo("Des demandes en cours dépassent le nouveau solde");
	}

	@Test
	@DisplayName("C3-05 : ajuster sa propre fiche -> 403 AUTO_AJUSTEMENT_INTERDIT, avant toute validation de corps")
	void autoAjustement() {
		when(connecte.resoudre(jwt)).thenReturn(Optional.of(cible));
		assertThatThrownBy(() -> service.ajuster(cible.getId(), ecart(null, null, null), jwt))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(e -> {
					assertThat(code(e)).isEqualTo("AUTO_AJUSTEMENT_INTERDIT");
					assertThat(((ReferentielMetierException) e).getHttpStatus()).isEqualTo(403);
				});
		verify(soldeService, never()).verrouiller(any());
	}

	@Test
	@DisplayName("404 COLLABORATEUR_INTROUVABLE avant tout autre contrôle")
	void collaborateurInconnu() {
		UUID inconnu = UUID.randomUUID();
		when(collaborateurRepository.findDetailById(inconnu)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.ajuster(inconnu, ecart(null, null, null), jwt))
				.satisfies(e -> assertThat(((ReferentielMetierException) e).getHttpStatus()).isEqualTo(404));
	}

	@Test
	@DisplayName("C3-06 : version périmée (comparée sous verrou) -> 409 SOLDE_MODIFIE_ENTRE_TEMPS, aucun écart perdu")
	void versionPerimee() {
		assertThatThrownBy(() -> service.ajuster(cible.getId(), ecart(1, "Motif ok", 2L), jwt))
				.satisfies(e -> {
					assertThat(code(e)).isEqualTo("SOLDE_MODIFIE_ENTRE_TEMPS");
					assertThat(((ReferentielMetierException) e).getHttpStatus()).isEqualTo(409);
				});
		verify(soldeService, never()).enregistrer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("ordre D-S15.f : 422 avant 409 (écart nul avec version périmée -> 422)")
	void ordre422Avant409() {
		assertThatThrownBy(() -> service.ajuster(cible.getId(), ecart(0, "Motif ok", 1L), jwt))
				.satisfies(e -> assertThat(code(e)).isEqualTo("AJUSTEMENT_NUL"));
	}
}
