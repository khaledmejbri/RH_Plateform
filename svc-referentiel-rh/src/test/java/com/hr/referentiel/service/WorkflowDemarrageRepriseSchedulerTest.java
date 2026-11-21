package com.hr.referentiel.service;

import com.hr.commun.societe.SocieteContexte;
import com.hr.commun.societe.SocietesActives;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-W5-05 : reprise des démarrages par société ; un échec n'arrête pas le lot. */
@ExtendWith(MockitoExtension.class)
class WorkflowDemarrageRepriseSchedulerTest {

	@Mock
	private WorkflowDemarrageService demarrageService;
	@Mock
	private SocietesActives societesActives;

	@Test
	void chaqueDemandeDuLotEstDemarreeDansLaSocieteEtUnEchecNArretePasLeLot() {
		UUID societe = UUID.randomUUID();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		when(societesActives.lister()).thenReturn(List.of(societe));
		List<UUID> societesVues = new ArrayList<>();
		when(demarrageService.selectionnerLotADemarrer(50)).thenAnswer(inv -> {
			societesVues.add(SocieteContexte.courante());
			return List.of(a, b, c);
		});
		List<UUID> demarres = new ArrayList<>();
		doAnswer(inv -> {
			UUID id = inv.getArgument(0);
			demarres.add(id);
			if (id.equals(b)) {
				throw new IllegalStateException("moteur en panne");
			}
			return null;
		}).when(demarrageService).demarrer(any());

		new WorkflowDemarrageRepriseScheduler(demarrageService, null, societesActives).reprendreDemarrages();

		assertThat(societesVues).containsExactly(societe);
		assertThat(demarres).containsExactly(a, b, c);
	}

	@Test
	void echecDeLaListeDesSocietesNeLancePasDeDemarrage() {
		when(societesActives.lister()).thenThrow(new IllegalStateException("base indisponible"));

		new WorkflowDemarrageRepriseScheduler(demarrageService, null, societesActives).reprendreDemarrages();

		verify(demarrageService, never()).selectionnerLotADemarrer(anyInt());
		verify(demarrageService, never()).demarrer(any());
	}
}
