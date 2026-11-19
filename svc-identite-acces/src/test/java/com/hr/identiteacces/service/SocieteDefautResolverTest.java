package com.hr.identiteacces.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hr.identiteacces.domain.StatutSociete;
import com.hr.identiteacces.entity.Societe;
import com.hr.identiteacces.repository.SocieteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocieteDefautResolverTest {

	@Mock
	private SocieteRepository societeRepository;

	@Test
	@DisplayName("D-SOC03.e / D-SOC20 #3 : propriété à true → résout l'identifiant et journalise un WARN")
	void proprieteTrue_resoutIdentifiantEtJournaliseWarn() {
		UUID idDefaut = UUID.randomUUID();
		Societe principale = Societe.builder()
				.id(idDefaut)
				.code("PRINCIPALE")
				.raisonSociale("Société principale")
				.statut(StatutSociete.ACTIVE)
				.build();
		when(societeRepository.findByCodeIgnoreCase("PRINCIPALE")).thenReturn(Optional.of(principale));

		Logger logger = (Logger) LoggerFactory.getLogger(SocieteDefautResolver.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			SocieteDefautResolver resolver = new SocieteDefautResolver(societeRepository, "PRINCIPALE", true);

			assertThat(resolver.resoudreIdSiTransitionActive()).isEqualTo(idDefaut);
			Optional<UUID> res = resolver.resoudrePourTransitionAvecWarn("Appel interne test");

			assertThat(res).contains(idDefaut);
			verify(societeRepository, org.mockito.Mockito.times(2)).findByCodeIgnoreCase("PRINCIPALE");
			assertThat(appender.list)
					.anySatisfy(ev -> {
						assertThat(ev.getLevel()).isEqualTo(Level.WARN);
						assertThat(ev.getFormattedMessage()).contains("repli sur la société par défaut PRINCIPALE");
					});
		} finally {
			logger.detachAppender(appender);
		}
	}

	@Test
	@DisplayName("D-SOC03.e / D-SOC20 #3 : propriété à false → vide, sans aucune requête en base")
	void proprieteFalse_videSansRequete() {
		SocieteDefautResolver resolver = new SocieteDefautResolver(societeRepository, "PRINCIPALE", false);

		assertThat(resolver.resoudreIdSiTransitionActive()).isNull();
		assertThat(resolver.resoudrePourTransitionAvecWarn("Appel interne test")).isEmpty();
		verifyNoInteractions(societeRepository);
	}
}
