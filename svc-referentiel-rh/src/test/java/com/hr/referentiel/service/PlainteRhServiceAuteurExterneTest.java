package com.hr.referentiel.service;

import com.hr.referentiel.domain.TypePlainteRh;
import com.hr.referentiel.dto.PlainteRhCreationRequest;
import com.hr.referentiel.dto.PlainteRhResponse;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.entity.PlainteRh;
import com.hr.referentiel.kafka.RhNotificationPublisher;
import com.hr.referentiel.repository.PlainteRhRepository;
import com.hr.referentiel.web.ReferentielMetierException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SCRUM-12 — § 9.1 / C-M04-01 : plainte EXTERNE déclarée par un RO ou la RH, pas par un collaborateur. */
@ExtendWith(MockitoExtension.class)
class PlainteRhServiceAuteurExterneTest {

	@Mock
	private PlainteRhRepository plainteRhRepository;
	@Mock
	private CollaborateurConnecteService collaborateurConnecteService;
	@Mock
	private RhNotificationPublisher notificationPublisher;

	@InjectMocks
	private PlainteRhService service;

	private Jwt jwt;
	private Collaborateur auteur;

	@BeforeEach
	void setUp() {
		jwt = Jwt.withTokenValue("t").header("alg", "none").subject("COL-1")
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
		auteur = new Collaborateur();
		auteur.setId(UUID.randomUUID());
		auteur.setPrenom("Lina");
		auteur.setNom("Test");
		auteur.setMatricule("COL-1");
	}

	private PlainteRhCreationRequest requete(TypePlainteRh type) {
		PlainteRhCreationRequest req = new PlainteRhCreationRequest();
		req.setTypePlainte(type);
		req.setTitre("Débordement");
		req.setDescription("Riverains");
		return req;
	}

	@Test
	@DisplayName("❌ Collaborateur sans rôle RO/RH → plainte EXTERNE refusée en 403")
	void externeRefuseeSansRole() {
		assertThatThrownBy(() -> service.creer(requete(TypePlainteRh.EXTERNE), jwt, false))
				.isInstanceOf(ReferentielMetierException.class)
				.satisfies(e -> assertThat(((ReferentielMetierException) e).getHttpStatus()).isEqualTo(403));
		verify(plainteRhRepository, never()).save(any());
	}

	@Test
	@DisplayName("✅ RO/RH → plainte EXTERNE créée, auteur exposé")
	void externeAcceptéeAvecRole() {
		when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(auteur);
		when(plainteRhRepository.save(any(PlainteRh.class))).thenAnswer(i -> i.getArgument(0));

		PlainteRhResponse r = service.creer(requete(TypePlainteRh.EXTERNE), jwt, true);

		assertThat(r.getTypePlainte()).isEqualTo(TypePlainteRh.EXTERNE);
		assertThat(r.getAuteurNom()).isEqualTo("Lina Test");
		assertThat(r.getValidationRo()).isEqualTo("EN_ATTENTE");
	}

	@Test
	@DisplayName("✅ Collaborateur → plainte INTERNE toujours autorisée")
	void interneAutoriseeSansRole() {
		when(collaborateurConnecteService.exigerCollaborateur(jwt)).thenReturn(auteur);
		when(plainteRhRepository.save(any(PlainteRh.class))).thenAnswer(i -> i.getArgument(0));

		PlainteRhResponse r = service.creer(requete(TypePlainteRh.INTERNE), jwt, false);

		assertThat(r.getTypePlainte()).isEqualTo(TypePlainteRh.INTERNE);
		assertThat(r.getValidationRo()).isNull();
	}
}
