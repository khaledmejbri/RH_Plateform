package com.hr.evaluation.service;

import com.hr.evaluation.client.PersonneResumee;
import com.hr.evaluation.client.ReferentielNomsClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Résolveur par lot : un seul appel pour 50 lignes, cache, repli sans exception si le référentiel est en panne. */
class NomsCollaborateursTest {

	private static class ClientFactice implements ReferentielNomsClient {
		final List<Integer> tailleDesAppels = new ArrayList<>();
		boolean panne;

		@Override
		public List<PersonneResumee> resoudre(Collection<UUID> ids) {
			tailleDesAppels.add(ids.size());
			if (panne) {
				throw new IllegalStateException("503");
			}
			return ids.stream().map(id -> new PersonneResumee(id, null, "Nom " + id, "M-" + id.toString().substring(0, 4),
					true, null, null, null)).toList();
		}
	}

	private final ClientFactice client = new ClientFactice();
	private final NomsCollaborateurs noms = new NomsCollaborateurs(client, 5, 1000);

	private static List<UUID> ids(int n) {
		return IntStream.range(0, n).mapToObj(i -> UUID.randomUUID()).toList();
	}

	@Test
	void cinquanteLignes_unSeulAppel_puisZeroAppelGraceAuCache() {
		List<UUID> cinquante = ids(50);
		assertThat(noms.resoudre(cinquante)).hasSize(50);
		assertThat(client.tailleDesAppels).containsExactly(50);
		assertThat(noms.resoudre(cinquante)).hasSize(50);
		assertThat(client.tailleDesAppels).hasSize(1);
	}

	@Test
	void plusDeCentIdentifiants_decoupesParLotsDeCent() {
		noms.resoudre(ids(230));
		assertThat(client.tailleDesAppels).containsExactly(100, 100, 30);
	}

	@Test
	void referentielEnPanne_pasdException_nomAbsent_repliSurLInstantane() {
		client.panne = true;
		UUID id = UUID.randomUUID();
		Map<UUID, PersonneResumee> resultat = noms.resoudre(List.of(id));
		assertThat(resultat).isEmpty();
		assertThat(NomsCollaborateurs.nom(resultat, id, "Instantané")).isEqualTo("Instantané");
		assertThat(NomsCollaborateurs.nom(resultat, id, null)).isNull();
	}

	@Test
	void panneApresSucces_lesNomsEnCacheRestentServis() {
		UUID connu = UUID.randomUUID();
		noms.resoudre(List.of(connu));
		client.panne = true;
		UUID autre = UUID.randomUUID();
		Map<UUID, PersonneResumee> resultat = noms.resoudre(List.of(connu, autre));
		assertThat(resultat).containsKey(connu).doesNotContainKey(autre);
	}

	@Test
	void identifiantsNulsEtDoublons_ignores() {
		UUID id = UUID.randomUUID();
		noms.resoudre(java.util.Arrays.asList(id, id, null));
		assertThat(client.tailleDesAppels).containsExactly(1);
	}
}
