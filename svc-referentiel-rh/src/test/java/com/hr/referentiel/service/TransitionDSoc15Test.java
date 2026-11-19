package com.hr.referentiel.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocieteFiltreInterne;
import com.hr.referentiel.entity.Collaborateur;
import com.hr.referentiel.kafka.CollaborateurCompteCreeEvent;
import com.hr.referentiel.kafka.CollaborateurCompteCreeKafkaListener;
import com.hr.referentiel.kafka.RhKafkaTopics;
import com.hr.referentiel.repository.CollaborateurRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la règle de transition D-SOC15 (appels internes {@code /internal/referentiel/**} et messages Kafka sans
 * en-tête {@code societe_id} avec {@code app.societe.repli-defaut-transition} à {@code true} puis {@code false}).
 */
class TransitionDSoc15Test {

	private static final UUID SOCIETE_DEFAUT = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final String JETON = "jeton-interne-test";

	@Test
	@DisplayName("D-SOC15 HTTP : sans X-Societe-Id, repli sur la société par défaut si transition=true, 400 SOCIETE_ABSENTE si transition=false")
	void transitionHttpInterne() throws Exception {
		IdentiteSocieteClient client = mock(IdentiteSocieteClient.class);
		when(client.trouverIdParCode("PRINCIPALE")).thenReturn(Optional.of(SOCIETE_DEFAUT));

		// 1. Transition active (true)
		SocieteDefautResolver resolverActif = new SocieteDefautResolver(client, "PRINCIPALE", true);
		SocieteFiltreInterne filtreActif = new SocieteFiltreInterne(
				JETON, resolverActif::resoudreIdSiTransitionActive);

		MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/internal/referentiel/collaborateurs");
		req1.addHeader("X-Internal-Token", JETON);
		MockHttpServletResponse res1 = new MockHttpServletResponse();
		AtomicReference<UUID> societeVue = new AtomicReference<>();

		filtreActif.doFilter(req1, res1, (request, response) -> societeVue.set(SocieteContexte.courante()));
		assertThat(res1.getStatus()).isEqualTo(200);
		assertThat(societeVue.get()).isEqualTo(SOCIETE_DEFAUT);

		// 2. Transition désactivée (false) -> 400 SOCIETE_ABSENTE
		SocieteDefautResolver resolverInactif = new SocieteDefautResolver(client, "PRINCIPALE", false);
		SocieteFiltreInterne filtreInactif = new SocieteFiltreInterne(
				JETON, resolverInactif::resoudreIdSiTransitionActive);

		MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/internal/referentiel/collaborateurs");
		req2.addHeader("X-Internal-Token", JETON);
		MockHttpServletResponse res2 = new MockHttpServletResponse();

		filtreInactif.doFilter(req2, res2, new MockFilterChain());
		assertThat(res2.getStatus()).isEqualTo(400);
		assertThat(res2.getContentAsString()).contains("SOCIETE_ABSENTE");
	}

	@Test
	@DisplayName("D-SOC15 Kafka : message collaborateur-compte.cree sans en-tête societe_id replie si transition=true et échoue si transition=false")
	void transitionKafkaCompteCree() {
		IdentiteSocieteClient client = mock(IdentiteSocieteClient.class);
		when(client.trouverIdParCode("PRINCIPALE")).thenReturn(Optional.of(SOCIETE_DEFAUT));

		CollaborateurRepository repo = mock(CollaborateurRepository.class);
		Collaborateur collab = new Collaborateur();
		UUID collabId = UUID.randomUUID();
		UUID compteId = UUID.randomUUID();
		collab.setId(collabId);
		when(repo.findById(collabId)).thenReturn(Optional.of(collab));

		PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
		when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		TransactionTemplate tt = new TransactionTemplate(tm);

		CollaborateurCompteCreeEvent event = new CollaborateurCompteCreeEvent(collabId, compteId);
		ConsumerRecord<String, CollaborateurCompteCreeEvent> recordSansEntete =
				new ConsumerRecord<>(RhKafkaTopics.COLLABORATEUR_COMPTE_CREE, 0, 1L, collabId.toString(), event);

		// 1. Transition active (true) -> compte lié sous SOCIETE_DEFAUT
		SocieteDefautResolver resolverActif = new SocieteDefautResolver(client, "PRINCIPALE", true);
		CollaborateurCompteCreeKafkaListener listenerActif =
				new CollaborateurCompteCreeKafkaListener(repo, resolverActif, tt);

		listenerActif.onCompteCree(recordSansEntete);
		assertThat(collab.getCompteUtilisateurId()).isEqualTo(compteId);
		verify(repo).save(collab);

		// 2. Transition désactivée (false) -> IllegalStateException
		SocieteDefautResolver resolverInactif = new SocieteDefautResolver(client, "PRINCIPALE", false);
		CollaborateurCompteCreeKafkaListener listenerInactif =
				new CollaborateurCompteCreeKafkaListener(repo, resolverInactif, tt);

		assertThatThrownBy(() -> listenerInactif.onCompteCree(recordSansEntete))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("sans en-tête societe_id");
	}
}
