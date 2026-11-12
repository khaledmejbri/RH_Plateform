package com.hr.presence.service;

import com.hr.presence.config.PresenceProperties;
import com.hr.presence.repository.PresenceParametreValeurRepository;
import com.hr.presence.repository.PresenceParametresRepository;

import static org.mockito.Mockito.mock;

/** Paramètres de test : dépôts vides, donc repli sur les propriétés et les libellés d'origine. */
final class ParametresTestSupport {

	private ParametresTestSupport() {
	}

	static PresenceParametresService depuis(PresenceProperties properties) {
		return new PresenceParametresService(mock(PresenceParametreValeurRepository.class),
				mock(PresenceParametresRepository.class), properties, 30);
	}
}
